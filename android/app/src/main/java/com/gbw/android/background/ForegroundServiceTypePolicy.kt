package com.gbw.android.background

import android.annotation.SuppressLint
import android.content.pm.ServiceInfo

internal object ForegroundServiceTypePolicy {
    @SuppressLint("InlinedApi")
    fun typeForSdk(sdkInt: Int): Int =
        when {
            sdkInt >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            sdkInt >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        }

    fun labelForSdk(sdkInt: Int): String =
        when {
            sdkInt >= 35 -> "mediaProcessing"
            sdkInt >= 29 -> "dataSync"
            else -> "legacy"
        }

    fun userFacingFailureOrNull(error: Throwable): String? =
        when (error::class.java.simpleName) {
            "InvalidForegroundServiceTypeException" ->
                "O Android rejeitou o tipo do serviço em segundo plano."
            "MissingForegroundServiceTypeException" ->
                "O Android não encontrou um tipo válido para o serviço em segundo plano."
            "ForegroundServiceStartNotAllowedException" ->
                "O Android não permitiu iniciar o processamento em segundo plano neste estado."
            "SecurityException" ->
                "O Android bloqueou o serviço por permissão ou tipo incompatível."
            "IllegalArgumentException" ->
                "O Android rejeitou a configuração do serviço em segundo plano."
            else -> null
        }

    fun userFacingFailure(error: Throwable): String =
        userFacingFailureOrNull(error)
            ?: "Falha ao iniciar o processamento em segundo plano (" +
                error::class.java.simpleName + ")."

    fun diagnostic(
        error: Throwable,
        sdkInt: Int,
        requestedType: Int,
        declaredType: Int,
    ): String =
        "exception=${error::class.java.name}; " +
            "api=$sdkInt; " +
            "requested=${describeType(requestedType)}; " +
            "declared=${describeType(declaredType)}; " +
            "cause=${sanitizeTechnicalMessage(error.message)}"

    fun timeoutDiagnostic(sdkInt: Int, callbackType: Int, declaredType: Int): String =
        "exception=ForegroundServiceTimeout; " +
            "api=$sdkInt; " +
            "callbackType=${describeType(callbackType)}; " +
            "declared=${describeType(declaredType)}"

    @SuppressLint("InlinedApi")
    internal fun describeType(type: Int): String {
        if (type < 0) return "unknown"
        if (type == ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE) return "none(0)"

        val labels = mutableListOf<String>()
        var known = 0
        if ((type and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) != 0) {
            labels += "dataSync"
            known = known or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        if ((type and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING) != 0) {
            labels += "mediaProcessing"
            known = known or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        }
        val unknown = type and known.inv()
        if (unknown != 0) labels += "unknown=0x${unknown.toString(16)}"
        return "${labels.joinToString("|")}($type)"
    }

    internal fun sanitizeTechnicalMessage(value: String?): String =
        value.orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
            .ifBlank { "sem mensagem técnica" }
}
