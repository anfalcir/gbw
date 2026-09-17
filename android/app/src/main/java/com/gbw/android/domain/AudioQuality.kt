package com.gbw.android.domain

enum class QualityStatus(val label: String) {
    IDEAL("Entrada ideal"),
    ADEQUATE("Entrada adequada"),
    CAUTION("Entrada com ressalvas")
}

data class QualityIssue(
    val title: String,
    val detail: String,
    val ideal: String,
    val risk: String,
)

data class AudioInspection(
    val displayName: String,
    val format: String,
    val codec: String = "",
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val bitDepth: Int? = null,
    val isFloat: Boolean = false,
    val isLossless: Boolean = false,
    val durationSeconds: Double? = null,
    val peakDbfs: Double? = null,
    val issues: List<QualityIssue> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    val status: QualityStatus
        get() = when {
            issues.isNotEmpty() -> QualityStatus.CAUTION
            format.equals("WAV", ignoreCase = true) && isFloat && bitDepth == 32 && (sampleRate ?: 0) >= 44_100 -> QualityStatus.IDEAL
            else -> QualityStatus.ADEQUATE
        }

    val channelLabel: String
        get() = when (channels) {
            1 -> "Mono"
            2 -> "Estéreo"
            null -> "canais não informados"
            else -> "$channels canais"
        }

    val depthLabel: String
        get() = when {
            bitDepth == null -> "profundidade não informada"
            isFloat -> "$bitDepth-bit float"
            else -> "$bitDepth-bit"
        }

    fun summary(): String = listOfNotNull(
        format.ifBlank { null },
        sampleRate?.let { if (it % 1000 == 0) "${it / 1000} kHz" else "${it / 1000.0} kHz" },
        depthLabel,
        channelLabel,
    ).joinToString(" • ")
}

object AudioQualityRules {
    fun classify(
        displayName: String,
        format: String,
        codec: String,
        sampleRate: Int?,
        channels: Int?,
        bitDepth: Int?,
        isFloat: Boolean,
        isLossless: Boolean,
        durationSeconds: Double? = null,
        peakDbfs: Double? = null,
    ): AudioInspection {
        val issues = mutableListOf<QualityIssue>()
        val notes = mutableListOf<String>()

        if (!isLossless) {
            issues += QualityIssue(
                title = "Formato com perdas",
                detail = "O formato/codec selecionado já pode ter descartado informação de áudio.",
                ideal = "Exporte novamente do DAW em WAV 32-bit float, mantendo a taxa da sessão.",
                risk = "A mudança de pitch pode tornar artefatos de compressão mais perceptíveis.",
            )
        }
        if (sampleRate != null && sampleRate < 44_100) {
            issues += QualityIssue(
                title = "Taxa de amostragem baixa",
                detail = "O arquivo está em ${sampleRate / 1000.0} kHz.",
                ideal = "Use a mesma taxa da sessão; 44,1 ou 48 kHz são os padrões mais comuns.",
                risk = "Há menos informação de alta frequência disponível para o processamento.",
            )
        }
        if (isLossless && bitDepth != null && !isFloat && bitDepth <= 16) {
            issues += QualityIssue(
                title = "Profundidade reduzida",
                detail = "O arquivo está em $bitDepth-bit inteiro.",
                ideal = "Prefira WAV 32-bit float ou WAV/FLAC 24-bit quando puder reexportar.",
                risk = "Há menos resolução e margem numérica para processamento adicional.",
            )
        }
        if (peakDbfs != null && peakDbfs >= -0.01) {
            issues += QualityIssue(
                title = "Picos no limite digital",
                detail = "O pico medido está em aproximadamente %.2f dBFS.".format(peakDbfs),
                ideal = "Evite normalizar ou limitar a exportação apenas para deixá-la mais alta.",
                risk = "Picos já limitados ou clipados não podem ser recuperados pelo pitch.",
            )
        } else if (peakDbfs != null && peakDbfs > -1.0) {
            notes += "Pico alto (%.2f dBFS), porém sem indicação suficiente para bloquear o processamento.".format(peakDbfs)
        }
        if (issues.isEmpty() && isLossless && !(format.equals("WAV", true) && isFloat && bitDepth == 32)) {
            notes += "Formato lossless adequado; não é necessário reexportar apenas para usar o GBW."
        }

        return AudioInspection(
            displayName = displayName,
            format = format,
            codec = codec,
            sampleRate = sampleRate,
            channels = channels,
            bitDepth = bitDepth,
            isFloat = isFloat,
            isLossless = isLossless,
            durationSeconds = durationSeconds,
            peakDbfs = peakDbfs,
            issues = issues,
            notes = notes,
        )
    }
}
