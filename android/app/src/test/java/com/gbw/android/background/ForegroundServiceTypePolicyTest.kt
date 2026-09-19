package com.gbw.android.background

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceTypePolicyTest {
    @Test
    fun android10Through14UsesDataSync() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(29))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(34))
    }

    @Test
    fun android15AndNewerUsesMediaProcessing() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, ForegroundServiceTypePolicy.typeForSdk(35))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, ForegroundServiceTypePolicy.typeForSdk(36))
    }

    @Test
    fun android9UsesLegacyNone() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, ForegroundServiceTypePolicy.typeForSdk(28))
    }

    @Test
    fun labelMatchesPolicy() {
        assertEquals("legacy", ForegroundServiceTypePolicy.labelForSdk(28))
        assertEquals("dataSync", ForegroundServiceTypePolicy.labelForSdk(29))
        assertEquals("mediaProcessing", ForegroundServiceTypePolicy.labelForSdk(36))
    }

    @Test
    fun diagnosticContainsSanitizedRuntimeFacts() {
        val error = InvalidForegroundServiceTypeException("bad\n type   value")
        val declared =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        val diagnostic = ForegroundServiceTypePolicy.diagnostic(
            error = error,
            sdkInt = 36,
            requestedType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING,
            declaredType = declared,
        )

        assertTrue(diagnostic.contains("InvalidForegroundServiceTypeException"))
        assertTrue(diagnostic.contains("api=36"))
        assertTrue(diagnostic.contains("requested=mediaProcessing(8192)"))
        assertTrue(diagnostic.contains("declared=dataSync|mediaProcessing(8193)"))
        assertTrue(diagnostic.contains("cause=bad type value"))
        assertEquals(
            "O Android rejeitou o tipo do serviço em segundo plano.",
            ForegroundServiceTypePolicy.userFacingFailure(error),
        )
    }

    @Test
    fun knownForegroundFailuresHaveSpecificFriendlyMessages() {
        assertEquals(
            "O Android não encontrou um tipo válido para o serviço em segundo plano.",
            ForegroundServiceTypePolicy.userFacingFailure(MissingForegroundServiceTypeException("missing")),
        )
        assertEquals(
            "O Android não permitiu iniciar o processamento em segundo plano neste estado.",
            ForegroundServiceTypePolicy.userFacingFailure(ForegroundServiceStartNotAllowedException("quota")),
        )
        assertEquals(
            "O Android bloqueou o serviço por permissão ou tipo incompatível.",
            ForegroundServiceTypePolicy.userFacingFailure(SecurityException("permission")),
        )
        assertEquals(
            null,
            ForegroundServiceTypePolicy.userFacingFailureOrNull(IllegalStateException("other")),
        )
    }

    @Test
    fun illegalArgumentHasSpecificFriendlyMessage() {
        assertEquals(
            "O Android rejeitou a configuração do serviço em segundo plano.",
            ForegroundServiceTypePolicy.userFacingFailure(IllegalArgumentException("bad flags")),
        )
    }

    private class InvalidForegroundServiceTypeException(message: String) : Exception(message)
    private class MissingForegroundServiceTypeException(message: String) : Exception(message)
    private class ForegroundServiceStartNotAllowedException(message: String) : Exception(message)
}
