package com.gbw.android.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal data class StagedAudioInput(
    val file: File,
    val bytes: Long,
)

internal object SafAudioStager {
    suspend fun stage(
        context: Context,
        uri: Uri,
        directory: File,
        baseName: String = "source-input",
    ): StagedAudioInput = withContext(Dispatchers.IO) {
        require(directory.isDirectory || directory.mkdirs()) {
            "Não foi possível criar a área temporária do áudio."
        }

        val displayName = displayName(context, uri)
        val extension = safeExtension(displayName)
        val destination = File(directory, "$baseName.$extension")
        destination.delete()

        try {
            val resolver = context.contentResolver
            val input =
                resolver.openInputStream(uri)
                    ?: throw IllegalStateException(
                        "O Android não conseguiu abrir o áudio selecionado."
                    )

            var copied = 0L
            input.buffered(BUFFER_SIZE).use { source ->
                FileOutputStream(destination).use { fileOutput ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        fileOutput.write(buffer, 0, read)
                        copied += read.toLong()
                    }
                    fileOutput.flush()
                    fileOutput.fd.sync()
                }
            }

            require(copied > 0L && destination.isFile && destination.length() == copied) {
                "A cópia privada do áudio ficou vazia ou incompleta."
            }
            StagedAudioInput(destination, copied)
        } catch (cancelled: CancellationException) {
            destination.delete()
            throw cancelled
        } catch (error: Throwable) {
            destination.delete()
            throw IllegalStateException(
                "Falha ao copiar o áudio selecionado para a área privada do GBW " +
                    "(${error::class.java.simpleName}).",
                error,
            )
        }
    }

    internal fun safeExtension(displayName: String?): String {
        val candidate =
            displayName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        return candidate ?: "bin"
    }

    private fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

    private const val BUFFER_SIZE = 256 * 1024
}
