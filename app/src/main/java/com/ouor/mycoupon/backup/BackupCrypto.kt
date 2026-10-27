package com.ouor.mycoupon.backup

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Wrong password, or a damaged / truncated file: indistinguishable by design (both fail authentication). */
class BackupAuthException : Exception("backup authentication failed")

/** Not a mycoupon backup, or a newer format than this app reads. */
class BackupFormatException(message: String) : Exception(message)

/**
 * F15 file encryption: PBKDF2-HMAC-SHA256 password key + AES-256-GCM in 1 MiB segments (the STREAM construction
 * used by Tink's AesGcmHkdfStreaming: nonce = random 7-byte prefix || segment counter || last-segment flag),
 * so files of any size stream through without holding the plaintext, and truncation or reordering fails.
 *
 * File: "MYCPNBK" | version(1) | iterations(int32 BE) | salt(16) | noncePrefix(7) | segments...
 * Every segment authenticates the whole header as AAD.
 * PBKDF2 is computed with javax.crypto.Mac because SecretKeyFactory PBKDF2WithHmacSHA256 needs API 26 (minSdk 24);
 * BackupCryptoTest checks it against the JDK implementation.
 */
object BackupCrypto {
    private val MAGIC = "MYCPNBK".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 1
    /** OWASP 2023 recommendation for PBKDF2-HMAC-SHA256 */
    const val ITERATIONS = 600_000
    const val SEGMENT = 1 shl 20
    private const val TAG = 16
    private const val HEADER = 7 + 1 + 4 + 16 + 7

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        require(password.isNotEmpty()) { "empty password" } // SecretKeySpec refuses empty keys; S14 requires a password
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(String(password).toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = ByteArray(bytes)
        var block = 1
        var off = 0
        while (off < bytes) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(block).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val n = minOf(t.size, bytes - off)
            System.arraycopy(t, 0, out, off, n)
            off += n
            block++
        }
        return out
    }

    private fun nonce(prefix: ByteArray, counter: Int, last: Boolean) =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(if (last) 1 else 0).array()

    /** Encrypts [input] to [output] with [password]. [checkpoint] runs before each segment (throw there to stop). */
    fun encrypt(input: InputStream, output: OutputStream, password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom(), checkpoint: () -> Unit = {}) {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(7).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(iterations).put(salt).put(prefix).array()
        val key = SecretKeySpec(pbkdf2(password, salt, iterations), "AES")
        output.write(header)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var cur = readFully(input, SEGMENT)
        var counter = 0
        while (true) {
            checkpoint()
            val next = if (cur.size == SEGMENT) readFully(input, SEGMENT) else ByteArray(0)
            val last = next.isEmpty()
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, counter, last)))
            cipher.updateAAD(header)
            output.write(cipher.doFinal(cur))
            if (last) break
            cur = next
            counter++
        }
        output.flush()
    }

    /**
     * Decrypts to [output]; throws [BackupAuthException] when the first segment fails (wrong password, or damage there:
     * indistinguishable) and [BackupFormatException] for a bad header or a later segment failing / missing (cut or damaged file). Segments are verified one
     * by one, so callers decrypt into a temp file and use it only after this returns.
     */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray, checkpoint: () -> Unit = {}) {
        val din = DataInputStream(input)
        val header = ByteArray(HEADER)
        try {
            din.readFully(header)
        } catch (_: EOFException) {
            throw BackupFormatException("too short")
        }
        if (!header.copyOfRange(0, 7).contentEquals(MAGIC)) throw BackupFormatException("not a backup file")
        if (header[7] != VERSION) throw BackupFormatException("unsupported version ${header[7]}")
        val bb = ByteBuffer.wrap(header, 8, HEADER - 8)
        val iterations = bb.int
        if (iterations !in 1..10_000_000) throw BackupFormatException("bad header")
        val salt = ByteArray(16).also { bb.get(it) }
        val prefix = ByteArray(7).also { bb.get(it) }
        val key = SecretKeySpec(pbkdf2(password, salt, iterations), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        var cur = readFully(din, SEGMENT + TAG)
        var counter = 0
        try {
            while (true) {
                checkpoint()
                if (cur.size < TAG) throw BackupFormatException("truncated")
                val next = if (cur.size == SEGMENT + TAG) readFully(din, SEGMENT + TAG) else ByteArray(0)
                val last = next.isEmpty()
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, counter, last)))
                cipher.updateAAD(header)
                output.write(cipher.doFinal(cur))
                if (last) break
                cur = next
                counter++
            }
        } catch (_: AEADBadTagException) {
            // the first segment is what a wrong password breaks; a later one failing means the key was right and the file is cut or damaged (QA R1-03)
            if (counter == 0) throw BackupAuthException() else throw BackupFormatException("damaged at segment $counter")
        }
        output.flush()
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArrayOutputStream(minOf(n, 1 shl 16))
        val tmp = ByteArray(64 * 1024)
        var left = n
        while (left > 0) {
            val r = input.read(tmp, 0, minOf(tmp.size, left))
            if (r < 0) break
            buf.write(tmp, 0, r)
            left -= r
        }
        return buf.toByteArray()
    }
}
