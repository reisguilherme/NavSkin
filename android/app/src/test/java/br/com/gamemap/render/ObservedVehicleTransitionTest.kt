package br.com.gamemap.render

import br.com.gamemap.navigation.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class ObservedVehicleTransitionTest {
    @Test fun interpolationStaysBetweenTwoObservedFixesAndNeverExtrapolates() {
        val transition = ObservedVehicleTransition(GeoPoint(-16.0, -49.0), GeoPoint(-16.002, -49.004), 20.0, 40.0)
        assertEquals(-16.001, transition.sample(0.5).point.latitude, 0.000001)
        assertEquals(-49.002, transition.sample(0.5).point.longitude, 0.000001)
        assertEquals(30.0, transition.sample(0.5).bearing, 0.001)
        assertEquals(GeoPoint(-16.0, -49.0), transition.sample(-1.0).point)
        assertEquals(GeoPoint(-16.002, -49.004), transition.sample(2.0).point)
    }

    @Test fun bothPositionAndHeadingTakeTheShortArcAcrossTheirWrapBoundary() {
        val transition = ObservedVehicleTransition(GeoPoint(0.0, 179.9), GeoPoint(0.0, -179.9), 359.0, 1.0)
        assertEquals(180.0, kotlin.math.abs(transition.sample(0.5).point.longitude), 0.001)
        assertEquals(0.0, transition.sample(0.5).bearing, 0.001)
        assertEquals(GeoPoint(0.0, -179.9), transition.sample(1.0).point)
    }
}
