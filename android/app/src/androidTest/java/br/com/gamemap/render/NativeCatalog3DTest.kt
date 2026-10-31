package br.com.gamemap.render

import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.gamemap.GameMapApplication
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

/** Real GPU evidence: every production theme draws volumes over its actual local footprints. */
@RunWith(AndroidJUnit4::class)
class NativeCatalog3DTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as GameMapApplication

    @Test fun allEightThemesDrawRealVolumesAndCanReturnToFlatFootprints() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMapRenderer? = null
            val map = AtomicReference<MapLibreMap>()
            val errors = mutableListOf<String>()
            val camera = CameraPosition.Builder().target(LatLng(-16.684, -49.2618))
                .zoom(17.0).bearing(335.0).tilt(48.0).build()
            try {
                scenario.onActivity { activity ->
                    val view = NativeMapRenderer.createMapView(activity)
                    activity.addContentView(view, ViewGroup.LayoutParams(-1, -1))
                    view.addOnDidFailLoadingMapListener { errors += it }
                    renderer = NativeMapRenderer(activity, view, app.maps.styleFor(
                        MapPreferences(baseMapMode = MapBaseMode.GOIANIA_OFFLINE)), initialCamera = camera)
                    view.getMapAsync { map.set(it) }
                    renderer!!.resume()
                }
                await { map.get() != null && renderer!!.mapView.width > 0 }
                for (theme in MapTheme.entries) {
                    val preferences = MapPreferences(theme = theme, baseMapMode = MapBaseMode.GOIANIA_OFFLINE, browse3D = true)
                    var generation = 0
                    scenario.onActivity {
                        renderer!!.applyMapPreferences(preferences)
                        renderer!!.applyStyle(app.maps.styleFor(preferences))
                        map.get().cameraPosition = camera
                        generation = renderer!!.styleState.requestedGeneration
                    }
                    await {
                        val state = renderer!!.styleState
                        state.loadedGeneration == generation && state.idleGeneration == generation
                    }
                    scenario.onActivity {
                        assertEquals("${theme.label} 3D camera", 48.0, map.get().cameraPosition.tilt, .01)
                        val volumes = map.get().style!!.layers.filter { it.id.startsWith(MAP_3D_LAYER_PREFIX) }
                        assertTrue("${theme.label} has no volume layers", volumes.isNotEmpty())
                        assertTrue("${theme.label} volumes not visible", volumes.all { it.visibility.value == "visible" })
                    }
                    val volumeFrame = snapshot(map.get())
                    save("${theme.assetName}-3d.png", volumeFrame)
                    scenario.onActivity {
                        renderer!!.applyMapPreferences(preferences.copy(browse3D = false))
                        assertEquals("Switching back to 2D", 0.0, map.get().cameraPosition.tilt, .01)
                        // Equal camera makes this a geometry comparison, not merely a tilt comparison.
                        map.get().cameraPosition = camera
                    }
                    val flatFrame = snapshot(map.get())
                    var changed = 0
                    for (y in 0 until volumeFrame.height step 3) for (x in 0 until volumeFrame.width step 3) {
                        val a = volumeFrame.getPixel(x, y)
                        val b = flatFrame.getPixel(x, y)
                        if (kotlin.math.abs(Color.red(a) - Color.red(b)) + kotlin.math.abs(Color.green(a) - Color.green(b)) +
                            kotlin.math.abs(Color.blue(a) - Color.blue(b)) > 35) changed++
                    }
                    assertTrue("${theme.label}: volume geometry made no meaningful pixel difference ($changed samples)", changed > 400)
                    volumeFrame.recycle(); flatFrame.recycle()
                }
                assertTrue("Native map load errors: $errors", errors.isEmpty())
            } finally {
                scenario.onActivity {
                    renderer?.let { it.close(); (it.mapView.parent as? ViewGroup)?.removeView(it.mapView) }
                }
            }
        }
    }

    private fun snapshot(map: MapLibreMap): Bitmap {
        // Snapshot the compositor after native paint transitions, rather than a stale prior frame.
        Thread.sleep(650)
        val result = AtomicReference<Bitmap>()
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync { map.snapshot { result.set(it); latch.countDown() } }
        assertTrue("Native snapshot timed out", latch.await(15, TimeUnit.SECONDS))
        return checkNotNull(result.get())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "v011-catalog").apply { mkdirs() }
        File(folder, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (System.nanoTime() < deadline) {
            var success = false
            instrumentation.runOnMainSync { success = check() }
            if (success) return
            Thread.sleep(75)
        }
        fail("Native production map did not finish loading")
    }
}
