import XCTest
import UIKit
@testable import NavSkin

/// Apple-only checks: run with the NavSkin scheme on a real iOS simulator/device.
final class NavSkinPlatformTests: XCTestCase {
    func testEveryThemeAndCoverageStartsWithControllableHiddenBuildings() throws {
        XCTAssertEqual(MapTheme.allCases.map(\.rawValue), ["gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy"])
        let root = try XCTUnwrap(Bundle.main.resourceURL).appendingPathComponent("Resources")
        let repository = MapResourceRepository(root: root)
        for theme in MapTheme.allCases { for mode in MapBaseMode.allCases { for night in [false, true] {
            let json = try repository.styleJSON(theme: theme, night: night, baseMapMode: mode)
            let style = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
            let layers = try XCTUnwrap(style["layers"] as? [[String: Any]])
            let buildings = layers.filter { $0["type"] as? String == "fill-extrusion" }
            XCTAssertFalse(buildings.isEmpty, "3D missing for \(theme.id), \(mode.id), night: \(night)")
            for layer in buildings {
                XCTAssertTrue((layer["id"] as? String)?.hasPrefix("navskin-3d-") == true)
                XCTAssertEqual((layer["layout"] as? [String: Any])?["visibility"] as? String, "none")
            }
        } } }
    }
    @MainActor
    func testConfiguredGameFontsRegisterTheirActualPostScriptNamesAndDoNotLeakAcrossThemes() throws {
        for theme in MapTheme.allCases {
            if let definition = GameTypography.catalog.themes[theme.rawValue]?.ui {
                let actual = try XCTUnwrap(GameTypography.fontName(theme: theme), "Configured font must load: \(definition.file)")
                XCTAssertNotNil(UIFont(name: actual, size: 17))
                if let expected = definition.postScriptName { XCTAssertEqual(actual, expected) }
            } else {
                XCTAssertNil(GameTypography.fontName(theme: theme), "A theme without a supplied font must keep its own system font")
            }
        }
    }
    func testEveryThemeCatalogImageAndNativePreviewLoadsInUIKit() throws {
        let root = try XCTUnwrap(Bundle.main.resourceURL).appendingPathComponent("Resources")
        let resources = ResourceCatalog(root: root)
        XCTAssertNil(resources.loadError)
        XCTAssertEqual(resources.saved.count, 12)
        XCTAssertEqual(resources.vehicles.count, 6)
        for theme in MapTheme.allCases {
            XCTAssertNotNil(UIImage(contentsOfFile: resources.previewURL(theme: theme).path))
            for icon in resources.saved + resources.vehicles {
                XCTAssertNotNil(UIImage(contentsOfFile: try XCTUnwrap(resources.imageURL(for: icon, theme: theme)).path))
            }
        }
    }
    func testAllNativeStylesResolveBundleURLsAndPreserveTileTemplates() throws {
        let root = try XCTUnwrap(Bundle.main.resourceURL).appendingPathComponent("Resources")
        let repository = MapResourceRepository(root: root)
        for theme in MapTheme.allCases { for night in [false, true] {
            let json = try repository.styleJSON(theme: theme, night: night, baseMapMode: .goianiaOffline)
            XCTAssertFalse(json.contains("asset://"))
            let style = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
            let sources = try XCTUnwrap(style["sources"] as? [String: [String: Any]])
            let tiles = try XCTUnwrap(sources["buildings"]?["tiles"] as? [String])
            XCTAssertTrue(tiles[0].contains("{z}/{x}/{y}.pbf"))
            XCTAssertTrue((style["glyphs"] as? String)?.contains("{fontstack}/{range}.pbf") == true)
            XCTAssertFalse(json.contains("%7B"))
            XCTAssertTrue(json.contains("file:"))
        } }
    }
    func testDeviceKeychainRoundTripUsesAnIndependentNamespace() throws {
        let keychain = APIKeyStore(service: "br.com.navskin.ios.platform-tests", account: UUID().uuidString)
        defer { try? keychain.remove() }
        XCTAssertEqual(try keychain.read(), "")
        try keychain.save("local-test-sentinel-never-sent-to-network")
        XCTAssertEqual(try keychain.read(), "local-test-sentinel-never-sent-to-network")
        try keychain.save("replacement")
        XCTAssertEqual(try keychain.read(), "replacement")
        try keychain.remove()
        XCTAssertEqual(try keychain.read(), "")
    }
    func testWorldIsDefaultAndRemoteTemplatesArePreservedForEveryTheme() throws {
        let root = try XCTUnwrap(Bundle.main.resourceURL).appendingPathComponent("Resources")
        let repository = MapResourceRepository(root: root)
        for theme in MapTheme.allCases { for night in [false, true] {
            let json = try repository.styleJSON(theme: theme, night: night)
            let explicitWorld = try repository.styleJSON(theme: theme, night: night, baseMapMode: .world)
            XCTAssertEqual(json, explicitWorld)
            let style = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(json.utf8)) as? [String: Any])
            let sources = try XCTUnwrap(style["sources"] as? [String: [String: Any]])
            XCTAssertEqual(sources["world"]?["url"] as? String, "https://tiles.openfreemap.org/planet")
            XCTAssertEqual(style["glyphs"] as? String, "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf")
            XCTAssertFalse(json.contains("asset://"))
            XCTAssertFalse(json.contains("%7B"))
            let offline = try repository.styleJSON(theme: theme, night: night, baseMapMode: .goianiaOffline)
            XCTAssertNotEqual(json, offline)
            XCTAssertEqual(try repository.styleJSON(theme: theme, night: night), json)
        } }
    }
}
