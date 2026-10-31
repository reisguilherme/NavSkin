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

    func testNewThemeFontStacksAreRoutedWithoutBorrowingAnotherThemeFont() throws {
        let value = try GameAssetCatalog(data: Data(##"{"schemaVersion":1,"themes":{"smw":{"map":{"road":"NavSkinSmwRoad","place":"NavSkinSmwDisplay"}},"cyberpunk":{"ui":{"file":"game-assets/cyberpunk/fonts/display.otf"},"map":{"poi":"NavSkinCyberpunkDisplay"}}}}"##.utf8))
        XCTAssertEqual(value.fontStacks, ["NavSkinSmwRoad", "NavSkinSmwDisplay", "NavSkinCyberpunkDisplay"])
        XCTAssertNil(value.themes["smw"]?.ui)
        XCTAssertEqual(value.themes["cyberpunk"]?.ui?.file, "game-assets/cyberpunk/fonts/display.otf")
        let local = URL(fileURLWithPath: "/resources/maps/glyphs/NavSkinSmwRoad/0-255.pbf")
        let router = GameGlyphRouter(stacks: value.fontStacks) { $0 == "maps/glyphs/NavSkinSmwRoad/0-255.pbf" ? local : nil }
        XCTAssertEqual(router.transformGlyphURL(URL(string: "https://tiles.openfreemap.org/fonts/NavSkinSmwRoad/0-255.pbf")!), local)
    }

    func testEveryNewThemeRejectsUnsafeFontsAndGlyphAliases() {
        for theme in ["smw", "cyberpunk", "pipboy", "hollow", "crossy"] {
            for file in ["../../font.ttf", "game-assets/\(theme)/fonts/../font.ttf", "game-assets/gta/fonts/pricedow.ttf"] {
                XCTAssertThrowsError(try GameAssetCatalog(data: Data("{\"schemaVersion\":1,\"themes\":{\"\(theme)\":{\"ui\":{\"file\":\"\(file)\"}}}}".utf8)), theme)
            }
            XCTAssertThrowsError(try GameAssetCatalog(data: Data("{\"schemaVersion\":1,\"themes\":{\"\(theme)\":{\"map\":{\"road\":\"../outside\"}}}}".utf8)), theme)
        }
    }

    func testPipboyHollowAndCrossyProfilesExposeDeclaredGlyphsAndOptionalFonts() throws {
        let value = try GameAssetCatalog(data: Data(##"{"schemaVersion":1,"themes":{"pipboy":{"ui":{"file":"game-assets/pipboy/fonts/BlenderPro-Bold.ttf"},"map":{"road":"NavSkinCyberpunk"}},"hollow":{"ui":{"file":"game-assets/hollow/fonts/HapnaSlabSerif-DemiBold.ttf"},"map":{"road":"NavSkinRdrMap"}},"crossy":{"map":{"road":"GameMapSans"}}}}"##.utf8))
        XCTAssertEqual(value.fontStacks, ["NavSkinCyberpunk", "NavSkinRdrMap", "GameMapSans"])
        XCTAssertEqual(value.themes["pipboy"]?.ui?.file, "game-assets/pipboy/fonts/BlenderPro-Bold.ttf")
        XCTAssertEqual(value.themes["hollow"]?.ui?.file, "game-assets/hollow/fonts/HapnaSlabSerif-DemiBold.ttf")
        XCTAssertNil(value.themes["crossy"]?.ui)
        let local = URL(fileURLWithPath: "/resources/maps/glyphs/GameMapSans/0-255.pbf")
        let router = GameGlyphRouter(stacks: value.fontStacks) { $0 == "maps/glyphs/GameMapSans/0-255.pbf" ? local : nil }
        XCTAssertEqual(router.transformGlyphURL(URL(string: "https://tiles.openfreemap.org/fonts/GameMapSans/0-255.pbf")!), local)
    }

    func testRemovedThemeProfilesDoNotRegisterGlyphRoutes() throws {
        let value = try GameAssetCatalog(data: Data(##"{"schemaVersion":1,"themes":{"minecraft":{"map":{"road":"NavSkinMinecraft"}},"gta5":{"map":{"place":"NavSkinGta5"}}}}"##.utf8))
        XCTAssertTrue(value.fontStacks.isEmpty)
        let router = GameGlyphRouter(stacks: value.fontStacks) { _ in XCTFail("Removed theme glyphs must not load local assets"); return nil }
        for stack in ["NavSkinMinecraft", "NavSkinGta5"] {
            let url = URL(string: "https://tiles.openfreemap.org/fonts/\(stack)/0-255.pbf")!
            XCTAssertEqual(router.transformGlyphURL(url), url)
        }
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
