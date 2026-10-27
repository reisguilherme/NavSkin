import Foundation

public struct GameUiFont: Decodable, Equatable {
    public let file: String
    public let postScriptName: String?
}

public struct GameThemeAssets: Decodable {
    public let ui: GameUiFont?
    public let map: [String: String]?
}

/// Exact font-file references and custom glyph aliases supplied with the artwork package.
public struct GameAssetCatalog: Decodable {
    public let schemaVersion: Int
    public let themes: [String: GameThemeAssets]
    public static let empty = GameAssetCatalog(schemaVersion: 1, themes: [:])

    public init(data: Data) throws {
        let value = try JSONDecoder().decode(Self.self, from: data)
        guard value.schemaVersion == 1 else { throw CocoaError(.fileReadCorruptFile) }
        for (theme, entry) in value.themes {
            guard ["gta", "rosa", "rdr2"].contains(theme) else { continue }
            if let font = entry.ui {
                let pattern = "^game-assets/\(theme)/fonts/[A-Za-z0-9_.-]+\\.(ttf|otf)$"
                guard font.file.range(of: pattern, options: .regularExpression) != nil,
                      !font.file.contains("..") else { throw CocoaError(.fileReadCorruptFile) }
            }
            for (role, stack) in entry.map ?? [:] where ["road", "poi", "place"].contains(role) {
                guard stack.range(of: "^[A-Za-z0-9_-]{1,64}$", options: .regularExpression) != nil else {
                    throw CocoaError(.fileReadCorruptFile)
                }
            }
        }
        self = value
    }

    private init(schemaVersion: Int, themes: [String: GameThemeAssets]) {
        self.schemaVersion = schemaVersion; self.themes = themes
    }

    public static func load(root: URL) -> GameAssetCatalog {
        guard let data = try? Data(contentsOf: root.appendingPathComponent("game-assets/catalog.json")),
              let value = try? GameAssetCatalog(data: data) else { return .empty }
        return value
    }

    public var fontStacks: Set<String> {
        Set(themes.filter { ["gta", "rosa", "rdr2"].contains($0.key) }.values.flatMap { entry in
            (entry.map ?? [:]).filter { ["road", "poi", "place"].contains($0.key) }.map(\.value)
        })
    }
}

/// Resolves only declared OpenFreeMap custom font stacks. Other requests stay untouched.
public final class GameGlyphRouter {
    private let stacks: Set<String>
    private let localResource: (String) -> URL?

    public init(stacks: Set<String>, localResource: @escaping (String) -> URL?) {
        self.stacks = stacks; self.localResource = localResource
    }

    public func transformGlyphURL(_ url: URL) -> URL {
        guard url.scheme == "https", url.host?.lowercased() == "tiles.openfreemap.org", url.port == nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil else { return url }
        let parts = url.path.split(separator: "/").map(String.init)
        guard parts.count == 3, parts[0] == "fonts", stacks.contains(parts[1]),
              url.path == "/fonts/\(parts[1])/\(parts[2])",
              parts[2].range(of: "^[0-9]+-[0-9]+\\.pbf$", options: .regularExpression) != nil else { return url }
        let range = String(parts[2].dropLast(4)).split(separator: "-")
        guard range.count == 2, let start = Int(range[0]), let end = Int(range[1]),
              (0...0x10ff00).contains(start), start % 256 == 0, end == start + 255 else { return url }
        if let local = localResource("maps/glyphs/\(parts[1])/\(parts[2])") { return local }
        return URL(string: "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/\(parts[2])") ?? url
    }
}
