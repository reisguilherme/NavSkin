package br.com.gamemap.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import br.com.gamemap.BuildConfig
import br.com.gamemap.navigation.core.EngineUpdate
import br.com.gamemap.navigation.core.GeoMath
import br.com.gamemap.navigation.core.NavigationEngine
import br.com.gamemap.navigation.model.*
import br.com.gamemap.navigation.provider.GeoapifyProvider
import br.com.gamemap.navigation.provider.ProviderErrorKind
import br.com.gamemap.navigation.provider.TypedProviderException
import br.com.gamemap.navigation.storage.LocalPlacesStore
import br.com.gamemap.navigation.storage.NavigationSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** The application owns one instance; phone, service and car observe the same main-thread state. */
class NavigationRuntime(
    context: Context,
    private val provider: GeoapifyProvider,
    private val settings: NavigationSettings,
    private val places: LocalPlacesStore,
    private val worker: ExecutorService = Executors.newFixedThreadPool(2),
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val serviceStarter: (Context) -> Unit = {
        ContextCompat.startForegroundService(it, Intent(it, NavigationService::class.java)); Unit
    },
    private val serviceStopper: (Context) -> Unit = {
        it.stopService(Intent(it, NavigationService::class.java)); Unit
    },
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine = NavigationEngine()
    private val listeners = linkedSetOf<(NavigationSnapshot) -> Unit>()
    private val clients = mutableMapOf<String, Int>()
    private var finePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private var coarsePermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    private var providersEnabled = locationEnabled()
    private var serviceRequested = false
    private var idleStopScheduled = false
    private var closed = false
    private var routeEpoch = 0L
    private var searchEpoch = 0L
    private var replayEpoch = 0L
    private var routeTask: Future<*>? = null
    private var searchTask: Future<*>? = null
    private var routeInFlight: Long? = null
    private var pendingPreview = false
    private var rateLimitedUntilMs = 0L
    private var locationLifecycleMessage: String? = null
    @Volatile private var state = NavigationSnapshot(
        phase = if (places.snapshot().resumeDestination != null) NavigationPhase.RESUME_AVAILABLE else NavigationPhase.IDLE,
        resumeDestination = places.snapshot().resumeDestination,
        resumeIntermediateStops = places.snapshot().resumeIntermediateStops,
        providerConfigured = settings.apiKey().isNotBlank(),
        message = places.snapshot().loadError,
    )
    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            applyEngine(engine.onTick(elapsedClock(), wallClock()))
            if (state.serviceRunning || state.simulation || state.location.fix != null && state.location.ageMs <= 15_000)
                main.postDelayed(this, 1_000L)
        }
    }
    private val stopUnusedService = Runnable {
        idleStopScheduled = false
        if (clients.isEmpty() && (!state.navigationActive || state.simulation)) {
            if (state.simulation) stopReplay()
            serviceRequested = false
            serviceStopper(appContext)
        }
    }
    private val removeSettingsListener = settings.addListener {
        onMain {
            publish(state.copy(providerConfigured = settings.apiKey().isNotBlank()))
            calculatePendingPreview()
        }
    }
    private val removePlacesListener = places.addListener { value ->
        onMain { publish(state.copy(resumeDestination = value.resumeDestination,
            resumeIntermediateStops = value.resumeIntermediateStops)) }
    }

    fun snapshot(): NavigationSnapshot = state

    fun addListener(listener: (NavigationSnapshot) -> Unit): () -> Unit {
        onMain { listeners += listener; listener(state) }
        return { onMain { listeners -= listener } }
    }

    fun attachClient(id: String): () -> Unit {
        onMain {
            clients[id] = (clients[id] ?: 0) + 1
            main.removeCallbacks(stopUnusedService)
            idleStopScheduled = false
            if (id == "phone") activatePendingPhoneLocation()
        }
        var attached = true
        return {
            onMain {
                if (attached) {
                    attached = false
                    val count = clients[id] ?: 0
                    if (count <= 1) clients.remove(id) else clients[id] = count - 1
                    scheduleIdleStop()
                }
            }
        }
    }

    fun setLocationPermission(fine: Boolean, coarse: Boolean) = onMain {
        finePermission = fine
        coarsePermission = coarse
        // Recreated phone/car views refresh real permissions; debug replay owns its separate source.
        if (state.simulation) return@onMain
        if (!fine && !coarse) {
            cancelRouteRequest()
            engine.stop()
            publish(state.copy(locationAccess = LocationAccess.DENIED, progress = null,
                phase = if (state.navigationActive) NavigationPhase.RESUME_AVAILABLE else state.phase,
                message = locationNotice("Permita o acesso à localização no celular.")))
            serviceStopper(appContext)
        } else {
            applyEngine(engine.onTick(elapsedClock(), wallClock()))
            activatePendingPhoneLocation()
        }
    }

    /** Call from a visible phone screen; Android may reject starting from a car-only background. */
    fun activateLocation(): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Activate location on the main thread" }
        if (closed || state.simulation) return false
        finePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        coarsePermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!finePermission && !coarsePermission) {
            publish(state.copy(locationAccess = LocationAccess.DENIED,
                message = locationNotice("Permita o acesso à localização no celular.")))
            return false
        }
        providersEnabled = locationEnabled()
        if (state.serviceRunning || serviceRequested) return true
        return try {
            serviceRequested = true
            publish(state.copy(locationAccess = if (providersEnabled) LocationAccess.WAITING else LocationAccess.DISABLED,
                message = locationNotice(if (providersEnabled) "Aguardando posição GPS atual." else "Ative a localização do aparelho.")))
            serviceStarter(appContext)
            true
        } catch (_: Exception) {
            serviceRequested = false
            publish(state.copy(locationAccess = LocationAccess.NEEDS_PHONE_START, serviceRunning = false,
                message = locationNotice("Abra o NavSkin no celular e ative o GPS para continuar.")))
            false
        }
    }

    fun search(query: String) = onMain {
        searchEpoch++
        searchTask?.cancel(true)
        val generation = searchEpoch
        val text = query.trim()
        if (text.length < 2) {
            publish(state.copy(searching = false, searchResults = emptyList(), searchError = null))
            return@onMain
        }
        val blocked = requestBlockMessage()
        if (blocked != null) {
            publish(state.copy(searching = false, searchError = blocked))
            return@onMain
        }
        val bias = state.location.fix?.point?.takeIf { state.location.quality == LocationQuality.GOOD }
        publish(state.copy(searching = true, searchError = null))
        searchTask = worker.submit {
            val result = runCatching { provider.search(text, bias) }
            onMain {
                if (generation != searchEpoch) return@onMain
                result.fold({ publish(state.copy(searching = false, searchResults = it, searchError = null)) }, {
                    recordRateLimit(it)
                    publish(state.copy(searching = false, searchResults = emptyList(), searchError = safeMessage(it)))
                })
            }
        }
    }

    fun selectDestination(destination: Destination) = onMain {
        selectItinerary(destination, emptyList())
    }

    private fun selectItinerary(destination: Destination, stops: List<Destination>) {
        if (state.simulation) stopReplay()
        cancelRouteRequest()
        engine.stop()
        saveResume(null)
        pendingPreview = true
        publish(state.copy(phase = NavigationPhase.IDLE, destination = destination, route = null,
            progress = null, intermediateStops = stops.toList(), pendingIntermediateStops = null,
            message = null, sessionId = state.sessionId + 1))
        if (finePermission && !state.serviceRunning && !serviceRequested) activateLocation()
        requestRoute(recalculation = false)
    }

    fun addStop(destination: Destination) = onMain {
        if (state.simulation) {
            publish(state.copy(message = "SIMULAÇÃO GPS: adicionar paradas pela internet está desativado."))
            return@onMain
        }
        val final = state.destination ?: return@onMain
        if (!state.navigationActive && state.phase != NavigationPhase.PREVIEW) return@onMain
        val stops = state.pendingIntermediateStops ?: state.intermediateStops
        val message = when {
            destination.id.isBlank() || destination.name.isBlank() || !GeoMath.isValid(destination.point) ->
                "Escolha um local válido para a parada."
            (stops + final).any { it.id == destination.id || GeoMath.distanceMeters(it.point, destination.point) <= 5.0 } ->
                "Esse local já está no percurso."
            state.location.quality == LocationQuality.GOOD && state.location.fix?.let {
                GeoMath.distanceMeters(it.point, destination.point) <= 5.0
            } == true -> "Você já está nesse local. Escolha outra parada."
            else -> null
        }
        if (message != null) publish(state.copy(message = message))
        else editStops(stops + destination)
    }

    fun removeStop(id: String) = onMain {
        if (state.simulation || state.destination == null ||
            !state.navigationActive && state.phase != NavigationPhase.PREVIEW) return@onMain
        val stops = state.pendingIntermediateStops ?: state.intermediateStops
        if (stops.none { it.id == id }) return@onMain
        editStops(stops.filterNot { it.id == id })
    }

    private fun editStops(stops: List<Destination>) {
        val active = state.navigationActive
        cancelRouteRequest()
        publish(state.copy(pendingIntermediateStops = stops.toList()))
        requestRoute(active)
    }

    fun startNavigation() = onMain {
        if (state.phase != NavigationPhase.PREVIEW || state.route == null) return@onMain
        if (state.pendingIntermediateStops != null) {
            publish(state.copy(message = "Recalcule as paradas pendentes antes de iniciar a navegação."))
            return@onMain
        }
        applyEngine(engine.onTick(elapsedClock(), wallClock()))
        if (!finePermission || !state.serviceRunning || state.location.quality != LocationQuality.GOOD) {
            publish(state.copy(message = "Ative o GPS preciso no celular e aguarde uma posição atual antes de iniciar."))
            return@onMain
        }
        engine.start(state.route!!)
        val fix = state.location.fix!!
        val update = engine.onFix(fix, elapsedClock(), wallClock())
        // The engine has already seen this fix before preview; the next GPS reading will match it.
        publish(state.copy(phase = NavigationPhase.NAVIGATING, progress = update.progress, message = null))
        saveResume(state.destination)
        runCatching { places.rememberRecent(state.destination!!) }.onFailure { publish(state.copy(message = "Navegação ativa; não foi possível salvar o destino recente.")) }
        main.removeCallbacks(stopUnusedService)
        idleStopScheduled = false
        startTicker()
    }

    /** The search-bar X only cancels preparation; an active trip keeps its explicit Stop action. */
    fun clearDestination() = onMain {
        if (state.navigationActive || state.simulation) return@onMain
        stopNavigation()
    }

    fun stopNavigation() = onMain {
        val wasSimulation = state.simulation
        cancelRouteRequest()
        searchEpoch++
        searchTask?.cancel(true)
        replayEpoch++
        pendingPreview = false
        if (wasSimulation) engine = NavigationEngine() else engine.stop()
        saveResume(null)
        publish(state.copy(phase = NavigationPhase.IDLE, destination = null, route = null, progress = null,
            intermediateStops = emptyList(), pendingIntermediateStops = null,
            resumeDestination = null, resumeIntermediateStops = emptyList(),
            location = if (wasSimulation) LocationState() else state.location,
            locationAccess = if (wasSimulation) LocationAccess.WAITING else state.locationAccess,
            message = null, searching = false, searchResults = emptyList(), searchError = null,
            simulation = false, sessionId = state.sessionId + 1))
        scheduleIdleStop()
    }

    fun retryRoute() = onMain {
        if (state.destination != null && !state.simulation && routeInFlight == null) {
            if (!state.navigationActive) pendingPreview = true
            requestRoute(state.navigationActive)
        }
    }

    fun resumeDestination() = onMain {
        state.resumeDestination?.let { selectItinerary(it, state.resumeIntermediateStops) }
    }

    fun onServiceStarted() = onMain {
        serviceRequested = true
        publish(state.copy(serviceRunning = true, locationAccess = accessFor(state.location)))
        startTicker()
        scheduleIdleStop()
    }

    fun onServiceStopped() = onMain {
        serviceRequested = false
        if (state.navigationActive && !state.simulation) {
            cancelRouteRequest()
            engine.stop()
            publish(state.copy(serviceRunning = false, phase = NavigationPhase.RESUME_AVAILABLE,
                locationAccess = LocationAccess.NEEDS_PHONE_START, progress = null, route = null,
                pendingIntermediateStops = null,
                message = locationNotice("GPS interrompido. Abra o celular e recalcule o destino para retomar.")))
        } else if (pendingPreview && state.phase == NavigationPhase.WAITING_FOR_LOCATION) {
            publish(state.copy(serviceRunning = false, locationAccess = when {
                !finePermission && !coarsePermission -> LocationAccess.DENIED
                !providersEnabled -> LocationAccess.DISABLED
                !finePermission -> LocationAccess.WEAK
                else -> LocationAccess.NEEDS_PHONE_START
            }, message = locationNotice("GPS interrompido. Ative a localização no celular para calcular a rota.")))
        } else publish(state.copy(serviceRunning = false))
    }

    fun onLocationProvidersChanged(enabled: Boolean) = onMain {
        providersEnabled = enabled
        applyEngine(engine.onTick(elapsedClock(), wallClock()))
        publish(state.copy(locationAccess = accessFor(state.location),
            message = if (!enabled) locationNotice("Localização desativada no aparelho.") else state.message))
    }

    fun onLocationFailure(message: String) = onMain {
        publish(state.copy(locationAccess = if (!finePermission && !coarsePermission) LocationAccess.DENIED
            else LocationAccess.NEEDS_PHONE_START, message = locationNotice(message), progress = null))
    }

    fun acceptFix(fix: LocationFix) = onMain {
        if (!state.simulation && (finePermission || coarsePermission)) {
            val allowedFix = if (finePermission) fix else fix.copy(accuracyMeters = maxOf(100.0, fix.accuracyMeters))
            applyEngine(engine.onFix(allowedFix, elapsedClock(), wallClock()))
            startTicker()
        }
    }

    /** Explicit debug-only playback. It never substitutes fixtures for a production HTTP response. */
    fun startReplay(route: NavRoute, fixes: List<LocationFix>, intervalMs: Long = 1_000L) = onMain {
        if (!BuildConfig.DEBUG || fixes.isEmpty() || clients.isEmpty()) return@onMain
        stopNavigation()
        serviceRequested = false
        serviceStopper(appContext)
        engine = NavigationEngine()
        engine.start(route)
        val generation = ++replayEpoch
        publish(state.copy(phase = NavigationPhase.NAVIGATING, route = route, destination = route.destination,
            intermediateStops = route.intermediateStops.map { it.destination }, pendingIntermediateStops = null,
            simulation = true, progress = null, message = "SIMULAÇÃO GPS DE DESENVOLVIMENTO", sessionId = state.sessionId + 1))
        fun replay(index: Int) {
            if (closed || generation != replayEpoch || !state.simulation) return
            val source = fixes[index]
            applyEngine(engine.onFix(source.copy(elapsedRealtimeMs = elapsedClock(), wallTimeMs = wallClock(), isMock = true),
                elapsedClock(), wallClock()))
            if (index + 1 < fixes.size) main.postDelayed({ replay(index + 1) }, intervalMs.coerceIn(250L, 60_000L))
            else publish(state.copy(message = "SIMULAÇÃO GPS: reprodução encerrada; o sinal será marcado como perdido."))
        }
        replay(0)
        startTicker()
    }

    fun stopReplay() = onMain {
        if (state.simulation) stopNavigation()
    }

    private fun requestRoute(recalculation: Boolean) {
        if (routeInFlight != null || state.simulation) return
        val destination = state.destination ?: return
        val requestedStops = (state.pendingIntermediateStops ?: state.intermediateStops).toList()
        val editingStops = state.pendingIntermediateStops != null
        val fix = state.location.fix
        val block = requestBlockMessage()
        if (block != null) {
            pendingPreview = false
            publishRouteFailure(block, recalculation, editingStops)
            return
        }
        if (!finePermission || state.location.quality != LocationQuality.GOOD || fix == null ||
            elapsedClock() - fix.elapsedRealtimeMs !in 0L..5_000L || !providersEnabled) {
            if (!recalculation && !editingStops) pendingPreview = true
            publishRouteFailure(if (recalculation || editingStops)
                    "Aguarde uma posição GPS atual e precisa para calcular a rota."
                    else "A rota será calculada automaticamente quando o GPS tiver uma posição atual e precisa.",
                recalculation, editingStops, waitingForLocation = true)
            return
        }
        val generation = ++routeEpoch
        pendingPreview = false
        routeInFlight = generation
        val origin = state.location.fix!!.point
        publish(state.copy(phase = if (recalculation) NavigationPhase.RECALCULATING else NavigationPhase.CALCULATING,
            progress = null, message = if (recalculation) "Recalculando rota…" else "Calculando rota…"))
        routeTask = worker.submit {
            val result = runCatching { provider.route(origin, destination, requestedStops) }
            onMain {
                if (generation != routeEpoch || state.destination?.id != destination.id) return@onMain
                routeInFlight = null
                result.fold({ route ->
                    if (recalculation) engine.start(route)
                    publish(state.copy(route = route, progress = null, intermediateStops = requestedStops,
                        pendingIntermediateStops = null,
                        phase = if (recalculation) NavigationPhase.NAVIGATING else NavigationPhase.PREVIEW,
                        message = "Estimativa sem trânsito em tempo real."))
                    if (recalculation) saveResume(destination)
                }, { error ->
                    recordRateLimit(error)
                    publishRouteFailure(safeMessage(error), recalculation, editingStops)
                })
                scheduleIdleStop()
            }
        }
    }

    private fun applyEngine(update: EngineUpdate) {
        val usableSource = state.simulation || providersEnabled && (finePermission || coarsePermission)
        val location = when {
            !usableSource -> update.location.copy(
                quality = if (update.location.fix == null) LocationQuality.UNAVAILABLE else LocationQuality.LOST,
                speedKmh = null)
            !state.simulation && !finePermission && update.location.quality == LocationQuality.GOOD ->
                update.location.copy(quality = LocationQuality.WEAK, speedKmh = null)
            else -> update.location
        }
        var next = state.copy(location = location, locationAccess = accessFor(location),
            progress = if (state.phase == NavigationPhase.NAVIGATING && location.quality == LocationQuality.GOOD)
                update.progress else if (state.navigationActive) null else state.progress)
        // Only accepted progress along this provider route can consume an intermediate stop.
        // Endpoint/centroid proximity alone would remove stops on a parallel road or later loop.
        val passedIds = if (state.navigationActive && location.quality == LocationQuality.GOOD &&
            update.progress?.onRoute == true && update.progress.routeId == state.route?.id) {
            val progress = update.progress
            state.route!!.intermediateStops.filter { boundary ->
                boundary.alongRouteMeters <= progress.alongRouteMeters + 0.01 &&
                    state.intermediateStops.any { it.id == boundary.destination.id }
            }.map { it.destination.id }.toSet()
        } else emptySet()
        val refreshPendingRoute = passedIds.isNotEmpty() && routeInFlight != null
        if (passedIds.isNotEmpty()) {
            next = next.copy(intermediateStops = next.intermediateStops.filterNot { it.id in passedIds },
                pendingIntermediateStops = next.pendingIntermediateStops?.filterNot { it.id in passedIds })
            if (refreshPendingRoute) cancelRouteRequest()
        }
        if (!state.simulation && location.quality == LocationQuality.GOOD) {
            // Only dismiss a notice still owned by the location lifecycle; newer route errors survive.
            if (next.message == locationLifecycleMessage) next = next.copy(message = null)
            locationLifecycleMessage = null
        }
        val arrived = state.phase == NavigationPhase.NAVIGATING && state.pendingIntermediateStops == null &&
            location.quality == LocationQuality.GOOD && update.arrived
        if (arrived) {
            next = next.copy(phase = NavigationPhase.ARRIVED, progress = update.progress,
                resumeDestination = null, resumeIntermediateStops = emptyList(), intermediateStops = emptyList(),
                message = "Você chegou ao destino.")
            engine.stop()
        }
        publish(next)
        if (arrived) saveResume(null)
        else if (passedIds.isNotEmpty() && !state.simulation) saveResume(state.destination)
        calculatePendingPreview()
        if (refreshPendingRoute) requestRoute(recalculation = true)
        if (update.shouldRecalculate && state.phase in setOf(NavigationPhase.NAVIGATING, NavigationPhase.REROUTE_FAILED)) {
            if (state.simulation) publish(state.copy(phase = NavigationPhase.REROUTE_FAILED, progress = null,
                message = "SIMULAÇÃO GPS: desvio confirmado; cálculo pela internet desativado nesta reprodução."))
            else requestRoute(recalculation = true)
        }
        scheduleIdleStop()
    }

    private fun calculatePendingPreview() {
        if (pendingPreview && !state.simulation && state.providerConfigured && providersEnabled &&
            state.location.quality == LocationQuality.GOOD && routeInFlight == null &&
            state.phase == NavigationPhase.WAITING_FOR_LOCATION) requestRoute(recalculation = false)
    }

    private fun activatePendingPhoneLocation() {
        if (pendingPreview && state.phase == NavigationPhase.WAITING_FOR_LOCATION && clients.containsKey("phone") &&
            finePermission && !state.serviceRunning && !serviceRequested) activateLocation()
    }

    private fun accessFor(location: LocationState): LocationAccess = when {
        state.simulation -> when (location.quality) {
            LocationQuality.GOOD -> LocationAccess.AVAILABLE
            LocationQuality.WEAK -> LocationAccess.WEAK
            LocationQuality.LOST -> LocationAccess.LOST
            else -> LocationAccess.WAITING
        }
        !finePermission && !coarsePermission -> LocationAccess.DENIED
        !providersEnabled -> LocationAccess.DISABLED
        !finePermission -> LocationAccess.WEAK
        pendingPreview && state.phase == NavigationPhase.WAITING_FOR_LOCATION &&
            !state.serviceRunning && !serviceRequested -> LocationAccess.NEEDS_PHONE_START
        location.quality == LocationQuality.LOST -> LocationAccess.LOST
        location.quality == LocationQuality.WEAK -> LocationAccess.WEAK
        location.quality == LocationQuality.GOOD -> LocationAccess.AVAILABLE
        else -> LocationAccess.WAITING
    }

    private fun requestBlockMessage(): String? = when {
        settings.apiKey().isBlank() -> "Configure sua chave Geoapify no celular."
        elapsedClock() < rateLimitedUntilMs -> "Limite de consultas atingido. Aguarde antes de tentar novamente."
        else -> null
    }

    private fun recordRateLimit(error: Throwable) {
        if (error is TypedProviderException && error.kind == ProviderErrorKind.RATE_LIMIT) {
            rateLimitedUntilMs = elapsedClock() + (error.retryAfterSeconds ?: 60).coerceIn(1, 86_400) * 1_000L
        }
    }

    private fun safeMessage(error: Throwable): String = (error as? TypedProviderException)?.message
        ?: "Não foi possível concluir a consulta. Tente novamente."

    private fun locationNotice(message: String): String {
        locationLifecycleMessage = message
        return message
    }

    private fun cancelRouteRequest() {
        routeEpoch++
        routeTask?.cancel(true)
        routeTask = null
        routeInFlight = null
    }

    private fun routeFailureMessage(message: String, editingStops: Boolean) = if (editingStops)
        "$message A alteração das paradas não foi aplicada; tente novamente." else message

    private fun publishRouteFailure(message: String, recalculation: Boolean, editingStops: Boolean,
                                    waitingForLocation: Boolean = false) {
        // A failed itinerary edit does not invalidate an otherwise usable committed route.
        // Genuine off-route reroutes and weak/lost GPS still suspend its instructions.
        val safeProgress = if (recalculation && editingStops && finePermission && providersEnabled && state.serviceRunning)
            engine.onTick(elapsedClock(), wallClock()).takeIf { it.location.quality == LocationQuality.GOOD }
                ?.progress?.takeIf { it.onRoute && it.routeId == state.route?.id }
            else null
        val phase = when {
            safeProgress != null -> NavigationPhase.NAVIGATING
            recalculation -> NavigationPhase.REROUTE_FAILED
            editingStops && state.route != null -> NavigationPhase.PREVIEW
            waitingForLocation -> NavigationPhase.WAITING_FOR_LOCATION
            else -> NavigationPhase.ERROR
        }
        publish(state.copy(phase = phase, route = state.route.takeIf { recalculation || editingStops },
            progress = safeProgress, message = routeFailureMessage(message, editingStops)))
    }

    private fun saveResume(destination: Destination?) {
        runCatching { places.setResumeDestination(destination,
            if (destination == null) emptyList() else state.intermediateStops) }
            .onFailure { publish(state.copy(message = "Não foi possível salvar o destino para retomada.")) }
    }

    private fun scheduleIdleStop() {
        val shouldStop = clients.isEmpty() && (!state.navigationActive || state.simulation)
        if (!shouldStop) {
            main.removeCallbacks(stopUnusedService)
            idleStopScheduled = false
        } else if (!idleStopScheduled) {
            idleStopScheduled = true
            main.postDelayed(stopUnusedService, 5_000L)
        }
    }

    private fun startTicker() {
        main.removeCallbacks(tick)
        main.postDelayed(tick, 1_000L)
    }

    private fun publish(next: NavigationSnapshot) {
        if (state == next || closed) return
        state = next
        listeners.toList().forEach { it(next) }
    }

    private fun onMain(block: () -> Unit) {
        if (closed) return
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { if (!closed) block() }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun locationEnabled(): Boolean = runCatching {
        val manager = appContext.getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 28) manager.isLocationEnabled else
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)

    override fun close() = onMain {
        routeEpoch++
        searchEpoch++
        replayEpoch++
        routeTask?.cancel(true)
        searchTask?.cancel(true)
        main.removeCallbacksAndMessages(null)
        removeSettingsListener()
        removePlacesListener()
        worker.shutdownNow()
        serviceStopper(appContext)
        listeners.clear()
        closed = true
    }
}
