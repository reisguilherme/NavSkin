package br.com.gamemap.navigation.core

import br.com.gamemap.navigation.model.*
import kotlin.math.*

data class EngineUpdate(
    val location: LocationState,
    val progress: RouteProgress?,
    val shouldRecalculate: Boolean = false,
    val arrived: Boolean = false,
)

/**
 * One synchronous navigation session. The owner serializes calls and performs network requests.
 * GPS time is monotonic elapsed realtime; wall time is used only for the displayed arrival clock.
 */
class NavigationEngine {
    private var route: NavRoute? = null
    private var geometry: RouteGeometry? = null
    private var lastFix: LocationFix? = null
    private var lastGoodFix: LocationFix? = null
    private var speedKmh: Double? = null
    private var bearing: Double? = null
    private var progress: RouteProgress? = null
    private var lastMatchElapsedMs: Long? = null
    private var deviationCount = 0
    private var deviationStartedMs = 0L
    private var lastDeviationMs = 0L
    private var confirmedOffRoute = false
    private var recoveryCount = 0
    private var lastRecalculationMs: Long? = null
    private var arrivalCount = 0
    private var hasArrived = false

    fun start(route: NavRoute) {
        require(route.geometry.size >= 2 && route.geometry.all(GeoMath::isValid)) { "Invalid route geometry" }
        require(route.distanceMeters.isFinite() && route.distanceMeters >= 0 &&
            route.durationSeconds.isFinite() && route.durationSeconds >= 0) { "Invalid route estimates" }
        require(GeoMath.isValid(route.destination.point)) { "Invalid destination" }
        require(route.steps.all { it.startIndex in route.geometry.indices &&
            it.endIndex in it.startIndex..route.geometry.lastIndex &&
            it.distanceMeters.isFinite() && it.distanceMeters >= 0 &&
            it.durationSeconds.isFinite() && it.durationSeconds >= 0 }) { "Invalid route steps" }
        require(route.steps.zipWithNext().all { (first, second) -> first.startIndex <= second.startIndex }) {
            "Route steps must follow geometry order"
        }
        this.route = route.copy(geometry = route.geometry.toList(), steps = route.steps.toList())
        geometry = RouteGeometry(this.route!!.geometry)
        resetRouteProgress()
    }

    fun stop() {
        route = null
        geometry = null
        resetRouteProgress()
    }

    private fun resetRouteProgress() {
        progress = null
        lastMatchElapsedMs = null
        deviationCount = 0
        confirmedOffRoute = false
        recoveryCount = 0
        lastRecalculationMs = null
        arrivalCount = 0
        hasArrived = false
    }

    fun onFix(fix: LocationFix, nowElapsedMs: Long, nowWallMs: Long): EngineUpdate {
        if (!GeoMath.isValid(fix.point) || !fix.accuracyMeters.isFinite() || fix.accuracyMeters < 0 ||
            fix.elapsedRealtimeMs < 0 || fix.elapsedRealtimeMs > nowElapsedMs + 1_000 ||
            lastFix?.let { fix.elapsedRealtimeMs <= it.elapsedRealtimeMs } == true) {
            return onTick(nowElapsedMs, nowWallMs)
        }
        lastFix = fix
        val quality = quality(fix, nowElapsedMs)
        if (quality != LocationQuality.GOOD) {
            speedKmh = null
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
            return onTick(nowElapsedMs, nowWallMs)
        }
        updateMotion(fix)
        val previousGood = lastGoodFix
        if (previousGood != null && fix.elapsedRealtimeMs - previousGood.elapsedRealtimeMs > 5_000) {
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
        }
        lastGoodFix = fix
        val activeRoute = route ?: return onTick(nowElapsedMs, nowWallMs)
        val routeGeometry = geometry ?: return onTick(nowElapsedMs, nowWallMs)
        val previous = progress
        val along = previous?.alongRouteMeters ?: 0.0
        val elapsedSeconds = lastMatchElapsedMs?.let { (fix.elapsedRealtimeMs - it) / 1_000.0 }
            ?.coerceIn(0.0, 15.0) ?: 0.0
        val speed = speedKmh?.div(3.6)
        val expectedMovement = (speed ?: previousGood?.let {
            GeoMath.distanceMeters(it.point, fix.point) / max(1.0, elapsedSeconds)
        } ?: 0.0) * elapsedSeconds
        val forwardWindow = max(45.0, min(500.0, expectedMovement * 1.8 + fix.accuracyMeters * 2 + 15))
        val match = routeGeometry.match(
            fix.point,
            if (previous == null) 0.0 else max(0.0, along - max(20.0, fix.accuracyMeters * 1.5)),
            if (previous == null) routeGeometry.lengthMeters else min(routeGeometry.lengthMeters, along + forwardWindow),
            min(routeGeometry.lengthMeters, along + expectedMovement), previous?.alongRouteMeters,
            bearing.takeIf { (speed ?: 0.0) >= 1.5 },
        )
        lastMatchElapsedMs = fix.elapsedRealtimeMs
        val withinRoute = match.distanceMeters <= max(18.0, fix.accuracyMeters * 1.5)
        val clearlyDeviating = match.distanceMeters > max(30.0, fix.accuracyMeters * 2.2)
        if (clearlyDeviating && fix.accuracyMeters <= 25.0) {
            if (deviationCount == 0 || fix.elapsedRealtimeMs - lastDeviationMs > 5_000) {
                deviationCount = 0
                deviationStartedMs = fix.elapsedRealtimeMs
            }
            deviationCount++
            lastDeviationMs = fix.elapsedRealtimeMs
            recoveryCount = 0
            if (deviationCount >= 3 && fix.elapsedRealtimeMs - deviationStartedMs >= 2_000) {
                confirmedOffRoute = true
            }
        } else {
            deviationCount = 0
            if (withinRoute) {
                recoveryCount++
                if (recoveryCount >= 2) confirmedOffRoute = false
            } else recoveryCount = 0
        }
        val onRoute = withinRoute && !confirmedOffRoute
        val acceptedAlong = if (onRoute) max(along, match.alongMeters) else along
        val acceptedMatch = if (onRoute || previous == null) match else RouteMatch(
            previous.segmentIndex, along, previous.projectedPoint, match.distanceMeters,
        )
        val remainingGeometry = max(0.0, routeGeometry.lengthMeters - acceptedAlong)
        val nearEnd = remainingGeometry <= max(20.0, fix.accuracyMeters * 1.5) &&
            acceptedAlong >= routeGeometry.lengthMeters * 0.9
        // Driving routes can end at road access to a park/campus rather than its requested centroid.
        val nearDriveableEndpoint = GeoMath.distanceMeters(fix.point, routeGeometry.points.last()) <=
            max(20.0, min(30.0, fix.accuracyMeters * 1.5))
        if (onRoute && nearEnd && nearDriveableEndpoint && fix.accuracyMeters <= 20.0) arrivalCount++
        else arrivalCount = 0
        if (arrivalCount >= 2) hasArrived = true
        progress = buildProgress(activeRoute, routeGeometry, acceptedAlong, acceptedMatch,
            onRoute, nowWallMs, match.distanceMeters)
        val recalculate = confirmedOffRoute && clearlyDeviating && fix.accuracyMeters <= 25.0 &&
            deviationCount >= 3 && fix.elapsedRealtimeMs - deviationStartedMs >= 2_000 &&
            (lastRecalculationMs?.let { fix.elapsedRealtimeMs - it >= 15_000 } ?: true)
        if (recalculate) lastRecalculationMs = fix.elapsedRealtimeMs
        return onTick(nowElapsedMs, nowWallMs).copy(shouldRecalculate = recalculate)
    }

    fun onTick(nowElapsedMs: Long, nowWallMs: Long): EngineUpdate {
        val fix = lastFix
        val quality = fix?.let { quality(it, nowElapsedMs) } ?: LocationQuality.UNAVAILABLE
        val current = quality == LocationQuality.GOOD
        if (!current) {
            arrivalCount = 0
            deviationCount = 0
            recoveryCount = 0
        }
        val location = LocationState(fix, quality, speedKmh.takeIf { current }, bearing,
            fix?.let { max(0L, nowElapsedMs - it.elapsedRealtimeMs) } ?: 0L)
        val visibleProgress = progress?.takeIf { current }?.let {
            it.copy(arrivalEpochMs = arrivalEpoch(nowWallMs, it.remainingSeconds))
        }
        return EngineUpdate(location, visibleProgress, arrived = current && hasArrived)
    }

    private fun quality(fix: LocationFix, nowElapsedMs: Long): LocationQuality {
        val age = max(0L, nowElapsedMs - fix.elapsedRealtimeMs)
        return when {
            age > 15_000 -> LocationQuality.LOST
            age > 5_000 || fix.accuracyMeters > 35.0 -> LocationQuality.WEAK
            else -> LocationQuality.GOOD
        }
    }

    private fun updateMotion(fix: LocationFix) {
        val reliableSpeed = fix.speedMps?.takeIf { speed -> speed.isFinite() && speed in 0.0..75.0 &&
            fix.speedAccuracyMps?.let { it.isFinite() && it in 0.0..3.0 } != false }
        val previous = lastGoodFix
        speedKmh = reliableSpeed?.let { speed ->
            if (speed < 0.6) 0.0 else {
                val old = speedKmh?.takeIf { previous != null && fix.elapsedRealtimeMs - previous.elapsedRealtimeMs < 5_000 }
                old?.let { it * 0.25 + speed * 3.6 * 0.75 } ?: (speed * 3.6)
            }
        }
        val moving = (reliableSpeed ?: 0.0) >= 1.5
        if (moving && fix.bearingDegrees?.isFinite() == true) {
            bearing = GeoMath.normalizeBearing(fix.bearingDegrees)
        } else if (previous != null && reliableSpeed == null &&
            fix.elapsedRealtimeMs - previous.elapsedRealtimeMs in 1..6_000 &&
            GeoMath.distanceMeters(previous.point, fix.point) >
                max(10.0, fix.accuracyMeters + previous.accuracyMeters)) {
            bearing = GeoMath.bearingDegrees(previous.point, fix.point)
        }
    }

    private fun buildProgress(route: NavRoute, geometry: RouteGeometry, along: Double,
                              match: RouteMatch, onRoute: Boolean, nowWallMs: Long,
                              distanceFromRoute: Double): RouteProgress {
        val currentStep = route.steps.indexOfLast { geometry.cumulativeMeters[it.startIndex] <= along + 0.01 }
            .coerceAtLeast(0)
        val upcomingStep = when {
            route.steps.isEmpty() -> 0
            currentStep >= route.steps.lastIndex -> route.steps.lastIndex
            else -> currentStep + 1
        }
        val upcomingAlong = route.steps.getOrNull(upcomingStep)?.let {
            // A provider's final instruction is sometimes missing; still measure to route end.
            if (upcomingStep == route.steps.lastIndex && it.kind != ManeuverKind.ARRIVE)
                geometry.lengthMeters else geometry.cumulativeMeters[it.startIndex]
        } ?: geometry.lengthMeters
        val geometryRemaining = max(0.0, geometry.lengthMeters - along)
        val distanceRemaining = if (geometry.lengthMeters > 0)
            route.distanceMeters * geometryRemaining / geometry.lengthMeters else 0.0
        val secondsRemaining = remainingDuration(route, geometry, along)
        return RouteProgress(route.id, along, match.point, match.segmentIndex, upcomingStep,
            if (hasArrived) 0.0 else max(0.0, upcomingAlong - along),
            if (hasArrived) 0.0 else distanceRemaining,
            if (hasArrived) 0.0 else secondsRemaining,
            arrivalEpoch(nowWallMs, if (hasArrived) 0.0 else secondsRemaining),
            distanceFromRoute, onRoute, hasArrived)
    }

    private fun remainingDuration(route: NavRoute, geometry: RouteGeometry, along: Double): Double {
        if (geometry.lengthMeters <= 0) return 0.0
        if (route.steps.isEmpty() || route.steps.sumOf { it.durationSeconds } <= 0.0) {
            return route.durationSeconds * (1 - along / geometry.lengthMeters).coerceIn(0.0, 1.0)
        }
        return route.steps.sumOf { step ->
            val start = geometry.cumulativeMeters[step.startIndex]
            val end = geometry.cumulativeMeters[step.endIndex]
            when {
                along >= end -> 0.0
                along <= start -> step.durationSeconds
                end > start -> step.durationSeconds * (end - along) / (end - start)
                else -> 0.0
            }
        }
    }

    private fun arrivalEpoch(nowWallMs: Long, remainingSeconds: Double): Long =
        nowWallMs + (remainingSeconds.coerceAtLeast(0.0) * 1_000).roundToLong()
}
