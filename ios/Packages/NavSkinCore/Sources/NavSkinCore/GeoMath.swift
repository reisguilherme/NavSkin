import Foundation

/// WGS84 coordinates in degrees; distances use the IUGG mean earth radius in metres.
public enum GeoMath {
    private static let earthRadiusMeters = 6_371_008.8

    public static func isValid(_ point: GeoPoint) -> Bool {
        point.latitude.isFinite && point.longitude.isFinite &&
            (-90...90).contains(point.latitude) && (-180...180).contains(point.longitude)
    }

    public static func distanceMeters(_ first: GeoPoint, _ second: GeoPoint) -> Double {
        let latitudeDelta = radians(second.latitude - first.latitude)
        let longitudeDelta = radians(self.longitudeDelta(first.longitude, second.longitude))
        let latitudeSine = sin(latitudeDelta / 2)
        let longitudeSine = sin(longitudeDelta / 2)
        let value = latitudeSine * latitudeSine + cos(radians(first.latitude)) *
            cos(radians(second.latitude)) * longitudeSine * longitudeSine
        return 2 * earthRadiusMeters * asin(sqrt(min(1, max(0, value))))
    }

    public static func bearingDegrees(_ first: GeoPoint, _ second: GeoPoint) -> Double {
        let firstLatitude = radians(first.latitude)
        let secondLatitude = radians(second.latitude)
        let delta = radians(longitudeDelta(first.longitude, second.longitude))
        let y = sin(delta) * cos(secondLatitude)
        let x = cos(firstLatitude) * sin(secondLatitude) -
            sin(firstLatitude) * cos(secondLatitude) * cos(delta)
        return normalizeBearing(atan2(y, x) * 180 / .pi)
    }

    public static func normalizeBearing(_ value: Double) -> Double {
        (value.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
    }

    public static func bearingDifference(_ first: Double, _ second: Double) -> Double {
        abs((first - second + 540).truncatingRemainder(dividingBy: 360) - 180)
    }

    /// Short-road tangent-plane projection, with longitude wrapped at the antimeridian.
    static func project(_ point: GeoPoint, _ first: GeoPoint, _ second: GeoPoint,
                        minFraction: Double = 0, maxFraction: Double = 1) -> Projection {
        let latitudeScale = Double.pi / 180 * earthRadiusMeters
        let longitudeScale = latitudeScale * cos(radians((first.latitude + second.latitude) / 2))
        let segmentX = longitudeDelta(first.longitude, second.longitude) * longitudeScale
        let segmentY = (second.latitude - first.latitude) * latitudeScale
        let pointX = longitudeDelta(first.longitude, point.longitude) * longitudeScale
        let pointY = (point.latitude - first.latitude) * latitudeScale
        let denominator = segmentX * segmentX + segmentY * segmentY
        let fraction = denominator < 1e-12 ? 0 :
            min(maxFraction, max(minFraction, (pointX * segmentX + pointY * segmentY) / denominator))
        let projected = GeoPoint(
            latitude: first.latitude + (second.latitude - first.latitude) * fraction,
            longitude: wrapLongitude(first.longitude + longitudeDelta(first.longitude, second.longitude) * fraction)
        )
        return Projection(point: projected, fraction: fraction, distanceMeters: distanceMeters(point, projected))
    }

    private static func radians(_ value: Double) -> Double { value * .pi / 180 }

    private static func longitudeDelta(_ first: Double, _ second: Double) -> Double {
        (second - first + 540).truncatingRemainder(dividingBy: 360) - 180
    }

    private static func wrapLongitude(_ value: Double) -> Double {
        (value + 540).truncatingRemainder(dividingBy: 360) - 180
    }
}

struct Projection: Equatable {
    let point: GeoPoint
    let fraction: Double
    let distanceMeters: Double
}

struct RouteGeometry {
    let points: [GeoPoint]
    let cumulativeMeters: [Double]
    var lengthMeters: Double { cumulativeMeters.last ?? 0 }

    init(_ points: [GeoPoint]) {
        self.points = points
        var cumulative = [Double](repeating: 0, count: points.count)
        if points.count > 1 {
            for index in 1..<points.count {
                cumulative[index] = cumulative[index - 1] + GeoMath.distanceMeters(points[index - 1], points[index])
            }
        }
        self.cumulativeMeters = cumulative
    }

    func match(_ point: GeoPoint, minAlong: Double, maxAlong: Double, predictedAlong: Double,
               previousAlong: Double?, heading: Double?) -> RouteMatch {
        var best: RouteMatch?
        var bestScore = Double.infinity
        for index in 0..<(points.count - 1) {
            let start = cumulativeMeters[index]
            let end = cumulativeMeters[index + 1]
            let length = end - start
            if end < minAlong || start > maxAlong || length < 0.001 { continue }
            let projection = GeoMath.project(point, points[index], points[index + 1],
                minFraction: min(1, max(0, (minAlong - start) / length)),
                maxFraction: min(1, max(0, (maxAlong - start) / length)))
            let along = start + projection.fraction * length
            let headingPenalty = heading.map {
                GeoMath.bearingDifference($0, GeoMath.bearingDegrees(points[index], points[index + 1])) / 180 * 35
            } ?? 0
            let continuityPenalty: Double
            if let previousAlong {
                continuityPenalty = abs(along - predictedAlong) * 0.15 + max(0, previousAlong - along) * 0.5
            } else {
                continuityPenalty = along * 0.02
            }
            let score = projection.distanceMeters + headingPenalty + continuityPenalty
            if score < bestScore {
                best = RouteMatch(segmentIndex: index, alongMeters: along, point: projection.point,
                                  distanceMeters: projection.distanceMeters)
                bestScore = score
            }
        }
        // Engine validation guarantees at least two points. Zero-length routes are valid arrivals.
        return best ?? RouteMatch(segmentIndex: 0, alongMeters: 0, point: points[0],
                                  distanceMeters: GeoMath.distanceMeters(point, points[0]))
    }
}

struct RouteMatch {
    let segmentIndex: Int
    let alongMeters: Double
    let point: GeoPoint
    let distanceMeters: Double
}
