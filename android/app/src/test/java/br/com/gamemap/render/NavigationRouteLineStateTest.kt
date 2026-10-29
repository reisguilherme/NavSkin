package br.com.gamemap.render

import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.*
import org.junit.Assert.*
import org.junit.Test

class NavigationRouteLineStateTest {
    private val points = listOf(GeoPoint(-16.0, -49.0), GeoPoint(-16.001, -49.0), GeoPoint(-16.002, -49.0))
    private val route = NavRoute("r", points, emptyList(), 200.0, 20.0, Destination("d", "End", points.last()), 0L)
    private val input = NavigationSnapshot(phase = NavigationPhase.NAVIGATING, route = route,
        location = LocationState(LocationFix(points[0], 5.0, elapsedRealtimeMs = 1L, wallTimeMs = 1L), LocationQuality.GOOD),
        progress = RouteProgress("r", 20.0, GeoPoint(-16.0002, -49.0), 0, 0, 80.0, 180.0, 18.0, 1L, 0.0, true, false))

    @Test fun timerChangesReuseTheSameRouteAndRemainingGeometry() {
        val lines = NavigationRouteLineState()
        lines.update(input)
        val routeLine = lines.route
        val remaining = lines.remaining
        lines.update(input.copy(location = input.location.copy(ageMs = 1_000L),
            progress = input.progress!!.copy(arrivalEpochMs = 2_000L)))
        assertSame(routeLine, lines.route)
        assertSame(remaining, lines.remaining)
        assertEquals(listOf(points[1], points[2]), lines.remaining)
        assertEquals(listOf(GeoPoint(-16.0002, -49.0), points[1]), lines.head)
    }

    @Test fun progressChangeUpdatesRemainingLineAndStaleFixRestoresWholeRoute() {
        val lines = NavigationRouteLineState()
        lines.update(input)
        lines.update(input.copy(progress = input.progress!!.copy(segmentIndex = 1, projectedPoint = GeoPoint(-16.0015, -49.0))))
        assertEquals(listOf(points[2]), lines.remaining)
        assertEquals(listOf(GeoPoint(-16.0015, -49.0), points[2]), lines.head)
        lines.update(input.copy(location = input.location.copy(ageMs = 6_000L)))
        assertSame(lines.route, lines.remaining)
        assertTrue(lines.head.isEmpty())
        lines.update(input.copy(route = null, progress = null))
        assertTrue(lines.route.isEmpty())
        assertTrue(lines.remaining.isEmpty())
    }

    @Test fun invalidVerticesNeverShiftTheProgressSegmentToTheWrongStreet() {
        val lines = NavigationRouteLineState()
        lines.update(input.copy(route = route.copy(geometry = listOf(points[0], GeoPoint(Double.NaN, 0.0), points[1], points[2]))))
        assertEquals(points, lines.route)
        assertSame(lines.route, lines.remaining)
    }

    @Test fun movementOnOneSegmentDoesNotRebuildTheLongRouteTail() {
        val lines = NavigationRouteLineState()
        lines.update(input)
        val tail = lines.remaining
        lines.update(input.copy(progress = input.progress!!.copy(projectedPoint = GeoPoint(-16.0005, -49.0))))
        assertSame(tail, lines.remaining)
    }
}
