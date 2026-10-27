import SwiftUI
import NavSkinCore

private enum MapSheet: String, Identifiable {
    case search, saved, appearance, settings, point
    var id: String { rawValue }
}

@MainActor
struct NavSkinRootView: View {
    @ObservedObject var store: AppStore
    @State private var sheet: MapSheet?
    @State private var carouselVisible = true
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    init(store: AppStore) { self.store = store }

    private var palette: ThemePalette { store.theme.palette }
    private var compact: Bool { verticalSizeClass == .compact }
    private var activeTrip: Bool {
        store.snapshot.navigationActive || store.snapshot.phase == .arrived ||
            store.snapshot.phase == .calculating || store.snapshot.phase == .preview
    }
    private var showsRoutePanel: Bool {
        activeTrip || store.snapshot.phase == .error || store.snapshot.phase == .resumeAvailable || store.snapshot.resumeDestination != nil
    }
    private var showsGPSStatus: Bool {
        store.snapshot.serviceRunning || [.denied, .disabled, .weak, .lost].contains(store.snapshot.locationAccess)
    }
    private var gpsText: String {
        switch store.snapshot.locationAccess {
        case .denied: return "GPS · permissão negada"
        case .disabled: return "GPS desativado no aparelho"
        case .weak: return "GPS impreciso · velocidade indisponível"
        case .lost: return "Sinal GPS perdido"
        default:
            let location = store.snapshot.location
            if location.quality == .good, location.ageMs <= 5_000, let speed = location.speedKmh, speed.isFinite, speed >= 0 {
                return "GPS ativo · \(Int(min(999, speed.rounded()))) km/h"
            }
            return location.quality == .good ? "GPS ativo" : "Aguardando GPS preciso"
        }
    }

    var body: some View {
        ZStack {
            NavSkinView(store: store).ignoresSafeArea()
            VStack(spacing: 10) {
                topBar
                if store.snapshot.simulation || store.snapshot.location.fix?.isMock == true { simulationBanner }
                if showsGPSStatus && !compact {
                    HStack { gpsStatus; Spacer() }.padding(.horizontal, 16)
                }
                if carouselVisible && !activeTrip && store.snapshot.resumeDestination == nil && !compact {
                    ThemeCarousel(store: store) { withAnimation { carouselVisible = false } }
                }
                if store.snapshot.navigationActive && !compact {
                    GuidanceCard(store: store)
                        .frame(maxWidth: compact ? 460 : .infinity)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                }
                Spacer(minLength: 4)
                HStack(alignment: .bottom, spacing: 12) {
                    if compact && showsRoutePanel {
                        ScrollView {
                            VStack(spacing: 8) {
                                if store.snapshot.navigationActive { GuidanceCard(store: store) }
                                if showsRoutePanel { RoutePanel(store: store, openSettings: { sheet = .settings }) }
                            }
                        }
                        .containerRelativeFrame(.horizontal) { length, _ in min(380, length * 0.44) }
                        .frame(maxHeight: 220)
                        .scrollIndicators(.hidden)
                    } else if !compact && store.baseMapMode == .goianiaOffline { coverageBadge }
                    Spacer(minLength: 12)
                    mapControls
                }.padding(.horizontal, 16)
                if let resourceError = store.resourceError {
                    MessageCard(text: resourceError, symbol: "exclamationmark.triangle", palette: palette).padding(.horizontal, 16)
                } else if let status = store.mapStatus {
                    MessageCard(text: status, palette: palette).padding(.horizontal, 16)
                }
                if showsRoutePanel && !compact {
                    RoutePanel(store: store, openSettings: { sheet = .settings })
                        .padding(.horizontal, 16)
                } else if !showsRoutePanel {
                    exploreBar
                }
                credits
            }
            .padding(.top, 8)
            .padding(.bottom, 4)
        }
        .tint(palette.accent)
        .sheet(item: $sheet, onDismiss: { store.mapSelection = nil }) { item in
            sheetContent(item)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .onChange(of: store.mapSelection) { _, destination in
            if destination != nil { sheet = .point }
        }
        .onChange(of: store.snapshot.destination) { _, destination in
            if destination != nil { carouselVisible = false }
        }
        .alert("Não foi possível concluir", isPresented: Binding(
            get: { store.uiError != nil }, set: { if !$0 { store.clearUIError() } }
        )) {
            Button("OK", role: .cancel) { store.clearUIError() }
        } message: { Text(store.uiError ?? "") }
    }

    private var topBar: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 1) {
                Text("NavSkin").font(.system(size: compact ? 22 : 27, weight: .black, design: .rounded))
                if !compact { Text(store.theme.title).font(.caption.weight(.semibold)).foregroundStyle(palette.muted) }
                else if showsGPSStatus { Text(gpsText).font(.caption2).foregroundStyle(palette.muted).lineLimit(1) }
            }
            .foregroundStyle(palette.text).padding(.horizontal, 14).padding(.vertical, 8)
            .background(palette.surface.opacity(0.96), in: RoundedRectangle(cornerRadius: 18))
            Spacer(minLength: 8)
            MapControl(symbol: "magnifyingglass", label: "Buscar destino", palette: palette) { sheet = .search }
            MapControl(symbol: "bookmark", label: "Locais salvos", palette: palette) { sheet = .saved }
            MapControl(symbol: "slider.horizontal.3", label: "Temas e mapa", palette: palette) { sheet = .appearance }
        }.padding(.horizontal, 16)
    }

    private var simulationBanner: some View {
        HStack {
            Label(store.snapshot.simulation ? "SIMULAÇÃO · replay de teste" : "SIMULAÇÃO · posição simulada pelo sistema", systemImage: "testtube.2").font(.caption.bold())
            Spacer()
            if store.snapshot.simulation { Button("Encerrar") { store.stopSimulation() }.font(.caption.bold()) }
        }
        .padding(10).foregroundStyle(Color.black).background(Color.yellow, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16).accessibilityElement(children: .combine)
    }

    private var gpsStatus: some View {
        Button { sheet = .settings } label: {
            Label(gpsText, systemImage: store.snapshot.location.quality == .good ? "location.fill" : "location.slash")
                .font(.caption.weight(.semibold)).foregroundStyle(palette.text)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(palette.surface.opacity(0.96), in: Capsule())
        }.buttonStyle(.plain).accessibilityHint("Abrir configurações de localização")
    }

    private var mapControls: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                MapControl(symbol: "minus", label: "Diminuir zoom", palette: palette) { store.zoomBy(-1) }
                MapControl(symbol: "plus", label: "Aumentar zoom", palette: palette) { store.zoomBy(1) }
            }
            HStack(spacing: 8) {
                MapControl(symbol: store.navigationPreferences.orientation == .northUp ? "location.north.line" : "location.north.fill",
                           label: "Alternar orientação do mapa", palette: palette) { store.toggleOrientation() }
                MapControl(symbol: store.following ? "location.fill" : "location", label: "Centralizar no GPS",
                           palette: palette, selected: store.following) { store.recenter() }
            }
        }
    }

    private var coverageBadge: some View {
        Button { store.showOfflineCoverage() } label: {
            VStack(alignment: .leading, spacing: 5) {
                Label("Goiânia · recorte local", systemImage: "map").font(.caption.weight(.semibold))
                Text("Busca e rotas usam internet").font(.caption2)
                Label("Ver região offline", systemImage: "viewfinder").font(.caption.weight(.semibold))
            }
            .foregroundStyle(palette.text).padding(10)
            .background(palette.surface.opacity(0.95), in: RoundedRectangle(cornerRadius: 13))
        }
        .buttonStyle(.plain)
        .accessibilityHint("Enquadrar Goiânia sem encerrar a navegação")
    }

    private var exploreBar: some View {
        HStack(spacing: 12) {
            Button { sheet = .search } label: {
                Label("Para onde vamos?", systemImage: "magnifyingglass")
                    .font(.headline).frame(maxWidth: .infinity, minHeight: 48)
            }
            .foregroundStyle(palette.surface).background(palette.accent, in: Capsule())
            if store.snapshot.locationAccess != .available {
                Button { store.activateGPS() } label: {
                    Image(systemName: "location.circle.fill").font(.title).frame(width: 48, height: 48)
                }
                .accessibilityLabel("Ativar localização")
            }
        }
        .padding(10).background(palette.surface, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(palette.border, lineWidth: 1))
        .padding(.horizontal, 16)
    }

    private var credits: some View {
        VStack(spacing: 2) {
            if store.baseMapMode == .world {
                HStack(spacing: 4) {
                    Link("OpenFreeMap", destination: URL(string: "https://openfreemap.org/")!)
                    Text("·")
                    Link("© OpenMapTiles", destination: URL(string: "https://openmaptiles.org/")!)
                    Text("·")
                    Link("© OpenStreetMap", destination: URL(string: "https://www.openstreetmap.org/copyright")!)
                }
            } else {
                HStack(spacing: 4) {
                    Link("© OpenStreetMap", destination: URL(string: "https://www.openstreetmap.org/copyright")!)
                    Text("·")
                    Link("Overture Maps", destination: URL(string: "https://overturemaps.org/attribution/")!)
                    if compact { Text("· Goiânia offline") }
                }
            }
            Link("Busca e rotas: Geoapify", destination: URL(string: "https://www.geoapify.com/")!)
        }
        .font(.system(size: 10)).foregroundStyle(palette.text)
        .padding(.horizontal, 8).padding(.vertical, 3)
        .background(palette.surface.opacity(0.95), in: Capsule())
    }

    @ViewBuilder
    private func sheetContent(_ item: MapSheet) -> some View {
        switch item {
        case .search:
            DestinationSearchSheet(store: store) { destination in
                store.selectDestination(destination)
                sheet = nil
            }
        case .saved:
            SavedPlacesSheet(store: store) { destination in
                store.selectDestination(destination)
                sheet = nil
            }
        case .appearance: AppearanceSheet(store: store)
        case .settings: APIKeySheet(store: store)
        case .point:
            if let destination = store.mapSelection {
                SelectedDestinationSheet(store: store, destination: destination) {
                    store.selectDestination(destination)
                    sheet = nil
                }
            }
        }
    }
}
