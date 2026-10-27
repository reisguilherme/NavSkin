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
import br.com.gamemap.phone.MainActivity
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.MapBaseMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** Uses the real native camera outside renderer wrappers, as MapView gestures do. */
@RunWith(AndroidJUnit4::class)
class NativeCameraRaceTest {
    @Test fun cameraMovedWhileThemeLoadsKeepsTheLatestNativePosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext as GameMapApplication
        val originalTheme = application.preferences.snapshot().theme
        val originalCoverage = application.preferences.snapshot().baseMapMode
        instrumentation.runOnMainSync {
            application.preferences.setTheme(MapTheme.GTA_SA)
            application.preferences.setBaseMapMode(MapBaseMode.GOIANIA_OFFLINE)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val device = UiDevice.getInstance(instrumentation)
                checkNotNull(device.wait(Until.findObject(By.desc("Explorar mapa")), 15000)).click()
                assertTrue("Initial theme carousel did not retract",
                    device.wait(Until.gone(By.desc("Explorar mapa")), 5000))
                val map = AtomicReference<MapLibreMap>()
                val mapView = AtomicReference<MapView>()
                val originalStyle = AtomicReference<Style>()
                val initialStyle = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val view = checkNotNull(findMapView(activity.window.decorView))
                    mapView.set(view)
                    view.getMapAsync { nativeMap ->
                        nativeMap.getStyle {
                            originalStyle.set(it)
                            map.set(nativeMap)
                            initialStyle.countDown()
                        }
                    }
                }
                assertTrue("Initial native style did not load", initialStyle.await(30, TimeUnit.SECONDS))

                val expected = AtomicReference<CameraPosition>()
                val observed = AtomicReference<CameraPosition>()
                val replacementStarted = CountDownLatch(1)
                val changedStyle = CountDownLatch(1)
                var moved = false
                val loading = MapView.OnWillStartLoadingMapListener {
                    if (!moved) {
                        moved = true
                        val nativeMap = checkNotNull(map.get())
                        val latest = CameraPosition.Builder(nativeMap.cameraPosition)
                            .target(LatLng(-16.709665, -49.2707999)).zoom(15.4).build()
                        expected.set(latest)
                        // Native load-start follows renderer.applyStyle's request, so this is
                        // deterministic even when SharedPreferences notification is posted.
                        // This bypasses renderer wrappers, like direct MapView gesture input.
                        nativeMap.cameraPosition = latest
                        nativeMap.getStyle { style ->
                            if (style !== originalStyle.get()) {
                                observed.set(nativeMap.cameraPosition)
                                changedStyle.countDown()
                            }
                        }
                        replacementStarted.countDown()
                    }
                }
                try {
                    scenario.onActivity {
                        mapView.get().addOnWillStartLoadingMapListener(loading)
                        application.preferences.setTheme(MapTheme.RDR2)
                    }
                    assertTrue("Replacement native load did not start", replacementStarted.await(30, TimeUnit.SECONDS))
                    assertTrue("Replacement native style did not load", changedStyle.await(30, TimeUnit.SECONDS))
                } finally {
                    scenario.onActivity { mapView.get().removeOnWillStartLoadingMapListener(loading) }
                }
                val latest = checkNotNull(expected.get())
                val actual = checkNotNull(observed.get())
                assertEquals(latest.target!!.latitude, actual.target!!.latitude, 0.000001)
                assertEquals(latest.target!!.longitude, actual.target!!.longitude, 0.000001)
                assertEquals(latest.zoom, actual.zoom, 0.000001)
                assertEquals(latest.bearing, actual.bearing, 0.000001)
            }
        } finally {
            instrumentation.runOnMainSync {
                application.preferences.setTheme(originalTheme)
                application.preferences.setBaseMapMode(originalCoverage)
            }
        }
    }

    private fun findMapView(view: View): MapView? {
        if (view is MapView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findMapView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
