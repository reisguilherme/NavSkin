package br.com.gamemap.render

import br.com.gamemap.navigation.model.GeoPoint

/** Interpolates between already observed fixes; it never projects movement into the future. */
internal class ObservedVehicleTransition(
    private val from: GeoPoint,
    private val to: GeoPoint,
    private val fromBearing: Double,
    private val toBearing: Double,
) {
    data class Frame(val point: GeoPoint, val bearing: Double)

    fun sample(fraction: Double): Frame {
        val progress = fraction.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 1.0
        val point = when (progress) {
            0.0 -> from
            1.0 -> to
            else -> GeoPoint(from.latitude + (to.latitude - from.latitude) * progress,
                normalizeBearing(from.longitude + shortestBearingDelta(from.longitude, to.longitude) * progress + 180.0) - 180.0)
        }
        return Frame(point, normalizeBearing(fromBearing + shortestBearingDelta(fromBearing, toBearing) * progress))
    }
}
