import XCTest
@testable import NavSkinCore

final class NavigationCameraTests: XCTestCase {
    func testVibingWithoutRouteUsesDrivingZoomAndHeadingWithoutCreatingNavigation() throws {
        var value = snapshot(heading: 125, speed: 20)
        value.phase = .idle
        value.route = nil
        value.progress = nil
        let original = value
        var policy = NavigationCameraPolicy()
        let camera = try XCTUnwrap(policy.update(value, orientation: .northUp, map3D: false, vibing: true))
        XCTAssertEqual(camera.pitch, 48)
        XCTAssertEqual(camera.zoom, 17)
        XCTAssertEqual(camera.bearing, 125)
        XCTAssertEqual(value, original)
        XCTAssertFalse(value.navigationActive)
    }

    func testLeavingVibingRestoresChosenDimensionAndOrientation() throws {
        let preferences = NavigationPreferences(orientation: .northUp, browsing3D: false, navigation3D: false)
        var policy = NavigationCameraPolicy()
        let value = snapshot(heading: 125)
        let immersive = try XCTUnwrap(policy.update(value, orientation: preferences.orientation,
            map3D: preferences.map3D(navigationActive: true), vibing: true))
        XCTAssertEqual(immersive.pitch, 48)
        XCTAssertEqual(immersive.bearing, 125)
        policy.reset()
        let regular = try XCTUnwrap(policy.update(value, orientation: preferences.orientation,
            map3D: preferences.map3D(navigationActive: true), vibing: false))
        XCTAssertEqual(regular.pitch, 0)
        XCTAssertEqual(regular.bearing, 0)
        XCTAssertFalse(preferences.navigation3D)
        XCTAssertFalse(preferences.browsing3D)
    }

    func testVibingStillUsesRouteTurnApproachAndRejectsStaleFixes() throws {
        var value = snapshot(speed: 90)
        value.progress?.distanceToManeuverMeters = 20
        var policy = NavigationCameraPolicy()
        let camera = try XCTUnwrap(policy.update(value, orientation: .movementUp, vibing: true))
        XCTAssertGreaterThan(camera.zoom, 17)
        value.location.ageMs = 6_000
        XCTAssertNil(policy.update(value, orientation: .movementUp, vibing: true))
    }

    func testPendingRoutePreviewCannotFlattenVibingFreeDriveCamera() throws {
        var value = snapshot()
        value.phase = .preview
        var policy = NavigationCameraPolicy()
        XCTAssertEqual(policy.update(value, orientation: .northUp, map3D: false, vibing: true)?.pitch, 48)
        XCTAssertEqual(value.phase, .preview)
    }

    func testNavigationTiltsByDefaultButBrowsingAndRoutePreviewStayFlat() throws {
        var policy = NavigationCameraPolicy()
        let driving = snapshot()
        XCTAssertEqual(try XCTUnwrap(policy.update(driving, orientation: .movementUp)).pitch, 48)
        var preview = driving
        preview.phase = .preview
        XCTAssertEqual(try XCTUnwrap(policy.update(preview, orientation: .movementUp, map3D: true)).pitch, 0)
        preview.phase = .idle
        XCTAssertEqual(try XCTUnwrap(policy.update(preview, orientation: .movementUp)).pitch, 0)
        XCTAssertEqual(try XCTUnwrap(policy.update(preview, orientation: .movementUp, map3D: true)).pitch, 48)
    }

    func testTwoDimensionalNavigationRemainsFlatAcrossFixesAndChangesWithoutANewFix() throws {
        var policy = NavigationCameraPolicy()
        let driving = snapshot()
        let tilted = try XCTUnwrap(policy.update(driving, orientation: .movementUp, map3D: true))
        let flat = try XCTUnwrap(policy.update(driving, orientation: .movementUp, map3D: false))
        XCTAssertEqual(flat.pitch, 0)
        XCTAssertEqual(flat.point, tilted.point)
        XCTAssertEqual(flat.zoom, tilted.zoom)
        XCTAssertEqual(flat.bearing, tilted.bearing)
        let later = try XCTUnwrap(policy.update(snapshot(time: 2_000), orientation: .movementUp, map3D: false))
        XCTAssertEqual(later.pitch, 0)
        XCTAssertEqual(policy.update(snapshot(time: 2_000), orientation: .movementUp, map3D: false), later)
    }

    func testDimensionChangeStillAppliesWithWeakGPSWithoutMovingReliableFraming() throws {
        var policy = NavigationCameraPolicy()
        let tilted = try XCTUnwrap(policy.update(snapshot(), orientation: .movementUp, map3D: true))
        let weak = snapshot(time: 2_000, heading: 230, speed: 80, latitude: -16.001, quality: .weak)
        let flat = try XCTUnwrap(policy.update(weak, orientation: .movementUp, map3D: false))
        XCTAssertEqual(flat.pitch, 0)
        XCTAssertEqual(flat.point, tilted.point)
        XCTAssertEqual(flat.zoom, tilted.zoom)
        XCTAssertEqual(flat.bearing, tilted.bearing)
    }

    func testUrbanDrivingAndApproachingTurnsRevealMoreStreetDetail() {
        let highway = NavigationCameraPolicy.targetZoom(speedKmh: 100, maneuver: nil, distanceMeters: nil)
        let urban = NavigationCameraPolicy.targetZoom(speedKmh: 25, maneuver: nil, distanceMeters: nil)
        let approach = NavigationCameraPolicy.targetZoom(speedKmh: 50, maneuver: .right, distanceMeters: 100)
        let turn = NavigationCameraPolicy.targetZoom(speedKmh: 50, maneuver: .right, distanceMeters: 40)
        let uturn = NavigationCameraPolicy.targetZoom(speedKmh: 25, maneuver: .uturn, distanceMeters: 40)
        XCTAssertLessThan(highway, urban)
        XCTAssertGreaterThan(approach, 16.5)
        XCTAssertGreaterThan(turn, approach)
        XCTAssertGreaterThan(uturn, turn)
        XCTAssertEqual(NavigationCameraPolicy.targetZoom(speedKmh: 50, maneuver: .straight, distanceMeters: 40), 16.5)
    }

    func testHeadingCrossesNorthByShortestArcAndZoomChangesGradually() throws {
        var policy = NavigationCameraPolicy()
        _ = policy.update(snapshot(heading: 359, speed: 95), orientation: .movementUp)
        let camera = try XCTUnwrap(policy.update(snapshot(time: 1_100, heading: 1, speed: 10), orientation: .movementUp))
        XCTAssertGreaterThan(camera.bearing, 359)
        XCTAssertLessThan(camera.bearing, 360)
        XCTAssertGreaterThan(camera.zoom, 15.5)
        XCTAssertLessThan(camera.zoom, 17)
    }

    func testStationaryNoiseAndWeakFixDoNotRotateOrDragCamera() throws {
        var policy = NavigationCameraPolicy()
        let original = try XCTUnwrap(policy.update(snapshot(heading: 85, speed: 10), orientation: .movementUp))
        let stationary = snapshot(time: 2_000, heading: 185, speed: 0, latitude: -16.00001)
        let stopped = try XCTUnwrap(policy.update(stationary, orientation: .movementUp))
        XCTAssertEqual(stopped.point, original.point)
        XCTAssertEqual(stopped.bearing, original.bearing)
        let weak = snapshot(time: 3_000, heading: 230, speed: 80, latitude: -16.001, quality: .weak)
        let frozen = try XCTUnwrap(policy.update(weak, orientation: .movementUp))
        XCTAssertEqual(frozen, stopped)
    }

    func testAgeOnlyTicksDoNotAdvanceSmoothingAndLostFixCannotMoveCamera() throws {
        var policy = NavigationCameraPolicy()
        let original = snapshot()
        let camera = try XCTUnwrap(policy.update(original, orientation: .movementUp))
        var tick = original
        tick.location.ageMs = 1_000
        XCTAssertEqual(policy.update(tick, orientation: .movementUp), camera)
        tick.location.ageMs = 6_000
        XCTAssertNil(policy.update(tick, orientation: .movementUp))
        tick.location.ageMs = 0
        tick.location.quality = .lost
        XCTAssertNil(policy.update(tick, orientation: .movementUp))
    }

    func testNorthUpAndResetAreImmediateEvenWhenStopped() throws {
        var policy = NavigationCameraPolicy()
        _ = policy.update(snapshot(heading: 140), orientation: .movementUp)
        let north = try XCTUnwrap(policy.update(snapshot(heading: 140), orientation: .northUp))
        XCTAssertEqual(north.bearing, 0)
        policy.reset()
        let stopped = try XCTUnwrap(policy.update(snapshot(heading: 140, speed: 0), orientation: .movementUp, fallbackBearing: 27))
        XCTAssertEqual(stopped.bearing, 27)
    }

    func testMismatchedOrOffRouteProgressDoesNotZoomIntoAnOldManeuver() throws {
        let driving = snapshot(speed: 90)
        var approaching = driving
        approaching.progress?.distanceToManeuverMeters = 20
        var policy = NavigationCameraPolicy()
        let turn = try XCTUnwrap(policy.update(approaching, orientation: .movementUp))
        XCTAssertGreaterThan(turn.zoom, 17)
        approaching.progress?.routeId = "old-route"
        policy.reset()
        XCTAssertEqual(policy.update(approaching, orientation: .movementUp)?.zoom, 15.5)
        approaching.progress?.routeId = "route"
        approaching.progress?.onRoute = false
        policy.reset()
        XCTAssertEqual(policy.update(approaching, orientation: .movementUp)?.zoom, 15.5)
    }

    func testIdleCameraDoesNotAdoptNavigationZoom() throws {
        var value = snapshot(speed: 0)
        value.phase = .idle
        var policy = NavigationCameraPolicy()
        XCTAssertEqual(policy.update(value, orientation: .movementUp)?.zoom, 16)
    }

    func testInvalidInputsNeverProduceAnInvalidCamera() throws {
        var value = snapshot(speed: .nan)
        value.location.bearingDegrees = .infinity
        value.progress?.distanceToManeuverMeters = .nan
        var policy = NavigationCameraPolicy()
        let camera = try XCTUnwrap(policy.update(value, orientation: .movementUp, fallbackBearing: .nan))
        XCTAssertTrue(camera.zoom.isFinite)
        XCTAssertTrue(camera.bearing.isFinite)
        value.location.fix?.point.latitude = .nan
        XCTAssertNil(policy.update(value, orientation: .movementUp))
    }

    private func snapshot(time: Int64 = 1_000, heading: Double = 90, speed: Double = 50,
                          latitude: Double = -16, quality: LocationQuality = .good) -> NavigationSnapshot {
        let point = GeoPoint(latitude: latitude, longitude: -49)
        let end = GeoPoint(latitude: -16.01, longitude: -49)
        let destination = Destination(id: "end", name: "Destino", point: end)
        let route = NavRoute(id: "route", geometry: [point, end], steps: [
            RouteStep(id: 0, instruction: "Vire à direita", streetName: "Rua", kind: .right,
                      startIndex: 0, endIndex: 1, distanceMeters: 1_000, durationSeconds: 60)
        ], distanceMeters: 1_000, durationSeconds: 60, destination: destination, calculatedAtMs: 0)
        let fix = LocationFix(point: point, accuracyMeters: 5, elapsedRealtimeMs: time, wallTimeMs: time)
        let progress = RouteProgress(routeId: "route", alongRouteMeters: 0, projectedPoint: point,
            segmentIndex: 0, stepIndex: 0, distanceToManeuverMeters: 1_000,
            remainingDistanceMeters: 1_000, remainingSeconds: 60, arrivalEpochMs: 60_000,
            distanceFromRouteMeters: 0, onRoute: true, arrived: false)
        return NavigationSnapshot(phase: .navigating,
            location: LocationState(fix: fix, quality: quality, speedKmh: speed, bearingDegrees: heading),
            route: route, progress: progress)
    }
}
