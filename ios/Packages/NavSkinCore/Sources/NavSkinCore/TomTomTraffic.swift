import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public struct TrafficIncident: Equatable, Sendable, Identifiable {
    public let id: String
    public let category: String
    public let description: String
    public let points: [GeoPoint]
}

public enum TomTomTraffic {
    public enum Failure: Error { case configuration, response }

    public static func request(apiKey: String, center: GeoPoint) throws -> URLRequest {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, key.count <= 512, !key.contains("\r"), !key.contains("\n"),
              center.isValidMapPoint else { throw Failure.configuration }
        let latitudeRadius = 2_500.0 / 111_320
        let longitudeRadius = latitudeRadius / max(0.08, cos(center.latitude * .pi / 180))
        let bounds = [max(-180, center.longitude - longitudeRadius), max(-85, center.latitude - latitudeRadius),
                      min(180, center.longitude + longitudeRadius), min(85, center.latitude + latitudeRadius)]
        guard bounds[0] < bounds[2], bounds[1] < bounds[3] else { throw Failure.configuration }
        var components = URLComponents(string: "https://api.tomtom.com/maps/orbis/traffic/incidents/details")!
        components.queryItems = [URLQueryItem(name: "apiVersion", value: "2"),
            URLQueryItem(name: "bbox", value: bounds.map { String($0) }.joined(separator: ",")),
            URLQueryItem(name: "timeValidity", value: "present")]
        var request = URLRequest(url: components.url!, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 15)
        request.setValue(key, forHTTPHeaderField: "TomTom-Api-Key")
        request.setValue("pt-BR", forHTTPHeaderField: "Accept-Language")
        request.setValue("incidents(type,geometry(type,coordinates),properties(id,iconCategory,events(description)))", forHTTPHeaderField: "Attributes")
        return request
    }

    public static func decode(_ data: Data) throws -> [TrafficIncident] {
        guard data.count <= 2_000_000,
              let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let incidents = root["incidents"] as? [[String: Any]] else { throw Failure.response }
        return incidents.prefix(250).compactMap { incident in
            guard let geometry = incident["geometry"] as? [String: Any],
                  let properties = incident["properties"] as? [String: Any],
                  let id = properties["id"] as? String, !id.isEmpty else { return nil }
            let coordinates: [[Double]]
            if geometry["type"] as? String == "Point", let point = geometry["coordinates"] as? [Double] {
                coordinates = [point]
            } else if geometry["type"] as? String == "LineString", let line = geometry["coordinates"] as? [[Double]] {
                coordinates = Array(line.prefix(2_000))
            } else { return nil }
            let points = coordinates.compactMap { value -> GeoPoint? in
                guard value.count >= 2 else { return nil }
                let point = GeoPoint(latitude: value[1], longitude: value[0])
                return point.isValidMapPoint ? point : nil
            }
            guard !points.isEmpty, points.count == coordinates.count else { return nil }
            let description = (properties["events"] as? [[String: Any]])?.first?["description"] as? String ?? "Ocorrência de trânsito"
            return TrafficIncident(id: String(id.prefix(160)), category: properties["iconCategory"] as? String ?? "unknown",
                description: String(description.prefix(240)), points: points)
        }
    }
}

public struct TrafficRefreshPolicy: Sendable {
    private var nextAllowed: Int64 = 0
    public init() {}
    public mutating func begin(enabled: Bool, configured: Bool, foreground: Bool, zoom: Double, now: Int64) -> Bool {
        guard enabled, configured, foreground, zoom.isFinite, zoom >= 12, now >= nextAllowed else { return false }
        nextAllowed = now + 120_000
        return true
    }
    public mutating func backOff(now: Int64) { nextAllowed = now + 300_000 }
    public static func canDisplay(zoom: Double, receivedAt: Int64, now: Int64) -> Bool {
        zoom.isFinite && zoom >= 12 && now >= receivedAt && Double(now) - Double(receivedAt) <= 300_000
    }
}
