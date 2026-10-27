package br.com.gamemap.navigation.runtime

import br.com.gamemap.navigation.core.NavigationEngine
import br.com.gamemap.navigation.model.LocationQuality
import br.com.gamemap.navigation.model.ManeuverKind
import br.com.gamemap.phone.PhoneNavigationReplay
import org.junit.Assert.*
import org.junit.Test

/** Exercises the actual phone's explicitly labelled fixture through the production matcher. */
class PhoneReplayIntegrationTest {
    @Test fun phoneFixtureTraversesBothTurnsAndArrivesOnlyOnTheFinalLeg() {
        val route = PhoneNavigationReplay.route
        val engine = NavigationEngine().apply { start(route) }
        val fixtures = PhoneNavigationReplay.fixes()
        val maneuvers = mutableSetOf<ManeuverKind>()
        var firstArrival = -1
        var previousRemaining = Double.POSITIVE_INFINITY
        fixtures.forEachIndexed { index, source ->
            val fix = source.copy(elapsedRealtimeMs = 100_000L + index * 1_000L,
                wallTimeMs = 1_000_000L + index * 1_000L)
            val update = engine.onFix(fix, fix.elapsedRealtimeMs, fix.wallTimeMs)
            assertEquals(LocationQuality.GOOD, update.location.quality)
            assertFalse(update.shouldRecalculate)
            val progress = update.progress!!
            assertTrue(progress.onRoute)
            assertTrue(progress.remainingSeconds <= previousRemaining + 0.01)
            previousRemaining = progress.remainingSeconds
            maneuvers += route.steps[progress.stepIndex].kind
            if (update.arrived && firstArrival < 0) firstArrival = index
        }
        assertTrue(maneuvers.containsAll(setOf(ManeuverKind.LEFT, ManeuverKind.RIGHT, ManeuverKind.ARRIVE)))
        assertTrue(firstArrival >= fixtures.size - 6)
        assertEquals(0.0, previousRemaining, 0.001)
    }
}
