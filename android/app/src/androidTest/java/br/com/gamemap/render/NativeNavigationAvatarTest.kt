package br.com.gamemap.render

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.MapPreferences
import br.com.gamemap.preferences.MapTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.json.JSONObject

/** GPU integration of the shipping mesh views, camera updates, and mode transitions. */
@RunWith(AndroidJUnit4::class)
class NativeNavigationAvatarTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as GameMapApplication
    private val origin = GeoPoint(-16.684, -49.2618)
    private val destination = Destination("avatar-test", "Avatar test", GeoPoint(-16.682, -49.2598))
    private val route = NavRoute("avatar-route", listOf(origin, destination.point), emptyList(), 200.0, 30.0, destination, 0L)
    private val fix = LocationState(LocationFix(origin, 5.0, elapsedRealtimeMs = 1_000L, wallTimeMs = 1_000L),
        LocationQuality.GOOD, 25.0, 45.0, 0L)
    private val preferences = NavigationPreferences(vehicleId = "sport", orientation = MapOrientation.NORTH_UP)

    @Test fun eightThemesDrawTheirMeshAtTheGpsAnchorAndKeepTheSelectedBrowseIcon() {
        withRenderer(productionMap = true) { scenario, renderer, map ->
            val active = NavigationSnapshot(phase = NavigationPhase.NAVIGATING, location = fix,
                route = route, destination = destination, sessionId = 813L)
            val imageHashes = mutableSetOf<Int>()
            for (theme in MapTheme.entries) {
                val mapPreferences = MapPreferences(theme = theme, baseMapMode = MapBaseMode.GOIANIA_OFFLINE)
                var generation = 0
                scenario.onActivity {
                    renderer.applyMapPreferences(mapPreferences)
                    renderer.applyStyle(app.maps.styleFor(mapPreferences))
                    renderer.updateNavigation(active, preferences, emptyList(), theme)
                    generation = renderer.styleState.requestedGeneration
                }
                val expectedPath = "navigation-3d/${theme.assetName}/p55/a04.png"
                var diagnostic = "${theme.assetName}: awaiting shipping style $generation"
                await(detail = { diagnostic }) {
                    val style = map.style ?: return@await false
                    val state = renderer.styleState
                    val styleTheme = JSONObject(style.json).optJSONObject("metadata")?.optJSONObject("gamemap")?.optString("theme")
                    val viewport = android.graphics.RectF(0f, 0f, renderer.mapView.width.toFloat(), renderer.mapView.height.toFloat())
                    val roadLayers = style.layers.map { it.id }.filter { it.startsWith("road-") && it.endsWith("-fill") }
                    val roads = if (roadLayers.isEmpty()) 0 else map.queryRenderedFeatures(viewport, *roadLayers.toTypedArray()).size
                    val buildings = map.queryRenderedFeatures(viewport, "building-footprints").size
                    val labels = map.queryRenderedFeatures(viewport, "road-labels", "place-labels", "district-labels").size
                    val vehicles = map.queryRenderedFeatures(viewport, NavigationMapOverlay.VEHICLE_LAYER).size
                    diagnostic = "${theme.assetName}: style=$styleTheme, requested=$generation loaded=${state.loadedGeneration} " +
                        "idle=${state.idleGeneration}, roads=$roads buildings=$buildings labels=$labels vehicles=$vehicles, " +
                        "camera=${map.cameraPosition}, frame=${renderer.navigationAvatarFramePath}"
                    state.loadedGeneration == generation && state.idleGeneration == generation &&
                        styleTheme == theme.assetName && style.isFullyLoaded && renderer.navigationAvatarFramePath == expectedPath &&
                        roads > 0 && buildings > 0 && labels > 0 && vehicles > 0 &&
                        style.getSourceAs<GeoJsonSource>(NavigationMapOverlay.VEHICLE_SOURCE)?.querySourceFeatures(null)?.isNotEmpty() == true
                }
                scenario.onActivity {
                    val style = map.style!!
                    val layer = style.getLayerAs<SymbolLayer>(NavigationMapOverlay.VEHICLE_LAYER)!!
                    assertEquals(NavigationMapOverlay.AVATAR_IMAGE, layer.iconImage.value)
                    assertEquals("viewport", layer.iconRotationAlignment.value)
                    assertEquals("viewport", layer.iconPitchAlignment.value)
                    assertEquals(0f, layer.iconRotate.value!!, .0001f)
                    assertEquals(72f / 192f, layer.iconSize.value!!, .0001f)
                    assertArrayEquals(arrayOf(0f, -38.4f), layer.iconOffset.value)
                    val image = style.getImage(NavigationMapOverlay.AVATAR_IMAGE)!!
                    assertEquals(192, image.width)
                    assertEquals(192, image.height)
                    val pixels = IntArray(image.width * image.height)
                    image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                    assertTrue("Every theme needs its own model", imageHashes.add(pixels.contentHashCode()))
                    assertTrue("Avatar needs transparent canvas", pixels.any { it ushr 24 == 0 })
                    assertTrue("Avatar needs visible mesh geometry", pixels.count { it ushr 24 > 0 } > 150)
                    assertGpsAnchor(style.getSourceAs(NavigationMapOverlay.VEHICLE_SOURCE)!!)
                    assertEquals("sport", preferences.vehicleId)
                }
                saveSnapshot(map, "${theme.assetName}-navigation.png")
                scenario.onActivity {
                    renderer.updateNavigation(active.copy(phase = NavigationPhase.IDLE, route = null, destination = null),
                        preferences, emptyList(), theme)
                    val layer = map.style!!.getLayerAs<SymbolLayer>(NavigationMapOverlay.VEHICLE_LAYER)!!
                    assertEquals("navigation-icons/${theme.assetName}/vehicles/sport.png", layer.iconImage.value)
                    assertEquals("map", layer.iconRotationAlignment.value)
                    assertEquals("map", layer.iconPitchAlignment.value)
                    assertEquals(1f, layer.iconSize.value!!, .0001f)
                    assertArrayEquals(arrayOf(0f, 0f), layer.iconOffset.value)
                    assertNull(renderer.navigationAvatarFramePath)
                }
            }
        }
    }

    @Test fun rotationChangesTheViewWithoutMovingTheGpsPointAndVibingWorksWithoutARoute() {
        withRenderer { scenario, renderer, map ->
            val state = NavigationSnapshot(location = fix)
            scenario.onActivity {
                renderer.updateNavigation(state, preferences, emptyList(), MapTheme.ROSA_POP)
                renderer.setVibing(true)
            }
            // Vibing follows the observed heading even when the saved preference is north-up.
            await { renderer.navigationAvatarFramePath == "navigation-3d/rosa/p55/a00.png" }
            val before = AtomicReference<Bitmap>()
            scenario.onActivity {
                before.set(map.style!!.getImage(NavigationMapOverlay.AVATAR_IMAGE))
                map.cameraPosition = CameraPosition.Builder(map.cameraPosition).bearing(135.0).tilt(40.0).build()
            }
            await { renderer.navigationAvatarFramePath == "navigation-3d/rosa/p40/a24.png" }
            scenario.onActivity {
                val after = map.style!!.getImage(NavigationMapOverlay.AVATAR_IMAGE)!!
                assertFalse("Rotating the camera must change the mesh face", before.get().sameAs(after))
                assertGpsAnchor(map.style!!.getSourceAs(NavigationMapOverlay.VEHICLE_SOURCE)!!)
                val layer = map.style!!.getLayerAs<SymbolLayer>(NavigationMapOverlay.VEHICLE_LAYER)!!
                assertEquals(0f, layer.iconRotate.value!!, .0001f)
                // Rapid changes must converge to the latest orientation, never a stale decode.
                for (bearing in 0 until 360 step 7) {
                    map.cameraPosition = CameraPosition.Builder(map.cameraPosition).bearing(bearing.toDouble()).build()
                }
                map.cameraPosition = CameraPosition.Builder(map.cameraPosition).bearing(45.0).tilt(0.0).build()
            }
            await { renderer.navigationAvatarFramePath == "navigation-3d/rosa/p0/a00.png" }
            scenario.onActivity {
                renderer.applyStyle(PROBE_STYLE.replace("#242b32", "#242b33"))
            }
            await { renderer.navigationAvatarFramePath == "navigation-3d/rosa/p0/a00.png" }
            scenario.onActivity {
                assertGpsAnchor(map.style!!.getSourceAs(NavigationMapOverlay.VEHICLE_SOURCE)!!)
                renderer.setVibing(false)
                assertNull(renderer.navigationAvatarFramePath)
                assertEquals("navigation-icons/rosa/vehicles/sport.png",
                    map.style!!.getLayerAs<SymbolLayer>(NavigationMapOverlay.VEHICLE_LAYER)!!.iconImage.value)
                assertFalse(state.navigationActive)
                assertNull(state.route)
            }
        }
    }

    private fun assertGpsAnchor(source: GeoJsonSource) {
        val point = source.querySourceFeatures(null).first().geometry() as Point
        assertEquals(origin.latitude, point.latitude(), .000001)
        assertEquals(origin.longitude, point.longitude(), .000001)
    }

    private fun withRenderer(productionMap: Boolean = false,
                             block: (ActivityScenario<MainActivity>, NativeMapRenderer, MapLibreMap) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMapRenderer? = null
            val native = AtomicReference<MapLibreMap>()
            try {
                scenario.onActivity { activity ->
                    val view = NativeMapRenderer.createMapView(activity)
                    activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
                    val initialStyle = if (productionMap) app.maps.styleFor(MapPreferences(baseMapMode = MapBaseMode.GOIANIA_OFFLINE)) else PROBE_STYLE
                    renderer = NativeMapRenderer(activity, view, initialStyle,
                        initialCamera = CameraPosition.Builder().target(LatLng(origin.latitude, origin.longitude))
                            .zoom(17.0).bearing(0.0).tilt(48.0).build())
                    view.getMapAsync { native.set(it) }
                    renderer!!.resume()
                }
                await { native.get() != null && renderer!!.inspectLoadedStyle {} }
                block(scenario, renderer!!, native.get())
            } finally {
                scenario.onActivity { renderer?.let { it.close(); (it.mapView.parent as? ViewGroup)?.removeView(it.mapView) } }
            }
        }
    }

    private fun saveSnapshot(map: MapLibreMap, name: String) {
        Thread.sleep(400)
        val completed = CountDownLatch(1)
        val frame = AtomicReference<Bitmap>()
        instrumentation.runOnMainSync { map.snapshot { frame.set(it); completed.countDown() } }
        assertTrue("Native avatar snapshot timed out", completed.await(15, TimeUnit.SECONDS))
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "v012-navigation-avatars").apply { mkdirs() }
        File(folder, name).outputStream().use { frame.get().compress(Bitmap.CompressFormat.PNG, 100, it) }
        var basePixels = 0
        var samples = 0
        for (y in 0 until frame.get().height step 4) for (x in 0 until frame.get().width step 4) {
            samples++
            if ((frame.get().getPixel(x, y) and 0x00ffffff) != 0) basePixels++
        }
        assertTrue("$name: snapshot is blank behind the avatar ($basePixels/$samples nonblack samples)", basePixels > samples / 5)
        frame.get().recycle()
    }

    private fun await(detail: () -> String = { "Native navigation avatar did not reach the expected view" }, check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35)
        while (System.nanoTime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = check() }
            if (ready) return
            Thread.sleep(50L)
        }
        fail(detail())
    }

    companion object {
        private const val PROBE_STYLE = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#242b32"}}]}"""
    }
}
