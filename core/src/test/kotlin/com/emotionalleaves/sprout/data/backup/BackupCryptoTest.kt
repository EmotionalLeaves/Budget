package com.emotionalleaves.sprout.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class BackupCryptoTest {
    // Fewer iterations keep the tests fast; the format stores the count so both work.
    private val fast = 2_000
    private val pw = "correct horse".toCharArray()
    private val data = """{"transactions":[{"description":"STARBUCKS","amountMinor":-575}]}""".toByteArray()

    private fun expectFailure(message: String, block: () -> Unit) {
        try { block(); fail("expected failure") } catch (e: BackupException) { assertEquals(message, e.message) }
    }

    @Test fun roundTrip() = assertArrayEquals(data, BackupCrypto.decrypt(BackupCrypto.encrypt(data, pw, fast), pw))

    @Test fun ciphertextHidesContent() =
        assertFalse(String(BackupCrypto.encrypt(data, pw, fast), Charsets.ISO_8859_1).contains("STARBUCKS"))

    @Test fun sameDataEncryptsDifferentlyEachTime() =
        assertFalse(BackupCrypto.encrypt(data, pw, fast).contentEquals(BackupCrypto.encrypt(data, pw, fast)))

    @Test fun wrongPasswordFails() = expectFailure("Wrong password, or the file was changed.") {
        BackupCrypto.decrypt(BackupCrypto.encrypt(data, pw, fast), "wrong password".toCharArray())
    }

    @Test fun tamperedBodyFails() = expectFailure("Wrong password, or the file was changed.") {
        val f = BackupCrypto.encrypt(data, pw, fast)
        f[f.size - 5] = (f[f.size - 5].toInt() xor 1).toByte()
        BackupCrypto.decrypt(f, pw)
    }

    @Test fun tamperedHeaderFails() = expectFailure("Wrong password, or the file was changed.") {
        val f = BackupCrypto.encrypt(data, pw, fast)
        f[20] = (f[20].toInt() xor 1).toByte() // inside the salt
        BackupCrypto.decrypt(f, pw)
    }

    @Test fun notABackup() = expectFailure("This isn't a Sprout backup file.") { BackupCrypto.decrypt("Date,Amount\n".toByteArray(), pw) }

    @Test fun newerFormatIsRefused() = expectFailure("This backup was made by a newer version of Sprout. Update Sprout first.") {
        val f = BackupCrypto.encrypt(data, pw, fast)
        f[8] = 9
        BackupCrypto.decrypt(f, pw)
    }

    @Test fun shortPasswordIsRefused() = expectFailure("Use a password of at least 8 characters.") {
        BackupCrypto.encrypt(data, "short".toCharArray(), fast)
    }
}
