package com.gbw.android.separation

import android.content.Context
import android.net.Uri
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

internal data class BsRoformerModelSpec(
    val id: String,
    val fileName: String,
    val expectedBytes: Long,
    val sha256: String,
    val downloadUrl: String?,
)

internal object BsRoformerModelManager {
    fun candidateFile(context: Context): File =
        File(File(context.filesDir, "models/bs-roformer"), BsRoformerContract.PTE_FILE_NAME)

    fun candidateLooksInstalled(context: Context): Boolean {
        val file = candidateFile(context)
        return file.isFile && file.length() == BsRoformerContract.PTE_BYTES
    }

    suspend fun ensureReady(
        context: Context,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val spec = BsRoformerContract.productionPte
        val modelDir = File(context.filesDir, "models/bs-roformer").apply { mkdirs() }
        val modelFile = File(modelDir, spec.fileName)
        val partFile = File(modelDir, "${spec.fileName}.part")

        if (modelFile.isFile) {
            onProgress(0, "Verificando integridade do BS-RoFormer-SW…")
            if (verify(modelFile, spec)) {
                onProgress(100, "BS-RoFormer-SW verificado.")
                return@withContext modelFile
            }
            modelFile.delete()
        }

        val url = spec.downloadUrl
        if (url.isNullOrBlank()) {
            throw IllegalStateException(
                "O PTE BS-RoFormer-SW ainda não possui URL pública de redistribuição " +
                    "aprovada. Importe o artifact exato produzido pela CI antes de iniciar."
            )
        }
        downloadVerified(url, spec, partFile, modelFile, onProgress)
    }

    suspend fun installFromUri(
        context: Context,
        sourceUri: Uri,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val spec = BsRoformerContract.productionPte
        val modelDir = File(context.filesDir, "models/bs-roformer").apply { mkdirs() }
        val modelFile = File(modelDir, spec.fileName)
        val partFile = File(modelDir, "${spec.fileName}.part")
        partFile.delete()

        context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { descriptor ->
            val declared = descriptor.length
            if (declared >= 0L) {
                require(declared == spec.expectedBytes) {
                    "Tamanho inesperado do PTE: $declared bytes; esperado ${spec.expectedBytes}"
                }
            }
        }

        onProgress(0, "Importando PTE BS-RoFormer-SW…")
        try {
            val raw = context.contentResolver.openInputStream(sourceUri)
                ?: error("Não foi possível abrir o PTE selecionado pelo SAF.")
            BufferedInputStream(raw, BUFFER_SIZE).use { input ->
                FileOutputStream(partFile).use { fileOutput ->
                    val output = BufferedOutputStream(fileOutput, BUFFER_SIZE)
                    copyBounded(input, output, spec) { copied ->
                        val percent = ((copied * 100L) / spec.expectedBytes)
                            .toInt().coerceIn(0, 100)
                        onProgress(percent, "Importando PTE BS-RoFormer-SW… $percent%")
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            require(verify(partFile, spec)) {
                "O PTE selecionado não corresponde ao artifact autoritativo (bytes/SHA-256)."
            }
            promoteAtomically(partFile, modelFile)
            require(verify(modelFile, spec)) {
                "PTE BS-RoFormer-SW corrompido após promoção atômica."
            }
            onProgress(100, "PTE BS-RoFormer-SW instalado e verificado.")
            modelFile
        } catch (error: Throwable) {
            partFile.delete()
            throw error
        }
    }

    internal fun verify(
        file: File,
        spec: BsRoformerModelSpec = BsRoformerContract.productionPte,
    ): Boolean {
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
        return digest.digest().joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private suspend fun downloadVerified(
        url: String,
        spec: BsRoformerModelSpec,
        partFile: File,
        modelFile: File,
        onProgress: (Int, String) -> Unit,
    ): File {
        partFile.delete()
        onProgress(0, "Baixando PTE BS-RoFormer-SW…")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
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
            require(status in 200..299) {
                "Falha ao baixar PTE BS-RoFormer-SW: HTTP $status"
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0L) {
                require(declaredLength == spec.expectedBytes) {
                    "Tamanho remoto inesperado do PTE: $declaredLength bytes"
                }
            }
            BufferedInputStream(connection.inputStream, BUFFER_SIZE).use { input ->
                FileOutputStream(partFile).use { fileOutput ->
                    val output = BufferedOutputStream(fileOutput, BUFFER_SIZE)
                    copyBounded(input, output, spec) { copied ->
                        val percent = ((copied * 100L) / spec.expectedBytes)
                            .toInt().coerceIn(0, 100)
                        onProgress(percent, "Baixando PTE BS-RoFormer-SW… $percent%")
                    }
                    output.flush()
                    fileOutput.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            require(verify(partFile, spec)) {
                "PTE BS-RoFormer-SW falhou na verificação SHA-256/tamanho"
            }
            promoteAtomically(partFile, modelFile)
            require(verify(modelFile, spec)) {
                "PTE BS-RoFormer-SW corrompido após instalação"
            }
            onProgress(100, "PTE BS-RoFormer-SW instalado e verificado.")
            return modelFile
        } catch (error: Throwable) {
            partFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun copyBounded(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        spec: BsRoformerModelSpec,
        onCopied: (Long) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        var lastPercent = -1
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            copied += read.toLong()
            require(copied <= spec.expectedBytes) {
                "PTE BS-RoFormer-SW excedeu o tamanho esperado."
            }
            val percent = ((copied * 100L) / spec.expectedBytes).toInt()
            if (percent != lastPercent) {
                onCopied(copied)
                lastPercent = percent
            }
        }
        require(copied == spec.expectedBytes) {
            "PTE BS-RoFormer-SW truncado: $copied bytes; esperado ${spec.expectedBytes}"
        }
        onCopied(copied)
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
            Files.move(
                partFile.toPath(),
                modelFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private const val BUFFER_SIZE = 256 * 1024
}
