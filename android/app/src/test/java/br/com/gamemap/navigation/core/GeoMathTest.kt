package br.com.gamemap.navigation.core

import br.com.gamemap.navigation.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test

class GeoMathTest {
    @Test fun datelineDistanceTakesTheShortWayAndProjectionStaysAtTheDateline() {
        val west = GeoPoint(0.0, 179.999)
        val east = GeoPoint(0.0, -179.999)
        assertEquals(222.39, GeoMath.distanceMeters(west, east), 0.1)
        val projected = GeoMath.project(GeoPoint(0.001, 180.0), west, east)
        assertEquals(0.5, projected.fraction, 0.001)
        assertEquals(111.195, projected.distanceMeters, 0.1)
        assertTrue(kotlin.math.abs(projected.point.longitude) > 179.99)
    }

    @Test fun identicalPointsHaveZeroDistanceAndFiniteProjection() {
        val point = GeoPoint(-16.68, -49.25)
        assertEquals(0.0, GeoMath.distanceMeters(point, point), 0.0)
        assertEquals(point, GeoMath.project(point, point, point).point)
    }

    @Test fun bearingsHandleTheNorthBoundaryAndDoNotSpinAcrossIt() {
        assertEquals(90.0, GeoMath.bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)), 0.01)
        assertEquals(0.0, GeoMath.bearingDegrees(GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.0)), 0.01)
        assertEquals(2.0, GeoMath.bearingDifference(359.0, 1.0), 0.001)
    }

    @Test fun antipodesAndPolesHaveFiniteDistances() {
        assertEquals(20_015_114.4, GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)), 1.0)
        assertTrue(GeoMath.project(GeoPoint(89.999, 10.0), GeoPoint(90.0, 0.0), GeoPoint(90.0, 90.0))
            .distanceMeters.isFinite())
    }
}
