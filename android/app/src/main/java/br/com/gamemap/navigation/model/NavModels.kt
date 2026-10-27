package br.com.gamemap.navigation.model

data class GeoPoint(val latitude: Double, val longitude: Double)

data class Destination(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val address: String = "",
)

data class LocationFix(
    val point: GeoPoint,
    val accuracyMeters: Double,
    val bearingDegrees: Double? = null,
    val speedMps: Double? = null,
    val speedAccuracyMps: Double? = null,
    val elapsedRealtimeMs: Long,
    val wallTimeMs: Long,
    val isMock: Boolean = false,
)

enum class LocationQuality { UNAVAILABLE, GOOD, WEAK, LOST }

data class LocationState(
    val fix: LocationFix? = null,
    val quality: LocationQuality = LocationQuality.UNAVAILABLE,
    val speedKmh: Double? = null,
    val bearingDegrees: Double? = null,
    val ageMs: Long = 0,
)

enum class ManeuverKind {
    DEPART, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,
    UTURN, ROUNDABOUT, ARRIVE, MERGE, FORK, EXIT, UNKNOWN,
}

/** Geometry indices are inclusive; an instruction occurs at the step's start index. */
data class RouteStep(
    val id: Int,
    val instruction: String,
    val streetName: String,
    val kind: ManeuverKind,
    val startIndex: Int,
    val endIndex: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val exitNumber: Int? = null,
)

data class NavRoute(
    val id: String,
    val geometry: List<GeoPoint>,
    val steps: List<RouteStep>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val destination: Destination,
    val calculatedAtMs: Long,
)

data class RouteProgress(
    val routeId: String,
    val alongRouteMeters: Double,
    val projectedPoint: GeoPoint,
    val segmentIndex: Int,
    val stepIndex: Int,
    val distanceToManeuverMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingSeconds: Double,
    val arrivalEpochMs: Long,
    val distanceFromRouteMeters: Double,
    val onRoute: Boolean,
    val arrived: Boolean,
)
