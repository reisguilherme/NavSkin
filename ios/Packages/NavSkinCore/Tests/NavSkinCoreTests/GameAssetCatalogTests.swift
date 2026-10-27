import Foundation
import XCTest
@testable import NavSkinCore

final class GameAssetCatalogTests: XCTestCase {
    private let alias = "NavSkinGtaDisplay"
    private var prefix: String { "https://tiles.openfreemap.org/fonts/\(alias)/" }

    func testCatalogReadsExactReferencesAndDoesNotInventOtherThemeFonts() throws {
        let value = try GameAssetCatalog(data: Data(##"{"schemaVersion":1,"themes":{"gta":{"ui":{"file":"game-assets/gta/fonts/pricedow.ttf","postScriptName":"Pricedown"},"map":{"road":"NavSkinGtaRoad","poi":"NavSkinGtaDisplay","place":"NavSkinGtaDisplay"}}}}"##.utf8))
        XCTAssertEqual(value.themes["gta"]?.ui?.postScriptName, "Pricedown")
        XCTAssertNil(value.themes["rosa"])
        XCTAssertEqual(value.fontStacks, ["NavSkinGtaRoad", "NavSkinGtaDisplay"])
    }

    func testCatalogRejectsTraversalAndUnknownSchema() {
        for file in ["../../font.ttf", "game-assets/gta/fonts/../font.ttf", "file:///font.ttf"] {
            XCTAssertThrowsError(try GameAssetCatalog(data: Data("{\"schemaVersion\":1,\"themes\":{\"gta\":{\"ui\":{\"file\":\"\(file)\"}}}}".utf8)))
        }
        XCTAssertThrowsError(try GameAssetCatalog(data: Data(##"{"schemaVersion":2,"themes":{}}"##.utf8)))
    }

    func testBundledFontBlockUsesLocalResourceAndMissingBlockUsesGlobalNoto() {
        let local = URL(fileURLWithPath: "/resources/gta/0-255.pbf")
        let router = GameGlyphRouter(stacks: [alias]) { $0 == "maps/glyphs/\(self.alias)/0-255.pbf" ? local : nil }
        XCTAssertEqual(router.transformGlyphURL(URL(string: prefix + "0-255.pbf")!), local)
        XCTAssertEqual(router.transformGlyphURL(URL(string: prefix + "19968-20223.pbf")!).absoluteString,
                       "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/19968-20223.pbf")
    }

    func testUnrelatedServersAndResourcesAreNotIntercepted() {
        let router = GameGlyphRouter(stacks: [alias]) { _ in XCTFail("Unrelated URL probed local assets"); return nil }
        for text in ["https://tiles.openfreemap.org/planet/14/6069/9295.pbf",
                     "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/0-255.pbf",
                     "https://api.geoapify.com/v1/routing?apiKey=fixture",
                     "https://other.example/fonts/\(alias)/0-255.pbf", "file:///maps/glyphs/\(alias)/0-255.pbf"] {
            let url = URL(string: text)!
            XCTAssertEqual(router.transformGlyphURL(url), url)
        }
    }

    func testInvalidRangesCredentialsAndParametersCannotBecomeLocalPaths() {
        let router = GameGlyphRouter(stacks: [alias]) { _ in XCTFail("Invalid request probed local assets"); return nil }
        for tail in ["../0-255.pbf", "0-254.pbf", "1-256.pbf", "1114112-1114367.pbf", "0-255.pbf?token=x", "0-255.pbf#x"] {
            let url = URL(string: prefix + tail)!
            XCTAssertEqual(router.transformGlyphURL(url), url)
        }
        let url = URL(string: "https://user@tiles.openfreemap.org/fonts/\(alias)/0-255.pbf")!
        XCTAssertEqual(router.transformGlyphURL(url), url)
    }
}
