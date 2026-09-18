package com.gbw.android.background

import android.content.pm.ServiceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundServiceTypePolicyTest {
    @Test
    fun android15PlusUsesMediaProcessing() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, ForegroundServiceTypePolicy.typeForSdk(35))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING, ForegroundServiceTypePolicy.typeForSdk(36))
    }

    @Test
    fun android10To14UsesDataSync() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(29))
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, ForegroundServiceTypePolicy.typeForSdk(34))
    }

    @Test
    fun android9UsesLegacyNone() {
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE, ForegroundServiceTypePolicy.typeForSdk(28))
    }
}
