import SwiftUI
import NavSkinCore

@MainActor
struct CompactRouteSummary: View {
    @ObservedObject var store: AppStore
    let openDetails: () -> Void
    var embedded = false
    private var palette: ThemePalette { store.theme.glassPalette }
    private var canResume: Bool {
        store.snapshot.phase == .resumeAvailable || (!store.snapshot.navigationActive &&
            store.snapshot.destination == nil && store.snapshot.resumeDestination != nil)
    }

    var body: some View {
        HStack(spacing: 10) {
            if !embedded && [.waitingForLocation, .calculating, .recalculating].contains(store.snapshot.phase) {
                ProgressView().accessibilityLabel("Aguardando rota")
            }
            Button(action: openDetails) {
                Group {
                    if let metrics {
                        HStack(spacing: 6) {
                            summaryMetric(metrics.duration, label: "restantes", prominent: true)
                            summaryMetric(metrics.distance, label: "distância")
                            summaryMetric(metrics.arrival, label: "chegada")
                        }
                    } else {
                        Text(summary).interfaceFont(.subheadline, weight: .bold).lineLimit(2).monospacedDigit()
                            .multilineTextAlignment(.center)
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .center)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(title). \(summary)")
            .accessibilityHint("Abrir detalhes e ações da rota")
            if store.snapshot.phase == .preview && store.snapshot.providerConfigured {
                Button("Iniciar") { store.startNavigation() }
                    .interfaceFont(.subheadline, weight: .bold)
                    .buttonStyle(.borderedProminent).controlSize(.large)
            }
        }
        .foregroundStyle(palette.text).padding(.horizontal, 14).padding(.vertical, 6)
        .background {
            if !embedded { Color.clear.glassSurface(radius: 24, palette: palette) }
        }
    }

    private var metrics: (duration: String, distance: String, arrival: String)? {
        let snapshot = store.snapshot
        if snapshot.guidanceAvailable, let progress = snapshot.progress {
            return (NavSkinFormat.duration(progress.remainingSeconds), NavSkinFormat.distance(progress.remainingDistanceMeters),
                    NavSkinFormat.arrival(progress.arrivalEpochMs))
        }
        if snapshot.phase == .preview, let route = snapshot.route {
            let arrival = Int64((Date().timeIntervalSince1970 + route.durationSeconds) * 1_000)
            return (NavSkinFormat.duration(route.durationSeconds), NavSkinFormat.distance(route.distanceMeters), NavSkinFormat.arrival(arrival))
        }
        if snapshot.navigationActive { return ("—", "—", "—") }
        return nil
    }

    private func summaryMetric(_ value: String, label: String, prominent: Bool = false) -> some View {
        VStack(spacing: 1) {
            Text(value).interfaceFont(prominent ? .title3 : .subheadline, weight: .bold)
                .foregroundStyle(prominent ? palette.accent : palette.text)
                .lineLimit(1).minimumScaleFactor(0.75).monospacedDigit()
            Text(label).interfaceFont(.caption2).foregroundStyle(palette.muted).lineLimit(1)
        }
        .frame(maxWidth: .infinity)
    }

    private var title: String {
        if canResume { return "Retomar destino" }
        if store.snapshot.phase == .arrived { return "Você chegou!" }
        return store.snapshot.destination?.name ?? store.snapshot.resumeDestination?.name ?? "Sua rota"
    }

    private var summary: String {
        let snapshot = store.snapshot
        if snapshot.phase == .waitingForLocation { return "Aguardando GPS preciso…" }
        if snapshot.phase == .calculating { return "Calculando rota…" }
        if snapshot.phase == .recalculating { return "Recalculando rota…" }
        if snapshot.phase == .arrived { return "Toque para concluir" }
        if canResume { return snapshot.resumeDestination?.name ?? "Recalcular com seu GPS atual" }
        if snapshot.phase == .rerouteFailed { return snapshot.message ?? "Recálculo indisponível · ver opções" }
        if snapshot.navigationActive {
            guard snapshot.guidanceAvailable, let progress = snapshot.progress else { return "Aguardando GPS válido" }
            return "\(NavSkinFormat.duration(progress.remainingSeconds)) · \(NavSkinFormat.distance(progress.remainingDistanceMeters)) · \(NavSkinFormat.arrival(progress.arrivalEpochMs))"
        }
        if snapshot.phase == .preview, let route = snapshot.route {
            let arrival = Int64((Date().timeIntervalSince1970 + route.durationSeconds) * 1_000)
            return "\(NavSkinFormat.duration(route.durationSeconds)) · \(NavSkinFormat.distance(route.distanceMeters)) · \(NavSkinFormat.arrival(arrival))"
        }
        return snapshot.message ?? "Toque para ver sua rota"
    }
}

@MainActor
struct GuidanceCard: View {
    @ObservedObject var store: AppStore
    let toggleMenu: () -> Void
    let menuExpanded: Bool
    private var palette: ThemePalette { store.theme.glassPalette }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            if store.snapshot.guidanceAvailable, let progress = store.snapshot.progress,
               let route = store.snapshot.route, route.steps.indices.contains(progress.stepIndex) {
                let step = route.steps[progress.stepIndex]
                Image(systemName: NavSkinFormat.maneuverSymbol(step.kind))
                    .font(.system(size: 36, weight: .bold)).frame(width: 44, height: 62)
                    .foregroundStyle(palette.accent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(NavSkinFormat.distance(progress.distanceToManeuverMeters))
                        .interfaceFont(.largeTitle, weight: .bold).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)
                    Text(step.instruction).interfaceFont(.headline).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                    if !step.streetName.isEmpty { Text(step.streetName).interfaceFont(.subheadline).foregroundStyle(palette.muted).lineLimit(1) }
                }
            } else {
                Image(systemName: store.snapshot.phase == .recalculating ? "arrow.triangle.2.circlepath" : "location.slash")
                    .font(.title2).frame(width: 44)
                VStack(alignment: .leading, spacing: 5) {
                    Text(statusTitle).interfaceFont(.headline)
                    Text(store.snapshot.message ?? "Aguardando uma localização precisa para continuar as instruções.")
                        .interfaceFont(.subheadline).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
            Button(action: toggleMenu) {
                Image(systemName: "ellipsis").font(.system(size: 22, weight: .bold))
                    .frame(width: 44, height: 44)
                    .background(.white.opacity(0.45), in: Circle())
            }.buttonStyle(.plain)
                .accessibilityLabel(menuExpanded ? "Fechar menu" : "Abrir menu")
                .accessibilityValue(menuExpanded ? "Expandido" : "Recolhido")
        }
        .foregroundStyle(palette.text).padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassSurface(radius: 23, palette: palette)
    }

    private var statusTitle: String {
        if store.snapshot.phase == .recalculating { return "Recalculando rota" }
        if store.snapshot.phase == .rerouteFailed { return "Recálculo indisponível" }
        if store.snapshot.location.quality == .lost { return "Sinal GPS perdido" }
        if store.snapshot.location.quality == .weak { return "Sinal GPS impreciso" }
        return "Aguardando GPS"
    }
}

@MainActor
struct RoutePanel: View {
    @ObservedObject var store: AppStore
    let openSettings: () -> Void
    @State private var saving = false
    private var palette: ThemePalette { store.theme.glassPalette }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if store.snapshot.phase == .resumeAvailable || (!store.snapshot.navigationActive && store.snapshot.destination == nil && store.snapshot.resumeDestination != nil) {
                resumePanel
            } else if store.snapshot.phase == .arrived {
                arrivedPanel
            } else {
                destinationHeading
                if !store.snapshot.intermediateStops.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Paradas antes do destino").interfaceFont(.subheadline, weight: .semibold)
                        ForEach(Array(store.snapshot.intermediateStops.enumerated()), id: \.element.id) { index, stop in
                            HStack(spacing: 8) {
                                Text("\(index + 1). \(stop.name)").interfaceFont(.subheadline)
                                Spacer()
                                Button { store.removeStop(id: stop.id) } label: {
                                    Image(systemName: "xmark.circle").frame(width: 44, height: 44)
                                }.accessibilityLabel("Remover parada \(stop.name)").disabled(store.snapshot.updatingStops)
                            }
                        }
                    }
                }
                if store.snapshot.phase == .waitingForLocation {
                    HStack { ProgressView(); Text("Aguardando GPS preciso…").interfaceFont(.subheadline) }
                    Text("A rota será calculada automaticamente quando houver uma posição atual e precisa.")
                        .interfaceFont(.caption).foregroundStyle(palette.muted)
                } else if store.snapshot.phase == .calculating {
                    HStack { ProgressView(); Text("Calculando rota…").interfaceFont(.subheadline) }
                } else if store.snapshot.phase == .preview, let route = store.snapshot.route {
                    routeSummary(route)
                } else if store.snapshot.guidanceAvailable, let progress = store.snapshot.progress {
                    navigationSummary(progress)
                } else if store.snapshot.navigationActive {
                    Text("Estimativas aguardam GPS válido e retorno à rota.").interfaceFont(.caption).foregroundStyle(palette.muted)
                }
                if let message = store.snapshot.message { Text(message).interfaceFont(.footnote).foregroundStyle(palette.muted) }
                if store.snapshot.pendingIntermediateStops != nil && !store.snapshot.updatingStops {
                    Button("Tentar atualizar paradas novamente") { store.retryRoute() }.buttonStyle(.borderedProminent)
                }
                actionRow
            }
        }
        .foregroundStyle(palette.text).padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(palette.border, lineWidth: 1))
        .sheet(isPresented: $saving) {
            if let destination = store.snapshot.destination {
                SavedPlaceEditor(store: store, destination: destination)
            }
        }
    }

    private var destinationHeading: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 3) {
                Text(store.snapshot.destination?.name ?? "Sua rota").interfaceFont(.headline).lineLimit(2)
                if let address = store.snapshot.destination?.address, !address.isEmpty {
                    Text(address).interfaceFont(.caption).foregroundStyle(palette.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            if store.snapshot.route != nil {
                Button { store.showOverview() } label: { Image(systemName: "arrow.up.left.and.arrow.down.right").frame(width: 44, height: 44) }
                    .accessibilityLabel("Ver rota completa")
            }
            if store.snapshot.destination != nil {
                Button { saving = true } label: { Image(systemName: "bookmark").frame(width: 44, height: 44) }
                    .accessibilityLabel("Salvar destino")
                if !store.snapshot.navigationActive && !store.snapshot.simulation {
                    Button { store.clearDestination() } label: { Image(systemName: "xmark").frame(width: 44, height: 44) }
                        .accessibilityLabel("Limpar destino e rota")
                }
            }
        }
    }

    private func routeSummary(_ route: NavRoute) -> some View {
        HStack(spacing: 22) {
            metric(NavSkinFormat.duration(route.durationSeconds), "tempo estimado")
            metric(NavSkinFormat.distance(route.distanceMeters), "distância")
            Spacer(minLength: 0)
            Text("Sem trânsito\nao vivo").interfaceFont(.caption2).foregroundStyle(palette.muted)
        }
    }

    private func navigationSummary(_ progress: RouteProgress) -> some View {
        HStack(alignment: .top, spacing: 16) {
            metric(NavSkinFormat.duration(progress.remainingSeconds), "restantes")
            metric(NavSkinFormat.distance(progress.remainingDistanceMeters), "distância")
            metric(NavSkinFormat.arrival(progress.arrivalEpochMs), "chegada")
        }
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).interfaceFont(.title3, weight: .bold).monospacedDigit()
            Text(label).interfaceFont(.caption).foregroundStyle(palette.muted)
        }
    }

    @ViewBuilder
    private var actionRow: some View {
        HStack(spacing: 10) {
            if !store.snapshot.providerConfigured {
                Button("Configurar chave", action: openSettings).buttonStyle(.borderedProminent)
            } else if store.snapshot.phase == .preview {
                Button { store.startNavigation() } label: { Label("Iniciar", systemImage: "location.fill").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).controlSize(.large)
            } else if store.snapshot.phase == .error || store.snapshot.phase == .rerouteFailed {
                Button("Tentar novamente") { store.retryRoute() }.buttonStyle(.borderedProminent)
            }
            if store.snapshot.locationAccess != .available && !store.snapshot.navigationActive &&
                !(store.snapshot.phase == .waitingForLocation && store.snapshot.serviceRunning) {
                Button("Ativar GPS") { store.activateGPS() }.buttonStyle(.bordered)
            }
            Button(store.snapshot.navigationActive ? "Parar" : "Cancelar", role: .destructive) { store.stopNavigation() }
                .buttonStyle(.bordered).controlSize(.large)
        }
    }

    private var resumePanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Retomar destino", systemImage: "arrow.clockwise").interfaceFont(.headline)
            Text(store.snapshot.resumeDestination?.name ?? "Último destino").interfaceFont(.subheadline)
            if !store.snapshot.resumeIntermediateStops.isEmpty {
                Text("Paradas: " + store.snapshot.resumeIntermediateStops.map(\.name).joined(separator: " → ")).interfaceFont(.subheadline)
            }
            Text("Uma nova rota será calculada usando seu GPS atual.").interfaceFont(.caption).foregroundStyle(palette.muted)
            HStack {
                Button("Recalcular") { store.resumeDestination() }.buttonStyle(.borderedProminent)
                Button("Dispensar") { store.stopNavigation() }.buttonStyle(.bordered)
            }
        }
    }

    private var arrivedPanel: some View {
        HStack(spacing: 14) {
            Image(systemName: "flag.checkered.circle.fill").font(.largeTitle).foregroundStyle(palette.accent)
            VStack(alignment: .leading, spacing: 4) {
                Text("Você chegou!").interfaceFont(.title2, weight: .bold)
                Text(store.snapshot.destination?.name ?? "Destino").interfaceFont(.subheadline)
            }
            Spacer()
            Button("Concluir") { store.stopNavigation() }.buttonStyle(.borderedProminent)
        }
    }
}
