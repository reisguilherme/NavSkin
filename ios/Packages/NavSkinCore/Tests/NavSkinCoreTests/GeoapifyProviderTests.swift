import Foundation
import XCTest
@testable import NavSkinCore

final class GeoapifyProviderTests: XCTestCase {
    private let origin = GeoPoint(latitude: -16.68, longitude: -49.25)
    private let destination = Destination(id: "saved:1", name: "Destino", point: GeoPoint(latitude: -16.679, longitude: -49.248), address: "Goiânia")

    func testRouteFlattensSharedLegEndpointsAndKeepsInstructionStartIndicesAndSeconds() async throws {
        let transport = FixtureTransport([response(Self.multiLeg)])
        let route = try await GeoapifyProvider(apiKey: "private-test-key", transport: transport, clock: { 1234 }).route(origin: origin, destination: destination)
        XCTAssertEqual(route.geometry.count, 4)
        XCTAssertEqual(route.geometry.last, destination.point)
        XCTAssertEqual(route.distanceMeters, 300)
        XCTAssertEqual(route.durationSeconds, 20)
        XCTAssertEqual(route.calculatedAtMs, 1234)
        XCTAssertEqual(route.steps.map(\.startIndex), [0, 2, 3])
        XCTAssertEqual(route.steps.map(\.endIndex), [2, 3, 3])
        XCTAssertEqual(route.steps.map(\.durationSeconds), [12, 8, 0])
        XCTAssertEqual(route.steps[1].kind, .right)
        XCTAssertEqual(route.steps[1].streetName, "Rua Dois")
        XCTAssertEqual(route.steps.last?.kind, .arrive)
        let urls = await transport.requestedURLs()
        let url = try XCTUnwrap(urls.first)
        XCTAssertEqual(url.scheme, "https")
        XCTAssertEqual(url.host, "api.geoapify.com")
        XCTAssertEqual(url.path, "/v1/routing")
        let query = try queryItems(url)
        XCTAssertEqual(query["waypoints"], "-16.68,-49.25|-16.679,-49.248")
        XCTAssertEqual(query["mode"], "drive")
        XCTAssertEqual(query["lang"], "pt-BR")
        XCTAssertEqual(query["details"], "instruction_details")
    }

    func testUnknownManeuverIsNeutralAndRoundaboutKeepsStructuredExit() async throws {
        let unknown = Self.multiLeg.replacingOccurrences(of: "\"Right\"", with: "\"FutureManeuver\"")
        let route = try await provider(unknown).route(origin: origin, destination: destination)
        XCTAssertEqual(route.steps[1].kind, .unknown)
        XCTAssertEqual(route.steps[1].startIndex, 2)
        XCTAssertEqual(route.steps[1].instruction, "Vire à direita na Rua Dois.")
        let roundabout = Self.multiLeg.replacingOccurrences(of: "\"Right\"", with: "\"Roundabout\"")
            .replacingOccurrences(of: "\"streets\":[\"Rua Dois\"]", with: "\"streets\":[\"Rua Dois\"],\"roundabout_exit\":3")
        let second = try await provider(roundabout).route(origin: origin, destination: destination)
        XCTAssertEqual(second.steps[1].kind, .roundabout)
        XCTAssertEqual(second.steps[1].exitNumber, 3)
    }

    func testMissingArrivalIsAppendedAtTheActualRouteEndpoint() async throws {
        let body = Self.multiLeg.replacingOccurrences(of: Self.arrival, with: "")
        let route = try await provider(body).route(origin: origin, destination: destination)
        XCTAssertEqual(route.steps.last?.kind, .arrive)
        XCTAssertEqual(route.steps.last?.startIndex, 3)
        XCTAssertEqual(route.steps.last?.endIndex, 3)
        XCTAssertEqual(route.steps.last?.durationSeconds, 0)
    }

    func testSearchCombinesRealAddressAndNearbyPOIWithLongitudeLatitudeBias() async throws {
        let transport = FixtureTransport([response(Self.address), response(Self.place)])
        let results = try await GeoapifyProvider(apiKey: "test", transport: transport).search(query: "Café Azul", bias: origin)
        XCTAssertEqual(results.map(\.name), ["Rua Um, 10", "Café Azul"])
        XCTAssertEqual(results[0].address, "Rua Um, 10, Goiânia, Brasil")
        XCTAssertEqual(results[0].point, origin)
        XCTAssertEqual(results[1].id, "poi:blue")
        let urls = await transport.requestedURLs()
        XCTAssertEqual(urls.map(\.path), ["/v1/geocode/search", "/v2/places"])
        XCTAssertEqual(try queryItems(urls[0])["bias"], "proximity:-49.25,-16.68")
        XCTAssertEqual(try queryItems(urls[1])["name"], "Café Azul")
        XCTAssertNotNil(try queryItems(urls[1])["categories"])
    }

    func testSearchWithoutGPSDoesNotInventBiasAndDuplicateIDsAppearOnce() async throws {
        let geocoder = FixtureTransport([response(Self.address)])
        let first = try await GeoapifyProvider(apiKey: "test", transport: geocoder).search(query: "Rua Um", bias: nil)
        XCTAssertEqual(first.count, 1)
        let urls = await geocoder.requestedURLs()
        XCTAssertEqual(urls.count, 1)
        XCTAssertNil(try queryItems(urls[0])["bias"])
        let duplicate = FixtureTransport([response(Self.place), response(Self.place)])
        let second = try await GeoapifyProvider(apiKey: "test", transport: duplicate).search(query: "Café", bias: origin)
        XCTAssertEqual(second.count, 1)
    }

    func testMissingProviderIDUsesTheSameStableUUIDAsAndroid() async throws {
        let body = Self.address.replacingOccurrences(of: "\"place_id\":\"address:1\",", with: "")
        let result = try await provider(body).search(query: "Rua Um", bias: nil)
        XCTAssertEqual(result.first?.id, "geoapify:5d15eb78-073f-3364-b533-b422fbfc1f3a")
    }

    func testReverseUsesLatitudeLongitudeParametersAndReturnsRealAddress() async throws {
        let transport = FixtureTransport([response(Self.address)])
        let address = try await GeoapifyProvider(apiKey: "test", transport: transport).reverse(point: origin)
        XCTAssertEqual(address, "Rua Um, 10, Goiânia, Brasil")
        let urls = await transport.requestedURLs()
        XCTAssertEqual(try queryItems(urls[0])["lat"], "-16.68")
        XCTAssertEqual(try queryItems(urls[0])["lon"], "-49.25")
    }

    func testMissingKeyAndInvalidCoordinatesStopBeforeTheNetwork() async {
        let transport = FixtureTransport([])
        await expect(.configuration) {
            _ = try await GeoapifyProvider(apiKey: "  ", transport: transport).route(origin: self.origin, destination: self.destination)
        }
        await expect(.invalidResponse) {
            _ = try await GeoapifyProvider(apiKey: "test", transport: transport).route(origin: GeoPoint(latitude: 91, longitude: 0), destination: self.destination)
        }
        let urls = await transport.requestedURLs()
        XCTAssertTrue(urls.isEmpty)
    }

    func testTypedErrorsKeepRetryAfterAndNeverRetainURLResponseOrTransportSecrets() async {
        let quota = GeoapifyProvider(apiKey: "private-test-key", transport: FixtureTransport([HTTPResponse(statusCode: 429, body: Data("key=private-test-key".utf8), headers: ["rEtRy-AfTeR": "45"])]))
        await expect(.rateLimit, retry: 45) { _ = try await quota.route(origin: self.origin, destination: self.destination) }
        for status in [401, 403] {
            let auth = GeoapifyProvider(apiKey: "private-test-key", transport: FixtureTransport([HTTPResponse(statusCode: status, body: Data("private-test-key".utf8))]))
            await expect(.authentication) { _ = try await auth.search(query: "Rua Um", bias: nil) }
        }
        let offline = GeoapifyProvider(apiKey: "private-test-key", transport: FailingTransport(error: URLError(.notConnectedToInternet)))
        await expect(.offline) { _ = try await offline.search(query: "Rua Um", bias: nil) }
        let timeout = GeoapifyProvider(apiKey: "private-test-key", transport: FailingTransport(error: URLError(.timedOut)))
        await expect(.unavailable) { _ = try await timeout.search(query: "Rua Um", bias: nil) }
    }

    func testQuotaForbiddenAndHTTPDateRetryAfterAreRecognized() async {
        let transport = FixtureTransport([HTTPResponse(statusCode: 403, body: Data("Daily limit exceeded".utf8), headers: ["Retry-After": "Thu, 01 Jan 1970 00:01:00 GMT"])])
        let provider = GeoapifyProvider(apiKey: "test", transport: transport, clock: { 15_000 })
        await expect(.rateLimit, retry: 45) { _ = try await provider.search(query: "Rua Um", bias: nil) }
    }

    func testEmptyResultsAndUnconnectedRouteHaveExplicitStates() async {
        await expect(.noResults) { _ = try await self.provider(Self.empty).search(query: "Rua Um", bias: nil) }
        await expect(.noRoute) { _ = try await self.provider(Self.empty).route(origin: self.origin, destination: self.destination) }
        let rejected = GeoapifyProvider(apiKey: "test", transport: FixtureTransport([HTTPResponse(statusCode: 400, body: Data("Locations are in unconnected regions.".utf8))]))
        await expect(.noRoute) { _ = try await rejected.route(origin: self.origin, destination: self.destination) }
    }

    func testMalformedCoordinatesIntervalsDurationsAndDisconnectedLegsCannotProduceGuidance() async {
        let bodies = ["not JSON", Self.multiLeg.replacingOccurrences(of: "-49.249", with: "\"NaN\""),
            Self.multiLeg.replacingOccurrences(of: "-16.679", with: "91.0"),
            Self.multiLeg.replacingOccurrences(of: "\"to_index\":2", with: "\"to_index\":99"),
            Self.multiLeg.replacingOccurrences(of: "\"time\":12", with: "\"time\":-12"),
            Self.multiLeg.replacingOccurrences(of: "\"from_index\":0", with: "\"from_index\":0.5"),
            Self.multiLeg.replacingOccurrences(of: "[[-49.249,-16.679],[-49.248", with: "[[-48.249,-15.679],[-49.248"),
            Self.multiLeg.replacingOccurrences(of: "-49.249", with: "-49.25").replacingOccurrences(of: "-49.248", with: "-49.25").replacingOccurrences(of: "-16.679", with: "-16.68")]
        for body in bodies {
            await expect(.invalidResponse) { _ = try await self.provider(body).route(origin: self.origin, destination: self.destination) }
        }
    }

    func testOversizedResponseIsRejectedEvenWithInjectedTransport() async {
        let transport = FixtureTransport([HTTPResponse(statusCode: 200, body: Data(repeating: 32, count: 4 * 1024 * 1024 + 1))])
        await expect(.invalidResponse) { _ = try await GeoapifyProvider(apiKey: "test", transport: transport).search(query: "Rua Um", bias: nil) }
    }

    func testDefaultTransportRejectsCleartextForeignHostsCredentialsAndPortsBeforeConnecting() async {
        for url in ["http://api.geoapify.com/v1/routing?apiKey=secret", "https://example.com/", "https://user@api.geoapify.com/", "https://api.geoapify.com:8443/"] {
            await expect(.configuration) { _ = try await URLSessionTransport().get(try XCTUnwrap(URL(string: url))) }
        }
    }

    private func response(_ body: String) -> HTTPResponse { HTTPResponse(statusCode: 200, body: Data(body.utf8)) }
    private func provider(_ body: String) -> GeoapifyProvider { GeoapifyProvider(apiKey: "private-test-key", transport: FixtureTransport([response(body)])) }
    private func queryItems(_ url: URL) throws -> [String: String] {
        let components = try XCTUnwrap(URLComponents(url: url, resolvingAgainstBaseURL: false))
        return Dictionary(uniqueKeysWithValues: (components.queryItems ?? []).map { ($0.name, $0.value ?? "") })
    }
    private func expect(_ kind: ProviderErrorKind, retry: Int64? = nil, operation: () async throws -> Void) async {
        do { try await operation(); XCTFail("Expected \(kind)") }
        catch let error as TypedProviderException {
            XCTAssertEqual(error.kind, kind)
            XCTAssertEqual(error.retryAfterSeconds, retry)
            XCTAssertFalse(String(describing: error).contains("private-test-key"))
            XCTAssertFalse(error.localizedDescription.contains("private-test-key"))
        } catch { XCTFail("Unexpected error type: \(type(of: error))") }
    }

    // Fixtures follow Geoapify's GeoJSON schema; no request to the live service is made.
    private static let empty = #"{"type":"FeatureCollection","features":[]}"#
    private static let address = #"{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-49.25,-16.68]},"properties":{"place_id":"address:1","address_line1":"Rua Um, 10","address_line2":"Goiânia, Brasil","formatted":"Rua Um, 10, Goiânia, Brasil","lat":-16.68,"lon":-49.25,"result_type":"building","rank":{"confidence":1}}}]}"#
    private static let place = #"{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-49.248,-16.679]},"properties":{"place_id":"poi:blue","name":"Café Azul","address_line1":"Café Azul","address_line2":"Rua Dois, Goiânia","formatted":"Café Azul, Rua Dois, Goiânia","categories":["catering.cafe"],"lat":-16.679,"lon":-49.248}}]}"#
    private static let arrival = #",{"from_index":1,"to_index":1,"distance":0,"time":0,"instruction":{"type":"DestinationReached","text":"Você chegou."}}"#
    private static let multiLeg = #"{"type":"FeatureCollection","properties":{"mode":"drive","units":"metric"},"features":[{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[-49.25,-16.68],[-49.249,-16.68],[-49.249,-16.679]],[[-49.249,-16.679],[-49.248,-16.679]]]},"properties":{"mode":"drive","units":"metric","distance":300,"distance_units":"meters","time":20,"legs":[{"distance":200,"time":12,"steps":[{"from_index":0,"to_index":2,"distance":200,"time":12,"instruction":{"type":"StartAt","text":"Siga na Rua Um.","streets":["Rua Um"]}}]},{"distance":100,"time":8,"steps":[{"from_index":0,"to_index":1,"distance":100,"time":8,"instruction":{"type":"Right","text":"Vire à direita na Rua Dois.","streets":["Rua Dois"]}},{"from_index":1,"to_index":1,"distance":0,"time":0,"instruction":{"type":"DestinationReached","text":"Você chegou."}}]}]}}]}"#
}

private actor FixtureTransport: HTTPTransport {
    private var responses: [HTTPResponse]
    private var urls: [URL] = []
    init(_ responses: [HTTPResponse]) { self.responses = responses }
    func get(_ url: URL) async throws -> HTTPResponse {
        urls.append(url)
        guard !responses.isEmpty else { throw URLError(.badServerResponse) }
        return responses.removeFirst()
    }
    func requestedURLs() -> [URL] { urls }
}

private struct FailingTransport: HTTPTransport {
    let error: URLError
    func get(_ url: URL) async throws -> HTTPResponse { throw error }
}
