package br.com.gamemap.preferences

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class PreferenceStoreTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(PreferenceStore.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun firstRunHasWorldGtaAndNineCategories() {
        PreferenceStore(context).use { store ->
            assertEquals(MapBaseMode.WORLD, store.snapshot().baseMapMode)
            assertEquals(MapTheme.GTA_SA, store.snapshot().theme)
            assertEquals(PoiCategory.entries.toSet(), store.snapshot().enabledCategories)
            assertFalse(store.snapshot().browse3D)
            assertTrue(store.snapshot().navigation3D)
        }
    }
    @Test fun dimensionPreferencesRemainIndependentAcrossThemesAndPhoneCarStores() {
        PreferenceStore(context).use { phone ->
            PreferenceStore(context).use { car ->
                phone.setMap3D(navigationActive = false, enabled = true)
                car.setMap3D(navigationActive = true, enabled = false)
                for (theme in MapTheme.entries) {
                    phone.setTheme(theme)
                    assertTrue(car.snapshot().browse3D)
                    assertFalse(car.snapshot().navigation3D)
                }
            }
        }
        PreferenceStore(context).use {
            assertTrue(it.snapshot().browse3D)
            assertFalse(it.snapshot().navigation3D)
        }
    }

    @Test fun dimensionChangesNotifyVisibleDisplays() {
        PreferenceStore(context).use { store ->
            val changes = mutableListOf<Pair<Boolean, Boolean>>()
            val remove = store.addListener { changes += it.browse3D to it.navigation3D }
            store.setMap3D(navigationActive = true, enabled = false)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            store.setMap3D(navigationActive = false, enabled = true)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(false to true, false to false, true to false), changes)
            remove()
        }
    }
    @Test fun coverageSurvivesReopeningAndIsSharedWithPhoneAndCar() {
        PreferenceStore(context).use { phone ->
            PreferenceStore(context).use { car ->
                phone.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
                assertEquals(MapBaseMode.GOIANIA_OFFLINE, car.snapshot().baseMapMode)
                car.setTheme(MapTheme.ROSA_POP)
                car.setCategoryEnabled(PoiCategory.FUEL, false)
            }
        }
        PreferenceStore(context).use {
            assertEquals(MapBaseMode.GOIANIA_OFFLINE, it.snapshot().baseMapMode)
            assertEquals(MapTheme.ROSA_POP, it.snapshot().theme)
            assertFalse(PoiCategory.FUEL in it.snapshot().enabledCategories)
            it.setBaseMapMode(MapBaseMode.WORLD)
        }
        PreferenceStore(context).use { assertEquals(MapBaseMode.WORLD, it.snapshot().baseMapMode) }
    }
    @Test fun existingInstallRetainsItsThemeAndFiltersAndStartsWithWorldCoverage() {
        context.getSharedPreferences(PreferenceStore.FILE_NAME, Context.MODE_PRIVATE).edit()
            .putString("map_theme", "rdr2").putStringSet("poi_categories", setOf("health")).commit()
        PreferenceStore(context).use {
            assertEquals(MapBaseMode.WORLD, it.snapshot().baseMapMode)
            assertEquals(MapTheme.RDR2, it.snapshot().theme)
            assertEquals(setOf(PoiCategory.HEALTH, PoiCategory.EDUCATION, PoiCategory.CULTURE, PoiCategory.RELIGION), it.snapshot().enabledCategories)
            it.setCategoryEnabled(PoiCategory.EDUCATION, false)
        }
        PreferenceStore(context).use { assertFalse(PoiCategory.EDUCATION in it.snapshot().enabledCategories) }
        assertEquals(MapBaseMode.WORLD, MapBaseMode.fromId("unknown-future-mode"))
    }
    @Test fun legacyExplicitAllOffRemainsOffWhenNewGroupsArrive() {
        context.getSharedPreferences(PreferenceStore.FILE_NAME, Context.MODE_PRIVATE).edit()
            .putStringSet("poi_categories", emptySet()).commit()
        PreferenceStore(context).use { assertTrue(it.snapshot().enabledCategories.isEmpty()) }
    }
    @Test fun coverageChangeNotifiesVisiblePhoneAndCarObservers() {
        PreferenceStore(context).use { store ->
            val changes = mutableListOf<MapBaseMode>()
            val remove = store.addListener { changes += it.baseMapMode }
            store.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(listOf(MapBaseMode.WORLD, MapBaseMode.GOIANIA_OFFLINE), changes)
            remove()
        }
    }
    @Test fun themeAndIndependentFiltersSurviveANewStore() {
        for (id in listOf("gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy")) {
            val theme = MapTheme.fromId(id)
            assertEquals("Persisted theme ID must not fall back to GTA SA", id, theme.assetName)
            PreferenceStore(context).use { store ->
                store.setTheme(theme)
                store.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
                store.setCategoryEnabled(PoiCategory.PARKS, false)
                store.setCategoryEnabled(PoiCategory.FUEL, false)
            }
            PreferenceStore(context).use { restored ->
                assertEquals(theme, restored.snapshot().theme)
                assertEquals(MapBaseMode.GOIANIA_OFFLINE, restored.snapshot().baseMapMode)
                assertEquals(PoiCategory.entries.toSet() - PoiCategory.PARKS - PoiCategory.FUEL,
                    restored.snapshot().enabledCategories)
            }
        }
    }
    @Test fun everyStoredThemeDecodesWithoutRewritingExistingPreferences() {
        val storage = context.getSharedPreferences(PreferenceStore.FILE_NAME, Context.MODE_PRIVATE)
        for (id in listOf("gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy")) {
            storage.edit().putString("map_theme", id).commit()
            PreferenceStore(context).use { assertEquals(id, it.snapshot().theme.assetName) }
            assertEquals(id, storage.getString("map_theme", null))
        }
        for (id in listOf("minecraft", "gta5", "future-theme")) {
            storage.edit().putString("map_theme", id).commit()
            PreferenceStore(context).use { assertEquals(MapTheme.GTA_SA, it.snapshot().theme) }
            assertEquals(id, storage.getString("map_theme", null))
        }
    }
    @Test fun allDisabledRemainsEmptyAfterReopening() {
        PreferenceStore(context).use { store -> PoiCategory.entries.forEach { store.setCategoryEnabled(it, false) } }
        PreferenceStore(context).use { assertTrue(it.snapshot().enabledCategories.isEmpty()) }
    }
    @Test fun phoneAndCarStoresObserveTheSamePersistedValues() {
        PreferenceStore(context).use { phone ->
            PreferenceStore(context).use { car ->
                phone.setCategoryEnabled(PoiCategory.SHOPS, false)
                assertFalse(PoiCategory.SHOPS in car.snapshot().enabledCategories)
                car.setTheme(MapTheme.RDR2)
                assertEquals(MapTheme.RDR2, phone.snapshot().theme)
            }
        }
    }
}
