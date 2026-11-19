package com.diaryvault.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MirrorAIErrorBusyTest {
    @Test
    fun fromHttp_mapsResourceExhaustedJsonToProviderBusy() {
        val body =
            """{"code":"resource_exhausted","error":"The model is currently overloaded. Please try again later."}"""
        val err = MirrorAIError.fromHttp(429, body)
        assertTrue(err is MirrorAIError.ProviderBusy)
    }

    @Test
    fun fromHttp_mapsHyphenatedResourceExhausted() {
        val body = """{"error":{"code":"resource-exhausted","message":"capacity"}}"""
        val err = MirrorAIError.fromHttp(500, body)
        assertTrue(err is MirrorAIError.ProviderBusy)
    }

    @Test
    fun fromHttp_503IsBusyWithoutBodyScan() {
        assertTrue(MirrorAIError.fromHttp(503, "") is MirrorAIError.ProviderBusy)
    }

    @Test
    fun fromHttp_generic500IsOpaqueNetwork() {
        val body = """{"error":"internal server error","trace":"abc123"}"""
        val err = MirrorAIError.fromHttp(500, body)
        assertTrue(err is MirrorAIError.Network)
        assertEquals("HTTP 500", err.message)
        assertFalse(err.message!!.contains("trace"))
        assertFalse(err.message!!.contains("abc123"))
    }

    @Test
    fun isProviderBusy_capacityMarker() {
        assertTrue(MirrorAIError.isProviderBusy(400, "provider capacity exceeded"))
        assertFalse(MirrorAIError.isProviderBusy(400, "invalid api key"))
    }
}
