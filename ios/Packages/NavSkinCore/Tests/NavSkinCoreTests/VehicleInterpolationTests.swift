import XCTest
@testable import NavSkinCore

final class VehicleInterpolationTests: XCTestCase {
    func testInterpolatesOnlyBetweenObservedFixesAndStopsAtDestination() {
        let start = GeoPoint(latitude: -16, longitude: -49)
        let end = GeoPoint(latitude: -16.002, longitude: -49.002)
        let middle = VehicleInterpolation.position(from: start, to: end, fraction: 0.5)
        XCTAssertEqual(middle.latitude, -16.001, accuracy: 0.000001)
        XCTAssertEqual(middle.longitude, -49.001, accuracy: 0.000001)
        XCTAssertEqual(VehicleInterpolation.position(from: start, to: end, fraction: -2), start)
        XCTAssertEqual(VehicleInterpolation.position(from: start, to: end, fraction: 9), end)
    }

    func testDatelineAndHeadingUseTheShortWay() {
        let middle = VehicleInterpolation.position(from: GeoPoint(latitude: 0, longitude: 179.9),
            to: GeoPoint(latitude: 0, longitude: -179.9), fraction: 0.5)
        XCTAssertGreaterThan(abs(middle.longitude), 179.99)
        XCTAssertEqual(VehicleInterpolation.bearing(from: 359, to: 1, fraction: 0.5), 0, accuracy: 0.0001)
        XCTAssertEqual(VehicleInterpolation.bearing(from: 1, to: 359, fraction: 0.5), 0, accuracy: 0.0001)
    }

    func testStaleJumpsAndUnknownQualityDoNotAnimate() {
        XCTAssertEqual(VehicleInterpolation.duration(fixIntervalMs: 1_000, distanceMeters: 20, reliable: true), 0.85)
        XCTAssertEqual(VehicleInterpolation.duration(fixIntervalMs: 10_000, distanceMeters: 20, reliable: true), 0)
        XCTAssertEqual(VehicleInterpolation.duration(fixIntervalMs: 1_000, distanceMeters: 800, reliable: true), 0)
        XCTAssertEqual(VehicleInterpolation.duration(fixIntervalMs: 1_000, distanceMeters: 20, reliable: false), 0)
    }
}
