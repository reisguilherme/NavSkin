package br.com.gamemap.navigation.provider

import br.com.gamemap.navigation.model.*
import br.com.gamemap.navigation.core.GeoMath
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/** Blocking, authenticated production API. Call on an executor, never the Android main thread. */
class GeoapifyProvider(
    private val apiKey: () -> String,
    private val transport: HttpTransport = HttpsTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun search(query: String, bias: GeoPoint? = null): List<Destination> {
        val key = key()
        val text = query.trim().take(240)
        if (text.length < 2) throw TypedProviderException(ProviderErrorKind.NO_RESULTS)
        bias?.let(::validatePoint)
        val params = linkedMapOf("text" to text, "lang" to "pt", "limit" to "8", "format" to "geojson")
        bias?.let { params["bias"] = "proximity:${it.longitude},${it.latitude}" }
        val addresses = parsePlaces(request("/v1/geocode/search", params, key))
        // Places requires a spatial control. Without GPS, the geocoder still searches addresses
        // and named amenities globally, without making up a location or geographic restriction.
        val places = if (bias == null) emptyList() else parsePlaces(request("/v2/places", linkedMapOf(
            "categories" to "commercial,catering,accommodation,healthcare,leisure,entertainment,sport,tourism",
            "name" to text, "lang" to "pt", "limit" to "8",
            "bias" to "proximity:${bias.longitude},${bias.latitude}",
        ), key))
        return (addresses + places).distinctBy { it.id }.take(16).ifEmpty {
            throw TypedProviderException(ProviderErrorKind.NO_RESULTS)
        }
    }

    fun route(origin: GeoPoint, destination: Destination, intermediateStops: List<Destination> = emptyList()): NavRoute {
        val key = key()
        validatePoint(origin)
        validatePoint(destination.point)
        intermediateStops.forEach { validatePoint(it.point) }
        val waypoints = listOf(origin) + intermediateStops.map { it.point } + destination.point
        if (intermediateStops.map { it.id }.distinct().size != intermediateStops.size ||
            intermediateStops.any { it.id == destination.id || samePoint(it.point, destination.point) } ||
            waypoints.zipWithNext().any { (a, b) -> samePoint(a, b) }) invalid()
        val json = request("/v1/routing", linkedMapOf(
            "waypoints" to waypoints.joinToString("|") { "${it.latitude},${it.longitude}" },
            "intermediate_waypoint_mode" to "stopover", "optimize_stops" to "false",
            "mode" to "drive", "units" to "metric", "lang" to "pt-BR",
            "details" to "instruction_details", "traffic" to "approximated", "format" to "geojson",
        ), key, routing = true)
        return parseRoute(json, destination, intermediateStops)
    }

    fun reverse(point: GeoPoint): String? {
        val key = key()
        validatePoint(point)
        val json = request("/v1/geocode/reverse", linkedMapOf(
            "lat" to point.latitude.toString(), "lon" to point.longitude.toString(),
            "lang" to "pt", "limit" to "1", "format" to "geojson",
        ), key)
        return parsePlaces(json).firstOrNull()?.address?.takeIf { it.isNotBlank() }
    }

    private fun key(): String {
        val value = try { apiKey().trim() } catch (_: Exception) { "" }
        if (value.isEmpty() || value.length > 512 || value.any { it.isWhitespace() }) {
            throw TypedProviderException(ProviderErrorKind.CONFIGURATION)
        }
        return value
    }

    private fun request(path: String, params: Map<String, String>, key: String, routing: Boolean = false): JSONObject {
        val query = (params + ("apiKey" to key)).entries.joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, Charsets.UTF_8.name())}"
        }
        val response = try { transport.get("https://api.geoapify.com$path?$query") }
        catch (error: TypedProviderException) { throw error }
        catch (_: SocketTimeoutException) { throw TypedProviderException(ProviderErrorKind.UNAVAILABLE) }
        catch (_: IOException) { throw TypedProviderException(ProviderErrorKind.OFFLINE) }
        catch (_: Exception) { throw TypedProviderException(ProviderErrorKind.UNAVAILABLE) }
        if (response.code !in 200..299) {
            val retry = retryAfter(response)
            val message = response.body.take(8192).lowercase()
            val kind = when {
                response.code == 429 || (response.code == 403 &&
                    listOf("quota", "credit", "rate limit", "daily limit").any(message::contains)) -> ProviderErrorKind.RATE_LIMIT
                response.code in listOf(401, 403) -> ProviderErrorKind.AUTHENTICATION
                routing && response.code in listOf(400, 404, 422) &&
                    listOf("unconnected", "route", "routable", "waypoint", "could not find").any(message::contains) -> ProviderErrorKind.NO_ROUTE
                else -> ProviderErrorKind.UNAVAILABLE
            }
            throw TypedProviderException(kind, retry)
        }
        return parseSafely {
            if (response.body.length > 4 * 1024 * 1024) invalid()
            val json = JSONObject(response.body)
            if (json.getString("type") != "FeatureCollection" || json.optJSONArray("features") == null) invalid()
            json
        }
    }

    private fun retryAfter(response: HttpResponse): Long? {
        val value = response.headers.entries.firstOrNull { it.key.equals("Retry-After", true) }
            ?.value?.firstOrNull()?.trim() ?: return null
        value.toLongOrNull()?.let { return it.takeIf { number -> number >= 0 } }
        return try {
            ((ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - clock()) / 1000)
                .coerceAtLeast(0)
        } catch (_: Exception) { null }
    }

    private fun parsePlaces(json: JSONObject): List<Destination> = parseSafely {
        val features = json.getJSONArray("features")
        (0 until features.length()).map { index ->
            val feature = features.getJSONObject(index)
            val geometry = feature.getJSONObject("geometry")
            if (geometry.getString("type") != "Point") invalid()
            val point = point(geometry.getJSONArray("coordinates"))
            val properties = feature.getJSONObject("properties")
            val address = properties.text("formatted").ifBlank {
                listOf(properties.text("address_line1"), properties.text("address_line2"))
                    .filter(String::isNotBlank).joinToString(", ")
            }
            val name = properties.text("name").ifBlank { properties.text("address_line1") }.ifBlank { address }
            if (name.isBlank()) invalid()
            val id = properties.text("place_id").ifBlank {
                "geoapify:" + UUID.nameUUIDFromBytes("${point.latitude},${point.longitude}:$name".toByteArray(Charsets.UTF_8))
            }
            Destination(id, name, point, address)
        }
    }

    private fun parseRoute(json: JSONObject, destination: Destination, intermediateStops: List<Destination>): NavRoute = parseSafely {
        val features = json.getJSONArray("features")
        if (features.length() == 0) throw TypedProviderException(ProviderErrorKind.NO_ROUTE)
        val feature = features.getJSONObject(0)
        val geometry = feature.getJSONObject("geometry")
        if (geometry.getString("type") != "MultiLineString") invalid()
        val coordinates = geometry.getJSONArray("coordinates")
        val properties = feature.getJSONObject("properties")
        val legs = properties.getJSONArray("legs")
        if (legs.length() == 0 || coordinates.length() != legs.length()) invalid()
        if (intermediateStops.isNotEmpty() && legs.length() != intermediateStops.size + 1) invalid()
        val totalDistance = properties.nonnegative("distance")
        val totalTime = properties.nonnegative("time")
        if (totalDistance <= 0 || totalTime <= 0) invalid()
        val points = mutableListOf<GeoPoint>()
        val steps = mutableListOf<RouteStep>()
        val boundaries = mutableListOf<RouteStop>()
        var alongMeters = 0.0
        for (legIndex in 0 until legs.length()) {
            val legCoordinates = coordinates.getJSONArray(legIndex)
            if (legCoordinates.length() < 2) invalid()
            val indexMap = IntArray(legCoordinates.length())
            for (index in 0 until legCoordinates.length()) {
                val p = point(legCoordinates.getJSONArray(index))
                if (index == 0 && legIndex > 0 && !samePoint(points.last(), p)) invalid()
                if (points.lastOrNull()?.let { samePoint(it, p) } != true) {
                    points.lastOrNull()?.let { alongMeters += GeoMath.distanceMeters(it, p) }
                    points += p
                }
                indexMap[index] = points.lastIndex
            }
            intermediateStops.getOrNull(legIndex)?.let {
                boundaries += RouteStop(it, points.lastIndex, alongMeters)
            }
            val leg = legs.getJSONObject(legIndex)
            leg.nonnegative("distance")
            leg.nonnegative("time")
            val legSteps = leg.getJSONArray("steps")
            if (legSteps.length() == 0) invalid()
            var previousEnd = 0
            for (stepIndex in 0 until legSteps.length()) {
                val raw = legSteps.getJSONObject(stepIndex)
                val from = raw.index("from_index")
                val to = raw.index("to_index")
                if (from !in indexMap.indices || to !in indexMap.indices || to < from || from != previousEnd) invalid()
                previousEnd = to
                val distance = raw.nonnegative("distance")
                val duration = raw.nonnegative("time")
                val instruction = raw.getJSONObject("instruction")
                val text = instruction.text("text")
                if (text.isBlank()) invalid()
                val kind = maneuver(instruction.text("type"))
                // A via-point arrival is not the destination arrival of this route.
                if (kind == ManeuverKind.ARRIVE && legIndex < legs.length() - 1) continue
                val street = instruction.optJSONArray("streets")?.let { names ->
                    (0 until names.length()).map { names.optString(it, "").trim() }
                        .filter(String::isNotBlank).joinToString(" / ")
                }.orEmpty().ifBlank { raw.text("name") }
                val exit = if (instruction.has("roundabout_exit") && !instruction.isNull("roundabout_exit"))
                    instruction.index("roundabout_exit").takeIf { it > 0 } else null
                steps += RouteStep(steps.size, text, street, kind, indexMap[from], indexMap[to], distance, duration, exit)
            }
            if (previousEnd != legCoordinates.length() - 1) invalid()
        }
        if (points.size < 2 || steps.isEmpty()) invalid()
        if (abs(steps.sumOf { it.durationSeconds } - totalTime) > max(1.0, totalTime * .05) ||
            abs(steps.sumOf { it.distanceMeters } - totalDistance) > max(10.0, totalDistance * .05)) invalid()
        if (steps.last().kind != ManeuverKind.ARRIVE || steps.last().startIndex != points.lastIndex) {
            steps += RouteStep(steps.size, "Você chegou ao destino.", "", ManeuverKind.ARRIVE,
                points.lastIndex, points.lastIndex, 0.0, 0.0)
        }
        NavRoute("geoapify:${UUID.randomUUID()}", points, steps, totalDistance, totalTime, destination, clock(), boundaries)
    }

    private fun maneuver(type: String): ManeuverKind = when (type) {
        "StartAt", "StartAtRight", "StartAtLeft" -> ManeuverKind.DEPART
        "DestinationReached", "DestinationReachedRight", "DestinationReachedLeft" -> ManeuverKind.ARRIVE
        "Straight" -> ManeuverKind.STRAIGHT
        "SlightLeft" -> ManeuverKind.SLIGHT_LEFT
        "Left" -> ManeuverKind.LEFT
        "SharpLeft" -> ManeuverKind.SHARP_LEFT
        "SlightRight" -> ManeuverKind.SLIGHT_RIGHT
        "Right" -> ManeuverKind.RIGHT
        "SharpRight" -> ManeuverKind.SHARP_RIGHT
        "TurnAroundLeft", "TurnAroundRight" -> ManeuverKind.UTURN
        "Roundabout" -> ManeuverKind.ROUNDABOUT
        "Merge", "MergeRight", "MergeLeft" -> ManeuverKind.MERGE
        "StayRight", "StayLeft" -> ManeuverKind.FORK
        "ExitRight", "ExitLeft" -> ManeuverKind.EXIT
        else -> ManeuverKind.UNKNOWN
    }

    private fun point(coordinates: JSONArray): GeoPoint {
        if (coordinates.length() < 2) invalid()
        return GeoPoint(coordinates.getDouble(1), coordinates.getDouble(0)).also(::validatePoint)
    }

    private fun validatePoint(point: GeoPoint) {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) invalid()
    }

    private fun samePoint(a: GeoPoint, b: GeoPoint) =
        abs(a.latitude - b.latitude) < 1e-8 && abs(a.longitude - b.longitude) < 1e-8

    private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name, "").trim()
    private fun JSONObject.nonnegative(name: String): Double = getDouble(name).also {
        if (!it.isFinite() || it < 0) invalid()
    }
    private fun JSONObject.index(name: String): Int = nonnegative(name).let {
        if (it > Int.MAX_VALUE || it != it.toInt().toDouble()) invalid()
        it.toInt()
    }
    private fun invalid(): Nothing = throw TypedProviderException(ProviderErrorKind.INVALID_RESPONSE)
    private inline fun <T> parseSafely(block: () -> T): T = try { block() }
        catch (error: TypedProviderException) { throw error }
        catch (_: Exception) { invalid() }
}
