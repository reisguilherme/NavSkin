import Foundation
import XCTest
@testable import NavSkinCore

/// Explicit synthetic metre-grid fixtures. These locations are never used in production.
final class NavigationEngineTests: XCTestCase {
    private func point(_ x: Double, _ y: Double = 0) -> GeoPoint {
        GeoPoint(latitude: y / 111_195, longitude: x / 111_195)
    }

    private func route(_ points: [GeoPoint]? = nil, steps: [RouteStep] = [], duration: Double = 120) -> NavRoute {
        let points = points ?? [point(0), point(300)]
        return NavRoute(id: "test-route", geometry: points, steps: steps, distanceMeters: 300,
                        durationSeconds: duration,
                        destination: Destination(id: "test-destination", name: "Synthetic destination", point: points.last!),
                        calculatedAtMs: 1_000_000)
    }

    private func fix(_ x: Double, _ y: Double = 0, t: Int64 = 100_000,
                     accuracy: Double = 5, speed: Double? = 10,
                     bearing: Double? = 90, speedAccuracy: Double? = 0.5) -> LocationFix {
        LocationFix(point: point(x, y), accuracyMeters: accuracy, bearingDegrees: bearing,
                    speedMps: speed, speedAccuracyMps: speedAccuracy,
                    elapsedRealtimeMs: t, wallTimeMs: 1_000_000 + t)
    }

    @discardableResult
    private func feed(_ engine: NavigationEngine, _ fix: LocationFix) -> EngineUpdate {
        engine.onFix(fix, nowElapsedMs: fix.elapsedRealtimeMs, nowWallMs: fix.wallTimeMs)
    }

    private func started(_ route: NavRoute? = nil) throws -> NavigationEngine {
        let engine = NavigationEngine()
        try engine.start(route ?? self.route())
        return engine
    }

    func testProviderSpeedIsCurrentAndParkedBearingDoesNotSpin() {
        let engine = NavigationEngine()
        let moving = feed(engine, fix(0))
        XCTAssertEqual(moving.location.speedKmh!, 36, accuracy: 0.01)
        let stopped = feed(engine, fix(1, t: 101_000, speed: 0.1, bearing: 270))
        XCTAssertEqual(stopped.location.speedKmh!, 0, accuracy: 0.01)
        XCTAssertEqual(stopped.location.bearingDegrees!, 90, accuracy: 0.01)
    }

    func testInaccurateProviderSpeedIsUnavailable() {
        XCTAssertNil(feed(NavigationEngine(), fix(0, speedAccuracy: 12)).location.speedKmh)
    }

    func testUnreliableSpeedNeverBecomesNegativeOrNonfiniteDisplay() {
        for speed in [-1.0, 76.0, .nan, .infinity] {
            XCTAssertNil(feed(NavigationEngine(), fix(0, speed: speed)).location.speedKmh)
        }
    }

    func testHeadingCanUseDisplacementWhenProviderHasNoSpeed() {
        let engine = NavigationEngine()
        feed(engine, fix(0, speed: nil, bearing: nil))
        let moving = feed(engine, fix(20, t: 102_000, speed: nil, bearing: nil))
        XCTAssertNil(moving.location.speedKmh)
        XCTAssertEqual(moving.location.bearingDegrees!, 90, accuracy: 0.01)
        let noise = feed(engine, fix(21, t: 103_000, speed: nil, bearing: nil))
        XCTAssertEqual(noise.location.bearingDegrees!, 90, accuracy: 0.01)
    }

    func testReliableSpeedChangesPromptly() {
        let engine = NavigationEngine()
        feed(engine, fix(0, speed: 5))
        XCTAssertGreaterThan(feed(engine, fix(20, t: 101_000, speed: 15)).location.speedKmh!, 40)
    }

    func testStaleSignalHidesSpeedAndProgressThenRecovers() throws {
        let engine = try started()
        feed(engine, fix(20))
        let weak = engine.onTick(nowElapsedMs: 106_000, nowWallMs: 1_106_000)
        XCTAssertEqual(weak.location.quality, .weak)
        XCTAssertNil(weak.location.speedKmh)
        XCTAssertNil(weak.progress)
        let lost = engine.onTick(nowElapsedMs: 116_000, nowWallMs: 1_116_000)
        XCTAssertEqual(lost.location.quality, .lost)
        XCTAssertNotNil(lost.location.fix)
        let recovered = feed(engine, fix(30, t: 117_000))
        XCTAssertEqual(recovered.location.quality, .good)
        XCTAssertGreaterThan(recovered.progress!.alongRouteMeters, 25)
    }

    func testFreshnessThresholdsUseElapsedTimeAndKeepExactBoundaries() {
        let engine = NavigationEngine()
        feed(engine, fix(0))
        XCTAssertEqual(engine.onTick(nowElapsedMs: 105_000, nowWallMs: 0).location.quality, .good)
        XCTAssertEqual(engine.onTick(nowElapsedMs: 105_001, nowWallMs: 0).location.quality, .weak)
        XCTAssertEqual(engine.onTick(nowElapsedMs: 115_000, nowWallMs: 0).location.quality, .weak)
        XCTAssertEqual(engine.onTick(nowElapsedMs: 115_001, nowWallMs: 0).location.quality, .lost)
    }

    func testOutOfOrderFixCannotRewindLocationOrAdvanceRoute() throws {
        let engine = try started()
        let first = feed(engine, fix(20, t: 102_000))
        let old = engine.onFix(fix(200, t: 101_000), nowElapsedMs: 102_000, nowWallMs: 1_102_000)
        XCTAssertEqual(first.location.fix, old.location.fix)
        XCTAssertEqual(first.progress!.alongRouteMeters, old.progress!.alongRouteMeters, accuracy: 0.01)
    }

    func testAlreadyStaleFixCannotAdvanceOrExposeCurrentSpeed() throws {
        let update = try started().onFix(fix(200), nowElapsedMs: 120_000, nowWallMs: 1_120_000)
        XCTAssertEqual(update.location.quality, .lost)
        XCTAssertNil(update.location.speedKmh)
        XCTAssertNil(update.progress)
    }

    func testInaccurateGPSDoesNotAdvanceOrCauseRerouting() throws {
        let engine = try started()
        feed(engine, fix(20))
        for index in 0..<5 {
            let update = feed(engine, fix(200, 80, t: 101_000 + Int64(index) * 1_000, accuracy: 70))
            XCTAssertEqual(update.location.quality, .weak)
            XCTAssertNil(update.progress)
            XCTAssertFalse(update.shouldRecalculate)
        }
        XCTAssertLessThan(feed(engine, fix(30, t: 107_000)).progress!.alongRouteMeters, 40)
    }

    func testETAUsesRemainingStepDurationsDespiteFastCurrentSpeed() throws {
        let steps = [
            RouteStep(id: 0, instruction: "Straight", streetName: "A", kind: .straight,
                      startIndex: 0, endIndex: 1, distanceMeters: 100, durationSeconds: 100),
            RouteStep(id: 1, instruction: "Arrive", streetName: "B", kind: .arrive,
                      startIndex: 1, endIndex: 2, distanceMeters: 200, durationSeconds: 20)
        ]
        let engine = try started(route([point(0), point(100), point(300)], steps: steps))
        let progress = feed(engine, fix(50, speed: 30)).progress!
        XCTAssertEqual(progress.remainingSeconds, 70, accuracy: 0.1)
        XCTAssertEqual(progress.arrivalEpochMs, 1_170_000)
        XCTAssertEqual(progress.distanceToManeuverMeters, 50, accuracy: 0.1)
    }

    func testETAUsesRouteProportionWithoutStepDurationsAndRebasesClockOnTick() throws {
        let engine = try started()
        let progress = feed(engine, fix(150)).progress!
        XCTAssertEqual(progress.remainingSeconds, 60, accuracy: 0.1)
        let ticked = engine.onTick(nowElapsedMs: 101_000, nowWallMs: 2_000_000).progress!
        XCTAssertEqual(ticked.arrivalEpochMs, 2_060_000)
        XCTAssertEqual(ticked.alongRouteMeters, progress.alongRouteMeters)
        XCTAssertEqual(ticked.remainingSeconds, progress.remainingSeconds)
    }

    func testAcceptedProgressDoesNotRewindOnSmallGPSBacktrack() throws {
        let engine = try started()
        let first = feed(engine, fix(100)).progress!
        let backtrack = feed(engine, fix(90, t: 101_000)).progress!
        XCTAssertTrue(backtrack.onRoute)
        XCTAssertEqual(backtrack.alongRouteMeters, first.alongRouteMeters, accuracy: 0.01)
    }

    func testMatchingDoesNotJumpToRepeatedLaterGeometry() throws {
        let engine = try started(route([point(0), point(200), point(200, 100),
                                        point(0, 100), point(0), point(200)]))
        feed(engine, fix(20))
        let progress = feed(engine, fix(40, t: 102_000)).progress!
        XCTAssertEqual(progress.segmentIndex, 0)
        XCTAssertLessThan(progress.alongRouteMeters, 50)
    }

    func testHeadingAndContinuityKeepParallelRoadFromStealingMatch() throws {
        let engine = try started(route([point(0), point(200), point(200, 15), point(0, 15)]))
        feed(engine, fix(20))
        let progress = feed(engine, fix(30, 12, t: 101_000)).progress!
        XCTAssertEqual(progress.segmentIndex, 0)
        XCTAssertLessThan(progress.alongRouteMeters, 40)
    }

    func testCrossingStaysOnCurrentLeg() throws {
        let engine = try started(route([point(0), point(100, 100), point(0, 100), point(100), point(200)]))
        feed(engine, fix(20, 20, bearing: 45))
        let progress = feed(engine, fix(50, 50, t: 103_000, bearing: 45)).progress!
        XCTAssertEqual(progress.segmentIndex, 0)
        XCTAssertLessThan(progress.alongRouteMeters, 80)
    }

    func testOneAccurateOutlierDoesNotTriggerRerouting() throws {
        let engine = try started()
        feed(engine, fix(20))
        XCTAssertFalse(feed(engine, fix(30, 80, t: 101_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(40, t: 102_000)).shouldRecalculate)
    }

    func testConsistentDeviationUnsnapsAndReroutesOnceDuringCooldown() throws {
        let engine = try started()
        feed(engine, fix(20))
        XCTAssertFalse(feed(engine, fix(30, 70, t: 101_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(40, 72, t: 102_000)).shouldRecalculate)
        let confirmed = feed(engine, fix(50, 74, t: 103_000))
        XCTAssertTrue(confirmed.shouldRecalculate)
        XCTAssertFalse(confirmed.progress!.onRoute)
        XCTAssertGreaterThan(confirmed.progress!.distanceFromRouteMeters, 65)
        XCTAssertLessThan(confirmed.progress!.alongRouteMeters, 25)
        for index in 0..<5 {
            XCTAssertFalse(feed(engine, fix(60 + Double(index), 80, t: 104_000 + Int64(index) * 1_000)).shouldRecalculate)
        }
        // A fresh, spaced sequence after the 15 second cooldown can request another route.
        XCTAssertFalse(feed(engine, fix(70, 80, t: 118_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(80, 80, t: 119_000)).shouldRecalculate)
        XCTAssertTrue(feed(engine, fix(90, 80, t: 120_000)).shouldRecalculate)
    }

    func testPlausibleOffsetWithWeakAccuracyDoesNotCauseDeviation() throws {
        let engine = try started()
        for index in 0..<6 {
            XCTAssertFalse(feed(engine, fix(20 + Double(index), 45, t: 100_000 + Int64(index) * 1_000,
                                           accuracy: 30)).shouldRecalculate)
        }
    }

    func testConfirmedDeviationRequiresTwoGoodReadingsToRecover() throws {
        let engine = try started()
        feed(engine, fix(20))
        for index in 1...3 { feed(engine, fix(30, 80, t: 100_000 + Int64(index) * 1_000)) }
        XCTAssertFalse(feed(engine, fix(30, t: 104_000)).progress!.onRoute)
        XCTAssertTrue(feed(engine, fix(40, t: 105_000)).progress!.onRoute)
    }

    func testPassingDestinationNearStartOfLoopCannotArrive() throws {
        let engine = try started(route([point(0), point(200), point(200, 100), point(0, 100), point(0)]))
        for index in 0..<3 {
            XCTAssertFalse(feed(engine, fix(Double(index), t: 100_000 + Int64(index) * 1_000)).arrived)
        }
    }

    func testArrivalNeedsSuccessiveGoodFixesNearFinalProgress() throws {
        let engine = try started()
        for index in 0...5 {
            feed(engine, fix(Double(index) * 50, t: 100_000 + Int64(index) * 5_000))
        }
        XCTAssertFalse(feed(engine, fix(286, t: 129_000)).arrived)
        let arrived = feed(engine, fix(295, t: 130_000))
        XCTAssertTrue(arrived.arrived)
        XCTAssertEqual(arrived.progress!.remainingSeconds, 0, accuracy: 0.01)
    }

    func testArrivalUsesDriveableEndpointForDestinationInsidePark() throws {
        let requested = Destination(id: "park", name: "Park destination", point: point(100, 80))
        var drivingRoute = route([point(0), point(100)])
        drivingRoute.destination = requested
        drivingRoute.distanceMeters = 100
        let engine = try started(drivingRoute)
        feed(engine, fix(0))
        XCTAssertFalse(feed(engine, fix(100, t: 110_000)).arrived)
        let arrived = feed(engine, fix(100, t: 111_000))
        XCTAssertTrue(arrived.arrived)
        XCTAssertEqual(arrived.progress!.remainingDistanceMeters, 0, accuracy: 0.01)
        XCTAssertEqual(drivingRoute.destination.point, point(100, 80))
    }

    func testRepeatedTimestampCannotSatisfySuccessiveArrivalReadings() throws {
        let engine = try started()
        feed(engine, fix(250))
        let nearEnd = fix(290, t: 105_000)
        XCTAssertFalse(feed(engine, nearEnd).arrived)
        XCTAssertFalse(feed(engine, nearEnd).arrived)
    }

    func testSignalLossBreaksSuccessiveArrivalConfidence() throws {
        let engine = try started()
        feed(engine, fix(250))
        XCTAssertFalse(feed(engine, fix(290, t: 105_000)).arrived)
        _ = engine.onTick(nowElapsedMs: 121_000, nowWallMs: 1_121_000)
        XCTAssertFalse(feed(engine, fix(295, t: 122_000)).arrived)
        XCTAssertTrue(feed(engine, fix(296, t: 123_000)).arrived)
    }

    func testGapWithoutTickBreaksArrivalConfidence() throws {
        let engine = try started()
        feed(engine, fix(250))
        XCTAssertFalse(feed(engine, fix(290, t: 105_000)).arrived)
        XCTAssertFalse(feed(engine, fix(295, t: 122_000)).arrived)
    }

    func testUpcomingManeuverUsesNextStartAndAdvancesAfterTurn() throws {
        let steps = [
            RouteStep(id: 0, instruction: "Depart", streetName: "A", kind: .depart,
                      startIndex: 0, endIndex: 1, distanceMeters: 100, durationSeconds: 40),
            RouteStep(id: 1, instruction: "Turn left", streetName: "B", kind: .left,
                      startIndex: 1, endIndex: 2, distanceMeters: 100, durationSeconds: 60),
            RouteStep(id: 2, instruction: "Arrive", streetName: "", kind: .arrive,
                      startIndex: 2, endIndex: 2, distanceMeters: 0, durationSeconds: 0)
        ]
        let engine = try started(route([point(0), point(100), point(100, 100)], steps: steps))
        let approaching = feed(engine, fix(80)).progress!
        XCTAssertEqual(approaching.stepIndex, 1)
        XCTAssertEqual(approaching.distanceToManeuverMeters, 20, accuracy: 0.1)
        let afterTurn = feed(engine, fix(100, 10, t: 103_000, bearing: 0)).progress!
        XCTAssertEqual(afterTurn.stepIndex, 2)
        XCTAssertEqual(afterTurn.distanceToManeuverMeters, 90, accuracy: 0.1)
        XCTAssertEqual(afterTurn.remainingSeconds, 54, accuracy: 0.1)
    }

    func testUnavailableInvalidAndFutureFixesCannotCreateCurrentPosition() {
        let engine = NavigationEngine()
        XCTAssertEqual(engine.onTick(nowElapsedMs: 1_000, nowWallMs: 2_000).location.quality, .unavailable)
        var invalid = fix(0)
        invalid.point = GeoPoint(latitude: .nan, longitude: 0)
        XCTAssertEqual(feed(engine, invalid).location.quality, .unavailable)
        XCTAssertEqual(engine.onFix(fix(0, t: 110_000), nowElapsedMs: 100_000, nowWallMs: 1_100_000).location.quality, .unavailable)
    }

    func testShortNoisyDeviationCannotRetryDuringCooldown() throws {
        let engine = try started()
        feed(engine, fix(20))
        for index in 0..<2 { feed(engine, fix(30, 80, t: 101_000 + Int64(index) * 1_000)) }
        XCTAssertTrue(feed(engine, fix(30, 80, t: 103_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(40, t: 104_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(50, t: 105_000)).shouldRecalculate)
        for index in 0..<2 { feed(engine, fix(60, 80, t: 106_000 + Int64(index) * 1_000)) }
        XCTAssertFalse(feed(engine, fix(60, 80, t: 108_000)).shouldRecalculate)
    }

    func testSignalLossRequiresFreshDeviationEvidence() throws {
        let engine = try started()
        feed(engine, fix(20))
        for index in 0..<2 { feed(engine, fix(30, 80, t: 101_000 + Int64(index) * 1_000)) }
        XCTAssertTrue(feed(engine, fix(30, 80, t: 103_000)).shouldRecalculate)
        _ = engine.onTick(nowElapsedMs: 120_000, nowWallMs: 1_120_000)
        XCTAssertFalse(feed(engine, fix(30, 80, t: 121_000)).shouldRecalculate)
        XCTAssertFalse(feed(engine, fix(40, 80, t: 122_000)).shouldRecalculate)
        XCTAssertTrue(feed(engine, fix(50, 80, t: 123_000)).shouldRecalculate)
    }

    func testZeroLengthSegmentsDoNotProduceNaNProgress() throws {
        let engine = try started(route([point(0), point(0), point(100), point(100), point(300)]))
        let progress = feed(engine, fix(50)).progress!
        XCTAssertTrue(progress.remainingSeconds.isFinite)
        XCTAssertEqual(progress.alongRouteMeters, 50, accuracy: 0.1)
    }

    func testAlreadyAtDestinationZeroLengthRouteCanArrive() throws {
        let engine = try started(route([point(0), point(0)]))
        XCTAssertFalse(feed(engine, fix(0)).arrived)
        let arrived = feed(engine, fix(0, t: 101_000))
        XCTAssertTrue(arrived.arrived)
        XCTAssertEqual(arrived.progress!.remainingSeconds, 0)
    }

    func testStoppingRemovesRouteAndKeepsCurrentGPS() throws {
        let engine = try started()
        feed(engine, fix(30))
        engine.stop()
        let update = engine.onTick(nowElapsedMs: 100_100, nowWallMs: 1_100_100)
        XCTAssertNil(update.progress)
        XCTAssertEqual(update.location.quality, .good)
    }

    func testStartRejectsInvalidRoutesWithoutReplacingActiveRoute() throws {
        let engine = try started()
        feed(engine, fix(20))
        var invalid = route()
        invalid.geometry = [point(0)]
        XCTAssertThrowsError(try engine.start(invalid))
        invalid = route()
        invalid.durationSeconds = -.infinity
        XCTAssertThrowsError(try engine.start(invalid))
        invalid = route()
        invalid.steps = [RouteStep(id: 0, instruction: "", streetName: "", kind: .unknown,
                                  startIndex: 1, endIndex: 2, distanceMeters: 0, durationSeconds: 0)]
        XCTAssertThrowsError(try engine.start(invalid))
        XCTAssertNotNil(engine.onTick(nowElapsedMs: 100_100, nowWallMs: 1_100_100).progress)
    }

    func testExtremeValidWallClockAndEstimateDoNotTrap() throws {
        let engine = try started(route(duration: Double.greatestFiniteMagnitude))
        let update = engine.onFix(fix(0), nowElapsedMs: 100_000, nowWallMs: Int64.max)
        XCTAssertEqual(update.progress!.arrivalEpochMs, Int64.max)
        let clockEdge = NavigationEngine()
        var edgeFix = fix(0)
        edgeFix.elapsedRealtimeMs = Int64.max
        XCTAssertEqual(clockEdge.onFix(edgeFix, nowElapsedMs: Int64.max, nowWallMs: 0).location.quality, .good)
    }

    func testSnapshotDefaultsAndCodableRoundTrip() throws {
        var snapshot = NavigationSnapshot()
        XCTAssertEqual(snapshot.phase, .idle)
        XCTAssertEqual(snapshot.locationAccess, .notRequested)
        XCTAssertFalse(snapshot.navigationActive)
        XCTAssertFalse(snapshot.guidanceAvailable)
        snapshot.phase = .navigating
        snapshot.route = route()
        snapshot.destination = snapshot.route!.destination
        XCTAssertTrue(snapshot.navigationActive)
        XCTAssertEqual(try JSONDecoder().decode(NavigationSnapshot.self, from: JSONEncoder().encode(snapshot)), snapshot)
        XCTAssertEqual(NavigationPreferences(), NavigationPreferences(vehicleId: "arrow", savedPlacesVisible: true, orientation: .movementUp))
    }
}
