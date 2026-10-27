package com.ouor.mycoupon.backup

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import java.text.Normalizer
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
 * F15 file encryption: password key + AES-256-GCM in 1 MiB segments (the STREAM construction used by Tink's
 * AesGcmHkdfStreaming: nonce = random 7-byte prefix || segment counter || last-segment flag), so files of any size
 * stream through without holding the plaintext, and truncation or reordering fails. Every segment authenticates the
 * whole header as AAD.
 *
 * v2 (written): "MYCPNBK" | 2 | memoryKiB(int32) | passes(int32) | lanes(int8) | salt(16) | noncePrefix(7) | segments...
 *   key = Argon2id(NFC(password) as UTF-8, salt) from BouncyCastle (security review S-02; decision in docs/core/backup-kdf.md).
 * v1 (read only, 1.0 development files): "MYCPNBK" | 1 | iterations(int32) | salt(16) | noncePrefix(7) | segments...
 *   key = PBKDF2-HMAC-SHA256(password as UTF-8, salt, iterations).
 * Header parameters are bounded before the KDF runs (S-10): a crafted file cannot make it run for minutes or exhaust memory.
 */
object BackupCrypto {
    private val MAGIC = "MYCPNBK".toByteArray(Charsets.US_ASCII)
    const val VERSION: Byte = 2
    /** v1 PBKDF2 count (OWASP 2023); v1 files are accepted with 0.5x..4x of it */
    const val ITERATIONS = 600_000
    const val SEGMENT = 1 shl 20
    private const val TAG = 16
    private const val HEADER_V1 = 7 + 1 + 4 + 16 + 7
    private const val HEADER_V2 = 7 + 1 + 4 + 4 + 1 + 16 + 7

    /** Argon2id cost. Default: 64 MiB, 3 passes, 1 lane (above OWASP's 19 MiB / 2 passes minimum; timing in docs/core/backup-kdf.md). */
    data class Kdf(val memoryKiB: Int = 64 * 1024, val passes: Int = 3, val lanes: Int = 1)

    fun argon2id(password: CharArray, salt: ByteArray, kdf: Kdf = Kdf(), bytes: Int = 32): ByteArray {
        require(password.isNotEmpty()) { "empty password" }
        // S-11: Korean typed as decomposed jamo by some IMEs must give the same key as precomposed syllables
        val pw = Normalizer.normalize(CharBuffer.wrap(password), Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        val gen = Argon2BytesGenerator()
        gen.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(kdf.memoryKiB).withIterations(kdf.passes).withParallelism(kdf.lanes).withSalt(salt).build(),
        )
        val out = ByteArray(bytes)
        try {
            gen.generateBytes(pw, out)
        } finally {
            pw.fill(0)
        }
        return out
    }

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, bytes: Int = 32): ByteArray {
        require(password.isNotEmpty()) { "empty password" } // SecretKeySpec refuses empty keys; S14 requires a password
        val mac = Mac.getInstance("HmacSHA256")
        val pw = String(password).toByteArray(Charsets.UTF_8)
        mac.init(SecretKeySpec(pw, "HmacSHA256"))
        pw.fill(0)
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

    /** The raw key bytes are zeroed once the key object holds its own copy (S-11). */
    private fun secretKey(raw: ByteArray) = SecretKeySpec(raw, "AES").also { raw.fill(0) }

    /** Encrypts [input] to [output] with [password] (format v2). [checkpoint] runs before each segment (throw there to stop). */
    fun encrypt(input: InputStream, output: OutputStream, password: CharArray, kdf: Kdf = Kdf(), random: SecureRandom = SecureRandom(), checkpoint: () -> Unit = {}) {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(7).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_V2).put(MAGIC).put(VERSION).putInt(kdf.memoryKiB).putInt(kdf.passes).put(kdf.lanes.toByte())
            .put(salt).put(prefix).array()
        val key = secretKey(argon2id(password, salt, kdf))
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

    /** Test-only: a v1 (PBKDF2) file, to keep reading 1.0 development backups. */
    internal fun encryptV1(input: InputStream, output: OutputStream, password: CharArray, iterations: Int, random: SecureRandom = SecureRandom()) {
        val salt = ByteArray(16).also(random::nextBytes)
        val prefix = ByteArray(7).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_V1).put(MAGIC).put(1).putInt(iterations).put(salt).put(prefix).array()
        val key = secretKey(pbkdf2(password, salt, iterations))
        output.write(header)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG * 8, nonce(prefix, 0, true)))
        cipher.updateAAD(header)
        output.write(cipher.doFinal(input.readBytes()))
    }

    /**
     * Decrypts to [output]; throws [BackupAuthException] when the first segment fails (wrong password, or damage there:
     * indistinguishable) and [BackupFormatException] for a bad header or a later segment failing / missing (cut or damaged
     * file). Segments are verified one by one, so callers decrypt into a temp file and use it only after this returns.
     * [v1Iterations] widens the accepted v1 PBKDF2 range for tests only.
     */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray, checkpoint: () -> Unit = {}, v1Iterations: IntRange = ITERATIONS / 2..ITERATIONS * 4) {
        val din = DataInputStream(input)
        val start = ByteArray(8)
        try {
            din.readFully(start)
        } catch (_: EOFException) {
            throw BackupFormatException("too short")
        }
        if (!start.copyOfRange(0, 7).contentEquals(MAGIC)) throw BackupFormatException("not a backup file")
        val size = when (start[7].toInt()) {
            1 -> HEADER_V1
            2 -> HEADER_V2
            else -> throw BackupFormatException("unsupported version ${start[7]}")
        }
        val header = start.copyOf(size)
        try {
            din.readFully(header, 8, size - 8)
        } catch (_: EOFException) {
            throw BackupFormatException("too short")
        }
        val bb = ByteBuffer.wrap(header, 8, size - 8)
        val raw = if (size == HEADER_V1) {
            val iterations = bb.int
            if (iterations !in v1Iterations) throw BackupFormatException("bad header")
            pbkdf2(password, ByteArray(16).also { bb.get(it) }, iterations)
        } else {
            val kdf = Kdf(bb.int, bb.int, bb.get().toInt())
            if (kdf.memoryKiB !in 1024..256 * 1024 || kdf.passes !in 1..10 || kdf.lanes !in 1..4) throw BackupFormatException("bad header")
            checkpoint()
            argon2id(password, ByteArray(16).also { bb.get(it) }, kdf)
        }
        val prefix = ByteArray(7).also { bb.get(it) }
        val key = secretKey(raw)
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
