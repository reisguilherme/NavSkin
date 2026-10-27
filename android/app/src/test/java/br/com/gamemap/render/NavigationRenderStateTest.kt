package br.com.gamemap.render

import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.navigation.model.*
import org.junit.Assert.*
import org.junit.Test

class NavigationRenderStateTest {
    private val actual = GeoPoint(-16.68, -49.25)
    private val snapped = GeoPoint(-16.6801, -49.2501)
    private val destination = Destination("destination", "Destination", snapped)
    private val route = NavRoute("route", listOf(actual, snapped), emptyList(), 20.0, 10.0, destination, 1L)
    private val progress = RouteProgress("route", 10.0, snapped, 0, 0, 10.0, 10.0, 5.0, 1L, 1.0, true, false)
    private fun state(quality: LocationQuality = LocationQuality.GOOD, age: Long = 0L) = NavigationSnapshot(
        phase = NavigationPhase.NAVIGATING, route = route, progress = progress, sessionId = 42L,
        location = LocationState(LocationFix(actual, 5.0, elapsedRealtimeMs = 1L, wallTimeMs = 1L),
            quality, speedKmh = 42.0, bearingDegrees = 90.0, ageMs = age),
    )

    @Test fun snappingRequiresFreshGoodSignalAndMatchingOnRouteProgress() {
        assertEquals(snapped, state().renderPosition())
        assertEquals(actual, state(LocationQuality.WEAK).renderPosition())
        assertEquals(actual, state(LocationQuality.LOST).renderPosition())
        assertEquals(actual, state(age = 6_000L).renderPosition())
        assertEquals(actual, state().copy(progress = progress.copy(onRoute = false)).renderPosition())
        assertEquals(actual, state().copy(progress = progress.copy(routeId = "old-route")).renderPosition())
        assertNull(NavigationSnapshot().renderPosition())
    }

    @Test fun stalePositionIsDimmedAndSpeedCannotLookCurrent() {
        assertEquals(1f, state().vehicleOpacity())
        assertTrue(state(LocationQuality.WEAK).vehicleOpacity() < 1f)
        assertTrue(state(LocationQuality.LOST).vehicleOpacity() < state(LocationQuality.WEAK).vehicleOpacity())
        assertEquals("42 km/h", state().surfaceSpeedText())
        assertEquals("— km/h • Sem GPS", state(LocationQuality.LOST).surfaceSpeedText())
        assertEquals("— km/h • Sem GPS", state(age = 6_000L).surfaceSpeedText())
        assertTrue(state().copy(simulation = true).surfaceSpeedText().contains("SIMULAÇÃO"))
    }

    @Test fun northUpAndMovementUpShareTheSameVehicleHeading() {
        assertEquals(0.0, state().followBearing(MapOrientation.NORTH_UP), 0.0)
        assertEquals(90.0, state().followBearing(MapOrientation.MOVEMENT_UP), 0.0)
        assertEquals(0.0, state(LocationQuality.LOST).followBearing(MapOrientation.MOVEMENT_UP), 0.0)
    }

    @Test fun onlyNewActiveSessionResumesFollowingAfterManualPan() {
        val camera = NavigationCameraState()
        camera.observe(state())
        camera.manual()
        camera.observe(state().copy(phase = NavigationPhase.RECALCULATING))
        assertEquals(NavigationCameraMode.MANUAL, camera.mode)
        camera.observe(state().copy(sessionId = 43L))
        assertEquals(NavigationCameraMode.FOLLOW, camera.mode)
        camera.overview()
        camera.observe(state().copy(sessionId = 43L))
        assertEquals(NavigationCameraMode.OVERVIEW, camera.mode)
        camera.follow()
        assertEquals(NavigationCameraMode.FOLLOW, camera.mode)
    }

    @Test fun restoredManualModeSurvivesTheFirstSameSessionSnapshot() {
        val camera = NavigationCameraState(NavigationCameraMode.MANUAL, 42L)
        camera.observe(state())
        assertEquals(NavigationCameraMode.MANUAL, camera.mode)
        camera.observe(state().copy(sessionId = 43L))
        assertEquals(NavigationCameraMode.FOLLOW, camera.mode)
    }
}
