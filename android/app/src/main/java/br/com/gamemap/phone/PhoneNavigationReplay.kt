package br.com.gamemap.phone

import br.com.gamemap.navigation.core.GeoMath
import br.com.gamemap.navigation.model.*

/** Original fixture, visibly labelled by the runtime. Never returned by the routing provider. */
internal object PhoneNavigationReplay {
    private val points = listOf(GeoPoint(-16.6817, -49.2596), GeoPoint(-16.6817, -49.2578),
        GeoPoint(-16.6808, -49.2578), GeoPoint(-16.6808, -49.2562))
    val route: NavRoute by lazy {
        val lengths = points.zipWithNext().map { GeoMath.distanceMeters(it.first, it.second) }
        NavRoute("development-fixture", points, listOf(
            RouteStep(0, "Siga em frente · SIMULAÇÃO", "Trajeto de desenvolvimento", ManeuverKind.DEPART, 0, 1, lengths[0], 20.0),
            RouteStep(1, "Vire à esquerda", "Trecho de teste", ManeuverKind.LEFT, 1, 2, lengths[1], 15.0),
            RouteStep(2, "Vire à direita", "Trecho final", ManeuverKind.RIGHT, 2, 3, lengths[2], 20.0),
            RouteStep(3, "Chegada · SIMULAÇÃO", "Destino de teste", ManeuverKind.ARRIVE, 3, 3, 0.0, 0.0),
        ), lengths.sum(), 55.0, Destination("simulation-destination", "Destino SIMULAÇÃO", points.last()), 0)
    }
    fun fixes(): List<LocationFix> = buildList {
        points.zipWithNext().forEach { (from, to) ->
            repeat(20) { index ->
                val fraction = index / 20.0
                add(LocationFix(GeoPoint(from.latitude + (to.latitude - from.latitude) * fraction,
                    from.longitude + (to.longitude - from.longitude) * fraction), 5.0,
                    GeoMath.bearingDegrees(from, to), GeoMath.distanceMeters(from, to) / 20.0,
                    .5, size * 1000L, size * 1000L, true))
            }
        }
        repeat(3) { add(LocationFix(points.last(), 5.0, 90.0, 0.0, .5, size * 1000L, size * 1000L, true)) }
    }
}
