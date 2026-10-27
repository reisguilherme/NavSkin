package br.com.gamemap.navigation.core

import br.com.gamemap.navigation.model.GeoPoint
import kotlin.math.*

/** WGS84 coordinates in degrees; distances use the IUGG mean earth radius in metres. */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun isValid(point: GeoPoint): Boolean = point.latitude.isFinite() &&
        point.longitude.isFinite() && point.latitude in -90.0..90.0 &&
        point.longitude in -180.0..180.0

    fun distanceMeters(first: GeoPoint, second: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(longitudeDelta(first.longitude, second.longitude))
        val value = sin(latitudeDelta / 2).pow(2) +
            cos(Math.toRadians(first.latitude)) * cos(Math.toRadians(second.latitude)) *
            sin(longitudeDelta / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(value.coerceIn(0.0, 1.0)))
    }

    fun bearingDegrees(first: GeoPoint, second: GeoPoint): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(longitudeDelta(first.longitude, second.longitude))
        val y = sin(longitudeDelta) * cos(secondLatitude)
        val x = cos(firstLatitude) * sin(secondLatitude) -
            sin(firstLatitude) * cos(secondLatitude) * cos(longitudeDelta)
        return normalizeBearing(Math.toDegrees(atan2(y, x)))
    }

    fun normalizeBearing(value: Double): Double = ((value % 360) + 360) % 360

    fun bearingDifference(first: Double, second: Double): Double =
        abs(((first - second + 540) % 360) - 180)

    /** A short-road tangent-plane projection, with wrapped longitude at the antimeridian. */
    internal fun project(point: GeoPoint, first: GeoPoint, second: GeoPoint,
                         minFraction: Double = 0.0, maxFraction: Double = 1.0): Projection {
        val latitudeScale = Math.PI / 180 * EARTH_RADIUS_METERS
        val longitudeScale = latitudeScale * cos(Math.toRadians((first.latitude + second.latitude) / 2))
        val segmentX = longitudeDelta(first.longitude, second.longitude) * longitudeScale
        val segmentY = (second.latitude - first.latitude) * latitudeScale
        val pointX = longitudeDelta(first.longitude, point.longitude) * longitudeScale
        val pointY = (point.latitude - first.latitude) * latitudeScale
        val denominator = segmentX * segmentX + segmentY * segmentY
        val fraction = if (denominator < 1e-12) 0.0 else
            ((pointX * segmentX + pointY * segmentY) / denominator).coerceIn(minFraction, maxFraction)
        val projected = GeoPoint(
            first.latitude + (second.latitude - first.latitude) * fraction,
            wrapLongitude(first.longitude + longitudeDelta(first.longitude, second.longitude) * fraction),
        )
        return Projection(projected, fraction, distanceMeters(point, projected))
    }

    private fun longitudeDelta(first: Double, second: Double): Double =
        ((second - first + 540) % 360) - 180

    private fun wrapLongitude(value: Double): Double = ((value + 540) % 360) - 180
}

internal data class Projection(val point: GeoPoint, val fraction: Double, val distanceMeters: Double)

internal class RouteGeometry(val points: List<GeoPoint>) {
    val cumulativeMeters = DoubleArray(points.size)
    init {
        for (index in 1 until points.size) {
            cumulativeMeters[index] = cumulativeMeters[index - 1] +
                GeoMath.distanceMeters(points[index - 1], points[index])
        }
    }
    val lengthMeters: Double get() = cumulativeMeters.last()

    fun match(point: GeoPoint, minAlong: Double, maxAlong: Double, predictedAlong: Double,
              previousAlong: Double?, heading: Double?): RouteMatch {
        var best: RouteMatch? = null
        var bestScore = Double.POSITIVE_INFINITY
        for (index in 0 until points.lastIndex) {
            val start = cumulativeMeters[index]
            val end = cumulativeMeters[index + 1]
            val length = end - start
            if (end < minAlong || start > maxAlong || length < 0.001) continue
            val projection = GeoMath.project(point, points[index], points[index + 1],
                ((minAlong - start) / length).coerceIn(0.0, 1.0),
                ((maxAlong - start) / length).coerceIn(0.0, 1.0))
            val along = start + projection.fraction * length
            val headingPenalty = heading?.let {
                GeoMath.bearingDifference(it, GeoMath.bearingDegrees(points[index], points[index + 1])) / 180 * 35
            } ?: 0.0
            val continuityPenalty = if (previousAlong == null) along * 0.02 else
                abs(along - predictedAlong) * 0.15 + max(0.0, previousAlong - along) * 0.5
            val score = projection.distanceMeters + headingPenalty + continuityPenalty
            if (score < bestScore) {
                best = RouteMatch(index, along, projection.point, projection.distanceMeters)
                bestScore = score
            }
        }
        // All-zero geometry is a legitimate already-at-destination route.
        return best ?: RouteMatch(0, 0.0, points.first(), GeoMath.distanceMeters(point, points.first()))
    }
}

internal data class RouteMatch(
    val segmentIndex: Int,
    val alongMeters: Double,
    val point: GeoPoint,
    val distanceMeters: Double,
)
