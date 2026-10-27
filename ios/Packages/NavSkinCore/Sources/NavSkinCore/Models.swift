import Foundation

public struct GeoPoint: Codable, Equatable, Sendable {
    public var latitude: Double
    public var longitude: Double

    public init(latitude: Double, longitude: Double) {
        self.latitude = latitude
        self.longitude = longitude
    }
}

public struct Destination: Codable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var point: GeoPoint
    public var address: String

    public init(id: String, name: String, point: GeoPoint, address: String = "") {
        self.id = id
        self.name = name
        self.point = point
        self.address = address
    }
}

public struct LocationFix: Codable, Equatable, Sendable {
    public var point: GeoPoint
    public var accuracyMeters: Double
    public var bearingDegrees: Double?
    public var speedMps: Double?
    public var speedAccuracyMps: Double?
    public var elapsedRealtimeMs: Int64
    public var wallTimeMs: Int64
    public var isMock: Bool

    public init(point: GeoPoint, accuracyMeters: Double, bearingDegrees: Double? = nil,
                speedMps: Double? = nil, speedAccuracyMps: Double? = nil,
                elapsedRealtimeMs: Int64, wallTimeMs: Int64, isMock: Bool = false) {
        self.point = point
        self.accuracyMeters = accuracyMeters
        self.bearingDegrees = bearingDegrees
        self.speedMps = speedMps
        self.speedAccuracyMps = speedAccuracyMps
        self.elapsedRealtimeMs = elapsedRealtimeMs
        self.wallTimeMs = wallTimeMs
        self.isMock = isMock
    }
}

public enum LocationQuality: String, Codable, Equatable, Sendable, CaseIterable {
    case unavailable, good, weak, lost
}

public struct LocationState: Codable, Equatable, Sendable {
    public var fix: LocationFix?
    public var quality: LocationQuality
    public var speedKmh: Double?
    public var bearingDegrees: Double?
    public var ageMs: Int64

    public init(fix: LocationFix? = nil, quality: LocationQuality = .unavailable,
                speedKmh: Double? = nil, bearingDegrees: Double? = nil, ageMs: Int64 = 0) {
        self.fix = fix
        self.quality = quality
        self.speedKmh = speedKmh
        self.bearingDegrees = bearingDegrees
        self.ageMs = ageMs
    }
}

public enum ManeuverKind: String, Codable, Equatable, Sendable, CaseIterable {
    case depart, straight, slightLeft, left, sharpLeft, slightRight, right, sharpRight
    case uturn, roundabout, arrive, merge, fork, exit, unknown
}

/// Geometry indices are inclusive; the instruction occurs at its step's start index.
public struct RouteStep: Codable, Equatable, Sendable {
    public var id: Int
    public var instruction: String
    public var streetName: String
    public var kind: ManeuverKind
    public var startIndex: Int
    public var endIndex: Int
    public var distanceMeters: Double
    public var durationSeconds: Double
    public var exitNumber: Int?

    public init(id: Int, instruction: String, streetName: String, kind: ManeuverKind,
                startIndex: Int, endIndex: Int, distanceMeters: Double, durationSeconds: Double,
                exitNumber: Int? = nil) {
        self.id = id
        self.instruction = instruction
        self.streetName = streetName
        self.kind = kind
        self.startIndex = startIndex
        self.endIndex = endIndex
        self.distanceMeters = distanceMeters
        self.durationSeconds = durationSeconds
        self.exitNumber = exitNumber
    }
}

public struct NavRoute: Codable, Equatable, Sendable {
    public var id: String
    public var geometry: [GeoPoint]
    public var steps: [RouteStep]
    public var distanceMeters: Double
    public var durationSeconds: Double
    public var destination: Destination
    public var calculatedAtMs: Int64

    public init(id: String, geometry: [GeoPoint], steps: [RouteStep], distanceMeters: Double,
                durationSeconds: Double, destination: Destination, calculatedAtMs: Int64) {
        self.id = id
        self.geometry = geometry
        self.steps = steps
        self.distanceMeters = distanceMeters
        self.durationSeconds = durationSeconds
        self.destination = destination
        self.calculatedAtMs = calculatedAtMs
    }
}

public struct RouteProgress: Codable, Equatable, Sendable {
    public var routeId: String
    public var alongRouteMeters: Double
    public var projectedPoint: GeoPoint
    public var segmentIndex: Int
    public var stepIndex: Int
    public var distanceToManeuverMeters: Double
    public var remainingDistanceMeters: Double
    public var remainingSeconds: Double
    public var arrivalEpochMs: Int64
    public var distanceFromRouteMeters: Double
    public var onRoute: Bool
    public var arrived: Bool

    public init(routeId: String, alongRouteMeters: Double, projectedPoint: GeoPoint,
                segmentIndex: Int, stepIndex: Int, distanceToManeuverMeters: Double,
                remainingDistanceMeters: Double, remainingSeconds: Double, arrivalEpochMs: Int64,
                distanceFromRouteMeters: Double, onRoute: Bool, arrived: Bool) {
        self.routeId = routeId
        self.alongRouteMeters = alongRouteMeters
        self.projectedPoint = projectedPoint
        self.segmentIndex = segmentIndex
        self.stepIndex = stepIndex
        self.distanceToManeuverMeters = distanceToManeuverMeters
        self.remainingDistanceMeters = remainingDistanceMeters
        self.remainingSeconds = remainingSeconds
        self.arrivalEpochMs = arrivalEpochMs
        self.distanceFromRouteMeters = distanceFromRouteMeters
        self.onRoute = onRoute
        self.arrived = arrived
    }
}

public enum NavigationPhase: String, Codable, Equatable, Sendable, CaseIterable {
    case idle, waitingForLocation, calculating, preview, navigating, recalculating, rerouteFailed
    case arrived, error, resumeAvailable
}

public enum LocationAccess: String, Codable, Equatable, Sendable, CaseIterable {
    case notRequested, denied, disabled, waiting, available, weak, lost, needsPhoneStart
}

public enum MapOrientation: String, Codable, Equatable, Sendable, CaseIterable {
    case northUp, movementUp
}

public struct NavigationPreferences: Codable, Equatable, Sendable {
    public var vehicleId: String
    public var savedPlacesVisible: Bool
    public var orientation: MapOrientation

    public init(vehicleId: String = "arrow", savedPlacesVisible: Bool = true,
                orientation: MapOrientation = .movementUp) {
        self.vehicleId = vehicleId
        self.savedPlacesVisible = savedPlacesVisible
        self.orientation = orientation
    }
}

public struct NavigationSnapshot: Codable, Equatable, Sendable {
    public var phase: NavigationPhase
    public var location: LocationState
    public var locationAccess: LocationAccess
    public var destination: Destination?
    public var route: NavRoute?
    public var progress: RouteProgress?
    public var message: String?
    public var searching: Bool
    public var searchResults: [Destination]
    public var searchError: String?
    public var providerConfigured: Bool
    public var serviceRunning: Bool
    public var simulation: Bool
    public var sessionId: Int64
    public var resumeDestination: Destination?

    public init(phase: NavigationPhase = .idle, location: LocationState = LocationState(),
                locationAccess: LocationAccess = .notRequested, destination: Destination? = nil,
                route: NavRoute? = nil, progress: RouteProgress? = nil, message: String? = nil,
                searching: Bool = false, searchResults: [Destination] = [], searchError: String? = nil,
                providerConfigured: Bool = false, serviceRunning: Bool = false, simulation: Bool = false,
                sessionId: Int64 = 0, resumeDestination: Destination? = nil) {
        self.phase = phase
        self.location = location
        self.locationAccess = locationAccess
        self.destination = destination
        self.route = route
        self.progress = progress
        self.message = message
        self.searching = searching
        self.searchResults = searchResults
        self.searchError = searchError
        self.providerConfigured = providerConfigured
        self.serviceRunning = serviceRunning
        self.simulation = simulation
        self.sessionId = sessionId
        self.resumeDestination = resumeDestination
    }

    public var navigationActive: Bool {
        phase == .navigating || phase == .recalculating || phase == .rerouteFailed
    }

    public var guidanceAvailable: Bool {
        phase == .navigating && progress?.onRoute == true
    }
}

public struct EngineUpdate: Codable, Equatable, Sendable {
    public var location: LocationState
    public var progress: RouteProgress?
    public var shouldRecalculate: Bool
    public var arrived: Bool

    public init(location: LocationState, progress: RouteProgress?,
                shouldRecalculate: Bool = false, arrived: Bool = false) {
        self.location = location
        self.progress = progress
        self.shouldRecalculate = shouldRecalculate
        self.arrived = arrived
    }
}
