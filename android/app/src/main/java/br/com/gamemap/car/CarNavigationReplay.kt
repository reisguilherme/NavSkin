package br.com.gamemap.car

import br.com.gamemap.navigation.core.GeoMath
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.LocationFix
import br.com.gamemap.navigation.model.NavRoute
import kotlin.math.ceil

/** Explicit host AUTO_DRIVE test mode, using the selected route rather than fake network data. */
internal object CarNavigationReplay {
    fun fixes(route: NavRoute): List<LocationFix> {
        val result = mutableListOf<LocationFix>()
        route.geometry.zipWithNext().forEach { (first, second) ->
            val distance = GeoMath.distanceMeters(first, second)
            val divisions = ceil(distance / 10.0).toInt().coerceAtLeast(1)
            if (result.size + divisions > 90_000) return emptyList()
            for (index in 0 until divisions) {
                val fraction = index.toDouble() / divisions
                result += LocationFix(GeoPoint(
                    first.latitude + (second.latitude - first.latitude) * fraction,
                    first.longitude + (second.longitude - first.longitude) * fraction,
                ), 5.0, GeoMath.bearingDegrees(first, second), distance / divisions,
                    0.5, result.size * 1_000L, result.size * 1_000L, isMock = true)
            }
        }
        val destination = route.geometry.lastOrNull() ?: return emptyList()
        repeat(3) { result += LocationFix(destination, 5.0, result.lastOrNull()?.bearingDegrees,
            0.0, 0.5, result.size * 1_000L, result.size * 1_000L, isMock = true) }
        return result
    }
}
