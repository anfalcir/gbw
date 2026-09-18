package com.gbw.android.separation

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

internal data class StemExportSummary(val fileNames: List<String>, val totalBytes: Long)

internal object SeparationStemExporter {
    suspend fun exportAll(
        context: Context,
        result: ValidatedSeparationResult,
        treeUri: Uri,
        onProgress: (completed: Int, total: Int, name: String) -> Unit,
    ): StemExportSummary = withContext(Dispatchers.IO) {
        val validated = requireNotNull(
            SeparationResultFiles.validate(context.filesDir, result.record)
        ) { "Os stems locais não estão mais íntegros; execute a separação novamente." }
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)
        val mode = if (validated.record.type == "separation-quick") "Quick" else "HQ"
        val tag = validated.record.jobId.filter { it.isLetterOrDigit() }.take(8).ifBlank { "result" }
        val created = mutableListOf<Uri>()
        val names = mutableListOf<String>()
        var totalBytes = 0L

        try {
            validated.stems.forEachIndexed { index, stem ->
                val name = "GBW_" + mode + "_" + tag + "_" + stem.name + ".wav"
                val target = requireNotNull(
                    DocumentsContract.createDocument(resolver, root, "audio/wav", name)
                ) { "A pasta escolhida não permitiu criar " + name }
                created += target
                var copied = 0L
                FileInputStream(stem.file).use { input ->
                    requireNotNull(resolver.openOutputStream(target, "w")).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read.toLong()
                        }
                        output.flush()
                    }
                }
                check(copied == stem.file.length()) {
                    "Exportação truncada de " + name + ": " + copied + " / " +
                        stem.file.length() + " bytes"
                }
                totalBytes += copied
                names += name
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, validated.stems.size, stem.name)
                }
            }
            StemExportSummary(names, totalBytes)
        } catch (error: Exception) {
            created.asReversed().forEach { uri ->
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
            throw error
        }
    }

    private const val COPY_BUFFER_BYTES = 1024 * 1024
}
