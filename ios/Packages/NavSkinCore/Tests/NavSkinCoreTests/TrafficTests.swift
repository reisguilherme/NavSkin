import XCTest
@testable import NavSkinCore

final class TrafficTests: XCTestCase {
    func testIncidentRequestUsesHeaderAuthenticationAndSmallLocalBounds() throws {
        let request = try TomTomTraffic.request(apiKey: "local-test-only", center: GeoPoint(latitude: -16.68, longitude: -49.25))
        XCTAssertEqual(request.value(forHTTPHeaderField: "TomTom-Api-Key"), "local-test-only")
        XCTAssertFalse(try XCTUnwrap(request.url?.absoluteString).contains("local-test-only"))
        let query = try XCTUnwrap(URLComponents(url: XCTUnwrap(request.url), resolvingAgainstBaseURL: false)?.queryItems)
        let bounds = try XCTUnwrap(query.first(where: { $0.name == "bbox" })?.value).split(separator: ",").compactMap { Double($0) }
        XCTAssertEqual(bounds.count, 4)
        XCTAssertLessThan(bounds[0], -49.25)
        XCTAssertGreaterThan(bounds[2], -49.25)
        XCTAssertLessThan(bounds[2] - bounds[0], 0.1)
        XCTAssertLessThan(bounds[3] - bounds[1], 0.1)
        XCTAssertThrowsError(try TomTomTraffic.request(apiKey: " ", center: GeoPoint(latitude: 0, longitude: 0)))
        XCTAssertThrowsError(try TomTomTraffic.request(apiKey: "key", center: GeoPoint(latitude: .nan, longitude: 0)))
    }

    func testOrbisTextCategoriesDecodeAndInvalidGeometryIsRejected() throws {
        let data = Data("""
        {"incidents":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[-49.25,-16.68]},"properties":{"id":"a","iconCategory":"accident","events":[{"description":"Acidente"}]}},
          {"type":"Feature","geometry":{"type":"LineString","coordinates":[[-49.25,-16.68],[-49.24,-16.68]]},"properties":{"id":"j","iconCategory":"jam"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[190,100]},"properties":{"id":"bad","iconCategory":"roadClosed"}}
        ]}
        """.utf8)
        let incidents = try TomTomTraffic.decode(data)
        XCTAssertEqual(incidents.count, 2)
        XCTAssertEqual(incidents[0].category, "accident")
        XCTAssertEqual(incidents[0].description, "Acidente")
        XCTAssertEqual(incidents[1].points.count, 2)
        XCTAssertThrowsError(try TomTomTraffic.decode(Data("{}".utf8)))
        XCTAssertEqual(try TomTomTraffic.decode(Data("{\"incidents\":[]}".utf8)), [])
    }

    func testRefreshPolicyBlocksDisabledUnconfiguredBackgroundAndRepeatedRequests() {
        var policy = TrafficRefreshPolicy()
        XCTAssertFalse(policy.begin(enabled: false, configured: true, foreground: true, zoom: 15, now: 0))
        XCTAssertFalse(policy.begin(enabled: true, configured: false, foreground: true, zoom: 15, now: 0))
        XCTAssertFalse(policy.begin(enabled: true, configured: true, foreground: false, zoom: 15, now: 0))
        XCTAssertFalse(policy.begin(enabled: true, configured: true, foreground: true, zoom: 8, now: 0))
        XCTAssertTrue(policy.begin(enabled: true, configured: true, foreground: true, zoom: 15, now: 0))
        XCTAssertFalse(policy.begin(enabled: true, configured: true, foreground: true, zoom: 15, now: 119_999))
        XCTAssertTrue(policy.begin(enabled: true, configured: true, foreground: true, zoom: 15, now: 120_000))
        policy.backOff(now: 120_000)
        XCTAssertFalse(policy.begin(enabled: true, configured: true, foreground: true, zoom: 15, now: 240_000))
        XCTAssertTrue(policy.begin(enabled: true, configured: true, foreground: true, zoom: 15, now: 420_000))
    }

    func testIncidentsCannotBePresentedAfterZoomOutExpiryOrClockRollback() {
        XCTAssertTrue(TrafficRefreshPolicy.canDisplay(zoom: 15, receivedAt: 1_000, now: 301_000))
        XCTAssertFalse(TrafficRefreshPolicy.canDisplay(zoom: 11.9, receivedAt: 1_000, now: 2_000))
        XCTAssertFalse(TrafficRefreshPolicy.canDisplay(zoom: 15, receivedAt: 1_000, now: 301_001))
        XCTAssertFalse(TrafficRefreshPolicy.canDisplay(zoom: 15, receivedAt: 1_000, now: 0))
        XCTAssertFalse(TrafficRefreshPolicy.canDisplay(zoom: .nan, receivedAt: 1_000, now: 2_000))
    }
}
