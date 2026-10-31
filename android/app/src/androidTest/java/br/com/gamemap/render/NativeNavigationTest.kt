package br.com.gamemap.render

import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import br.com.gamemap.GameMapApplication
import br.com.gamemap.data.GameAssetCatalog
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
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import kotlin.math.abs

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

    @Test fun twoNewThemesKeepTheActiveRouteAndSemanticVehicleAndSavedIcons() {
        val original = app.preferences.snapshot()
        val settings = app.navigationSettings.snapshot()
        var savedId: String? = null
        val native = AtomicReference<MapLibreMap>()
        val completedFrame = AtomicReference<Style?>(null)
        val idleFrame = AtomicReference<Style?>(null)
        val gameAssets = GameAssetCatalog.load(instrumentation.targetContext)
        val themes = listOf(MapTheme.SUPER_MARIO_WORLD, MapTheme.CYBERPUNK_2077)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.desc("Menu NavSkin")), 15_000))
                scenario.onActivity { activity ->
                    val mapView = findMap(activity.window.decorView)!!
                    mapView.addOnWillStartLoadingMapListener { completedFrame.set(null); idleFrame.set(null) }
                    mapView.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                        if (fully) native.get()?.style?.takeIf { it.isFullyLoaded }?.let(completedFrame::set)
                    }
                    mapView.addOnDidBecomeIdleListener {
                        native.get()?.style?.takeIf { it.isFullyLoaded }?.let(idleFrame::set)
                    }
                    mapView.getMapAsync { map ->
                        native.set(map)
                        map.addOnCameraMoveStartedListener { idleFrame.set(null) }
                    }
                    savedId = app.places.savePlace("Theme switch route evidence",
                        PhoneNavigationReplay.route.geometry[1], iconId = "cafe").id
                    app.navigationSettings.setVehicleId("sport")
                    app.navigationSettings.setSavedPlacesVisible(true)
                    // Keep fixes live long enough to inspect all four replacement styles.
                    app.navigation.startReplay(PhoneNavigationReplay.route, PhoneNavigationReplay.fixes(), 5_000)
                }
                assertTrue(device.wait(Until.hasObject(By.textContains("SIMULAÇÃO")), 15_000))
                val session = app.navigation.snapshot().sessionId
                val route = app.navigation.snapshot().route
                val destination = app.navigation.snapshot().destination
                val stops = app.navigation.snapshot().intermediateStops
                for (theme in themes) {
                    scenario.onActivity { app.preferences.setTheme(theme) }
                    var overlayStatus = "style not evaluated"
                    await(detail = { "${theme.assetName}: $overlayStatus" }) {
                        val style = native.get()?.style ?: return@await false
                        val styleTheme = JSONObject(style.json).optJSONObject("metadata")
                            ?.optJSONObject("gamemap")?.optString("theme")
                        overlayStatus = "styleTheme=$styleTheme, loaded=${style.isFullyLoaded}, " +
                            "fullFrame=${completedFrame.get() === style}, idle=${idleFrame.get() === style}"
                        if (!style.isFullyLoaded || styleTheme != theme.assetName ||
                            completedFrame.get() !== style || idleFrame.get() !== style) return@await false
                        val vehicle = style.getLayerAs<SymbolLayer>(NavigationMapOverlay.VEHICLE_LAYER)
                        val saved = style.getSourceAs<GeoJsonSource>(NavigationMapOverlay.SAVED_SOURCE)
                            ?.querySourceFeatures(null).orEmpty()
                        val routeFeatures = style.getSourceAs<GeoJsonSource>(NavigationMapOverlay.ROUTE_SOURCE)
                            ?.querySourceFeatures(null).orEmpty()
                        val viewport = android.graphics.RectF(0f, 0f,
                            device.displayWidth.toFloat(), device.displayHeight.toFloat())
                        val visibleVehicle = native.get().queryRenderedFeatures(viewport, NavigationMapOverlay.VEHICLE_LAYER)
                        val visibleRoute = native.get().queryRenderedFeatures(viewport, NavigationMapOverlay.ROUTE_LAYER,
                            NavigationMapOverlay.REMAINING_LAYER, NavigationMapOverlay.HEAD_LAYER)
                        val roadLayers = style.layers.map { it.id }.filter { it.startsWith("road-") && it.endsWith("-fill") }
                        val visibleRoads = native.get().queryRenderedFeatures(viewport, *roadLayers.toTypedArray())
                        val visibleBuildings = native.get().queryRenderedFeatures(viewport, "building-footprints")
                        val visibleLabels = native.get().queryRenderedFeatures(viewport,
                            "road-labels", "place-labels", "district-labels")
                        overlayStatus = "vehicle=${vehicle?.iconImage?.value}, saved=${saved.size}, " +
                            "routeSource=${routeFeatures.size}, renderedVehicle=${visibleVehicle.size}, " +
                            "renderedRoute=${visibleRoute.size}, baseRoads=${visibleRoads.size}, " +
                            "baseBuildings=${visibleBuildings.size}, baseLabels=${visibleLabels.size}"
                        // GeoJSON sources finish independently after a style replacement. A ready
                        // vehicle/saved source does not guarantee that route tiles have arrived.
                        if (vehicle?.iconImage?.value != NavigationMapOverlay.AVATAR_IMAGE ||
                            saved.none { it.getStringProperty("savedId") == savedId } ||
                            routeFeatures.isEmpty() || visibleVehicle.isEmpty() || visibleRoute.isEmpty() ||
                            visibleRoads.isEmpty() || visibleBuildings.isEmpty() || visibleLabels.isEmpty()) return@await false
                        assertArrayEquals("Completed labels must use ${theme.label}'s font",
                            arrayOf(gameAssets.themes.getValue(theme.assetName).map.getValue("road")),
                            style.getLayerAs<SymbolLayer>("road-labels")!!.textFont.value)
                        assertNotNull("Completed style needs the themed POI atlas", style.getImage("poi-parks"))
                        assertFalse("${theme.label} must keep the decoded route", routeFeatures.isEmpty())
                        true
                    }
                    scenario.onActivity {
                        val snapshot = app.navigation.snapshot()
                        assertEquals(session, snapshot.sessionId)
                        assertSame(route, snapshot.route)
                        assertEquals(destination, snapshot.destination)
                        assertEquals(stops, snapshot.intermediateStops)
                        assertTrue(snapshot.navigationActive)
                        assertEquals("sport", app.navigationSettings.snapshot().vehicleId)
                        assertEquals("cafe", app.places.snapshot().savedPlaces.single { it.id == savedId }.iconId)
                        val style = native.get().style!!
                        val line = style.getLayerAs<LineLayer>(NavigationMapOverlay.REMAINING_LAYER)!!
                        val casing = style.getLayerAs<LineLayer>(NavigationMapOverlay.REMAINING_CASING)!!
                        assertNotEquals("Route needs a visible border", line.lineColor.value, casing.lineColor.value)
                    }
                    assertNativeBuildingPixels(native.get(), theme)
                    saveScreenshot("nav-${theme.assetName}-simulation.png")
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                app.navigation.stopNavigation()
                savedId?.let(app.places::deletePlace)
                app.preferences.setTheme(original.theme)
                app.navigationSettings.setVehicleId(settings.vehicleId)
                app.navigationSettings.setSavedPlacesVisible(settings.savedPlacesVisible)
            }
        }
    }

    @Test fun simulationAndSavedIconsSurviveThemeFiltersVehicleAndActivityRecreation() {
        val original = app.preferences.snapshot()
        val settings = app.navigationSettings.snapshot()
        val expectedSaved = app.places.snapshot().savedPlaces.size + 1
        var savedId: String? = null
        try {
            instrumentation.runOnMainSync {
                app.preferences.setTheme(MapTheme.GTA_SA)
                app.navigationSettings.setVehicleId("arrow")
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.desc("Menu NavSkin")), 15_000))
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
                scenario.onActivity { activity ->
                    val summary = activity.findViewById<ViewGroup>(br.com.gamemap.R.id.phone_route_summary)
                    assertFalse("Search must give way to navigation actions", activity.findViewById<View>(br.com.gamemap.R.id.phone_search).isShown)
                    assertTrue(activity.findViewById<View>(br.com.gamemap.R.id.phone_stop_navigation).isShown)
                    assertTrue(activity.findViewById<View>(br.com.gamemap.R.id.phone_add_stop).isShown)
                    assertSame(activity.findViewById<View>(br.com.gamemap.R.id.phone_guidance),
                        activity.findViewById<View>(br.com.gamemap.R.id.phone_main_menu).parent)
                    fun visibleTexts(view: View): List<String> = when {
                        !view.isShown -> emptyList()
                        view is android.widget.TextView -> listOf(view.text.toString())
                        view is ViewGroup -> (0 until view.childCount).flatMap { visibleTexts(view.getChildAt(it)) }
                        else -> emptyList()
                    }
                    val text = visibleTexts(summary).joinToString(" ")
                    assertFalse("ETA card must not duplicate speed", text.contains("km/h"))
                    assertFalse("ETA card must not show GPS precision or diagnostics", text.contains("GPS") || text.contains("SIMULAÇÃO") || text.contains("Carregando"))
                    assertTrue("ETA card must retain three clear trip estimates", text.contains("TEMPO") &&
                        text.contains("DISTÂNCIA") && text.contains("CHEGADA") && text.contains(":"))
                }
                // GPS/camera controls must never hide the followed vehicle at the map center.
                val followControl = device.findObject(By.res("${instrumentation.targetContext.packageName}:id/phone_recenter"))
                assertNotNull(followControl)
                val vehiclePixel = projectedVehicle.get()
                assertFalse("Camera controls cover the vehicle", followControl!!.visibleBounds.contains(vehiclePixel.x.toInt(), vehiclePixel.y.toInt()))
                await {
                    native.get().queryRenderedFeatures(android.graphics.RectF(0f, 0f,
                        device.displayWidth.toFloat(), device.displayHeight.toFloat()), "gamemap-nav-vehicle-symbol").isNotEmpty()
                }
                saveScreenshot("nav-gta-player-simulation.png")
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
                        layer?.iconImage?.value == NavigationMapOverlay.AVATAR_IMAGE &&
                            style.getSourceAs<GeoJsonSource>("gamemap-nav-saved")?.querySourceFeatures(null)?.map { it.getStringProperty("savedId") }?.distinct()?.size == expectedSaved
                    } == true
                }
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.textContains("SIMULAÇÃO")), 10_000))
                awaitCompleteMapFrame()
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
                awaitCompleteMapFrame()
                scenario.onActivity { assertEquals(NavigationPhase.NAVIGATING, app.navigation.snapshot().phase); assertTrue(app.navigation.snapshot().guidanceAvailable) }
                scenario.onActivity { activity ->
                    fun findGuidance(view: View): View? {
                        if (view is br.com.gamemap.phone.GuidanceCard) return view
                        if (view is ViewGroup) for (index in 0 until view.childCount) {
                            findGuidance(view.getChildAt(index))?.let { return it }
                        }
                        return null
                    }
                    val cue = android.graphics.Rect()
                    val eta = android.graphics.Rect()
                    assertTrue(findGuidance(activity.window.decorView)!!.getGlobalVisibleRect(cue))
                    assertTrue(activity.findViewById<View>(br.com.gamemap.R.id.phone_route_summary).getGlobalVisibleRect(eta))
                    assertFalse("Landscape maneuver must not overlap ETA", android.graphics.Rect.intersects(cue, eta))
                    val speed = activity.findViewById<View>(br.com.gamemap.R.id.phone_speed)
                    val recenter = activity.findViewById<View>(br.com.gamemap.R.id.phone_recenter)
                    assertEquals(speed.width, recenter.width)
                    assertEquals(speed.width, speed.height)
                    assertEquals(recenter.width, recenter.height)
                }
                device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "nav-landscape-simulation.png"))
                saveScreenshot("nav-landscape-simulation.png")
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

    @Test fun addStopSearchKeepsFinalDestinationAndStopButtonEndsNavigation() {
        var savedId: String? = null
        val previousHome = app.places.snapshot().homeId
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    val saved = app.places.savePlace("Parada de teste UI", PhoneNavigationReplay.route.geometry[1])
                    savedId = saved.id
                    app.places.setHome(saved.id)
                    app.navigation.startReplay(PhoneNavigationReplay.route, PhoneNavigationReplay.fixes(), 1_000)
                }
                assertTrue(device.wait(Until.hasObject(By.text("SIMULAÇÃO GPS")), 10_000))
                val destination = app.navigation.snapshot().destination
                val session = app.navigation.snapshot().sessionId
                device.findObject(By.res("${instrumentation.targetContext.packageName}:id/phone_add_stop")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Painel Adicionar parada")), 5_000))
                saveScreenshot("nav-glass-add-stop-search.png")
                device.findObject(By.desc("Seus lugares · salvos e recentes")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Casa · Parada de teste UI")), 5_000))
                device.findObject(By.desc("Casa · Parada de teste UI")).click()
                instrumentation.waitForIdleSync()
                // Replay deliberately forbids internet requests. Selecting a stop still must
                // reach addStop (which retains the route), never selectDestination.
                assertEquals(destination, app.navigation.snapshot().destination)
                assertEquals(session, app.navigation.snapshot().sessionId)
                assertTrue(app.navigation.snapshot().simulation)
                device.findObject(By.res("${instrumentation.targetContext.packageName}:id/phone_stop_navigation")).click()
                instrumentation.waitForIdleSync()
                assertFalse(app.navigation.snapshot().navigationActive)
                assertNull(app.navigation.snapshot().destination)
                assertTrue(device.wait(Until.hasObject(By.res("${instrumentation.targetContext.packageName}:id/phone_search")), 5_000))
                scenario.onActivity { activity ->
                    assertFalse(activity.findViewById<View>(br.com.gamemap.R.id.phone_stop_navigation).isShown)
                    assertTrue(activity.findViewById<View>(br.com.gamemap.R.id.phone_main_menu).parent is android.widget.FrameLayout)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                app.navigation.stopNavigation()
                savedId?.let(app.places::deletePlace)
                app.places.setHome(previousHome)
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

    private fun awaitCompleteMapFrame() {
        // GPS replay keeps the 3D camera moving. A fully rendered frame proves
        // readiness even if another real fix arrives before the map becomes idle.
        val complete = java.util.regex.Pattern.compile(
            ".*(?:primeiro quadro completo renderizado|recursos e renderização prontos).*")
        assertTrue("The recreated map must report a fully rendered frame",
            device.wait(Until.hasObject(By.desc(complete)), 30_000))
    }

    private fun await(detail: () -> String = { "" }, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        while (System.nanoTime() < deadline) {
            var success = false
            instrumentation.runOnMainSync { success = predicate() }
            if (success) return
            Thread.sleep(100)
        }
        fail("Native navigation overlay was not ready. ${detail()}")
    }
    private fun saveScreenshot(name: String) {
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { saveImage(name, screenshot) } finally { screenshot.recycle() }
    }

    private fun assertNativeBuildingPixels(map: MapLibreMap, theme: MapTheme) {
        val result = AtomicReference<Bitmap>()
        val captured = CountDownLatch(1)
        instrumentation.runOnMainSync { map.snapshot { result.set(it); captured.countDown() } }
        assertTrue("${theme.label} GPU snapshot timed out", captured.await(10, TimeUnit.SECONDS))
        val image = checkNotNull(result.get())
        try {
            saveImage("nav-${theme.assetName}-map.png", image)
            val isNight = instrumentation.targetContext.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val building = NativeMapPalette.forTheme(theme, isNight).building
            var matchingPixels = 0
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
                val pixel = image.getPixel(x, y)
                if (abs(Color.red(pixel) - Color.red(building)) <= 6 &&
                    abs(Color.green(pixel) - Color.green(building)) <= 6 &&
                    abs(Color.blue(pixel) - Color.blue(building)) <= 6) matchingPixels++
            }
            assertTrue("${theme.label} navigation capture has incomplete or stale cartography: " +
                "$matchingPixels building pixels; saved nav-${theme.assetName}-map.png", matchingPixels >= 12)
        } finally { image.recycle() }
    }

    private fun saveImage(name: String, screenshot: Bitmap) {
        val context = instrumentation.targetContext
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NavSkinWorld/v0.10/")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(context.contentResolver.openOutputStream(uri)).use { check(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
            values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }
    private fun findMap(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findMap(view.getChildAt(index))?.let { return it }
        return null
    }
}
