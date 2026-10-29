import Foundation
import Combine
import NavSkinCore

@MainActor
final class TrafficStore: ObservableObject {
    @Published private(set) var enabled: Bool
    @Published private(set) var configured = false
    @Published private(set) var foreground = true
    @Published private(set) var message = "Trânsito desligado"
    @Published private(set) var incidents: [TrafficIncident] = []
    @Published private(set) var lastUpdated: Date?
    @Published private(set) var revision = UUID()
    private(set) var configurationRevision = UUID()
    @Published private(set) var flowUnavailable = false
    private let vault = APIKeyStore(service: "br.com.navskin.ios.tomtom")
    private let preferences = UserDefaults.standard
    private var key = ""
    private var center: GeoPoint?
    private var zoom: Double = 0
    private var fetchedCenter: GeoPoint?
    private var requestedCenter: GeoPoint?
    private var receivedAt: Int64?
    private var generation = 0
    private var refresh = TrafficRefreshPolicy()
    private var task: Task<Void, Never>?
    private let transport = URLSession(configuration: .ephemeral)

    var active: Bool { enabled && configured && foreground }
    var compactStatus: String {
        if !configured { return "Trânsito · configurar" }
        if flowUnavailable || message.contains("indisponível") || message.contains("recusada") || message.contains("Limite") { return "Trânsito · indisponível" }
        if task != nil && lastUpdated == nil { return "Trânsito · carregando" }
        return "Trânsito · TomTom"
    }

    init() {
        enabled = preferences.bool(forKey: "tomtom_traffic_enabled")
        key = (try? vault.read()) ?? ""
        configured = !key.isEmpty
        configureTransport()
    }

    func setEnabled(_ value: Bool) {
        guard enabled != value else { return }
        enabled = value
        preferences.set(value, forKey: "tomtom_traffic_enabled")
        invalidate()
        configureTransport()
        tick()
    }

    func saveKey(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count <= 512, !trimmed.contains("\r"), !trimmed.contains("\n") else {
            message = "Chave TomTom inválida. Copie somente a chave da API."
            return false
        }
        do { try vault.save(trimmed) }
        catch { message = "Não foi possível salvar a chave TomTom no Keychain."; return false }
        key = trimmed
        configured = !trimmed.isEmpty
        refresh = TrafficRefreshPolicy()
        invalidate()
        configureTransport()
        tick()
        return true
    }

    func setForeground(_ value: Bool) {
        guard foreground != value else { return }
        foreground = value
        invalidate()
        configureTransport()
        if value { tick() }
    }

    func viewport(center: GeoPoint, zoom: Double) {
        guard center.isValidMapPoint else { return }
        self.center = center
        self.zoom = zoom
        if let fetchedCenter, GeoMath.distanceMeters(center, fetchedCenter) > 3_000 {
            invalidateIncidents()
        } else if let requestedCenter, GeoMath.distanceMeters(center, requestedCenter) > 3_000 {
            invalidateIncidents()
        }
        tick()
    }

    func tick() {
        guard active else { return }
        let now = NavigationSession.elapsedMilliseconds()
        if let receivedAt, !TrafficRefreshPolicy.canDisplay(zoom: zoom, receivedAt: receivedAt, now: now) {
            incidents = []; revision = UUID()
            self.receivedAt = nil
            message = "Trânsito indisponível · dados anteriores expiraram."
        }
        guard zoom >= 12, let center else {
            if task != nil || !incidents.isEmpty || lastUpdated != nil { invalidateIncidents() }
            if message != "Aproxime o mapa para ver incidentes." { message = "Aproxime o mapa para ver incidentes." }
            return
        }
        guard task == nil, refresh.begin(enabled: enabled, configured: configured, foreground: foreground, zoom: zoom, now: now),
              let request = try? TomTomTraffic.request(apiKey: key, center: center) else { return }
        let expected = generation
        requestedCenter = center
        message = "Atualizando incidentes TomTom…"
        task = Task { [weak self] in
            guard let self else { return }
            do {
                let (data, response) = try await self.transport.data(for: request)
                guard !Task.isCancelled, self.generation == expected, self.active, self.zoom >= 12 else { return }
                guard let http = response as? HTTPURLResponse else { throw TomTomTraffic.Failure.response }
                guard http.statusCode == 200 else {
                    self.fail(http.statusCode == 401 || http.statusCode == 403 ? "Chave TomTom recusada. Confira a chave e a habilitação de Traffic." :
                        http.statusCode == 429 ? "Limite TomTom atingido. As consultas foram pausadas por alguns minutos." : "Trânsito indisponível. Tente novamente mais tarde.")
                    return
                }
                let decoded = try TomTomTraffic.decode(data)
                guard let latest = self.center, GeoMath.distanceMeters(latest, center) <= 3_000 else {
                    self.task = nil
                    self.requestedCenter = nil
                    self.message = "Aguardando atualização de incidentes nesta área."
                    return
                }
                self.incidents = decoded
                self.fetchedCenter = center
                self.lastUpdated = Date()
                self.receivedAt = NavigationSession.elapsedMilliseconds()
                self.revision = UUID()
                self.task = nil
                self.requestedCenter = nil
                self.message = decoded.isEmpty ? "Nenhum incidente informado nesta área. Isso não confirma trânsito livre." : "\(decoded.count) incidentes informados nesta área."
            } catch {
                guard !Task.isCancelled, self.generation == expected else { return }
                self.fail("Trânsito indisponível. Confira a conexão e a chave TomTom.")
            }
        }
    }

    private func fail(_ text: String) {
        task = nil
        requestedCenter = nil
        incidents = []
        revision = UUID()
        refresh.backOff(now: NavigationSession.elapsedMilliseconds())
        message = text
    }

    private func invalidate() {
        invalidateIncidents()
        configurationRevision = UUID()
    }

    private func invalidateIncidents() {
        generation += 1
        task?.cancel()
        task = nil
        incidents = []
        lastUpdated = nil
        fetchedCenter = nil
        requestedCenter = nil
        receivedAt = nil
        revision = UUID()
    }

    private func configureTransport() {
        flowUnavailable = false
        let expectedConfiguration = configurationRevision
        TrafficTileURLProtocol.configure(key: active ? key : "") { [weak self] success in
            DispatchQueue.main.async { [weak self] in
                guard let self, self.active, self.configurationRevision == expectedConfiguration,
                      self.flowUnavailable == success else { return }
                self.flowUnavailable = !success
            }
        }
        message = !enabled ? "Trânsito desligado" : !configured ? "Adicione sua chave TomTom para ativar o trânsito." :
            !foreground ? "Trânsito pausado em segundo plano." : "Fluxo TomTom ativado. Aguardando incidentes da área visível."
    }
}
