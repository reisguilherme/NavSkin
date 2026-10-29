import Foundation

/// Checks the actual Foundation-only app resources/repository and generated OpenStep project.
/// Compile together with AppTypes.swift and MapResourceRepository.swift, without Apple UI modules.
@main
enum ProjectProbe {
    static func main() throws {
        guard CommandLine.arguments.count == 2 else { throw ProbeError.failed("Pass the ios directory") }
        let ios = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
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
                let font = layers.compactMap { ($0["layout"] as? [String: Any])?["text-font"] as? [String] }.first!.joined(separator: ",")
                let glyph = (style["glyphs"] as! String).replacingOccurrences(of: "{fontstack}", with: font)
                    .replacingOccurrences(of: "{range}", with: "0-255")
                try exists(URL(string: glyph)!)
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
            try require(settings["MARKETING_VERSION"] as? String == "0.8.0", "Marketing version")
            try require(settings["CURRENT_PROJECT_VERSION"] as? String == "1", "Build version")
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
        print("PASS: actual resource resolver, six worldwide + six offline styles, world default, mode cache, all catalog previews, OpenStep project references and XML property lists")
    }
    private static func exists(_ url: URL) throws { try require(url.isFileURL && FileManager.default.fileExists(atPath: url.path), "Missing file: \(url.lastPathComponent)") }
    private static func require(_ condition: Bool, _ message: String) throws { if !condition { throw ProbeError.failed(message) } }
    private enum ProbeError: Error { case failed(String) }
}
