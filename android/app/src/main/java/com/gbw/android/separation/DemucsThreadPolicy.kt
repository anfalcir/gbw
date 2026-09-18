package com.gbw.android.separation

/**
 * Internal-only Demucs/OpenBLAS policy. Chunk-level concurrency stays at one;
 * this controls only BLAS workers inside the active model inference.
 */
internal object DemucsThreadPolicy {
    const val DEFAULT_THREADS = 1
    const val OVERRIDE_PROPERTY = "gbw.demucs.blasThreads"
    val supportedThreadCounts: Set<Int> = setOf(1, 2, 4)

    fun resolve(overrideValue: String? = System.getProperty(OVERRIDE_PROPERTY)): Int {
        val requested = overrideValue?.trim()?.toIntOrNull()
        return requested?.takeIf { it in supportedThreadCounts } ?: DEFAULT_THREADS
    }
}
