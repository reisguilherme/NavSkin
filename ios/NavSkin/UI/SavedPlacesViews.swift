import SwiftUI
import NavSkinCore

private struct PlaceEditing: Identifiable {
    let place: SavedPlace?
    let destination: Destination
    var id: String { place?.id ?? destination.id }
}

@MainActor
struct SavedPlacesSheet: View {
    @ObservedObject var store: AppStore
    let select: (Destination) -> Void
    @State private var editing: PlaceEditing?
    @State private var deleting: SavedPlace?
    @Environment(\.dismiss) private var dismiss
    private var palette: ThemePalette { store.theme.glassPalette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    SheetHeading(title: "Seus lugares", subtitle: "Casa, trabalho e favoritos com a sua cara.", palette: palette)
                    if let error = store.places.loadError {
                        MessageCard(text: error, symbol: "exclamationmark.triangle", palette: palette)
                    }
                    if store.places.savedPlaces.isEmpty {
                        ContentUnavailableView("Nenhum favorito ainda", systemImage: "bookmark",
                                               description: Text("Adicione um local pelas coordenadas ou salve um destino no mapa."))
                    } else {
                        ForEach(store.places.savedPlaces, id: \.id) { place in savedRow(place) }
                    }
                    Button { addPlace() } label: { Label("Adicionar local", systemImage: "plus").frame(maxWidth: .infinity) }
                        .buttonStyle(.borderedProminent).controlSize(.large).disabled(store.places.loadError != nil)
                    if !store.places.recents.isEmpty {
                        Text("RECENTES").interfaceFont(.caption, weight: .bold).foregroundStyle(palette.muted)
                        ForEach(store.places.recents, id: \.destination.id) { recent in
                            DestinationRow(destination: recent.destination, palette: palette, symbol: "clock") { select(recent.destination) }
                        }
                    }
                }.padding(20)
            }
            .background(palette.surface).foregroundStyle(palette.text)
            .navigationTitle("Locais salvos").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { dismiss() }.interfaceFont(.body) } }
            .sheet(item: $editing) { item in SavedPlaceEditor(store: store, destination: item.destination, existing: item.place) }
            .confirmationDialog("Excluir este local?", isPresented: Binding(
                get: { deleting != nil }, set: { if !$0 { deleting = nil } }
            ), titleVisibility: .visible) {
                Button("Excluir", role: .destructive) { if let deleting { store.deletePlace(deleting.id) }; deleting = nil }
                Button("Cancelar", role: .cancel) { deleting = nil }
            } message: { Text(deleting?.name ?? "") }
        }
    }

    private func savedRow(_ place: SavedPlace) -> some View {
        HStack(spacing: 12) {
            Button { select(place.asDestination()) } label: {
                HStack(spacing: 12) {
                    CatalogBitmap(url: store.resources.savedIcon(id: place.iconId).flatMap { store.resources.imageURL(for: $0, theme: store.theme) }, size: 44)
                    VStack(alignment: .leading, spacing: 4) {
                        Text(place.name).interfaceFont(.headline).foregroundStyle(palette.text)
                        if !place.address.isEmpty { Text(place.address).interfaceFont(.caption).foregroundStyle(palette.muted).lineLimit(1) }
                        HStack(spacing: 8) {
                            if store.places.homeId == place.id { Label("Casa", systemImage: "house.fill") }
                            if store.places.workId == place.id { Label("Trabalho", systemImage: "briefcase.fill") }
                        }.interfaceFont(.caption2, weight: .bold).foregroundStyle(palette.accent)
                    }
                    Spacer(minLength: 0)
                }.frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            }.buttonStyle(.plain)
            Menu {
                Button("Editar", systemImage: "pencil") { editing = PlaceEditing(place: place, destination: place.asDestination()) }
                Button(store.places.homeId == place.id ? "Remover de Casa" : "Definir como Casa", systemImage: "house") {
                    store.setHome(store.places.homeId == place.id ? nil : place.id)
                }
                Button(store.places.workId == place.id ? "Remover de Trabalho" : "Definir como Trabalho", systemImage: "briefcase") {
                    store.setWork(store.places.workId == place.id ? nil : place.id)
                }
                Button("Excluir", systemImage: "trash", role: .destructive) { deleting = place }
            } label: { Image(systemName: "ellipsis.circle").font(.title2).frame(width: 44, height: 44) }
            .accessibilityLabel("Opções de \(place.name)")
        }
        .padding(12).background(palette.soft, in: RoundedRectangle(cornerRadius: 17))
    }

    private func addPlace() {
        let destination: Destination
        if let selected = store.snapshot.destination { destination = selected }
        else if let fix = store.snapshot.location.fix, store.snapshot.location.quality == .good {
            destination = Destination(id: UUID().uuidString, name: "", point: fix.point)
        } else {
            // Empty fields require explicit coordinates; no invented default position is saved.
            destination = Destination(id: UUID().uuidString, name: "", point: GeoPoint(latitude: .nan, longitude: .nan))
        }
        editing = PlaceEditing(place: nil, destination: destination)
    }
}

@MainActor
struct SavedPlaceEditor: View {
    @ObservedObject var store: AppStore
    let existing: SavedPlace?
    @State private var name: String
    @State private var address: String
    @State private var latitude: String
    @State private var longitude: String
    @State private var categoryId: String
    @State private var iconId: String
    @State private var validation: String?
    @Environment(\.dismiss) private var dismiss
    private var palette: ThemePalette { store.theme.glassPalette }

    init(store: AppStore, destination: Destination, existing: SavedPlace? = nil) {
        self.store = store
        self.existing = existing
        _name = State(initialValue: existing?.name ?? destination.name)
        _address = State(initialValue: existing?.address ?? destination.address)
        let point = existing?.point ?? destination.point
        _latitude = State(initialValue: point.latitude.isFinite ? String(point.latitude) : "")
        _longitude = State(initialValue: point.longitude.isFinite ? String(point.longitude) : "")
        _categoryId = State(initialValue: existing?.categoryId ?? "")
        _iconId = State(initialValue: existing?.iconId ?? "special")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Local") {
                    TextField("Nome", text: $name).textInputAutocapitalization(.words)
                    TextField("Endereço (opcional)", text: $address).textInputAutocapitalization(.words)
                    TextField("Latitude", text: $latitude).keyboardType(.numbersAndPunctuation).autocorrectionDisabled()
                    TextField("Longitude", text: $longitude).keyboardType(.numbersAndPunctuation).autocorrectionDisabled()
                }
                Section("Categoria") {
                    Picker("Categoria", selection: $categoryId) {
                        Text("Sem categoria").tag("")
                        ForEach(PoiCategory.allCases) { category in Label(category.title, systemImage: category.symbol).tag(category.id) }
                    }
                }
                Section("Ícone · \(store.theme.title)") {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 78))], spacing: 12) {
                        ForEach(store.resources.saved) { icon in
                            Button { iconId = icon.id } label: {
                                VStack(spacing: 5) {
                                    CatalogBitmap(url: store.resources.imageURL(for: icon, theme: store.theme), size: 46)
                                    Text(icon.label).interfaceFont(.caption2).lineLimit(2).multilineTextAlignment(.center)
                                }
                                .foregroundStyle(palette.text).frame(maxWidth: .infinity, minHeight: 82)
                                .background(iconId == icon.id ? palette.soft : Color.clear, in: RoundedRectangle(cornerRadius: 12))
                                .overlay(RoundedRectangle(cornerRadius: 12).stroke(iconId == icon.id ? palette.accent : Color.clear, lineWidth: 2))
                            }.buttonStyle(.plain).accessibilityAddTraits(iconId == icon.id ? .isSelected : [])
                        }
                    }.padding(.vertical, 6)
                }
                if let validation { Section { Text(validation).foregroundStyle(.red).interfaceFont(.footnote) } }
                if let error = store.uiError { Section { Text(error).foregroundStyle(.red).interfaceFont(.footnote) } }
                Section { Button("Salvar local", action: save).frame(maxWidth: .infinity).interfaceFont(.headline) }
            }
            .tint(palette.accent)
            .navigationTitle(existing == nil ? "Novo favorito" : "Editar favorito").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarLeading) { Button("Cancelar") { dismiss() }.interfaceFont(.body) } }
        }
    }

    private func save() {
        guard let lat = coordinate(latitude), let lon = coordinate(longitude),
              GeoMath.isValid(GeoPoint(latitude: lat, longitude: lon)) else {
            validation = "Informe latitude entre −90 e 90 e longitude entre −180 e 180."
            return
        }
        guard !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            validation = "Informe um nome para este local."
            return
        }
        validation = nil
        if store.savePlace(name: name, point: GeoPoint(latitude: lat, longitude: lon), address: address,
                           categoryId: categoryId.isEmpty ? nil : categoryId, iconId: iconId, id: existing?.id) {
            dismiss()
        }
    }

    private func coordinate(_ text: String) -> Double? {
        Double(text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: "."))
    }
}
