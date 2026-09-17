package com.gbw.android.domain

import java.text.Normalizer
import java.util.Locale

object TextNormalization {
    private val spaces = Regex("\\s+")

    fun titleCase(value: String): String {
        val clean = value.trim().replace(spaces, " ")
        if (clean.isBlank()) return ""
        return clean.split(' ').joinToString(" ") { word ->
            if (word.isBlank()) word else word.lowercase(Locale.ROOT).replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
        }
    }

    fun searchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(spaces, " ")
}
