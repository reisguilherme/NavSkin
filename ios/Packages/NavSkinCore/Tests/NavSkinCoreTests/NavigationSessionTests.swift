import Foundation
import XCTest
@testable import NavSkinCore

final class NavigationSessionTests: XCTestCase {
    @MainActor
    func testSelectedDestinationWaitsForCurrentGPSAndAutomaticallyPreviews() async throws {
        let context = try SessionContext()
        var starts = 0
        context.session.startLocation = { starts += 1 }
        context.session.selectDestination(context.destination("A"))
        XCTAssertEqual(starts, 1)
        XCTAssertEqual(context.session.snapshot.destination?.id, "A")
        XCTAssertNil(context.session.snapshot.route)
        context.session.setLocationStatus(access: .waiting, running: true)
        context.fix()
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .preview)
        XCTAssertEqual(context.session.snapshot.route?.destination.id, "A")
        XCTAssertFalse(context.session.snapshot.navigationActive)
    }

    @MainActor
    func testUninterruptibleOlderRouteCannotReplaceNewDestinationOrResurrectAfterStop() async throws {
        let delayed = DelayedSessionProvider()
        let context = try SessionContext(provider: delayed)
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.selectDestination(context.destination("B"))
        await settle()
        await delayed.completeRoute(id: "B")
        await settle()
        XCTAssertEqual(context.session.snapshot.route?.destination.id, "B")
        await delayed.completeRoute(id: "A")
        await settle()
        XCTAssertEqual(context.session.snapshot.route?.destination.id, "B")
        context.session.selectDestination(context.destination("C"))
        await settle()
        context.session.stopNavigation()
        await delayed.completeRoute(id: "C")
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .idle)
        XCTAssertNil(context.session.snapshot.route)
        XCTAssertNil(context.session.snapshot.destination)
    }

    @MainActor
    func testStartRequiresActuallyRunningSourceAndPreciseCurrentFix() async throws {
        let context = try SessionContext()
        context.session.setLocationStatus(access: .available, running: false)
        context.fix()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        XCTAssertEqual(context.session.snapshot.phase, .preview)
        context.session.setLocationStatus(access: .available, running: true)
        context.session.startNavigation()
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
        XCTAssertEqual(context.places.snapshot.resumeDestination?.id, "A")
        context.session.setLocationStatus(access: .weak, running: true)
        XCTAssertFalse(context.session.snapshot.guidanceAvailable)
        XCTAssertNil(context.session.snapshot.location.speedKmh)
    }

    @MainActor
    func testSourceDeathOffersResumeWithoutOldRouteAndFreshGPSOnlyClearsOwnedNotice() async throws {
        let context = try SessionContext()
        context.session.activateGPS()
        context.ready()
        XCTAssertNil(context.session.snapshot.message)
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.session.setLocationStatus(access: .needsPhoneStart, running: false, message: "GPS interrompido")
        XCTAssertEqual(context.session.snapshot.phase, .resumeAvailable)
        XCTAssertNil(context.session.snapshot.route)
        XCTAssertNil(context.session.snapshot.progress)
        XCTAssertEqual(context.session.snapshot.resumeDestination?.id, "A")
        context.session.setLocationStatus(access: .waiting, running: true)
        context.elapsed += 1000
        context.fix()
        XCTAssertNil(context.session.snapshot.message)
    }

    @MainActor
    func testQuotaBlocksRepeatedRequestsUntilMonotonicCooldownExpiresAndRetryRecovers() async throws {
        let provider = RecoveringSessionProvider()
        let context = try SessionContext(provider: provider)
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .error)
        context.session.retryRoute()
        await settle()
        let count = await provider.routeCount()
        XCTAssertEqual(count, 1)
        await provider.recover()
        context.elapsed += 31_000
        context.fix()
        context.session.retryRoute()
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .preview)
    }

    @MainActor
    func testStopAndKeyChangeInvalidateUninterruptibleSearchResults() async throws {
        let provider = DelayedSessionProvider()
        let context = try SessionContext(provider: provider)
        context.session.search("one")
        await settle()
        context.session.search("two")
        await settle()
        await provider.completeSearch(query: "two")
        await settle()
        await provider.completeSearch(query: "one")
        await settle()
        XCTAssertEqual(context.session.snapshot.searchResults.first?.name, "two")
        context.session.search("three")
        await settle()
        context.session.stopNavigation()
        await provider.completeSearch(query: "three")
        await settle()
        XCTAssertFalse(context.session.snapshot.searching)
        XCTAssertTrue(context.session.snapshot.searchResults.isEmpty)
        context.session.search("four")
        await settle()
        context.key = "replacement"
        context.session.keyChanged()
        await provider.completeSearch(query: "four")
        await settle()
        XCTAssertTrue(context.session.snapshot.searchResults.isEmpty)
    }

    @MainActor
    func testStaleGPSHidesGuidanceETASpeedAndDisabledStatusSuppressesImmediately() async throws {
        let context = try SessionContext()
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.elapsed += 1000
        context.fix(x: 10)
        XCTAssertTrue(context.session.snapshot.guidanceAvailable)
        XCTAssertNotNil(context.session.snapshot.location.speedKmh)
        context.elapsed += 6000
        context.session.tick()
        XCTAssertNil(context.session.snapshot.progress)
        XCTAssertNil(context.session.snapshot.location.speedKmh)
        context.elapsed += 1000
        context.fix(x: 20)
        XCTAssertTrue(context.session.snapshot.guidanceAvailable)
        context.session.setLocationStatus(access: .disabled, running: true, message: "Localização desativada")
        XCTAssertNil(context.session.snapshot.progress)
        XCTAssertNil(context.session.snapshot.location.speedKmh)
        XCTAssertEqual(context.session.snapshot.locationAccess, .disabled)
    }

    @MainActor
    func testBackgroundKeepsRealNavigationButStopsReplayAndClearsSimulatedPosition() async throws {
        let context = try SessionContext()
        var stops = 0
        context.session.stopLocation = { stops += 1 }
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.session.setForeground(false)
        XCTAssertEqual(stops, 0)
        context.session.setForeground(true)
        context.session.startReplay(route: sessionRoute(context.destination("A")), fixes: [context.locationFix(x: 30)])
        XCTAssertTrue(context.session.snapshot.simulation)
        XCTAssertEqual(context.session.snapshot.location.fix?.isMock, true)
        XCTAssertTrue(context.session.snapshot.message?.hasPrefix("SIMULAÇÃO") == true)
        context.session.setForeground(false)
        XCTAssertFalse(context.session.snapshot.simulation)
        XCTAssertNil(context.session.snapshot.location.fix)
        XCTAssertFalse(context.session.snapshot.navigationActive)
        let before = context.session.snapshot.route
        XCTAssertNil(before)
    }

    @MainActor
    func testCurrentGPSDoesNotEraseNewerProviderErrorAndRetryRecovers() async throws {
        let provider = RecoveringSessionProvider(kind: .unavailable)
        let context = try SessionContext(provider: provider)
        context.ready()
        context.session.setLocationStatus(access: .waiting, running: true, message: "Aviso temporário")
        context.session.selectDestination(context.destination("A"))
        await settle()
        let message = context.session.snapshot.message
        context.elapsed += 1000
        context.fix()
        XCTAssertEqual(context.session.snapshot.message, message)
        await provider.recover()
        context.session.retryRoute()
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .preview)
    }

    @MainActor
    func testArrivalClearsBothPersistedAndVisibleResumeDestination() async throws {
        let context = try SessionContext()
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.elapsed += 1000; context.fix(x: 10)
        context.elapsed += 5000; context.fix(x: 60)
        context.elapsed += 4000; context.fix(x: 102)
        context.elapsed += 1000; context.fix(x: 109)
        XCTAssertEqual(context.session.snapshot.phase, .arrived)
        XCTAssertNil(context.places.snapshot.resumeDestination)
        XCTAssertNil(context.session.snapshot.resumeDestination)
    }

    @MainActor
    func testPermissionRevocationSuspendsGuidanceAndRetainsOnlyDestinationForResume() async throws {
        let context = try SessionContext()
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.elapsed += 1000; context.fix(x: 10)
        XCTAssertTrue(context.session.snapshot.guidanceAvailable)
        context.session.setLocationStatus(access: .denied, running: false)
        XCTAssertEqual(context.session.snapshot.phase, .resumeAvailable)
        XCTAssertNil(context.session.snapshot.route)
        XCTAssertNil(context.session.snapshot.progress)
        XCTAssertNil(context.session.snapshot.location.speedKmh)
        XCTAssertEqual(context.session.snapshot.resumeDestination?.id, "A")
    }

    @MainActor
    func testLocationFailureHidesOldGuidanceUntilANewValidSampleRecovers() async throws {
        let context = try SessionContext()
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.elapsed += 1000; context.fix(x: 10)
        context.session.setLocationStatus(access: .lost, running: true, message: "Sinal indisponível")
        XCTAssertNil(context.session.snapshot.progress)
        XCTAssertNil(context.session.snapshot.location.speedKmh)
        XCTAssertEqual(context.session.snapshot.locationAccess, .lost)
        context.session.tick()
        XCTAssertEqual(context.session.snapshot.message, "Sinal indisponível")
        context.fix(x: 10) // Replayed sample timestamp cannot recover the source.
        XCTAssertEqual(context.session.snapshot.locationAccess, .lost)
        context.elapsed += 1000; context.fix(x: 20)
        XCTAssertEqual(context.session.snapshot.locationAccess, .available)
        XCTAssertNil(context.session.snapshot.message)
        XCTAssertTrue(context.session.snapshot.guidanceAvailable)
    }

    @MainActor
    func testMockFixesAreRejectedByDefaultAndExplicitDebugPolicyUsesRealProviderWithoutReplayMode() async throws {
        let transport = SessionHTTPFixture()
        let provider = GeoapifyProvider(apiKey: "fixture-test-key", transport: transport)
        let production = try SessionContext(provider: provider)
        production.session.setLocationStatus(access: .waiting, running: true)
        var mock = production.locationFix()
        mock.point = GeoPoint(latitude: 0, longitude: 0.0001)
        mock.isMock = true
        production.session.acceptFix(mock)
        production.session.selectDestination(production.destination("A"))
        await settle()
        XCTAssertNil(production.session.snapshot.location.fix)
        XCTAssertNil(production.session.snapshot.route)
        var urls = await transport.requestedURLs()
        XCTAssertTrue(urls.isEmpty)

        let debug = try SessionContext(provider: provider, allowSimulatedLocations: true)
        debug.session.setLocationStatus(access: .waiting, running: true)
        debug.session.acceptFix(mock)
        debug.session.selectDestination(debug.destination("A"))
        await settle()
        XCTAssertEqual(debug.session.snapshot.phase, .preview)
        XCTAssertFalse(debug.session.snapshot.simulation)
        XCTAssertEqual(debug.session.snapshot.location.fix?.isMock, true)
        urls = await transport.requestedURLs()
        XCTAssertEqual(urls.count, 1)
        let components = try XCTUnwrap(URLComponents(url: try XCTUnwrap(urls.first), resolvingAgainstBaseURL: false))
        XCTAssertEqual(components.queryItems?.first(where: { $0.name == "waypoints" })?.value, "0.0,0.0001|0.0,0.001")
    }

    @MainActor
    func testExplicitReplaySearchClearsResultsAndExplainsSimulationWithoutAnyHTTP() async throws {
        let transport = SessionHTTPFixture()
        let context = try SessionContext(provider: GeoapifyProvider(apiKey: "fixture-test-key", transport: transport))
        context.session.startReplay(route: sessionRoute(context.destination("A")), fixes: [context.locationFix(x: 30)])
        context.session.search("Rua Um")
        await settle()
        XCTAssertTrue(context.session.snapshot.simulation)
        XCTAssertFalse(context.session.snapshot.searching)
        XCTAssertTrue(context.session.snapshot.searchResults.isEmpty)
        XCTAssertTrue(context.session.snapshot.searchError?.hasPrefix("SIMULAÇÃO") == true)
        let urls = await transport.requestedURLs()
        XCTAssertTrue(urls.isEmpty)
        context.session.stopReplay()
    }

    @MainActor
    func testKeyRemovalInvalidatesAnOutstandingRouteCompletion() async throws {
        let provider = DelayedSessionProvider()
        let context = try SessionContext(provider: provider)
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.key = ""
        context.session.keyChanged()
        await provider.completeRoute(id: "A")
        await settle()
        XCTAssertFalse(context.session.snapshot.providerConfigured)
        XCTAssertNil(context.session.snapshot.route)
        XCTAssertEqual(context.session.snapshot.phase, .error)
    }

    @MainActor
    func testFailedRecalculationKeepsOldGuidanceHiddenUntilSuccessfulReplacement() async throws {
        let provider = RecoveringSessionProvider(kind: .unavailable)
        await provider.recover()
        let context = try SessionContext(provider: provider)
        context.ready()
        context.session.selectDestination(context.destination("A"))
        await settle()
        context.session.startNavigation()
        context.elapsed += 1000; context.fix(x: 10)
        await provider.failAgain()
        for _ in 0..<3 { context.elapsed += 1000; context.fix(x: 20, y: 80) }
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .rerouteFailed)
        XCTAssertNil(context.session.snapshot.progress)
        context.elapsed += 1000; context.fix(x: 30, y: 80)
        XCTAssertFalse(context.session.snapshot.guidanceAvailable)
        await provider.recover()
        context.session.retryRoute()
        await settle()
        XCTAssertEqual(context.session.snapshot.phase, .navigating)
    }

    @MainActor
    private func settle() async { for _ in 0..<80 { await Task.yield() } }
}

@MainActor
private final class SessionContext {
    let directory: URL
    let places: PlacesStore
    var elapsed: Int64 = 100_000
    var key = "fixture-test-key"
    var session: NavigationSession!
    init(provider: any NavigationProviding = ImmediateSessionProvider(), allowSimulatedLocations: Bool = false) throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("NavSkinSession-\(UUID().uuidString)")
        places = PlacesStore(fileURL: directory.appendingPathComponent("places.json"))
        session = NavigationSession(places: places, apiKey: { [weak self] in self?.key ?? "" },
            providerFactory: { _ in provider }, elapsedClock: { [weak self] in self?.elapsed ?? 0 }, wallClock: { 1_000_000 },
            allowSimulatedLocations: allowSimulatedLocations)
    }
    deinit { try? FileManager.default.removeItem(at: directory) }
    func destination(_ id: String) -> Destination { Destination(id: id, name: "Destination \(id)", point: GeoPoint(latitude: 0, longitude: id == "A" ? 0.001 : 0.002)) }
    func ready() { session.setLocationStatus(access: .waiting, running: true); fix() }
    func fix(x: Double = 0, y: Double = 0) { session.acceptFix(locationFix(x: x, y: y)) }
    func locationFix(x: Double = 0, y: Double = 0) -> LocationFix {
        LocationFix(point: GeoPoint(latitude: y / 111_195, longitude: x / 111_195), accuracyMeters: 5,
                    bearingDegrees: 90, speedMps: 10, speedAccuracyMps: 0.5, elapsedRealtimeMs: elapsed, wallTimeMs: 1_000_000)
    }
}

private func sessionRoute(_ destination: Destination) -> NavRoute {
    NavRoute(id: "route-\(destination.id)", geometry: [GeoPoint(latitude: 0, longitude: 0), destination.point],
             steps: [], distanceMeters: 111.2, durationSeconds: 60, destination: destination, calculatedAtMs: 1_000_000)
}

private struct ImmediateSessionProvider: NavigationProviding {
    func search(query: String, bias: GeoPoint?) async throws -> [Destination] { [Destination(id: query, name: query, point: GeoPoint(latitude: 0, longitude: 0.001))] }
    func route(origin: GeoPoint, destination: Destination) async throws -> NavRoute { sessionRoute(destination) }
}

/// Ignores cancellation intentionally, like an HTTP completion already enqueued by the OS.
private actor DelayedSessionProvider: NavigationProviding {
    private var routes: [String: (Destination, CheckedContinuation<NavRoute, Error>)] = [:]
    private var searches: [String: CheckedContinuation<[Destination], Error>] = [:]
    func search(query: String, bias: GeoPoint?) async throws -> [Destination] {
        try await withCheckedThrowingContinuation { searches[query] = $0 }
    }
    func route(origin: GeoPoint, destination: Destination) async throws -> NavRoute {
        try await withCheckedThrowingContinuation { routes[destination.id] = (destination, $0) }
    }
    func completeRoute(id: String) {
        guard let (destination, continuation) = routes.removeValue(forKey: id) else { return }
        continuation.resume(returning: sessionRoute(destination))
    }
    func completeSearch(query: String) {
        searches.removeValue(forKey: query)?.resume(returning: [Destination(id: query, name: query, point: GeoPoint(latitude: 0, longitude: 0))])
    }
}

private actor RecoveringSessionProvider: NavigationProviding {
    private var failing = true
    private var requests = 0
    private let kind: ProviderErrorKind
    init(kind: ProviderErrorKind = .rateLimit) { self.kind = kind }
    func search(query: String, bias: GeoPoint?) async throws -> [Destination] { [] }
    func route(origin: GeoPoint, destination: Destination) async throws -> NavRoute {
        requests += 1
        if failing { throw TypedProviderException(kind: kind, retryAfterSeconds: kind == .rateLimit ? 30 : nil) }
        return sessionRoute(destination)
    }
    func recover() { failing = false }
    func failAgain() { failing = true }
    func routeCount() -> Int { requests }
}

private actor SessionHTTPFixture: HTTPTransport {
    private var urls: [URL] = []
    func get(_ url: URL) async throws -> HTTPResponse {
        urls.append(url)
        return HTTPResponse(statusCode: 200, body: Data(#"{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[0.0001,0],[0.001,0]]]},"properties":{"distance":100,"time":60,"legs":[{"distance":100,"time":60,"steps":[{"from_index":0,"to_index":1,"distance":100,"time":60,"instruction":{"type":"StartAt","text":"Siga em frente."}}]}]}}]}"#.utf8))
    }
    func requestedURLs() -> [URL] { urls }
}
