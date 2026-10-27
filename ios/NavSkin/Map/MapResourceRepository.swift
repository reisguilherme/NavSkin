import Foundation

enum MapResourceError: Error, LocalizedError {
    case missingStyle, invalidStyle, unsafeAsset, missingCategories
    var errorDescription: String? {
        switch self {
        case .missingStyle: return "Estilo do mapa não encontrado. Prepare os recursos do aplicativo."
        case .invalidStyle: return "Não foi possível ler o estilo do mapa."
        case .unsafeAsset: return "O estilo contém um caminho de recurso inválido."
        case .missingCategories: return "O estilo não contém os seis filtros de lugares."
        }
    }
}

/// Resolves bundled assets without an HTTP server, preserving raw tile/glyph placeholders.
final class MapResourceRepository {
    let root: URL
    private var cached: [String: [String: Any]] = [:]
    static let emptyStyle = ##"{"version":8,"name":"Mapa indisponível","sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#E8E4D9"}}]}"##

    init(root: URL) { self.root = root }

    func styleJSON(theme: MapTheme, night: Bool, baseMapMode: MapBaseMode = .world) throws -> String {
        let variant = (baseMapMode == .world ? "world-" : "") + theme.rawValue + (night ? "-night" : "")
        var style: [String: Any]
        if let saved = cached[variant] { style = saved }
        else {
            let url = root.appendingPathComponent("maps/styles/\(variant).json")
            guard FileManager.default.fileExists(atPath: url.path) else { throw MapResourceError.missingStyle }
            guard let dictionary = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any],
                  dictionary["version"] as? Int == 8 else { throw MapResourceError.invalidStyle }
            style = dictionary
            cached[variant] = dictionary
        }
        for key in ["sprite", "glyphs"] {
            if let value = style[key] as? String { style[key] = try resolve(value) }
        }
        guard var sources = style["sources"] as? [String: [String: Any]],
              let layers = style["layers"] as? [[String: Any]] else { throw MapResourceError.invalidStyle }
        for (identifier, var source) in sources {
            for key in ["url", "data"] {
                if let value = source[key] as? String { source[key] = try resolve(value) }
            }
            if let tiles = source["tiles"] as? [String] { source["tiles"] = try tiles.map(resolve) }
            sources[identifier] = source
        }
        var found: Set<String> = []
        for index in layers.indices {
            guard let id = layers[index]["id"] as? String,
                  let category = PoiCategory.allCases.first(where: { id == "poi-" + $0.id }) else { continue }
            found.insert(category.id)
        }
        guard found == Set(PoiCategory.allCases.map(\.id)) else { throw MapResourceError.missingCategories }
        style["sources"] = sources
        style["layers"] = layers
        let data = try JSONSerialization.data(withJSONObject: style, options: [.sortedKeys])
        guard let text = String(data: data, encoding: .utf8) else { throw MapResourceError.invalidStyle }
        return text
    }

    private func resolve(_ value: String) throws -> String {
        guard value.hasPrefix("asset://") else { return value }
        let relative = String(value.dropFirst("asset://".count))
        guard relative.hasPrefix("maps/"), !relative.contains(".."), !relative.contains(":"),
              !relative.contains("\\"), !relative.contains("%") else { throw MapResourceError.unsafeAsset }
        let prefix = root.absoluteString.hasSuffix("/") ? root.absoluteString : root.absoluteString + "/"
        // Appending a template with URL.appendingPathComponent would encode its braces.
        return prefix + relative
    }
}
