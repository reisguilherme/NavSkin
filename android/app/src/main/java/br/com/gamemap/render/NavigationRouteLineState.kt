package br.com.gamemap.render

import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.GeoPoint
import br.com.gamemap.navigation.model.LocationQuality

/** Retains immutable geometry across clock/status updates, including copied ETA progress. */
internal class NavigationRouteLineState {
    private data class ProgressKey(val segment: Int, val point: GeoPoint)
    private var observed: List<GeoPoint>? = null
    private var progressKey: ProgressKey? = null
    var route: List<GeoPoint> = emptyList()
        private set
    var remaining: List<GeoPoint> = emptyList()
        private set
    var head: List<GeoPoint> = emptyList()
        private set

    fun update(state: NavigationSnapshot) {
        val requested = state.route?.geometry ?: emptyList()
        var routeChanged = false
        if (requested !== observed) {
            val valid = requested.takeIf { it.all(GeoPoint::isValidMapPoint) }
                ?: requested.filter(GeoPoint::isValidMapPoint)
            if (valid != route) {
                route = valid
                routeChanged = true
            }
            observed = requested
        }
        // Filtering corrupt geometry changes vertex indices: never apply original progress to it.
        val progress = state.progress?.takeIf {
            requested.size == route.size && state.navigationActive && state.hasCurrentFix() &&
                state.location.quality == LocationQuality.GOOD && it.onRoute && it.routeId == state.route?.id &&
                it.segmentIndex in 0 until route.lastIndex && it.projectedPoint.isValidMapPoint()
        }?.let { ProgressKey(it.segmentIndex, it.projectedPoint) }
        if (routeChanged || progress != progressKey) {
            if (routeChanged || progress?.segment != progressKey?.segment) {
                remaining = if (progress == null) route else route.subList(progress.segment + 1, route.size)
            }
            // Only this two-vertex source changes while moving within one route segment.
            head = if (progress == null) emptyList() else listOf(progress.point, route[progress.segment + 1])
            progressKey = progress
        }
    }
}
