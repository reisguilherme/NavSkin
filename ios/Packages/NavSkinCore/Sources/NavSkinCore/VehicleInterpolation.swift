import Foundation

/// Rendering only: interpolate observed positions without changing navigation or forecasting GPS.
public enum VehicleInterpolation {
    public static func position(from: GeoPoint, to: GeoPoint, fraction: Double) -> GeoPoint {
        let amount = fraction.isFinite ? min(1, max(0, fraction)) : 1
        if amount == 0 { return from }
        if amount == 1 { return to }
        let longitudeDelta = (to.longitude - from.longitude + 540).truncatingRemainder(dividingBy: 360) - 180
        let longitude = (from.longitude + longitudeDelta * amount + 540).truncatingRemainder(dividingBy: 360) - 180
        return GeoPoint(latitude: from.latitude + (to.latitude - from.latitude) * amount, longitude: longitude)
    }

    public static func bearing(from: Double, to: Double, fraction: Double) -> Double {
        let amount = fraction.isFinite ? min(1, max(0, fraction)) : 1
        let start = from.isFinite ? GeoMath.normalizeBearing(from) : 0
        let target = to.isFinite ? GeoMath.normalizeBearing(to) : start
        let delta = (target - start + 540).truncatingRemainder(dividingBy: 360) - 180
        return GeoMath.normalizeBearing(start + delta * amount)
    }

    public static func duration(fixIntervalMs: Double, distanceMeters: Double, reliable: Bool) -> Double {
        guard reliable, fixIntervalMs.isFinite, (1...5_000).contains(fixIntervalMs),
              distanceMeters.isFinite, (0...300).contains(distanceMeters) else { return 0 }
        return min(1, max(0.2, fixIntervalMs * 0.00085))
    }
}
