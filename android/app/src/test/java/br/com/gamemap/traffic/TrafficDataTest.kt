package br.com.gamemap.traffic

import android.app.Application
import br.com.gamemap.navigation.model.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TrafficDataTest {
    // Synthetic example follows the official Orbis v2 contract; no live reports or credentials.
    private val fixture = """{"incidents":[
      {"type":"Feature","geometry":{"type":"LineString","coordinates":[[-49.25,-16.68],[-49.24,-16.67]]},
       "properties":{"id":"synthetic-accident","iconCategory":"accident","magnitudeOfDelay":"major",
         "events":[{"description":"Acidente informado","code":101,"iconCategory":"accident"}],
         "delayInSeconds":180,"lastReportTime":"2026-10-29T12:00:00Z","startTime":null,"endTime":null}},
      {"type":"Feature","geometry":{"type":"Point","coordinates":[-49.26,-16.69]},
       "properties":{"id":"synthetic-future-category","iconCategory":"futureCategory","magnitudeOfDelay":"unknown",
         "events":[],"delayInSeconds":null,"lastReportTime":null,"startTime":null,"endTime":null}}
    ]}"""

    @Test fun decodesOrbisStringCategoriesGeometryAndNullableMetadata() {
        val incidents = TrafficIncidentParser.parse(fixture)
        assertEquals(2, incidents.size)
        assertEquals(TrafficIncidentKind.ACCIDENT, incidents[0].kind)
        assertEquals(GeoPoint(-16.68, -49.25), incidents[0].points.first())
        assertEquals(180, incidents[0].delaySeconds)
        assertEquals(1793275200000L, incidents[0].reportedAtMs)
        assertEquals(TrafficIncidentKind.OTHER, incidents[1].kind)
        assertNull(incidents[1].delaySeconds)
        assertNull(incidents[1].reportedAtMs)
    }

    @Test fun malformedPayloadIsNotInterpretedAsNoIncidents() {
        for (payload in listOf("{}", fixture.replace("-16.68", "-96.68"), "{\"incidents\":null}")) {
            assertThrows(TrafficException::class.java) { TrafficIncidentParser.parse(payload) }
        }
        assertTrue(TrafficIncidentParser.parse("{\"incidents\":[]}").isEmpty())
    }

    @Test fun queryAreaIsClippedAndPolarOrDatelineViewsAreRejected() {
        val area = TrafficBounds.forViewport(-20.0, -55.0, -10.0, -40.0, GeoPoint(-16.68, -49.25))!!
        assertTrue(area.north - area.south <= 0.1)
        assertTrue(area.east - area.west <= 0.11)
        assertTrue(area.contains(GeoPoint(-16.68, -49.25)))
        assertNull(TrafficBounds.forViewport(-10.0, 170.0, 10.0, -170.0, GeoPoint(0.0, 180.0)))
        assertNull(TrafficBounds.forViewport(84.0, 0.0, 86.0, 5.0, GeoPoint(85.0, 2.0)))
    }

    @Test fun refreshGateLimitsRepeatedMovementAndBacksOffQuotaFailures() {
        val gate = TrafficRefreshGate()
        assertTrue(gate.tryBegin(0))
        assertFalse(gate.tryBegin(10_000))
        gate.succeeded()
        assertFalse(gate.tryBegin(119_000))
        assertTrue(gate.tryBegin(120_000))
        gate.failed(120_000, TrafficFailure.RATE_LIMIT, 600)
        assertFalse(gate.tryBegin(719_000))
        assertTrue(gate.tryBegin(720_000))
    }
}
