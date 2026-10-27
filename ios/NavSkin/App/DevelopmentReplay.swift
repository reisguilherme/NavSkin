import Foundation
import NavSkinCore

/// Original development geometry. It is never returned by the HTTP provider.
enum DevelopmentReplay {
    private static let points = [GeoPoint(latitude: -16.6817, longitude: -49.2596),
        GeoPoint(latitude: -16.6817, longitude: -49.2578), GeoPoint(latitude: -16.6808, longitude: -49.2578),
        GeoPoint(latitude: -16.6808, longitude: -49.2562)]
    static var route: NavRoute {
        let distances = zip(points, points.dropFirst()).map { GeoMath.distanceMeters($0.0, $0.1) }
        return NavRoute(id: "development-fixture", geometry: points, steps: [
            RouteStep(id: 0, instruction: "Siga em frente · SIMULAÇÃO", streetName: "Trajeto de desenvolvimento", kind: .depart, startIndex: 0, endIndex: 1, distanceMeters: distances[0], durationSeconds: 20),
            RouteStep(id: 1, instruction: "Vire à esquerda", streetName: "Trecho de teste", kind: .left, startIndex: 1, endIndex: 2, distanceMeters: distances[1], durationSeconds: 15),
            RouteStep(id: 2, instruction: "Vire à direita", streetName: "Trecho final", kind: .right, startIndex: 2, endIndex: 3, distanceMeters: distances[2], durationSeconds: 20),
            RouteStep(id: 3, instruction: "Chegada · SIMULAÇÃO", streetName: "Destino de teste", kind: .arrive, startIndex: 3, endIndex: 3, distanceMeters: 0, durationSeconds: 0)],
            distanceMeters: distances.reduce(0, +), durationSeconds: 55,
            destination: Destination(id: "simulation-destination", name: "Destino SIMULAÇÃO", point: points.last!), calculatedAtMs: 0)
    }
    static func fixes() -> [LocationFix] {
        var fixes: [LocationFix] = []
        for (from, to) in zip(points, points.dropFirst()) {
            for index in 0..<20 {
                let fraction = Double(index) / 20
                fixes.append(LocationFix(point: GeoPoint(latitude: from.latitude + (to.latitude - from.latitude) * fraction,
                    longitude: from.longitude + (to.longitude - from.longitude) * fraction), accuracyMeters: 5,
                    bearingDegrees: GeoMath.bearingDegrees(from, to), speedMps: GeoMath.distanceMeters(from, to) / 20,
                    speedAccuracyMps: 0.5, elapsedRealtimeMs: Int64(fixes.count * 1000), wallTimeMs: 0, isMock: true))
            }
        }
        for _ in 0..<3 { fixes.append(LocationFix(point: points.last!, accuracyMeters: 5, speedMps: 0, speedAccuracyMps: 0.5,
            elapsedRealtimeMs: Int64(fixes.count * 1000), wallTimeMs: 0, isMock: true)) }
        return fixes
    }
}
