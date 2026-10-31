import Foundation
#if canImport(SwiftUI)
import SwiftUI
#endif

enum MapBaseMode: String, CaseIterable, Identifiable, Codable {
    case world, goianiaOffline
    var id: String { rawValue }
    var title: String {
        switch self {
        case .world: return "Mapa mundial (online)"
        case .goianiaOffline: return "Goiânia offline"
        }
    }
    var description: String {
        switch self {
        case .world: return "Explore cidades e estradas no mundo todo. O mapa precisa de internet."
        case .goianiaOffline: return "Recorte de Goiânia disponível sem internet. Busca e rotas continuam online."
        }
    }
}

enum MapTheme: String, CaseIterable, Identifiable, Codable {
    case gta, rosa, rdr2, smw, cyberpunk, pipboy, hollow, crossy
    var id: String { rawValue }
    var title: String {
        switch self {
        case .gta: return "GTA SA"
        case .rosa: return "Rosa Pop"
        case .rdr2: return "RDR2"
        case .smw: return "Super Mario World"
        case .cyberpunk: return "Cyberpunk 2077"
        case .pipboy: return "Pip-Boy"
        case .hollow: return "Hollow Knight"
        case .crossy: return "Crossy Road"
        }
    }
    var subtitle: String {
        switch self {
        case .gta: return "O clássico das ruas"
        case .rosa: return "Cores doces, cidade viva"
        case .rdr2: return "Papel, terra e horizonte"
        case .smw: return "Caminhos de uma aventura 16-bit"
        case .cyberpunk: return "Neon nas ruas da cidade"
        case .pipboy: return "Verde fósforo no seu caminho"
        case .hollow: return "Explore um reino em tons de azul"
        case .crossy: return "Quarteirões coloridos e caminhos em blocos"
        }
    }
    #if canImport(SwiftUI)
    var palette: ThemePalette {
        switch self {
        case .gta: return ThemePalette(surface: 0xFFFCF4, text: 0x252A1E, muted: 0x6A705D, accent: 0x4C692B, soft: 0xE8EEDB, border: 0xE0E4D6)
        case .rosa: return ThemePalette(surface: 0xFFF7FA, text: 0x542840, muted: 0x93647A, accent: 0xD92376, soft: 0xFFE2EE, border: 0xF7CADF)
        case .rdr2: return ThemePalette(surface: 0xFFF9EA, text: 0x483722, muted: 0x7D654C, accent: 0xA13228, soft: 0xF0E3C4, border: 0xDED0B0)
        case .smw: return ThemePalette(surface: 0xFFF9E4, text: 0x273A21, muted: 0x5B7048, accent: 0xD94734, soft: 0xE2F0B8, border: 0xC8D58E)
        case .cyberpunk: return ThemePalette(surface: 0x101E2C, text: 0xD8F8FC, muted: 0x9FC4CE, accent: 0x44D9E8, soft: 0x193949, border: 0x2C5668)
        case .pipboy: return ThemePalette(surface: 0x07170D, text: 0xB6F7A0, muted: 0x7BA76C, accent: 0x85ED63, soft: 0x18371C, border: 0x39633A)
        case .hollow: return ThemePalette(surface: 0x152037, text: 0xE1E8F5, muted: 0xA3B1CC, accent: 0x9ABCE8, soft: 0x263651, border: 0x455A7D)
        case .crossy: return ThemePalette(surface: 0xF5F8E8, text: 0x2C3C35, muted: 0x657660, accent: 0xD96932, soft: 0xE3EFC5, border: 0xC2D5A8)
        }
    }
    #endif
}

#if canImport(SwiftUI)
struct ThemePalette {
    let surface, text, muted, accent, soft, border: Color
    init(surface: UInt32, text: UInt32, muted: UInt32, accent: UInt32, soft: UInt32, border: UInt32) {
        self.surface = Color(rgb: surface); self.text = Color(rgb: text); self.muted = Color(rgb: muted)
        self.accent = Color(rgb: accent); self.soft = Color(rgb: soft); self.border = Color(rgb: border)
    }
}

extension Color {
    init(rgb: UInt32) { self.init(.sRGB, red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255, opacity: 1) }
}
#endif

enum PoiCategory: String, CaseIterable, Identifiable {
    case parks, fuel, food, parking, health, shops, education, culture, religion
    var id: String { rawValue }
    var title: String { switch self { case .parks: return "Parques"; case .fuel: return "Combustível"; case .food: return "Restaurantes, cafés e bares"; case .parking: return "Estacionamento"; case .health: return "Saúde"; case .shops: return "Mercados e conveniências"; case .education: return "Escolas e universidades"; case .culture: return "Museus, teatros e cultura"; case .religion: return "Locais de culto" } }
    var symbol: String { switch self { case .parks: return "tree"; case .fuel: return "fuelpump"; case .food: return "fork.knife"; case .parking: return "parkingsign.circle"; case .health: return "cross.case"; case .shops: return "basket"; case .education: return "graduationcap"; case .culture: return "theatermasks"; case .religion: return "building.2" } }
    static func migratedSelection(_ stored: [String]?, schemaVersion: Int) -> Set<String> {
        let all = Set(allCases.map(\.rawValue))
        guard let stored else { return all }
        var selected = Set(stored).intersection(all)
        if schemaVersion < 2 && !stored.isEmpty { selected.formUnion([education.rawValue, culture.rawValue, religion.rawValue]) }
        return selected
    }
}

enum MapCommandKind { case recenter, overview, offlineCoverage, zoom(Double) }
struct MapCommand { let id = UUID(); let kind: MapCommandKind }

struct CatalogAsset: Decodable { let image, preview, svg: String }
struct CatalogIcon: Decodable, Identifiable { let id, label: String; let themeAssets: [String: CatalogAsset] }

final class ResourceCatalog {
    let root: URL
    let saved: [CatalogIcon]
    let vehicles: [CatalogIcon]
    let loadError: String?
    private struct Document: Decodable { let schemaVersion: Int; let themes: [String]; let saved, vehicles: [CatalogIcon] }
    private static let savedIDs: Set<String> = ["home", "work", "favorite", "restaurant", "cafe", "park", "shopping", "gym", "health", "parking", "meeting", "special"]
    private static let vehicleIDs: Set<String> = ["arrow", "compact", "sport", "suv", "motorcycle", "themed"]
    init(root: URL) {
        self.root = root
        do {
            let data = try Data(contentsOf: root.appendingPathComponent("navigation-icons/catalog.json"))
            let document = try JSONDecoder().decode(Document.self, from: data)
            let themes = Set(MapTheme.allCases.map(\.rawValue))
            guard document.schemaVersion == 1, themes.isSubset(of: Set(document.themes)),
                  Self.validIcons(document.saved, requiredIDs: Self.savedIDs, themes: themes),
                  Self.validIcons(document.vehicles, requiredIDs: Self.vehicleIDs, themes: themes) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            saved = document.saved; vehicles = document.vehicles; loadError = nil
        } catch {
            saved = []; vehicles = []
            loadError = "Não foi possível abrir os recursos do mapa. Prepare os assets antes de compilar o aplicativo."
        }
    }
    func savedIcon(id: String) -> CatalogIcon? { saved.first { $0.id == id } ?? saved.first { $0.id == "special" } }
    func vehicleIcon(id: String) -> CatalogIcon? { vehicles.first { $0.id == id } ?? vehicles.first { $0.id == "arrow" } }
    func imageURL(for icon: CatalogIcon, theme: MapTheme, preview: Bool = true) -> URL? {
        guard let asset = icon.themeAssets[theme.rawValue] else { return nil }
        return navigationURL(preview ? asset.preview : asset.image)
    }
    private func navigationURL(_ path: String) -> URL? {
        guard Self.safePath(path) else { return nil }
        return root.appendingPathComponent("navigation-icons").appendingPathComponent(path)
    }
    private static func validIcons(_ icons: [CatalogIcon], requiredIDs: Set<String>, themes: Set<String>) -> Bool {
        let ids = Set(icons.map(\.id))
        guard ids.count == icons.count, requiredIDs.isSubset(of: ids) else { return false }
        return icons.allSatisfy { icon in
            themes.allSatisfy { theme in
                guard let asset = icon.themeAssets[theme] else { return false }
                return [asset.image, asset.preview, asset.svg].allSatisfy {
                    safePath($0) && $0.hasPrefix(theme + "/")
                }
            }
        }
    }
    private static func safePath(_ path: String) -> Bool {
        !path.isEmpty && !path.contains("..") && !path.contains(":") && !path.hasPrefix("/") &&
            !path.contains("\\") && !path.contains("%")
    }
    func previewURL(theme: MapTheme) -> URL { root.appendingPathComponent("maps/previews/\(theme.rawValue).png") }
}
