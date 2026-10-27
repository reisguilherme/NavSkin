package br.com.gamemap.render

import android.app.Application
import br.com.gamemap.preferences.PoiCategory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NavigationStyleVisibilityTest {
    private fun style() = JSONObject().put("version", 8).put("sources", JSONObject()
        .put("roads", JSONObject().put("type", "geojson").put("data", "asset://same-real-geometry")))
        .put("layers", JSONArray(PoiCategory.entries.map { category ->
            JSONObject().put("id", "poi-${category.id}").put("type", "symbol")
                .put("layout", JSONObject().put("visibility", "visible").put("icon-image", "same-icon"))
        }))

    @Test fun categoryToggleReturnsOnlyItsNativePropertyChange() {
        val before = style()
        val after = JSONObject(before.toString())
        after.getJSONArray("layers").getJSONObject(0).getJSONObject("layout").put("visibility", "none")
        assertEquals(mapOf("poi-parks" to "none"), poiVisibilityChanges(before.toString(), after.toString()))
    }

    @Test fun themeOrGeometryChangesMustReplaceTheBaseStyle() {
        val before = style()
        val after = JSONObject(before.toString())
        after.getJSONObject("sources").getJSONObject("roads").put("data", "asset://different-geometry")
        assertNull(poiVisibilityChanges(before.toString(), after.toString()))
        after.getJSONObject("sources").getJSONObject("roads").put("data", "asset://same-real-geometry")
        after.getJSONArray("layers").getJSONObject(0).getJSONObject("layout").put("icon-image", "different-theme")
        assertNull(poiVisibilityChanges(before.toString(), after.toString()))
    }

    @Test fun minimalProbeAndMalformedJsonKeepTheOriginalLoadPath() {
        assertNull(poiVisibilityChanges("{}", "{}"))
        assertNull(poiVisibilityChanges("not json", style().toString()))
    }
}
