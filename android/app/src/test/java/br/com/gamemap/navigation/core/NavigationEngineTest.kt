package br.com.gamemap.navigation.core

import br.com.gamemap.navigation.model.*
import org.junit.Assert.*
import org.junit.Test

/** Explicit synthetic metre-grid GPS fixtures; these never run in production. */
class NavigationEngineTest {
    private fun point(x: Double, y: Double = 0.0) = GeoPoint(y / 111195.0, x / 111195.0)
    private fun route(points: List<GeoPoint> = listOf(point(0.0), point(300.0)),
                      steps: List<RouteStep> = emptyList(), duration: Double = 120.0) = NavRoute(
        "test-route", points, steps, 300.0, duration,
        Destination("test-destination", "Synthetic destination", points.last()), 1_000_000L,
    )
    private fun fix(x: Double, y: Double = 0.0, t: Long = 100_000L,
                    accuracy: Double = 5.0, speed: Double? = 10.0,
                    bearing: Double? = 90.0, speedAccuracy: Double? = 0.5) = LocationFix(
        point(x, y), accuracy, bearing, speed, speedAccuracy, t, 1_000_000L + t,
    )
    private fun NavigationEngine.feed(value: LocationFix) =
        onFix(value, value.elapsedRealtimeMs, value.wallTimeMs)

    @Test fun providerSpeedIsCurrentAndParkedBearingDoesNotSpin() {
        val engine = NavigationEngine()
        val moving = engine.feed(fix(0.0, speed = 10.0, bearing = 90.0))
        assertEquals(36.0, moving.location.speedKmh!!, 0.01)
        val stopped = engine.feed(fix(1.0, t = 101_000L, speed = 0.1, bearing = 270.0))
        assertEquals(0.0, stopped.location.speedKmh!!, 0.01)
        assertEquals(90.0, stopped.location.bearingDegrees!!, 0.01)
    }

    @Test fun inaccurateProviderSpeedIsUnavailable() {
        val update = NavigationEngine().feed(fix(0.0, speedAccuracy = 12.0))
        assertNull(update.location.speedKmh)
    }

    @Test fun reliableSpeedChangesPromptlyWithoutRepeatingAnOldValue() {
        val engine = NavigationEngine()
        engine.feed(fix(0.0, speed = 5.0))
        val update = engine.feed(fix(20.0, t = 101_000L, speed = 15.0))
        assertTrue(update.location.speedKmh!! > 40.0)
    }

    @Test fun staleSignalHidesSpeedAndProgressAndThenRecovers() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        val weak = engine.onTick(106_000L, 1_106_000L)
        assertEquals(LocationQuality.WEAK, weak.location.quality)
        assertNull(weak.location.speedKmh)
        assertNull(weak.progress)
        val lost = engine.onTick(116_000L, 1_116_000L)
        assertEquals(LocationQuality.LOST, lost.location.quality)
        assertNotNull(lost.location.fix)
        val recovered = engine.feed(fix(30.0, t = 117_000L))
        assertEquals(LocationQuality.GOOD, recovered.location.quality)
        assertTrue(recovered.progress!!.alongRouteMeters > 25.0)
    }

    @Test fun outOfOrderFixCannotRewindLocationOrAdvanceRoute() {
        val engine = NavigationEngine().apply { start(route()) }
        val first = engine.feed(fix(20.0, t = 102_000L))
        val old = engine.onFix(fix(200.0, t = 101_000L), 102_000L, 1_102_000L)
        assertEquals(first.location.fix, old.location.fix)
        assertEquals(first.progress!!.alongRouteMeters, old.progress!!.alongRouteMeters, 0.01)
    }

    @Test fun alreadyStaleFixCannotAdvanceOrExposeCurrentSpeed() {
        val engine = NavigationEngine().apply { start(route()) }
        val update = engine.onFix(fix(200.0), 120_000L, 1_120_000L)
        assertEquals(LocationQuality.LOST, update.location.quality)
        assertNull(update.location.speedKmh)
        assertNull(update.progress)
    }

    @Test fun inaccurateGpsDoesNotAdvanceOrCauseRerouting() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        repeat(5) { index ->
            val update = engine.feed(fix(200.0, 80.0, 101_000L + index * 1_000L, accuracy = 70.0))
            assertEquals(LocationQuality.WEAK, update.location.quality)
            assertNull(update.progress)
            assertFalse(update.shouldRecalculate)
        }
        assertTrue(engine.feed(fix(30.0, t = 107_000L)).progress!!.alongRouteMeters < 40.0)
    }

    @Test fun routeDurationUsesRemainingStepsEvenWithFastInstantaneousSpeed() {
        val steps = listOf(
            RouteStep(0, "Straight", "A", ManeuverKind.STRAIGHT, 0, 1, 100.0, 100.0),
            RouteStep(1, "Arrive", "B", ManeuverKind.ARRIVE, 1, 2, 200.0, 20.0),
        )
        val engine = NavigationEngine().apply {
            start(route(listOf(point(0.0), point(100.0), point(300.0)), steps))
        }
        val update = engine.feed(fix(50.0, speed = 30.0))
        assertEquals(70.0, update.progress!!.remainingSeconds, 0.1)
        assertEquals(1_170_000L, update.progress!!.arrivalEpochMs)
        assertEquals(50.0, update.progress!!.distanceToManeuverMeters, 0.1)
    }

    @Test fun matchingDoesNotJumpToRepeatedLaterGeometry() {
        val engine = NavigationEngine().apply { start(route(listOf(
            point(0.0), point(200.0), point(200.0, 100.0), point(0.0, 100.0),
            point(0.0), point(200.0),
        ))) }
        engine.feed(fix(20.0))
        val update = engine.feed(fix(40.0, t = 102_000L))
        assertEquals(0, update.progress!!.segmentIndex)
        assertTrue(update.progress!!.alongRouteMeters < 50.0)
    }

    @Test fun headingAndContinuityKeepParallelRoadFromStealingMatch() {
        val engine = NavigationEngine().apply { start(route(listOf(
            point(0.0), point(200.0), point(200.0, 15.0), point(0.0, 15.0),
        ))) }
        engine.feed(fix(20.0))
        val update = engine.feed(fix(30.0, 12.0, 101_000L))
        assertEquals(0, update.progress!!.segmentIndex)
        assertTrue(update.progress!!.alongRouteMeters < 40.0)
    }

    @Test fun crossingStaysOnTheCurrentLeg() {
        val engine = NavigationEngine().apply { start(route(listOf(
            point(0.0), point(100.0, 100.0), point(0.0, 100.0),
            point(100.0), point(200.0),
        ))) }
        engine.feed(fix(20.0, 20.0, bearing = 45.0))
        val update = engine.feed(fix(50.0, 50.0, 103_000L, bearing = 45.0))
        assertEquals(0, update.progress!!.segmentIndex)
        assertTrue(update.progress!!.alongRouteMeters < 80.0)
    }

    @Test fun oneAccurateOutlierDoesNotTriggerRerouting() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        assertFalse(engine.feed(fix(30.0, 80.0, 101_000L)).shouldRecalculate)
        assertFalse(engine.feed(fix(40.0, t = 102_000L)).shouldRecalculate)
    }

    @Test fun consistentDeviationUnsnapAndRerouteOnlyOnceDuringCooldown() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        assertFalse(engine.feed(fix(30.0, 70.0, 101_000L)).shouldRecalculate)
        assertFalse(engine.feed(fix(40.0, 72.0, 102_000L)).shouldRecalculate)
        val confirmed = engine.feed(fix(50.0, 74.0, 103_000L))
        assertTrue(confirmed.shouldRecalculate)
        assertFalse(confirmed.progress!!.onRoute)
        assertTrue(confirmed.progress!!.distanceFromRouteMeters > 65.0)
        assertTrue(confirmed.progress!!.alongRouteMeters < 25.0)
        repeat(5) { index ->
            assertFalse(engine.feed(fix(60.0 + index, 80.0, 104_000L + index * 1_000L)).shouldRecalculate)
        }
    }

    @Test fun weakAccuracyDoesNotTurnAPlausibleRoadOffsetIntoDeviation() {
        val engine = NavigationEngine().apply { start(route()) }
        repeat(6) { index ->
            assertFalse(engine.feed(fix(20.0 + index, 45.0, 100_000L + index * 1_000L,
                accuracy = 30.0)).shouldRecalculate)
        }
    }

    @Test fun passingDestinationNearStartOfLoopCannotArrive() {
        val engine = NavigationEngine().apply { start(route(listOf(
            point(0.0), point(200.0), point(200.0, 100.0), point(0.0, 100.0), point(0.0),
        ))) }
        repeat(3) { index ->
            assertFalse(engine.feed(fix(index.toDouble(), t = 100_000L + index * 1_000L)).arrived)
        }
    }

    @Test fun arrivalNeedsSuccessiveGoodFixesNearFinalProgress() {
        val engine = NavigationEngine().apply { start(route()) }
        (0..5).forEach { engine.feed(fix(it * 50.0, t = 100_000L + it * 5_000L)) }
        assertFalse(engine.feed(fix(286.0, t = 129_000L)).arrived)
        val arrived = engine.feed(fix(295.0, t = 130_000L))
        assertTrue(arrived.arrived)
        assertEquals(0.0, arrived.progress!!.remainingSeconds, 0.01)
    }

    @Test fun arrivalUsesDriveableEndpointWhenDestinationIsInsideAPark() {
        val requestedDestination = Destination("park", "Park entrance destination", point(100.0, 80.0))
        val drivingRoute = route(listOf(point(0.0), point(100.0))).copy(
            destination = requestedDestination, distanceMeters = 100.0,
        )
        val engine = NavigationEngine().apply { start(drivingRoute) }
        engine.feed(fix(0.0))
        assertFalse(engine.feed(fix(100.0, t = 110_000L)).arrived)
        val arrived = engine.feed(fix(100.0, t = 111_000L))
        assertTrue(arrived.arrived)
        assertEquals(0.0, arrived.progress!!.remainingDistanceMeters, 0.01)
        assertEquals(point(100.0, 80.0), drivingRoute.destination.point)
    }

    @Test fun repeatedTimestampCannotSatisfySuccessiveArrivalReadings() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(250.0))
        val nearEnd = fix(290.0, t = 105_000L)
        assertFalse(engine.feed(nearEnd).arrived)
        assertFalse(engine.feed(nearEnd).arrived)
    }

    @Test fun signalLossBreaksSuccessiveArrivalConfidence() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(250.0))
        assertFalse(engine.feed(fix(290.0, t = 105_000L)).arrived)
        engine.onTick(121_000L, 1_121_000L)
        assertFalse(engine.feed(fix(295.0, t = 122_000L)).arrived)
        assertTrue(engine.feed(fix(296.0, t = 123_000L)).arrived)
    }

    @Test fun gapBetweenFixesBreaksArrivalConfidenceWithoutATick() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(250.0))
        assertFalse(engine.feed(fix(290.0, t = 105_000L)).arrived)
        assertFalse(engine.feed(fix(295.0, t = 122_000L)).arrived)
    }

    @Test fun upcomingManeuverUsesTheNextStepStartAndAdvancesAfterTurn() {
        val steps = listOf(
            RouteStep(0, "Depart", "A", ManeuverKind.DEPART, 0, 1, 100.0, 40.0),
            RouteStep(1, "Turn left", "B", ManeuverKind.LEFT, 1, 2, 100.0, 60.0),
            RouteStep(2, "Arrive", "", ManeuverKind.ARRIVE, 2, 2, 0.0, 0.0),
        )
        val engine = NavigationEngine().apply {
            start(route(listOf(point(0.0), point(100.0), point(100.0, 100.0)), steps))
        }
        val approaching = engine.feed(fix(80.0)).progress!!
        assertEquals(1, approaching.stepIndex)
        assertEquals(20.0, approaching.distanceToManeuverMeters, 0.1)
        val afterTurn = engine.feed(fix(100.0, 10.0, 103_000L, bearing = 0.0)).progress!!
        assertEquals(2, afterTurn.stepIndex)
        assertEquals(90.0, afterTurn.distanceToManeuverMeters, 0.1)
        assertEquals(54.0, afterTurn.remainingSeconds, 0.1)
    }

    @Test fun unavailableAndInvalidCoordinatesCannotCreateACurrentPosition() {
        val engine = NavigationEngine()
        assertEquals(LocationQuality.UNAVAILABLE, engine.onTick(1_000L, 2_000L).location.quality)
        val invalid = fix(0.0).copy(point = GeoPoint(Double.NaN, 0.0))
        assertEquals(LocationQuality.UNAVAILABLE, engine.feed(invalid).location.quality)
        val future = fix(0.0, t = 110_000L)
        assertEquals(LocationQuality.UNAVAILABLE, engine.onFix(future, 100_000L, 1_100_000L).location.quality)
    }

    @Test fun shortLivedNoisyDeviationCannotImmediatelyRetryAfterConfirmedRequest() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        repeat(2) { index -> engine.feed(fix(30.0, 80.0, 101_000L + index * 1_000L)) }
        assertTrue(engine.feed(fix(30.0, 80.0, 103_000L)).shouldRecalculate)
        assertFalse(engine.feed(fix(40.0, t = 104_000L)).shouldRecalculate)
        assertFalse(engine.feed(fix(50.0, t = 105_000L)).shouldRecalculate)
        repeat(2) { index -> engine.feed(fix(60.0, 80.0, 106_000L + index * 1_000L)) }
        assertFalse(engine.feed(fix(60.0, 80.0, 108_000L)).shouldRecalculate)
    }

    @Test fun signalLossRequiresFreshDeviationEvidenceBeforeRetrying() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(20.0))
        repeat(2) { index -> engine.feed(fix(30.0, 80.0, 101_000L + index * 1_000L)) }
        assertTrue(engine.feed(fix(30.0, 80.0, 103_000L)).shouldRecalculate)
        engine.onTick(120_000L, 1_120_000L)
        assertFalse(engine.feed(fix(30.0, 80.0, 121_000L)).shouldRecalculate)
        assertFalse(engine.feed(fix(40.0, 80.0, 122_000L)).shouldRecalculate)
        assertTrue(engine.feed(fix(50.0, 80.0, 123_000L)).shouldRecalculate)
    }

    @Test fun zeroLengthSegmentsDoNotProduceNanProgress() {
        val engine = NavigationEngine().apply { start(route(listOf(
            point(0.0), point(0.0), point(100.0), point(100.0), point(300.0),
        ))) }
        val progress = engine.feed(fix(50.0)).progress!!
        assertTrue(progress.remainingSeconds.isFinite())
        assertEquals(50.0, progress.alongRouteMeters, 0.1)
    }

    @Test fun stoppingRemovesRouteButKeepsCurrentGps() {
        val engine = NavigationEngine().apply { start(route()) }
        engine.feed(fix(30.0))
        engine.stop()
        val update = engine.onTick(100_100L, 1_100_100L)
        assertNull(update.progress)
        assertEquals(LocationQuality.GOOD, update.location.quality)
    }
}
