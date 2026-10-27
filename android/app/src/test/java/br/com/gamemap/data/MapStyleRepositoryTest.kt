package br.com.gamemap.data

import android.app.Application
import br.com.gamemap.preferences.*
import br.com.gamemap.render.poiVisibilityChanges
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MapStyleRepositoryTest {
    private val repository get() = MapStyleRepository(RuntimeEnvironment.getApplication())
    @Test fun disablingAnyCategoryChangesOnlyItsPoiLayerForEveryDayAndNightTheme() {
        for (theme in listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2)) {
            for (night in listOf(false, true)) {
                for (category in PoiCategory.entries) {
                    val before = JSONObject(repository.styleFor(MapPreferences(theme, baseMapMode = MapBaseMode.GOIANIA_OFFLINE), night))
                    val after = JSONObject(repository.styleFor(
                        MapPreferences(theme, PoiCategory.entries.toSet() - category, MapBaseMode.GOIANIA_OFFLINE), night))
                    assertEquals(before.getJSONObject("sources").toString(), after.getJSONObject("sources").toString())
                    val a = before.getJSONArray("layers")
                    val b = after.getJSONArray("layers")
                    for (i in 0 until a.length()) {
                        val original = a.getJSONObject(i)
                        val changed = b.getJSONObject(i)
                        if (original.getString("id") == "poi-${category.id}") {
                            assertEquals("none", changed.getJSONObject("layout").getString("visibility"))
                        } else assertEquals(original.toString(), changed.toString())
                    }
                }
            }
        }
    }
    @Test fun allThreeThemesContainExactlyTheSameSemanticGroups() {
        for (theme in listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2)) {
            val layers = JSONObject(repository.styleFor(MapPreferences(theme, baseMapMode = MapBaseMode.GOIANIA_OFFLINE))).getJSONArray("layers")
            val ids = (0 until layers.length()).map { layers.getJSONObject(it).getString("id") }.filter { it.startsWith("poi-") }
            assertEquals(PoiCategory.entries.map { "poi-${it.id}" }.toSet(), ids.toSet())
        }
    }
    @Test fun catalogHasRealCoverageForAllSixGroups() {
        val counts = repository.categoryCounts()
        assertEquals(1571, counts.values.sum())
        assertTrue(counts.values.all { it > 0 })
        assertEquals(94, counts[PoiCategory.SHOPS])
    }
    @Test fun nightAppearanceKeepsThemeIdentitySourcesAndFilters() {
        for (theme in MapTheme.entries) {
            val state = MapPreferences(theme, setOf(PoiCategory.FUEL, PoiCategory.HEALTH), MapBaseMode.GOIANIA_OFFLINE)
            val day = JSONObject(repository.styleFor(state))
            val night = JSONObject(repository.styleFor(state, true))
            assertEquals(day.getJSONObject("sources").toString(), night.getJSONObject("sources").toString())
            val dayLayers = day.getJSONArray("layers")
            val nightLayers = night.getJSONArray("layers")
            assertEquals(dayLayers.length(), nightLayers.length())
            for (index in 0 until dayLayers.length()) {
                val a = dayLayers.getJSONObject(index)
                val b = nightLayers.getJSONObject(index)
                assertEquals(a.getString("id"), b.getString("id"))
                if (a.getString("id").startsWith("poi-"))
                    assertEquals(a.getJSONObject("layout").getString("visibility"), b.getJSONObject("layout").getString("visibility"))
            }
        }
    }
    @Test fun worldUsesHttpsVectorTilesAtEveryThemeAndAppearanceWithoutOfflineGeometry() {
        for (theme in MapTheme.entries) for (night in listOf(false, true)) {
            val preferences = MapPreferences(theme, setOf(PoiCategory.PARKS), MapBaseMode.WORLD)
            val style = JSONObject(repository.styleFor(preferences, night))
            val sources = style.getJSONObject("sources")
            val vectors = sources.keys().asSequence().map { sources.getJSONObject(it) }
                .filter { it.getString("type") == "vector" }.toList()
            assertTrue("World style requires an online vector source", vectors.isNotEmpty())
            assertTrue(vectors.any { source ->
                source.optString("url").startsWith("https://tiles.openfreemap.org/") ||
                    source.optJSONArray("tiles")?.let { tiles ->
                        (0 until tiles.length()).any { tiles.getString(it).startsWith("https://tiles.openfreemap.org/") }
                    } == true
            })
            assertFalse(style.toString().contains("maps/data/"))
            val layers = style.getJSONArray("layers")
            val pois = (0 until layers.length()).map { layers.getJSONObject(it) }
                .filter { it.getString("id").startsWith("poi-") }
            assertEquals(PoiCategory.entries.map { "poi-${it.id}" }.toSet(), pois.map { it.getString("id") }.toSet())
            pois.forEach {
                assertEquals(if (it.getString("id") == "poi-parks") "visible" else "none",
                    it.getJSONObject("layout").getString("visibility"))
            }
        }
    }
    @Test fun worldFilterToggleKeepsNativeSourcesButCoverageChangeLoadsTheOtherMap() {
        val before = MapPreferences()
        val hidden = before.copy(enabledCategories = before.enabledCategories - PoiCategory.FUEL)
        val world = repository.styleFor(before)
        assertEquals(mapOf("poi-fuel" to "none"), poiVisibilityChanges(world, repository.styleFor(hidden)))
        assertNull(poiVisibilityChanges(world,
            repository.styleFor(before.copy(baseMapMode = MapBaseMode.GOIANIA_OFFLINE))))
    }
}
