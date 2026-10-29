import SwiftUI
import UIKit
import NavSkinCore

@MainActor
struct APIKeySheet: View {
    @ObservedObject var store: AppStore
    @State private var key = ""
    @State private var saved = false
    @State private var removing = false
    @State private var trafficKey = ""
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Label(store.snapshot.providerConfigured ? "Chave salva neste iPhone" : "Configure sua chave", systemImage: "key.fill")
                        .gameFont(.headline)
                    Text("Sua chave é guardada no Keychain e enviada diretamente à Geoapify nas consultas. Uma chave salva ainda pode ser recusada pelo serviço.")
                        .gameFont(.footnote).foregroundStyle(.secondary)
                    SecureField(store.snapshot.providerConfigured ? "Nova chave para substituir" : "Chave da API Geoapify", text: $key)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().privacySensitive()
                        .focused($focused).submitLabel(.done).onSubmit(saveKey)
                    Button(store.snapshot.providerConfigured ? "Substituir chave" : "Salvar chave", action: saveKey)
                        .disabled(key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if saved { Label("Chave salva. Tente calcular a rota novamente.", systemImage: "checkmark.circle.fill").gameFont(.footnote) }
                    if store.snapshot.providerConfigured {
                        Button("Remover chave", role: .destructive) { removing = true }
                    }
                    Link("Gerenciar chave na Geoapify", destination: URL(string: "https://myprojects.geoapify.com/")!)
                } header: { Text("Busca e rotas online") }
                Section("Trânsito opcional · TomTom") {
                    Toggle("Mostrar fluxo e incidentes", isOn: Binding(
                        get: { store.traffic.enabled }, set: { store.traffic.setEnabled($0) }))
                    Text("A chave TomTom fica no Keychain deste iPhone e é enviada somente à TomTom. Sem chave ou com a camada desligada, não há consultas. O uso consome a cota da sua conta.")
                        .gameFont(.footnote).foregroundStyle(.secondary)
                    SecureField(store.traffic.configured ? "Nova chave TomTom para substituir" : "Chave da API TomTom", text: $trafficKey)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().privacySensitive()
                    Button(store.traffic.configured ? "Substituir chave TomTom" : "Salvar chave TomTom") {
                        if store.traffic.saveKey(trafficKey) { trafficKey = "" }
                    }
                    .disabled(trafficKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if store.traffic.configured {
                        Button("Remover chave TomTom", role: .destructive) { _ = store.traffic.saveKey(""); trafficKey = "" }
                    }
                    Text(store.traffic.message).gameFont(.footnote)
                    if store.traffic.flowUnavailable {
                        Text("Fluxo no mapa indisponível. Confira sua conexão, chave e cota TomTom.").gameFont(.footnote).foregroundStyle(.secondary)
                    }
                    if let updated = store.traffic.lastUpdated {
                        HStack { Text("Incidentes atualizados"); Text(updated, style: .relative) }.gameFont(.caption)
                    }
                    Text("Incidentes são consultados na área visível, no máximo a cada 2 minutos. A estimativa da rota Geoapify continua sem trânsito ao vivo. Radares em operação e fases dos semáforos não estão disponíveis.")
                        .gameFont(.footnote).foregroundStyle(.secondary)
                    if !store.traffic.incidents.isEmpty {
                        ForEach(store.traffic.incidents.prefix(8)) { incident in
                            Label(incident.description, systemImage: "exclamationmark.triangle")
                                .gameFont(.caption).lineLimit(2)
                        }
                    }
                    Link("Gerenciar chave e cota TomTom", destination: URL(string: "https://my.tomtom.com/")!)
                    Text("© TomTom · © OpenStreetMap contributors").gameFont(.caption2).foregroundStyle(.secondary)
                }
                Section("Localização") {
                    Label(locationDescription, systemImage: "location.circle")
                    Button("Ativar GPS") { store.activateGPS() }
                    if store.snapshot.locationAccess == .denied {
                        Button("Abrir Ajustes do iPhone") {
                            if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                        }
                    }
                    Text("O GPS é solicitado pela sua ação. Durante uma navegação ativa, o aplicativo pode manter a localização para acompanhar a viagem com a tela bloqueada.")
                        .gameFont(.footnote).foregroundStyle(.secondary)
                }
                #if DEBUG
                Section("Ferramentas de desenvolvimento") {
                    Button(store.snapshot.simulation ? "Encerrar SIMULAÇÃO" : "Iniciar replay · SIMULAÇÃO") {
                        if store.snapshot.simulation { store.stopSimulation() }
                        else { store.startSimulation(); dismiss() }
                    }
                    Text("Replay sintético identificado como SIMULAÇÃO. Não representa uma posição GPS real.").gameFont(.footnote).foregroundStyle(.secondary)
                }
                #endif
                Section {
                    Text("NavSkin oferece mapa mundial online e um recorte de Goiânia offline. Busca e rotas são online; a estimativa de chegada não inclui trânsito ao vivo.")
                        .gameFont(.footnote).foregroundStyle(.secondary)
                    if let error = store.uiError { Text(error).gameFont(.footnote).foregroundStyle(.red) }
                }
            }
            .tint(palette.accent)
            .gameNavigationTitle("Configurações").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Concluir") { key = ""; trafficKey = ""; dismiss() }.gameFont(.body) } }
            .confirmationDialog("Remover a chave salva?", isPresented: $removing, titleVisibility: .visible) {
                Button("Remover chave", role: .destructive) { store.removeAPIKey(); saved = false; key = "" }
                Button("Cancelar", role: .cancel) {}
            }
            .onDisappear { key = ""; trafficKey = "" }
        }
    }

    private func saveKey() {
        focused = false
        if store.setAPIKey(key) { key = ""; saved = true }
    }

    private var locationDescription: String {
        switch store.snapshot.locationAccess {
        case .notRequested: return "Permissão ainda não solicitada"
        case .denied: return "Acesso à localização negado"
        case .disabled: return "Localização desativada no aparelho"
        case .waiting: return "Aguardando sinal GPS"
        case .available: return "GPS disponível"
        case .weak: return "Sinal GPS impreciso"
        case .lost: return "Sinal GPS perdido"
        case .needsPhoneStart: return "Ative a localização neste iPhone"
        }
    }
}
