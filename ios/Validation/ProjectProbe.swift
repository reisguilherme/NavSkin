import Foundation

/// Checks the actual Foundation-only app resources/repository and generated OpenStep project.
/// Compile together with AppTypes.swift and MapResourceRepository.swift, without Apple UI modules.
@main
enum ProjectProbe {
    static func main() throws {
        guard CommandLine.arguments.count == 2 else { throw ProbeError.failed("Pass the ios directory") }
        let ios = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
        let expectedThemes = ["gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy"]
        try require(MapTheme.allCases.map(\.rawValue) == expectedThemes, "Exactly eight active themes must be selectable; preserve saved IDs")
        for id in expectedThemes {
            let decoded = try JSONDecoder().decode(MapTheme.self, from: Data("\"\(id)\"".utf8))
            try require(decoded.rawValue == id, "Stored theme ID changed")
            let encoded = try JSONEncoder().encode(decoded)
            try require(String(data: encoded, encoding: .utf8) == "\"\(id)\"", "Theme persistence changed")
        }
        for removedID in ["minecraft", "gta5"] {
            try require(MapTheme(rawValue: removedID) == nil, "Removed theme must not remain selectable")
            try require((MapTheme(rawValue: removedID) ?? .gta) == .gta, "Removed theme preference must fall back to GTA SA")
        }
        try validateIconCatalogContract()
        try require(PoiCategory.allCases.count == 9, "Nine categories required")
        try require(PoiCategory.migratedSelection(nil, schemaVersion: 0) == Set(PoiCategory.allCases.map(\.id)), "New install categories")
        try require(PoiCategory.migratedSelection(["health"], schemaVersion: 0) == ["health", "education", "culture", "religion"], "Legacy category migration")
        try require(PoiCategory.migratedSelection([], schemaVersion: 0).isEmpty, "Explicit all-off migration")
        try require(PoiCategory.migratedSelection(["health"], schemaVersion: 2) == ["health"], "Category migration must run once")
        let root = ios.appendingPathComponent("NavSkin/Resources")
        let resources = ResourceCatalog(root: root)
        try require(resources.loadError == nil, "Catalog failed to load")
        try require(resources.saved.count == 12 && resources.vehicles.count == 6, "Catalog size")
        let repo = MapResourceRepository(root: root)
        for theme in MapTheme.allCases {
            try exists(resources.previewURL(theme: theme))
            for icon in resources.saved + resources.vehicles {
                guard let url = resources.imageURL(for: icon, theme: theme) else { throw ProbeError.failed("Catalog path") }
                try exists(url)
            }
            for night in [false, true] {
                let text = try repo.styleJSON(theme: theme, night: night, baseMapMode: .goianiaOffline)
                let style = try JSONSerialization.jsonObject(with: Data(text.utf8)) as! [String: Any]
                try require(!text.contains("asset://") && !text.contains("%7B"), "Unresolved or escaped URL")
                let sprite = style["sprite"] as! String
                try exists(URL(string: sprite + ".png")!)
                try exists(URL(string: sprite + ".json")!)
                let layers = style["layers"] as! [[String: Any]]
                try validateDimensionLayers(layers)
                let fonts = Set(layers.compactMap { ($0["layout"] as? [String: Any])?["text-font"] as? [String] }.map { $0.joined(separator: ",") })
                try require(!fonts.isEmpty, "Map labels must declare a font stack")
                for font in fonts {
                    let glyph = (style["glyphs"] as! String).replacingOccurrences(of: "{fontstack}", with: font)
                        .replacingOccurrences(of: "{range}", with: "0-255")
                    try exists(URL(string: glyph)!)
                }
                for (_, source) in style["sources"] as! [String: [String: Any]] {
                    if let data = source["data"] as? String { try exists(URL(string: data)!) }
                    if let tiles = source["tiles"] as? [String] { try require(tiles[0].contains("{z}/{x}/{y}.pbf"), "Tile template lost") }
                }
                // Default coverage is worldwide. Remote URLs must survive local asset resolution unchanged.
                let worldText = try repo.styleJSON(theme: theme, night: night)
                let explicitWorld = try repo.styleJSON(theme: theme, night: night, baseMapMode: .world)
                try require(worldText == explicitWorld, "Default mode must be world")
                let worldStyle = try JSONSerialization.jsonObject(with: Data(worldText.utf8)) as! [String: Any]
                let worldSources = worldStyle["sources"] as! [String: [String: Any]]
                try require(worldSources["world"]?["url"] as? String == "https://tiles.openfreemap.org/planet", "World TileJSON URL changed")
                try require(worldStyle["glyphs"] as? String == "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf", "World glyph URL changed")
                try require(!worldText.contains("asset://") && !worldText.contains("%7B"), "Unresolved world asset or escaped URL")
                let worldSprite = worldStyle["sprite"] as! String
                try exists(URL(string: worldSprite + ".png")!)
                try exists(URL(string: worldSprite + ".json")!)
                let worldLayers = worldStyle["layers"] as! [[String: Any]]
                try validateDimensionLayers(worldLayers)
                let categoryIDs = Set(worldLayers.compactMap { $0["id"] as? String }.filter { $0.hasPrefix("poi-") })
                try require(Set(PoiCategory.allCases.map { "poi-" + $0.id }).isSubset(of: categoryIDs), "World POI filters missing")
                // Read offline again after world to catch a cache key shared between map modes.
                let offlineAgain = try repo.styleJSON(theme: theme, night: night, baseMapMode: .goianiaOffline)
                try require(offlineAgain == text, "Mode cache mixed world and offline styles")
            }
        }
        var format = PropertyListSerialization.PropertyListFormat.openStep
        let projectData = try Data(contentsOf: ios.appendingPathComponent("NavSkin.xcodeproj/project.pbxproj"))
        let project = try PropertyListSerialization.propertyList(from: projectData, options: [], format: &format) as! [String: Any]
        let objects = project["objects"] as! [String: [String: Any]]
        let rootObject = objects[project["rootObject"] as! String]!
        try require(rootObject["isa"] as? String == "PBXProject", "Project root")
        try require((rootObject["targets"] as? [String])?.count == 2, "App and test targets")
        let targets = objects.values.filter { $0["isa"] as? String == "PBXNativeTarget" }
        try require(Set(targets.compactMap { $0["name"] as? String }) == ["NavSkin", "NavSkinTests"], "Renamed target names")
        let corePackage = objects.values.first { $0["isa"] as? String == "XCLocalSwiftPackageReference" }
        try require(corePackage?["relativePath"] as? String == "Packages/NavSkinCore", "Renamed local package")
        for object in objects.values where object["isa"] as? String == "XCBuildConfiguration" {
            guard let settings = object["buildSettings"] as? [String: Any], settings["PRODUCT_NAME"] as? String == "NavSkin" else { continue }
            try require(settings["PRODUCT_MODULE_NAME"] as? String == "NavSkin", "Renamed app module")
            try require(settings["MARKETING_VERSION"] as? String == "0.12.0", "Marketing version")
            try require(settings["CURRENT_PROJECT_VERSION"] as? String == "12", "Build version")
        }
        for (_, object) in objects where object["isa"] as? String == "PBXFileReference" {
            if object["sourceTree"] as? String == "<group>", let path = object["path"] as? String {
                try exists(ios.appendingPathComponent(path))
            }
        }
        for (_, object) in objects where object["isa"] as? String == "PBXBuildFile" {
            let reference = (object["fileRef"] ?? object["productRef"]) as! String
            try require(objects[reference] != nil, "Build-file reference")
        }
        for file in ["NavSkin/Info.plist", "NavSkin/PrivacyInfo.xcprivacy"] {
            _ = try PropertyListSerialization.propertyList(from: Data(contentsOf: ios.appendingPathComponent(file)), options: [], format: nil)
        }
        let variantsPerCoverage = MapTheme.allCases.count * 2
        print("PASS: actual resource resolver, \(variantsPerCoverage) worldwide + \(variantsPerCoverage) offline styles, eight persisted themes and removed-theme fallback, hidden 3D layers, world default, mode cache, all catalog previews, OpenStep project references and XML property lists")
    }
    private static func exists(_ url: URL) throws { try require(url.isFileURL && FileManager.default.fileExists(atPath: url.path), "Missing file: \(url.lastPathComponent)") }
    private static func require(_ condition: Bool, _ message: String) throws { if !condition { throw ProbeError.failed(message) } }
    private enum ProbeError: Error { case failed(String) }

    private static func validateDimensionLayers(_ layers: [[String: Any]]) throws {
        let extrusions = layers.filter { $0["type"] as? String == "fill-extrusion" }
        try require(!extrusions.isEmpty, "Each theme must include 3D buildings")
        for layer in extrusions {
            try require((layer["id"] as? String)?.hasPrefix("navskin-3d-") == true, "3D layer cannot be controlled by runtime")
            try require((layer["layout"] as? [String: Any])?["visibility"] as? String == "none", "3D layers must start hidden for flat browsing")
        }
    }

    private static func validateIconCatalogContract() throws {
        let temporary = FileManager.default.temporaryDirectory.appendingPathComponent("NavSkinCatalogProbe-" + UUID().uuidString)
        let navigation = temporary.appendingPathComponent("navigation-icons")
        try FileManager.default.createDirectory(at: navigation, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: temporary) }
        let themes = ["gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy"]
        let saved = ["home", "work", "favorite", "restaurant", "cafe", "park", "shopping", "gym", "health", "parking", "meeting", "special"]
        let vehicles = ["arrow", "compact", "sport", "suv", "motorcycle", "themed"]
        func icon(_ id: String, kind: String) -> [String: Any] {
            let assets = Dictionary(uniqueKeysWithValues: themes.map { theme in
                (theme, ["image": "\(theme)/\(kind)/\(id).png", "preview": "\(theme)/\(kind)/\(id)@2x.png", "svg": "\(theme)/\(kind)/\(id).svg"])
            })
            return ["id": id, "label": id, "themeAssets": assets]
        }
        let document: [String: Any] = ["schemaVersion": 1, "themes": themes,
            "saved": saved.map { icon($0, kind: "saved") }, "vehicles": vehicles.map { icon($0, kind: "vehicles") }]
        func catalog(_ data: [String: Any]) throws -> ResourceCatalog {
            try JSONSerialization.data(withJSONObject: data).write(to: navigation.appendingPathComponent("catalog.json"))
            return ResourceCatalog(root: temporary)
        }
        let complete = try catalog(document)
        try require(complete.loadError == nil, "Complete eight-theme catalog rejected")
        try require(complete.savedIcon(id: "legacy-unknown")?.id == "special", "Legacy saved icon fallback changed")
        try require(complete.vehicleIcon(id: "legacy-unknown")?.id == "arrow", "Legacy vehicle fallback changed")
        for kind in ["saved", "vehicles"] {
            var missingSemanticID = document
            var icons = document[kind] as! [[String: Any]]
            icons[0]["id"] = "replacement"
            missingSemanticID[kind] = icons
            try require(try catalog(missingSemanticID).loadError != nil, "Required semantic icon ID missing")
            var duplicate = document
            icons[0] = icons[1]
            duplicate[kind] = icons
            try require(try catalog(duplicate).loadError != nil, "Duplicate semantic icon ID accepted")
        }
        for theme in MapTheme.allCases {
            let home = complete.savedIcon(id: "home")!
            let vehicle = complete.vehicleIcon(id: "compact")!
            try require(complete.imageURL(for: home, theme: theme)?.path.hasSuffix("\(theme.id)/saved/home@2x.png") == true, "Saved place semantic ID changed with theme")
            try require(complete.imageURL(for: vehicle, theme: theme, preview: false)?.path.hasSuffix("\(theme.id)/vehicles/compact.png") == true, "Vehicle semantic ID changed with theme")
        }
        for theme in ["smw", "cyberpunk", "pipboy", "hollow", "crossy"] {
            var missingTheme = document
            missingTheme["themes"] = themes.filter { $0 != theme }
            try require(try catalog(missingTheme).loadError != nil, "Missing declared theme must reject catalog")
            var incomplete = document
            var icons = incomplete["saved"] as! [[String: Any]]
            var assets = icons[0]["themeAssets"] as! [String: [String: String]]
            assets.removeValue(forKey: theme)
            icons[0]["themeAssets"] = assets
            incomplete["saved"] = icons
            try require(try catalog(incomplete).loadError != nil, "Missing theme artwork must reject catalog")
        }
        for path in ["../outside.png", "gta/saved/home.png", "smw/saved/%2e%2e/outside.png", "smw\\saved\\home.png"] {
            var unsafe = document
            var icons = unsafe["saved"] as! [[String: Any]]
            var assets = icons[0]["themeAssets"] as! [String: [String: String]]
            assets["smw"]?["preview"] = path
            icons[0]["themeAssets"] = assets
            unsafe["saved"] = icons
            try require(try catalog(unsafe).loadError != nil, "Unsafe or cross-theme icon path accepted")
        }
    }
}
