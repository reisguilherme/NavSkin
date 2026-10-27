package br.com.gamemap.render

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.phone.PhoneNavigationReplay
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.MapBaseMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Native Android GPU and disk evidence. All route playback is explicitly SIMULAÇÃO. */
@RunWith(AndroidJUnit4::class)
class NativeNavigationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as GameMapApplication
    private val device get() = UiDevice.getInstance(instrumentation)
    private var previousCoverage = MapBaseMode.WORLD

    @Before fun useDeterministicOfflineMap() {
        instrumentation.runOnMainSync {
            previousCoverage = app.preferences.snapshot().baseMapMode
            app.preferences.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
        }
    }

    @After fun restoreMapCoverage() {
        instrumentation.runOnMainSync { app.preferences.setBaseMapMode(previousCoverage) }
    }

    @Test fun simulationAndSavedIconsSurviveThemeFiltersVehicleAndActivityRecreation() {
        val original = app.preferences.snapshot()
        val settings = app.navigationSettings.snapshot()
        val expectedSaved = app.places.snapshot().savedPlaces.size + 1
        var savedId: String? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                device.wait(Until.findObject(By.desc("Explorar mapa")), 15_000)?.click()
                val native = AtomicReference<MapLibreMap>()
                scenario.onActivity { activity ->
                    findMap(activity.window.decorView)!!.getMapAsync { native.set(it) }
                    val saved = app.places.savePlace("Teste nativo de navegação", PhoneNavigationReplay.route.geometry[1], iconId = "cafe")
                    savedId = saved.id
                    app.navigationSettings.setSavedPlacesVisible(true)
                    app.navigation.startReplay(PhoneNavigationReplay.route, PhoneNavigationReplay.fixes(), 1_000)
                }
                assertTrue(device.wait(Until.hasObject(By.textContains("SIMULAÇÃO")), 15_000))
                await { native.get()?.style?.getSourceAs<GeoJsonSource>("gamemap-nav-vehicle")?.querySourceFeatures(null)?.isNotEmpty() == true }
                val projectedVehicle = AtomicReference<android.graphics.PointF>()
                scenario.onActivity {
                    projectedVehicle.set(native.get().projection.toScreenLocation(
                        org.maplibre.android.geometry.LatLng(app.navigation.snapshot().location.fix!!.point.latitude,
                            app.navigation.snapshot().location.fix!!.point.longitude)))
                }
                // GPS/camera controls must never hide the followed vehicle at the map center.
                val followControl = device.findObject(By.desc("GPS acompanhando; recentralizar"))
                assertNotNull(followControl)
                val vehiclePixel = projectedVehicle.get()
                assertFalse("Camera controls cover the vehicle", followControl!!.parent.visibleBounds.contains(vehiclePixel.x.toInt(), vehiclePixel.y.toInt()))
                var sessionId = 0L
                scenario.onActivity {
                    sessionId = app.navigation.snapshot().sessionId
                    val style = native.get().style!!
                    assertNotNull(style.getSource("gamemap-nav-route"))
                    assertEquals(expectedSaved, style.getSourceAs<GeoJsonSource>("gamemap-nav-saved")!!.querySourceFeatures(null).map { it.getStringProperty("savedId") }.distinct().size)
                    app.preferences.setTheme(MapTheme.ROSA_POP)
                    app.preferences.snapshot().enabledCategories.forEach { category -> app.preferences.setCategoryEnabled(category, false) }
                    app.navigationSettings.setVehicleId("sport")
                    app.navigationSettings.setOrientation(MapOrientation.NORTH_UP)
                }
                await {
                    native.get()?.style?.let { style ->
                        val layer = style.getLayerAs<SymbolLayer>("gamemap-nav-vehicle-symbol")
                        layer?.iconImage?.value?.contains("rosa/vehicles/sport") == true &&
                            style.getSourceAs<GeoJsonSource>("gamemap-nav-saved")?.querySourceFeatures(null)?.map { it.getStringProperty("savedId") }?.distinct()?.size == expectedSaved
                    } == true
                }
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.textContains("SIMULAÇÃO")), 10_000))
                assertTrue(device.wait(Until.hasObject(By.descContains("recursos e renderização prontos")), 30_000))
                scenario.onActivity {
                    assertEquals(sessionId, app.navigation.snapshot().sessionId)
                    assertEquals(NavigationPhase.NAVIGATING, app.navigation.snapshot().phase)
                    assertTrue(app.navigation.snapshot().guidanceAvailable)
                    assertEquals("sport", app.navigationSettings.snapshot().vehicleId)
                    assertEquals(MapTheme.ROSA_POP, app.preferences.snapshot().theme)
                }
                device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "nav-rosa-simulation.png"))
                scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await { device.displayWidth > device.displayHeight }
                assertTrue(device.wait(Until.hasObject(By.descContains("recursos e renderização prontos")), 30_000))
                scenario.onActivity { assertEquals(NavigationPhase.NAVIGATING, app.navigation.snapshot().phase); assertTrue(app.navigation.snapshot().guidanceAvailable) }
                device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "nav-landscape-simulation.png"))
                instrumentation.runOnMainSync { app.navigation.stopReplay() }
                assertFalse(app.navigation.snapshot().simulation)
                assertNull(app.navigation.snapshot().location.fix)
            }
        } finally {
            instrumentation.runOnMainSync {
                app.navigation.stopNavigation()
                savedId?.let(app.places::deletePlace)
                app.preferences.setTheme(original.theme)
                br.com.gamemap.preferences.PoiCategory.entries.forEach { app.preferences.setCategoryEnabled(it, it in original.enabledCategories) }
                app.navigationSettings.setVehicleId(settings.vehicleId)
                app.navigationSettings.setOrientation(settings.orientation)
                app.navigationSettings.setSavedPlacesVisible(settings.savedPlacesVisible)
            }
        }
    }

    @Test fun actualAndroidAtomicStorageReopensRepeatedEditsAndRemoval() {
        val context = instrumentation.targetContext
        val file = java.io.File(context.cacheDir, "native-places-${System.nanoTime()}.json")
        val places = br.com.gamemap.navigation.storage.LocalPlacesStore(context, file = file)
        val one = places.savePlace("Primeiro nome", PhoneNavigationReplay.route.destination.point)
        places.setHome(one.id)
        places.savePlace("Nome atualizado", one.point, iconId = "health", id = one.id)
        var reopened = br.com.gamemap.navigation.storage.LocalPlacesStore(context, file = file)
        assertEquals("Nome atualizado", reopened.snapshot().savedPlaces.single().name)
        assertEquals(one.id, reopened.snapshot().homeId)
        reopened.deletePlace(one.id)
        reopened = br.com.gamemap.navigation.storage.LocalPlacesStore(context, file = file)
        assertTrue(reopened.snapshot().savedPlaces.isEmpty())
        assertNull(reopened.snapshot().homeId)
        file.delete()
    }

    @Test fun deviceKeyStoreEncryptsAndRestoresProviderKeyWithoutPlaintextPreferences() {
        val settings = app.navigationSettings
        val original = settings.apiKey()
        val sentinel = "instrumentation-key-never-sent-to-network"
        try {
            instrumentation.runOnMainSync { app.navigation.stopNavigation(); settings.setApiKey(sentinel) }
            assertEquals(sentinel, settings.apiKey())
            val preferences = instrumentation.targetContext.getSharedPreferences(
                br.com.gamemap.navigation.storage.NavigationSettings.FILE_NAME, android.content.Context.MODE_PRIVATE)
            assertFalse(preferences.all.values.any { it.toString().contains(sentinel) })
            assertTrue(preferences.all.keys.any { it.contains("ciphertext") })
            instrumentation.runOnMainSync { settings.setApiKey("") }
            assertEquals("", settings.apiKey())
        } finally { instrumentation.runOnMainSync { settings.setApiKey(original) } }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        while (System.nanoTime() < deadline) {
            var success = false
            instrumentation.runOnMainSync { success = predicate() }
            if (success) return
            Thread.sleep(100)
        }
        fail("Native navigation overlay was not ready")
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
}
