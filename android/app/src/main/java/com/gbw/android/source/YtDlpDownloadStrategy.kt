package com.gbw.android.source

import com.gbw.android.domain.SourceProvider

internal data class YtDlpDownloadAttempt(
    val name: String,
    val format: String,
    val extractorArgs: String? = null,
    val forceRuntimeUpdateBefore: Boolean = false,
)

internal object YtDlpDownloadStrategy {
    fun attempts(provider: SourceProvider, freshFormatId: String): List<YtDlpDownloadAttempt> {
        if (provider != SourceProvider.YOUTUBE) {
            return listOf(
                YtDlpDownloadAttempt(
                    name = "provider-default",
                    format = freshFormatId.ifBlank { "bestaudio/best" },
                )
            )
        }
        return listOf(
            YtDlpDownloadAttempt(
                name = "youtube-fresh-default",
                format = freshFormatId.ifBlank { "bestaudio/best" },
            ),
            YtDlpDownloadAttempt(
                name = "youtube-updated-android-vr",
                format = "bestaudio/best",
                extractorArgs = "youtube:player_client=android_vr",
                forceRuntimeUpdateBefore = true,
            ),
            YtDlpDownloadAttempt(
                name = "youtube-updated-web-embedded",
                format = "bestaudio[protocol^=http]/bestaudio/best",
                extractorArgs = "youtube:player_client=web_embedded",
            ),
        )
    }

    fun retryable(error: Throwable): Boolean {
        val text = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return listOf(
            "403",
            "forbidden",
            "unable to download video data",
            "po token",
            "sabr",
            "signature",
            "requested format is not available",
        ).any { it in text }
    }
}
