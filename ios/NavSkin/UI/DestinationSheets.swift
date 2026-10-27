import SwiftUI
import NavSkinCore

@MainActor
struct DestinationSearchSheet: View {
    @ObservedObject var store: AppStore
    let select: (Destination) -> Void
    @State private var query = ""
    @State private var settings = false
    @FocusState private var focused: Bool
    @Environment(\.dismiss) private var dismiss
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    SheetHeading(title: "Seu próximo destino", subtitle: "Busque um lugar, endereço ou cidade.", palette: palette)
                    searchField
                    if !store.snapshot.providerConfigured {
                        MessageCard(text: "Adicione sua chave Geoapify para buscar lugares e calcular rotas online.", symbol: "key", palette: palette)
                        Button("Configurar chave") { settings = true }.buttonStyle(.borderedProminent)
                    }
                    if store.snapshot.searching {
                        HStack { ProgressView(); Text("Buscando lugares…").gameFont(.subheadline) }.padding(.vertical, 12)
                    }
                    if let error = store.snapshot.searchError {
                        MessageCard(text: error, symbol: "exclamationmark.triangle", palette: palette)
                    }
                    results
                    if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        quickDestinations
                        Text("Dica: mantenha pressionado um ponto no mapa para escolher um destino pelas coordenadas.")
                            .gameFont(.footnote).foregroundStyle(palette.muted)
                    }
                }.padding(20)
            }
            .background(palette.surface).foregroundStyle(palette.text)
            .gameNavigationTitle("Buscar").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { dismiss() }.gameFont(.body) } }
            .sheet(isPresented: $settings) { APIKeySheet(store: store) }
        }
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass").foregroundStyle(palette.muted)
            TextField("Lugar, endereço ou cidade", text: $query)
                .textInputAutocapitalization(.words).autocorrectionDisabled()
                .submitLabel(.search).focused($focused)
                .onSubmit { focused = false; store.search(query) }
            if !query.isEmpty {
                Button { query = ""; store.search("") } label: { Image(systemName: "xmark.circle.fill") }
                    .accessibilityLabel("Limpar busca")
            }
            Button("Buscar") { focused = false; store.search(query) }
                .gameFont(.subheadline, weight: .bold).disabled(query.trimmingCharacters(in: .whitespacesAndNewlines).count < 2 || store.snapshot.searching)
        }
        .padding(14).background(palette.soft, in: RoundedRectangle(cornerRadius: 16))
    }

    private var results: some View {
        VStack(spacing: 0) {
            ForEach(store.snapshot.searchResults, id: \.id) { destination in
                DestinationRow(destination: destination, palette: palette) { select(destination) }
                Divider().overlay(palette.border)
            }
        }
    }

    private var quickDestinations: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let home = store.places.savedPlaces.first(where: { $0.id == store.places.homeId }) {
                DestinationRow(destination: home.asDestination(), palette: palette, symbol: "house.fill") { select(home.asDestination()) }
            }
            if let work = store.places.savedPlaces.first(where: { $0.id == store.places.workId }) {
                DestinationRow(destination: work.asDestination(), palette: palette, symbol: "briefcase.fill") { select(work.asDestination()) }
            }
            if !store.places.recents.isEmpty {
                Text("RECENTES").gameFont(.caption, weight: .bold).foregroundStyle(palette.muted)
                ForEach(store.places.recents, id: \.destination.id) { recent in
                    DestinationRow(destination: recent.destination, palette: palette, symbol: "clock") { select(recent.destination) }
                }
            }
        }
    }
}

@MainActor
struct DestinationRow: View {
    let destination: Destination
    let palette: ThemePalette
    var symbol = "mappin.circle.fill"
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol).font(.title2).foregroundStyle(palette.accent).frame(width: 32)
                VStack(alignment: .leading, spacing: 4) {
                    Text(destination.name).gameFont(.headline).foregroundStyle(palette.text).multilineTextAlignment(.leading)
                    Text(destination.address.isEmpty ? NavSkinFormat.coordinates(destination.point) : destination.address)
                        .gameFont(.caption).foregroundStyle(palette.muted).lineLimit(2).multilineTextAlignment(.leading)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right").font(.caption.bold()).foregroundStyle(palette.muted)
            }
            .frame(maxWidth: .infinity, minHeight: 52).padding(.vertical, 7)
        }.buttonStyle(.plain)
    }
}

@MainActor
struct SelectedDestinationSheet: View {
    @ObservedObject var store: AppStore
    let destination: Destination
    let navigate: () -> Void
    @State private var saving = false
    @Environment(\.dismiss) private var dismiss
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                SheetHeading(title: destination.name, subtitle: destination.address.isEmpty ? "Ponto selecionado no mapa" : destination.address, palette: palette)
                Label(NavSkinFormat.coordinates(destination.point), systemImage: "mappin.and.ellipse")
                    .gameFont(.subheadline).monospacedDigit().foregroundStyle(palette.muted)
                Button(action: navigate) { Label("Calcular rota", systemImage: "arrow.triangle.turn.up.right.diamond.fill").frame(maxWidth: .infinity) }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                Button { saving = true } label: { Label("Salvar nos favoritos", systemImage: "bookmark").frame(maxWidth: .infinity) }
                    .buttonStyle(.bordered).controlSize(.large)
                Text("A rota de carro pode terminar no acesso viário mais próximo do destino.").gameFont(.footnote).foregroundStyle(palette.muted)
                Spacer(minLength: 0)
            }
            .padding(20).background(palette.surface).foregroundStyle(palette.text)
            .gameNavigationTitle("Local").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { dismiss() }.gameFont(.body) } }
            .sheet(isPresented: $saving) { SavedPlaceEditor(store: store, destination: destination) }
        }
    }
}
