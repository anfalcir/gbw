package com.gbw.android.project

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object ProjectHashing {
    fun sha256(file: File, bufferBytes: Int = 1024 * 1024): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).buffered(bufferBytes).use { input ->
            val buffer = ByteArray(bufferBytes)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

    fun revisionId(project: ProjectManifest): String {
        val digest = sha256(project.canonicalRevisionState()).take(20)
        return "r_${project.updatedAtEpochMs}_$digest"
    }
}
