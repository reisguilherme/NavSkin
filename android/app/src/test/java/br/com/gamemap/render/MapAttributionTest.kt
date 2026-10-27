package br.com.gamemap.render

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MapAttributionTest {
    @Test fun worldAttributionRetainsAllProvidersWithoutHtmlOnCarSurface() {
        val style = JSONObject().put("metadata", JSONObject().put("navskin", JSONObject().put("attribution",
            "<a href=\"https://openfreemap.org/\">OpenFreeMap</a> · © OpenMapTiles · © OpenStreetMap contributors")))
        val text = mapAttributionText(style.toString(), withNavigationProvider = true)
        assertEquals("OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors · ODbL · Geoapify", text)
        assertFalse(text.contains("<a"))
    }

    @Test fun legacyOfflineAndSourceAttributionRemainAvailable() {
        val offline = JSONObject().put("metadata", JSONObject().put("gamemap", JSONObject()
            .put("attribution", "© OpenStreetMap · Overture · ODbL")))
        assertEquals("© OpenStreetMap · Overture · ODbL", mapAttributionText(offline.toString(), false))
        val source = JSONObject().put("sources", JSONObject().put("world", JSONObject()
            .put("attribution", "OpenFreeMap · © OpenMapTiles · © OpenStreetMap")))
        assertTrue(mapAttributionText(source.toString(), false).contains("OpenFreeMap · © OpenMapTiles"))
        assertEquals("© OpenStreetMap contributors · ODbL", mapAttributionText("invalid", false))
    }
}
