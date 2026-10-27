package br.com.gamemap.render

import org.junit.Assert.*
import org.junit.Test

class GameGlyphRouterTest {
    private val alias = "NavSkinGtaDisplay"
    private val endpoint = "https://tiles.openfreemap.org/fonts/$alias/"

    @Test fun bundledGlyphsUseAssetsAndMissingBlocksUseGlobalNoto() {
        val router = GameGlyphRouter(setOf(alias)) { it == "maps/glyphs/$alias/0-255.pbf" }
        assertEquals("asset://maps/glyphs/$alias/0-255.pbf", router.transform(4, endpoint + "0-255.pbf"))
        assertEquals("https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/19968-20223.pbf",
            router.transform(4, endpoint + "19968-20223.pbf"))
    }

    @Test fun unrelatedResourcesAndUndeclaredFontsAreNeverRewritten() {
        val router = GameGlyphRouter(setOf(alias)) { true }
        for (url in listOf("https://tiles.openfreemap.org/planet/14/6069/9295.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/0-255.pbf",
            "https://api.geoapify.com/v1/routing?apiKey=fixture",
            "https://other.example/fonts/$alias/0-255.pbf", "asset://maps/glyphs/$alias/0-255.pbf")) {
            assertEquals(url, router.transform(4, url))
        }
        assertEquals(endpoint + "0-255.pbf", router.transform(3, endpoint + "0-255.pbf"))
    }

    @Test fun unsafeOrInvalidRequestsCannotBecomeLocalPaths() {
        val router = GameGlyphRouter(setOf(alias)) { error("Invalid requests must not probe assets") }
        for (tail in listOf("../0-255.pbf", "0-254.pbf", "1-256.pbf", "-256--1.pbf",
            "9999999999999999-255.pbf", "1114112-1114367.pbf", "0-255.pbf?token=x", "0-255.pbf#x")) {
            assertEquals(endpoint + tail, router.transform(4, endpoint + tail))
        }
        val credentialed = "https://user@tiles.openfreemap.org/fonts/$alias/0-255.pbf"
        assertEquals(credentialed, router.transform(4, credentialed))
    }
}
