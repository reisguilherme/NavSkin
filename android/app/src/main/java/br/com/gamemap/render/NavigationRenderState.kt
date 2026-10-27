package br.com.gamemap.render

import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.LocationQuality
import kotlin.math.roundToInt

internal enum class NavigationCameraMode { FOLLOW, MANUAL, OVERVIEW }

/** Camera ownership belongs to one display. Recalculation never overrides a gesture. */
internal class NavigationCameraState(
    initialMode: NavigationCameraMode = NavigationCameraMode.FOLLOW,
    initialSessionId: Long? = null,
) {
    var mode = initialMode
        private set
    var sessionId = initialSessionId
        private set

    fun observe(snapshot: NavigationSnapshot) {
        if (snapshot.navigationActive && snapshot.sessionId != sessionId) {
            sessionId = snapshot.sessionId
            mode = NavigationCameraMode.FOLLOW
        }
    }

    fun follow() { mode = NavigationCameraMode.FOLLOW }
    fun manual() { mode = NavigationCameraMode.MANUAL }
    fun overview() { mode = NavigationCameraMode.OVERVIEW }
    fun restoreSession(id: Long) { sessionId = id }
}

internal fun GeoPoint.isValidMapPoint() = latitude.isFinite() && longitude.isFinite() &&
    latitude in -85.051129..85.051129 && longitude in -180.0..180.0

internal fun NavigationSnapshot.hasCurrentFix() = location.fix?.point?.isValidMapPoint() == true &&
    location.quality in setOf(LocationQuality.GOOD, LocationQuality.WEAK) && location.ageMs <= 5_000L

internal fun NavigationSnapshot.renderPosition(): GeoPoint? {
    val actual = location.fix?.point?.takeIf { it.isValidMapPoint() } ?: return null
    val matched = progress?.takeIf {
        navigationActive && hasCurrentFix() && location.quality == LocationQuality.GOOD &&
            it.routeId == route?.id && it.onRoute && it.projectedPoint.isValidMapPoint()
    }
    return matched?.projectedPoint ?: actual
}

internal fun NavigationSnapshot.vehicleOpacity(): Float = when {
    location.fix == null || location.quality == LocationQuality.UNAVAILABLE -> 0f
    location.quality == LocationQuality.LOST || location.ageMs > 15_000L -> 0.35f
    location.quality == LocationQuality.WEAK || location.ageMs > 5_000L -> 0.65f
    else -> 1f
}

internal fun NavigationSnapshot.followBearing(orientation: MapOrientation): Double =
    if (orientation == MapOrientation.MOVEMENT_UP && hasCurrentFix()) {
        location.bearingDegrees?.takeIf { it.isFinite() }?.let { ((it % 360.0) + 360.0) % 360.0 } ?: 0.0
    } else 0.0

internal fun NavigationSnapshot.surfaceSpeedText(): String {
    val speed = location.speedKmh?.takeIf { hasCurrentFix() && it.isFinite() && it >= 0.0 }
    val label = if (speed != null) "${speed.roundToInt()} km/h" else "— km/h • Sem GPS"
    return if (simulation) "SIMULAÇÃO • $label" else label
}
