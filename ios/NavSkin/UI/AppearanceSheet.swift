import SwiftUI
import NavSkinCore

enum AppearanceSection {
    case themes, filters, options
    var title: String {
        switch self {
        case .themes: return "Temas"
        case .filters: return "Filtros"
        case .options: return "Opções do mapa"
        }
    }
}

@MainActor
struct AppearanceSheet: View {
    @ObservedObject var store: AppStore
    var section: AppearanceSection = .themes
    @State private var settings = false
    @Environment(\.dismiss) private var dismiss
    private var palette: ThemePalette { store.theme.palette }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    switch section {
                    case .themes:
                        SheetHeading(title: "Do seu jeito", subtitle: "Três universos para explorar o mundo.", palette: palette).padding(.horizontal, 20)
                        ThemeCarousel(store: store, onSelect: {})
                        VStack(alignment: .leading, spacing: 24) {
                            vehicleSelection
                            markerCatalog
                        }.padding(.horizontal, 20)
                    case .filters:
                        VStack(alignment: .leading, spacing: 24) {
                            categories
                            if store.baseMapMode == .goianiaOffline {
                                Text("Este snapshot offline não inclui escolas, cultura e locais de culto. Use o mapa mundial para esses grupos.")
                                    .gameFont(.caption).foregroundStyle(palette.muted)
                            }
                        }.padding(.horizontal, 20)
                    case .options:
                        VStack(alignment: .leading, spacing: 24) {
                            baseMapSelection
                            mapPreferences
                            Button { settings = true } label: {
                                Label("Chave Geoapify e localização", systemImage: "key").frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            }.buttonStyle(.bordered).controlSize(.large)
                            Text("Busca e rotas dependem de conexão com a internet. A estimativa de chegada não inclui trânsito ao vivo.")
                                .gameFont(.footnote).foregroundStyle(palette.muted)
                        }.padding(.horizontal, 20)
                    }
                }.padding(.vertical, 20)
            }
            .background(palette.surface).foregroundStyle(palette.text)
            .gameNavigationTitle(section.title).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("Fechar") { dismiss() }.gameFont(.body) } }
            .sheet(isPresented: $settings) { APIKeySheet(store: store) }
        }
        .tint(palette.accent)
    }

    private var baseMapSelection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Cobertura do mapa").gameFont(.headline)
            ForEach(MapBaseMode.allCases) { mode in
                Button { store.setBaseMapMode(mode) } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: mode == .world ? "globe.americas" : "arrow.down.circle")
                            .frame(width: 24).padding(.top, 2)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(mode.title).gameFont(.subheadline, weight: .semibold)
                            Text(mode.description).gameFont(.caption).foregroundStyle(palette.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer(minLength: 4)
                        Image(systemName: store.baseMapMode == mode ? "checkmark.circle.fill" : "circle")
                    }
                    .foregroundStyle(palette.text).padding(14).frame(maxWidth: .infinity, minHeight: 64, alignment: .leading)
                    .background(store.baseMapMode == mode ? palette.soft : palette.surface, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(store.baseMapMode == mode ? palette.accent : palette.border, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(store.baseMapMode == mode ? .isSelected : [])
            }
            if store.baseMapMode == .goianiaOffline {
                Text("Este snapshot offline não inclui escolas, cultura e locais de culto. Use o mapa mundial para esses grupos.")
                    .gameFont(.caption).foregroundStyle(palette.muted)
                Button {
                    store.showOfflineCoverage()
                    dismiss()
                } label: {
                    Label("Ver região offline", systemImage: "viewfinder")
                        .frame(maxWidth: .infinity, minHeight: 44)
                }
                .buttonStyle(.bordered)
                Text("Fora do recorte, use o mapa mundial. Enquadrar a região não encerra o GPS nem a navegação.")
                    .gameFont(.caption).foregroundStyle(palette.muted)
            }
        }
    }

    private var categories: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("O que aparece no mapa").gameFont(.headline)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(PoiCategory.allCases) { category in
                    let enabled = store.enabledCategories.contains(category.id)
                    Button { store.setCategory(category.id, enabled: !enabled) } label: {
                        HStack(spacing: 8) {
                            Image(systemName: category.symbol).frame(width: 23)
                            Text(category.title).gameFont(.subheadline, weight: .semibold).lineLimit(1)
                            Spacer(minLength: 0)
                            Image(systemName: enabled ? "checkmark.circle.fill" : "circle").font(.caption)
                        }
                        .foregroundStyle(palette.text).padding(12).frame(minHeight: 48)
                        .background(enabled ? palette.soft : palette.surface, in: RoundedRectangle(cornerRadius: 14))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(enabled ? palette.accent : palette.border, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("\(category.title), \(enabled ? "visível" : "oculto")")
                    .accessibilityAddTraits(enabled ? .isSelected : [])
                }
            }
        }
    }

    private var vehicleSelection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Seu marcador em movimento").gameFont(.headline)
            Text("O veículo muda com o tema e preserva sua rota.").gameFont(.caption).foregroundStyle(palette.muted)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 88))], spacing: 10) {
                ForEach(store.resources.vehicles) { vehicle in
                    let selected = store.navigationPreferences.vehicleId == vehicle.id
                    Button { store.setVehicle(vehicle.id) } label: {
                        VStack(spacing: 7) {
                            CatalogBitmap(url: store.resources.imageURL(for: vehicle, theme: store.theme), size: 58)
                            Text(vehicle.label).gameFont(.caption).lineLimit(2).multilineTextAlignment(.center)
                        }
                        .frame(maxWidth: .infinity, minHeight: 100).foregroundStyle(palette.text)
                        .background(selected ? palette.soft : Color.clear, in: RoundedRectangle(cornerRadius: 15))
                        .overlay(RoundedRectangle(cornerRadius: 15).stroke(selected ? palette.accent : palette.border, lineWidth: selected ? 2 : 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Usar \(vehicle.label)")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
        }
    }

    private var mapPreferences: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Preferências do mapa").gameFont(.headline)
            Toggle("Mostrar locais salvos", isOn: Binding(
                get: { store.navigationPreferences.savedPlacesVisible },
                set: { store.setSavedVisible($0) }
            ))
            Button { store.toggleOrientation() } label: {
                HStack {
                    Label("Orientação", systemImage: "location.north.line")
                    Spacer()
                    Text(store.navigationPreferences.orientation == .northUp ? "Norte para cima" : "Direção do movimento")
                        .gameFont(.caption).foregroundStyle(palette.muted)
                }.frame(minHeight: 44)
            }.buttonStyle(.plain)
        }
        .padding(16).background(palette.soft, in: RoundedRectangle(cornerRadius: 18))
    }

    private var markerCatalog: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Ícones dos seus lugares").gameFont(.headline)
            Text("Escolha um destes ícones ao criar ou editar um favorito.").gameFont(.caption).foregroundStyle(palette.muted)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 72))], spacing: 14) {
                ForEach(store.resources.saved) { icon in
                    VStack(spacing: 5) {
                        CatalogBitmap(url: store.resources.imageURL(for: icon, theme: store.theme), size: 42)
                        Text(icon.label).gameFont(.caption2).lineLimit(2).multilineTextAlignment(.center)
                    }.frame(maxWidth: .infinity, minHeight: 72)
                }
            }
        }
    }
}
