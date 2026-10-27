package br.com.gamemap.car

import android.app.Application
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.RoutingInfo
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CarNavigationModelsTest {
    private val destination = Destination("place", "Parque", GeoPoint(-16.67, -49.25), "Goiânia")
    private val route = NavRoute("route", listOf(GeoPoint(-16.68, -49.25), destination.point),
        listOf(
            RouteStep(0, "Siga", "Rua A", ManeuverKind.DEPART, 0, 1, 1_000.0, 100.0),
            RouteStep(1, "Você chegou", "Parque", ManeuverKind.ARRIVE, 1, 1, 0.0, 0.0),
        ), 1_000.0, 100.0, destination, 0)
    private val progress = RouteProgress("route", 400.0, GeoPoint(-16.676, -49.25), 0, 1,
        600.0, 600.0, 60.0, 1_700_000_060_000L, 0.0, true, false)
    private fun active() = NavigationSnapshot(phase = NavigationPhase.NAVIGATING,
        route = route, destination = destination, progress = progress, serviceRunning = true,
        locationAccess = LocationAccess.AVAILABLE,
        location = LocationState(quality = LocationQuality.GOOD))

    @Test fun realProgressProducesHostManeuverAndDestinationEstimate() {
        val state = active()
        val info = CarNavigationModels.navigationInfo(state) as RoutingInfo
        assertEquals("Você chegou", info.currentStep!!.cue!!.toString())
        assertEquals(600.0, info.currentDistance!!.displayDistance, 0.01)
        val trip = CarNavigationModels.trip(state)
        assertFalse(trip.isLoading)
        assertEquals(1, trip.steps.size)
        assertEquals("Parque", trip.destinations.single().name!!.toString())
        assertEquals(60L, trip.destinationTravelEstimates.single().remainingTimeSeconds)
        assertEquals(progress.arrivalEpochMs,
            trip.destinationTravelEstimates.single().arrivalTimeAtDestination!!.timeSinceEpochMillis)
    }

    @Test fun staleWeakAndFailedRerouteNeverPublishOldHostSteps() {
        listOf(
            active().copy(locationAccess = LocationAccess.LOST,
                location = LocationState(quality = LocationQuality.LOST)),
            active().copy(locationAccess = LocationAccess.WEAK,
                location = LocationState(quality = LocationQuality.WEAK)),
            active().copy(phase = NavigationPhase.REROUTE_FAILED),
            active().copy(progress = progress.copy(onRoute = false)),
        ).forEach { state ->
            assertTrue(CarNavigationModels.navigationInfo(state) is MessageInfo)
            assertTrue(CarNavigationModels.trip(state).steps.isEmpty())
            assertNull(CarNavigationModels.destinationEstimate(state))
        }
    }

    @Test fun recalculatingUsesValidEmptyLoadingModels() {
        val state = active().copy(phase = NavigationPhase.RECALCULATING, progress = null)
        val info = CarNavigationModels.navigationInfo(state) as RoutingInfo
        assertTrue(info.isLoading)
        assertNull(info.currentStep)
        assertTrue(CarNavigationModels.trip(state).isLoading)
        assertTrue(CarNavigationModels.trip(state).steps.isEmpty())
    }

    @Test fun carReplayIsExplicitAndHasBoundedSmallGpsSteps() {
        val fixes = CarNavigationReplay.fixes(route)
        assertTrue(fixes.size > 20)
        assertTrue(fixes.all { it.isMock && it.accuracyMeters <= 10.0 })
        assertEquals(destination.point, fixes.last().point)
        assertTrue(fixes.zipWithNext().all { (a, b) ->
            br.com.gamemap.navigation.core.GeoMath.distanceMeters(a.point, b.point) <= 11.0
        })
    }
}
