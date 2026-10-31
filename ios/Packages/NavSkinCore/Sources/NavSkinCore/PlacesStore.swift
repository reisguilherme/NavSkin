import Foundation
import CoreFoundation

public struct SavedPlace: Codable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var point: GeoPoint
    public var address: String
    public var categoryId: String?
    public var iconId: String
    public var createdAtMs: Int64
    public var updatedAtMs: Int64
    public init(id: String, name: String, point: GeoPoint, address: String = "", categoryId: String? = nil,
                iconId: String = "special", createdAtMs: Int64, updatedAtMs: Int64) {
        self.id = id; self.name = name; self.point = point; self.address = address
        self.categoryId = categoryId; self.iconId = iconId
        self.createdAtMs = createdAtMs; self.updatedAtMs = updatedAtMs
    }
    public func asDestination() -> Destination { Destination(id: id, name: name, point: point, address: address) }
}

public struct RecentDestination: Codable, Equatable, Sendable {
    public var destination: Destination
    public var usedAtMs: Int64
    public init(destination: Destination, usedAtMs: Int64) { self.destination = destination; self.usedAtMs = usedAtMs }
}

public struct LocalPlacesState: Codable, Equatable, Sendable {
    public var savedPlaces: [SavedPlace]
    public var recents: [RecentDestination]
    public var homeId: String?
    public var workId: String?
    public var resumeDestination: Destination?
    public var resumeIntermediateStops: [Destination]
    public var loadError: String?
    public init(savedPlaces: [SavedPlace] = [], recents: [RecentDestination] = [], homeId: String? = nil,
                workId: String? = nil, resumeDestination: Destination? = nil, loadError: String? = nil,
                resumeIntermediateStops: [Destination] = []) {
        self.savedPlaces = savedPlaces; self.recents = recents
        self.homeId = homeId; self.workId = workId
        self.resumeDestination = resumeDestination; self.loadError = loadError
        self.resumeIntermediateStops = resumeIntermediateStops
    }
}

public struct LocalPlacesStorageException: Error, LocalizedError, Equatable, Sendable {
    public let message: String
    public init(_ message: String) { self.message = message }
    public var errorDescription: String? { message }
}

/// Synchronous persistence, confined by its caller (the app uses the main actor).
/// Only committed data is published; corrupt sources are kept untouched and block every mutation.
public final class PlacesStore {
    public static let schemaVersion = 1
    public static let maxRecents = 20
    private let fileURL: URL
    private let now: () -> Int64
    public private(set) var snapshot: LocalPlacesState

    public init(fileURL: URL, now: @escaping () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.fileURL = fileURL
        self.now = now
        self.snapshot = Self.readState(fileURL)
    }

    @discardableResult
    public func savePlace(name: String, point: GeoPoint, address: String = "", categoryId: String? = nil,
                          iconId: String = "special", id: String? = nil) throws -> SavedPlace {
        guard !trim(name).isEmpty else { throw LocalPlacesStorageException("Informe um nome para o local") }
        try Self.validatePoint(point)
        guard !trim(iconId).isEmpty else { throw LocalPlacesStorageException("Escolha um ícone para o local") }
        try ensureWritable()
        let previous = id.flatMap { requested in snapshot.savedPlaces.first { $0.id == requested } }
        guard id == nil || previous != nil else { throw LocalPlacesStorageException("O local a editar não existe") }
        let time = max(now(), previous?.updatedAtMs ?? 0)
        let saved = SavedPlace(id: previous?.id ?? UUID().uuidString, name: trim(name), point: point, address: trim(address),
            categoryId: categoryId.flatMap { trim($0).isEmpty ? nil : $0 }, iconId: iconId,
            createdAtMs: previous?.createdAtMs ?? time, updatedAtMs: time)
        var candidate = snapshot
        if previous == nil { candidate.savedPlaces.append(saved) }
        else { candidate.savedPlaces = candidate.savedPlaces.map { $0.id == saved.id ? saved : $0 } }
        try commit(candidate)
        return saved
    }

    public func deletePlace(id: String) throws {
        try ensureWritable()
        var candidate = snapshot
        candidate.savedPlaces.removeAll { $0.id == id }
        if candidate.homeId == id { candidate.homeId = nil }
        if candidate.workId == id { candidate.workId = nil }
        try commit(candidate)
    }

    public func setHome(id: String?) throws {
        try ensureWritable()
        guard id == nil || snapshot.savedPlaces.contains(where: { $0.id == id }) else { throw LocalPlacesStorageException("Escolha um local salvo para Casa") }
        var candidate = snapshot
        candidate.homeId = id
        try commit(candidate)
    }

    public func setWork(id: String?) throws {
        try ensureWritable()
        guard id == nil || snapshot.savedPlaces.contains(where: { $0.id == id }) else { throw LocalPlacesStorageException("Escolha um local salvo para Trabalho") }
        var candidate = snapshot
        candidate.workId = id
        try commit(candidate)
    }

    public func rememberRecent(_ destination: Destination) throws {
        try Self.validateDestination(destination)
        try ensureWritable()
        var candidate = snapshot
        candidate.recents.removeAll { item in
            item.destination.id == destination.id ||
                (trim(item.destination.name).lowercased() == trim(destination.name).lowercased() &&
                 Self.distanceMeters(item.destination.point, destination.point) <= 40)
        }
        candidate.recents.insert(RecentDestination(destination: destination, usedAtMs: max(0, now())), at: 0)
        candidate.recents = Array(candidate.recents.prefix(Self.maxRecents))
        try commit(candidate)
    }

    public func setResumeDestination(_ destination: Destination?, intermediateStops: [Destination] = []) throws {
        if let destination { try Self.validateDestination(destination) }
        try Self.validateItinerary(destination: destination, stops: intermediateStops)
        try ensureWritable()
        var candidate = snapshot
        candidate.resumeDestination = destination
        candidate.resumeIntermediateStops = intermediateStops
        try commit(candidate)
    }

    private func ensureWritable() throws {
        if let error = snapshot.loadError { throw LocalPlacesStorageException(error) }
    }

    private func commit(_ candidate: LocalPlacesState) throws {
        try ensureWritable()
        // An explicit envelope prevents guidance, GPS history, or loadError from entering storage.
        let source: [String: Any] = [
            "schemaVersion": Self.schemaVersion,
            "savedPlaces": candidate.savedPlaces.map { place -> [String: Any] in
                ["id": place.id, "name": place.name, "point": Self.pointJSON(place.point), "address": place.address,
                 "categoryId": place.categoryId.map { $0 as Any } ?? NSNull(), "iconId": place.iconId,
                 "createdAtMs": place.createdAtMs, "updatedAtMs": place.updatedAtMs]
            },
            "recents": candidate.recents.map { ["destination": Self.destinationJSON($0.destination), "usedAtMs": $0.usedAtMs] as [String: Any] },
            "homeId": candidate.homeId.map { $0 as Any } ?? NSNull(),
            "workId": candidate.workId.map { $0 as Any } ?? NSNull(),
            "resumeDestination": candidate.resumeDestination.map { Self.destinationJSON($0) as Any } ?? NSNull(),
            "resumeIntermediateStops": candidate.resumeIntermediateStops.map(Self.destinationJSON)
        ]
        do {
            let data = try JSONSerialization.data(withJSONObject: source, options: [.sortedKeys])
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: fileURL, options: .atomic)
            guard try Data(contentsOf: fileURL) == data else { throw LocalPlacesStorageException("Atomic commit verification failed") }
            snapshot = candidate
        } catch {
            throw LocalPlacesStorageException("Não foi possível salvar os locais neste dispositivo")
        }
    }

    private static func readState(_ fileURL: URL) -> LocalPlacesState {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return LocalPlacesState() }
        do {
            let source = try object(JSONSerialization.jsonObject(with: Data(contentsOf: fileURL)))
            let version = try integer(source["schemaVersion"])
            guard (0...Int64(schemaVersion)).contains(version) else { throw invalid() }
            let places = try array(source["savedPlaces"]).map { value -> SavedPlace in
                let item = try object(value)
                let point: GeoPoint
                if version == 0, item["point"] == nil {
                    point = GeoPoint(latitude: try number(item["latitude"]), longitude: try number(item["longitude"]))
                } else { point = try parsePoint(item["point"]) }
                try validatePoint(point)
                let created: Int64
                if version == 0, item["createdAtMs"] == nil { created = 0 }
                else { created = try integer(item["createdAtMs"]) }
                let updated: Int64
                if version == 0, item["updatedAtMs"] == nil { updated = created }
                else { updated = try integer(item["updatedAtMs"]) }
                guard created >= 0, updated >= created else { throw invalid() }
                let id = try requiredString(item["id"])
                let name = try requiredString(item["name"])
                let icon: String
                if version == 0, item["iconId"] == nil { icon = "special" }
                else { icon = try requiredString(item["iconId"]) }
                return SavedPlace(id: id, name: name, point: point, address: try optionalString(item["address"]) ?? "",
                    categoryId: try optionalString(item["categoryId"]), iconId: icon, createdAtMs: created, updatedAtMs: updated)
            }
            guard Set(places.map(\.id)).count == places.count else { throw invalid() }
            var recents: [RecentDestination] = []
            if let raw = source["recents"], !(raw is NSNull) {
                // Validate the whole source before applying the in-memory limit.
                recents = try array(raw).map { value in
                    let item = try object(value)
                    let usedAt = try integer(item["usedAtMs"])
                    guard usedAt >= 0 else { throw invalid() }
                    return RecentDestination(destination: try parseDestination(item["destination"]), usedAtMs: usedAt)
                }
            }
            let homeId = try optionalString(source["homeId"])
            let workId = try optionalString(source["workId"])
            guard homeId == nil || places.contains(where: { $0.id == homeId }),
                  workId == nil || places.contains(where: { $0.id == workId }) else { throw invalid() }
            let resume: Destination?
            if let raw = source["resumeDestination"], !(raw is NSNull) { resume = try parseDestination(raw) }
            else { resume = nil }
            let stops = try source["resumeIntermediateStops"].map { try array($0).map(parseDestination) } ?? []
            try validateItinerary(destination: resume, stops: stops)
            return LocalPlacesState(savedPlaces: places, recents: Array(recents.prefix(maxRecents)), homeId: homeId, workId: workId, resumeDestination: resume,
                resumeIntermediateStops: stops)
        } catch {
            return LocalPlacesState(loadError: "Não foi possível ler os locais salvos. O arquivo original foi preservado; alterações estão bloqueadas até sua recuperação.")
        }
    }

    private static func parseDestination(_ value: Any?) throws -> Destination {
        let item = try object(value)
        let destination = Destination(id: try requiredString(item["id"]), name: try requiredString(item["name"]),
            point: try parsePoint(item["point"]), address: try optionalString(item["address"]) ?? "")
        try validateDestination(destination)
        return destination
    }
    private static func parsePoint(_ value: Any?) throws -> GeoPoint {
        let item = try object(value)
        let point = GeoPoint(latitude: try number(item["latitude"]), longitude: try number(item["longitude"]))
        try validatePoint(point)
        return point
    }
    private static func validateDestination(_ destination: Destination) throws {
        guard !trim(destination.id).isEmpty, !trim(destination.name).isEmpty else { throw LocalPlacesStorageException("Destino inválido") }
        try validatePoint(destination.point)
    }
    private static func validateItinerary(destination: Destination?, stops: [Destination]) throws {
        guard stops.count <= 8, destination != nil || stops.isEmpty else { throw invalid() }
        let all = stops + (destination.map { [$0] } ?? [])
        for (index, stop) in all.enumerated() {
            try validateDestination(stop)
            guard !all.prefix(index).contains(where: { $0.id == stop.id || distanceMeters($0.point, stop.point) <= 10 }) else { throw invalid() }
        }
    }
    private static func validatePoint(_ point: GeoPoint) throws {
        guard point.latitude.isFinite, point.longitude.isFinite,
              (-90...90).contains(point.latitude), (-180...180).contains(point.longitude) else { throw LocalPlacesStorageException("Coordenadas inválidas") }
    }
    private static func pointJSON(_ point: GeoPoint) -> [String: Any] { ["latitude": point.latitude, "longitude": point.longitude] }
    private static func destinationJSON(_ destination: Destination) -> [String: Any] {
        ["id": destination.id, "name": destination.name, "point": pointJSON(destination.point), "address": destination.address]
    }
    private static func object(_ value: Any?) throws -> [String: Any] { guard let value = value as? [String: Any] else { throw invalid() }; return value }
    private static func array(_ value: Any?) throws -> [Any] { guard let value = value as? [Any] else { throw invalid() }; return value }
    private static func optionalString(_ value: Any?) throws -> String? {
        guard let value, !(value is NSNull) else { return nil }
        guard let value = value as? String else { throw invalid() }
        return value
    }
    private static func requiredString(_ value: Any?) throws -> String {
        guard let value = try optionalString(value), !trim(value).isEmpty else { throw invalid() }
        return value
    }
    private static func number(_ value: Any?) throws -> Double {
        guard let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID(), value.doubleValue.isFinite else { throw invalid() }
        return value.doubleValue
    }
    private static func integer(_ value: Any?) throws -> Int64 {
        guard let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID(), value.doubleValue.isFinite,
              value.doubleValue.rounded(.towardZero) == value.doubleValue,
              value.compare(NSNumber(value: Int64.max)) != .orderedDescending,
              value.compare(NSNumber(value: Int64.min)) != .orderedAscending else { throw invalid() }
        return value.int64Value
    }
    private static func invalid() -> LocalPlacesStorageException { LocalPlacesStorageException("Invalid saved-place data") }
    private static func distanceMeters(_ a: GeoPoint, _ b: GeoPoint) -> Double {
        let latitude = (b.latitude - a.latitude) * .pi / 180
        let longitude = (b.longitude - a.longitude) * .pi / 180
        let h = sin(latitude / 2) * sin(latitude / 2) + cos(a.latitude * .pi / 180) * cos(b.latitude * .pi / 180) * sin(longitude / 2) * sin(longitude / 2)
        return 6_371_000 * 2 * asin(sqrt(min(1, max(0, h))))
    }
}

private func trim(_ value: String) -> String { value.trimmingCharacters(in: .whitespacesAndNewlines) }
