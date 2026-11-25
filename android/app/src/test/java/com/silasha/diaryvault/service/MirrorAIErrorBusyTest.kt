package com.silasha.diaryvault.service

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

    @Test
    fun fromHttp_401MapsToInvalidApiKeyNotGenericNetwork() {
        val err = MirrorAIError.fromHttp(401, """{"error":"Incorrect API key provided"}""")
        assertTrue(err is MirrorAIError.InvalidApiKey)
        assertFalse(err is MirrorAIError.Network)
        assertFalse(err is MirrorAIError.ProviderBusy)
    }

    @Test
    fun fromHttp_403MapsToInvalidApiKey() {
        assertTrue(MirrorAIError.fromHttp(403, "") is MirrorAIError.InvalidApiKey)
    }

    @Test
    fun fromHttp_invalidKeyBodyWithoutAuthStatus() {
        val err = MirrorAIError.fromHttp(400, """{"error":{"message":"Invalid API Key"}}""")
        assertTrue(err is MirrorAIError.InvalidApiKey)
    }

    @Test
    fun isInvalidApiKey_doesNotStealBusyMarkers() {
        assertFalse(MirrorAIError.isInvalidApiKey(429, "rate_limit"))
        assertTrue(MirrorAIError.isProviderBusy(429, "rate_limit"))
    }
}
