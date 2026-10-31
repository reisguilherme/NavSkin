import Foundation
import XCTest
@testable import NavSkinCore

final class IntermediateStopsTests: XCTestCase {
    @MainActor
    func testOldDestinationArrivalKeepsPendingOrFailedStopEditAvailable() async throws {
        for failed in [false, true] {
            let context = try StopContext()
            await context.start()
            let originalRoute = context.session.snapshot.route
            if failed { await context.http.setStatus(503) }
            else { await context.http.delayNext() }
            context.session.addStop(context.other)
            await context.settle()
            for x in [30.0, 60, 90, 120, 150, 180, 210, 240, 270, 300, 325, 334] {
                context.elapsed += 2_000
                context.fix(x: x)
            }
            XCTAssertEqual(context.session.snapshot.phase, .navigating)
            XCTAssertEqual(context.session.snapshot.route, originalRoute)
            XCTAssertEqual(context.session.snapshot.pendingIntermediateStops, [context.other])
            XCTAssertEqual(context.session.snapshot.updatingStops, !failed)
            XCTAssertEqual(context.places.snapshot.resumeDestination, context.destination)
            if !failed { await context.http.completeDelayed(); await context.settle() }
        }
    }

    @MainActor
    func testOffRouteRerouteRetainsDelayedStopEditAndIgnoresItsOlderResponse() async throws {
        let context = try StopContext()
        await context.start()
        await context.http.delayNext()
        context.session.addStop(context.other)
        await context.settle()
        for x in [30.0, 45, 60] { context.elapsed += 1_000; context.fix(x: x, y: 100) }
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops, [context.other])
        XCTAssertEqual(context.places.snapshot.resumeIntermediateStops, [context.other])
        let replacement = context.session.snapshot.route
        let requests = await context.http.requests()
        XCTAssertEqual(requests.count, 3)
        XCTAssertEqual(requests.last?.split(separator: "|").suffix(2), ["0.0,0.004", "0.0,0.003"])
        await context.http.completeDelayed()
        await context.settle()
        XCTAssertEqual(context.session.snapshot.route, replacement)
        XCTAssertEqual(context.session.snapshot.intermediateStops, [context.other])
    }

    func testLegacySnapshotAndRouteDecodeWithNoStops() throws {
        let snapshot = NavigationSnapshot()
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(snapshot)) as? [String: Any])
        for key in ["intermediateStops", "pendingIntermediateStops", "resumeIntermediateStops", "updatingStops"] { json.removeValue(forKey: key) }
        let restored = try JSONDecoder().decode(NavigationSnapshot.self, from: JSONSerialization.data(withJSONObject: json))
        XCTAssertTrue(restored.intermediateStops.isEmpty)
        XCTAssertTrue(restored.resumeIntermediateStops.isEmpty)
        XCTAssertFalse(restored.updatingStops)
        let route = NavRoute(id: "old", geometry: [], steps: [], distanceMeters: 0, durationSeconds: 0,
            destination: Destination(id: "d", name: "D", point: GeoPoint(latitude: 0, longitude: 0)), calculatedAtMs: 0)
        var oldRoute = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(route)) as? [String: Any])
        oldRoute.removeValue(forKey: "intermediateStops"); oldRoute.removeValue(forKey: "intermediateStopIndices")
        let decodedRoute = try JSONDecoder().decode(NavRoute.self, from: JSONSerialization.data(withJSONObject: oldRoute))
        XCTAssertTrue(decodedRoute.intermediateStopIndices.isEmpty)
    }

    @MainActor
    func testMultipleStopsRemainOrderedAndFailedRemovalDoesNotDisappear() async throws {
        let context = try StopContext()
        await context.start()
        let second = Destination(id: "second", name: "Second", point: GeoPoint(latitude: 0, longitude: 0.002))
        context.session.addStop(context.stop)
        await context.settle()
        context.session.addStop(second)
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops.map(\.id), ["stop", "second"])
        XCTAssertEqual(context.session.snapshot.route?.intermediateStopIndices, [1, 2])
        let requests = await context.http.requests()
        XCTAssertEqual(requests.last, "0.0,0.0|0.0,0.001|0.0,0.002|0.0,0.003")
        await context.http.setStatus(503)
        context.session.removeStop(id: context.stop.id)
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops.map(\.id), ["stop", "second"])
        XCTAssertEqual(context.places.snapshot.resumeIntermediateStops.map(\.id), ["stop", "second"])
        XCTAssertEqual(context.session.snapshot.pendingIntermediateStops?.map(\.id), ["second"])
    }

    @MainActor
    func testPassingStopDuringDelayedEditCannotRestorePassedWaypoint() async throws {
        let context = try StopContext()
        await context.start()
        context.session.addStop(context.stop)
        await context.settle()
        await context.http.delayNext()
        let second = Destination(id: "second", name: "Second", point: GeoPoint(latitude: 0, longitude: 0.002))
        context.session.addStop(second)
        await context.settle()
        for x in [30.0, 60, 90, 115, 135] { context.elapsed += 2_000; context.fix(x: x) }
        await context.http.completeDelayed()
        await context.settle()
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.pendingIntermediateStops, [second])
        XCTAssertTrue(context.places.snapshot.resumeIntermediateStops.isEmpty)
        context.session.retryRoute()
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops, [second])
    }

    @MainActor
    func testStorageFailureRejectsStopCommitWithoutReplacingActiveRoute() async throws {
        let context = try StopContext()
        await context.start()
        let route = context.session.snapshot.route
        try FileManager.default.removeItem(at: context.file)
        try FileManager.default.createDirectory(at: context.file, withIntermediateDirectories: false)
        context.session.addStop(context.stop)
        await context.settle()
        XCTAssertEqual(context.session.snapshot.route, route)
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertTrue(context.places.snapshot.resumeIntermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        XCTAssertNotNil(context.session.snapshot.pendingIntermediateStops)
    }

    @MainActor
    func testPassingStopDuringRerouteDoesNotLeaveCalculationStuck() async throws {
        let context = try StopContext()
        await context.start()
        context.session.addStop(context.stop)
        await context.settle()
        await context.http.delayNext()
        context.session.retryRoute()
        await context.settle()
        XCTAssertEqual(context.session.snapshot.phase, .recalculating)
        for x in [30.0, 60, 90, 115, 135] { context.elapsed += 2_000; context.fix(x: x) }
        await context.http.completeDelayed()
        await context.settle()
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        XCTAssertNil(context.session.snapshot.pendingIntermediateStops)
        XCTAssertTrue(context.session.snapshot.guidanceAvailable)
    }
    @MainActor
    func testAddingAndRemovingStopsCommitsRouteAndResumeTogether() async throws {
        let context = try StopContext()
        await context.start()
        context.session.addStop(context.stop)
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops, [context.stop])
        XCTAssertEqual(context.session.snapshot.destination, context.destination)
        XCTAssertEqual(context.session.snapshot.route?.intermediateStopIndices, [1])
        XCTAssertEqual(context.places.snapshot.resumeIntermediateStops, [context.stop])
        let requests = await context.http.requests()
        XCTAssertEqual(requests.last, "0.0,0.0|0.0,0.001|0.0,0.003")
        context.session.removeStop(id: context.stop.id)
        await context.settle()
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertTrue(context.places.snapshot.resumeIntermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.destination, context.destination)
    }

    @MainActor
    func testFailedEditRetainsCommittedRouteAndRetryCommitsPendingStops() async throws {
        let context = try StopContext()
        await context.start()
        let route = context.session.snapshot.route
        await context.http.setStatus(503)
        context.session.addStop(context.stop)
        await context.settle()
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        XCTAssertEqual(context.session.snapshot.route, route)
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.pendingIntermediateStops, [context.stop])
        XCTAssertTrue(context.places.snapshot.resumeIntermediateStops.isEmpty)
        await context.http.setStatus(200)
        context.session.retryRoute()
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops, [context.stop])
        XCTAssertNil(context.session.snapshot.pendingIntermediateStops)
    }

    @MainActor
    func testWeakGPSMissingKeyAndDuplicateStopsCannotMutateItinerary() async throws {
        let context = try StopContext()
        await context.start()
        context.elapsed += 6_000
        context.session.addStop(context.stop)
        await context.settle()
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        context.fix(x: 0)
        context.key = ""
        context.session.keyChanged()
        context.session.addStop(context.stop)
        await context.settle()
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        context.key = "fixture"
        context.session.keyChanged()
        context.session.addStop(context.stop)
        await context.settle()
        context.session.addStop(context.stop)
        context.session.addStop(context.destination)
        context.session.addStop(Destination(id: "invalid", name: "Invalid", point: GeoPoint(latitude: 95, longitude: 0)))
        await context.settle()
        XCTAssertEqual(context.session.snapshot.intermediateStops, [context.stop])
        let requests = await context.http.requests()
        XCTAssertEqual(requests.count, 2)
    }

    @MainActor
    func testPassedStopIsPersistedAndExcludedFromReroute() async throws {
        let context = try StopContext()
        await context.start()
        context.session.addStop(context.stop)
        await context.settle()
        for x in [30.0, 60, 90, 115, 135] {
            context.elapsed += 2_000
            context.fix(x: x)
        }
        XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
        XCTAssertTrue(context.places.snapshot.resumeIntermediateStops.isEmpty)
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        for x in [145.0, 155, 165] { context.elapsed += 1_000; context.fix(x: x, y: 100) }
        await context.settle()
        let requests = await context.http.requests()
        XCTAssertEqual(requests.last?.split(separator: "|").count, 2)
        XCTAssertEqual(context.session.snapshot.destination, context.destination)
    }

    @MainActor
    func testStopAndNewDestinationIgnoreDelayedStopResponse() async throws {
        for replacement in [false, true] {
            let context = try StopContext()
            await context.start()
            await context.http.delayNext()
            context.session.addStop(context.stop)
            await context.settle()
            if replacement { context.session.selectDestination(context.other) }
            else { context.session.stopNavigation() }
            await context.http.completeDelayed()
            await context.settle()
            XCTAssertEqual(context.session.snapshot.destination, replacement ? context.other : nil)
            XCTAssertTrue(context.session.snapshot.intermediateStops.isEmpty)
            XCTAssertFalse(context.session.snapshot.updatingStops)
            XCTAssertEqual(context.session.snapshot.phase, replacement ? .preview : .idle)
        }
    }

    @MainActor
    func testResumeReloadsStopsAndRecalculatesWithoutOldGuidance() async throws {
        let context = try StopContext()
        await context.start()
        context.session.addStop(context.stop)
        await context.settle()
        let loaded = PlacesStore(fileURL: context.file)
        let restored = NavigationSession(places: loaded, apiKey: { "fixture" },
            providerFactory: { _ in GeoapifyProvider(apiKey: "fixture", transport: context.http) },
            elapsedClock: { context.elapsed }, wallClock: { 1_000_000 })
        XCTAssertEqual(restored.snapshot.resumeIntermediateStops, [context.stop])
        XCTAssertNil(restored.snapshot.route)
        restored.resumeDestination()
        restored.setLocationStatus(access: .waiting, running: true)
        restored.acceptFix(context.sample(x: 0, y: 0))
        await context.settle()
        XCTAssertEqual(restored.snapshot.phase, .preview)
        XCTAssertEqual(restored.snapshot.intermediateStops, [context.stop])
        XCTAssertEqual(restored.snapshot.route?.destination, context.destination)
        XCTAssertEqual(loaded.snapshot.resumeIntermediateStops, [context.stop])
    }
}

@MainActor
private final class StopContext {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent("Stops-\(UUID().uuidString)")
    var file: URL { directory.appendingPathComponent("places.json") }
    let http = StopHTTP()
    let places: PlacesStore
    var session: NavigationSession!
    var elapsed: Int64 = 100_000
    var key = "fixture"
    let destination = Destination(id: "final", name: "Final", point: GeoPoint(latitude: 0, longitude: 0.003))
    let stop = Destination(id: "stop", name: "Parada", point: GeoPoint(latitude: 0, longitude: 0.001))
    let other = Destination(id: "other", name: "Outro destino", point: GeoPoint(latitude: 0, longitude: 0.004))
    init() throws {
        places = PlacesStore(fileURL: directory.appendingPathComponent("places.json"))
        session = NavigationSession(places: places, apiKey: { [weak self] in self?.key ?? "" },
            providerFactory: { [http] _ in GeoapifyProvider(apiKey: "fixture", transport: http) },
            elapsedClock: { [weak self] in self?.elapsed ?? 0 }, wallClock: { 1_000_000 })
    }
    deinit { try? FileManager.default.removeItem(at: directory) }
    func start() async {
        session.setLocationStatus(access: .waiting, running: true)
        fix(x: 0)
        session.selectDestination(destination)
        await settle()
        session.startNavigation()
    }
    func sample(x: Double, y: Double) -> LocationFix {
        LocationFix(point: GeoPoint(latitude: y / 111_195, longitude: x / 111_195), accuracyMeters: 5,
            bearingDegrees: 90, speedMps: 15, speedAccuracyMps: 0.5, elapsedRealtimeMs: elapsed, wallTimeMs: 1_000_000)
    }
    func fix(x: Double, y: Double = 0) { session.acceptFix(sample(x: x, y: y)) }
    func settle() async { for _ in 0..<200 { await Task.yield() } }
}

private actor StopHTTP: HTTPTransport {
    var status = 200
    var waypoints: [String] = []
    var delay = false
    var delayed: (CheckedContinuation<HTTPResponse, Error>, HTTPResponse)?
    func setStatus(_ value: Int) { status = value }
    func delayNext() { delay = true }
    func completeDelayed() { let pending = delayed; delayed = nil; pending?.0.resume(returning: pending!.1) }
    func requests() -> [String] { waypoints }
    func get(_ url: URL) async throws -> HTTPResponse {
        let value = URLComponents(url: url, resolvingAgainstBaseURL: false)!.queryItems!.first { $0.name == "waypoints" }!.value!
        waypoints.append(value)
        let points = value.split(separator: "|").map { $0.split(separator: ",").map { Double($0)! } }
        let lines = (1..<points.count).map { [[points[$0 - 1][1], points[$0 - 1][0]], [points[$0][1], points[$0][0]]] }
        let legs: [[String: Any]] = lines.map { _ in
            ["distance": 150, "time": 20, "steps": [["from_index": 0, "to_index": 1, "distance": 150, "time": 20,
                "instruction": ["type": "Straight", "text": "Siga em frente"]]]]
        }
        let json: [String: Any] = ["type": "FeatureCollection", "features": [["type": "Feature",
            "geometry": ["type": "MultiLineString", "coordinates": lines],
            "properties": ["distance": 150 * legs.count, "time": 20 * legs.count, "legs": legs]]]]
        let response = HTTPResponse(statusCode: status, body: try JSONSerialization.data(withJSONObject: json))
        if delay { delay = false; return try await withCheckedThrowingContinuation { delayed = ($0, response) } }
        return response
    }
}
