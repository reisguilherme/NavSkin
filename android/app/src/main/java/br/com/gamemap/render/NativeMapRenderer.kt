package br.com.gamemap.render

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.view.View
import br.com.gamemap.navigation.NavigationPreferences
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.storage.SavedPlace
import br.com.gamemap.preferences.MapTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory.visibility
import kotlin.math.ln
import kotlin.math.roundToInt

/** A native map and its lifecycle. The caller supplies a view belonging to its own display. */
class NativeMapRenderer(
    context: Context,
    val mapView: MapView,
    styleJson: String = probeStyle(context),
    private val onStatus: (String) -> Unit = {},
    initialCamera: CameraPosition? = null,
    savedInstanceState: Bundle? = null,
) : AutoCloseable {
    private var map: MapLibreMap? = null
    private var pendingStyle = styleJson
    private var pendingCamera = initialCamera ?: defaultCamera()
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var styleGeneration = 0
    private var loadedGeneration: Int? = null
    private var idleGeneration: Int? = null
    private var styleReady = false
    private var firstFrameReported = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null
    private val navigationOverlay = NavigationMapOverlay(context)
    private val navigationCamera = NavigationCameraState(
        savedInstanceState?.getString(CAMERA_MODE_KEY)?.let { saved ->
            NavigationCameraMode.entries.firstOrNull { it.name == saved }
        } ?: NavigationCameraMode.FOLLOW,
        savedInstanceState?.takeIf { it.containsKey(CAMERA_SESSION_KEY) }?.getLong(CAMERA_SESSION_KEY),
    )
    private var navigation: NavigationSnapshot? = null
    private var navigationPreferences = NavigationPreferences()
    private var cameraModeListener: ((Boolean) -> Unit)? = null
    private var destinationListener: ((GeoPoint) -> Unit)? = null
    private var followNeedsRecenter = true
    private var lastFollowCameraKey: Triple<GeoPoint, Long, Double>? = null
    private var pendingOverview = false
    private val effectiveWidth: Int get() = viewportWidth.takeIf { it > 0 } ?: mapView.width
    private val effectiveHeight: Int get() = viewportHeight.takeIf { it > 0 } ?: mapView.height

    val isFollowing: Boolean get() = navigationCamera.mode == NavigationCameraMode.FOLLOW

    internal val navigationCameraMode: NavigationCameraMode get() = navigationCamera.mode
    internal val navigationSessionId: Long? get() = navigationCamera.sessionId

    private val cameraMoveListener = MapLibreMap.OnCameraMoveStartedListener { reason ->
        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) enterManualMode()
    }
    private val mapLongClickListener = MapLibreMap.OnMapLongClickListener { position ->
        val callback = destinationListener
        if (callback == null || destroyed) false else {
            enterManualMode()
            callback(GeoPoint(position.latitude, position.longitude))
            true
        }
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (!destroyed && pendingOverview) fitRouteOverview()
    }

    internal data class StyleState(
        val requestedGeneration: Int,
        val loadedGeneration: Int?,
        val idleGeneration: Int?,
    )

    /** Diagnostics for the current native request; read only on the main thread. */
    internal val styleState: StyleState
        get() {
            requireMainThread()
            return StyleState(styleGeneration, loadedGeneration, idleGeneration)
        }

    /** The Style is valid only during this synchronous callback, before its next replacement. */
    internal fun inspectLoadedStyle(inspect: (Style) -> Unit): Boolean {
        requireMainThread()
        if (destroyed || !styleReady || loadedGeneration != styleGeneration) return false
        val style = map?.style ?: return false
        inspect(style)
        return true
    }

    val cameraState: CameraPosition
        get() {
            requireMainThread()
            return map?.cameraPosition ?: pendingCamera
        }

    val safeArea: Rect
        get() {
            requireMainThread()
            return contentArea()
        }

    private val loadErrorListener = MapView.OnDidFailLoadingMapListener { error ->
        if (!destroyed) onStatus("MapLibre Native: falha ao carregar mapa: $error")
    }

    private val frameListener = MapView.OnDidFinishRenderingFrameListener { fully, _, _ ->
        if (!destroyed && styleReady && fully && !firstFrameReported) {
            firstFrameReported = true
            onStatus("MapLibre Native: primeiro quadro completo renderizado")
        }
    }

    private val idleListener = MapView.OnDidBecomeIdleListener {
        if (!destroyed && styleReady && loadedGeneration == styleGeneration &&
            map?.style?.isFullyLoaded == true && idleGeneration != styleGeneration
        ) {
            idleGeneration = styleGeneration
            onStatus("MapLibre Native: recursos e renderização prontos · estilo $styleGeneration")
        }
    }

    init {
        requireMainThread()
        mapView.addOnDidFailLoadingMapListener(loadErrorListener)
        mapView.addOnDidFinishRenderingFrameListener(frameListener)
        mapView.addOnDidBecomeIdleListener(idleListener)
        mapView.addOnLayoutChangeListener(layoutListener)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { nativeMap ->
            if (!destroyed) {
                map = nativeMap
                if (initialCamera != null || savedInstanceState == null) {
                    nativeMap.cameraPosition = pendingCamera
                } else {
                    pendingCamera = nativeMap.cameraPosition
                }
                nativeMap.setMinZoomPreference(1.0)
                nativeMap.setMaxZoomPreference(18.5)
                nativeMap.addOnCameraMoveStartedListener(cameraMoveListener)
                nativeMap.addOnMapLongClickListener(mapLongClickListener)
                if (navigation != null) {
                    nativeMap.setMinZoomPreference(1.0)
                    updateFollowCamera()
                    if (pendingOverview) fitRouteOverview()
                }
                loadStyle()
            }
        }
    }

    fun start() {
        requireMainThread()
        if (destroyed || started) return
        mapView.onStart()
        started = true
    }

    fun resume() {
        requireMainThread()
        if (destroyed || resumed) return
        start()
        mapView.onResume()
        resumed = true
    }

    fun pause() {
        requireMainThread()
        if (destroyed) return
        if (resumed) {
            mapView.onPause()
            resumed = false
        }
    }

    fun stop() {
        requireMainThread()
        if (destroyed) return
        pause()
        if (started) {
            mapView.onStop()
            started = false
        }
    }

    fun applyStyle(styleJson: String) {
        requireMainThread()
        if (destroyed || pendingStyle == styleJson) return
        val nativeMap = map
        val currentStyle = nativeMap?.style
        val changes = if (styleReady && loadedGeneration == styleGeneration && currentStyle != null)
            poiVisibilityChanges(pendingStyle, styleJson) else null
        if (changes != null && changes.keys.all { currentStyle?.getLayer(it) != null }) {
            pendingStyle = styleJson
            val generation = ++styleGeneration
            idleGeneration = null
            firstFrameReported = false
            changes.forEach { (id, value) -> currentStyle?.getLayer(id)?.setProperties(visibility(value)) }
            loadedGeneration = generation
            nativeMap?.triggerRepaint()
            onStatus("MapLibre Native: estilo carregado")
            return
        }
        pendingStyle = styleJson
        loadStyle()
    }

    private fun loadStyle() {
        val nativeMap = map ?: return
        val generation = ++styleGeneration
        loadedGeneration = null
        idleGeneration = null
        styleReady = false
        firstFrameReported = false
        navigationOverlay.styleLoading()
        onStatus("Carregando mapa…")
        // MapLibre retains its live camera transform across setStyle. Restoring a snapshot
        // here would overwrite direct MapView gestures made while the style is loading.
        nativeMap.setStyle(Style.Builder().fromJson(pendingStyle)) { loadedStyle ->
            if (!destroyed && generation == styleGeneration) {
                loadedGeneration = generation
                styleReady = true
                // Reinstall sources and current icon images before reporting style readiness/idle.
                navigationOverlay.styleLoaded(loadedStyle)
                applyPadding()
                onStatus("MapLibre Native: estilo carregado")
            }
        }
    }

    fun panBy(distanceX: Float, distanceY: Float) {
        requireMainThread()
        if (destroyed || !distanceX.isFinite() || !distanceY.isFinite()) return
        enterManualMode()
        val nativeMap = map ?: return
        nativeMap.cancelTransitions()
        // SurfaceCallback's distances use previous minus current touch positions.
        nativeMap.scrollBy(-distanceX, -distanceY)
    }

    fun zoomBy(amount: Double, focusX: Float? = null, focusY: Float? = null) {
        requireMainThread()
        if (destroyed || !amount.isFinite()) return
        enterManualMode()
        val nativeMap = map ?: return
        nativeMap.cancelTransitions()
        val focus = if (focusX != null && focusY != null &&
            focusX.isFinite() && focusY.isFinite() && focusX >= 0 && focusY >= 0
        ) {
            Point(
                focusX.roundToInt().coerceIn(0, (effectiveWidth - 1).coerceAtLeast(0)),
                focusY.roundToInt().coerceIn(0, (effectiveHeight - 1).coerceAtLeast(0)),
            )
        } else null
        // Native's unfocused update preserves the exact camera target. Rounding an
        // odd-sized viewport's center to an integer pixel drifts the map on +/-.
        nativeMap.moveCamera(if (focus == null) CameraUpdateFactory.zoomBy(amount)
            else CameraUpdateFactory.zoomBy(amount, focus))
    }

    fun scale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        zoomBy(ln(scaleFactor.toDouble()) / ln(2.0), focusX, focusY)
    }

    fun fling(velocityX: Float, velocityY: Float) {
        requireMainThread()
        if (destroyed || !velocityX.isFinite() || !velocityY.isFinite()) return
        enterManualMode()
        val nativeMap = map ?: return
        nativeMap.cancelTransitions()
        // Approximate a short deceleration; bounded so a bad host event cannot launch the map.
        val maxX = effectiveWidth.coerceAtLeast(1).toFloat()
        val maxY = effectiveHeight.coerceAtLeast(1).toFloat()
        nativeMap.scrollBy(
            (velocityX * 0.15f).coerceIn(-maxX, maxX),
            (velocityY * 0.15f).coerceIn(-maxY, maxY),
            300L,
        )
    }

    fun reset() {
        requireMainThread()
        if (destroyed) return
        enterManualMode()
        pendingCamera = defaultCamera()
        map?.let {
            it.cancelTransitions()
            it.cameraPosition = pendingCamera
            applyPadding()
        }
    }

    /** Centers an explicitly selected catalog place; this never reads device location. */
    fun focusOn(latitude: Double, longitude: Double) {
        requireMainThread()
        if (destroyed || !latitude.isFinite() || !longitude.isFinite()) return
        enterManualMode()
        pendingCamera = CameraPosition.Builder(map?.cameraPosition ?: pendingCamera)
            .target(LatLng(latitude, longitude)).zoom(16.0).build()
        map?.let {
            it.cancelTransitions()
            it.cameraPosition = pendingCamera
            applyPadding()
        }
    }

    fun setOnMapDestinationListener(listener: (GeoPoint) -> Unit) {
        requireMainThread()
        destinationListener = listener
    }

    fun setOnCameraModeChangedListener(listener: (Boolean) -> Unit) {
        requireMainThread()
        cameraModeListener = listener
        listener(isFollowing)
    }

    fun updateNavigation(
        state: NavigationSnapshot,
        prefs: NavigationPreferences,
        saved: List<SavedPlace>,
        theme: MapTheme,
    ) {
        requireMainThread()
        if (destroyed) return
        val previousMode = navigationCamera.mode
        val previousSession = navigationCamera.sessionId
        navigation = state
        navigationPreferences = prefs
        navigationCamera.observe(state)
        if (previousSession != navigationCamera.sessionId) {
            followNeedsRecenter = true
            lastFollowCameraKey = null
            pendingOverview = false
        }
        if (previousMode != navigationCamera.mode) cameraModeListener?.invoke(isFollowing)
        navigationOverlay.update(state, prefs, saved, theme)
        map?.setMinZoomPreference(1.0)
        updateFollowCamera()
        if (pendingOverview) fitRouteOverview()
    }

    fun recenter() {
        requireMainThread()
        if (destroyed) return
        navigationCamera.follow()
        pendingOverview = false
        followNeedsRecenter = true
        lastFollowCameraKey = null
        cameraModeListener?.invoke(true)
        updateFollowCamera()
    }

    fun showRouteOverview() {
        requireMainThread()
        if (destroyed) return
        navigationCamera.overview()
        pendingOverview = true
        cameraModeListener?.invoke(false)
        fitRouteOverview()
    }

    internal fun restoreNavigationCamera(mode: NavigationCameraMode, sessionId: Long?) {
        requireMainThread()
        when (mode) {
            NavigationCameraMode.FOLLOW -> navigationCamera.follow()
            NavigationCameraMode.MANUAL -> navigationCamera.manual()
            NavigationCameraMode.OVERVIEW -> navigationCamera.overview()
        }
        // Observe the retained session without resetting manual/overview on surface reconnect.
        if (sessionId != null) navigationCamera.restoreSession(sessionId)
        followNeedsRecenter = false
        cameraModeListener?.invoke(isFollowing)
    }

    private fun enterManualMode() {
        if (destroyed || navigationCamera.mode == NavigationCameraMode.MANUAL) return
        navigationCamera.manual()
        pendingOverview = false
        cameraModeListener?.invoke(false)
    }

    private fun updateFollowCamera() {
        val nativeMap = map ?: return
        val state = navigation ?: return
        if (!isFollowing || !state.hasCurrentFix()) return
        val point = state.renderPosition() ?: return
        val bearing = state.followBearing(navigationPreferences.orientation)
        val key = Triple(point, state.location.fix?.elapsedRealtimeMs ?: 0L, bearing)
        if (!followNeedsRecenter && key == lastFollowCameraKey) return
        val camera = CameraPosition.Builder(nativeMap.cameraPosition)
            .target(LatLng(point.latitude, point.longitude)).bearing(bearing).tilt(0.0)
        if (followNeedsRecenter) camera.zoom(16.0)
        nativeMap.cancelTransitions()
        nativeMap.cameraPosition = camera.build()
        followNeedsRecenter = false
        lastFollowCameraKey = key
        pendingCamera = nativeMap.cameraPosition
        applyPadding()
    }

    private fun fitRouteOverview() {
        val nativeMap = map ?: return
        if (!pendingOverview || effectiveWidth <= 0 || effectiveHeight <= 0) return
        val points = navigation?.route?.geometry?.filter { it.isValidMapPoint() } ?: return
        if (points.isEmpty()) return
        nativeMap.cancelTransitions()
        nativeMap.setMinZoomPreference(1.0)
        if (points.distinct().size == 1) {
            nativeMap.cameraPosition = CameraPosition.Builder(nativeMap.cameraPosition)
                .target(LatLng(points[0].latitude, points[0].longitude)).zoom(16.0).bearing(0.0).tilt(0.0).build()
        } else {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
            val safe = contentArea()
            val margin = (32 * mapView.resources.displayMetrics.density).roundToInt()
                .coerceAtMost((minOf(safe.width(), safe.height()) / 4).coerceAtLeast(0))
            nativeMap.getCameraForLatLngBounds(bounds, intArrayOf(
                safe.left + margin, safe.top + margin,
                effectiveWidth - safe.right + margin, effectiveHeight - safe.bottom + margin,
            ), 0.0, 0.0)?.let { nativeMap.cameraPosition = it }
        }
        pendingCamera = nativeMap.cameraPosition
        pendingOverview = false
        applyPadding()
    }

    fun setViewport(width: Int, height: Int, visible: Rect?, stable: Rect?) {
        requireMainThread()
        if (destroyed) return
        viewportWidth = width.coerceAtLeast(0)
        viewportHeight = height.coerceAtLeast(0)
        visibleArea = visible?.let(::Rect)
        stableArea = stable?.let(::Rect)
        applyPadding()
    }

    private fun contentArea(): Rect {
        val bounds = Rect(0, 0, effectiveWidth, effectiveHeight)
        val safe = Rect(bounds)
        // Empty rectangles mean unknown, rather than an entirely obscured map.
        for (area in listOf(stableArea, visibleArea)) {
            if (area != null && !area.isEmpty) {
                val intersection = Rect(safe)
                if (intersection.intersect(area)) safe.set(intersection)
            }
        }
        return safe
    }

    private fun applyPadding() {
        val nativeMap = map ?: return
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val safe = contentArea()
        nativeMap.moveCamera(
            CameraUpdateFactory.paddingTo(
                safe.left.toDouble(), safe.top.toDouble(),
                (viewportWidth - safe.right).toDouble(),
                (viewportHeight - safe.bottom).toDouble(),
            ),
        )
    }

    fun onLowMemory() {
        requireMainThread()
        if (!destroyed) mapView.onLowMemory()
    }

    fun saveInstanceState(outState: Bundle) {
        requireMainThread()
        if (!destroyed) {
            mapView.onSaveInstanceState(outState)
            outState.putString(CAMERA_MODE_KEY, navigationCamera.mode.name)
            navigationCamera.sessionId?.let { outState.putLong(CAMERA_SESSION_KEY, it) }
        }
    }

    override fun close() {
        requireMainThread()
        if (destroyed) return
        pendingCamera = cameraState
        stop()
        destroyed = true
        styleGeneration++
        loadedGeneration = null
        idleGeneration = null
        mapView.removeOnDidFailLoadingMapListener(loadErrorListener)
        mapView.removeOnDidFinishRenderingFrameListener(frameListener)
        mapView.removeOnDidBecomeIdleListener(idleListener)
        mapView.removeOnLayoutChangeListener(layoutListener)
        map?.removeOnCameraMoveStartedListener(cameraMoveListener)
        map?.removeOnMapLongClickListener(mapLongClickListener)
        mapView.onDestroy()
        map = null
    }

    companion object {
        const val PROBE_STYLE_ASSET = "maps/probe-style.json"
        private const val CAMERA_MODE_KEY = "gamemap.navigation.camera.mode"
        private const val CAMERA_SESSION_KEY = "gamemap.navigation.camera.session"

        fun probeStyle(context: Context): String =
            context.assets.open(PROBE_STYLE_ASSET).bufferedReader().use { it.readText() }

        /** Must be called before constructing the renderer; each display gets its own view. */
        fun createMapView(context: Context, textureMode: Boolean = false): MapView {
            requireMainThread()
            MapLibre.getInstance(context.applicationContext)
            return MapView(
                context,
                MapLibreMapOptions.createFromAttributes(context)
                    .textureMode(textureMode)
                    .compassEnabled(false)
                    .logoEnabled(false)
                    .attributionEnabled(false)
                    .rotateGesturesEnabled(false)
                    .tiltGesturesEnabled(false),
            )
        }

        fun defaultCamera(): CameraPosition = CameraPosition.Builder()
            .target(LatLng(-16.6809224, -49.2562748))
            .zoom(14.4)
            .bearing(0.0)
            .tilt(0.0)
            .build()

        private fun requireMainThread() {
            check(Looper.myLooper() == Looper.getMainLooper()) {
                "MapLibre Native must be accessed on the Android main thread"
            }
        }
    }
}
