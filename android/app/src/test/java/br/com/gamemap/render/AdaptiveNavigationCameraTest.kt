package br.com.gamemap.render

import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveNavigationCameraTest {
    private val origin = GeoPoint(-16.68, -49.25)
    private val end = GeoPoint(-16.69, -49.26)
    private fun state(speed: Double? = 40.0, distance: Double = 800.0,
                      kind: ManeuverKind = ManeuverKind.RIGHT, heading: Double? = 90.0,
                      time: Long = 1_000L): NavigationSnapshot {
        val route = NavRoute("route", listOf(origin, end),
            listOf(RouteStep(0, "Turn", "Street", kind, 1, 1, 100.0, 15.0)),
            1_500.0, 120.0, Destination("end", "End", end), 0L)
        return NavigationSnapshot(phase = NavigationPhase.NAVIGATING, route = route, sessionId = 7L,
            progress = RouteProgress("route", 100.0, origin, 0, 0, distance, 1_400.0,
                110.0, 0L, 0.0, true, false),
            location = LocationState(LocationFix(origin, 5.0, elapsedRealtimeMs = time, wallTimeMs = time),
                LocationQuality.GOOD, speed, heading, 0L))
    }

    @Test fun urbanSpeedShowsMoreStreetDetailThanFastTravel() {
        assertTrue(adaptiveFollowZoom(state(speed = 15.0)) > adaptiveFollowZoom(state(speed = 90.0)))
        assertEquals(16.5, adaptiveFollowZoom(state(speed = 50.0)), 0.001)
        assertEquals(15.5, adaptiveFollowZoom(state(speed = 120.0)), 0.001)
    }

    @Test fun turnsProgressivelyZoomInAndUturnGetsExtraDetail() {
        val far = adaptiveFollowZoom(state(speed = 50.0, distance = 800.0))
        val approach = adaptiveFollowZoom(state(speed = 50.0, distance = 100.0))
        val near = adaptiveFollowZoom(state(speed = 50.0, distance = 35.0))
        val uturn = adaptiveFollowZoom(state(speed = 50.0, distance = 35.0, kind = ManeuverKind.UTURN))
        assertTrue(far < approach && approach < near)
        assertEquals(17.5, near, 0.001)
        assertEquals(18.0, uturn, 0.001)
        assertEquals(far, adaptiveFollowZoom(state(speed = 50.0, distance = 35.0, kind = ManeuverKind.STRAIGHT)), 0.001)
    }

    @Test fun fasterApproachBeginsZoomingAtTwelveSecondsFromTheTurn() {
        val fastBase = adaptiveFollowZoom(state(speed = 90.0, distance = 800.0))
        assertTrue(adaptiveFollowZoom(state(speed = 90.0, distance = 240.0)) > fastBase)
        assertEquals(adaptiveFollowZoom(state(speed = 20.0, distance = 800.0)),
            adaptiveFollowZoom(state(speed = 20.0, distance = 240.0)), 0.001)
    }

    @Test fun mismatchedOffRouteAndRecalculatingProgressCannotTriggerTurnZoom() {
        val input = state(speed = 50.0, distance = 20.0)
        assertEquals(16.5, adaptiveFollowZoom(input.copy(progress = input.progress!!.copy(routeId = "previous"))), 0.001)
        assertEquals(16.5, adaptiveFollowZoom(input.copy(progress = input.progress!!.copy(onRoute = false))), 0.001)
        assertEquals(16.5, adaptiveFollowZoom(input.copy(phase = NavigationPhase.RECALCULATING)), 0.001)
        assertEquals(16.0, adaptiveFollowZoom(input.copy(phase = NavigationPhase.PREVIEW)), 0.001)
        assertTrue(adaptiveFollowZoom(state(speed = Double.NaN, distance = Double.NaN)).isFinite())
    }

    @Test fun timerOnlySnapshotsDoNotRestartAnAnimationButRecenterDoes() {
        val camera = AdaptiveNavigationCamera()
        val input = state()
        assertNotNull(camera.update(input, MapOrientation.MOVEMENT_UP))
        val tick = input.copy(location = input.location.copy(ageMs = 800L),
            progress = input.progress!!.copy(arrivalEpochMs = 12_345L))
        assertNull(camera.update(tick, MapOrientation.MOVEMENT_UP))
        assertNotNull(camera.update(tick, MapOrientation.MOVEMENT_UP, recenter = true))
    }

    @Test fun northCrossingTakesTheShortArcAndStopsRetainTheirLastHeading() {
        val camera = AdaptiveNavigationCamera()
        assertEquals(359.0, camera.update(state(heading = 359.0), MapOrientation.MOVEMENT_UP)!!.bearing, 0.001)
        val moved = camera.update(state(heading = 1.0, time = 1_250L), MapOrientation.MOVEMENT_UP)!!
        assertTrue(moved.bearing > 359.0 || moved.bearing < 1.0)
        val stopped = camera.update(state(speed = 0.0, heading = 137.0, time = 2_250L), MapOrientation.MOVEMENT_UP)!!
        assertEquals(moved.bearing, stopped.bearing, 0.001)
        assertEquals(2.0, shortestBearingDelta(359.0, 1.0), 0.001)
        assertEquals(-2.0, shortestBearingDelta(1.0, 359.0), 0.001)
    }

    @Test fun zoomAndHeadingEaseInsteadOfJumpingBetweenFixes() {
        val camera = AdaptiveNavigationCamera()
        val initial = camera.update(state(speed = 90.0, heading = 0.0), MapOrientation.MOVEMENT_UP)!!
        val next = camera.update(state(speed = 15.0, heading = 90.0, time = 1_250L), MapOrientation.MOVEMENT_UP)!!
        assertTrue(next.zoom > initial.zoom && next.zoom < 17.0)
        assertTrue(next.bearing > 0.0 && next.bearing < 90.0)
        assertTrue(next.durationMs in 200..1_000)
        assertEquals(0.0, camera.update(state(time = 1_250L), MapOrientation.NORTH_UP)!!.bearing, 0.001)
    }

    @Test fun staleFixCannotMoveCameraAndResetStartsAFreshSession() {
        val camera = AdaptiveNavigationCamera()
        val input = state()
        camera.update(input, MapOrientation.MOVEMENT_UP)
        assertNull(camera.update(input.copy(location = input.location.copy(ageMs = 5_001L)), MapOrientation.MOVEMENT_UP))
        assertNull(camera.update(input.copy(location = input.location.copy(quality = LocationQuality.LOST)), MapOrientation.MOVEMENT_UP))
        assertNull(camera.update(NavigationSnapshot(), MapOrientation.MOVEMENT_UP))
        camera.reset()
        assertEquals(270.0, camera.update(state(heading = 270.0), MapOrientation.MOVEMENT_UP)!!.bearing, 0.001)
    }
}
