import SwiftUI
import UIKit
import NavSkinCore

private enum MapSheet: String, Identifiable {
    case search, saved, themes, filters, settings, route, point
    var id: String { rawValue }
}

@MainActor
struct NavSkinRootView: View {
    @ObservedObject var store: AppStore
    @State private var sheet: MapSheet?
    @State private var menuExpanded = false
    @State private var addingStop = false
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @Environment(\.scenePhase) private var scenePhase

    init(store: AppStore) { self.store = store }

    private var palette: ThemePalette { store.theme.glassPalette }
    private var compact: Bool { verticalSizeClass == .compact }
    private var controlDiameter: CGFloat { compact ? 54 : 64 }
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

    var body: some View {
        ZStack {
            NavSkinView(store: store).ignoresSafeArea()
            if store.vibing {
                if store.vibingExitVisible {
                    Button { store.exitVibing() } label: {
                        Label("Sair do Vibing", systemImage: "xmark")
                            .interfaceFont(.subheadline, weight: .semibold)
                            .foregroundStyle(palette.text).padding(.horizontal, 20)
                            .frame(minHeight: 48).glassSurface(radius: 24, palette: palette)
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(store.snapshot.navigationActive
                        ? "A rota continua ativa ao voltar aos controles" : "Voltar aos controles do mapa")
                    .padding(.bottom, 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
            } else {
            bottomControls.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            GeometryReader { geometry in
                ZStack(alignment: .topTrailing) {
                    if menuExpanded {
                        Color.black.opacity(0.08).ignoresSafeArea()
                            .onTapGesture { withAnimation { menuExpanded = false } }
                            .accessibilityHidden(true)
                    }
                    VStack(spacing: 8) {
                        if !store.snapshot.navigationActive {
                            HStack { Spacer(); overflowButton.glassSurface(radius: 26, palette: palette) }
                                .padding(.horizontal, 16)
                        }
                        if store.snapshot.simulation || store.snapshot.location.fix?.isMock == true { simulationBanner }
                        if store.snapshot.navigationActive {
                            ScrollView {
                                GuidanceCard(store: store, toggleMenu: toggleMenu, menuExpanded: menuExpanded)
                            }
                            .scrollIndicators(.hidden)
                            .frame(maxWidth: compact ? max(240, geometry.size.width * 0.46) : .infinity)
                            .frame(maxHeight: compact ? 112 : 168)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.top, 8)
                    if menuExpanded {
                        mapMenu
                            .frame(width: min(248, max(0, geometry.size.width - 32)))
                            .frame(height: min(280, max(80, geometry.size.height - (store.snapshot.navigationActive ? (compact ? 128 : 168) : 70))))
                            .padding(.top, store.snapshot.navigationActive ? (compact ? 120 : 160) : 62).padding(.trailing, 16)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }
                }
            }
            }
        }
        .statusBarHidden(store.vibing)
        .persistentSystemOverlays(store.vibing ? .hidden : .automatic)
        .tint(palette.accent)
        .environment(\.gameTheme, store.theme)
        .font(.system(.body))
        .sheet(item: $sheet, onDismiss: { store.mapSelection = nil }) { item in
            sheetContent(item)
                .environment(\.gameTheme, store.theme)
                .font(.system(.body))
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
        .onChange(of: store.snapshot.navigationActive) { _, active in
            if !active && addingStop { sheet = nil; addingStop = false }
        }
        .onChange(of: store.vibing) { _, active in
            if active { menuExpanded = false; sheet = nil; addingStop = false }
            UIApplication.shared.isIdleTimerDisabled = active && scenePhase == .active
        }
        .onChange(of: scenePhase) { _, phase in
            UIApplication.shared.isIdleTimerDisabled = store.vibing && phase == .active
        }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
        .alert("Não foi possível concluir", isPresented: Binding(
            get: { store.uiError != nil }, set: { if !$0 { store.clearUIError() } }
        )) {
            Button("OK", role: .cancel) { store.clearUIError() }
        } message: { Text(store.uiError ?? "") }
    }

    private func toggleMenu() {
        withAnimation(.easeInOut(duration: 0.18)) { menuExpanded.toggle() }
    }

    private var overflowButton: some View {
        Button(action: toggleMenu) {
            Image(systemName: "ellipsis").font(.system(size: 23, weight: .bold))
                .foregroundStyle(palette.text).frame(width: 52, height: 52)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(menuExpanded ? "Fechar menu" : "Abrir menu")
        .accessibilityValue(menuExpanded ? "Expandido" : "Recolhido")
    }

    private var bottomControls: some View {
        VStack(spacing: 8) {
            if let resourceError = store.resourceError {
                MessageCard(text: resourceError, symbol: "exclamationmark.triangle", palette: palette)
            }
            HStack(alignment: .bottom) {
                speedBadge
                Spacer(minLength: 16)
                Button { store.recenter() } label: {
                    Image(systemName: "scope").font(.system(size: 27, weight: .regular))
                        .foregroundStyle(store.following ? palette.text : palette.accent)
                        .frame(width: controlDiameter, height: controlDiameter)
                        .glassSurface(radius: controlDiameter / 2, palette: palette)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Centralizar no GPS")
                .accessibilityHint("Voltar a acompanhar sua posição")
                .accessibilityAddTraits(store.following ? .isSelected : [])
            }
            if !compact && !store.snapshot.intermediateStops.isEmpty { stopChips }
            if store.snapshot.pendingIntermediateStops != nil {
                Button { if !store.snapshot.updatingStops { store.retryRoute() } } label: {
                    HStack(spacing: 8) {
                        if store.snapshot.updatingStops { ProgressView() }
                        Text(store.snapshot.message ?? "Atualizar paradas").interfaceFont(.caption).lineLimit(2)
                        if !store.snapshot.updatingStops { Image(systemName: "arrow.clockwise") }
                    }
                    .foregroundStyle(palette.text).padding(10).frame(maxWidth: .infinity, minHeight: 44)
                    .glassSurface(radius: 16, palette: palette)
                }.buttonStyle(.plain).disabled(store.snapshot.updatingStops)
            }
            if store.snapshot.navigationActive {
                VStack(spacing: 0) {
                    CompactRouteSummary(store: store, openDetails: { present(.route) }, embedded: true)
                    HStack(spacing: 8) {
                        Button { store.stopNavigation(); addingStop = false } label: {
                            Label("Parar", systemImage: "stop.fill")
                                .interfaceFont(.subheadline, weight: .semibold)
                                .frame(minWidth: 84, maxWidth: 110, minHeight: 48)
                                .foregroundStyle(.white).background(Color(rgb: 0xDE3542), in: RoundedRectangle(cornerRadius: 15))
                        }
                        Button { addingStop = true; store.search(""); present(.search) } label: {
                            Label("Adicionar parada", systemImage: "plus")
                                .interfaceFont(.subheadline, weight: .semibold)
                                .multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .padding(.horizontal, 6).foregroundStyle(palette.text)
                                .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 15))
                        }.disabled(store.snapshot.simulation)
                    }.buttonStyle(.plain).padding(.horizontal, 10).padding(.bottom, 10)
                }.glassSurface(radius: 24, palette: palette)
            } else {
                if showsRoutePanel { CompactRouteSummary(store: store, openDetails: { present(.route) }) }
                searchButton
            }
        }
        .frame(maxWidth: compact ? 330 : .infinity)
        .padding(.horizontal, 16).padding(.bottom, 8)
        .frame(maxWidth: .infinity, alignment: compact ? .trailing : .center)
    }

    private var searchButton: some View {
        HStack(spacing: 0) {
            Button { addingStop = false; store.search(""); present(.search) } label: {
                HStack(spacing: 12) {
                    Image(systemName: "magnifyingglass").font(.system(size: 20, weight: .semibold))
                    Text(store.snapshot.destination?.name ?? store.snapshot.resumeDestination?.name ?? "Para onde?")
                        .interfaceFont(.headline).lineLimit(1)
                }
                .foregroundStyle(palette.text).padding(.horizontal, 18).frame(maxWidth: .infinity, minHeight: 54)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Buscar um destino ou trocar sua rota")
            if !store.snapshot.simulation && (store.snapshot.destination != nil || store.snapshot.resumeDestination != nil) {
                Button { addingStop = false; store.clearDestination() } label: {
                    Image(systemName: "xmark").font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(palette.text).frame(width: 48, height: 54)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Limpar destino e rota")
                .accessibilityHint("Voltar ao mapa sem um destino selecionado")
            }
        }
        .glassSurface(radius: 23, palette: palette)
    }

    private var stopChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(store.snapshot.intermediateStops.enumerated()), id: \.element.id) { index, stop in
                    Button { store.removeStop(id: stop.id) } label: {
                        HStack(spacing: 8) {
                            Text("\(index + 1). \(stop.name)").lineLimit(1)
                            Image(systemName: "xmark")
                        }.interfaceFont(.caption, weight: .semibold).foregroundStyle(palette.text)
                            .padding(.horizontal, 14).frame(minHeight: 44).glassSurface(radius: 22, palette: palette)
                    }.buttonStyle(.plain).disabled(store.snapshot.updatingStops)
                        .accessibilityLabel("Remover parada \(stop.name)")
                }
            }.padding(.vertical, 3)
        }
    }

    private var speedBadge: some View {
        VStack(spacing: 0) {
            Text(speedText).interfaceFont(size: compact ? 24 : 28, weight: .bold).monospacedDigit()
            Text("km/h").interfaceFont(size: 10)
        }
        .foregroundStyle(palette.text).frame(width: controlDiameter, height: controlDiameter)
        .glassSurface(radius: controlDiameter / 2, palette: palette)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(speedText == "—" ? "Velocidade indisponível" : "Velocidade: \(speedText) quilômetros por hora")
    }

    private var simulationBanner: some View {
        HStack {
            Label(store.snapshot.simulation ? "SIMULAÇÃO · replay de teste" : "SIMULAÇÃO · posição simulada pelo sistema", systemImage: "testtube.2").interfaceFont(.caption, weight: .bold)
            Spacer()
            if store.snapshot.simulation {
                Button("Encerrar") { store.stopSimulation() }.interfaceFont(.caption, weight: .bold).frame(minHeight: 44)
            }
        }
        .padding(.horizontal, 10).padding(.vertical, 4)
        .foregroundStyle(Color.black).background(Color.yellow, in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16).accessibilityElement(children: .contain)
    }

    private var mapMenu: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                menuAction("Vibing", symbol: "sparkles") { store.enterVibing() }
                menuAction("Temas", symbol: "paintpalette") { present(.themes) }
                menuAction("Filtros", symbol: "line.3.horizontal.decrease") { present(.filters) }
                MapDimensionToggle(store: store)
                menuAction("Seus lugares", symbol: "bookmark") { present(.saved) }
                menuAction("Configurações", symbol: "gearshape") { present(.settings) }
            }
            .padding(14)
        }
        .foregroundStyle(palette.text)
        .glassSurface(radius: 22, palette: palette)
        .accessibilityElement(children: .contain)
    }

    private func menuAction(_ title: String, symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: symbol).interfaceFont(.subheadline)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func present(_ item: MapSheet) {
        menuExpanded = false
        sheet = item
    }

    @ViewBuilder
    private func sheetContent(_ item: MapSheet) -> some View {
        switch item {
        case .search:
            DestinationSearchSheet(store: store, addingStop: addingStop) { destination in
                choose(destination)
                sheet = nil
            }
        case .saved:
            SavedPlacesSheet(store: store) { destination in
                choose(destination)
                sheet = nil
            }
        case .themes: AppearanceSheet(store: store, section: .themes)
        case .filters: AppearanceSheet(store: store, section: .filters)
        case .settings: APIKeySheet(store: store)
        case .route:
            NavigationStack {
                ScrollView {
                    RoutePanel(store: store, openSettings: { present(.settings) }).padding(16)
                }
                .background(palette.surface)
                .navigationTitle("Sua rota").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { sheet = nil }.interfaceFont(.body) } }
            }
        case .point:
            if let destination = store.mapSelection {
                SelectedDestinationSheet(store: store, destination: destination, addingStop: addingStop && store.snapshot.navigationActive) {
                    choose(destination)
                    sheet = nil
                }
            }
        }
    }

    private func choose(_ destination: Destination) {
        if addingStop {
            // A search opened for a stop never silently becomes a replacement destination.
            if store.snapshot.navigationActive { store.addStop(destination) }
        } else { store.selectDestination(destination) }
        addingStop = false
    }
}
