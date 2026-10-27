import XCTest
import UIKit
@testable import NavSkin

/// Apple-only checks: run with the NavSkin scheme on a real iOS simulator/device.
final class NavSkinPlatformTests: XCTestCase {
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
    func testSixNativeStylesResolveBundleURLsAndPreserveTileTemplates() throws {
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
