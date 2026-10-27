import Foundation

public enum NavigationEngineError: String, Error, Equatable, Sendable, LocalizedError {
    case invalidGeometry, invalidEstimates, invalidDestination, invalidSteps, unorderedSteps

    public var errorDescription: String? {
        switch self {
        case .invalidGeometry: return "Invalid route geometry"
        case .invalidEstimates: return "Invalid route estimates"
        case .invalidDestination: return "Invalid destination"
        case .invalidSteps: return "Invalid route steps"
        case .unorderedSteps: return "Route steps must follow geometry order"
        }
    }
}

/// One synchronous navigation session. Its owner serializes calls and performs network requests.
/// GPS uses monotonic elapsed milliseconds; wall time only determines the displayed arrival clock.
public final class NavigationEngine {
    private var route: NavRoute?
    private var geometry: RouteGeometry?
    private var lastFix: LocationFix?
    private var lastGoodFix: LocationFix?
    private var speedKmh: Double?
    private var bearing: Double?
    private var progress: RouteProgress?
    private var lastMatchElapsedMs: Int64?
    private var deviationCount = 0
    private var deviationStartedMs: Int64 = 0
    private var lastDeviationMs: Int64 = 0
    private var confirmedOffRoute = false
    private var recoveryCount = 0
    private var lastRecalculationMs: Int64?
    private var arrivalCount = 0
    private var hasArrived = false

    public init() {}

    public func start(_ route: NavRoute) throws {
        guard route.geometry.count >= 2, route.geometry.allSatisfy(GeoMath.isValid) else {
            throw NavigationEngineError.invalidGeometry
        }
        guard route.distanceMeters.isFinite, route.distanceMeters >= 0,
              route.durationSeconds.isFinite, route.durationSeconds >= 0 else {
            throw NavigationEngineError.invalidEstimates
        }
        guard GeoMath.isValid(route.destination.point) else {
            throw NavigationEngineError.invalidDestination
        }
        guard route.steps.allSatisfy({ step in
            route.geometry.indices.contains(step.startIndex) &&
                step.endIndex >= step.startIndex && step.endIndex < route.geometry.count &&
                step.distanceMeters.isFinite && step.distanceMeters >= 0 &&
                step.durationSeconds.isFinite && step.durationSeconds >= 0
        }) else {
            throw NavigationEngineError.invalidSteps
        }
        guard zip(route.steps, route.steps.dropFirst()).allSatisfy({ $0.0.startIndex <= $0.1.startIndex }) else {
            throw NavigationEngineError.unorderedSteps
        }
        // Swift value semantics preserve this route if the caller later mutates its arrays.
        self.route = route
        geometry = RouteGeometry(route.geometry)
        resetRouteProgress()
    }

    public func stop() {
        route = nil
        geometry = nil
        resetRouteProgress()
    }

    private func resetRouteProgress() {
        progress = nil
        lastMatchElapsedMs = nil
        deviationCount = 0
        confirmedOffRoute = false
        recoveryCount = 0
        lastRecalculationMs = nil
        arrivalCount = 0
        hasArrived = false
    }

    public func onFix(_ fix: LocationFix, nowElapsedMs: Int64, nowWallMs: Int64) -> EngineUpdate {
        let maximumFuture = nowElapsedMs > Int64.max - 1_000 ? Int64.max : nowElapsedMs + 1_000
        guard GeoMath.isValid(fix.point), fix.accuracyMeters.isFinite, fix.accuracyMeters >= 0,
              fix.elapsedRealtimeMs >= 0,
              fix.elapsedRealtimeMs <= maximumFuture,
              lastFix.map({ fix.elapsedRealtimeMs > $0.elapsedRealtimeMs }) ?? true else {
            return onTick(nowElapsedMs: nowElapsedMs, nowWallMs: nowWallMs)
        }
        lastFix = fix
        guard quality(fix, nowElapsedMs: nowElapsedMs) == .good else {
            speedKmh = nil
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
            return onTick(nowElapsedMs: nowElapsedMs, nowWallMs: nowWallMs)
        }
        updateMotion(fix)
        let previousGood = lastGoodFix
        if let previousGood, fix.elapsedRealtimeMs - previousGood.elapsedRealtimeMs > 5_000 {
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
        }
        lastGoodFix = fix
        guard let activeRoute = route, let routeGeometry = geometry else {
            return onTick(nowElapsedMs: nowElapsedMs, nowWallMs: nowWallMs)
        }
        let previous = progress
        let along = previous?.alongRouteMeters ?? 0
        let elapsedSeconds = lastMatchElapsedMs.map {
            min(15, max(0, Double(fix.elapsedRealtimeMs - $0) / 1_000))
        } ?? 0
        let speed = speedKmh.map { $0 / 3.6 }
        let inferredSpeed = previousGood.map {
            GeoMath.distanceMeters($0.point, fix.point) / max(1, elapsedSeconds)
        } ?? 0
        let expectedMovement = (speed ?? inferredSpeed) * elapsedSeconds
        let forwardWindow = max(45, min(500, expectedMovement * 1.8 + fix.accuracyMeters * 2 + 15))
        let match = routeGeometry.match(fix.point,
            minAlong: previous == nil ? 0 : max(0, along - max(20, fix.accuracyMeters * 1.5)),
            maxAlong: previous == nil ? routeGeometry.lengthMeters : min(routeGeometry.lengthMeters, along + forwardWindow),
            predictedAlong: min(routeGeometry.lengthMeters, along + expectedMovement),
            previousAlong: previous?.alongRouteMeters,
            heading: (speed ?? 0) >= 1.5 ? bearing : nil)
        lastMatchElapsedMs = fix.elapsedRealtimeMs

        let withinRoute = match.distanceMeters <= max(18, fix.accuracyMeters * 1.5)
        let clearlyDeviating = match.distanceMeters > max(30, fix.accuracyMeters * 2.2)
        if clearlyDeviating && fix.accuracyMeters <= 25 {
            if deviationCount == 0 || fix.elapsedRealtimeMs - lastDeviationMs > 5_000 {
                deviationCount = 0
                deviationStartedMs = fix.elapsedRealtimeMs
            }
            deviationCount += 1
            lastDeviationMs = fix.elapsedRealtimeMs
            recoveryCount = 0
            if deviationCount >= 3 && fix.elapsedRealtimeMs - deviationStartedMs >= 2_000 {
                confirmedOffRoute = true
            }
        } else {
            deviationCount = 0
            if withinRoute {
                recoveryCount += 1
                if recoveryCount >= 2 { confirmedOffRoute = false }
            } else {
                recoveryCount = 0
            }
        }
        let onRoute = withinRoute && !confirmedOffRoute
        let acceptedAlong = onRoute ? max(along, match.alongMeters) : along
        let acceptedMatch: RouteMatch
        if onRoute || previous == nil {
            acceptedMatch = match
        } else {
            acceptedMatch = RouteMatch(segmentIndex: previous!.segmentIndex, alongMeters: along,
                                       point: previous!.projectedPoint, distanceMeters: match.distanceMeters)
        }
        let remainingGeometry = max(0, routeGeometry.lengthMeters - acceptedAlong)
        let nearEnd = remainingGeometry <= max(20, fix.accuracyMeters * 1.5) &&
            acceptedAlong >= routeGeometry.lengthMeters * 0.9
        // Driving routes may finish at road access rather than a requested park/campus centroid.
        let nearDriveableEndpoint = GeoMath.distanceMeters(fix.point, routeGeometry.points.last!) <=
            max(20, min(30, fix.accuracyMeters * 1.5))
        if onRoute && nearEnd && nearDriveableEndpoint && fix.accuracyMeters <= 20 {
            arrivalCount += 1
        } else {
            arrivalCount = 0
        }
        if arrivalCount >= 2 { hasArrived = true }
        progress = buildProgress(activeRoute, geometry: routeGeometry, along: acceptedAlong,
                                 match: acceptedMatch, onRoute: onRoute, nowWallMs: nowWallMs,
                                 distanceFromRoute: match.distanceMeters)
        let recalculate = confirmedOffRoute && clearlyDeviating && fix.accuracyMeters <= 25 &&
            deviationCount >= 3 && fix.elapsedRealtimeMs - deviationStartedMs >= 2_000 &&
            (lastRecalculationMs.map { fix.elapsedRealtimeMs - $0 >= 15_000 } ?? true)
        if recalculate { lastRecalculationMs = fix.elapsedRealtimeMs }
        var update = onTick(nowElapsedMs: nowElapsedMs, nowWallMs: nowWallMs)
        update.shouldRecalculate = recalculate
        return update
    }

    public func onTick(nowElapsedMs: Int64, nowWallMs: Int64) -> EngineUpdate {
        let quality = lastFix.map { self.quality($0, nowElapsedMs: nowElapsedMs) } ?? .unavailable
        let current = quality == .good
        if !current {
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
        }
        let location = LocationState(fix: lastFix, quality: quality,
                                     speedKmh: current ? speedKmh : nil, bearingDegrees: bearing,
                                     ageMs: lastFix.map { ageMs($0, nowElapsedMs: nowElapsedMs) } ?? 0)
        var visibleProgress = current ? progress : nil
        if let remainingSeconds = visibleProgress?.remainingSeconds {
            visibleProgress?.arrivalEpochMs = arrivalEpoch(nowWallMs: nowWallMs, remainingSeconds: remainingSeconds)
        }
        return EngineUpdate(location: location, progress: visibleProgress, arrived: current && hasArrived)
    }

    private func quality(_ fix: LocationFix, nowElapsedMs: Int64) -> LocationQuality {
        let age = ageMs(fix, nowElapsedMs: nowElapsedMs)
        if age > 15_000 { return .lost }
        if age > 5_000 || fix.accuracyMeters > 35 { return .weak }
        return .good
    }

    private func ageMs(_ fix: LocationFix, nowElapsedMs: Int64) -> Int64 {
        guard nowElapsedMs > fix.elapsedRealtimeMs else { return 0 }
        // A valid fix timestamp is nonnegative, so subtraction cannot overflow here.
        return nowElapsedMs - fix.elapsedRealtimeMs
    }

    private func updateMotion(_ fix: LocationFix) {
        let reliableSpeed = fix.speedMps.flatMap { speed -> Double? in
            guard speed.isFinite, (0...75).contains(speed),
                  fix.speedAccuracyMps.map({ $0.isFinite && (0...3).contains($0) }) ?? true else { return nil }
            return speed
        }
        let previous = lastGoodFix
        if let speed = reliableSpeed {
            if speed < 0.6 {
                speedKmh = 0
            } else if let old = speedKmh, let previous,
                      fix.elapsedRealtimeMs - previous.elapsedRealtimeMs < 5_000 {
                speedKmh = old * 0.25 + speed * 3.6 * 0.75
            } else {
                speedKmh = speed * 3.6
            }
        } else {
            speedKmh = nil
        }
        let moving = (reliableSpeed ?? 0) >= 1.5
        if moving, let fixBearing = fix.bearingDegrees, fixBearing.isFinite {
            bearing = GeoMath.normalizeBearing(fixBearing)
        } else if let previous, reliableSpeed == nil,
                  (1...6_000).contains(fix.elapsedRealtimeMs - previous.elapsedRealtimeMs),
                  GeoMath.distanceMeters(previous.point, fix.point) >
                    max(10, fix.accuracyMeters + previous.accuracyMeters) {
            bearing = GeoMath.bearingDegrees(previous.point, fix.point)
        }
    }

    private func buildProgress(_ route: NavRoute, geometry: RouteGeometry, along: Double,
                               match: RouteMatch, onRoute: Bool, nowWallMs: Int64,
                               distanceFromRoute: Double) -> RouteProgress {
        let currentStep = route.steps.lastIndex {
            geometry.cumulativeMeters[$0.startIndex] <= along + 0.01
        } ?? 0
        let upcomingStep: Int
        if route.steps.isEmpty { upcomingStep = 0 }
        else if currentStep >= route.steps.count - 1 { upcomingStep = route.steps.count - 1 }
        else { upcomingStep = currentStep + 1 }
        let upcomingAlong: Double
        if route.steps.indices.contains(upcomingStep) {
            let step = route.steps[upcomingStep]
            // If a provider omits its final arrival instruction, measure to the route end.
            upcomingAlong = upcomingStep == route.steps.count - 1 && step.kind != .arrive ?
                geometry.lengthMeters : geometry.cumulativeMeters[step.startIndex]
        } else {
            upcomingAlong = geometry.lengthMeters
        }
        let geometryRemaining = max(0, geometry.lengthMeters - along)
        let distanceRemaining = geometry.lengthMeters > 0 ?
            route.distanceMeters * geometryRemaining / geometry.lengthMeters : 0
        let secondsRemaining = remainingDuration(route, geometry: geometry, along: along)
        return RouteProgress(routeId: route.id, alongRouteMeters: along, projectedPoint: match.point,
            segmentIndex: match.segmentIndex, stepIndex: upcomingStep,
            distanceToManeuverMeters: hasArrived ? 0 : max(0, upcomingAlong - along),
            remainingDistanceMeters: hasArrived ? 0 : distanceRemaining,
            remainingSeconds: hasArrived ? 0 : secondsRemaining,
            arrivalEpochMs: arrivalEpoch(nowWallMs: nowWallMs, remainingSeconds: hasArrived ? 0 : secondsRemaining),
            distanceFromRouteMeters: distanceFromRoute, onRoute: onRoute, arrived: hasArrived)
    }

    private func remainingDuration(_ route: NavRoute, geometry: RouteGeometry, along: Double) -> Double {
        guard geometry.lengthMeters > 0 else { return 0 }
        if route.steps.isEmpty || route.steps.reduce(0, { $0 + $1.durationSeconds }) <= 0 {
            return route.durationSeconds * min(1, max(0, 1 - along / geometry.lengthMeters))
        }
        return route.steps.reduce(0) { total, step in
            let start = geometry.cumulativeMeters[step.startIndex]
            let end = geometry.cumulativeMeters[step.endIndex]
            if along >= end { return total }
            if along <= start { return total + step.durationSeconds }
            if end > start { return total + step.durationSeconds * (end - along) / (end - start) }
            return total
        }
    }

    private func arrivalEpoch(nowWallMs: Int64, remainingSeconds: Double) -> Int64 {
        let milliseconds = (max(0, remainingSeconds) * 1_000).rounded()
        // Swift traps converting overflowed estimates to Int64; saturate malformed extremes.
        guard milliseconds.isFinite, milliseconds < Double(Int64.max) else { return .max }
        let delta = Int64(milliseconds)
        let result = nowWallMs.addingReportingOverflow(delta)
        return result.overflow ? .max : result.partialValue
    }
}
