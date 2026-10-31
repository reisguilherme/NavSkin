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
    public var intermediateStops: [Destination]
    /// Geometry vertex at the end of each intermediate leg, in itinerary order.
    public var intermediateStopIndices: [Int]

    public init(id: String, geometry: [GeoPoint], steps: [RouteStep], distanceMeters: Double,
                durationSeconds: Double, destination: Destination, calculatedAtMs: Int64,
                intermediateStops: [Destination] = [], intermediateStopIndices: [Int] = []) {
        self.id = id
        self.geometry = geometry
        self.steps = steps
        self.distanceMeters = distanceMeters
        self.durationSeconds = durationSeconds
        self.destination = destination
        self.calculatedAtMs = calculatedAtMs
        self.intermediateStops = intermediateStops
        self.intermediateStopIndices = intermediateStopIndices
    }

    private enum CodingKeys: String, CodingKey {
        case id, geometry, steps, distanceMeters, durationSeconds, destination, calculatedAtMs
        case intermediateStops, intermediateStopIndices
    }
    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(id: try values.decode(String.self, forKey: .id), geometry: try values.decode([GeoPoint].self, forKey: .geometry),
            steps: try values.decode([RouteStep].self, forKey: .steps), distanceMeters: try values.decode(Double.self, forKey: .distanceMeters),
            durationSeconds: try values.decode(Double.self, forKey: .durationSeconds), destination: try values.decode(Destination.self, forKey: .destination),
            calculatedAtMs: try values.decode(Int64.self, forKey: .calculatedAtMs),
            intermediateStops: try values.decodeIfPresent([Destination].self, forKey: .intermediateStops) ?? [],
            intermediateStopIndices: try values.decodeIfPresent([Int].self, forKey: .intermediateStopIndices) ?? [])
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
    public var vehicleSelectionExplicit: Bool
    public var savedPlacesVisible: Bool
    public var orientation: MapOrientation
    public var browsing3D: Bool
    public var navigation3D: Bool

    public init(vehicleId: String = "arrow", savedPlacesVisible: Bool = true,
                orientation: MapOrientation = .movementUp, browsing3D: Bool = false, navigation3D: Bool = true,
                vehicleSelectionExplicit: Bool = false) {
        self.vehicleId = vehicleId
        self.vehicleSelectionExplicit = vehicleSelectionExplicit || vehicleId != "arrow"
        self.savedPlacesVisible = savedPlacesVisible
        self.orientation = orientation
        self.browsing3D = browsing3D
        self.navigation3D = navigation3D
    }

    private enum CodingKeys: String, CodingKey {
        case vehicleId, vehicleSelectionExplicit, savedPlacesVisible, orientation, browsing3D, navigation3D
    }

    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(vehicleId: try values.decode(String.self, forKey: .vehicleId),
                  savedPlacesVisible: try values.decode(Bool.self, forKey: .savedPlacesVisible),
                  orientation: try values.decode(MapOrientation.self, forKey: .orientation),
                  browsing3D: try values.decodeIfPresent(Bool.self, forKey: .browsing3D) ?? false,
                  navigation3D: try values.decodeIfPresent(Bool.self, forKey: .navigation3D) ?? true,
                  // Legacy preferences cannot distinguish a chosen arrow from the old default.
                  // Preserve that stored choice; only a new, unchosen default adapts to its theme.
                  vehicleSelectionExplicit: try values.decodeIfPresent(Bool.self, forKey: .vehicleSelectionExplicit) ?? true)
    }

    public func vehicleID(theme: String) -> String {
        theme == "rosa" && !vehicleSelectionExplicit ? "themed" : vehicleId
    }

    public func map3D(navigationActive: Bool) -> Bool {
        navigationActive ? navigation3D : browsing3D
    }

    public mutating func setMap3D(_ enabled: Bool, navigationActive: Bool) {
        if navigationActive { navigation3D = enabled }
        else { browsing3D = enabled }
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
    public var intermediateStops: [Destination]
    public var pendingIntermediateStops: [Destination]?
    public var resumeIntermediateStops: [Destination]
    public var updatingStops: Bool

    public init(phase: NavigationPhase = .idle, location: LocationState = LocationState(),
                locationAccess: LocationAccess = .notRequested, destination: Destination? = nil,
                route: NavRoute? = nil, progress: RouteProgress? = nil, message: String? = nil,
                searching: Bool = false, searchResults: [Destination] = [], searchError: String? = nil,
                providerConfigured: Bool = false, serviceRunning: Bool = false, simulation: Bool = false,
                sessionId: Int64 = 0, resumeDestination: Destination? = nil,
                intermediateStops: [Destination] = [], pendingIntermediateStops: [Destination]? = nil,
                resumeIntermediateStops: [Destination] = [], updatingStops: Bool = false) {
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
        self.intermediateStops = intermediateStops
        self.pendingIntermediateStops = pendingIntermediateStops
        self.resumeIntermediateStops = resumeIntermediateStops
        self.updatingStops = updatingStops
    }

    private enum CodingKeys: String, CodingKey {
        case phase, location, locationAccess, destination, route, progress, message, searching, searchResults, searchError
        case providerConfigured, serviceRunning, simulation, sessionId, resumeDestination
        case intermediateStops, pendingIntermediateStops, resumeIntermediateStops, updatingStops
    }
    public init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        self.init(phase: try values.decode(NavigationPhase.self, forKey: .phase), location: try values.decode(LocationState.self, forKey: .location),
            locationAccess: try values.decode(LocationAccess.self, forKey: .locationAccess),
            destination: try values.decodeIfPresent(Destination.self, forKey: .destination), route: try values.decodeIfPresent(NavRoute.self, forKey: .route),
            progress: try values.decodeIfPresent(RouteProgress.self, forKey: .progress), message: try values.decodeIfPresent(String.self, forKey: .message),
            searching: try values.decode(Bool.self, forKey: .searching), searchResults: try values.decode([Destination].self, forKey: .searchResults),
            searchError: try values.decodeIfPresent(String.self, forKey: .searchError), providerConfigured: try values.decode(Bool.self, forKey: .providerConfigured),
            serviceRunning: try values.decode(Bool.self, forKey: .serviceRunning), simulation: try values.decode(Bool.self, forKey: .simulation),
            sessionId: try values.decode(Int64.self, forKey: .sessionId), resumeDestination: try values.decodeIfPresent(Destination.self, forKey: .resumeDestination),
            intermediateStops: try values.decodeIfPresent([Destination].self, forKey: .intermediateStops) ?? [],
            pendingIntermediateStops: try values.decodeIfPresent([Destination].self, forKey: .pendingIntermediateStops),
            resumeIntermediateStops: try values.decodeIfPresent([Destination].self, forKey: .resumeIntermediateStops) ?? [],
            updatingStops: try values.decodeIfPresent(Bool.self, forKey: .updatingStops) ?? false)
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
