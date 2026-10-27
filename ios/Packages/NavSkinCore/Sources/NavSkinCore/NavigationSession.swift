import Foundation

/// The app owns one main-actor session and supplies CoreLocation and its one-second tick.
@MainActor
public final class NavigationSession {
    public private(set) var snapshot: NavigationSnapshot
    public var onChange: ((NavigationSnapshot) -> Void)?
    public var startLocation: (() -> Void)?
    public var stopLocation: (() -> Void)?
    private let places: PlacesStore
    private let apiKey: () -> String
    private let providerFactory: (String) -> any NavigationProviding
    private let elapsedClock: () -> Int64
    private let wallClock: () -> Int64
    private let allowSimulatedLocations: Bool
    private var engine = NavigationEngine()
    private var routeTask: Task<Void, Never>?
    private var searchTask: Task<Void, Never>?
    private var replayTask: Task<Void, Never>?
    private var routeEpoch: Int64 = 0
    private var searchEpoch: Int64 = 0
    private var replayEpoch: Int64 = 0
    private var routeInFlight: Int64?
    private var pendingPreview = false
    private var rateLimitedUntilMs: Int64 = 0
    private var sourceAccess: LocationAccess = .notRequested
    private var locationRequested = false
    private var locationLifecycleMessage: String?
    private var foreground = true
    private nonisolated static let monotonicAnchor = ContinuousClock.now

    public nonisolated static func elapsedMilliseconds() -> Int64 {
        let components = monotonicAnchor.duration(to: ContinuousClock.now).components
        return components.seconds * 1000 + components.attoseconds / 1_000_000_000_000_000
    }

    public init(places: PlacesStore, apiKey: @escaping () -> String,
                providerFactory: @escaping (String) -> any NavigationProviding = { GeoapifyProvider(apiKey: $0) },
                elapsedClock: @escaping () -> Int64 = { NavigationSession.elapsedMilliseconds() },
                wallClock: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
                allowSimulatedLocations: Bool = false) {
        self.places = places
        self.apiKey = apiKey
        self.providerFactory = providerFactory
        self.elapsedClock = elapsedClock
        self.wallClock = wallClock
        self.allowSimulatedLocations = allowSimulatedLocations
        snapshot = NavigationSnapshot(phase: places.snapshot.resumeDestination == nil ? .idle : .resumeAvailable,
            message: places.snapshot.loadError, providerConfigured: !apiKey().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            resumeDestination: places.snapshot.resumeDestination)
    }

    deinit { routeTask?.cancel(); searchTask?.cancel(); replayTask?.cancel() }

    public func search(_ query: String) {
        searchEpoch &+= 1
        searchTask?.cancel()
        searchTask = nil
        let generation = searchEpoch
        guard !snapshot.simulation else {
            change {
                $0.searching = false; $0.searchResults = []
                $0.searchError = "SIMULAÇÃO GPS: busca pela internet desativada nesta reprodução."
            }
            return
        }
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= 2 else {
            change { $0.searching = false; $0.searchResults = []; $0.searchError = nil }
            return
        }
        if let block = requestBlockMessage() {
            change { $0.searching = false; $0.searchResults = []; $0.searchError = block }
            return
        }
        // Re-evaluate age even if the UI's last tick has not happened yet.
        applyEngine(engine.onTick(nowElapsedMs: elapsedClock(), nowWallMs: wallClock()))
        let bias = snapshot.location.quality == .good ? snapshot.location.fix?.point : nil
        let provider = providerFactory(apiKey())
        change { $0.searching = true; $0.searchError = nil }
        searchTask = Task { [weak self] in
            let result: Result<[Destination], Error>
            do { result = .success(try await provider.search(query: text, bias: bias)) }
            catch { result = .failure(error) }
            guard let self, generation == self.searchEpoch else { return }
            self.searchTask = nil
            switch result {
            case .success(let destinations):
                self.change { $0.searching = false; $0.searchResults = destinations; $0.searchError = nil }
            case .failure(let error):
                self.recordRateLimit(error)
                self.change { $0.searching = false; $0.searchResults = []; $0.searchError = self.safeMessage(error) }
            }
        }
    }

    public func selectDestination(_ destination: Destination) {
        if snapshot.simulation { stopReplay() }
        cancelRouteRequest()
        engine.stop()
        pendingPreview = true
        let storageError = saveResume(nil)
        change {
            $0.phase = .idle; $0.destination = destination; $0.route = nil; $0.progress = nil
            $0.message = storageError; $0.sessionId &+= 1
        }
        if !snapshot.serviceRunning, !locationRequested { activateGPS() }
        requestRoute(recalculation: false)
    }

    public func startNavigation() {
        guard snapshot.phase == .preview, let route = snapshot.route else { return }
        applyEngine(engine.onTick(nowElapsedMs: elapsedClock(), nowWallMs: wallClock()))
        guard snapshot.serviceRunning, snapshot.locationAccess == .available,
              snapshot.location.quality == .good, let fix = snapshot.location.fix,
              isFresh(fix) else {
            change { $0.message = "Ative o GPS preciso no celular e aguarde uma posição atual antes de iniciar." }
            return
        }
        do { try engine.start(route) }
        catch {
            change { $0.phase = .error; $0.progress = nil; $0.message = "O serviço retornou dados de navegação inválidos. Tente novamente." }
            return
        }
        let update = engine.onFix(fix, nowElapsedMs: elapsedClock(), nowWallMs: wallClock())
        change { $0.phase = .navigating; $0.progress = update.progress; $0.message = nil }
        if let message = saveResume(snapshot.destination) { change { $0.message = message } }
        if let destination = snapshot.destination {
            do { try places.rememberRecent(destination) }
            catch { change { $0.message = "Navegação ativa; não foi possível salvar o destino recente." } }
        }
    }

    public func stopNavigation() {
        let wasSimulation = snapshot.simulation
        cancelRouteRequest()
        searchEpoch &+= 1
        searchTask?.cancel()
        searchTask = nil
        replayEpoch &+= 1
        replayTask?.cancel()
        replayTask = nil
        pendingPreview = false
        if wasSimulation { engine = NavigationEngine(); sourceAccess = .waiting; locationRequested = false }
        else { engine.stop() }
        let storageError = saveResume(nil)
        locationLifecycleMessage = nil
        change {
            $0.phase = .idle; $0.destination = nil; $0.route = nil; $0.progress = nil
            if wasSimulation { $0.location = LocationState(); $0.locationAccess = .waiting; $0.serviceRunning = false }
            $0.message = storageError; $0.searching = false; $0.searchResults = []; $0.searchError = nil
            $0.simulation = false; $0.sessionId &+= 1
        }
        if !foreground { locationRequested = false; stopLocation?() }
    }

    public func retryRoute() {
        guard snapshot.destination != nil, !snapshot.simulation else { return }
        let recalculation = snapshot.navigationActive
        if !recalculation { pendingPreview = true }
        requestRoute(recalculation: recalculation)
    }

    public func resumeDestination() {
        if let destination = places.snapshot.resumeDestination { selectDestination(destination) }
    }

    public func activateGPS() {
        guard !snapshot.simulation, !snapshot.serviceRunning, !locationRequested else { return }
        locationRequested = true
        sourceAccess = .waiting
        let message = locationNotice("Aguardando posição GPS atual.")
        change { $0.locationAccess = .waiting; $0.message = message }
        startLocation?()
    }

    /// Platform authority describes permission/provider status; engine quality describes each fix.
    public func setLocationStatus(access: LocationAccess, running: Bool, message: String? = nil) {
        guard !snapshot.simulation else { return }
        sourceAccess = access
        locationRequested = running
        let mustSuspend = snapshot.navigationActive && (!running || access == .denied || access == .needsPhoneStart)
        if mustSuspend {
            cancelRouteRequest()
            engine.stop()
        }
        let notice: String?
        if let message { notice = locationNotice(message) }
        else if mustSuspend { notice = locationNotice("GPS interrompido. Abra o celular e recalcule o destino para retomar.") }
        else { notice = nil }
        change {
            $0.serviceRunning = running; $0.locationAccess = access
            if mustSuspend { $0.phase = .resumeAvailable; $0.route = nil; $0.progress = nil }
            if let notice { $0.message = notice }
        }
        applyEngine(engine.onTick(nowElapsedMs: elapsedClock(), nowWallMs: wallClock()))
    }

    public func acceptFix(_ fix: LocationFix) {
        guard !snapshot.simulation, sourceAccess != .denied, sourceAccess != .disabled,
              sourceAccess != .notRequested, !fix.isMock || allowSimulatedLocations else { return }
        if sourceAccess == .lost, GeoMath.isValid(fix.point), fix.accuracyMeters.isFinite, fix.accuracyMeters >= 0,
           fix.elapsedRealtimeMs >= 0, fix.elapsedRealtimeMs > (snapshot.location.fix?.elapsedRealtimeMs ?? -1),
           fix.elapsedRealtimeMs <= elapsedClock() {
            sourceAccess = .waiting
        }
        applyEngine(engine.onFix(fix, nowElapsedMs: elapsedClock(), nowWallMs: wallClock()))
    }

    public func tick() { applyEngine(engine.onTick(nowElapsedMs: elapsedClock(), nowWallMs: wallClock())) }

    public func setForeground(_ visible: Bool) {
        foreground = visible
        if !visible {
            if snapshot.simulation { stopReplay() }
            else if !snapshot.navigationActive { locationRequested = false; stopLocation?() }
        }
    }

    public func keyChanged() {
        let wasCalculating = snapshot.phase == .calculating
        let wasRecalculating = snapshot.phase == .recalculating
        cancelRouteRequest()
        searchEpoch &+= 1
        searchTask?.cancel()
        searchTask = nil
        if wasCalculating { pendingPreview = true }
        change {
            $0.providerConfigured = !apiKey().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            $0.searching = false; $0.searchResults = []; $0.searchError = nil
            if wasCalculating { $0.phase = .error; $0.progress = nil }
            if wasRecalculating { $0.phase = .rerouteFailed; $0.progress = nil }
        }
        if wasRecalculating { requestRoute(recalculation: true) }
        else { calculatePendingPreview() }
    }

    /// The app exposes this only in DEBUG. Production HTTP is never replaced by replay fixtures.
    public func startReplay(route: NavRoute, fixes: [LocationFix], intervalMs: Int64 = 1000) {
        guard foreground, !fixes.isEmpty else { return }
        stopNavigation()
        locationRequested = false
        stopLocation?()
        engine = NavigationEngine()
        do { try engine.start(route) }
        catch { change { $0.message = "SIMULAÇÃO GPS: rota inválida." }; return }
        replayEpoch &+= 1
        let generation = replayEpoch
        change {
            $0.phase = .navigating; $0.route = route; $0.destination = route.destination
            $0.simulation = true; $0.serviceRunning = false; $0.progress = nil
            $0.message = "SIMULAÇÃO GPS DE DESENVOLVIMENTO"; $0.sessionId &+= 1
        }
        feedReplay(fixes[0])
        if fixes.count == 1 {
            change { $0.message = "SIMULAÇÃO GPS: reprodução encerrada; o sinal será marcado como perdido." }
            return
        }
        let nanoseconds = UInt64(min(60_000, max(250, intervalMs))) * 1_000_000
        replayTask = Task { [weak self] in
            for fix in fixes.dropFirst() {
                do { try await Task.sleep(nanoseconds: nanoseconds) }
                catch { return }
                guard let self, generation == self.replayEpoch, self.snapshot.simulation, self.foreground else { return }
                self.feedReplay(fix)
            }
            guard let self, generation == self.replayEpoch, self.snapshot.simulation else { return }
            self.replayTask = nil
            self.change { $0.message = "SIMULAÇÃO GPS: reprodução encerrada; o sinal será marcado como perdido." }
        }
    }

    public func stopReplay() { if snapshot.simulation { stopNavigation() } }

    private func feedReplay(_ source: LocationFix) {
        var fix = source
        fix.elapsedRealtimeMs = elapsedClock(); fix.wallTimeMs = wallClock(); fix.isMock = true
        applyEngine(engine.onFix(fix, nowElapsedMs: elapsedClock(), nowWallMs: wallClock()))
    }

    private func requestRoute(recalculation: Bool) {
        guard routeInFlight == nil, !snapshot.simulation, let destination = snapshot.destination else { return }
        let fix = snapshot.location.fix
        let gpsReady = snapshot.location.quality == .good && fix.map(isFresh) == true &&
            sourceAccess != .denied && sourceAccess != .disabled && sourceAccess != .weak && sourceAccess != .notRequested
        if let block = requestBlockMessage() ?? (gpsReady ? nil : "Aguarde uma posição GPS atual e precisa para calcular a rota.") {
            change { $0.phase = recalculation ? .rerouteFailed : .error; $0.progress = nil; $0.message = block }
            return
        }
        guard let origin = fix?.point else { return }
        routeEpoch &+= 1
        let generation = routeEpoch
        pendingPreview = false
        routeInFlight = generation
        let provider = providerFactory(apiKey())
        change {
            $0.phase = recalculation ? .recalculating : .calculating; $0.progress = nil
            $0.message = recalculation ? "Recalculando rota…" : "Calculando rota…"
        }
        routeTask = Task { [weak self] in
            let result: Result<NavRoute, Error>
            do { result = .success(try await provider.route(origin: origin, destination: destination)) }
            catch { result = .failure(error) }
            guard let self, generation == self.routeEpoch, self.snapshot.destination?.id == destination.id else { return }
            self.routeInFlight = nil
            self.routeTask = nil
            switch result {
            case .success(let route):
                do {
                    // Validate both preview and replacement routes before exposing them.
                    let validator = NavigationEngine()
                    try validator.start(route)
                    guard route.destination == destination else { throw NavigationEngineError.invalidDestination }
                    if recalculation { try self.engine.start(route) }
                    self.change {
                        $0.route = route; $0.progress = nil; $0.phase = recalculation ? .navigating : .preview
                        $0.message = "Estimativa sem trânsito em tempo real."
                    }
                } catch { self.routeFailed(TypedProviderException(kind: .invalidResponse), recalculation: recalculation) }
            case .failure(let error): self.routeFailed(error, recalculation: recalculation)
            }
        }
    }

    private func routeFailed(_ error: Error, recalculation: Bool) {
        recordRateLimit(error)
        change {
            $0.phase = recalculation ? .rerouteFailed : .error
            if !recalculation { $0.route = nil }
            $0.progress = nil; $0.message = safeMessage(error)
        }
    }

    private func applyEngine(_ update: EngineUpdate) {
        var location = update.location
        let blockedSource = !snapshot.simulation && [.denied, .disabled, .needsPhoneStart, .notRequested, .lost].contains(sourceAccess)
        if blockedSource {
            location.quality = location.fix == nil ? .unavailable : .lost
            location.speedKmh = nil
        } else if !snapshot.simulation, sourceAccess == .weak {
            if location.quality == .good { location.quality = .weak }
            location.speedKmh = nil
        }
        var next = snapshot
        next.location = location
        next.locationAccess = accessFor(location)
        if snapshot.phase == .navigating, location.quality == .good { next.progress = update.progress }
        else if snapshot.navigationActive || location.quality != .good { next.progress = nil }
        if !snapshot.simulation, location.quality == .good {
            if next.message == locationLifecycleMessage { next.message = nil }
            locationLifecycleMessage = nil
        }
        if snapshot.navigationActive, update.arrived, location.quality == .good {
            let storageError = saveResume(nil)
            next.phase = .arrived; next.progress = update.progress
            next.message = storageError ?? "Você chegou ao destino."
            engine.stop()
        }
        publish(next)
        calculatePendingPreview()
        if update.shouldRecalculate, [.navigating, .rerouteFailed].contains(snapshot.phase) {
            if snapshot.simulation {
                change { $0.phase = .rerouteFailed; $0.progress = nil; $0.message = "SIMULAÇÃO GPS: desvio confirmado; cálculo pela internet desativado nesta reprodução." }
            } else { requestRoute(recalculation: true) }
        }
    }

    private func calculatePendingPreview() {
        if pendingPreview, !snapshot.simulation, snapshot.providerConfigured, snapshot.location.quality == .good,
           routeInFlight == nil, [.idle, .error].contains(snapshot.phase) { requestRoute(recalculation: false) }
    }
    private func accessFor(_ location: LocationState) -> LocationAccess {
        if !snapshot.simulation {
            if [.denied, .disabled, .needsPhoneStart, .notRequested, .lost].contains(sourceAccess) { return sourceAccess }
            if sourceAccess == .weak { return location.quality == .lost ? .lost : .weak }
        }
        switch location.quality {
        case .good: return .available
        case .weak: return .weak
        case .lost: return .lost
        case .unavailable: return .waiting
        }
    }
    private func isFresh(_ fix: LocationFix) -> Bool {
        let elapsed = elapsedClock()
        return fix.elapsedRealtimeMs >= 0 && elapsed >= fix.elapsedRealtimeMs && elapsed - fix.elapsedRealtimeMs <= 5_000
    }
    private func requestBlockMessage() -> String? {
        if apiKey().trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return TypedProviderException(kind: .configuration).description }
        if elapsedClock() < rateLimitedUntilMs { return TypedProviderException(kind: .rateLimit).description }
        return nil
    }
    private func recordRateLimit(_ error: Error) {
        guard let error = error as? TypedProviderException, error.kind == .rateLimit else { return }
        let milliseconds = min(86_400, max(1, error.retryAfterSeconds ?? 60)) * 1000
        let expiry = elapsedClock().addingReportingOverflow(milliseconds)
        rateLimitedUntilMs = expiry.overflow ? .max : expiry.partialValue
    }
    private func safeMessage(_ error: Error) -> String {
        (error as? TypedProviderException)?.description ?? "Não foi possível concluir a consulta. Tente novamente."
    }
    private func locationNotice(_ message: String) -> String { locationLifecycleMessage = message; return message }
    private func cancelRouteRequest() {
        routeEpoch &+= 1
        routeTask?.cancel(); routeTask = nil; routeInFlight = nil
    }
    private func saveResume(_ destination: Destination?) -> String? {
        do { try places.setResumeDestination(destination); return nil }
        catch { return "Não foi possível salvar o destino para retomada." }
    }
    private func change(_ mutation: (inout NavigationSnapshot) -> Void) {
        var next = snapshot
        mutation(&next)
        publish(next)
    }
    private func publish(_ value: NavigationSnapshot) {
        var next = value
        next.resumeDestination = places.snapshot.resumeDestination
        guard next != snapshot else { return }
        snapshot = next
        onChange?(next)
    }
}
