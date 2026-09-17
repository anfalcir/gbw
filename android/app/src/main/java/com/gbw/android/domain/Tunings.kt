package com.gbw.android.domain

object Tunings {
    val values: LinkedHashMap<String, IntArray> = linkedMapOf(
        "E Standard" to intArrayOf(40, 45, 50, 55, 59, 64),
        "Eb Standard" to intArrayOf(39, 44, 49, 54, 58, 63),
        "D Standard" to intArrayOf(38, 43, 48, 53, 57, 62),
        "C# Standard" to intArrayOf(37, 42, 47, 52, 56, 61),
        "C Standard" to intArrayOf(36, 41, 46, 51, 55, 60),
        "B Standard" to intArrayOf(35, 40, 45, 50, 54, 59),
        "Bb Standard" to intArrayOf(34, 39, 44, 49, 53, 58),
        "A Standard" to intArrayOf(33, 38, 43, 48, 52, 57),
        "Drop D" to intArrayOf(38, 45, 50, 55, 59, 64),
        "Drop C#" to intArrayOf(37, 44, 49, 54, 58, 63),
        "Drop C" to intArrayOf(36, 43, 48, 53, 57, 62),
        "Drop B" to intArrayOf(35, 42, 47, 52, 56, 61),
        "Drop Bb" to intArrayOf(34, 41, 46, 51, 55, 60),
        "Drop A" to intArrayOf(33, 40, 45, 50, 54, 59),
        "Drop Ab" to intArrayOf(32, 39, 44, 49, 53, 58),
        "Drop G" to intArrayOf(31, 38, 43, 48, 52, 57),
        "Drop F#" to intArrayOf(30, 37, 42, 47, 51, 56),
        "Drop F" to intArrayOf(29, 36, 41, 46, 50, 55),
    )

    val names: List<String> get() = values.keys.toList()

    fun delta(source: String, target: String): Int? {
        val a = values[source] ?: return null
        val b = values[target] ?: return null
        if (a.size != b.size) return null
        val diffs = a.indices.map { b[it] - a[it] }.toSet()
        return diffs.singleOrNull()
    }
}
