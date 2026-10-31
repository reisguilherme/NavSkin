package br.com.gamemap.render

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import br.com.gamemap.navigation.NavigationPreferences
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.storage.SavedPlace
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.MapPreferences
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style

/** Renders a separate native map into the Surface delivered by the Android Auto host. */
class CarSurfaceRenderer(
    private val carContext: Context,
    styleJson: String = NativeMapRenderer.probeStyle(carContext),
    private val onStatus: (String) -> Unit = {},
) : SurfaceCallback, AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private var style = styleJson
    private var mapPreferences = MapPreferences()
    private var closed = false
    private var active = true
    private var surface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MapPresentation? = null
    private var width = 0
    private var height = 0
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null
    private var savedCamera = NativeMapRenderer.defaultCamera()
    private var savedCameraMode = NavigationCameraMode.FOLLOW
    private var savedSessionId: Long? = null
    private var savedFlatOverview = false
    private var pendingNavigation: PendingNavigation? = null
    private var cameraModeListener: ((Boolean) -> Unit)? = null
    private var overviewRequested = false
    private var recenterRequested = false

    private data class PendingNavigation(
        val snapshot: NavigationSnapshot,
        val preferences: NavigationPreferences,
        val saved: List<SavedPlace>,
        val theme: MapTheme,
    )

    val isFollowing: Boolean get() = presentation?.renderer?.isFollowing
        ?: (savedCameraMode == NavigationCameraMode.FOLLOW)

    val cameraState: CameraPosition
        get() {
            check(Looper.myLooper() == Looper.getMainLooper())
            return presentation?.renderer?.cameraState ?: savedCamera
        }

    internal val styleState: NativeMapRenderer.StyleState?
        get() {
            check(Looper.myLooper() == Looper.getMainLooper())
            return presentation?.renderer?.styleState
        }

    internal fun inspectLoadedStyle(inspect: (Style) -> Unit): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        return presentation?.renderer?.inspectLoadedStyle(inspect) ?: false
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) = onMain {
        val incoming = surfaceContainer.surface
        if (closed) {
            incoming?.release()
            return@onMain
        }
        // Size or density can change without onSurfaceDestroyed. Recreate the display/view,
        // preserving camera, so Presentation resources also receive the new density.
        val reusedSurface = surface === incoming
        detach(releaseSurface = !reusedSurface)
        if (incoming == null || !incoming.isValid || surfaceContainer.width <= 0 ||
            surfaceContainer.height <= 0
        ) {
            incoming?.release()
            report("Android Auto: aguardando uma superfície válida")
            return@onMain
        }

        surface = incoming
        width = surfaceContainer.width
        height = surfaceContainer.height
        val density = surfaceContainer.dpi.takeIf { it > 0 }
            ?: carContext.resources.displayMetrics.densityDpi.coerceAtLeast(1)
        try {
            val display = displayManager.createVirtualDisplay(
                "NavSkin Android Auto Native Map", width, height, density, incoming,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            ) ?: error("O Android não criou o display virtual")
            virtualDisplay = display
            val nextPresentation = MapPresentation(carContext, display.display)
            presentation = nextPresentation
            nextPresentation.show()
            updateViewport()
            report("Android Auto: superfície nativa ${width}×${height}, ${density} dpi")
        } catch (error: RuntimeException) {
            detach()
            report("Android Auto: falha ao anexar superfície: ${error.message}")
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = onMain {
        val incoming = surfaceContainer.surface
        val current = surface
        detach()
        // A destroyed callback can parcel a separate Surface wrapper. Release that reference too.
        if (incoming !== current) incoming?.release()
        visibleArea = null
        stableArea = null
        report("Android Auto: superfície desconectada")
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) = onMain {
        this.visibleArea = Rect(visibleArea)
        updateViewport()
    }

    override fun onStableAreaChanged(stableArea: Rect) = onMain {
        this.stableArea = Rect(stableArea)
        updateViewport()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) = onMain {
        presentation?.renderer?.panBy(distanceX, distanceY)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) = onMain {
        presentation?.renderer?.scale(focusX, focusY, scaleFactor)
    }

    override fun onFling(velocityX: Float, velocityY: Float) = onMain {
        presentation?.renderer?.fling(velocityX, velocityY)
    }

    fun applyStyle(styleJson: String) = onMain {
        if (!closed) {
            style = styleJson
            presentation?.renderer?.applyStyle(styleJson)
            presentation?.updateAttribution()
        }
    }

    fun applyMapPreferences(preferences: MapPreferences) = onMain {
        if (!closed) {
            if (mapPreferences.browse3D != preferences.browse3D || mapPreferences.navigation3D != preferences.navigation3D)
                savedFlatOverview = savedCameraMode == NavigationCameraMode.OVERVIEW
            mapPreferences = preferences
            presentation?.renderer?.applyMapPreferences(preferences)
        }
    }

    fun zoomBy(amount: Double) = onMain { presentation?.renderer?.zoomBy(amount) }

    fun panBy(distanceX: Float, distanceY: Float) = onMain {
        presentation?.renderer?.panBy(distanceX, distanceY)
    }

    fun reset() = onMain {
        savedCameraMode = NavigationCameraMode.MANUAL
        savedFlatOverview = false
        overviewRequested = false
        recenterRequested = false
        savedCamera = NativeMapRenderer.defaultCamera()
        presentation?.renderer?.reset()
    }

    fun focusOn(latitude: Double, longitude: Double) = onMain {
        if (!closed && latitude.isFinite() && longitude.isFinite()) {
            savedCameraMode = NavigationCameraMode.MANUAL
            savedFlatOverview = false
            overviewRequested = false
            recenterRequested = false
            savedCamera = CameraPosition.Builder(cameraState)
                .target(LatLng(latitude, longitude)).zoom(16.0)
                .padding(0.0, 0.0, 0.0, 0.0).build()
            presentation?.renderer?.focusOn(latitude, longitude)
        }
    }

    fun setOnCameraModeChangedListener(listener: (Boolean) -> Unit) = onMain {
        cameraModeListener = listener
        listener(isFollowing)
    }

    fun updateNavigation(
        state: NavigationSnapshot,
        prefs: NavigationPreferences,
        saved: List<SavedPlace>,
        theme: MapTheme,
    ) = onMain {
        if (!closed) {
            pendingNavigation = PendingNavigation(state, prefs, saved.toList(), theme)
            val current = presentation
            if (current == null && state.navigationActive && state.sessionId != savedSessionId) {
                savedCameraMode = NavigationCameraMode.FOLLOW
                savedFlatOverview = false
                overviewRequested = false
            }
            current?.renderer?.updateNavigation(state, prefs, saved, theme)
            current?.updateSpeed()
            current?.updateAttribution()
        }
    }

    fun recenter() = onMain {
        if (!closed) {
            savedCameraMode = NavigationCameraMode.FOLLOW
            savedFlatOverview = false
            overviewRequested = false
            recenterRequested = presentation?.renderer == null
            presentation?.renderer?.recenter()
            cameraModeListener?.invoke(true)
        }
    }

    fun showRouteOverview() = onMain {
        if (!closed) {
            savedCameraMode = NavigationCameraMode.OVERVIEW
            savedFlatOverview = true
            overviewRequested = presentation?.renderer == null
            recenterRequested = false
            presentation?.renderer?.showRouteOverview()
            cameraModeListener?.invoke(false)
        }
    }

    fun onLowMemory() = onMain { presentation?.renderer?.onLowMemory() }

    fun start() = onMain {
        if (!closed) {
            active = true
            presentation?.renderer?.start()
            presentation?.renderer?.resume()
        }
    }

    fun stop() = onMain {
        active = false
        presentation?.renderer?.pause()
        presentation?.renderer?.stop()
    }

    private fun updateViewport() {
        presentation?.setViewport(width, height, visibleArea, stableArea)
    }

    private fun detach(releaseSurface: Boolean = true) {
        presentation?.let {
            it.renderer?.let { renderer ->
                savedCameraMode = renderer.navigationCameraMode
                savedSessionId = renderer.navigationSessionId
                savedFlatOverview = renderer.isFlatOverview
            }
            // Insets belong to the old host viewport and may exceed a smaller replacement.
            savedCamera = CameraPosition.Builder(it.renderer?.cameraState ?: savedCamera)
                .padding(0.0, 0.0, 0.0, 0.0)
                .build()
            it.release()
        }
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        if (releaseSurface) surface?.release()
        surface = null
        width = 0
        height = 0
    }

    override fun close() = onMain {
        if (!closed) {
            closed = true
            detach()
            visibleArea = null
            stableArea = null
        }
    }

    private fun report(message: String) {
        Log.i("GameMapSurface", message)
        if (!closed) onStatus(message)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private inner class MapPresentation(context: Context, display: Display) :
        Presentation(context, display) {
        var renderer: NativeMapRenderer? = null
            private set
        private var attribution: TextView? = null
        private var speed: TextView? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
                setBackgroundDrawable(ColorDrawable(Color.BLACK))
            }
            val presentationContext = getContext()
            val mapView = NativeMapRenderer.createMapView(presentationContext, textureMode = true)
            val content = FrameLayout(presentationContext)
            content.addView(mapView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            attribution = TextView(presentationContext).also { credit ->
                credit.setTextColor(Color.WHITE)
                credit.setBackgroundColor(0xD9222930.toInt())
                credit.textSize = 11f
                credit.setPadding(dp(8), dp(3), dp(8), dp(3))
                credit.isClickable = false
                credit.isFocusable = false
                content.addView(credit, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START,
                ))
            }
            // A passive current-speed display is allowed on a navigation map Surface.
            // All maneuver instructions and actions remain in the host's navigation template.
            speed = TextView(presentationContext).also { label ->
                label.setTextColor(Color.WHITE)
                label.setBackgroundColor(0xF0182028.toInt())
                label.textSize = 22f
                label.setPadding(dp(12), dp(7), dp(12), dp(7))
                label.isClickable = false
                label.isFocusable = false
                label.visibility = View.GONE
                content.addView(label, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ))
            }
            setContentView(content)
            renderer = NativeMapRenderer(
                presentationContext, mapView, style, ::report, initialCamera = savedCamera,
            )
            renderer?.applyMapPreferences(mapPreferences)
            renderer?.restoreNavigationCamera(savedCameraMode, savedSessionId, savedFlatOverview)
            renderer?.setOnCameraModeChangedListener { following -> cameraModeListener?.invoke(following) }
            pendingNavigation?.let { pending ->
                renderer?.updateNavigation(pending.snapshot, pending.preferences, pending.saved, pending.theme)
            }
            if (overviewRequested) renderer?.showRouteOverview()
            if (recenterRequested) renderer?.recenter()
            overviewRequested = false
            recenterRequested = false
            updateAttribution()
            updateSpeed()
            setOnDismissListener {
                renderer?.close()
                renderer = null
            }
        }

        override fun onStart() {
            super.onStart()
            if (active && !closed) {
                renderer?.start()
                renderer?.resume()
            }
        }

        override fun onStop() {
            renderer?.pause()
            renderer?.stop()
            super.onStop()
        }

        fun setViewport(width: Int, height: Int, visible: Rect?, stable: Rect?) {
            renderer?.setViewport(width, height, visible, stable)
            val safe = renderer?.safeArea ?: Rect(0, 0, width, height)
            attribution?.let { credit ->
                credit.maxWidth = safe.width().coerceAtLeast(1)
                credit.layoutParams = (credit.layoutParams as FrameLayout.LayoutParams).apply {
                    leftMargin = safe.left
                    bottomMargin = height - safe.bottom
                }
            }
            speed?.let { label ->
                label.maxWidth = (safe.width() - dp(16)).coerceAtLeast(1)
                label.layoutParams = (label.layoutParams as FrameLayout.LayoutParams).apply {
                    leftMargin = safe.left + dp(8)
                    topMargin = safe.top + dp(8)
                }
            }
        }

        fun updateSpeed() {
            val state = pendingNavigation?.snapshot
            speed?.apply {
                visibility = if (state == null) View.GONE else View.VISIBLE
                text = state?.surfaceSpeedText().orEmpty()
                // Simulation cannot be mistaken for the vehicle's measured speed.
                setTextColor(if (state?.simulation == true) 0xFFFFDF64.toInt() else Color.WHITE)
                contentDescription = text
            }
        }

        fun updateAttribution() {
            attribution?.text = mapAttributionText(style, withNavigationProvider = pendingNavigation != null)
        }

        private fun dp(value: Int): Int =
            (value * getContext().resources.displayMetrics.density + 0.5f).toInt()

        fun release() {
            renderer?.close()
            renderer = null
            dismiss()
        }
    }
}
