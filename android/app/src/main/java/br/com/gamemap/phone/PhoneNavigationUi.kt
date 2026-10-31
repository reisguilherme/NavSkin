package br.com.gamemap.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import br.com.gamemap.BuildConfig
import br.com.gamemap.data.OfflineMapCoverage
import br.com.gamemap.GameMapApplication
import br.com.gamemap.R
import br.com.gamemap.car.CarDestinationIntent
import br.com.gamemap.car.CarNavigationIntents
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import br.com.gamemap.navigation.storage.CatalogIcon
import br.com.gamemap.navigation.storage.SavedPlace
import br.com.gamemap.render.NativeMapRenderer
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.traffic.TrafficStatus
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Retractable phone controls observing the same session used by the car and foreground service. */
internal class PhoneNavigationUi(
    private val activity: ComponentActivity,
    private val root: FrameLayout,
    private val renderer: NativeMapRenderer,
    restoredState: android.os.Bundle?,
    private val closeAppearanceSheet: () -> Unit,
    private val openMapSettings: () -> Unit,
    private val openMapFilters: () -> Unit,
    private val onGuidanceChanged: (Boolean) -> Unit,
    private val onVibingChanged: (Boolean) -> Unit = {},
) {
    private val app get() = activity.application as GameMapApplication
    private val runtime get() = app.navigation
    private var colors = PhonePalette.forTheme(app.preferences.snapshot().theme)
    private var state = runtime.snapshot()
    private val removers = mutableListOf<() -> Unit>()
    private var dialog: BottomSheetDialog? = null
    private var searchResults: LinearLayout? = null
    private var searchStatus: TextView? = null
    private var routeStatus: TextView? = null
    private var routeStops: LinearLayout? = null
    private var routeRetry: TextView? = null
    private var lastRouteStopsKey: String? = null
    private var lastSearch: Pair<List<Destination>, String?>? = null
    private var afterPermission: (() -> Unit)? = null
    private var topInset = 0
    private var bottomInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var currentMapNotice: String? = null
    private var lastPreviewId: String? = restoredState?.getString(PREVIEW_STATE)
    private var selectingStop = restoredState?.getBoolean(ADD_STOP_STATE) == true
    private var restoreStopSearch = selectingStop
    private var searchQuery = restoredState?.getString(SEARCH_QUERY_STATE).orEmpty()
    private var menuPopup: PopupWindow? = null
    private var vibing = restoredState?.getBoolean(VIBING_STATE) == true
    private val vibingControls = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = View.GONE
    }
    private val exitVibing = PhoneAction(activity, R.drawable.phone_close, "Sair do Vibing", showLabel = true).apply {
        id = R.id.phone_vibing_exit
        setOnClickListener { setVibing(false) }
    }
    private val vibingRecenter = PhoneAction(activity, R.drawable.phone_locate, "Recentralizar no Vibing").apply {
        id = R.id.phone_vibing_recenter
        setOnClickListener { renderer.recenter(); hideVibingControls.run() }
    }
    private val hideVibingControls = Runnable { vibingControls.visibility = View.GONE }
    private val vibingBack = object : OnBackPressedCallback(vibing) {
        override fun handleOnBackPressed() { setVibing(false) }
    }
    private var lastStopsKey: String? = null
    private val search = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
    private val searchLabel = label("Para onde?", 16f, true)
    private val clearSearch = PhoneAction(activity, R.drawable.phone_close, "Limpar destino e busca").apply {
        id = R.id.phone_clear_destination
        setOnClickListener { clearDestination() }
        visibility = View.GONE
    }
    private val guidance = GuidanceCard(activity)
    private val simulationNotice = label("SIMULAÇÃO GPS", 10f, true).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(90, 60, 0))
        setPadding(dp(10), dp(5), dp(10), dp(5))
        background = activity.rounded(Color.rgb(255, 229, 146), 10)
        visibility = View.GONE
    }
    private val summary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val preparationProgress = ProgressBar(activity).apply {
        isIndeterminate = true
        visibility = View.GONE
        contentDescription = "Preparando rota"
    }
    private val metrics = label("", 18f, true).apply { gravity = Gravity.CENTER }
    private val tripMetrics = TripMetrics(activity)
    private val tripActions = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val stopAction = label("Parar", 14f, true)
    private val addStopAction = label("Adicionar parada", 14f, true)
    private val stopList = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val stops = HorizontalScrollView(activity).apply {
        id = R.id.phone_stops
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        addView(stopList)
        visibility = View.GONE
    }
    private var lastLayoutKey: List<Int>? = null
    private var lastTracking: Boolean? = null
    private var trafficMessage = "Configure uma chave TomTom e ative a camada para consultar o trânsito."
    private var trafficDetails: TextView? = null
    private val trafficBadge = label("Trânsito · © TomTom", 10f, true).apply {
        gravity = Gravity.CENTER
        maxLines = 2
        setPadding(dp(8), dp(6), dp(8), dp(6))
        minimumHeight = dp(48)
        setOnClickListener { showTraffic() }
        visibility = View.GONE
    }
    private val primary = label("", 14f, true)
    private val speedNumber = label("—", 24f, true).apply { gravity = Gravity.CENTER; maxLines = 1 }
    private val speedUnit = label("km/h", 9f).apply { gravity = Gravity.CENTER; maxLines = 1 }
    private val speedBadge = LinearLayout(activity).apply {
        id = R.id.phone_speed
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true; isFocusable = true
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        addView(speedNumber, LinearLayout.LayoutParams(WRAP, WRAP))
        addView(speedUnit, LinearLayout.LayoutParams(WRAP, WRAP))
        setOnClickListener { showOptions() }
    }
    private val recenter = PhoneAction(activity, R.drawable.phone_locate, "Recentralizar").apply {
        id = R.id.phone_recenter
        setOnClickListener { withGps { renderer.recenter() } }
    }
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        runtime.setLocationPermission(fine, coarse)
        val continuation = afterPermission
        afterPermission = null
        if (fine) { runtime.activateLocation(); continuation?.invoke() }
        else toast(if (coarse) "Para navegar, permita localização precisa nas configurações do aparelho."
            else "Localização não permitida. Seus locais e o mapa continuam disponíveis.")
    }

    init {
        search.id = R.id.phone_search
        search.minimumHeight = dp(60)
        search.gravity = Gravity.CENTER_VERTICAL
        search.setPadding(dp(18), dp(4), dp(6), dp(4))
        searchLabel.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.phone_search, 0, 0, 0)
        searchLabel.compoundDrawablePadding = dp(12)
        searchLabel.maxLines = 1
        searchLabel.ellipsize = android.text.TextUtils.TruncateAt.END
        search.addView(searchLabel, LinearLayout.LayoutParams(0, WRAP, 1f))
        search.addView(clearSearch, LinearLayout.LayoutParams(dp(48), dp(48)))
        search.contentDescription = "Para onde? Buscar destinos"
        search.isFocusable = true
        search.setOnClickListener { showDestinations() }
        add(search, Gravity.BOTTOM, MATCH, WRAP)
        guidance.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        guidance.visibility = View.GONE
        add(guidance, Gravity.TOP, MATCH, WRAP)
        guidance.menu.setOnClickListener { showOptions() }
        add(simulationNotice, Gravity.TOP or Gravity.START, WRAP, WRAP)
        summary.setPadding(dp(11), dp(8), dp(11), dp(11))
        summary.id = R.id.phone_route_summary
        summary.minimumHeight = dp(48)
        summary.addView(preparationProgress, LinearLayout.LayoutParams(dp(20), dp(20)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(6)
        })
        summary.addView(metrics)
        summary.addView(tripMetrics, LinearLayout.LayoutParams(MATCH, WRAP))
        summary.addView(primary, LinearLayout.LayoutParams(MATCH, dp(48)).apply { topMargin = dp(6) })
        stopAction.id = R.id.phone_stop_navigation
        addStopAction.id = R.id.phone_add_stop
        listOf(stopAction, addStopAction).forEach {
            it.gravity = Gravity.CENTER
            it.minimumHeight = dp(48)
            it.setPadding(dp(7), dp(10), dp(7), dp(10))
            it.isClickable = true
            it.isFocusable = true
        }
        stopAction.setOnClickListener { selectingStop = false; runtime.stopNavigation() }
        addStopAction.setOnClickListener { showDestinations(addStop = true) }
        tripActions.addView(stopAction, LinearLayout.LayoutParams(0, WRAP, .88f).apply { marginEnd = dp(8) })
        tripActions.addView(addStopAction, LinearLayout.LayoutParams(0, WRAP, 1.6f))
        summary.addView(tripActions, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(5) })
        primary.gravity = Gravity.CENTER
        primary.setOnClickListener { primaryAction() }
        summary.setOnClickListener { showRoute() }
        summary.visibility = View.GONE
        add(summary, Gravity.BOTTOM, MATCH, WRAP)
        add(stops, Gravity.BOTTOM, MATCH, WRAP)
        val diameter = dp(if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 54 else 64)
        add(speedBadge, Gravity.BOTTOM or Gravity.START, diameter, diameter)
        add(recenter, Gravity.BOTTOM or Gravity.END, diameter, diameter)
        add(trafficBadge, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, dp(154), dp(48))
        vibingControls.addView(exitVibing, LinearLayout.LayoutParams(WRAP, dp(48)))
        vibingControls.addView(vibingRecenter, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginStart = dp(8) })
        add(vibingControls, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, WRAP, WRAP)
        activity.onBackPressedDispatcher.addCallback(activity, vibingBack)
        listOf(search, summary, stops, guidance).forEach { view -> view.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) position()
        }
        }
        renderer.setOnMapDestinationListener { point -> showPoint(point) }
        renderer.setOnMapTapListener {
            if (vibing) { revealVibingControls(); true } else false
        }
        renderer.setOnCameraModeChangedListener { following ->
            recenter.isSelected = following
            updateRecenterAppearance()
        }
        renderer.setOnTrafficStatusChangedListener { status ->
            trafficMessage = status.displayText() + status.incidents.take(10).joinToString("", prefix = "") { incident ->
                "\n\n${incident.kind.label} · ${incident.description.take(220)}"
            } + if (status.incidents.size > 10) "\n\nMais ${status.incidents.size - 10} ocorrências na área consultada." else ""
            trafficDetails?.updateText(trafficMessage)
            trafficBadge.updateText(when (status.state) {
                TrafficStatus.AUTHENTICATION -> "Verificar chave\n© TomTom"
                TrafficStatus.RATE_LIMIT -> "Limite de trânsito\n© TomTom"
                TrafficStatus.OFFLINE -> "Trânsito sem conexão\n© TomTom"
                TrafficStatus.UNAVAILABLE -> "Trânsito indisponível\n© TomTom"
                TrafficStatus.LOADING -> "Consultando trânsito\n© TomTom"
                TrafficStatus.WAITING_ZOOM -> "Aproxime para trânsito\n© TomTom"
                else -> "Trânsito · © TomTom"
            })
            trafficBadge.contentDescription = "Trânsito TomTom. $trafficMessage. Abrir opções de trânsito."
        }
        themeChanged()
        renderer.setVibing(vibing)
        onVibingChanged(vibing)
    }

    fun start() {
        removers += runtime.attachClient("phone")
        removers += runtime.addListener { state = it; render() }
        removers += app.navigationSettings.addListener { render() }
        removers += app.places.addListener { render() }
        runtime.setLocationPermission(granted(Manifest.permission.ACCESS_FINE_LOCATION), granted(Manifest.permission.ACCESS_COARSE_LOCATION))
        if (vibing) {
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) runtime.activateLocation()
            else setVibing(false)
        }
        if (restoreStopSearch && state.destination != null) {
            restoreStopSearch = false
            root.post { showDestinations(addStop = true) }
        }
    }
    fun stop() { root.removeCallbacks(hideVibingControls); hideVibingControls.run(); removers.toList().forEach { it() }; removers.clear() }
    fun close() { stop(); vibingBack.remove(); menuPopup?.dismiss(); dialog?.dismiss(); dialog = null }
    fun saveState(out: android.os.Bundle) {
        out.putString(PREVIEW_STATE, lastPreviewId)
        out.putBoolean(ADD_STOP_STATE, selectingStop && dialog?.isShowing == true)
        out.putString(SEARCH_QUERY_STATE, searchQuery)
        out.putBoolean(VIBING_STATE, vibing)
    }
    fun handleIntent(intent: Intent) {
        if (CarNavigationIntents.parse(intent) != null && vibing) setVibing(false)
        when (val request = CarNavigationIntents.parse(intent)) {
            is CarDestinationIntent.Place -> { closeAppearanceSheet(); choose(request.destination, asStop = false) }
            is CarDestinationIntent.Search -> { showDestinations(request.query); runtime.search(request.query) }
            null -> Unit
        }
    }
    fun themeChanged() {
        colors = PhonePalette.forTheme(app.preferences.snapshot().theme)
        menuPopup?.dismiss()
        search.surface(colors, 23)
        searchLabel.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(colors.muted)
        clearSearch.bind(colors)
        guidance.bind(colors)
        tripMetrics.bind(colors)
        summary.surface(colors, 24)
        speedBadge.surface(colors, 100)
        trafficBadge.surface(colors, 18)
        trafficBadge.setTextColor(colors.text)
        recenter.bind(colors)
        recenter.surface(colors, 100)
        exitVibing.bind(colors)
        exitVibing.surface(colors, 100)
        vibingRecenter.bind(colors)
        vibingRecenter.surface(colors, 100)
        lastTracking = null
        listOf(searchLabel, metrics).forEach { it.setTextColor(colors.text) }
        speedNumber.setTextColor(colors.text)
        speedUnit.setTextColor(colors.muted)
        preparationProgress.indeterminateTintList = android.content.res.ColorStateList.valueOf(colors.accent)
        primary.setTextColor(Color.WHITE)
        primary.touchSurface(colors.accent, 14)
        stopAction.setTextColor(Color.WHITE)
        stopAction.touchSurface(Color.rgb(222, 53, 66), 15)
        addStopAction.setTextColor(colors.text)
        addStopAction.touchSurface(0xbbffffff.toInt(), 15, Color.WHITE)
        lastStopsKey = null
        render()
    }
    fun mapNotice(value: String?) {
        if (currentMapNotice != value) { currentMapNotice = value; render() }
    }
    fun mapAvailabilityChanged() { render() }
    fun insets(left: Int, top: Int, right: Int, bottom: Int) {
        leftInset = left; topInset = top; rightInset = right; bottomInset = bottom
        position()
    }
    private fun position() {
        place(vibingControls, bottom = bottomInset + dp(20))
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val viewportWidth = root.width.takeIf { it > 0 } ?: activity.resources.displayMetrics.widthPixels
        val active = state.navigationActive || state.simulation
        val layoutKey = listOf(viewportWidth, root.height, leftInset, rightInset, topInset, bottomInset,
            search.height, summary.height, stops.height, guidance.height, stops.visibility, summary.visibility,
            if (active) 1 else 0, if (landscape) 1 else 0)
        if (layoutKey == lastLayoutKey) return
        lastLayoutKey = layoutKey
        guidance.compact(landscape)
        val availableWidth = viewportWidth - leftInset - rightInset - dp(28)
        val panelWidth = if (landscape) minOf(dp(330), (availableWidth - dp(20)) / 2) else availableWidth
        search.layoutParams = (search.layoutParams as FrameLayout.LayoutParams).apply {
            width = if (landscape) minOf(dp(300), availableWidth - dp(144)) else panelWidth
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        }
        guidance.layoutParams = (guidance.layoutParams as FrameLayout.LayoutParams).apply {
            width = panelWidth; gravity = Gravity.TOP or Gravity.START
        }
        summary.layoutParams = (summary.layoutParams as FrameLayout.LayoutParams).apply {
            width = panelWidth
            gravity = Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.CENTER_HORIZONTAL
        }
        val base = bottomInset + dp(14)
        val searchHeight = if (search.visibility == View.VISIBLE) search.height.coerceAtLeast(dp(60)) else 0
        val summaryHeight = if (summary.visibility == View.VISIBLE) summary.height.coerceAtLeast(dp(128)) else 0
        val stopsHeight = if (stops.visibility == View.VISIBLE) stops.height.coerceAtLeast(dp(48)) + dp(8) else 0
        val summaryBottom = base + if (!active && searchHeight > 0) searchHeight + dp(10) else 0
        place(search, bottom = base)
        place(guidance, top = topInset + dp(12))
        place(simulationNotice, top = topInset + dp(20) + guidance.height.coerceAtLeast(dp(if (landscape) 88 else 102)))
        place(summary, bottom = summaryBottom)
        stops.layoutParams = (stops.layoutParams as FrameLayout.LayoutParams).apply {
            width = panelWidth
            gravity = Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.CENTER_HORIZONTAL
        }
        place(stops, bottom = summaryBottom + summaryHeight + dp(8))
        val circleBottom = if (landscape && !active && summaryHeight == 0) base
            else if (summaryHeight > 0) summaryBottom + summaryHeight + stopsHeight + dp(12)
            else base + searchHeight + dp(12)
        val circleSize = dp(if (landscape) 54 else 64)
        speedBadge.layoutParams = (speedBadge.layoutParams as FrameLayout.LayoutParams).apply {
            width = circleSize; height = circleSize
            gravity = Gravity.BOTTOM or if (landscape && active) Gravity.END else Gravity.START
        }
        recenter.layoutParams = (recenter.layoutParams as FrameLayout.LayoutParams).apply { width = circleSize; height = circleSize }
        place(speedBadge, bottom = circleBottom)
        if (landscape && active) speedBadge.layoutParams = (speedBadge.layoutParams as FrameLayout.LayoutParams).apply {
            marginEnd = rightInset + dp(14) + panelWidth - circleSize
        }
        place(recenter, bottom = circleBottom)
        trafficBadge.layoutParams = (trafficBadge.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            width = minOf(dp(154), (availableWidth - 2 * circleSize - dp(16)).coerceAtLeast(dp(80)))
        }
        place(trafficBadge, bottom = circleBottom + (circleSize - dp(48)) / 2)
        if (guidance.menu.parent === root) place(guidance.menu, top = topInset + dp(12))
    }
    private fun render() {
        val active = state.navigationActive || state.simulation
        speedBadge.visibility = View.VISIBLE
        recenter.visibility = View.VISIBLE
        guidance.menu.visibility = View.VISIBLE
        trafficBadge.visibility = if (app.navigationSettings.trafficEnabled() && !active) View.VISIBLE else View.GONE
        search.visibility = if (active) View.GONE else View.VISIBLE
        tripActions.visibility = if (active) View.VISIBLE else View.GONE
        simulationNotice.visibility = if (state.simulation) View.VISIBLE else View.GONE
        if (active && guidance.menu.parent !== guidance) guidance.attachMenu()
        else if (!active && guidance.menu.parent !== root) {
            (guidance.menu.parent as? ViewGroup)?.removeView(guidance.menu)
            add(guidance.menu, Gravity.TOP or Gravity.END, dp(52), dp(52))
        }
        if (!active) guidance.menu.surface(colors, 100)
        renderer.updateNavigation(state, app.navigationSettings.snapshot(), app.places.snapshot().savedPlaces, app.preferences.snapshot().theme)
        updateRecenterAppearance()
        val visible = state.destination != null || state.resumeDestination != null
        summary.visibility = if (visible) View.VISIBLE else View.GONE
        val speed = state.location.speedKmh?.takeIf { state.location.quality == LocationQuality.GOOD }?.roundToInt()?.let { "$it km/h" } ?: "— km/h"
        val progress = state.progress.takeIf { state.guidanceAvailable }
        val preparing = RoutePreparationUi.forState(state, granted(Manifest.permission.ACCESS_FINE_LOCATION))
        preparationProgress.visibility = if (preparing?.busy == true && !active) View.VISIBLE else View.GONE
        val estimate = when {
            progress != null -> Triple(progress.remainingSeconds, progress.remainingDistanceMeters, progress.arrivalEpochMs)
            state.phase == NavigationPhase.PREVIEW && state.route != null -> state.route!!.let {
                Triple(it.durationSeconds, it.distanceMeters, System.currentTimeMillis() + (it.durationSeconds * 1000).toLong())
            }
            else -> null
        }
        tripMetrics.visibility = if (estimate != null || active) View.VISIBLE else View.GONE
        metrics.visibility = if (estimate == null && !active) View.VISIBLE else View.GONE
        estimate?.let { tripMetrics.update(duration(it.first), distance(it.second), eta(it.third)) }
        if (estimate == null && active) tripMetrics.update("—", "—", "—")
        if (estimate == null) metrics.updateText(when {
            preparing != null -> preparing.message
            state.phase == NavigationPhase.ARRIVED -> "Você chegou!"
            state.phase == NavigationPhase.RESUME_AVAILABLE -> "Retomar destino?"
            else -> state.destination?.name ?: "Sua posição"
        })
        val quality = when (state.locationAccess) {
            LocationAccess.AVAILABLE -> "GPS ±${state.location.fix?.accuracyMeters?.roundToInt() ?: 0} m"
            LocationAccess.WEAK -> "GPS impreciso"
            LocationAccess.LOST -> "Sinal GPS perdido"
            LocationAccess.DISABLED -> "Localização desativada"
            LocationAccess.DENIED -> "Permita GPS preciso"
            LocationAccess.NEEDS_PHONE_START -> "Ative GPS no celular"
            else -> "Aguardando GPS"
        }
        val notice = if (state.simulation) "SIMULAÇÃO · " else ""
        speedNumber.updateText(speed.substringBefore(" km/h"))
        speedBadge.contentDescription = "${notice}Velocidade: $speed · $quality"
        guidance.visibility = if (active) View.VISIBLE else View.GONE
        onGuidanceChanged(!vibing && guidance.visibility == View.VISIBLE)
        if (progress != null) {
            val step = state.route?.steps?.getOrNull(progress.stepIndex)
            guidance.update(distance(progress.distanceToManeuverMeters), step?.instruction ?: "Siga a rota",
                step?.streetName, step?.kind)
        } else guidance.update(null, when (state.phase) {
            NavigationPhase.RECALCULATING -> "Recalculando rota…"
            NavigationPhase.REROUTE_FAILED -> "Rota indisponível · tente novamente"
            else -> "Aguardando GPS preciso para orientar"
        }, null, null)
        primary.updateText(when {
            preparing != null -> preparing.action.orEmpty()
            state.phase == NavigationPhase.PREVIEW -> "Iniciar navegação"
            state.phase == NavigationPhase.RESUME_AVAILABLE -> "Retomar e recalcular"
            state.phase == NavigationPhase.ERROR || state.phase == NavigationPhase.REROUTE_FAILED -> "Tentar novamente"
            state.phase == NavigationPhase.ARRIVED -> "Concluir"
            state.navigationActive -> "Opções da viagem"
            state.destination != null -> "Ver destino"
            else -> "Seus lugares e opções"
        })
        primary.visibility = if (state.navigationActive || preparing != null && preparing.action == null) View.GONE else View.VISIBLE
        summary.isClickable = preparing?.busy != true
        summary.isFocusable = preparing?.busy != true
        if (state.phase == NavigationPhase.PREVIEW && state.route?.id != lastPreviewId) {
            lastPreviewId = state.route?.id
            renderer.showRouteOverview()
        }
        renderSearch()
        renderStops()
        renderRouteOptions()
        val currentSearch = state.searchResults to state.searchError
        searchStatus?.text = when { state.searching -> "Buscando endereços e estabelecimentos…"
            state.searchError != null -> state.searchError
            state.searchResults.isEmpty() -> "Busque por nome ou endereço. Pressione o mapa para escolher um ponto."
            else -> "${state.searchResults.size} resultados · Geoapify / OpenStreetMap" }
        if (state.searching) {
            searchResults?.removeAllViews()
            lastSearch = null
        } else if (lastSearch != currentSearch) {
            lastSearch = currentSearch
            searchResults?.let { list -> list.removeAllViews(); state.searchResults.forEach { destination ->
                action(list, destination.name + destination.address.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()) { choose(destination) }
            } }
        }
        if (vibing) listOf(search, summary, stops, guidance, guidance.menu, simulationNotice,
            speedBadge, recenter, trafficBadge).forEach { it.visibility = View.GONE }
        position()
    }

    private fun enterVibing() {
        if (state.simulation) { toast("Encerre a simulação para usar o Vibing com GPS real."); return }
        if (state.destination != null && !state.navigationActive &&
            state.phase != NavigationPhase.PREVIEW && state.phase != NavigationPhase.ARRIVED) {
            toast("Conclua o cálculo da rota ou limpe o destino para sair dirigindo sem rota.")
            return
        }
        withGps {
            val current = runtime.snapshot()
            if (current.locationAccess in setOf(LocationAccess.DENIED, LocationAccess.DISABLED, LocationAccess.NEEDS_PHONE_START)) {
                toast(current.message ?: "Ative a localização precisa do aparelho para usar o Vibing.")
                return@withGps
            }
            if (current.phase == NavigationPhase.PREVIEW) {
                runtime.startNavigation()
                if (!runtime.snapshot().navigationActive) {
                    toast(runtime.snapshot().message ?: "Aguarde uma posição GPS atual para iniciar a rota.")
                    return@withGps
                }
            }
            setVibing(true)
            toast("Vibing · toque no mapa para sair ou recentralizar.")
        }
    }

    private fun setVibing(enabled: Boolean) {
        vibing = enabled
        vibingBack.isEnabled = enabled
        menuPopup?.dismiss()
        dialog?.dismiss()
        closeAppearanceSheet()
        root.removeCallbacks(hideVibingControls)
        hideVibingControls.run()
        renderer.setVibing(enabled)
        onVibingChanged(enabled)
        lastLayoutKey = null
        lastStopsKey = null
        render()
    }

    private fun revealVibingControls() {
        if (!vibing) return
        vibingControls.visibility = View.VISIBLE
        vibingControls.bringToFront()
        root.removeCallbacks(hideVibingControls)
        val accessibility = activity.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        if (!accessibility.isTouchExplorationEnabled) root.postDelayed(hideVibingControls, 5_000L)
    }

    private fun updateRecenterAppearance() {
        val tracking = renderer.isFollowing && state.location.quality == LocationQuality.GOOD
        if (lastTracking == tracking) return
        lastTracking = tracking
        recenter.bind(colors)
        recenter.surface(colors, 100)
        // Follow state stays subtle; the control retains the same glass circle as speed.
        recenter.isSelected = tracking
        recenter.contentDescription = if (tracking) "Recentralizar; GPS acompanhando" else "Recentralizar"
    }

    private fun renderSearch() {
        val destination = state.destination ?: state.resumeDestination
        val title = destination?.name ?: searchQuery.takeIf { it.isNotBlank() } ?: "Para onde?"
        searchLabel.updateText(title)
        val separator = if (title.lastOrNull() in setOf('?', '!', '.')) " " else ". "
        search.contentDescription = title + separator + "Buscar destinos"
        clearSearch.visibility = if (!state.navigationActive && !state.simulation &&
            (destination != null || searchQuery.isNotEmpty() || state.searching ||
                state.searchResults.isNotEmpty() || state.searchError != null)) View.VISIBLE else View.GONE
    }

    private fun clearDestination() {
        if (state.navigationActive || state.simulation) return
        searchQuery = ""
        selectingStop = false
        restoreStopSearch = false
        afterPermission = null
        lastPreviewId = null
        dialog?.dismiss()
        runtime.clearDestination()
    }

    private fun primaryAction() = when (state.phase) {
        NavigationPhase.WAITING_FOR_LOCATION -> {
            if (state.locationAccess == LocationAccess.DISABLED)
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else withGps { runtime.retryRoute() }
        }
        NavigationPhase.CALCULATING -> Unit
        NavigationPhase.PREVIEW -> withGps { runtime.startNavigation(); renderer.recenter(); dialog?.dismiss() }
        NavigationPhase.RESUME_AVAILABLE -> withGps { runtime.resumeDestination() }
        NavigationPhase.ERROR, NavigationPhase.REROUTE_FAILED -> {
            if (!state.providerConfigured) showProvider() else withGps { runtime.retryRoute() }
        }
        NavigationPhase.ARRIVED -> runtime.stopNavigation()
        else -> showRoute()
    }

    private fun renderStops() {
        val committed = if (state.phase == NavigationPhase.RESUME_AVAILABLE) state.resumeIntermediateStops else state.intermediateStops
        val pending = state.pendingIntermediateStops
        val key = committed.joinToString { "${it.id}:${it.name}" } + "|" + pending?.joinToString { it.id } + "|" + state.phase + "|" + colors.accent
        if (key == lastStopsKey) return
        lastStopsKey = key
        stopList.removeAllViews()
        committed.forEachIndexed { index, stop ->
            val chip = LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(2), 0)
                surface(colors, 24)
            }
            chip.addView(label("${index + 1}. ${stop.name}", 12f, true).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                maxWidth = dp(190)
                setOnClickListener { showRoute() }
            })
            val resumable = state.phase == NavigationPhase.RESUME_AVAILABLE
            val remove = PhoneAction(activity, if (resumable) R.drawable.phone_arrow else R.drawable.phone_close,
                if (resumable) "Retomar viagem para editar ${stop.name}" else "Remover parada ${stop.name}").apply {
                bind(colors)
                setOnClickListener { if (resumable) showRoute() else runtime.removeStop(stop.id) }
            }
            chip.addView(remove, LinearLayout.LayoutParams(dp(48), dp(48)))
            stopList.addView(chip, LinearLayout.LayoutParams(WRAP, dp(48)).apply { marginEnd = dp(8) })
        }
        if (pending != null) {
            val status = when (state.phase) {
                NavigationPhase.CALCULATING, NavigationPhase.RECALCULATING -> "Atualizando paradas…"
                NavigationPhase.WAITING_FOR_LOCATION -> "Paradas aguardando GPS"
                else -> "Paradas não atualizadas · tentar novamente"
            }
            stopList.addView(label(status, 12f, true).apply {
                gravity = Gravity.CENTER
                minimumHeight = dp(48)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                surface(colors, 24)
                setOnClickListener { showRoute() }
            })
        }
        stops.visibility = if (committed.isNotEmpty() || pending != null) View.VISIBLE else View.GONE
    }
    private fun choose(destination: Destination, asStop: Boolean = selectingStop) {
        dialog?.dismiss()
        searchQuery = ""
        selectingStop = false
        if (asStop) {
            if (state.destination == null) { toast("A viagem terminou. Escolha um destino para começar outra rota."); return }
            runtime.addStop(destination)
            val updated = runtime.snapshot()
            // Validation failures and replay restrictions must be visible immediately.
            if (updated.pendingIntermediateStops == null && updated.message != null) {
                toast(updated.message)
                return
            }
        } else runtime.selectDestination(destination)
        if (!state.providerConfigured) { showProvider(); return }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) withGps { runtime.retryRoute() }
        else runtime.activateLocation()
    }
    private fun withGps(after: () -> Unit) {
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            runtime.activateLocation(); after()
        } else {
            afterPermission = after
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun showDestinations(initialQuery: String? = null, addStop: Boolean = false) {
        selectingStop = addStop
        searchQuery = initialQuery ?: if (addStop) "" else searchQuery
        val content = sheet(if (addStop) "Adicionar parada" else "Para onde?",
            if (addStop) "A parada será visitada antes de ${state.destination?.name ?: "seu destino"}." else "Endereços, estabelecimentos e seus lugares")
        val entry = input("Buscar endereço ou lugar", searchQuery)
        entry.imeOptions = EditorInfo.IME_ACTION_SEARCH
        val queryRow = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val clearQuery = PhoneAction(activity, R.drawable.phone_close,
            if (addStop || state.navigationActive || state.simulation) "Limpar busca" else "Limpar busca e destino").apply {
            bind(colors)
            visibility = if (searchQuery.isNotEmpty() || !addStop &&
                (state.destination != null || state.resumeDestination != null)) View.VISIBLE else View.GONE
            setOnClickListener {
                entry.text.clear()
                if (addStop || state.navigationActive || state.simulation) runtime.search("") else clearDestination()
            }
        }
        queryRow.addView(entry, LinearLayout.LayoutParams(0, WRAP, 1f))
        queryRow.addView(clearQuery, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(queryRow)
        entry.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty()
            clearQuery.visibility = if (searchQuery.isNotEmpty() || !addStop &&
                (state.destination != null || state.resumeDestination != null)) View.VISIBLE else View.GONE
            if (searchQuery.isEmpty()) runtime.search("")
            renderSearch()
        }
        entry.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runtime.search(entry.text.toString()); true } else false
        }
        action(content, "Buscar") { runtime.search(entry.text.toString()) }
        searchStatus = label("", 13f)
        content.addView(searchStatus)
        searchResults = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(searchResults)
        lastSearch = null
        render()
        action(content, "Seus lugares · salvos e recentes") { showPlaces() }
        if (addStop) action(content, "Escolher ponto no mapa") { dialog?.dismiss(); toast("Mantenha pressionado o ponto da parada no mapa.") }
    }

    fun showOptions() {
        selectingStop = false
        closeAppearanceSheet()
        menuPopup?.dismiss()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            contentDescription = "Menu do mapa"
            surface(colors, 22)
        }
        val menuScroll = NestedScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }
        val popup = PopupWindow(menuScroll, minOf(dp(244), root.width - leftInset - rightInset - dp(28)), WRAP, true)
        fun entry(icon: Int, text: String, click: () -> Unit) {
            val row = PhoneAction(activity, icon, text, showLabel = true).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                bind(colors)
                setOnClickListener { popup.dismiss(); click() }
            }
            content.addView(row, LinearLayout.LayoutParams(MATCH, dp(54)))
        }
        entry(R.drawable.phone_layers, "Temas") { openMapSettings() }
        entry(R.drawable.phone_filters, "Filtros") { openMapFilters() }
        entry(R.drawable.phone_arrow, "Vibing") { enterVibing() }
        val map3D = app.preferences.snapshot().is3D(state.navigationActive)
        entry(R.drawable.phone_map, "Mapa 3D · ${if (map3D) "ativado" else "desativado"}") {
            val navigationActive = state.navigationActive
            app.preferences.setMap3D(navigationActive, !app.preferences.snapshot().is3D(navigationActive))
            if (state.phase == NavigationPhase.PREVIEW) toast("Preferência salva. A prévia da rota permanece em 2D.")
        }
        entry(R.drawable.phone_map, "Seus lugares") { showPlaces() }
        entry(R.drawable.phone_settings, "Configurações") { showSettings() }
        popup.height = minOf(dp(content.childCount * 54 + 12),
            (root.height - topInset - bottomInset - dp(88)).coerceAtLeast(dp(108)))
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        popup.elevation = dp(12).toFloat()
        popup.isOutsideTouchable = true
        menuPopup = popup
        popup.showAsDropDown(guidance.menu, guidance.menu.width - popup.width, dp(8))
    }

    private fun showSettings() {
        val content = sheet("Seus lugares e opções", "GPS, aparência e preferências deste aparelho")
        note(content, speedBadge.contentDescription.toString() + state.message?.let { "\n$it" }.orEmpty())
        currentMapNotice?.let { note(content, it) }
        action(content, "Seus lugares · salvos e recentes") { showPlaces() }
        if (state.destination != null || state.resumeDestination != null)
            action(content, "Sua viagem · destino e opções") { showRoute() }
        action(content, "Minha posição · ativar GPS") { dialog?.dismiss(); withGps { renderer.recenter() } }
        action(content, "Salvar minha posição") { withGps {
            runtime.snapshot().location.takeIf { it.quality == LocationQuality.GOOD }?.fix?.point?.let { editPlace(Destination(UUID.randomUUID().toString(), "Minha posição", it)) }
                ?: toast("Aguarde uma posição atual e precisa e toque novamente.")
        } }
        action(content, "Marcador do veículo e orientação") { showVehicle() }
        action(content, "Aumentar zoom") { renderer.zoomBy(1.0); dialog?.dismiss() }
        action(content, "Diminuir zoom") { renderer.zoomBy(-1.0); dialog?.dismiss() }
        action(content, "Voltar ao enquadramento inicial") { renderer.reset(); dialog?.dismiss() }
        action(content, "Cobertura do mapa · ${app.preferences.snapshot().baseMapMode.label}") {
            dialog?.dismiss(); openMapSettings()
        }
        action(content, if (state.providerConfigured) "Geoapify · chave configurada" else "Configurar chave Geoapify") { showProvider() }
        action(content, if (app.navigationSettings.trafficEnabled()) "Trânsito TomTom · ativado" else "Trânsito ao vivo · TomTom") { showTraffic() }
        action(content, "Configurações de localização do aparelho") { activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
            action(content, "Permitir notificação da navegação") { permission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        if (BuildConfig.DEBUG) action(content, "Desenvolvimento · simulação GPS") { showReplay() }
        note(content, if (app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD)
            "O mapa mundial usa internet e dados OpenFreeMap / OpenStreetMap. Sem conexão, escolha Goiânia offline em Cobertura do mapa. Busca e rotas usam Geoapify. ETA sem trânsito em tempo real."
            else "Goiânia offline cobre somente o recorte incluído; fora dele, o mapa base fica sem detalhes. Busca e rotas usam internet. ETA sem trânsito em tempo real.")
        note(content, "Dados do mapa: OpenFreeMap · OpenMapTiles · © OpenStreetMap contributors · Overture Maps Foundation · Google · Microsoft. Busca e rotas: Geoapify. Fontes e sprites dos jogos pertencem aos respectivos autores.")
        action(content, "Sobre o NavSkin · fontes e licenças") {
            activity.startActivity(Intent(activity, ConnectionInstructionsActivity::class.java))
        }
    }

    private fun showRoute() {
        val destination = state.destination ?: state.resumeDestination
        if (destination == null) { showDestinations(); return }
        val content = sheet(destination.name, destination.address.ifBlank { "Destino escolhido no mapa" })
        state.route?.let { route ->
            val progress = state.progress.takeIf { state.guidanceAvailable }
            when {
                progress != null -> note(content, "${distance(progress.remainingDistanceMeters)} · ${duration(progress.remainingSeconds)} · chegada ${eta(progress.arrivalEpochMs)}")
                state.phase == NavigationPhase.PREVIEW -> note(content, "${distance(route.distanceMeters)} · ${duration(route.durationSeconds)} · chegada estimada ${eta(System.currentTimeMillis() + (route.durationSeconds * 1000).toLong())}")
                state.phase == NavigationPhase.ARRIVED -> note(content, "Você chegou ao destino.")
                else -> note(content, "Estimativa indisponível até recuperar GPS preciso e uma rota válida.")
            }
            if (app.preferences.snapshot().baseMapMode == MapBaseMode.GOIANIA_OFFLINE &&
                route.geometry.any { !OfflineMapCoverage.contains(it.latitude, it.longitude) })
                note(content, "Parte da rota está fora do mapa de Goiânia incluído. Nesses trechos o fundo não terá vias ou edifícios.")
        }
        routeStatus = label("", 13f).apply {
            setTextColor(colors.muted)
            setPadding(0, dp(10), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }.also { content.addView(it) }
        routeStops = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }.also { content.addView(it) }
        routeRetry = action(content, "Tentar atualizar paradas novamente") {
            if (!state.providerConfigured) showProvider()
            else if (state.locationAccess == LocationAccess.DISABLED)
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else withGps { runtime.retryRoute() }
        }
        lastRouteStopsKey = null
        renderRouteOptions()
        val preparing = RoutePreparationUi.forState(state, granted(Manifest.permission.ACCESS_FINE_LOCATION))
        if (state.navigationActive) action(content, "Continuar viagem") { dialog?.dismiss() }
        else if (preparing == null || preparing.action != null)
            action(content, preparing?.action ?: primary.text.toString()) { primaryAction() }
        if (state.route != null) action(content, "Visão geral da rota") { renderer.showRouteOverview(); dialog?.dismiss() }
        action(content, "Recentralizar e acompanhar") { withGps { renderer.recenter(); dialog?.dismiss() } }
        if (state.phase !in setOf(NavigationPhase.WAITING_FOR_LOCATION, NavigationPhase.CALCULATING, NavigationPhase.RECALCULATING))
            action(content, "Recalcular rota") { withGps { runtime.retryRoute(); dialog?.dismiss() } }
        action(content, "Salvar este destino") { editPlace(destination) }
        if (state.destination != null) action(content, "Adicionar parada") { showDestinations(addStop = true) }
        action(content, "Encerrar / cancelar rota") { runtime.stopNavigation(); dialog?.dismiss() }
        note(content, "Estimativas do provedor, sem trânsito ao vivo. Orientação visual, sem voz. Geoapify · © OpenStreetMap contributors.")
    }

    /** Route edits can complete while this sheet is open; update only its changing rows. */
    private fun renderRouteOptions() {
        val list = routeStops ?: return
        val pending = state.pendingIntermediateStops
        val status = listOfNotNull(state.message, pending?.let {
            "Alteração ainda não aplicada à rota. " + if (it.isEmpty()) "Remover todas as paradas."
            else "Paradas solicitadas: ${it.joinToString { stop -> stop.name }}."
        }).joinToString("\n\n")
        routeStatus?.apply { updateText(status); visibility = if (status.isBlank()) View.GONE else View.VISIBLE }
        routeRetry?.visibility = if (pending != null && state.phase !in setOf(NavigationPhase.CALCULATING, NavigationPhase.RECALCULATING)) View.VISIBLE else View.GONE
        val committed = if (state.phase == NavigationPhase.RESUME_AVAILABLE) state.resumeIntermediateStops else state.intermediateStops
        val key = committed.joinToString { "${it.id}:${it.name}" } + "|" + (state.phase == NavigationPhase.RESUME_AVAILABLE)
        if (key == lastRouteStopsKey) return
        lastRouteStopsKey = key
        list.removeAllViews()
        committed.forEachIndexed { index, stop ->
            if (state.phase == NavigationPhase.RESUME_AVAILABLE) note(list, "${index + 1}. ${stop.name}")
            else action(list, "${index + 1}. ${stop.name} · remover parada") { runtime.removeStop(stop.id) }
        }
    }

    private fun showPoint(point: GeoPoint) {
        val destination = Destination(UUID.randomUUID().toString(), "Ponto no mapa", point,
            String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude))
        val content = sheet("Ponto no mapa", destination.address)
        if (state.destination != null) action(content, "Adicionar como parada") { choose(destination, asStop = true) }
        if (!selectingStop) action(content, "Traçar rota até aqui") { choose(destination, asStop = false) }
        action(content, "Salvar este local") { editPlace(destination) }
    }

    private fun showPlaces() {
        val places = app.places.snapshot()
        val content = sheet("Seus lugares", "Salvos neste aparelho")
        places.loadError?.let { note(content, it) }
        val visible = app.navigationSettings.snapshot().savedPlacesVisible
        action(content, if (visible) "Ocultar locais salvos no mapa" else "Mostrar locais salvos no mapa") {
            safely { app.navigationSettings.setSavedPlacesVisible(!visible); showPlaces() }
        }
        places.homeId?.let { id -> places.savedPlaces.firstOrNull { it.id == id } }?.let { home ->
            action(content, "Casa · ${home.name}") { choose(home.asDestination()) }
        }
        places.workId?.let { id -> places.savedPlaces.firstOrNull { it.id == id } }?.let { work ->
            action(content, "Trabalho · ${work.name}") { choose(work.asDestination()) }
        }
        note(content, "Salvos · ${places.savedPlaces.size}")
        if (places.savedPlaces.isEmpty()) note(content, "Mantenha pressionado um ponto no mapa ou salve um resultado da busca.")
        places.savedPlaces.forEach { saved ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(preview(app.icons.savedIcon(saved.iconId, app.preferences.snapshot().theme).preview, saved.name), LinearLayout.LayoutParams(dp(48), dp(48)))
            action(row, saved.name) {
                val details = sheet(saved.name, saved.address)
                action(details, if (selectingStop) "Adicionar como parada" else "Ir para este local") { choose(saved.asDestination()) }
                action(details, "Editar nome, categoria ou ícone") { editPlace(saved.asDestination(), saved) }
                action(details, "Definir como Casa") { safely { app.places.setHome(saved.id); showPlaces() } }
                action(details, "Definir como Trabalho") { safely { app.places.setWork(saved.id); showPlaces() } }
                if (places.homeId == saved.id) action(details, "Remover atalho Casa") { safely { app.places.setHome(null); showPlaces() } }
                if (places.workId == saved.id) action(details, "Remover atalho Trabalho") { safely { app.places.setWork(null); showPlaces() } }
                action(details, "Excluir local") {
                    val confirm = sheet("Excluir ${saved.name}?", "O local será removido dos salvos deste aparelho.")
                    action(confirm, "Excluir") { safely { app.places.deletePlace(saved.id); showPlaces() } }
                }
            }
            content.addView(row)
        }
        note(content, "Recentes")
        places.recents.forEach { recent -> action(content, recent.destination.name) { choose(recent.destination) } }
    }

    private fun editPlace(destination: Destination, saved: SavedPlace? = null) {
        val content = sheet(if (saved == null) "Salvar local" else "Editar local", "Escolha um ícone do tema atual")
        val name = input("Nome", saved?.name ?: destination.name)
        val address = input("Endereço / referência", saved?.address ?: destination.address)
        val category = input("Categoria (opcional)", saved?.categoryId.orEmpty())
        val latitude = input("Latitude", destination.point.latitude.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        val longitude = input("Longitude", destination.point.longitude.toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        content.addView(name); content.addView(address); content.addView(category)
        content.addView(latitude); content.addView(longitude)
        var selected = saved?.iconId ?: "special"
        iconGrid(content, app.icons.savedIcons(), false, selected) { selected = it }
        action(content, "Salvar local") {
            safely {
                val lat = latitude.text.toString().replace(',', '.').toDoubleOrNull() ?: error("Informe uma latitude válida.")
                val lon = longitude.text.toString().replace(',', '.').toDoubleOrNull() ?: error("Informe uma longitude válida.")
                app.places.savePlace(name.text.toString(), GeoPoint(lat, lon), address.text.toString(),
                    category.text.toString().ifBlank { null }, selected, saved?.id)
                showPlaces()
            }
        }
    }

    private fun showVehicle() {
        val preferences = app.navigationSettings.snapshot()
        val content = sheet("Seu marcador", "A aparência muda com o tema; o perfil de rota continua sendo automóvel.")
        iconGrid(content, app.icons.vehicleIcons(), true, preferences.vehicleIdFor(app.preferences.snapshot().theme)) { id ->
            safely { app.navigationSettings.setVehicleId(id) }
        }
        action(content, if (preferences.orientation == MapOrientation.NORTH_UP) "Orientação: norte para cima · mudar" else "Orientação: direção do movimento · mudar") {
            safely { app.navigationSettings.setOrientation(if (preferences.orientation == MapOrientation.NORTH_UP) MapOrientation.MOVEMENT_UP else MapOrientation.NORTH_UP); showVehicle() }
        }
    }

    private fun showProvider() {
        val content = sheet("Busca e rotas", "Geoapify · chave da sua conta")
        note(content, "Configure a chave da sua conta Geoapify. Ela é criptografada no aparelho e não entra no código do app. Consultas enviam ao provedor o texto pesquisado e as coordenadas necessárias à rota.")
        val key = input(if (state.providerConfigured) "Nova chave (a atual está salva)" else "Sua chave Geoapify").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
        }
        content.addView(key)
        action(content, "Salvar chave") {
            if (key.text.isNullOrBlank()) { toast("Informe sua chave."); return@action }
            safely {
                app.navigationSettings.setApiKey(key.text.toString())
                key.text.clear()
                dialog?.dismiss()
                toast("Chave salva. Busque um destino para validar a conexão.")
                if (state.destination != null) withGps { runtime.retryRoute() }
            }
        }
        if (state.providerConfigured) action(content, "Remover chave deste aparelho") { safely { app.navigationSettings.setApiKey(""); showProvider() } }
        note(content, "Plano gratuito consultado: 3.000 créditos por dia, com limites compartilhados entre os serviços. Confira o plano e os termos na sua conta. O app não contrata um plano pago. © Geoapify · © OpenStreetMap contributors.")
        action(content, "Abrir Geoapify") { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.geoapify.com/pricing/"))) }
    }

    private fun showTraffic() {
        val settings = app.navigationSettings
        val configured = settings.trafficApiKey().isNotBlank()
        val content = sheet("Trânsito ao vivo", "TomTom · serviço opcional")
        trafficDetails = label(trafficMessage, 14f).also { content.addView(it) }
        note(content, "A camada mostra o fluxo das vias e incidentes informados pela TomTom na região visível. Ao ativar, a área do mapa é enviada ao serviço. A chave fica criptografada neste aparelho.")
        val toggle = Switch(activity).apply {
            text = "Mostrar trânsito e incidentes"
            isChecked = settings.trafficEnabled()
            isEnabled = configured
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, checked -> safely { settings.setTrafficEnabled(checked) } }
        }
        content.addView(toggle, LinearLayout.LayoutParams(MATCH, WRAP))
        val key = input(if (configured) "Nova chave TomTom (a atual está salva)" else "Sua chave TomTom").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
        }
        content.addView(key)
        action(content, "Salvar chave e ativar trânsito") {
            if (key.text.isNullOrBlank()) { toast("Informe sua chave TomTom."); return@action }
            safely {
                settings.setTrafficApiKey(key.text.toString())
                key.text.clear()
                settings.setTrafficEnabled(true)
                showTraffic()
            }
        }
        if (configured) action(content, "Remover chave TomTom") {
            safely { settings.setTrafficApiKey(""); showTraffic() }
        }
        note(content, "Fluxo: verde indica circulação mais livre; amarelo, laranja e vermelho indicam lentidão crescente. Os incidentes podem estar incompletos ou atrasados. A ausência de marcadores não confirma uma via livre.")
        note(content, "O ETA e o recálculo continuam usando Geoapify, sem trânsito ao vivo. Esta camada não fornece radares nem o estado vermelho/verde dos semáforos.")
        note(content, "© TomTom. Disponibilidade e limites dependem da sua conta. O NavSkin não contrata plano pago. Desative a opção para interromper as consultas desta camada.")
        action(content, "Criar ou consultar chave TomTom") {
            activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://developer.tomtom.com/")))
        }
    }

    private fun showReplay() {
        val content = sheet("SIMULAÇÃO GPS", "Somente desenvolvimento · não é navegação real")
        note(content, "Reproduz um trajeto local fixo para inspecionar orientação, progresso e chegada. Não consulta rotas pela internet. Para validar navegação real, configure a chave e use GPS real.")
        action(content, "Iniciar SIMULAÇÃO") { runtime.startReplay(PhoneNavigationReplay.route, PhoneNavigationReplay.fixes()); dialog?.dismiss() }
        if (state.simulation) action(content, "Encerrar SIMULAÇÃO") { runtime.stopReplay(); dialog?.dismiss() }
    }

    private fun iconGrid(content: LinearLayout, icons: List<CatalogIcon>, vehicle: Boolean, initial: String, select: (String) -> Unit) {
        val cells = mutableMapOf<String, LinearLayout>()
        fun bind(id: String) { cells.forEach { (key, cell) ->
            cell.background = activity.rounded(if (key == id) colors.soft else colors.surface, 16,
                if (key == id) colors.accent else colors.border, if (key == id) 2 else 1)
            cell.isSelected = key == id
            cell.contentDescription = "${icons.first { it.id == key }.label}, ${if (key == id) "selecionado" else "disponível"}"
        } }
        icons.chunked(3).forEach { group ->
            val row = LinearLayout(activity)
            group.forEach { icon ->
                val path = if (vehicle) app.icons.vehicleIcon(icon.id, app.preferences.snapshot().theme).preview
                    else app.icons.savedIcon(icon.id, app.preferences.snapshot().theme).preview
                val cell = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(6), dp(10), dp(6), dp(10))
                    addView(preview(path, null), LinearLayout.LayoutParams(dp(48), dp(48)))
                    addView(label(icon.label, 11f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(MATCH, dp(32)))
                    isClickable = true; isFocusable = true
                    setOnClickListener { select(icon.id); bind(icon.id) }
                }
                cells[icon.id] = cell
                row.addView(cell, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
            }
            content.addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        bind(initial)
    }
    private fun sheet(title: String, subtitle: String): LinearLayout {
        closeAppearanceSheet()
        menuPopup?.dismiss()
        dialog?.dismiss()
        searchResults = null; searchStatus = null; trafficDetails = null
        routeStatus = null; routeStops = null; routeRetry = null
        val newDialog = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(24) + bottomInset)
            surface(colors, 28)
        }
        content.addView(View(activity).apply { background = activity.rounded(0x6692a095, 2) },
            LinearLayout.LayoutParams(dp(35), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(12) })
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(label(title, 26f, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        heading.addView(PhoneAction(activity, R.drawable.phone_close, "Fechar").apply {
            bind(colors)
            setOnClickListener { newDialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(heading, LinearLayout.LayoutParams(MATCH, WRAP))
        note(content, subtitle)
        val scroll = NestedScrollView(activity).apply {
            addView(content); isFillViewport = false
            contentDescription = "Painel $title"
        }
        newDialog.setContentView(scroll)
        newDialog.setOnDismissListener { if (dialog === newDialog) {
            dialog = null; searchStatus = null; searchResults = null; trafficDetails = null
            routeStatus = null; routeStops = null; routeRetry = null
        } }
        newDialog.setOnShowListener {
            newDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { frame ->
                frame.setBackgroundColor(Color.TRANSPARENT)
                frame.layoutParams.height = (activity.resources.displayMetrics.heightPixels * .82).toInt()
                frame.layoutParams = frame.layoutParams
                BottomSheetBehavior.from(frame).apply { state = BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true }
            }
            newDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog = newDialog
        newDialog.show()
        return content
    }
    private fun input(hintText: String, value: String = "") = EditText(activity).apply {
        hint = hintText; setText(value); textSize = 16f; setTextColor(colors.text); setHintTextColor(colors.muted)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        setSingleLine(true); setPadding(dp(12), dp(12), dp(12), dp(12)); minimumHeight = dp(52)
        background = activity.rounded(0xbbffffff.toInt(), 14, colors.border)
    }
    private fun preview(path: String, description: String?) = ImageView(activity).apply {
        contentDescription = description
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setImageBitmap(activity.assets.open(path).use { BitmapFactory.decodeStream(it) })
    }
    private fun action(parent: LinearLayout, text: String, click: () -> Unit): TextView = label(text, 14f, true).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(48)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true; isFocusable = true
        contentDescription = text
        touchSurface(0x66ffffff, 14)
        setOnClickListener { click() }
        parent.addView(this, if (parent.orientation == LinearLayout.HORIZONTAL)
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(3) }
        else LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(8) })
    }
    private fun label(value: String, size: Float, bold: Boolean = false) = activity.phoneText(value, size, bold).apply { setTextColor(colors.text) }
    private fun note(parent: LinearLayout, value: String) = parent.addView(label(value, 13f).apply {
        setTextColor(colors.muted); setLineSpacing(dp(3).toFloat(), 1f)
    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10); bottomMargin = dp(8) })
    private fun add(view: View, gravity: Int, width: Int, height: Int) = root.addView(view, FrameLayout.LayoutParams(width, height, gravity))
    private fun place(view: View, top: Int = 0, bottom: Int = 0) {
        view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
            if (gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.CENTER_HORIZONTAL) {
                // FrameLayout adds the margin difference to a centered view. Half the
                // asymmetric system inset keeps the center inside the actual map area.
                marginStart = (leftInset - rightInset) / 2
                marginEnd = 0
            } else {
                marginStart = leftInset + dp(14); marginEnd = rightInset + dp(14)
            }
            topMargin = top; bottomMargin = bottom
        }
    }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    private fun safely(block: () -> Unit) { runCatching(block).onFailure { toast(it.message ?: "Não foi possível salvar. Tente novamente.") } }
    private fun toast(text: String) { Toast.makeText(activity, text, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = activity.dip(value)
    private fun duration(seconds: Double): String = (seconds / 60.0).roundToInt().coerceAtLeast(1).let { if (it < 60) "$it min" else "${it / 60} h ${it % 60} min" }
    private fun distance(meters: Double): String = if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale("pt", "BR"), "%.1f km", meters / 1000)
    private fun eta(epoch: Long) = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(epoch))
    companion object {
        private const val PREVIEW_STATE = "gamemap.phone.previewRouteId"
        private const val ADD_STOP_STATE = "gamemap.phone.addStop"
        private const val SEARCH_QUERY_STATE = "gamemap.phone.searchQuery"
        private const val VIBING_STATE = "gamemap.phone.vibing"
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
