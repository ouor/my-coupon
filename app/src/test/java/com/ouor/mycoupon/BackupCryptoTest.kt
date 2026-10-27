package com.ouor.mycoupon

import com.ouor.mycoupon.backup.BackupAuthException
import com.ouor.mycoupon.backup.BackupCrypto
import com.ouor.mycoupon.backup.BackupFormatException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

class BackupCryptoTest {
    private val pw = "비밀번호-123".toCharArray()

    @Test
    fun pbkdf2MatchesJdk() {
        val salt = Random(1).nextBytes(16)
        for (iter in listOf(1, 2, 1000)) {
            val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw, salt, iter, 256)).encoded
            assertArrayEquals(jdk, BackupCrypto.pbkdf2(pw, salt, iter))
        }
        // 64-byte output spans two blocks (RFC 7914 §11 vector, P="passwd", S="salt", c=1)
        val rfc = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        assertArrayEquals(rfc.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), BackupCrypto.pbkdf2("passwd".toCharArray(), "salt".toByteArray(), 1, 64))
    }

    private val cheap = BackupCrypto.Kdf(memoryKiB = 1024, passes = 1)
    private fun enc(data: ByteArray): ByteArray = ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(data), it, pw, cheap) }.toByteArray()
    private fun dec(file: ByteArray, p: CharArray = pw): ByteArray = ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(file), it, p) }.toByteArray()

    @Test
    fun roundTripAcrossSegments() {
        for (n in listOf(0, 1, BackupCrypto.SEGMENT - 1, BackupCrypto.SEGMENT, BackupCrypto.SEGMENT + 1, 3 * BackupCrypto.SEGMENT + 7)) {
            val data = Random(n).nextBytes(n)
            assertArrayEquals("size $n", data, dec(enc(data)))
        }
    }

    @Test
    fun plaintextIsNotVisible() {
        val secret = "1234567890123456 아메리카노".toByteArray()
        val f = enc(secret)
        assertFalse(String(f, Charsets.ISO_8859_1).contains("1234567890123456"))
    }

    @Test(expected = BackupAuthException::class)
    fun wrongPassword() { dec(enc(ByteArray(100)), "other".toCharArray()) }

    @Test(expected = BackupFormatException::class)
    fun truncatedAtSegmentBoundary() {
        val f = enc(Random(3).nextBytes(2 * BackupCrypto.SEGMENT + 10))
        dec(f.copyOf(f.size - (10 + 16))) // drops the last segment exactly: the previous one is not flagged last
    }

    /** One-segment file cut: the only segment fails, which a wrong password also does (reported as auth). */
    @Test(expected = BackupAuthException::class)
    fun truncatedInsideSingleSegment() {
        val f = enc(Random(4).nextBytes(5000))
        dec(f.copyOf(f.size / 2))
    }

    /** QA R1-03: a large backup cut in half with the right password is "damaged", not "wrong password". */
    @Test(expected = BackupFormatException::class)
    fun truncatedHalfMultiSegment() {
        val f = enc(Random(5).nextBytes(3 * BackupCrypto.SEGMENT + 123))
        dec(f.copyOf(f.size / 2))
    }

    @Test(expected = BackupAuthException::class)
    fun tamperedHeader() {
        val f = enc(ByteArray(10))
        f[10] = (f[10] + 1).toByte() // memory cost field (1024 -> 1280 KiB): different key and AAD
        dec(f)
    }

    /** phc-winner-argon2 reference vector (src/test.c): Argon2id v1.3, t=2, m=64 MiB, p=1, "password" / "somesalt". */
    @Test
    fun argon2idMatchesReference() {
        val got = BackupCrypto.argon2id("password".toCharArray(), "somesalt".toByteArray(), BackupCrypto.Kdf(65536, 2, 1))
        assertEquals("09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7", got.joinToString("") { "%02x".format(it) })
    }

    /** S-11: decomposed Hangul (NFD, some IMEs) opens a file made with precomposed text. */
    @Test
    fun passwordIsNfcNormalised() {
        val nfc = "한글암호".toCharArray()
        val nfd = java.text.Normalizer.normalize("한글암호", java.text.Normalizer.Form.NFD).toCharArray()
        val f = ByteArrayOutputStream().also { BackupCrypto.encrypt(ByteArrayInputStream(ByteArray(10)), it, nfc, cheap) }.toByteArray()
        assertArrayEquals(ByteArray(10), dec(f, nfd))
    }

    /** 1.0 development files (PBKDF2, format v1) still open. */
    @Test
    fun readsV1() {
        val data = Random(7).nextBytes(3000)
        val f = ByteArrayOutputStream().also { BackupCrypto.encryptV1(ByteArrayInputStream(data), it, pw, 1000) }.toByteArray()
        assertArrayEquals(data, ByteArrayOutputStream().also { BackupCrypto.decrypt(ByteArrayInputStream(f), it, pw, v1Iterations = 1..1000) }.toByteArray())
    }

    /** S-10: a crafted cost is refused before the KDF runs. */
    @Test(expected = BackupFormatException::class)
    fun hugeCostRefused() {
        val f = enc(ByteArray(10))
        f[8] = 0x7f // memoryKiB ~ 2 TiB
        dec(f)
    }

    @Test(expected = BackupFormatException::class)
    fun v1IterationsOutOfRangeRefused() {
        val f = ByteArrayOutputStream().also { BackupCrypto.encryptV1(ByteArrayInputStream(ByteArray(10)), it, pw, 1000) }.toByteArray()
        dec(f) // default range is 300k..2.4M
    }

    @Test(expected = BackupFormatException::class)
    fun notABackup() { dec("hello world, this is not a backup file at all".toByteArray()) }
}
