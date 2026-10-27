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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import br.com.gamemap.BuildConfig
import br.com.gamemap.data.OfflineMapCoverage
import br.com.gamemap.GameMapApplication
import br.com.gamemap.car.CarDestinationIntent
import br.com.gamemap.car.CarNavigationIntents
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import br.com.gamemap.navigation.storage.CatalogIcon
import br.com.gamemap.navigation.storage.SavedPlace
import br.com.gamemap.render.NativeMapRenderer
import br.com.gamemap.preferences.MapBaseMode
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
    private val routeLayout: (Boolean) -> Unit,
) {
    private val app get() = activity.application as GameMapApplication
    private val runtime get() = app.navigation
    private var colors = PhonePalette.forTheme(app.preferences.snapshot().theme)
    private var state = runtime.snapshot()
    private val removers = mutableListOf<() -> Unit>()
    private var dialog: BottomSheetDialog? = null
    private var searchResults: LinearLayout? = null
    private var searchStatus: TextView? = null
    private var lastSearch: Pair<List<Destination>, String?>? = null
    private var afterPermission: (() -> Unit)? = null
    private var topInset = 0
    private var bottomInset = 0
    private var leftInset = 0
    private var rightInset = 0
    private var lastPreviewId: String? = restoredState?.getString(PREVIEW_STATE)
    private val search = label("Para onde?", 16f, true)
    private val guidance = label("", 17f, true)
    private val summary = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val metrics = label("", 18f, true)
    private val status = label("", 12f)
    private val primary = label("", 14f, true)
    private val controls = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
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
        search.setPadding(dp(18), dp(14), dp(18), dp(14))
        search.contentDescription = "Para onde? Buscar destinos e abrir seus lugares"
        search.setOnClickListener { showDestinations() }
        add(search, Gravity.TOP, MATCH, WRAP)
        guidance.setPadding(dp(16), dp(14), dp(16), dp(14))
        guidance.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        guidance.visibility = View.GONE
        add(guidance, Gravity.TOP, MATCH, WRAP)
        summary.setPadding(dp(16), dp(12), dp(16), dp(10))
        summary.addView(metrics)
        summary.addView(status, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        summary.addView(primary, LinearLayout.LayoutParams(MATCH, dp(48)).apply { topMargin = dp(6) })
        primary.gravity = Gravity.CENTER
        primary.setOnClickListener { primaryAction() }
        summary.setOnClickListener { showRoute() }
        status.setOnClickListener {
            if (app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD && !app.mapConnectivity.isOnline) {
                dialog?.dismiss(); openMapSettings()
            } else showRoute()
        }
        summary.visibility = View.GONE
        add(summary, Gravity.BOTTOM, MATCH, WRAP)
        action(controls, "GPS") { withGps { renderer.recenter() } }
        action(controls, "Rota") { renderer.showRouteOverview() }
        action(controls, "N ↑") {
            safely { app.navigationSettings.setOrientation(if (app.navigationSettings.snapshot().orientation == MapOrientation.NORTH_UP)
                MapOrientation.MOVEMENT_UP else MapOrientation.NORTH_UP) }
        }
        add(controls, Gravity.BOTTOM or Gravity.START, WRAP, WRAP)
        renderer.setOnMapDestinationListener { point -> showPoint(point) }
        renderer.setOnCameraModeChangedListener { following ->
            (controls.getChildAt(0) as TextView).apply {
                text = if (following) "Seguindo" else "Recentralizar"
                isSelected = following
                contentDescription = if (following) "GPS acompanhando; recentralizar" else "Recentralizar e acompanhar GPS"
            }
        }
        themeChanged()
    }

    fun start() {
        removers += runtime.attachClient("phone")
        removers += runtime.addListener { state = it; render() }
        removers += app.navigationSettings.addListener { render() }
        removers += app.places.addListener { render() }
        runtime.setLocationPermission(granted(Manifest.permission.ACCESS_FINE_LOCATION), granted(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun stop() { removers.toList().forEach { it() }; removers.clear() }
    fun close() { stop(); dialog?.dismiss(); dialog = null }
    fun saveState(out: android.os.Bundle) { out.putString(PREVIEW_STATE, lastPreviewId) }
    fun handleIntent(intent: Intent) {
        when (val request = CarNavigationIntents.parse(intent)) {
            is CarDestinationIntent.Place -> { closeAppearanceSheet(); choose(request.destination) }
            is CarDestinationIntent.Search -> { showDestinations(request.query); runtime.search(request.query) }
            null -> Unit
        }
    }
    fun themeChanged() {
        colors = PhonePalette.forTheme(app.preferences.snapshot().theme)
        search.surface(colors, 22)
        guidance.surface(colors, 20)
        summary.surface(colors, 22)
        controls.surface(colors, 20)
        listOf(search, guidance, metrics).forEach { it.setTextColor(colors.text) }
        status.setTextColor(colors.muted)
        primary.setTextColor(Color.WHITE)
        primary.touchSurface(colors.accent, 14)
        for (index in 0 until controls.childCount) (controls.getChildAt(index) as? TextView)?.setTextColor(colors.text)
        render()
    }
    fun mapAvailabilityChanged() { render() }
    fun insets(left: Int, top: Int, right: Int, bottom: Int) {
        leftInset = left; topInset = top; rightInset = right; bottomInset = bottom
        position()
    }
    private fun position() {
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        controls.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        controls.layoutParams = (controls.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.BOTTOM or if (landscape) Gravity.START else Gravity.END
        }
        val sideWidth = dp(280).coerceAtMost((activity.resources.displayMetrics.widthPixels - leftInset - rightInset) * 2 / 5)
        listOf(search, guidance).forEach { view ->
            view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply { width = if (landscape) sideWidth else MATCH; gravity = Gravity.TOP or Gravity.START }
        }
        summary.layoutParams = (summary.layoutParams as FrameLayout.LayoutParams).apply {
            width = if (landscape) sideWidth else MATCH
            gravity = Gravity.BOTTOM or if (landscape) Gravity.END else Gravity.CENTER_HORIZONTAL
        }
        guidance.maxLines = if (landscape) 4 else 5
        guidance.ellipsize = android.text.TextUtils.TruncateAt.END
        status.maxLines = if (landscape) 3 else 4
        status.ellipsize = android.text.TextUtils.TruncateAt.END
        place(search, top = topInset + dp(if (landscape) 12 else 82))
        place(guidance, top = topInset + dp(if (landscape) 74 else 142))
        place(summary, bottom = bottomInset + dp(110))
        place(controls, bottom = bottomInset + dp(if (landscape) 36 else 292))
    }
    private fun render() {
        renderer.updateNavigation(state, app.navigationSettings.snapshot(), app.places.snapshot().savedPlaces, app.preferences.snapshot().theme)
        (controls.getChildAt(2) as TextView).apply {
            val north = app.navigationSettings.snapshot().orientation == MapOrientation.NORTH_UP
            text = if (north) "N ↑" else "Rumo ↑"
            contentDescription = if (north) "Norte para cima; mudar orientação" else "Direção do movimento para cima; mudar orientação"
        }
        (controls.getChildAt(0) as TextView).text = when {
            !renderer.isFollowing -> "Recentralizar"
            state.locationAccess == LocationAccess.AVAILABLE -> "Seguindo"
            else -> "GPS"
        }
        val visible = state.destination != null || state.resumeDestination != null || state.serviceRunning || state.simulation ||
            state.message != null && state.locationAccess in setOf(LocationAccess.NEEDS_PHONE_START, LocationAccess.DISABLED, LocationAccess.LOST)
        controls.getChildAt(1).visibility = if (state.route != null) View.VISIBLE else View.GONE
        controls.getChildAt(2).visibility = if (visible) View.VISIBLE else View.GONE
        summary.visibility = if (visible) View.VISIBLE else View.GONE
        routeLayout(visible)
        val speed = state.location.speedKmh?.takeIf { state.location.quality == LocationQuality.GOOD }?.roundToInt()?.let { "$it km/h" } ?: "— km/h"
        val progress = state.progress.takeIf { state.guidanceAvailable }
        metrics.text = when {
            progress != null -> "${duration(progress.remainingSeconds)} · ${distance(progress.remainingDistanceMeters)} · ${eta(progress.arrivalEpochMs)}"
            state.phase == NavigationPhase.PREVIEW && state.route != null -> "${duration(state.route!!.durationSeconds)} · ${distance(state.route!!.distanceMeters)}"
            state.phase == NavigationPhase.ARRIVED -> "Você chegou!"
            state.phase == NavigationPhase.RESUME_AVAILABLE -> "Retomar destino?"
            else -> state.destination?.name ?: "Sua posição"
        }
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
        val mapNotice = if (app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD && !app.mapConnectivity.isOnline)
            "\nMapa mundial sem internet · toque para escolher Goiânia offline." else ""
        status.text = "$notice$speed · $quality" + (if (state.message != null) "\n${state.message}" else "") + mapNotice
        guidance.visibility = if (state.navigationActive || state.simulation) View.VISIBLE else View.GONE
        guidance.text = if (progress != null) {
            val step = state.route?.steps?.getOrNull(progress.stepIndex)
            "${distance(progress.distanceToManeuverMeters)}  ${step?.instruction ?: "Siga a rota"}" +
                (step?.streetName?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "")
        } else when (state.phase) {
            NavigationPhase.RECALCULATING -> "Recalculando rota…"
            NavigationPhase.REROUTE_FAILED -> "Rota indisponível · tente novamente"
            else -> "Aguardando GPS preciso para orientar"
        }
        primary.text = when {
            state.phase == NavigationPhase.PREVIEW -> "Iniciar navegação"
            state.phase == NavigationPhase.RESUME_AVAILABLE -> "Retomar e recalcular"
            state.phase == NavigationPhase.ERROR || state.phase == NavigationPhase.REROUTE_FAILED -> "Tentar novamente"
            state.phase == NavigationPhase.ARRIVED -> "Concluir"
            state.navigationActive -> "Opções da viagem"
            state.destination != null -> "Ver destino"
            else -> "Seus lugares e opções"
        }
        if (state.phase == NavigationPhase.PREVIEW && state.route?.id != lastPreviewId) {
            lastPreviewId = state.route?.id
            renderer.showRouteOverview()
        }
        search.text = if (state.simulation) "SIMULAÇÃO · Para onde?" else "Para onde?"
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
        position()
    }

    private fun primaryAction() = when (state.phase) {
        NavigationPhase.PREVIEW -> withGps { runtime.startNavigation(); renderer.recenter(); dialog?.dismiss() }
        NavigationPhase.RESUME_AVAILABLE -> withGps { runtime.resumeDestination() }
        NavigationPhase.ERROR, NavigationPhase.REROUTE_FAILED -> {
            if (!state.providerConfigured) showProvider() else withGps { runtime.retryRoute() }
        }
        NavigationPhase.ARRIVED -> runtime.stopNavigation()
        else -> showRoute()
    }
    private fun choose(destination: Destination) {
        dialog?.dismiss()
        runtime.selectDestination(destination)
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

    private fun showDestinations(initialQuery: String = "") {
        val content = sheet("Para onde?", "Endereços, estabelecimentos e seus lugares")
        val entry = input("Buscar endereço ou lugar", initialQuery)
        entry.imeOptions = EditorInfo.IME_ACTION_SEARCH
        content.addView(entry)
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
        action(content, "Minha posição · ativar GPS") { dialog?.dismiss(); withGps { renderer.recenter() } }
        action(content, "Salvar minha posição") { withGps {
            runtime.snapshot().location.takeIf { it.quality == LocationQuality.GOOD }?.fix?.point?.let { editPlace(Destination(UUID.randomUUID().toString(), "Minha posição", it)) }
                ?: toast("Aguarde uma posição atual e precisa e toque novamente.")
        } }
        action(content, "Marcador do veículo e orientação") { showVehicle() }
        action(content, "Cobertura do mapa · ${app.preferences.snapshot().baseMapMode.label}") {
            dialog?.dismiss(); openMapSettings()
        }
        action(content, if (state.providerConfigured) "Geoapify · chave configurada" else "Configurar chave Geoapify") { showProvider() }
        action(content, "Configurações de localização do aparelho") { activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS))
            action(content, "Permitir notificação da navegação") { permission.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }
        if (BuildConfig.DEBUG) action(content, "Desenvolvimento · simulação GPS") { showReplay() }
        note(content, if (app.preferences.snapshot().baseMapMode == MapBaseMode.WORLD)
            "O mapa mundial usa internet e dados OpenFreeMap / OpenStreetMap. Sem conexão, escolha Goiânia offline em Cobertura do mapa. Busca e rotas usam Geoapify. ETA sem trânsito em tempo real."
            else "Goiânia offline cobre somente o recorte incluído; fora dele, o mapa base fica sem detalhes. Busca e rotas usam internet. ETA sem trânsito em tempo real.")
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
        state.message?.let { note(content, it) }
        if (state.navigationActive) action(content, "Continuar viagem") { dialog?.dismiss() }
        else action(content, primary.text.toString()) { primaryAction() }
        action(content, "Visão geral da rota") { renderer.showRouteOverview(); dialog?.dismiss() }
        action(content, "Recentralizar e acompanhar") { withGps { renderer.recenter(); dialog?.dismiss() } }
        action(content, "Recalcular rota") { withGps { runtime.retryRoute(); dialog?.dismiss() } }
        action(content, "Salvar este destino") { editPlace(destination) }
        action(content, "Encerrar / cancelar rota") { runtime.stopNavigation(); dialog?.dismiss() }
        note(content, "Estimativas do provedor, sem trânsito ao vivo. Orientação visual, sem voz. Geoapify · © OpenStreetMap contributors.")
    }

    private fun showPoint(point: GeoPoint) {
        val destination = Destination(UUID.randomUUID().toString(), "Ponto no mapa", point,
            String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude))
        val content = sheet("Ponto no mapa", destination.address)
        action(content, "Traçar rota até aqui") { choose(destination) }
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
                action(details, "Ir para este local") { choose(saved.asDestination()) }
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
        iconGrid(content, app.icons.vehicleIcons(), true, preferences.vehicleId) { id ->
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
        dialog?.dismiss()
        searchResults = null; searchStatus = null
        val newDialog = BottomSheetDialog(activity)
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24) + bottomInset)
            setBackgroundColor(colors.surface)
        }
        action(content, "Fechar") { newDialog.dismiss() }.gravity = Gravity.END
        content.addView(label(title, 26f, true))
        note(content, subtitle)
        val scroll = ScrollView(activity).apply { addView(content); isFillViewport = false }
        newDialog.setContentView(scroll)
        newDialog.setOnDismissListener { if (dialog === newDialog) { dialog = null; searchStatus = null; searchResults = null } }
        newDialog.setOnShowListener {
            newDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { frame ->
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
        touchSurface(colors.soft, 14)
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
            marginStart = leftInset + dp(16); marginEnd = rightInset + dp(16); topMargin = top; bottomMargin = bottom
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
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
