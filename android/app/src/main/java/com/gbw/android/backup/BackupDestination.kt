package com.gbw.android.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class BackupDestinationDescriptor(
    val label: String,
    val folderName: String,
    val providerName: String,
)

internal object BackupDestination {
    suspend fun describe(context: Context, treeUri: Uri): BackupDestinationDescriptor =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val documentId = DocumentsContract.getTreeDocumentId(treeUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val folderName = runCatching {
                resolver.query(
                    documentUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: Uri.decode(documentId).substringAfterLast('/').substringAfterLast(':')
                    .ifBlank { "Pasta selecionada" }

            val providerName = runCatching {
                val authority = treeUri.authority.orEmpty()
                context.packageManager.resolveContentProvider(authority, 0)
                    ?.loadLabel(context.packageManager)
                    ?.toString()
                    ?.trim()
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: treeUri.authority.orEmpty().substringAfterLast('.').ifBlank { "Armazenamento" }

            val label = if (
                providerName.equals(folderName, ignoreCase = true) ||
                providerName.contains(folderName, ignoreCase = true)
            ) {
                folderName
            } else {
                "$providerName • $folderName"
            }
            BackupDestinationDescriptor(label, folderName, providerName)
        }
}
