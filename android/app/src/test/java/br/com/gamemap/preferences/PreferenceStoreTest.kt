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
    @Test fun firstRunHasWorldGtaAndSixCategories() {
        PreferenceStore(context).use { store ->
            assertEquals(MapBaseMode.WORLD, store.snapshot().baseMapMode)
            assertEquals(MapTheme.GTA_SA, store.snapshot().theme)
            assertEquals(PoiCategory.entries.toSet(), store.snapshot().enabledCategories)
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
            assertEquals(setOf(PoiCategory.HEALTH), it.snapshot().enabledCategories)
        }
        assertEquals(MapBaseMode.WORLD, MapBaseMode.fromId("unknown-future-mode"))
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
        for (theme in listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2)) {
            PreferenceStore(context).use { store ->
                store.setTheme(theme)
                store.setCategoryEnabled(PoiCategory.PARKS, false)
                store.setCategoryEnabled(PoiCategory.FUEL, false)
            }
            PreferenceStore(context).use { restored ->
                assertEquals(theme, restored.snapshot().theme)
                assertEquals(PoiCategory.entries.toSet() - PoiCategory.PARKS - PoiCategory.FUEL,
                    restored.snapshot().enabledCategories)
            }
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
