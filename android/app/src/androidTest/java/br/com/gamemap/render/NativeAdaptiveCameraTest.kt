package br.com.gamemap.render

import android.graphics.Rect
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationPreferences
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.*
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.MapTheme
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Collections
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

@RunWith(AndroidJUnit4::class)
class NativeAdaptiveCameraTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val origin = GeoPoint(-16.6809224, -49.2562748)
    private val destination = Destination("test", "SIMULAÇÃO", GeoPoint(-16.6815, -49.257))
    private val route = NavRoute("camera-test", listOf(origin, destination.point),
        listOf(RouteStep(0, "Retorno", "", ManeuverKind.UTURN, 1, 1, 100.0, 15.0)),
        100.0, 15.0, destination, 0L)
    private fun snapshot(time: Long, heading: Double, speed: Double = 40.0, distance: Double = 800.0,
                         point: GeoPoint = origin) =
        NavigationSnapshot(phase = NavigationPhase.NAVIGATING, sessionId = 990L, simulation = true,
            route = route, destination = destination,
            location = LocationState(LocationFix(point, 5.0, elapsedRealtimeMs = time, wallTimeMs = time),
                LocationQuality.GOOD, speed, heading),
            progress = RouteProgress(route.id, 0.0, point, 0, 0, distance, 100.0, 15.0, 0L, 0.0, true, false))

    @Test fun changedViewportAndStylePreserveTheAcceptedFollowTargetDuringAnEase() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMapRenderer? = null
            var native: MapLibreMap? = null
            var starts = 0
            val preferences = NavigationPreferences(orientation = MapOrientation.MOVEMENT_UP)
            val far = GeoPoint(origin.latitude - 0.02, origin.longitude - 0.02)
            var width = 0
            var height = 0
            try {
                scenario.onActivity { activity ->
                    val view = NativeMapRenderer.createMapView(activity)
                    activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    renderer = NativeMapRenderer(activity, view, BACKGROUND_STYLE)
                    renderer!!.resume()
                    view.getMapAsync { map ->
                        native = map
                        map.addOnCameraMoveStartedListener { starts++ }
                    }
                }
                await { native != null && renderer!!.mapView.width > 0 && renderer!!.inspectLoadedStyle {} }
                scenario.onActivity {
                    width = renderer!!.mapView.width
                    height = renderer!!.mapView.height
                    renderer!!.setViewport(width, height, null, null)
                    renderer!!.updateNavigation(snapshot(1_000L, 90.0, point = far), preferences, emptyList(), MapTheme.GTA_SA)
                }
                await {
                    val latitude = native!!.cameraPosition.target!!.latitude
                    latitude < origin.latitude - 0.001 && latitude > far.latitude + 0.001
                }
                val visible = Rect(40, 80, width - 20, height - 40)
                scenario.onActivity {
                    renderer!!.setViewport(width, height, visible, visible)
                    renderer!!.applyStyle(BACKGROUND_STYLE.replace("#101010", "#202020"))
                    // The policy intentionally suppresses an identical target on the next
                    // observation. Padding must preserve the target without relying on it.
                    renderer!!.updateNavigation(snapshot(2_000L, 90.0, point = far), preferences, emptyList(), MapTheme.GTA_SA)
                }
                await { kotlin.math.abs(native!!.cameraPosition.target!!.latitude - far.latitude) < 0.000001 &&
                    renderer!!.inspectLoadedStyle {} }
                scenario.onActivity {
                    assertEquals(40.0, native!!.cameraPosition.padding!![0], 0.01)
                    assertEquals(80.0, native!!.cameraPosition.padding!![1], 0.01)
                    renderer!!.updateNavigation(snapshot(3_000L, 90.0), preferences, emptyList(), MapTheme.GTA_SA)
                }
                await {
                    val latitude = native!!.cameraPosition.target!!.latitude
                    latitude > far.latitude + 0.001 && latitude < origin.latitude - 0.001
                }
                var before = 0
                scenario.onActivity {
                    before = starts
                    repeat(5) { renderer!!.setViewport(width, height, visible, visible) }
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals("Identical viewport updates restarted follow", before, starts) }
                await { kotlin.math.abs(native!!.cameraPosition.target!!.latitude - origin.latitude) < 0.000001 }
                scenario.onActivity {
                    renderer!!.panBy(60f, 20f)
                    val manual = native!!.cameraPosition
                    renderer!!.setViewport(width, height, Rect(10, 20, width - 10, height - 20), null)
                    renderer!!.updateNavigation(snapshot(4_000L, 90.0, point = far), preferences, emptyList(), MapTheme.GTA_SA)
                    assertEquals(NavigationCameraMode.MANUAL, renderer!!.navigationCameraMode)
                    assertEquals(manual.target!!.latitude, native!!.cameraPosition.target!!.latitude, 0.000001)
                    renderer!!.showRouteOverview()
                    val overview = native!!.cameraPosition
                    renderer!!.setViewport(width, height, Rect(20, 30, width - 20, height - 30), null)
                    renderer!!.updateNavigation(snapshot(5_000L, 90.0, point = far), preferences, emptyList(), MapTheme.GTA_SA)
                    assertEquals(NavigationCameraMode.OVERVIEW, renderer!!.navigationCameraMode)
                    assertEquals(overview.zoom, native!!.cameraPosition.zoom, 0.000001)
                }
            } finally {
                scenario.onActivity {
                    renderer?.let { value ->
                        value.close()
                        (value.mapView.parent as? ViewGroup)?.removeView(value.mapView)
                    }
                }
            }
        }
    }

    @Test fun nativeFollowEasesAcrossNorthIgnoresClockTicksAndHonorsManualCamera() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMapRenderer? = null
            var native: MapLibreMap? = null
            val prefs = NavigationPreferences(orientation = MapOrientation.MOVEMENT_UP)
            val samples = mutableListOf<Double>()
            val frames = Collections.synchronizedList(mutableListOf<Long>())
            val recordFrames = AtomicBoolean(false)
            var cameraStarts = 0
            try {
                scenario.onActivity { activity ->
                    val view = NativeMapRenderer.createMapView(activity)
                    view.addOnDidFinishRenderingFrameListener { _, _, _ ->
                        if (recordFrames.get()) frames += android.os.SystemClock.elapsedRealtimeNanos()
                    }
                    activity.addContentView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    renderer = NativeMapRenderer(activity, view,
                        initialCamera = CameraPosition.Builder(NativeMapRenderer.defaultCamera()).bearing(359.0).zoom(16.0).build())
                    renderer!!.resume()
                    view.getMapAsync { map ->
                        native = map
                        assertTrue("Direct two-finger map rotation must be enabled", map.uiSettings.isRotateGesturesEnabled)
                        map.addOnCameraMoveStartedListener { cameraStarts++ }
                        map.addOnCameraMoveListener { samples += map.cameraPosition.bearing }
                        renderer!!.updateNavigation(snapshot(1_000L, 359.0), prefs, emptyList(), MapTheme.GTA_SA)
                    }
                }
                await { native?.cameraPosition?.zoom?.let { kotlin.math.abs(it - 16.6666667) < 0.01 } == true }
                scenario.onActivity {
                    samples.clear()
                    renderer!!.updateNavigation(snapshot(2_000L, 1.0), prefs, emptyList(), MapTheme.GTA_SA)
                    assertTrue("New fix must animate rather than jump", native!!.cameraPosition.bearing > 358.0)
                }
                await { native!!.cameraPosition.bearing in 0.5..1.1 }
                scenario.onActivity {
                    assertTrue("North crossing spun around the long arc: $samples", samples.all { it > 350.0 || it < 10.0 })
                }
                var count = 0
                scenario.onActivity {
                    count = cameraStarts
                    val state = snapshot(2_000L, 1.0)
                    repeat(4) { tick ->
                        renderer!!.updateNavigation(state.copy(location = state.location.copy(ageMs = tick * 200L),
                            progress = state.progress!!.copy(arrivalEpochMs = tick * 1_000L)), prefs, emptyList(), MapTheme.GTA_SA)
                    }
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { assertEquals("Clock ticks restarted native camera animation", count, cameraStarts) }

                var manual: CameraPosition? = null
                scenario.onActivity {
                    renderer!!.panBy(80f, 20f)
                    native!!.cameraPosition = CameraPosition.Builder(native!!.cameraPosition).bearing(123.0).zoom(15.0).build()
                    manual = native!!.cameraPosition
                    renderer!!.updateNavigation(snapshot(3_000L, 90.0, 15.0, 20.0), prefs, emptyList(), MapTheme.ROSA_POP)
                    assertEquals(NavigationCameraMode.MANUAL, renderer!!.navigationCameraMode)
                    assertEquals(manual, native!!.cameraPosition)
                    renderer!!.showRouteOverview()
                    renderer!!.updateNavigation(snapshot(4_000L, 90.0).copy(phase = NavigationPhase.RECALCULATING), prefs, emptyList(), MapTheme.ROSA_POP)
                    assertEquals(NavigationCameraMode.OVERVIEW, renderer!!.navigationCameraMode)
                    renderer!!.updateNavigation(snapshot(5_000L, 90.0, 15.0, 20.0), prefs, emptyList(), MapTheme.ROSA_POP)
                    renderer!!.recenter()
                }
                await { native!!.cameraPosition.zoom > 17.99 && kotlin.math.abs(native!!.cameraPosition.bearing - 90.0) < 0.01 }
                scenario.onActivity { assertTrue(renderer!!.isFollowing) }
                val moved = GeoPoint(origin.latitude - 0.0002, origin.longitude - 0.0002)
                scenario.onActivity {
                    recordFrames.set(true)
                    renderer!!.updateNavigation(snapshot(6_000L, 90.0, 15.0, 10.0, moved), prefs, emptyList(), MapTheme.ROSA_POP)
                }
                await {
                    val latitude = (native!!.style?.getSourceAs<GeoJsonSource>(NavigationMapOverlay.VEHICLE_SOURCE)
                        ?.querySourceFeatures(null)?.firstOrNull()?.geometry() as? Point)?.latitude()
                    latitude != null && latitude < origin.latitude - 0.00001 && latitude > moved.latitude + 0.00001
                }
                await {
                    val latitude = (native!!.style?.getSourceAs<GeoJsonSource>(NavigationMapOverlay.VEHICLE_SOURCE)
                        ?.querySourceFeatures(null)?.firstOrNull()?.geometry() as? Point)?.latitude()
                    latitude != null && kotlin.math.abs(latitude - moved.latitude) < 0.000001
                }
                recordFrames.set(false)
                val times = synchronized(frames) { frames.toList() }
                val intervals = times.zipWithNext { a, b -> (b - a) / 1_000_000.0 }.sorted()
                assertTrue("An observed vehicle transition must render multiple frames", intervals.isNotEmpty())
                val evidence = """{"scene":"offline_probe_observed_fix_transition","frames":${times.size},"medianFrameIntervalMs":${intervals[intervals.size / 2]},"p95FrameIntervalMs":${intervals[((intervals.size - 1) * 0.95).toInt()]}}"""
                java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "native-camera-frame-metrics.json").writeText(evidence)
                android.util.Log.i("NativeAdaptiveCameraTest", evidence)
            } finally {
                scenario.onActivity {
                    renderer?.let { value ->
                        value.close()
                        (value.mapView.parent as? ViewGroup)?.removeView(value.mapView)
                    }
                }
            }
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(50L)
        }
        fail("Native camera did not settle at the expected navigation target")
    }

    companion object {
        private const val BACKGROUND_STYLE = """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#101010"}}]}"""
    }

}
