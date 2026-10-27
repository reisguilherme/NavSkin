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
    @Published private(set) var mapStatus: String? = "Carregando mapa…"
    @Published private(set) var resourceError: String?
    @Published private(set) var uiError: String?
    @Published private(set) var mapCommand: MapCommand?
    @Published var mapSelection: Destination?
    let resources: ResourceCatalog
    let mapRepository: MapResourceRepository
    private let preferences: UserDefaults
    private let keyStore: APIKeyStore
    private let storage: PlacesStore
    private let session: NavigationSession
    private let location = IOSLocationSource()
    private var timer: Timer?
    private var previousAccess: LocationAccess = .notRequested

    init() {
        let defaults = UserDefaults.standard
        preferences = defaults
        theme = MapTheme(rawValue: defaults.string(forKey: "map_theme") ?? "gta") ?? .gta
        baseMapMode = MapBaseMode(rawValue: defaults.string(forKey: "base_map_mode") ?? "world") ?? .world
        enabledCategories = Set(defaults.stringArray(forKey: "poi_categories") ?? PoiCategory.allCases.map(\.rawValue))
            .intersection(Set(PoiCategory.allCases.map(\.rawValue)))
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
                self.uiError = message
            }
            self.previousAccess = access
        }
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.session.tick() }
        }
    }

    func activateGPS() { session.activateGPS() }
    func search(_ query: String) { session.search(query) }
    func selectDestination(_ destination: Destination) { mapSelection = nil; session.selectDestination(destination) }
    func startNavigation() { session.startNavigation() }
    func stopNavigation() { session.stopNavigation() }
    func retryRoute() { session.retryRoute() }
    func resumeDestination() { session.resumeDestination() }
    func setForeground(_ foreground: Bool) {
        session.setForeground(foreground)
        // Refresh freshness immediately after suspension before presenting old guidance again.
        if foreground { session.tick() }
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
    func setVehicle(_ id: String) { navigationPreferences.vehicleId = id; savePreferences() }
    func setSavedVisible(_ visible: Bool) { navigationPreferences.savedPlacesVisible = visible; savePreferences() }
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
