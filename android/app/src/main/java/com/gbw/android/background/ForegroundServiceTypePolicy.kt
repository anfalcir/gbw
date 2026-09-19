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

    fun userFacingFailure(error: Exception): String =
        when (error::class.java.simpleName) {
            "InvalidForegroundServiceTypeException" ->
                "O Android rejeitou o tipo do serviço em segundo plano."
            "MissingForegroundServiceTypeException" ->
                "O Android não encontrou um tipo válido para o serviço em segundo plano."
            "ForegroundServiceStartNotAllowedException" ->
                "O Android não permitiu iniciar o processamento em segundo plano neste estado."
            "SecurityException" ->
                "O Android bloqueou o serviço por permissão ou tipo incompatível."
            else ->
                "Falha ao iniciar o processamento em segundo plano (" +
                    error::class.java.simpleName + ")."
        }
}
