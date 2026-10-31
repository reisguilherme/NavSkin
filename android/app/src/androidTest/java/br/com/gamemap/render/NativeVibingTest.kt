package br.com.gamemap.render

import android.Manifest
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import br.com.gamemap.R
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.MapPreferences
import br.com.gamemap.preferences.MapTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillExtrusionLayer
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class NativeVibingTest {
    @get:Rule val locationPermission: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val origin = GeoPoint(-16.6809, -49.2563)

    @Test fun freeDrivingForces3DHeadingAndRestoresFlatBrowsePreferences() = verifyCamera(false)
    @Test fun activeRouteKeepsSessionAndRestoresItsFlatNavigationPreference() = verifyCamera(true)

    private fun verifyCamera(withRoute: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMapRenderer? = null
            var native: MapLibreMap? = null
            val destination = Destination("vibing-test", "Test", GeoPoint(-16.6815, -49.257))
            val route = NavRoute("vibing-route", listOf(origin, destination.point), emptyList(), 90.0, 12.0, destination, 0L)
            val snapshot = NavigationSnapshot(
                phase = if (withRoute) NavigationPhase.NAVIGATING else NavigationPhase.IDLE,
                route = if (withRoute) route else null, destination = if (withRoute) destination else null,
                sessionId = 775L,
                location = LocationState(LocationFix(origin, 5.0, elapsedRealtimeMs = 1_000L, wallTimeMs = 1_000L),
                    LocationQuality.GOOD, 15.0, 125.0, 0L))
            val prefs = NavigationPreferences(orientation = MapOrientation.NORTH_UP)
            try {
                scenario.onActivity { activity ->
                    val view = NativeMapRenderer.createMapView(activity)
                    activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
                    renderer = NativeMapRenderer(activity, view, STYLE)
                    renderer!!.resume()
                    renderer!!.applyMapPreferences(MapPreferences(browse3D = false, navigation3D = false))
                    view.getMapAsync { native = it }
                }
                await { native != null && renderer!!.inspectLoadedStyle {} }
                scenario.onActivity {
                    renderer!!.updateNavigation(snapshot, prefs, emptyList(), MapTheme.GTA_SA)
                    renderer!!.setVibing(true)
                }
                await { native!!.cameraPosition.tilt == MAP_3D_TILT &&
                    kotlin.math.abs(native!!.cameraPosition.bearing - 125.0) < .1 &&
                    kotlin.math.abs(native!!.cameraPosition.zoom - 17.0) < .01 }
                scenario.onActivity {
                    val body = native!!.style!!.getLayerAs<FillExtrusionLayer>("navskin-3d-probe")!!
                    assertEquals("visible", body.visibility.value)
                    assertEquals(withRoute, snapshot.navigationActive)
                    assertEquals(if (withRoute) "vibing-route" else null, snapshot.route?.id)
                    renderer!!.panBy(45f, 20f)
                    assertEquals(NavigationCameraMode.MANUAL, renderer!!.navigationCameraMode)
                    renderer!!.recenter()
                }
                await { kotlin.math.abs(native!!.cameraPosition.target!!.latitude - origin.latitude) < .000001 }
                scenario.onActivity { renderer!!.setVibing(false) }
                await { native!!.cameraPosition.tilt == 0.0 && kotlin.math.abs(native!!.cameraPosition.bearing) < .1 }
                scenario.onActivity {
                    assertEquals("none", native!!.style!!.getLayerAs<FillExtrusionLayer>("navskin-3d-probe")!!.visibility.value)
                    assertEquals(if (withRoute) 17.0 else 16.0, native!!.cameraPosition.zoom, .01)
                    if (withRoute) assertEquals(775L, renderer!!.navigationSessionId!!)
                }
            } finally {
                scenario.onActivity {
                    renderer?.let { value -> value.close(); (value.mapView.parent as? ViewGroup)?.removeView(value.mapView) }
                }
            }
        }
    }

    @Test fun phoneHidesControlsSurvivesRecreationAndBackRestoresTheMapUi() {
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("cmd location set-location-enabled true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun dismissImmersiveOnboarding() {
                // Android's first-use fullscreen tutorial owns focus and consumes
                // Back. Handle its observed SystemUI dialog, without settings overrides.
                if (device.wait(Until.findObject(By.res("com.android.systemui:id/immersive_cling_title")), 2_000L) != null) {
                    val okay = device.wait(Until.findObject(By.res("com.android.systemui:id/ok")), 2_000L)
                    assertNotNull("Fullscreen tutorial needs its Got it button", okay)
                    okay!!.click()
                }
                var foreground: MainActivity? = null
                scenario.onActivity { foreground = it }
                await { foreground!!.hasWindowFocus() }
            }
            fun checkHidden(activity: MainActivity) {
                listOf(R.id.phone_search, R.id.phone_route_summary, R.id.phone_speed, R.id.phone_recenter,
                    R.id.phone_main_menu, R.id.phone_guidance).forEach { id ->
                    assertFalse("Control $id should not cover Vibing", activity.findViewById<View>(id).isShown)
                }
                assertFalse(activity.findViewById<View>(R.id.phone_vibing_exit).isShown)
            }
            fun checkRestoredUi() {
                val restored = device.wait(Until.findObject(By.res("${instrumentation.targetContext.packageName}:id/phone_search")), 5_000L)
                if (restored == null) {
                    device.takeScreenshot(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "vibing-exit-failure.png"))
                    device.dumpWindowHierarchy(java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "vibing-exit-failure.xml"))
                }
                assertNotNull("Leaving Vibing must restore search", restored)
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<View>(R.id.phone_search).isShown)
                    assertTrue(activity.findViewById<View>(R.id.phone_speed).isShown)
                    assertTrue(activity.findViewById<View>(R.id.phone_recenter).isShown)
                    assertFalse(activity.findViewById<View>(R.id.phone_vibing_exit).isShown)
                }
            }
            assertNotNull(device.wait(Until.findObject(By.desc("Menu NavSkin")), 15_000))
            device.findObject(By.desc("Menu NavSkin")).click()
            assertNotNull(device.wait(Until.findObject(By.desc("Vibing")), 5_000))
            device.findObject(By.desc("Vibing")).click()
            dismissImmersiveOnboarding()
            scenario.onActivity { activity -> checkHidden(activity) }
            scenario.recreate()
            var recreatedRenderer: NativeMapRenderer? = null
            scenario.onActivity { activity ->
                recreatedRenderer = MainActivity::class.java.getDeclaredField("renderer").apply {
                    isAccessible = true
                }.get(activity) as NativeMapRenderer
            }
            // MapView initializes asynchronously after Activity recreation. Then
            // MapLibre confirms a single tap only after its double-tap interval.
            await { recreatedRenderer!!.mapView.isShown && recreatedRenderer!!.inspectLoadedStyle {} }
            dismissImmersiveOnboarding()
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            assertNotNull("A confirmed map tap must reveal the Vibing exit",
                device.wait(Until.findObject(By.desc("Sair do Vibing")), 5_000L))
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.phone_search).isShown)
                assertFalse(activity.findViewById<View>(R.id.phone_speed).isShown)
                assertTrue(activity.findViewById<View>(R.id.phone_vibing_exit).isShown)
                assertTrue(activity.findViewById<View>(R.id.phone_vibing_recenter).isShown)
            }
            device.pressBack()
            checkRestoredUi()

            // The visible exit control is a second, independent way out.
            assertNotNull(device.wait(Until.findObject(By.desc("Menu NavSkin")), 5_000L))
            device.findObject(By.desc("Menu NavSkin")).click()
            assertNotNull(device.wait(Until.findObject(By.desc("Vibing")), 5_000L))
            device.findObject(By.desc("Vibing")).click()
            dismissImmersiveOnboarding()
            scenario.onActivity { activity -> checkHidden(activity) }
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            val exit = device.wait(Until.findObject(By.res("${instrumentation.targetContext.packageName}:id/phone_vibing_exit")), 5_000L)
            assertNotNull("Map tap must reveal the exit button", exit)
            exit!!.click()
            checkRestoredUi()
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
        fail("Vibing camera did not settle")
    }

    companion object {
        private const val STYLE = """{"version":8,"sources":{"probe":{"type":"geojson","data":{"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[[-49.2565,-16.6808],[-49.2564,-16.6808],[-49.2564,-16.6809],[-49.2565,-16.6809],[-49.2565,-16.6808]]]}}]}}},"layers":[{"id":"background","type":"background","paint":{"background-color":"#20252a"}},{"id":"navskin-3d-probe","type":"fill-extrusion","source":"probe","layout":{"visibility":"none"},"paint":{"fill-extrusion-height":20,"fill-extrusion-color":"#55aadd"}}]}"""
    }
}
