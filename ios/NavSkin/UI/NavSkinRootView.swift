import SwiftUI
import NavSkinCore

private enum MapSheet: String, Identifiable {
    case search, saved, themes, filters, options, settings, route, point
    var id: String { rawValue }
}

@MainActor
struct NavSkinRootView: View {
    @ObservedObject var store: AppStore
    @State private var sheet: MapSheet?
    @State private var menuExpanded = false
    @Environment(\.verticalSizeClass) private var verticalSizeClass

    init(store: AppStore) { self.store = store }

    private var palette: ThemePalette { store.theme.palette }
    private var compact: Bool { verticalSizeClass == .compact }
    private var showsRoutePanel: Bool {
        store.snapshot.navigationActive || [.arrived, .waitingForLocation, .calculating, .preview, .error, .resumeAvailable].contains(store.snapshot.phase) ||
            store.snapshot.resumeDestination != nil
    }
    private var speedText: String {
        let location = store.snapshot.location
        guard location.quality == .good, location.ageMs <= 5_000,
              let speed = location.speedKmh, speed.isFinite, speed >= 0 else { return "—" }
        return "\(Int(min(999, speed.rounded())))"
    }
    private var gpsText: String {
        switch store.snapshot.locationAccess {
        case .denied: return "GPS · permissão negada"
        case .disabled: return "GPS desativado no aparelho"
        case .weak: return "GPS impreciso · velocidade indisponível"
        case .lost: return "Sinal GPS perdido"
        default:
            return store.snapshot.location.quality == .good ? "GPS ativo" : "Aguardando GPS preciso"
        }
    }

    var body: some View {
        ZStack {
            NavSkinView(store: store).ignoresSafeArea()
            GeometryReader { geometry in
                ZStack(alignment: .topTrailing) {
                    if menuExpanded {
                        Color.black.opacity(0.08).ignoresSafeArea()
                            .onTapGesture { withAnimation { menuExpanded = false } }
                            .accessibilityHidden(true)
                    }
                    VStack(spacing: 8) {
                        topBar
                        if store.snapshot.simulation || store.snapshot.location.fix?.isMock == true { simulationBanner }
                        if store.snapshot.navigationActive && !menuExpanded {
                            ScrollView {
                                GuidanceCard(store: store)
                            }
                            .scrollIndicators(.hidden)
                            .frame(maxWidth: compact ? 420 : .infinity)
                            .frame(maxHeight: compact ? 112 : 168)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.top, 8)
                    if menuExpanded {
                        mapMenu
                            .frame(width: min(320, max(0, geometry.size.width - 32)))
                            .frame(maxHeight: max(80, geometry.size.height - 72))
                            .padding(.top, 60).padding(.trailing, 16)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }
            .safeAreaInset(edge: .bottom, spacing: 0) { bottomControls }
        }
        .tint(palette.accent)
        .environment(\.gameTheme, store.theme)
        .font(GameTypography.font(.body, theme: store.theme))
        .sheet(item: $sheet, onDismiss: { store.mapSelection = nil }) { item in
            sheetContent(item)
                .environment(\.gameTheme, store.theme)
                .font(GameTypography.font(.body, theme: store.theme))
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(28)
        }
        .onChange(of: store.mapSelection) { _, destination in
            if destination != nil { present(.point) }
        }
        .onChange(of: showsRoutePanel) { _, visible in
            if !visible && sheet == .route { sheet = nil }
        }
        .alert("Não foi possível concluir", isPresented: Binding(
            get: { store.uiError != nil }, set: { if !$0 { store.clearUIError() } }
        )) {
            Button("OK", role: .cancel) { store.clearUIError() }
        } message: { Text(store.uiError ?? "") }
    }

    private var topBar: some View {
        HStack(spacing: 6) {
            Text("NavSkin").gameFont(size: 20, weight: .black).lineLimit(1)
                .foregroundStyle(palette.text)
                .padding(.horizontal, 10).frame(minHeight: 44)
                .background(palette.surface.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
            Spacer(minLength: 0)
            topAction("Temas", sheet: .themes)
            topAction("Filtros", sheet: .filters)
            MapControl(symbol: menuExpanded ? "xmark" : "line.3.horizontal",
                       label: menuExpanded ? "Fechar menu" : "Abrir menu", palette: palette) {
                withAnimation(.easeInOut(duration: 0.18)) { menuExpanded.toggle() }
            }
            .accessibilityValue(menuExpanded ? "Expandido" : "Recolhido")
        }
        .padding(.horizontal, 16)
    }

    private func topAction(_ title: String, sheet item: MapSheet) -> some View {
        Button { present(item) } label: {
            Text(title).gameFont(.caption, weight: .semibold).lineLimit(1)
                .foregroundStyle(palette.text).padding(.horizontal, 10).frame(minHeight: 44)
                .background(palette.surface.opacity(0.96), in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(palette.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title == "Temas" ? "Escolher tema" : "Filtrar lugares no mapa")
    }

    private var bottomControls: some View {
        VStack(spacing: 8) {
            if let resourceError = store.resourceError {
                MessageCard(text: resourceError, symbol: "exclamationmark.triangle", palette: palette)
            }
            if showsRoutePanel {
                CompactRouteSummary(store: store, openDetails: { present(.route) })
            }
            HStack(alignment: .bottom) {
                speedBadge
                Spacer(minLength: 16)
                Button { store.recenter() } label: {
                    Image(systemName: "location.fill").font(.system(size: 21, weight: .semibold))
                        .foregroundStyle(store.following ? palette.surface : palette.text)
                        .frame(width: 56, height: 56)
                        .background(store.following ? palette.accent : palette.surface, in: Circle())
                        .overlay(Circle().stroke(palette.border, lineWidth: 1))
                        .shadow(color: .black.opacity(0.14), radius: 8, y: 3)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Centralizar no GPS")
                .accessibilityHint("Voltar a acompanhar sua posição")
                .accessibilityAddTraits(store.following ? .isSelected : [])
            }
            Button { present(.search) } label: {
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass").font(.system(size: 20, weight: .semibold))
                    Text("Para onde?").gameFont(.headline)
                }
                .foregroundStyle(palette.text).padding(.horizontal, 18).frame(maxWidth: .infinity, minHeight: 54)
                .background(palette.surface, in: Capsule())
                .overlay(Capsule().stroke(palette.border, lineWidth: 1))
                .shadow(color: .black.opacity(0.14), radius: 10, y: 3)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Buscar um destino ou trocar sua rota")
        }
        .padding(.horizontal, 16).padding(.bottom, 8)
    }

    private var speedBadge: some View {
        VStack(spacing: 0) {
            Text(speedText).gameFont(size: 22, weight: .bold).monospacedDigit()
            Text("km/h").gameFont(size: 10)
        }
        .foregroundStyle(palette.text).frame(width: 60, height: 60)
        .background(palette.surface, in: Circle())
        .overlay(Circle().stroke(palette.border, lineWidth: 1))
        .shadow(color: .black.opacity(0.14), radius: 8, y: 3)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(speedText == "—" ? "Velocidade indisponível" : "Velocidade: \(speedText) quilômetros por hora")
    }

    private var simulationBanner: some View {
        HStack {
            Label(store.snapshot.simulation ? "SIMULAÇÃO · replay de teste" : "SIMULAÇÃO · posição simulada pelo sistema", systemImage: "testtube.2").gameFont(.caption, weight: .bold)
            Spacer()
            if store.snapshot.simulation {
                Button("Encerrar") { store.stopSimulation() }.gameFont(.caption, weight: .bold).frame(minHeight: 44)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .foregroundStyle(Color.black).background(Color.yellow, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16).accessibilityElement(children: .contain)
    }

    private var mapMenu: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Menu").gameFont(.headline).accessibilityAddTraits(.isHeader)
                menuAction("Seus lugares", symbol: "bookmark") { present(.saved) }
                menuAction("Opções do mapa", symbol: "slider.horizontal.3") { present(.options) }
                Divider()
                Text(gpsText).gameFont(.caption).foregroundStyle(palette.muted)
                menuAction("Ativar GPS", symbol: "location.circle") { store.activateGPS() }
                menuAction("Configurações e chave Geoapify", symbol: "gearshape") { present(.settings) }
                menuAction(store.navigationPreferences.orientation == .northUp ? "Orientação: norte para cima" : "Orientação: direção do movimento",
                           symbol: "location.north.line") { store.toggleOrientation() }
                HStack(spacing: 8) {
                    menuAction("Menos zoom", symbol: "minus.magnifyingglass") { store.zoomBy(-1) }
                    menuAction("Mais zoom", symbol: "plus.magnifyingglass") { store.zoomBy(1) }
                }
                if store.baseMapMode == .goianiaOffline {
                    menuAction("Ver região offline · Goiânia", symbol: "viewfinder") {
                        store.showOfflineCoverage()
                        withAnimation { menuExpanded = false }
                    }
                    Text("Busca e rotas usam internet.").gameFont(.caption).foregroundStyle(palette.muted)
                }
                if showsRoutePanel { menuAction("Detalhes da rota", symbol: "point.topleft.down.curvedto.point.bottomright.up") { present(.route) } }
                if let status = store.mapStatus { Text(status).gameFont(.caption).foregroundStyle(palette.muted) }
                Divider()
                credits
            }
            .padding(14)
        }
        .foregroundStyle(palette.text)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(palette.border, lineWidth: 1))
        .shadow(color: .black.opacity(0.16), radius: 12, y: 4)
        .accessibilityElement(children: .contain)
    }

    private func menuAction(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol).gameFont(.subheadline)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var credits: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Créditos e atribuições").gameFont(.headline).accessibilityAddTraits(.isHeader)
            if store.baseMapMode == .world {
                creditLink("OpenFreeMap", "https://openfreemap.org/")
                creditLink("© OpenMapTiles", "https://openmaptiles.org/")
            }
            creditLink("© OpenStreetMap", "https://www.openstreetmap.org/copyright")
            creditLink("Overture Maps Foundation", "https://docs.overturemaps.org/attribution/")
            creditLink("Google Open Buildings", "https://sites.research.google/open-buildings/")
            creditLink("Microsoft Global ML Building Footprints", "https://github.com/microsoft/GlobalMLBuildingFootprints")
            creditLink("Busca e rotas: Geoapify", "https://www.geoapify.com/")
        }
    }

    private func creditLink(_ title: String, _ address: String) -> some View {
        Link(title, destination: URL(string: address)!)
            .gameFont(.caption).frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
    }

    private func present(_ item: MapSheet) {
        menuExpanded = false
        sheet = item
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
        case .themes: AppearanceSheet(store: store, section: .themes)
        case .filters: AppearanceSheet(store: store, section: .filters)
        case .options: AppearanceSheet(store: store, section: .options)
        case .settings: APIKeySheet(store: store)
        case .route:
            NavigationStack {
                ScrollView {
                    RoutePanel(store: store, openSettings: { present(.settings) }).padding(16)
                }
                .background(palette.surface)
                .gameNavigationTitle("Sua rota").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { sheet = nil }.gameFont(.body) } }
            }
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
