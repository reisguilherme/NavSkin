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
    case gta, rosa, rdr2
    var id: String { rawValue }
    var title: String { switch self { case .gta: return "GTA SA"; case .rosa: return "Rosa Pop"; case .rdr2: return "RDR2" } }
    var subtitle: String { switch self { case .gta: return "O clássico das ruas"; case .rosa: return "Cores doces, cidade viva"; case .rdr2: return "Papel, terra e horizonte" } }
    #if canImport(SwiftUI)
    var palette: ThemePalette {
        switch self {
        case .gta: return ThemePalette(surface: 0xFFFCF4, text: 0x252A1E, muted: 0x6A705D, accent: 0x4C692B, soft: 0xE8EEDB, border: 0xE0E4D6)
        case .rosa: return ThemePalette(surface: 0xFFF7FA, text: 0x542840, muted: 0x93647A, accent: 0xD92376, soft: 0xFFE2EE, border: 0xF7CADF)
        case .rdr2: return ThemePalette(surface: 0xFFF9EA, text: 0x483722, muted: 0x7D654C, accent: 0xA13228, soft: 0xF0E3C4, border: 0xDED0B0)
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
    case parks, fuel, food, parking, health, shops
    var id: String { rawValue }
    var title: String { switch self { case .parks: return "Parques"; case .fuel: return "Combustível"; case .food: return "Alimentação"; case .parking: return "Estacionamento"; case .health: return "Saúde"; case .shops: return "Mercados e conveniências" } }
    var symbol: String { switch self { case .parks: return "tree"; case .fuel: return "fuelpump"; case .food: return "fork.knife"; case .parking: return "parkingsign.circle"; case .health: return "cross.case"; case .shops: return "basket" } }
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
    private struct Document: Decodable { let schemaVersion: Int; let saved, vehicles: [CatalogIcon] }
    init(root: URL) {
        self.root = root
        do {
            let data = try Data(contentsOf: root.appendingPathComponent("navigation-icons/catalog.json"))
            let document = try JSONDecoder().decode(Document.self, from: data)
            guard document.schemaVersion == 1, document.saved.count >= 12, document.vehicles.count >= 6 else {
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
        guard !path.contains(".."), !path.contains(":"), !path.hasPrefix("/") else { return nil }
        return root.appendingPathComponent("navigation-icons").appendingPathComponent(path)
    }
    func previewURL(theme: MapTheme) -> URL { root.appendingPathComponent("maps/previews/\(theme.rawValue).png") }
}
