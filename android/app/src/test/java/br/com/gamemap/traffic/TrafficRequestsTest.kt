package br.com.gamemap.traffic

import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class TrafficRequestsTest {
    @Test fun incidentRequestKeepsSecretOutOfUrlAndUsesLonLatOrder() {
        val request = TomTomTrafficRequests.incidents(TrafficBounds(-17.0, -50.0, -16.0, -49.0), "synthetic-token")
        assertEquals("-50.0,-17.0,-49.0,-16.0", request.url.queryParameter("bbox"))
        assertEquals("2", request.url.queryParameter("apiVersion"))
        assertEquals("present", request.url.queryParameter("timeValidity"))
        assertEquals("synthetic-token", request.header("TomTom-Api-Key"))
        assertFalse(request.url.toString().contains("synthetic-token"))
        assertNotNull(request.header("Attributes"))
    }

    @Test fun tileAuthenticationIsRestrictedToOfficialHttpsTrafficHostAndToggle() {
        val tile = Request.Builder().url("https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/12/4/8?apiVersion=2").build()
        var enabled = false
        val interceptor = TrafficTileInterceptor({ "synthetic-token" }, { enabled })
        assertNull(interceptor.authorizedRequest(tile))
        enabled = true
        assertEquals("synthetic-token", interceptor.authorizedRequest(tile)?.header("TomTom-Api-Key"))
        for (url in listOf("https://example.org/tile", "https://api.tomtom.com.attacker.test/maps/orbis/traffic/flow/raster/tile/12/4/8",
            "http://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/12/4/8")) {
            assertNull(interceptor.authorizedRequest(Request.Builder().url(url).build())?.header("TomTom-Api-Key"))
        }
    }

    @Test fun disabledApplicationGuardNeverStartsANetworkRequest() {
        val client = OkHttpClient.Builder().addInterceptor(TrafficTileInterceptor({ "synthetic-token" }, { false }))
            .addInterceptor { error("Disabled traffic must not reach transport") }.build()
        val request = Request.Builder().url("https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/12/4/8").build()
        client.newCall(request).execute().use { assertEquals(204, it.code) }
    }

    @Test fun redirectGuardStripsSecretAndAlwaysProceedsIncludingDisabledState() {
        for ((url, enabled, expectedKey) in listOf(
            Triple("https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/12/4/8", true, "synthetic-token"),
            Triple("https://example.org/redirected", true, null),
            Triple("https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/12/4/8", false, null),
        )) {
            var forwarded: Request? = null
            var count = 0
            // Exercise the interceptor against a real OkHttp chain with a terminal fixture transport.
            val client = OkHttpClient.Builder()
                .addInterceptor(TrafficTileAuthenticationInterceptor({ "synthetic-token" }, { enabled }))
                .addInterceptor { chain ->
                    count++
                    forwarded = chain.request()
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                        .message("synthetic transport").body(ByteArray(0).toResponseBody()).build()
                }.build()
            client.newCall(Request.Builder().url(url).header("TomTom-Api-Key", "previous-hop-secret").build()).execute().close()
            assertEquals(1, count)
            assertEquals(expectedKey, forwarded!!.header("TomTom-Api-Key"))
        }
    }
}
