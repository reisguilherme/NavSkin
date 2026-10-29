package br.com.gamemap.traffic

import br.com.gamemap.navigation.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.cos

enum class TrafficFailure { AUTHENTICATION, RATE_LIMIT, OFFLINE, UNAVAILABLE, INVALID_RESPONSE }
class TrafficException(val kind: TrafficFailure, val retryAfterSeconds: Long? = null) : Exception(kind.name)

enum class TrafficIncidentKind(val label: String, val color: String) {
    ACCIDENT("Acidente", "#E8504A"), JAM("Congestionamento", "#EF9F30"),
    CLOSURE("Interdição", "#DC4457"), WORKS("Obras", "#E8A337"), OTHER("Ocorrência", "#D5A441");
    companion object {
        fun fromCategory(value: String) = when (value) {
            "accident" -> ACCIDENT
            "jam" -> JAM
            "roadClosed", "laneClosed" -> CLOSURE
            "roadWorks" -> WORKS
            else -> OTHER
        }
    }
}

data class TrafficIncident(
    val id: String,
    val kind: TrafficIncidentKind,
    val points: List<GeoPoint>,
    val description: String,
    val delaySeconds: Int?,
    val reportedAtMs: Long?,
)

object TrafficIncidentParser {
    const val MAX_BODY_BYTES = 2 * 1024 * 1024
    fun parse(body: String): List<TrafficIncident> = try {
        if (body.length > MAX_BODY_BYTES) invalid()
        val items = JSONObject(body).getJSONArray("incidents")
        if (items.length() > 500) invalid()
        var pointCount = 0
        (0 until items.length()).map { index ->
            val feature = items.getJSONObject(index)
            if (feature.getString("type") != "Feature") invalid()
            val geometry = feature.getJSONObject("geometry")
            val raw = geometry.getJSONArray("coordinates")
            val points = when (geometry.getString("type")) {
                "Point" -> listOf(point(raw))
                "LineString" -> {
                    if (raw.length() !in 2..4096) invalid()
                    (0 until raw.length()).map { point(raw.getJSONArray(it)) }
                }
                else -> invalid()
            }
            pointCount += points.size
            if (pointCount > 20_000) invalid()
            val properties = feature.getJSONObject("properties")
            val id = properties.getString("id").take(240)
            if (id.isBlank()) invalid()
            val kind = TrafficIncidentKind.fromCategory(properties.getString("iconCategory"))
            val event = properties.optJSONArray("events")?.optJSONObject(0)
            val description = event?.optString("description", "")?.take(300)?.trim().orEmpty().ifBlank { kind.label }
            val delay = if (properties.isNull("delayInSeconds")) null else properties.getDouble("delayInSeconds").let {
                if (!it.isFinite() || it < 0 || it > Int.MAX_VALUE || it % 1.0 != 0.0) invalid()
                it.toInt()
            }
            val reported = properties.optString("lastReportTime", "").takeIf { it.isNotBlank() && it != "null" }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            TrafficIncident(id, kind, points, description, delay, reported)
        }.distinctBy { it.id }
    } catch (error: TrafficException) { throw error }
      catch (_: Exception) { invalid() }

    private fun point(raw: JSONArray): GeoPoint {
        if (raw.length() < 2) invalid()
        val lon = raw.getDouble(0)
        val lat = raw.getDouble(1)
        if (!lon.isFinite() || !lat.isFinite() || lon !in -180.0..180.0 || lat !in -90.0..90.0) invalid()
        return GeoPoint(lat, lon)
    }
    private fun invalid(): Nothing = throw TrafficException(TrafficFailure.INVALID_RESPONSE)
}

/** A bounded viewport query, at most approximately 10 x 10 km, far below the provider's limit. */
data class TrafficBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val query: String get() = "$west,$south,$east,$north"
    fun contains(point: GeoPoint) = point.latitude in south..north && point.longitude in west..east
    companion object {
        fun forViewport(south: Double, west: Double, north: Double, east: Double, center: GeoPoint): TrafficBounds? {
            if (listOf(south, west, north, east, center.latitude, center.longitude).any { !it.isFinite() } ||
                south >= north || west >= east || center.latitude !in -80.0..80.0 ||
                center.longitude !in -180.0..180.0) return null
            val halfLatitude = 0.045
            val halfLongitude = halfLatitude / cos(Math.toRadians(center.latitude))
            val result = TrafficBounds(maxOf(south, center.latitude - halfLatitude, -85.0),
                maxOf(west, center.longitude - halfLongitude, -180.0),
                minOf(north, center.latitude + halfLatitude, 85.0),
                minOf(east, center.longitude + halfLongitude, 180.0))
            return result.takeIf { it.south < it.north && it.west < it.east }
        }
    }
}

/** Monotonic-clock request budget shared across style changes and camera movement. */
class TrafficRefreshGate {
    private var nextAllowedMs = 0L
    private var failures = 0
    fun tryBegin(nowMs: Long): Boolean {
        if (nowMs < nextAllowedMs) return false
        nextAllowedMs = nowMs + 120_000L
        return true
    }
    fun succeeded() { failures = 0 }
    fun failed(nowMs: Long, kind: TrafficFailure, retryAfterSeconds: Long? = null) {
        failures = (failures + 1).coerceAtMost(5)
        val backoff = when (kind) {
            TrafficFailure.AUTHENTICATION -> 3_600_000L
            TrafficFailure.RATE_LIMIT -> 600_000L
            else -> (120_000L shl (failures - 1)).coerceAtMost(900_000L)
        }
        nextAllowedMs = maxOf(nextAllowedMs, nowMs + maxOf(backoff,
            (retryAfterSeconds ?: 0).coerceIn(0, 3600) * 1000L))
    }
}

enum class TrafficStatus { DISABLED, KEY_REQUIRED, PAUSED, WAITING_ZOOM, WAITING_REFRESH, LOADING, AVAILABLE, OFFLINE, AUTHENTICATION, RATE_LIMIT, UNAVAILABLE }

data class TrafficOverlayStatus(
    val state: TrafficStatus = TrafficStatus.DISABLED,
    val lastUpdatedAtMs: Long? = null,
    val incidentCount: Int = 0,
    val incidents: List<TrafficIncident> = emptyList(),
) {
    fun displayText(nowMs: Long = System.currentTimeMillis()): String {
        val message = when (state) {
            TrafficStatus.DISABLED -> "Trânsito desligado"
            TrafficStatus.KEY_REQUIRED -> "Configure a chave de trânsito"
            TrafficStatus.PAUSED -> "Trânsito pausado"
            TrafficStatus.WAITING_ZOOM -> "Aproxime para consultar incidentes"
            TrafficStatus.WAITING_REFRESH -> "Aguardando próxima consulta"
            TrafficStatus.LOADING -> "Consultando incidentes…"
            TrafficStatus.AVAILABLE -> if (incidentCount == 0) "Nenhum incidente recebido" else "$incidentCount ocorrências na área"
            TrafficStatus.OFFLINE -> "Trânsito sem conexão"
            TrafficStatus.AUTHENTICATION -> "Verifique a chave TomTom"
            TrafficStatus.RATE_LIMIT -> "Limite de trânsito atingido"
            TrafficStatus.UNAVAILABLE -> "Trânsito indisponível"
        }
        val age = lastUpdatedAtMs?.let { " · consulta há ${((nowMs - it).coerceAtLeast(0) / 60_000)} min" }.orEmpty()
        return message + age + if (state == TrafficStatus.DISABLED || state == TrafficStatus.KEY_REQUIRED) "" else " · © TomTom"
    }
}
