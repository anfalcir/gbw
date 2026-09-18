package com.gbw.android.background

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundServiceTypePolicyTest {
    @Test
    fun android10AndNewerUsesDataSync() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(29))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(34))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(35))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(36))
    }

    @Test
    fun android9UsesLegacyNone() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, ForegroundServiceTypePolicy.typeForSdk(28))
    }

    @Test
    fun labelMatchesPolicy() {
        assertEquals("legacy", ForegroundServiceTypePolicy.labelForSdk(28))
        assertEquals("dataSync", ForegroundServiceTypePolicy.labelForSdk(29))
        assertEquals("dataSync", ForegroundServiceTypePolicy.labelForSdk(36))
    }
}
