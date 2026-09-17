package com.gbw.android.separation

internal data class DemucsModelSpec(
    val id: String,
    val fileName: String,
    val sourceRevision: String,
    val downloadUrl: String,
    val expectedBytes: Long,
    val sha256: String,
)

internal object DemucsModelContract {
    const val RUNTIME_COMMIT = "f1206e9adeea103aef4a636b9e62297cf1f8e34e"
    const val SOURCE_REVISION = "5f5daffffcf06ad7b27a7285da327e18ea62068a"
    const val EXPECTED_BYTES = 54_855_129L
    const val SHA256 = "09704f4ceae204e56e77d5eefd6ac71d7275be81fd507e6913371d59abcee856"
    const val FILE_NAME = "ggml-model-htdemucs-6s-f16.bin"

    val quick = DemucsModelSpec(
        id = "htdemucs_6s",
        fileName = FILE_NAME,
        sourceRevision = SOURCE_REVISION,
        downloadUrl = "https://huggingface.co/datasets/Retrobear/demucs.cpp/resolve/" +
            SOURCE_REVISION + "/" + FILE_NAME + "?download=true",
        expectedBytes = EXPECTED_BYTES,
        sha256 = SHA256,
    )

    val stemNames = listOf("drums", "bass", "other", "vocals", "guitar", "piano")
}
