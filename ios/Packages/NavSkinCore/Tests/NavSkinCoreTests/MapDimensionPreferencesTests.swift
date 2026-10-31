import Foundation
import XCTest
@testable import NavSkinCore

final class MapDimensionPreferencesTests: XCTestCase {
    func testPinkVehicleDefaultAdaptsOnlyBeforeAnExplicitChoice() throws {
        let fresh = NavigationPreferences()
        XCTAssertEqual(fresh.vehicleID(theme: "rosa"), "themed")
        XCTAssertEqual(fresh.vehicleID(theme: "gta"), "arrow")
        let reloaded = try JSONDecoder().decode(NavigationPreferences.self, from: JSONEncoder().encode(fresh))
        XCTAssertEqual(reloaded.vehicleID(theme: "rosa"), "themed")
        let explicitArrow = NavigationPreferences(vehicleSelectionExplicit: true)
        XCTAssertEqual(explicitArrow.vehicleID(theme: "rosa"), "arrow")
        XCTAssertEqual(NavigationPreferences(vehicleId: "sport").vehicleID(theme: "rosa"), "sport")
    }

    func testLegacyStoredVehicleIsPreservedWhenChoiceProvenanceIsUnknown() throws {
        let legacy = Data(#"{"vehicleId":"arrow","savedPlacesVisible":true,"orientation":"movementUp"}"#.utf8)
        let preferences = try JSONDecoder().decode(NavigationPreferences.self, from: legacy)
        XCTAssertEqual(preferences.vehicleID(theme: "rosa"), "arrow")
        XCTAssertTrue(preferences.vehicleSelectionExplicit)
    }

    func testFreshInstallPersistsFlatBrowsingAndThreeDimensionalNavigation() throws {
        let json = try encoded(NavigationPreferences())
        XCTAssertEqual(json["browsing3D"] as? Bool, false)
        XCTAssertEqual(json["navigation3D"] as? Bool, true)
    }

    func testLegacyPreferencesKeepVehicleVisibilityAndOrientationWhenDimensionsAreAdded() throws {
        let data = Data(#"{"vehicleId":"sport","savedPlacesVisible":false,"orientation":"northUp"}"#.utf8)
        let preferences = try JSONDecoder().decode(NavigationPreferences.self, from: data)
        XCTAssertEqual(preferences.vehicleId, "sport")
        XCTAssertFalse(preferences.savedPlacesVisible)
        XCTAssertEqual(preferences.orientation, .northUp)
        let json = try encoded(preferences)
        XCTAssertEqual(json["browsing3D"] as? Bool, false)
        XCTAssertEqual(json["navigation3D"] as? Bool, true)
    }

    func testExplicitDimensionChoicesSurviveReloadIndependently() throws {
        let data = Data(#"{"vehicleId":"compact","savedPlacesVisible":true,"orientation":"movementUp","browsing3D":true,"navigation3D":false}"#.utf8)
        let first = try JSONDecoder().decode(NavigationPreferences.self, from: data)
        let reloaded = try JSONDecoder().decode(NavigationPreferences.self, from: JSONEncoder().encode(first))
        let json = try encoded(reloaded)
        XCTAssertEqual(json["browsing3D"] as? Bool, true)
        XCTAssertEqual(json["navigation3D"] as? Bool, false)
    }

    private func encoded(_ preferences: NavigationPreferences) throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(preferences)) as? [String: Any])
    }

    func testActiveContextToggleDoesNotOverwriteOtherContextOrResetOnNewNavigation() {
        var preferences = NavigationPreferences()
        XCTAssertFalse(preferences.map3D(navigationActive: false))
        XCTAssertTrue(preferences.map3D(navigationActive: true))
        preferences.setMap3D(false, navigationActive: true)
        preferences.setMap3D(true, navigationActive: false)
        XCTAssertTrue(preferences.map3D(navigationActive: false))
        XCTAssertFalse(preferences.map3D(navigationActive: true))
        preferences.setMap3D(false, navigationActive: false)
        XCTAssertFalse(preferences.map3D(navigationActive: true))
    }
}
