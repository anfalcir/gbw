package com.gbw.android.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * Resolve a human-friendly label for an SAF audio Uri.
 *
 * Providers are authoritative: DISPLAY_NAME is preferred. The URI path is only
 * a defensive fallback because document IDs can be opaque implementation details.
 */
internal fun audioDisplayName(context: Context, uriText: String): String {
    if (uriText.isBlank()) return "Arquivo selecionado"
    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return "Arquivo selecionado"

    val providerName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()?.trim()

    if (!providerName.isNullOrBlank()) return providerName

    val decodedSegment = runCatching { Uri.decode(uri.lastPathSegment.orEmpty()) }.getOrDefault("")
    return fallbackAudioDisplayName(decodedSegment)
}

/**
 * Pure fallback kept testable on the JVM. Opaque/encoded document IDs are not
 * exposed to the user; a neutral label is safer than leaking provider internals.
 */
internal fun fallbackAudioDisplayName(rawSegment: String?): String {
    val raw = rawSegment?.trim().orEmpty()
    if (raw.isBlank()) return "Arquivo selecionado"

    val candidate = raw
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()

    if (candidate.isBlank()) return "Arquivo selecionado"

    val looksOpaque =
        candidate.length > 120 ||
            (!candidate.contains('.') && candidate.length > 64) ||
            (candidate.endsWith("==") && !candidate.contains('.'))

    return if (looksOpaque) "Arquivo selecionado" else candidate
}
