package com.gbw.android.separation

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BsRoformerModelManagerTest {
    @Test
    fun verificationRequiresExactSizeAndSha256() {
        val file = File.createTempFile("gbw-bsroformer", ".bin")
        try {
            val bytes = "exact-pte-contract".toByteArray()
            file.writeBytes(bytes)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val exact = BsRoformerModelSpec(
                id = "test",
                fileName = file.name,
                expectedBytes = bytes.size.toLong(),
                sha256 = digest,
                downloadUrl = null,
            )
            assertTrue(BsRoformerModelManager.verify(file, exact))
            assertFalse(BsRoformerModelManager.verify(
                file, exact.copy(expectedBytes = bytes.size.toLong() + 1L)
            ))
            assertFalse(BsRoformerModelManager.verify(
                file, exact.copy(sha256 = "0".repeat(64))
            ))
        } finally {
            file.delete()
        }
    }
}
