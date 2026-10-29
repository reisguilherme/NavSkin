import Foundation

public struct NavigationCameraPosition: Equatable, Sendable {
    public var point: GeoPoint
    public var zoom: Double
    public var bearing: Double
}

/// Shared navigation defaults: city detail, speed-aware look-ahead and gradual turn approach.
/// The native map owns gesture/overview modes and animates each accepted target.
public struct NavigationCameraPolicy: Sendable {
    private var position: NavigationCameraPosition?
    private var previousInput: Input?

    private struct Input: Equatable, Sendable {
        var timestamp: Int64
        var point: GeoPoint
        var speed: Double?
        var bearing: Double?
        var quality: LocationQuality
        var orientation: MapOrientation
        var navigation: Bool
        var maneuver: ManeuverKind?
        var distance: Double?
    }

    public init() {}

    public mutating func reset() {
        position = nil
        previousInput = nil
    }

    public mutating func update(_ snapshot: NavigationSnapshot, orientation: MapOrientation,
                                fallbackBearing: Double = 0) -> NavigationCameraPosition? {
        guard snapshot.hasCurrentMapFix, let fix = snapshot.location.fix,
              let point = snapshot.mapRenderPosition else { return nil }
        let speed = snapshot.location.speedKmh.flatMap { $0.isFinite && $0 >= 0 ? min($0, 270) : nil }
        let bearing = snapshot.location.bearingDegrees.flatMap { $0.isFinite ? GeoMath.normalizeBearing($0) : nil }
        let progress = snapshot.guidanceAvailable && snapshot.location.quality == .good &&
            snapshot.progress?.routeId == snapshot.route?.id && snapshot.progress?.onRoute == true ? snapshot.progress : nil
        let maneuver = progress.flatMap { progress in
            snapshot.route.flatMap { $0.steps.indices.contains(progress.stepIndex) ? $0.steps[progress.stepIndex].kind : nil }
        }
        let distance = progress?.distanceToManeuverMeters.flatMapFiniteNonnegative
        let input = Input(timestamp: fix.elapsedRealtimeMs, point: point, speed: speed, bearing: bearing,
            quality: snapshot.location.quality, orientation: orientation, navigation: snapshot.navigationActive,
            maneuver: maneuver, distance: distance)
        // Store ticks and UI updates must not restart the native camera animation.
        if input == previousInput { return position }
        let previous = previousInput
        previousInput = input
        let moving = (speed ?? 0) >= 5
        let usableHeading = snapshot.location.quality == .good && moving ? bearing : nil
        let initialBearing = fallbackBearing.isFinite ? GeoMath.normalizeBearing(fallbackBearing) : 0
        let desiredBearing = orientation == .northUp ? 0 : usableHeading ?? position?.bearing ?? initialBearing
        let desiredZoom = snapshot.navigationActive ? Self.targetZoom(speedKmh: speed, maneuver: maneuver, distanceMeters: distance) : 16
        guard var next = position else {
            let initial = NavigationCameraPosition(point: point, zoom: desiredZoom, bearing: desiredBearing)
            position = initial
            return initial
        }
        // Imprecise GPS keeps the last reliable framing, including its zoom.
        if snapshot.location.quality != .good {
            if orientation == .northUp { next.bearing = 0 }
            position = next
            return next
        }
        let elapsed = max(0, min(1_500, Double(fix.elapsedRealtimeMs) - Double(previous?.timestamp ?? fix.elapsedRealtimeMs)))
        let headingFactor = 1 - exp(-elapsed / 450)
        let zoomFactor = 1 - exp(-elapsed / 700)
        let delta = (desiredBearing - next.bearing + 540).truncatingRemainder(dividingBy: 360) - 180
        if orientation == .northUp || previous?.orientation != orientation {
            next.bearing = desiredBearing
        } else if abs(delta) >= 0.5 {
            next.bearing = GeoMath.normalizeBearing(next.bearing + delta * headingFactor)
        }
        if abs(desiredZoom - next.zoom) >= 0.025 {
            next.zoom += (desiredZoom - next.zoom) * zoomFactor
        }
        let jitterRadius = max(3, min(12, fix.accuracyMeters.isFinite ? fix.accuracyMeters : 3))
        if moving || GeoMath.distanceMeters(next.point, point) >= jitterRadius { next.point = point }
        position = next
        return next
    }

    public static func targetZoom(speedKmh: Double?, maneuver: ManeuverKind?, distanceMeters: Double?) -> Double {
        let speed = speedKmh.flatMap { $0.isFinite && $0 >= 0 ? min($0, 270) : nil }
        let base: Double
        switch speed {
        case nil: base = 16.5
        case let value? where value <= 20: base = 17
        case let value? where value <= 50: base = 17 - (value - 20) / 60
        case let value? where value <= 90: base = 16.5 - (value - 50) / 40
        default: base = 15.5
        }
        guard let maneuver, ![.straight, .depart, .unknown].contains(maneuver),
              let distance = distanceMeters, distance.isFinite, distance >= 0 else { return base }
        let approach = min(450, max(180, (speed ?? 50) / 3.6 * 12))
        let weight = min(1, max(0, (approach - distance) / (approach - 60)))
        let nearZoom = maneuver == .uturn ? 18.0 : 17.5
        return base + (nearZoom - base) * weight * weight * (3 - 2 * weight)
    }
}

private extension Double {
    var flatMapFiniteNonnegative: Double? { isFinite && self >= 0 ? self : nil }
}

public extension GeoPoint {
    var isValidMapPoint: Bool { GeoMath.isValid(self) && (-85.051129...85.051129).contains(latitude) }
}

public extension NavigationSnapshot {
    var hasCurrentMapFix: Bool {
        location.fix?.point.isValidMapPoint == true &&
            (location.quality == .good || location.quality == .weak) && (0...5_000).contains(location.ageMs)
    }
    var mapRenderPosition: GeoPoint? {
        guard let actual = location.fix?.point, actual.isValidMapPoint else { return nil }
        if navigationActive, hasCurrentMapFix, location.quality == .good, let progress,
           progress.routeId == route?.id, progress.onRoute, progress.projectedPoint.isValidMapPoint {
            return progress.projectedPoint
        }
        return actual
    }
    var mapVehicleOpacity: Double {
        if location.fix == nil || location.quality == .unavailable { return 0 }
        if location.quality == .lost || location.ageMs > 15_000 { return 0.35 }
        if location.quality == .weak || location.ageMs > 5_000 { return 0.65 }
        return 1
    }
    func mapFollowBearing(_ orientation: MapOrientation) -> Double {
        guard orientation == .movementUp, hasCurrentMapFix,
              let bearing = location.bearingDegrees, bearing.isFinite else { return 0 }
        return GeoMath.normalizeBearing(bearing)
    }
}
