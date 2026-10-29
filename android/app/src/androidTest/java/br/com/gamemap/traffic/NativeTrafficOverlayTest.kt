package br.com.gamemap.traffic

import android.graphics.Bitmap
import android.graphics.RectF
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.gamemap.GameMapApplication
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.phone.ConnectionInstructionsActivity
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
import java.io.File

/** Real MapLibre/GPU lifecycle with synthetic incidents and a local raster tile; no HTTP changes. */
@RunWith(AndroidJUnit4::class)
class NativeTrafficOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun installsRendersCleansUpAndRejectsLateIncidentCallbacks() {
        val app = instrumentation.targetContext.applicationContext as GameMapApplication
        val settings = app.navigationSettings
        val originalKey = settings.trafficApiKey()
        val originallyEnabled = settings.trafficEnabled()
        val tile = File(app.cacheDir, "traffic-native-fixture-${System.nanoTime()}.png")
        Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).also { bitmap ->
            tile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val client = FixtureTrafficClient("file://${tile.absolutePath}")
        var view: MapView? = null
        var map: MapLibreMap? = null
        var overlay: TrafficMapOverlay? = null
        var currentStyle: Style? = null
        var status = TrafficOverlayStatus()
        try {
            onMain { settings.setTrafficEnabled(false); settings.setTrafficApiKey("native-fixture-not-a-real-key") }
            // This host does not create a renderer; only the injected controller can load traffic.
            ActivityScenario.launch(ConnectionInstructionsActivity::class.java).use { scenario ->
                try {
                    scenario.onActivity { activity ->
                        val layout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                        layout.addView(TextView(activity).apply { text = "DADOS SINTÉTICOS · teste de trânsito" })
                        val nativeView = MapView(activity)
                        view = nativeView
                        layout.addView(nativeView, LinearLayout.LayoutParams(-1, 0, 1f))
                        activity.setContentView(layout)
                        nativeView.onCreate(null)
                        nativeView.onStart()
                        nativeView.onResume()
                        nativeView.getMapAsync { native ->
                            map = native
                            native.cameraPosition = CameraPosition.Builder().target(LatLng(-16.68, -49.25)).zoom(15.0).build()
                            val controller = TrafficMapOverlay(activity, { status = it }, client, { true })
                            overlay = controller
                            controller.attach(native)
                            native.setStyle(Style.Builder().fromJson(STYLE)) { loaded ->
                                currentStyle = loaded
                                controller.styleLoaded(loaded)
                            }
                        }
                    }
                    await { currentStyle != null && (view?.width ?: 0) > 0 && (view?.height ?: 0) > 0 }
                    onMain { overlay!!.start(); settings.setTrafficEnabled(true) }
                    await { client.pending.size == 1 }
                    onMain {
                        val loaded = currentStyle!!
                        val ids = loaded.layers.map { it.id }
                        assertTrue(ids.indexOf(TrafficMapOverlay.FLOW_LAYER) < ids.indexOf("base-label-placeholder"))
                        assertTrue(ids.indexOf(TrafficMapOverlay.INCIDENT_POINT) < ids.indexOf("gamemap-nav-route-casing"))
                        assertNotNull(loaded.getLayerAs<SymbolLayer>(TrafficMapOverlay.INCIDENT_POINT))
                        TrafficIncidentKind.entries.forEach { assertNotNull(loaded.getImage(TrafficSymbols.imageId(it))) }
                        client.pending[0].complete(FIXTURE)
                    }
                    await {
                        status.state == TrafficStatus.AVAILABLE &&
                            currentStyle!!.getSourceAs<GeoJsonSource>(TrafficMapOverlay.POINT_SOURCE)!!
                                .querySourceFeatures(null).map { it.getStringProperty("id") }.distinct().size == 2
                    }
                    await {
                        map!!.queryRenderedFeatures(RectF(0f, 0f, view!!.width.toFloat(), view!!.height.toFloat()),
                            TrafficMapOverlay.INCIDENT_POINT).isNotEmpty()
                    }
                    onMain {
                        assertEquals(2, status.incidentCount)
                        assertEquals("fixture-accident", status.incidents.first().id)
                        settings.setTrafficEnabled(false)
                    }
                    await { status.state == TrafficStatus.DISABLED && currentStyle!!.getSource(TrafficMapOverlay.FLOW_SOURCE) == null }
                    onMain {
                        assertNull(currentStyle!!.getLayer(TrafficMapOverlay.INCIDENT_POINT))
                        assertNull(currentStyle!!.getSource(TrafficMapOverlay.POINT_SOURCE))
                        client.pending[0].complete(FIXTURE) // Deliberately deliver despite cancellation.
                    }
                    instrumentation.waitForIdleSync()
                    onMain { assertEquals(TrafficStatus.DISABLED, status.state); assertEquals(0, status.incidentCount) }

                    onMain { settings.setTrafficEnabled(true) }
                    await { client.pending.size == 2 }
                    onMain { settings.setTrafficApiKey("native-second-fixture-key") }
                    await { client.pending.size == 3 }
                    onMain {
                        assertTrue(client.pending[1].cancelled)
                        client.pending[1].complete(FIXTURE)
                        client.pending[2].complete(listOf(FIXTURE.last()))
                    }
                    await { status.state == TrafficStatus.AVAILABLE && status.incidentCount == 1 }
                    onMain { assertEquals("fixture-closure", status.incidents.single().id) }

                    // A style replacement invalidates an in-flight response even when its client ignores cancel.
                    onMain { settings.setTrafficApiKey("native-third-fixture-key") }
                    await { client.pending.size == 4 }
                    onMain {
                        overlay!!.styleLoading()
                        currentStyle = null
                        map!!.setStyle(Style.Builder().fromJson(STYLE.replace("#18232D", "#19232D"))) { loaded ->
                            currentStyle = loaded
                            overlay!!.styleLoaded(loaded)
                        }
                    }
                    await { currentStyle != null && currentStyle!!.getLayer(TrafficMapOverlay.INCIDENT_POINT) != null }
                    onMain {
                        assertTrue(client.pending[3].cancelled)
                        client.pending[3].complete(FIXTURE)
                    }
                    instrumentation.waitForIdleSync()
                    onMain {
                        assertEquals(TrafficStatus.WAITING_REFRESH, status.state)
                        assertTrue(status.incidents.isEmpty())
                        assertNull(status.lastUpdatedAtMs)
                        overlay!!.close()
                        overlay = null
                        assertNull(currentStyle!!.getSource(TrafficMapOverlay.INCIDENT_SOURCE))
                        assertNull(currentStyle!!.getLayer(TrafficMapOverlay.FLOW_LAYER))
                    }
                } finally {
                    scenario.onActivity {
                        overlay?.close()
                        overlay = null
                        view?.onPause(); view?.onStop(); view?.onDestroy()
                        view = null
                    }
                }
            }
        } finally {
            onMain {
                settings.setTrafficEnabled(false)
                settings.setTrafficApiKey(originalKey)
                settings.setTrafficEnabled(originallyEnabled)
            }
            tile.delete()
        }
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20)
        do {
            var ready = false
            onMain { ready = predicate() }
            if (ready) return
            Thread.sleep(50)
        } while (System.nanoTime() < deadline)
        fail("Native traffic state did not become ready within 20 seconds")
    }

    private class FixtureTrafficClient(private val raster: String) : TrafficClient {
        val pending = mutableListOf<Pending>()
        override fun rasterTemplate(dark: Boolean) = raster
        override fun fetch(bounds: TrafficBounds, key: String, callback: (Result<List<TrafficIncident>>) -> Unit): TrafficRequest {
            assertTrue(bounds.north - bounds.south <= .1)
            return Pending(callback).also(pending::add)
        }
        class Pending(private val callback: (Result<List<TrafficIncident>>) -> Unit) : TrafficRequest {
            var cancelled = false
            override fun cancel() { cancelled = true }
            fun complete(incidents: List<TrafficIncident>) { callback(Result.success(incidents)) }
        }
    }

    companion object {
        private val FIXTURE = listOf(
            TrafficIncident("fixture-accident", TrafficIncidentKind.ACCIDENT,
                listOf(GeoPoint(-16.68, -49.25), GeoPoint(-16.6805, -49.2505)), "Acidente sintético", 180, null),
            TrafficIncident("fixture-closure", TrafficIncidentKind.CLOSURE,
                listOf(GeoPoint(-16.682, -49.252)), "Interdição sintética", null, null),
        )
        private const val STYLE = """{"version":8,"sources":{"anchor":{"type":"geojson","data":{"type":"FeatureCollection","features":[]}}},"layers":[
          {"id":"background","type":"background","paint":{"background-color":"#18232D"}},
          {"id":"base-label-placeholder","type":"symbol","source":"anchor"},
          {"id":"gamemap-nav-route-casing","type":"line","source":"anchor"},
          {"id":"gamemap-nav-vehicle-symbol","type":"symbol","source":"anchor"}
        ]}"""
    }
}
