import SwiftUI
import NavSkinCore

@MainActor
struct GuidanceCard: View {
    @ObservedObject var store: AppStore
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            if store.snapshot.guidanceAvailable, let progress = store.snapshot.progress,
               let route = store.snapshot.route, route.steps.indices.contains(progress.stepIndex) {
                let step = route.steps[progress.stepIndex]
                Image(systemName: NavSkinFormat.maneuverSymbol(step.kind))
                    .font(.system(size: 32, weight: .bold)).frame(width: 48)
                VStack(alignment: .leading, spacing: 5) {
                    Text(NavSkinFormat.distance(progress.distanceToManeuverMeters)).gameFont(.title2, weight: .bold)
                    Text(step.instruction).gameFont(.headline).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                    if !step.streetName.isEmpty { Text(step.streetName).gameFont(.subheadline).foregroundStyle(palette.muted).lineLimit(1) }
                }
            } else {
                Image(systemName: store.snapshot.phase == .recalculating ? "arrow.triangle.2.circlepath" : "location.slash")
                    .font(.title2).frame(width: 44)
                VStack(alignment: .leading, spacing: 5) {
                    Text(statusTitle).gameFont(.headline)
                    Text(store.snapshot.message ?? "Aguardando uma localização precisa para continuar as instruções.")
                        .gameFont(.subheadline).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
                }
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(palette.text).padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 22))
        .overlay(RoundedRectangle(cornerRadius: 22).stroke(palette.border, lineWidth: 1))
        .shadow(color: .black.opacity(0.14), radius: 12, y: 4)
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
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if store.snapshot.phase == .resumeAvailable || (!store.snapshot.navigationActive && store.snapshot.destination == nil && store.snapshot.resumeDestination != nil) {
                resumePanel
            } else if store.snapshot.phase == .arrived {
                arrivedPanel
            } else {
                destinationHeading
                if store.snapshot.phase == .calculating {
                    HStack { ProgressView(); Text("Calculando rota…").gameFont(.subheadline) }
                } else if store.snapshot.phase == .preview, let route = store.snapshot.route {
                    routeSummary(route)
                } else if store.snapshot.guidanceAvailable, let progress = store.snapshot.progress {
                    navigationSummary(progress)
                } else if store.snapshot.navigationActive {
                    Text("Estimativas aguardam GPS válido e retorno à rota.").gameFont(.caption).foregroundStyle(palette.muted)
                }
                if let message = store.snapshot.message { Text(message).gameFont(.footnote).foregroundStyle(palette.muted) }
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
                Text(store.snapshot.destination?.name ?? "Sua rota").gameFont(.headline).lineLimit(2)
                if let address = store.snapshot.destination?.address, !address.isEmpty {
                    Text(address).gameFont(.caption).foregroundStyle(palette.muted).lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            if store.snapshot.route != nil {
                Button { store.showOverview() } label: { Image(systemName: "arrow.up.left.and.arrow.down.right").frame(width: 40, height: 40) }
                    .accessibilityLabel("Ver rota completa")
            }
            if store.snapshot.destination != nil {
                Button { saving = true } label: { Image(systemName: "bookmark").frame(width: 40, height: 40) }
                    .accessibilityLabel("Salvar destino")
            }
        }
    }

    private func routeSummary(_ route: NavRoute) -> some View {
        HStack(spacing: 22) {
            metric(NavSkinFormat.duration(route.durationSeconds), "tempo estimado")
            metric(NavSkinFormat.distance(route.distanceMeters), "distância")
            Spacer(minLength: 0)
            Text("Sem trânsito\nao vivo").gameFont(.caption2).foregroundStyle(palette.muted)
        }
    }

    private func navigationSummary(_ progress: RouteProgress) -> some View {
        HStack(alignment: .top, spacing: 16) {
            metric(NavSkinFormat.arrival(progress.arrivalEpochMs), "chegada")
            metric(NavSkinFormat.duration(progress.remainingSeconds), NavSkinFormat.distance(progress.remainingDistanceMeters))
            Spacer(minLength: 0)
            if let speed = store.snapshot.location.speedKmh, speed.isFinite {
                metric("\(Int(speed.rounded()))", "km/h")
            } else { metric("—", "km/h") }
        }
    }

    private func metric(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).gameFont(.title3, weight: .bold).monospacedDigit()
            Text(label).gameFont(.caption).foregroundStyle(palette.muted)
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
            if store.snapshot.locationAccess != .available && !store.snapshot.navigationActive {
                Button("Ativar GPS") { store.activateGPS() }.buttonStyle(.bordered)
            }
            Button(store.snapshot.navigationActive ? "Encerrar" : "Cancelar", role: .destructive) { store.stopNavigation() }
                .buttonStyle(.bordered).controlSize(.large)
        }
    }

    private var resumePanel: some View {
        VStack(alignment: .leading, spacing: 10) {
            Label("Retomar destino", systemImage: "arrow.clockwise").gameFont(.headline)
            Text(store.snapshot.resumeDestination?.name ?? "Último destino").gameFont(.subheadline)
            Text("Uma nova rota será calculada usando seu GPS atual.").gameFont(.caption).foregroundStyle(palette.muted)
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
                Text("Você chegou!").gameFont(.title2, weight: .bold)
                Text(store.snapshot.destination?.name ?? "Destino").gameFont(.subheadline)
            }
            Spacer()
            Button("Concluir") { store.stopNavigation() }.buttonStyle(.borderedProminent)
        }
    }
}
