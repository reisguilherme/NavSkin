package br.com.gamemap.navigation.provider

import android.app.Application
import br.com.gamemap.navigation.model.*
import java.io.IOException
import java.net.URLDecoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Fixtures follow Geoapify's documented GeoJSON schema, not a live service recording. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class GeoapifyProviderTest {
    private val origin = GeoPoint(-16.68, -49.25)
    private val destination = Destination("saved:1", "Destino", GeoPoint(-16.679, -49.248), "Goiânia")

    @Test fun intermediateStopIsRequestedBeforeFinalDestinationAndMappedToItsLegBoundary() {
        val stop = Destination("stop", "Café", GeoPoint(-16.679, -49.249))
        val responses = Responses(HttpResponse(200, MULTI_LEG))
        val route = GeoapifyProvider({ "key" }, responses).route(origin, destination, listOf(stop))
        val query = URLDecoder.decode(responses.urls.single(), "UTF-8")
        assertTrue(query.contains("waypoints=-16.68,-49.25|-16.679,-49.249|-16.679,-49.248"))
        assertTrue(query.contains("intermediate_waypoint_mode=stopover"))
        assertTrue(query.contains("optimize_stops=false"))
        assertEquals(destination, route.destination)
        assertEquals(stop, route.intermediateStops.single().destination)
        assertEquals(2, route.intermediateStops.single().geometryIndex)
        assertEquals(217.7, route.intermediateStops.single().alongRouteMeters, 1.0)
        assertEquals(1, route.steps.count { it.kind == ManeuverKind.ARRIVE })
    }

    @Test fun missingIntermediateLegCannotSilentlySkipARequestedStop() {
        val stops = listOf(Destination("one", "Um", GeoPoint(-16.679, -49.249)),
            Destination("two", "Dois", GeoPoint(-16.679, -49.2485)))
        expect(ProviderErrorKind.INVALID_RESPONSE) {
            provider(HttpResponse(200, MULTI_LEG)).route(origin, destination, stops)
        }
    }

    @Test fun routeKeepsSecondsAndConvertsLegLocalIndicesAfterSharedEndpointDeduplication() {
        val transport = Responses(HttpResponse(200, MULTI_LEG))
        val route = GeoapifyProvider({ "private-test-key" }, transport, { 1234L }).route(origin, destination)
        assertEquals(4, route.geometry.size)
        assertEquals(GeoPoint(-16.679, -49.248), route.geometry.last())
        assertEquals(20.0, route.durationSeconds, 0.001)
        assertEquals(300.0, route.distanceMeters, 0.001)
        assertEquals(1234L, route.calculatedAtMs)
        assertEquals(listOf(0, 2, 3), route.steps.map { it.startIndex })
        assertEquals(listOf(2, 3, 3), route.steps.map { it.endIndex })
        assertEquals(listOf(12.0, 8.0, 0.0), route.steps.map { it.durationSeconds })
        assertEquals(ManeuverKind.RIGHT, route.steps[1].kind)
        assertEquals("Rua Dois", route.steps[1].streetName)
        assertEquals(ManeuverKind.ARRIVE, route.steps.last().kind)
        val query = URLDecoder.decode(transport.urls.single(), "UTF-8")
        assertTrue(query.startsWith("https://api.geoapify.com/v1/routing?"))
        assertTrue(query.contains("waypoints=-16.68,-49.25|-16.679,-49.248"))
        assertTrue(query.contains("lang=pt-BR"))
        assertTrue(query.contains("details=instruction_details"))
        assertTrue(query.contains("mode=drive"))
    }

    @Test fun maneuverOccursAtBeginningOfProviderStepAndUnknownTypeRemainsNeutral() {
        val response = MULTI_LEG.replace("\"Right\"", "\"FutureManeuver\"")
        val route = provider(HttpResponse(200, response)).route(origin, destination)
        assertEquals(ManeuverKind.UNKNOWN, route.steps[1].kind)
        assertEquals(2, route.steps[1].startIndex)
        assertEquals("Vire à direita na Rua Dois.", route.steps[1].instruction)
    }

    @Test fun roundaboutExitAndStreetComeFromStructuredInstructionMetadata() {
        val response = MULTI_LEG.replace("\"Right\"", "\"Roundabout\"")
            .replace("\"streets\":[\"Rua Dois\"]", "\"streets\":[\"Rua Dois\"],\"roundabout_exit\":3")
        val route = provider(HttpResponse(200, response)).route(origin, destination)
        assertEquals(ManeuverKind.ROUNDABOUT, route.steps[1].kind)
        assertEquals(3, route.steps[1].exitNumber)
    }

    @Test fun missingArrivalInstructionGetsZeroDurationArrivalAtTheActualFinalCoordinate() {
        val response = MULTI_LEG.replace(
            ",{\"from_index\":1,\"to_index\":1,\"distance\":0,\"time\":0,\"instruction\":{\"type\":\"DestinationReached\",\"text\":\"Você chegou.\"}}", "")
        val route = provider(HttpResponse(200, response)).route(origin, destination)
        assertEquals(ManeuverKind.ARRIVE, route.steps.last().kind)
        assertEquals(3, route.steps.last().startIndex)
        assertEquals(0.0, route.steps.last().durationSeconds, 0.0)
    }

    @Test fun namedSearchCombinesAddressesAndNearbyPoiWithCorrectLongitudeLatitudeBias() {
        val responses = Responses(HttpResponse(200, ADDRESS), HttpResponse(200, PLACE))
        val results = GeoapifyProvider({ "key" }, responses).search("Café Azul", origin)
        assertEquals(listOf("Rua Um, 10", "Café Azul"), results.map { it.name })
        assertEquals("Rua Um, 10, Goiânia, Brasil", results[0].address)
        assertEquals(GeoPoint(-16.68, -49.25), results[0].point)
        assertEquals("poi:blue", results[1].id)
        val urls = responses.urls.map { URLDecoder.decode(it, "UTF-8") }
        assertTrue(urls[0].contains("/v1/geocode/search?"))
        assertTrue(urls[0].contains("lang=pt"))
        assertTrue(urls[0].contains("bias=proximity:-49.25,-16.68"))
        assertTrue(urls[1].contains("/v2/places?"))
        assertTrue(urls[1].contains("name=Café Azul"))
        assertTrue(urls[1].contains("categories="))
    }

    @Test fun searchWithoutGpsUsesGeocoderWithoutInventingALocation() {
        val responses = Responses(HttpResponse(200, ADDRESS))
        val results = GeoapifyProvider({ "key" }, responses).search("Rua Um", null)
        assertEquals(1, results.size)
        assertFalse(responses.urls.single().contains("bias="))
    }

    @Test fun duplicateSearchPlaceIdsAppearOnlyOnce() {
        val responses = Responses(HttpResponse(200, PLACE), HttpResponse(200, PLACE))
        assertEquals(1, GeoapifyProvider({ "key" }, responses).search("Café Azul", origin).size)
    }

    @Test fun missingKeyDoesNotMakeARequest() {
        val responses = Responses()
        expect(ProviderErrorKind.CONFIGURATION) {
            GeoapifyProvider({ "  " }, responses).route(origin, destination)
        }
        assertTrue(responses.urls.isEmpty())
    }

    @Test fun quotaErrorKeepsRetryAfterAndDoesNotExposeResponseOrKey() {
        val error = expect(ProviderErrorKind.RATE_LIMIT) {
            provider(HttpResponse(429, "key=private-test-key", mapOf("Retry-After" to listOf("45"))))
                .route(origin, destination)
        }
        assertEquals(45L, error.retryAfterSeconds)
        assertFalse(error.toString().contains("private-test-key"))
        assertNull(error.cause)
    }

    @Test fun authenticationAndNetworkFailuresAreDistinct() {
        expect(ProviderErrorKind.AUTHENTICATION) { provider(HttpResponse(401, "secret response")).search("Rua Um") }
        expect(ProviderErrorKind.AUTHENTICATION) { provider(HttpResponse(403, "secret response")).search("Rua Um") }
        expect(ProviderErrorKind.OFFLINE) {
            GeoapifyProvider({ "key" }, HttpTransport { throw IOException("url contains key") }).search("Rua Um")
        }
    }

    @Test fun providerNoRouteAndEmptySearchHaveExplicitStates() {
        expect(ProviderErrorKind.NO_ROUTE) {
            provider(HttpResponse(400, """{"statusCode":400,"error":"Bad Request","message":"Locations are in unconnected regions."}"""))
                .route(origin, destination)
        }
        expect(ProviderErrorKind.NO_ROUTE) { provider(HttpResponse(200, EMPTY)).route(origin, destination) }
        expect(ProviderErrorKind.NO_RESULTS) { provider(HttpResponse(200, EMPTY)).search("Rua Um") }
    }

    @Test fun malformedNonfiniteAndOutOfBoundsCoordinatesCannotProduceARoute() {
        for (body in listOf("not JSON", MULTI_LEG.replace("-49.249", "\"NaN\""), MULTI_LEG.replace("-16.679", "91.0"))) {
            expect(ProviderErrorKind.INVALID_RESPONSE) { provider(HttpResponse(200, body)).route(origin, destination) }
        }
    }

    @Test fun invalidStepIntervalsAndNegativeDurationsCannotProduceGuidance() {
        for (body in listOf(MULTI_LEG.replace("\"to_index\":2", "\"to_index\":99"),
            MULTI_LEG.replace("\"time\":12", "\"time\":-12"),
            MULTI_LEG.replace("\"from_index\":0", "\"from_index\":0.5"))) {
            expect(ProviderErrorKind.INVALID_RESPONSE) { provider(HttpResponse(200, body)).route(origin, destination) }
        }
    }

    @Test fun disconnectedLegsAndDegenerateGeometryAreRejected() {
        val disconnected = MULTI_LEG.replace("[[-49.249,-16.679],[-49.248", "[[-48.249,-15.679],[-49.248")
        val degenerate = MULTI_LEG.replace("-49.249", "-49.25").replace("-49.248", "-49.25").replace("-16.679", "-16.68")
        for (body in listOf(disconnected, degenerate)) {
            expect(ProviderErrorKind.INVALID_RESPONSE) { provider(HttpResponse(200, body)).route(origin, destination) }
        }
    }

    @Test fun reverseReturnsAddressAndUsesLatitudeLongitudeParameters() {
        val responses = Responses(HttpResponse(200, ADDRESS))
        assertEquals("Rua Um, 10, Goiânia, Brasil", GeoapifyProvider({ "key" }, responses).reverse(origin))
        assertTrue(responses.urls.single().contains("lat=-16.68&lon=-49.25"))
    }

    @Test fun defaultTransportRejectsCleartextWithoutMakingAConnection() {
        expect(ProviderErrorKind.CONFIGURATION) { HttpsTransport.get("http://api.geoapify.com/v1/routing?apiKey=secret") }
    }

    private fun provider(response: HttpResponse) = GeoapifyProvider({ "private-test-key" }, Responses(response))

    private fun expect(kind: ProviderErrorKind, block: () -> Unit): TypedProviderException {
        try { block(); fail("Expected $kind") } catch (error: TypedProviderException) {
            assertEquals(kind, error.kind)
            return error
        }
        throw AssertionError("Unreachable")
    }

    private class Responses(vararg responses: HttpResponse) : HttpTransport {
        private val queue = ArrayDeque(responses.toList())
        val urls = mutableListOf<String>()
        override fun get(url: String): HttpResponse {
            urls += url
            return queue.removeFirst()
        }
    }

    companion object {
        private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        private const val ADDRESS = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-49.25,-16.68]},"properties":{"place_id":"address:1","address_line1":"Rua Um, 10","address_line2":"Goiânia, Brasil","formatted":"Rua Um, 10, Goiânia, Brasil","lat":-16.68,"lon":-49.25,"result_type":"building","rank":{"confidence":1}}}]}"""
        private const val PLACE = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[-49.248,-16.679]},"properties":{"place_id":"poi:blue","name":"Café Azul","address_line1":"Café Azul","address_line2":"Rua Dois, Goiânia","formatted":"Café Azul, Rua Dois, Goiânia","categories":["catering.cafe"],"lat":-16.679,"lon":-49.248}}]}"""
        private const val MULTI_LEG = """{"type":"FeatureCollection","properties":{"mode":"drive","units":"metric"},"features":[{"type":"Feature","geometry":{"type":"MultiLineString","coordinates":[[[-49.25,-16.68],[-49.249,-16.68],[-49.249,-16.679]],[[-49.249,-16.679],[-49.248,-16.679]]]},"properties":{"mode":"drive","units":"metric","distance":300,"distance_units":"meters","time":20,"legs":[{"distance":200,"time":12,"steps":[{"from_index":0,"to_index":2,"distance":200,"time":12,"instruction":{"type":"StartAt","text":"Siga na Rua Um.","streets":["Rua Um"]}}]},{"distance":100,"time":8,"steps":[{"from_index":0,"to_index":1,"distance":100,"time":8,"instruction":{"type":"Right","text":"Vire à direita na Rua Dois.","streets":["Rua Dois"]}},{"from_index":1,"to_index":1,"distance":0,"time":0,"instruction":{"type":"DestinationReached","text":"Você chegou."}}]}]}}]}"""
    }
}
