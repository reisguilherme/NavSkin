import Foundation
import XCTest
@testable import NavSkinCore

final class NavigationAvatarTests: XCTestCase {
    private func document() -> [String: Any] {
        ["schemaVersion": 1, "frameCount": 32, "pitches": [0, 40, 55],
         "imageSize": 192, "displaySize": 72, "anchor": [0.5, 0.7],
         "themes": ["gta": ["label": "CJ · BMX", "frames": [0, 40, 55].map { pitch in
             (0..<32).map { "gta/p\(pitch)/a\(String(format: "%02d", $0)).png" }
         }]]]
    }

    private func catalog(_ document: [String: Any]? = nil) throws -> NavigationAvatarCatalog {
        try NavigationAvatarCatalog(data: JSONSerialization.data(withJSONObject: document ?? self.document()))
    }

    func testHeadingUsesCameraRelativeViewAndWrapsAcrossNorth() throws {
        let value = try catalog()
        XCTAssertEqual(value.frame(theme: "gta", heading: 90, cameraBearing: 90, pitch: 48)?.path, "gta/p55/a00.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 90, cameraBearing: 0, pitch: 40)?.path, "gta/p40/a08.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 0, cameraBearing: 90, pitch: 40)?.path, "gta/p40/a24.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 359, cameraBearing: 1, pitch: 40)?.path, "gta/p40/a00.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: -11.25, cameraBearing: 0, pitch: 40)?.path, "gta/p40/a31.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 720, cameraBearing: -360, pitch: 40)?.path, "gta/p40/a00.png")
    }

    func testPitchBucketsAndSubframeMotionStayStable() throws {
        let value = try catalog()
        XCTAssertEqual(value.frame(theme: "gta", heading: 0, cameraBearing: 0, pitch: 0)?.path, "gta/p0/a00.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 0, cameraBearing: 0, pitch: 44)?.path, "gta/p40/a00.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 0, cameraBearing: 0, pitch: 70)?.path, "gta/p55/a00.png")
        XCTAssertEqual(value.frame(theme: "gta", heading: 2, cameraBearing: 0, pitch: 42),
                       value.frame(theme: "gta", heading: 4, cameraBearing: 0, pitch: 44))
        XCTAssertEqual(value.frame(theme: "gta", heading: 6, cameraBearing: 0, pitch: 40)?.path, "gta/p40/a01.png")
    }

    func testGroundAnchorAndSizeAreIndependentOfFrame() throws {
        let value = try catalog()
        XCTAssertEqual(value.iconScale, 0.375, accuracy: 0.0001)
        XCTAssertEqual(value.offsetX, 0, accuracy: 0.0001)
        XCTAssertEqual(value.offsetY, -38.4, accuracy: 0.0001)
        // MapLibre scales the icon offset by iconScale, keeping the ground at the GPS point.
        XCTAssertEqual(value.offsetY * value.iconScale, -14.4, accuracy: 0.0001)
    }

    func testOnlyNavigationAndVibingReplaceTheChosenBrowsingIcon() {
        XCTAssertFalse(NavigationAvatarCatalog.isEnabled(navigationActive: false, vibing: false))
        XCTAssertTrue(NavigationAvatarCatalog.isEnabled(navigationActive: true, vibing: false))
        XCTAssertTrue(NavigationAvatarCatalog.isEnabled(navigationActive: false, vibing: true))
        XCTAssertTrue(NavigationAvatarCatalog.isEnabled(navigationActive: true, vibing: true))
    }

    func testUnknownThemeOrInvalidCameraDataDoesNotSelectAnAsset() throws {
        let value = try catalog()
        XCTAssertNil(value.frame(theme: "missing", heading: 0, cameraBearing: 0, pitch: 40))
        XCTAssertNil(value.frame(theme: "gta", heading: .nan, cameraBearing: 0, pitch: 40))
        XCTAssertNil(value.frame(theme: "gta", heading: 0, cameraBearing: .infinity, pitch: 40))
        XCTAssertNil(value.frame(theme: "gta", heading: 0, cameraBearing: 0, pitch: .nan))
    }

    func testCorruptCatalogAndUnsafeFramePathsAreRejected() throws {
        var invalid = document()
        invalid["schemaVersion"] = 2
        XCTAssertThrowsError(try catalog(invalid))
        for key in ["frameCount", "imageSize", "displaySize"] {
            invalid = document(); invalid[key] = 0
            XCTAssertThrowsError(try catalog(invalid), key)
        }
        for pitches in [[Double](), [0, 40, 40], [55, 40, 0], [0, 91]] {
            invalid = document(); invalid["pitches"] = pitches
            XCTAssertThrowsError(try catalog(invalid))
        }
        for anchor in [[0.5], [-0.5, 0.7], [0.5, 2]] {
            invalid = document(); invalid["anchor"] = anchor
            XCTAssertThrowsError(try catalog(invalid))
        }
        for path in ["../outside.png", "gta/../../outside.png", "file:///outside.png", "rosa/p0/a00.png", "gta\\p0\\a00.png"] {
            invalid = document()
            var frames = [0, 40, 55].map { pitch in
                (0..<32).map { "gta/p\(pitch)/a\(String(format: "%02d", $0)).png" }
            }
            frames[0][0] = path
            invalid["themes"] = ["gta": ["label": "CJ", "frames": frames]]
            XCTAssertThrowsError(try catalog(invalid))
        }
        invalid = document()
        invalid["themes"] = ["gta": ["label": "CJ", "frames": [["gta/p0/a00.png"]]]]
        XCTAssertThrowsError(try catalog(invalid))
    }
}
