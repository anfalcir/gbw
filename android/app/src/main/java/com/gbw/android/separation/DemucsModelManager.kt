package com.gbw.android.separation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

internal object DemucsModelManager {
    suspend fun ensureReady(
        context: Context,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val spec = DemucsModelContract.quick
        val modelDir = File(context.filesDir, "models/demucs").apply { mkdirs() }
        val modelFile = File(modelDir, spec.fileName)
        val partFile = File(modelDir, "${spec.fileName}.part")

        if (modelFile.isFile) {
            onProgress(0, "Verificando integridade do modelo htdemucs_6s…")
            if (verify(modelFile, spec)) {
                onProgress(100, "Modelo htdemucs_6s verificado.")
                return@withContext modelFile
            }
            modelFile.delete()
        }

        partFile.delete()
        onProgress(0, "Baixando modelo htdemucs_6s (54,9 MB)…")
        val connection = (URL(spec.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "GBW-Android/6.0")
        }

        try {
            connection.connect()
            val status = connection.responseCode
            require(status in 200..299) { "Falha ao baixar modelo Demucs: HTTP $status" }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0L) {
                require(declaredLength == spec.expectedBytes) {
                    "Tamanho remoto inesperado para htdemucs_6s: $declaredLength bytes"
                }
            }

            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(partFile).use { fileOutput ->
                    val output = BufferedOutputStream(fileOutput, BUFFER_SIZE)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    var lastPercent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read.toLong()
                        require(copied <= spec.expectedBytes) {
                            "Download do modelo excedeu o tamanho esperado"
                        }
                        val percent = ((copied * 100L) / spec.expectedBytes).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            onProgress(percent, "Baixando htdemucs_6s… $percent%")
                            lastPercent = percent
                        }
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }

            currentCoroutineContext().ensureActive()
            require(verify(partFile, spec)) {
                "Modelo htdemucs_6s falhou na verificação SHA-256/tamanho"
            }
            promoteAtomically(partFile, modelFile)
            require(verify(modelFile, spec)) { "Modelo htdemucs_6s corrompido após instalação" }
            onProgress(100, "Modelo htdemucs_6s instalado e verificado.")
            modelFile
        } catch (error: Throwable) {
            partFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    internal fun verify(file: File, spec: DemucsModelSpec = DemucsModelContract.quick): Boolean {
        if (!file.isFile || file.length() != spec.expectedBytes) return false
        return sha256(file).equals(spec.sha256, ignoreCase = true)
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun promoteAtomically(partFile: File, modelFile: File) {
        try {
            Files.move(
                partFile.toPath(),
                modelFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partFile.toPath(), modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private const val BUFFER_SIZE = 256 * 1024
}
