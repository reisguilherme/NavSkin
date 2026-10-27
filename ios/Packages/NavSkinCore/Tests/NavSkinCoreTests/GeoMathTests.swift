import XCTest
@testable import NavSkinCore

final class GeoMathTests: XCTestCase {
    func testDatelineDistanceAndProjectionTakeTheShortWay() {
        let west = GeoPoint(latitude: 0, longitude: 179.999)
        let east = GeoPoint(latitude: 0, longitude: -179.999)
        XCTAssertEqual(GeoMath.distanceMeters(west, east), 222.39, accuracy: 0.1)
        let projected = GeoMath.project(GeoPoint(latitude: 0.001, longitude: 180), west, east)
        XCTAssertEqual(projected.fraction, 0.5, accuracy: 0.001)
        XCTAssertEqual(projected.distanceMeters, 111.195, accuracy: 0.1)
        XCTAssertGreaterThan(abs(projected.point.longitude), 179.99)
    }

    func testIdenticalPointsHaveZeroDistanceAndFiniteProjection() {
        let point = GeoPoint(latitude: -16.68, longitude: -49.25)
        XCTAssertEqual(GeoMath.distanceMeters(point, point), 0)
        XCTAssertEqual(GeoMath.project(point, point, point).point, point)
    }

    func testBearingsHandleTheNorthBoundaryWithoutSpinning() {
        let origin = GeoPoint(latitude: 0, longitude: 0)
        XCTAssertEqual(GeoMath.bearingDegrees(origin, GeoPoint(latitude: 0, longitude: 0.001)), 90, accuracy: 0.01)
        XCTAssertEqual(GeoMath.bearingDegrees(origin, GeoPoint(latitude: 0.001, longitude: 0)), 0, accuracy: 0.01)
        XCTAssertEqual(GeoMath.bearingDifference(359, 1), 2, accuracy: 0.001)
        XCTAssertEqual(GeoMath.normalizeBearing(-90), 270)
    }

    func testAntipodesAndPolesHaveFiniteDistances() {
        XCTAssertEqual(GeoMath.distanceMeters(
            GeoPoint(latitude: 0, longitude: 0), GeoPoint(latitude: 0, longitude: 180)
        ), 20_015_114.4, accuracy: 1)
        XCTAssertTrue(GeoMath.project(
            GeoPoint(latitude: 89.999, longitude: 10),
            GeoPoint(latitude: 90, longitude: 0), GeoPoint(latitude: 90, longitude: 90)
        ).distanceMeters.isFinite)
    }

    func testInvalidCoordinatesAreRejected() {
        XCTAssertFalse(GeoMath.isValid(GeoPoint(latitude: .nan, longitude: 0)))
        XCTAssertFalse(GeoMath.isValid(GeoPoint(latitude: 0, longitude: .infinity)))
        XCTAssertFalse(GeoMath.isValid(GeoPoint(latitude: 90.01, longitude: 0)))
        XCTAssertFalse(GeoMath.isValid(GeoPoint(latitude: 0, longitude: -180.01)))
        XCTAssertTrue(GeoMath.isValid(GeoPoint(latitude: -90, longitude: 180)))
    }
}
