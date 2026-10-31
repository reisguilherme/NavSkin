import Foundation
import Combine
import NavSkinCore

/// SwiftUI, map and CoreLocation all observe this application-owned session.
@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var snapshot = NavigationSnapshot()
    @Published private(set) var theme: MapTheme
    @Published private(set) var baseMapMode: MapBaseMode
    @Published private(set) var enabledCategories: Set<String>
    @Published private(set) var navigationPreferences: NavigationPreferences
    @Published private(set) var places = LocalPlacesState()
    @Published private(set) var following = true
    @Published private(set) var vibing = false
    @Published private(set) var vibingExitVisible = false
    @Published private(set) var mapStatus: String? = "Carregando mapa…"
    @Published private(set) var resourceError: String?
    @Published private(set) var uiError: String?
    @Published private(set) var mapCommand: MapCommand?
    @Published var mapSelection: Destination?
    let resources: ResourceCatalog
    let mapRepository: MapResourceRepository
    let traffic = TrafficStore()
    private let preferences: UserDefaults
    private let keyStore: APIKeyStore
    private let storage: PlacesStore
    private let session: NavigationSession
    private let location = IOSLocationSource()
    private var timer: Timer?
    private var trafficChanges: AnyCancellable?
    private var previousAccess: LocationAccess = .notRequested
    private var vibingExitTask: Task<Void, Never>?

    init() {
        let defaults = UserDefaults.standard
        preferences = defaults
        theme = MapTheme(rawValue: defaults.string(forKey: "map_theme") ?? "gta") ?? .gta
        baseMapMode = MapBaseMode(rawValue: defaults.string(forKey: "base_map_mode") ?? "world") ?? .world
        let initialCategories = PoiCategory.migratedSelection(defaults.stringArray(forKey: "poi_categories"),
            schemaVersion: defaults.integer(forKey: "poi_categories_schema"))
        enabledCategories = initialCategories
        defaults.set(initialCategories.sorted(), forKey: "poi_categories")
        defaults.set(2, forKey: "poi_categories_schema")
        navigationPreferences = defaults.data(forKey: "navigation_preferences")
            .flatMap { try? JSONDecoder().decode(NavigationPreferences.self, from: $0) } ?? NavigationPreferences()
        let assets = (Bundle.main.resourceURL ?? Bundle.main.bundleURL).appendingPathComponent("Resources", isDirectory: true)
        resources = ResourceCatalog(root: assets)
        mapRepository = MapResourceRepository(root: assets)
        resourceError = resources.loadError
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            // Keep the existing local store path so a product rename preserves saved places.
            .appendingPathComponent("GameMap", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            var excluded = directory
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try excluded.setResourceValues(values)
            try FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
        } catch { /* PlacesStore exposes a write failure without discarding an existing source. */ }
        let saved = PlacesStore(fileURL: directory.appendingPathComponent("navigation-places.json"))
        storage = saved
        let vault = APIKeyStore()
        keyStore = vault
        #if DEBUG
        let allowSystemSimulation = true
        #else
        let allowSystemSimulation = false
        #endif
        session = NavigationSession(places: saved, apiKey: { (try? vault.read()) ?? "" },
            allowSimulatedLocations: allowSystemSimulation)
        snapshot = session.snapshot
        places = saved.snapshot
        session.onChange = { [weak self] state in
            guard let self else { return }
            self.snapshot = state
            self.places = self.storage.snapshot
            self.location.setNavigationActive(state.navigationActive && !state.simulation)
        }
        session.startLocation = { [weak self] in self?.location.start() }
        session.stopLocation = { [weak self] in self?.location.stop() }
        location.onFix = { [weak self] fix in self?.session.acceptFix(fix) }
        location.onStatus = { [weak self] access, running, message in
            guard let self else { return }
            self.session.setLocationStatus(access: access, running: running, message: message)
            if access != self.previousAccess, (access == .denied || access == .disabled), let message {
                if self.vibing { self.exitVibing() }
                self.uiError = message
            }
            self.previousAccess = access
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.session.tick(); self?.traffic.tick() }
        }
        trafficChanges = traffic.objectWillChange.sink { [weak self] _ in self?.objectWillChange.send() }
    }

    func activateGPS() { session.activateGPS() }
    func search(_ query: String) { session.search(query) }
    func selectDestination(_ destination: Destination) { mapSelection = nil; session.selectDestination(destination) }
    func addStop(_ destination: Destination) {
        mapSelection = nil
        session.addStop(destination)
        if session.snapshot.pendingIntermediateStops == nil && !session.snapshot.updatingStops {
            uiError = session.snapshot.message ?? "Não foi possível adicionar esta parada."
        }
    }
    func removeStop(id: String) { session.removeStop(id: id) }
    func startNavigation() { session.startNavigation() }
    func enterVibing() {
        guard !vibing else { return }
        guard !snapshot.simulation else {
            uiError = "Encerre a simulação para usar o Vibing com GPS real."
            return
        }
        guard snapshot.destination == nil || snapshot.navigationActive || [.preview, .arrived].contains(snapshot.phase) else {
            uiError = "Conclua o cálculo da rota ou limpe o destino para sair dirigindo sem rota."
            return
        }
        session.activateGPS()
        guard snapshot.locationAccess != .denied, snapshot.locationAccess != .disabled else {
            uiError = snapshot.message ?? "Ative a localização precisa nos Ajustes do iPhone para usar o Vibing."
            return
        }
        if snapshot.phase == .preview {
            session.startNavigation()
            guard snapshot.navigationActive else {
                uiError = snapshot.message ?? "Aguarde uma posição GPS precisa para iniciar esta rota."
                return
            }
        }
        mapSelection = nil
        vibing = true
        mapCommand = MapCommand(kind: .recenter)
        revealVibingExit()
    }
    func exitVibing() {
        guard vibing else { return }
        vibingExitTask?.cancel(); vibingExitTask = nil
        vibing = false
        vibingExitVisible = false
        mapCommand = MapCommand(kind: .recenter)
    }
    func revealVibingExit() {
        guard vibing else { return }
        vibingExitTask?.cancel()
        vibingExitVisible = true
        // A map tap also recovers GPS follow after the user explored by gesture.
        mapCommand = MapCommand(kind: .recenter)
        vibingExitTask = Task { [weak self] in
            do { try await Task.sleep(for: .seconds(4)) } catch { return }
            self?.vibingExitVisible = false
        }
    }
    func clearDestination() {
        guard !snapshot.navigationActive, !snapshot.simulation else { return }
        mapSelection = nil
        session.clearDestination()
    }
    func stopNavigation() { session.stopNavigation() }
    func retryRoute() { session.retryRoute() }
    func resumeDestination() { session.resumeDestination() }
    func setForeground(_ foreground: Bool) {
        session.setForeground(foreground)
        traffic.setForeground(foreground)
        // Refresh freshness immediately after suspension before presenting old guidance again.
        if foreground {
            session.tick()
            if vibing { session.activateGPS() }
        }
    }
    func recenter() { session.activateGPS(); mapCommand = MapCommand(kind: .recenter) }
    func showOverview() { mapCommand = MapCommand(kind: .overview) }
    func showOfflineCoverage() { mapCommand = MapCommand(kind: .offlineCoverage) }
    func zoomBy(_ delta: Double) { mapCommand = MapCommand(kind: .zoom(delta)) }
    func reportMapStatus(_ message: String?) { if mapStatus != message { mapStatus = message } }
    func reportFollowing(_ value: Bool) { if following != value { following = value } }
    func selectMapPoint(_ point: GeoPoint) {
        mapSelection = Destination(id: UUID().uuidString, name: "Ponto no mapa", point: point,
            address: String(format: "%.6f, %.6f", point.latitude, point.longitude))
    }
    func setTheme(_ value: MapTheme) { theme = value; preferences.set(value.rawValue, forKey: "map_theme") }
    func setBaseMapMode(_ value: MapBaseMode) {
        guard baseMapMode != value else { return }
        baseMapMode = value
        preferences.set(value.rawValue, forKey: "base_map_mode")
    }
    func setCategory(_ id: String, enabled: Bool) {
        guard PoiCategory(rawValue: id) != nil else { return }
        if enabled { enabledCategories.insert(id) } else { enabledCategories.remove(id) }
        preferences.set(enabledCategories.sorted(), forKey: "poi_categories")
    }
    var vehicleID: String { navigationPreferences.vehicleID(theme: theme.rawValue) }
    func setVehicle(_ id: String) {
        navigationPreferences.vehicleId = id
        navigationPreferences.vehicleSelectionExplicit = true
        savePreferences()
    }
    func setSavedVisible(_ visible: Bool) { navigationPreferences.savedPlacesVisible = visible; savePreferences() }
    var map3D: Bool { vibing || navigationPreferences.map3D(navigationActive: snapshot.navigationActive) }
    var mapOrientation: MapOrientation { vibing ? .movementUp : navigationPreferences.orientation }
    func setMap3D(_ enabled: Bool) {
        navigationPreferences.setMap3D(enabled, navigationActive: snapshot.navigationActive)
        savePreferences()
    }
    func toggleOrientation() {
        navigationPreferences.orientation = navigationPreferences.orientation == .northUp ? .movementUp : .northUp
        savePreferences()
    }
    private func savePreferences() {
        do { preferences.set(try JSONEncoder().encode(navigationPreferences), forKey: "navigation_preferences") }
        catch { uiError = "Não foi possível salvar as preferências." }
    }
    func setAPIKey(_ key: String) -> Bool {
        do {
            try keyStore.save(key)
            session.keyChanged()
            if snapshot.destination != nil { session.retryRoute() }
            return true
        } catch { uiError = error.localizedDescription; return false }
    }
    func removeAPIKey() { _ = setAPIKey("") }
    func clearUIError() { uiError = nil }
    @discardableResult
    func savePlace(name: String, point: GeoPoint, address: String, categoryId: String?, iconId: String, id: String? = nil) -> Bool {
        mutatePlaces { try storage.savePlace(name: name, point: point, address: address, categoryId: categoryId, iconId: iconId, id: id) }
    }
    func deletePlace(_ id: String) { _ = mutatePlaces { try storage.deletePlace(id: id) } }
    func setHome(_ id: String?) { _ = mutatePlaces { try storage.setHome(id: id) } }
    func setWork(_ id: String?) { _ = mutatePlaces { try storage.setWork(id: id) } }
    private func mutatePlaces<T>(_ change: () throws -> T) -> Bool {
        do { _ = try change(); places = storage.snapshot; return true }
        catch { uiError = error.localizedDescription; return false }
    }
    func startSimulation() {
        #if DEBUG
        session.startReplay(route: DevelopmentReplay.route, fixes: DevelopmentReplay.fixes())
        #endif
    }
    func stopSimulation() { session.stopReplay() }
}
