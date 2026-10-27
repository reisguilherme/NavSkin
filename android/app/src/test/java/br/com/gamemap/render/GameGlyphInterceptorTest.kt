package br.com.gamemap.render

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class GameGlyphInterceptorTest {
    private val alias = "NavSkinGtaDisplay"
    private val endpoint = "https://tiles.openfreemap.org/fonts/$alias/"

    @Test fun realHttpPipelineServesBundledBytesBeforeAnyNetworkConnection() {
        val expected = byteArrayOf(10, 12, 24, 32)
        val router = GameGlyphRouter(setOf(alias)) { it.endsWith("/0-255.pbf") }
        val client = OkHttpClient.Builder().addInterceptor(GameGlyphInterceptor(router) { expected })
            .addInterceptor { error("A local glyph must not reach a network interceptor") }.build()
        client.newCall(Request.Builder().url(endpoint + "0-255.pbf").build()).execute().use { response ->
            assertEquals(200, response.code)
            assertArrayEquals(expected, response.body!!.bytes())
            assertEquals("application/x-protobuf", response.header("Content-Type"))
        }
    }

    @Test fun missingCustomRangeUsesNotoButOtherResourcesRetainTheirExactUrl() {
        val router = GameGlyphRouter(setOf(alias)) { false }
        var requested = ""
        val client = OkHttpClient.Builder().addInterceptor(GameGlyphInterceptor(router) { error("No local asset exists") })
            .addInterceptor { chain ->
                requested = chain.request().url.toString()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture sink")
                    .body(byteArrayOf().toResponseBody()).build()
            }.build()
        client.newCall(Request.Builder().url(endpoint + "19968-20223.pbf").build()).execute().close()
        assertEquals("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/19968-20223.pbf", requested)
        val tile = "https://tiles.openfreemap.org/planet/14/6069/9295.pbf"
        client.newCall(Request.Builder().url(tile).build()).execute().close()
        assertEquals(tile, requested)
    }
}
