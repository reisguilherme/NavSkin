package br.com.gamemap.render

import android.app.UiModeManager
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PoiCategory
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlin.math.abs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Point

/** Real online service and Android GPU evidence, outside the bundled Goiânia area.
 * Requires internet. Network errors fail this test; no fixture tiles or skipped assertions.
 * Native frames are saved in Pictures/NavSkinWorld for review after AGP removes the app.
 */
@RunWith(AndroidJUnit4::class)
class NativeWorldMapTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as GameMapApplication
    private val device get() = UiDevice.getInstance(instrumentation)
    private val map = AtomicReference<MapLibreMap>()
    private val view = AtomicReference<MapView>()
    private val lastNativeError = AtomicReference<String?>(null)
    private val finishedFrameStyle = AtomicReference<Style?>(null)
    private val idleStyle = AtomicReference<Style?>(null)
    private val saoPaulo = GeoPoint(-23.5505, -46.6333)

    @Test fun saoPauloRendersThreeOnlineThemesAndKeepsCameraAndSavedOverlayAcrossCoverageChanges() {
        // A warmed SDK cache must not turn a disconnected run into an online pass.
        requireLiveProductionTileJson()
        val original = app.preferences.snapshot()
        val settings = app.navigationSettings.snapshot()
        val nightMode = context.getSystemService(UiModeManager::class.java)
        val originalNightMode = if (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
            UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        var savedId: String? = null
        try {
            instrumentation.runOnMainSync {
                assertFalse("Run online cartography evidence with an idle navigation session", app.navigation.snapshot().navigationActive)
                nightMode.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
                app.preferences.setBaseMapMode(MapBaseMode.WORLD)
                app.preferences.setTheme(MapTheme.GTA_SA)
                PoiCategory.entries.forEach { app.preferences.setCategoryEnabled(it, true) }
                app.navigationSettings.setSavedPlacesVisible(true)
                savedId = app.places.savePlace("Native world map evidence", saoPaulo, iconId = "cafe").id
            }
            val marker = checkNotNull(savedId)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                // The initial theme sheet may be absent when a prior destination is resumable.
                device.wait(Until.findObject(By.desc("Explorar mapa")), 3_000)?.click()
                assertTrue("Theme sheet still covers the native map",
                    device.wait(Until.gone(By.desc("Explorar mapa")), 5_000))
                val obtainedMap = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val mapView = checkNotNull(findMap(activity.window.decorView))
                    view.set(mapView)
                    mapView.addOnDidFailLoadingMapListener { lastNativeError.set(it) }
                    mapView.addOnWillStartLoadingMapListener { forgetRenderedFrame() }
                    mapView.addOnDidFinishRenderingFrameListener { fully, _, _ ->
                        if (fully) map.get()?.style?.takeIf { it.isFullyLoaded }?.let(finishedFrameStyle::set)
                    }
                    mapView.addOnDidBecomeIdleListener {
                        map.get()?.style?.takeIf { it.isFullyLoaded }?.let(idleStyle::set)
                    }
                    mapView.getMapAsync { native ->
                        map.set(native)
                        native.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(saoPaulo.latitude, saoPaulo.longitude))
                            .zoom(16.8).bearing(0.0).tilt(0.0).build()
                        obtainedMap.countDown()
                    }
                }
                assertTrue("Native map did not initialize", obtainedMap.await(10, TimeUnit.SECONDS))
                // A real map gesture selects manual camera mode, so later GPS updates cannot
                // turn theme/coverage camera checks into comparisons of unrelated positions.
                device.swipe(device.displayWidth / 2, device.displayHeight / 2,
                    device.displayWidth / 2 + 40, device.displayHeight / 2 + 20, 12)
                val expectedCamera = AtomicReference<CameraPosition>()
                scenario.onActivity {
                    forgetRenderedFrame()
                    map.get().cameraPosition = CameraPosition.Builder()
                        .target(LatLng(saoPaulo.latitude, saoPaulo.longitude))
                        .zoom(16.8).bearing(0.0).tilt(0.0).build()
                    expectedCamera.set(map.get().cameraPosition)
                }

                var previousStyle: Style? = null
                for (theme in listOf(MapTheme.GTA_SA, MapTheme.ROSA_POP, MapTheme.RDR2)) {
                    if (theme != MapTheme.GTA_SA) scenario.onActivity {
                        forgetRenderedFrame()
                        app.preferences.setTheme(theme)
                    }
                    previousStyle = awaitWorldFrame(theme, marker, PoiCategory.entries.toSet(), previousStyle)
                    scenario.onActivity { assertSameCamera(expectedCamera.get(), map.get().cameraPosition) }
                    saveNativeFrame("world-sao-paulo-${theme.assetName}.png", theme)
                }

                // Category changes must leave buildings and the independently saved overlay.
                scenario.onActivity {
                    forgetRenderedFrame()
                    PoiCategory.entries.forEach { app.preferences.setCategoryEnabled(it, false) }
                }
                val hiddenStyle = awaitWorldFrame(MapTheme.RDR2, marker, emptySet())
                assertSame("POI toggles should mutate the loaded style without reloading it", previousStyle, hiddenStyle)
                scenario.onActivity { assertSameCamera(expectedCamera.get(), map.get().cameraPosition) }

                scenario.onActivity {
                    forgetRenderedFrame()
                    app.preferences.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
                }
                val offlineStyle = AtomicReference<Style>()
                var lastOfflineProbe: String? = null
                awaitNative("offline map with retained São Paulo saved marker") {
                    val style = map.get().style ?: return@awaitNative false
                    if (style === hiddenStyle || !style.isFullyLoaded || style.getSource("world") != null ||
                        style.getSource("areas") == null || !hasSavedMarker(style, marker)) return@awaitNative false
                    assertSameCamera(expectedCamera.get(), map.get().cameraPosition)
                    val decodedBuildings = style.getSourceAs<VectorSource>("buildings")
                        ?.querySourceFeatures(arrayOf("buildings"), null)?.size
                    val renderedBuildings = map.get().queryRenderedFeatures(viewport(), "building-footprints").size
                    val probe = "offline sourceBuildings=$decodedBuildings renderedBuildings=$renderedBuildings " +
                        "fullFrame=${finishedFrameStyle.get() === style} idle=${idleStyle.get() === style}"
                    if (probe != lastOfflineProbe) { Log.i("NativeWorldMapTest", probe); lastOfflineProbe = probe }
                    // Style/source readiness is on the UI side. Rendered-feature queries use
                    // the completed renderer frame, which can still belong to the old world
                    // style. Wait for this replacement's frame/idle and final empty geometry.
                    if (!renderedFrameReady(style) || decodedBuildings != 0 || renderedBuildings != 0)
                        return@awaitNative false
                    if (!savedOverlayRendered(marker)) return@awaitNative false
                    offlineStyle.set(style)
                    true
                }
                scenario.onActivity {
                    assertTrue("Local buildings cannot provide São Paulo geometry after the offline frame",
                        map.get().queryRenderedFeatures(viewport(), "building-footprints").isEmpty())
                    assertSameCamera(expectedCamera.get(), map.get().cameraPosition)
                    assertTrue("Saved overlay missing from the completed offline frame", savedOverlayRendered(marker))
                    forgetRenderedFrame()
                    app.preferences.setBaseMapMode(MapBaseMode.WORLD)
                }
                awaitWorldFrame(MapTheme.RDR2, marker, emptySet(), offlineStyle.get())
                scenario.onActivity { assertSameCamera(expectedCamera.get(), map.get().cameraPosition) }
                saveNativeFrame("world-sao-paulo-rdr2-after-offline.png", MapTheme.RDR2)
                // Same real Goiânia square shown in the user's visual reference.
                scenario.onActivity {
                    forgetRenderedFrame()
                    PoiCategory.entries.forEach { app.preferences.setCategoryEnabled(it, true) }
                    app.preferences.setTheme(MapTheme.GTA_SA)
                }
                awaitWorldFrame(MapTheme.GTA_SA, marker, PoiCategory.entries.toSet())
                scenario.onActivity {
                    forgetRenderedFrame()
                    map.get().cameraPosition = CameraPosition.Builder()
                        .target(LatLng(-16.6769541, -49.2438716)).zoom(16.8).bearing(0.0).tilt(0.0).build()
                }
                var goianiaStatus = "not evaluated"
                var goianiaSnapshotRequested = false
                awaitNative("Goiânia university square with real world geometry and game lettering", { goianiaStatus }) {
                    val style = map.get().style ?: return@awaitNative false
                    val poi = style.getSourceAs<VectorSource>("world")?.querySourceFeatures(arrayOf("poi"), null) ?: return@awaitNative false
                    val inCity = poi.any { feature -> (feature.geometry() as? Point)?.let {
                        it.longitude() in -49.27..-49.22 && it.latitude() in -16.70..-16.65
                    } == true }
                    val labels = map.get().queryRenderedFeatures(viewport(), "road-labels").size
                    val localBuildings = style.getSourceAs<VectorSource>("buildings-local")
                        ?.querySourceFeatures(arrayOf("buildings"), null)?.size ?: 0
                    val visibleBuildings = map.get().queryRenderedFeatures(viewport(), "building-footprints-local").size
                    val duplicateBuildings = map.get().queryRenderedFeatures(viewport(), "building-footprints").size
                    goianiaStatus = "camera=${map.get().cameraPosition.target}, poi=${poi.size}, inCity=$inCity, roadLabels=$labels, localBuildings=$localBuildings, visibleBuildings=$visibleBuildings, duplicateBuildings=$duplicateBuildings, full=${style.isFullyLoaded}, frame=${renderedFrameReady(style)}"
                    if (!goianiaSnapshotRequested && renderedFrameReady(style) && inCity) {
                        goianiaSnapshotRequested = true
                        map.get().snapshot { image ->
                            savePersistentImage("world-goiania-diagnostic.png", image)
                            image.recycle()
                        }
                    }
                    renderedFrameReady(style) && inCity && labels > 0 && localBuildings > 100 && visibleBuildings > 30 && duplicateBuildings == 0
                }
                saveNativeFrame("world-goiania-universitaria-gta.png", MapTheme.GTA_SA)
                // A busy online style can delay the initial camera gesture and expose
                // its point-selection sheet. Close it before recording the phone chrome.
                if (device.hasObject(By.desc("Painel Ponto no mapa"))) device.pressBack()
                assertTrue("Point selection must not cover the phone evidence",
                    device.wait(Until.gone(By.desc("Painel Ponto no mapa")), 5_000))
                device.waitForIdle()
                instrumentation.uiAutomation.takeScreenshot()?.also { image ->
                    try { savePersistentImage("phone-goiania-gta.png", image) } finally { image.recycle() }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                savedId?.let(app.places::deletePlace)
                app.preferences.setTheme(original.theme)
                PoiCategory.entries.forEach { app.preferences.setCategoryEnabled(it, it in original.enabledCategories) }
                app.preferences.setBaseMapMode(original.baseMapMode)
                app.navigationSettings.setSavedPlacesVisible(settings.savedPlacesVisible)
                nightMode.setApplicationNightMode(originalNightMode)
            }
        }
    }

    private fun requireLiveProductionTileJson() {
        val connection = URL("https://tiles.openfreemap.org/planet").openConnection() as HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("User-Agent", "NavSkin/0.7 Android-instrumentation-world-validation")
        try {
            assertEquals("Production TileJSON unavailable; online verification cannot pass", 200, connection.responseCode)
            val tilejson = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            assertEquals(0, tilejson.getInt("minzoom"))
            assertTrue(tilejson.getJSONArray("tiles").getString(0).startsWith("https://tiles.openfreemap.org/planet/"))
        } finally { connection.disconnect() }
    }

    private fun awaitWorldFrame(theme: MapTheme, marker: String, enabled: Set<PoiCategory>, after: Style? = null): Style {
        val answer = AtomicReference<Style>()
        awaitNative("${theme.label} online São Paulo geometry, labels, sprites and saved overlay") {
            val native = map.get() ?: return@awaitNative false
            val style = native.style ?: return@awaitNative false
            if (style === after || !style.isFullyLoaded || !renderedFrameReady(style)) return@awaitNative false
            val world = style.getSourceAs<VectorSource>("world") ?: return@awaitNative false
            val metadata = JSONObject(style.json).optJSONObject("metadata")?.optJSONObject("navskin")
            if (metadata?.optString("theme") != theme.assetName || !hasSavedMarker(style, marker)) return@awaitNative false
            assertEquals("https://tiles.openfreemap.org/planet", world.url)
            val buildings = world.querySourceFeatures(arrayOf("building"), null)
            val pois = world.querySourceFeatures(arrayOf("poi"), null)
            if (buildings.isEmpty() || pois.isEmpty()) return@awaitNative false
            assertTrue("Decoded POIs must be in São Paulo, outside the offline package", pois.any { feature ->
                val point = feature.geometry() as? Point
                point != null && point.longitude() in -46.68..-46.58 && point.latitude() in -23.59..-23.51
            })
            if (native.queryRenderedFeatures(viewport(), "building-footprints").isEmpty()) return@awaitNative false
            // Symbol placement only succeeds once the real glyph resources can be rendered.
            if (native.queryRenderedFeatures(viewport(), "road-labels").none { feature ->
                    feature.hasProperty("name") && feature.getStringProperty("name").isNotBlank()
                }) return@awaitNative false
            val roadLabels = checkNotNull(style.getLayerAs<SymbolLayer>("road-labels"))
            val directions = native.queryRenderedFeatures(viewport(), "road-oneway-directions")
            if (directions.isEmpty()) return@awaitNative false
            assertTrue("Direction markers must come from actual one-way roads", directions.all {
                it.hasProperty("oneway") && kotlin.math.abs(it.getNumberProperty("oneway").toInt()) == 1
            })
            assertArrayEquals(arrayOf(when(theme) { MapTheme.GTA_SA -> "NavSkinGtaRoad"; MapTheme.RDR2 -> "NavSkinRdrMap"; else -> "Noto Sans Bold" }), roadLabels.textFont.value)
            for (category in PoiCategory.entries) {
                val layer = checkNotNull(style.getLayer("poi-${category.id}"))
                if (layer.visibility.value != if (category in enabled) "visible" else "none") return@awaitNative false
                if (category in enabled) assertNotNull("Missing actual native POI sprite ${category.id}", style.getImage("poi-${category.id}"))
            }
            val renderedPois = native.queryRenderedFeatures(viewport(), *PoiCategory.entries.map { "poi-${it.id}" }.toTypedArray())
            if (enabled.isNotEmpty() && renderedPois.isEmpty()) return@awaitNative false
            if (enabled.isEmpty()) assertTrue("POIs still rendered after every category was hidden", renderedPois.isEmpty())
            if (!savedOverlayRendered(marker)) return@awaitNative false
            answer.set(style)
            true
        }
        return checkNotNull(answer.get())
    }

    private fun hasSavedMarker(style: Style, marker: String) =
        style.getSourceAs<GeoJsonSource>(NavigationMapOverlay.SAVED_SOURCE)?.querySourceFeatures(null)
            ?.any { it.getStringProperty("savedId") == marker } == true

    private fun savedOverlayRendered(marker: String) =
        map.get().queryRenderedFeatures(viewport(), NavigationMapOverlay.SAVED_LAYER)
            .any { it.getStringProperty("savedId") == marker }

    private fun viewport() = RectF(0f, 0f, view.get().width.toFloat(), view.get().height.toFloat())

    private fun forgetRenderedFrame() {
        finishedFrameStyle.set(null)
        idleStyle.set(null)
    }

    private fun renderedFrameReady(style: Style) = finishedFrameStyle.get() === style && idleStyle.get() === style

    private fun awaitNative(description: String, detail: () -> String = { "" }, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = predicate() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Timed out after 30s: $description. ${detail()}. Native error: ${lastNativeError.get() ?: "none reported"}")
    }

    private fun saveNativeFrame(name: String, theme: MapTheme) {
        val bitmap = AtomicReference<Bitmap>()
        val snapshot = CountDownLatch(1)
        instrumentation.runOnMainSync { map.get().snapshot { image -> bitmap.set(image); snapshot.countDown() } }
        assertTrue("Native GPU snapshot timed out", snapshot.await(10, TimeUnit.SECONDS))
        val image = checkNotNull(bitmap.get())
        try {
            savePersistentImage(name, image)
            val expectedBuilding = when (theme) {
                MapTheme.GTA_SA -> 0xffe0e0dc.toInt()
                MapTheme.ROSA_POP -> 0xfffff3fb.toInt()
                MapTheme.RDR2 -> 0xffb6a17a.toInt()
            }
            var footprintPixels = 0
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
                val pixel = image.getPixel(x, y)
                if (abs(Color.red(pixel) - Color.red(expectedBuilding)) <= 6 &&
                    abs(Color.green(pixel) - Color.green(expectedBuilding)) <= 6 &&
                    abs(Color.blue(pixel) - Color.blue(expectedBuilding)) <= 6) footprintPixels++
            }
            assertTrue("GPU frame has no ${theme.label} building footprints ($footprintPixels pixels); saved $name", footprintPixels >= 12)
        } finally { image.recycle() }
    }

    private fun savePersistentImage(name: String, bitmap: Bitmap) {
        val directory = "Pictures/NavSkinWorld/v0.7/"
        val media = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.delete(media,
            "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(name, directory))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, directory)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(media, values))
        try {
            checkNotNull(context.contentResolver.openOutputStream(uri)).use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } catch (error: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    private fun assertSameCamera(expected: CameraPosition, actual: CameraPosition) {
        assertEquals(expected.target!!.latitude, actual.target!!.latitude, 0.000001)
        assertEquals(expected.target!!.longitude, actual.target!!.longitude, 0.000001)
        assertEquals(expected.zoom, actual.zoom, 0.000001)
        assertEquals(expected.bearing, actual.bearing, 0.000001)
    }

    private fun findMap(root: View): MapView? {
        if (root is MapView) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) findMap(root.getChildAt(index))?.let { return it }
        return null
    }
}
