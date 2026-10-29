package br.com.gamemap.traffic

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.storage.NavigationSettings
import br.com.gamemap.preferences.MapTheme
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Main-thread MapLibre controller. Queries only the visible area after explicit key + toggle. */
class TrafficMapOverlay internal constructor(
    context: Context,
    private val onStatus: (TrafficOverlayStatus) -> Unit,
    private val client: TrafficClient,
    private val connectionAvailable: (() -> Boolean)? = null,
) : AutoCloseable {
    constructor(context: Context, onStatus: (TrafficOverlayStatus) -> Unit = {}) :
        this(context, onStatus, TomTomTrafficClient())
    private val app = context.applicationContext
    private val settings = NavigationSettings(app)
    private val main = Handler(Looper.getMainLooper())
    private var gate = TrafficRefreshGate()
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var active = false
    private var closed = false
    private var enabled = false
    private var key = ""
    private var dark = true
    private var generation = 0L
    private var request: TrafficRequest? = null
    private var requestedArea: TrafficBounds? = null
    private var lastFailure: TrafficFailure? = null
    private var cachedArea: TrafficBounds? = null
    private var incidents = emptyList<TrafficIncident>()
    private var status = TrafficOverlayStatus()
    private var removeSettingsListener: (() -> Unit)? = null
    private val idle = MapLibreMap.OnCameraIdleListener { if (!closed) refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            if (!active || closed) return
            refresh()
            main.postDelayed(this, 15_000L)
        }
    }

    init { removeSettingsListener = settings.addListener { configurationChanged() } }

    fun attach(value: MapLibreMap) {
        if (closed || map === value) return
        map?.removeOnCameraIdleListener(idle)
        map = value
        value.addOnCameraIdleListener(idle)
    }

    fun styleLoading() {
        cancelRequest()
        style = null
    }

    fun styleLoaded(value: Style) {
        if (closed) return
        style = value
        refresh()
    }

    fun updateTheme(theme: MapTheme) {
        val nextDark = theme != MapTheme.ROSA_POP
        if (nextDark == dark || closed) return
        dark = nextDark
        removeLayers()
        refresh()
    }

    fun start() {
        if (closed || active) return
        active = true
        refresh()
        main.postDelayed(ticker, 15_000L)
    }

    fun stop() {
        if (closed) return
        active = false
        main.removeCallbacks(ticker)
        cancelRequest()
        removeLayers()
        publish(if (enabled) TrafficStatus.PAUSED else TrafficStatus.DISABLED)
    }

    override fun close() {
        if (closed) return
        stop()
        closed = true
        map?.removeOnCameraIdleListener(idle)
        map = null
        style = null
        removeSettingsListener?.invoke()
        removeSettingsListener = null
        settings.close()
        key = ""
    }

    private fun configurationChanged() {
        if (closed) return
        val nextKey = settings.trafficApiKey()
        val nextEnabled = settings.trafficEnabled()
        if (nextKey != key || nextEnabled != enabled) {
            cancelRequest()
            key = nextKey
            enabled = nextEnabled
            gate = TrafficRefreshGate()
            incidents = emptyList()
            lastFailure = null
            cachedArea = null
            status = TrafficOverlayStatus()
            removeLayers()
        }
        refresh()
    }

    private fun refresh() {
        if (closed) return
        if (!enabled || key.isBlank()) { removeLayers(); publish(TrafficStatus.DISABLED); return }
        if (!active) { publish(TrafficStatus.PAUSED); return }
        val native = map ?: return
        val loaded = style ?: return
        if (!online()) {
            cancelRequest()
            removeLayers()
            publish(TrafficStatus.OFFLINE)
            return
        }
        val center = native.cameraPosition.target?.let { GeoPoint(it.latitude, it.longitude) } ?: return
        val visible = native.projection.visibleRegion.latLngBounds
        val area = TrafficBounds.forViewport(visible.latitudeSouth, visible.longitudeWest,
            visible.latitudeNorth, visible.longitudeEast, center)
        if (native.cameraPosition.zoom < 12.0 || area == null) {
            cancelRequest()
            removeLayers()
            publish(TrafficStatus.WAITING_ZOOM)
            return
        }
        if (requestedArea?.contains(center) == false) cancelRequest()
        if (cachedArea?.contains(center) == false ||
            status.lastUpdatedAtMs?.let { System.currentTimeMillis() - it > 300_000L } == true) {
            incidents = emptyList()
            cachedArea = null
            status = status.copy(lastUpdatedAtMs = null, incidentCount = 0, incidents = emptyList())
            renderIncidents(loaded)
        }
        // Authentication/quota failures stop tile churn as well as incident polling until backoff.
        if (lastFailure == null) install(loaded)
        if (request != null) return
        if (!gate.tryBegin(SystemClock.elapsedRealtime())) {
            status = status.copy(state = lastFailure?.let(::failureStatus)
                ?: if (cachedArea != null) TrafficStatus.AVAILABLE else TrafficStatus.WAITING_REFRESH)
            onStatus(status)
            return
        }
        install(loaded, refreshRaster = true)
        val capturedGeneration = ++generation
        requestedArea = area
        publish(TrafficStatus.LOADING)
        request = client.fetch(area, key) { result ->
            main.post {
                if (closed || !active || !enabled || generation != capturedGeneration || style !== loaded) return@post
                request = null
                requestedArea = null
                val currentCenter = map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }
                if (currentCenter == null || !area.contains(currentCenter)) return@post
                result.fold(onSuccess = { received ->
                    gate.succeeded()
                    lastFailure = null
                    incidents = received
                    cachedArea = area
                    status = TrafficOverlayStatus(TrafficStatus.AVAILABLE, System.currentTimeMillis(), received.size, received)
                    renderIncidents(loaded)
                    onStatus(status)
                }, onFailure = { failure ->
                    val typed = failure as? TrafficException ?: TrafficException(TrafficFailure.UNAVAILABLE)
                    lastFailure = typed.kind
                    gate.failed(SystemClock.elapsedRealtime(), typed.kind, typed.retryAfterSeconds)
                    removeLayers()
                    publish(failureStatus(typed.kind))
                })
            }
        }
    }

    private fun cancelRequest() {
        generation++
        request?.cancel()
        request = null
        requestedArea = null
    }

    private fun install(loaded: Style, refreshRaster: Boolean = false) {
        if (refreshRaster) {
            loaded.removeLayer(FLOW_LAYER)
            loaded.removeSource(FLOW_SOURCE)
        }
        if (loaded.getSource(FLOW_SOURCE) == null) {
            val tiles = TileSet("2.2.0", client.rasterTemplate(dark))
            tiles.setMinZoom(12f)
            tiles.setMaxZoom(22f)
            tiles.attribution = "© TomTom · © OpenStreetMap contributors"
            loaded.addSource(RasterSource(FLOW_SOURCE, tiles, 256))
            insertBelowNavigation(loaded, RasterLayer(FLOW_LAYER, FLOW_SOURCE).withProperties(rasterOpacity(.78f)), true)
        }
        if (loaded.getSource(INCIDENT_SOURCE) == null) {
            loaded.addSource(GeoJsonSource(INCIDENT_SOURCE, EMPTY))
            loaded.addSource(GeoJsonSource(POINT_SOURCE, EMPTY))
            insertBelowNavigation(loaded, LineLayer(INCIDENT_LINE, INCIDENT_SOURCE).withProperties(
                lineColor(get("color")), lineWidth(3f), lineOpacity(.8f), lineDasharray(arrayOf(1f, 1f))))
            TrafficIncidentKind.entries.forEach { kind ->
                if (loaded.getImage(TrafficSymbols.imageId(kind)) == null)
                    loaded.addImage(TrafficSymbols.imageId(kind), TrafficSymbols.draw(kind))
            }
            insertBelowNavigation(loaded, SymbolLayer(INCIDENT_POINT, POINT_SOURCE).withProperties(
                iconImage(get("image")), iconSize(.8f), iconAnchor("center"),
                iconAllowOverlap(false), iconRotationAlignment("viewport")))
            renderIncidents(loaded)
        }
    }

    private fun insertBelowNavigation(loaded: Style, layer: Layer, belowLabels: Boolean = false) {
        val before = loaded.layers.firstOrNull {
            it.id.startsWith("gamemap-nav-") || (belowLabels && it is SymbolLayer)
        }?.id
        if (before != null) loaded.addLayerBelow(layer, before) else loaded.addLayer(layer)
    }

    private fun renderIncidents(loaded: Style) {
        val lines = incidents.filter { it.points.size > 1 }.map { incident ->
            Feature.fromGeometry(LineString.fromLngLats(incident.points.map { Point.fromLngLat(it.longitude, it.latitude) })).apply {
                addStringProperty("color", incident.kind.color)
            }
        }
        val points = incidents.map { incident ->
            val point = incident.points.first()
            Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                addStringProperty("color", incident.kind.color)
                addStringProperty("description", incident.description)
                addStringProperty("category", incident.kind.label)
                addStringProperty("id", incident.id)
                addStringProperty("image", TrafficSymbols.imageId(incident.kind))
            }
        }
        loaded.getSourceAs<GeoJsonSource>(INCIDENT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        loaded.getSourceAs<GeoJsonSource>(POINT_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(points))
    }

    private fun removeLayers() {
        val loaded = style ?: return
        listOf(INCIDENT_POINT, INCIDENT_LINE, FLOW_LAYER).forEach { loaded.removeLayer(it) }
        listOf(POINT_SOURCE, INCIDENT_SOURCE, FLOW_SOURCE).forEach { loaded.removeSource(it) }
    }

    private fun publish(state: TrafficStatus) {
        status = status.copy(state = state)
        onStatus(status)
    }

    private fun failureStatus(failure: TrafficFailure) = when (failure) {
        TrafficFailure.AUTHENTICATION -> TrafficStatus.AUTHENTICATION
        TrafficFailure.RATE_LIMIT -> TrafficStatus.RATE_LIMIT
        TrafficFailure.OFFLINE -> TrafficStatus.OFFLINE
        else -> TrafficStatus.UNAVAILABLE
    }

    private fun online(): Boolean = try {
        connectionAvailable?.invoke() ?: run {
            val connectivity = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    } catch (_: Exception) { false }

    companion object {
        const val FLOW_SOURCE = "gamemap-traffic-flow"
        const val FLOW_LAYER = "gamemap-traffic-flow-raster"
        const val INCIDENT_SOURCE = "gamemap-traffic-incidents"
        const val POINT_SOURCE = "gamemap-traffic-incident-points"
        const val INCIDENT_LINE = "gamemap-traffic-incident-lines"
        const val INCIDENT_POINT = "gamemap-traffic-incident-markers"
        private val EMPTY = FeatureCollection.fromFeatures(emptyList<Feature>())
    }
}
