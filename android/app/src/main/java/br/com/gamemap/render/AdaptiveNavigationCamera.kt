package br.com.gamemap.render

import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.ManeuverKind
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

internal data class FollowCameraTarget(
    val point: GeoPoint,
    val bearing: Double,
    val zoom: Double,
    val durationMs: Int,
)

/** Per-display camera filtering. It consumes real fixes only; clock ticks never advance motion. */
internal class AdaptiveNavigationCamera {
    private data class Input(val point: GeoPoint, val fixTime: Long, val bearing: Double,
                             val zoom: Double, val orientation: MapOrientation)
    private var lastInput: Input? = null
    private var lastTarget: FollowCameraTarget? = null

    fun reset() {
        lastInput = null
        lastTarget = null
    }

    fun update(state: NavigationSnapshot, orientation: MapOrientation, recenter: Boolean = false,
               freeDrive: Boolean = false): FollowCameraTarget? {
        if (!state.hasCurrentFix()) return null
        val point = state.renderPosition() ?: return null
        val previous = lastTarget
        val bearing = state.followBearing(orientation, previous?.bearing ?: 0.0)
        val input = Input(point, state.location.fix!!.elapsedRealtimeMs, bearing, adaptiveFollowZoom(state, freeDrive), orientation)
        val before = lastInput
        if (!recenter && input == before) return null
        val elapsed = before?.let { (input.fixTime - it.fixTime).coerceIn(0L, 5_000L) } ?: 1_000L
        // Only a new GPS observation advances the filters. Explicit orientation/recenter
        // changes still respond immediately, even when they use the same observation.
        val immediate = previous == null || recenter || before?.orientation != orientation
        val headingWeight = if (immediate) 1.0 else 1.0 - exp(-elapsed / 450.0)
        val zoomWeight = if (immediate) 1.0 else 1.0 - exp(-elapsed / 700.0)
        val next = FollowCameraTarget(
            point,
            if (orientation == MapOrientation.NORTH_UP) 0.0 else normalizeBearing(
                (previous?.bearing ?: bearing) + shortestBearingDelta(previous?.bearing ?: bearing, bearing) * headingWeight),
            (previous?.zoom ?: input.zoom) + (input.zoom - (previous?.zoom ?: input.zoom)) * zoomWeight,
            if (immediate) 650 else (elapsed * 0.85).toInt().coerceIn(200, 1_000),
        )
        lastInput = input
        lastTarget = next
        if (!recenter && previous != null && next.point == previous.point &&
            abs(shortestBearingDelta(previous.bearing, next.bearing)) < 0.05 && abs(previous.zoom - next.zoom) < 0.005) return null
        return next
    }
}

/** More street detail at urban speed; a twelve-second approach window opens before a turn. */
internal fun adaptiveFollowZoom(state: NavigationSnapshot, freeDrive: Boolean = false): Double {
    if (!state.navigationActive && !freeDrive) return 16.0
    val speed = state.location.speedKmh?.takeIf { it.isFinite() && it >= 0.0 } ?: 50.0
    val cruising = when {
        speed <= 20.0 -> 17.0
        speed <= 50.0 -> 17.0 - (speed - 20.0) / 30.0 * 0.5
        speed <= 90.0 -> 16.5 - (speed - 50.0) / 40.0
        else -> 15.5
    }
    val progress = state.progress?.takeIf { state.guidanceAvailable && it.routeId == state.route?.id &&
        it.distanceToManeuverMeters.isFinite() && it.distanceToManeuverMeters >= 0.0 } ?: return cruising
    val maneuver = state.route?.steps?.getOrNull(progress.stepIndex)?.kind ?: return cruising
    val closeZoom = when (maneuver) {
        ManeuverKind.UTURN -> 18.0
        ManeuverKind.SLIGHT_LEFT, ManeuverKind.LEFT, ManeuverKind.SHARP_LEFT,
        ManeuverKind.SLIGHT_RIGHT, ManeuverKind.RIGHT, ManeuverKind.SHARP_RIGHT,
        ManeuverKind.ROUNDABOUT, ManeuverKind.MERGE, ManeuverKind.FORK, ManeuverKind.EXIT -> 17.5
        else -> return cruising
    }
    val approachMeters = max(180.0, speed / 3.6 * 12.0).coerceAtMost(450.0)
    val approach = ((approachMeters - progress.distanceToManeuverMeters) / (approachMeters - 60.0)).coerceIn(0.0, 1.0)
    val eased = approach * approach * (3.0 - 2.0 * approach)
    return cruising + (closeZoom - cruising) * eased
}

internal fun normalizeBearing(degrees: Double): Double = ((degrees % 360.0) + 360.0) % 360.0
internal fun shortestBearingDelta(from: Double, to: Double): Double = normalizeBearing(to - from + 180.0) - 180.0
