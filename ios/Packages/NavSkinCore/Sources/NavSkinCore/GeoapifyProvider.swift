import Foundation
import CoreFoundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public enum ProviderErrorKind: String, Codable, Equatable, Sendable {
    case configuration, authentication, rateLimit, offline, noResults, noRoute, unavailable, invalidResponse
}

/// Safe to display or log: it never retains the authenticated URL, response body, or underlying error.
public struct TypedProviderException: Error, LocalizedError, CustomStringConvertible, Equatable, Sendable {
    public let kind: ProviderErrorKind
    public let retryAfterSeconds: Int64?
    public init(kind: ProviderErrorKind, retryAfterSeconds: Int64? = nil) {
        self.kind = kind
        self.retryAfterSeconds = retryAfterSeconds
    }
    public var errorDescription: String? {
        switch kind {
        case .configuration: return "Configure sua chave Geoapify no celular."
        case .authentication: return "Chave Geoapify inválida ou sem acesso. Confira a configuração."
        case .rateLimit: return "Limite de consultas atingido. Aguarde antes de tentar novamente."
        case .offline: return "Sem conexão com o serviço. Verifique a internet."
        case .noResults: return "Nenhum local encontrado. Tente outro nome ou endereço."
        case .noRoute: return "Não foi possível encontrar uma rota para este destino."
        case .unavailable: return "Serviço temporariamente indisponível. Tente novamente."
        case .invalidResponse: return "O serviço retornou dados de navegação inválidos. Tente novamente."
        }
    }
    public var description: String { errorDescription ?? kind.rawValue }
}

public struct HTTPResponse: Sendable {
    public let statusCode: Int
    public let body: Data
    public let headers: [String: String]
    public init(statusCode: Int, body: Data, headers: [String: String] = [:]) {
        self.statusCode = statusCode
        self.body = body
        self.headers = headers
    }
}

public protocol HTTPTransport: Sendable {
    func get(_ url: URL) async throws -> HTTPResponse
}

public protocol NavigationProviding: Sendable {
    func search(query: String, bias: GeoPoint?) async throws -> [Destination]
    func route(origin: GeoPoint, destination: Destination) async throws -> NavRoute
}

/// Each request has an ephemeral session, a bounded streaming buffer, and no redirect or disk cache.
public struct URLSessionTransport: HTTPTransport {
    public init() {}
    public func get(_ url: URL) async throws -> HTTPResponse {
        guard url.scheme == "https", url.host == "api.geoapify.com",
              url.user == nil, url.password == nil, url.port == nil || url.port == 443 else {
            throw TypedProviderException(kind: .configuration)
        }
        let connection = BoundedConnection()
        return try await withTaskCancellationHandler(operation: {
            try await withCheckedThrowingContinuation { continuation in
                connection.start(url: url, continuation: continuation)
            }
        }, onCancel: { connection.cancel() })
    }
}

private let maximumGeoapifyBodyBytes = 4 * 1024 * 1024

/// URLSession invokes delegate callbacks serially; the lock also protects cancellation from other tasks.
private final class BoundedConnection: NSObject, URLSessionDataDelegate, @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<HTTPResponse, Error>?
    private var session: URLSession?
    private var task: URLSessionDataTask?
    private var response: HTTPURLResponse?
    private var body = Data()
    private var failure: TypedProviderException?
    private var cancelled = false

    func start(url: URL, continuation: CheckedContinuation<HTTPResponse, Error>) {
        lock.lock()
        guard !cancelled else {
            lock.unlock()
            continuation.resume(throwing: CancellationError())
            return
        }
        self.continuation = continuation
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 10
        configuration.timeoutIntervalForResource = 25
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        let session = URLSession(configuration: configuration, delegate: self, delegateQueue: nil)
        self.session = session
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("NavSkin-iOS/0.5.0", forHTTPHeaderField: "User-Agent")
        let task = session.dataTask(with: request)
        self.task = task
        lock.unlock()
        task.resume()
    }

    func cancel() {
        lock.lock()
        cancelled = true
        let task = task
        lock.unlock()
        task?.cancel()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        lock.lock()
        guard let http = response as? HTTPURLResponse,
              response.expectedContentLength <= Int64(maximumGeoapifyBodyBytes) else {
            failure = TypedProviderException(kind: .invalidResponse)
            lock.unlock()
            completionHandler(.cancel)
            return
        }
        self.response = http
        lock.unlock()
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        lock.lock()
        if body.count > maximumGeoapifyBodyBytes - data.count {
            failure = TypedProviderException(kind: .invalidResponse)
            lock.unlock()
            dataTask.cancel()
            return
        }
        body.append(data)
        lock.unlock()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let continuation = continuation
        self.continuation = nil
        let result: Result<HTTPResponse, Error>
        if cancelled {
            result = .failure(CancellationError())
        } else if let failure {
            result = .failure(failure)
        } else if let error {
            result = .failure(safeNetworkError(error))
        } else if let response {
            var headers: [String: String] = [:]
            for (name, value) in response.allHeaderFields {
                if let name = name as? String { headers[name] = String(describing: value) }
            }
            result = .success(HTTPResponse(statusCode: response.statusCode, body: body, headers: headers))
        } else {
            result = .failure(TypedProviderException(kind: .invalidResponse))
        }
        self.task = nil
        self.session = nil
        lock.unlock()
        session.finishTasksAndInvalidate()
        continuation?.resume(with: result)
    }
}

private func safeNetworkError(_ error: Error) -> TypedProviderException {
    if let safe = error as? TypedProviderException { return safe }
    if let urlError = error as? URLError {
        switch urlError.code {
        case .notConnectedToInternet, .networkConnectionLost, .cannotFindHost, .cannotConnectToHost,
             .dnsLookupFailed, .internationalRoamingOff, .dataNotAllowed:
            return TypedProviderException(kind: .offline)
        default: break
        }
    }
    return TypedProviderException(kind: .unavailable)
}

public struct GeoapifyProvider: NavigationProviding {
    private let apiKey: String
    private let transport: any HTTPTransport
    private let clock: @Sendable () -> Int64
    public init(apiKey: String, transport: any HTTPTransport = URLSessionTransport(),
                clock: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) }) {
        self.apiKey = apiKey
        self.transport = transport
        self.clock = clock
    }

    public func search(query: String, bias: GeoPoint? = nil) async throws -> [Destination] {
        let key = try validatedKey()
        let text = String(query.trimmingCharacters(in: .whitespacesAndNewlines).prefix(240))
        guard text.count >= 2 else { throw TypedProviderException(kind: .noResults) }
        if let bias { try validatePoint(bias) }
        var params = [("text", text), ("lang", "pt"), ("limit", "8"), ("format", "geojson")]
        if let bias { params.append(("bias", "proximity:\(bias.longitude),\(bias.latitude)")) }
        let addresses = try parsePlaces(await request(path: "/v1/geocode/search", params: params, key: key))
        let places: [Destination]
        if let bias {
            places = try parsePlaces(await request(path: "/v2/places", params: [
                ("categories", "commercial,catering,accommodation,healthcare,leisure,entertainment,sport,tourism"),
                ("name", text), ("lang", "pt"), ("limit", "8"),
                ("bias", "proximity:\(bias.longitude),\(bias.latitude)")], key: key))
        } else { places = [] }
        var ids = Set<String>()
        let combined = Array((addresses + places).filter { ids.insert($0.id).inserted }.prefix(16))
        guard !combined.isEmpty else { throw TypedProviderException(kind: .noResults) }
        return combined
    }

    public func route(origin: GeoPoint, destination: Destination) async throws -> NavRoute {
        let key = try validatedKey()
        try validatePoint(origin)
        try validatePoint(destination.point)
        let json = try await request(path: "/v1/routing", params: [
            ("waypoints", "\(origin.latitude),\(origin.longitude)|\(destination.point.latitude),\(destination.point.longitude)"),
            ("mode", "drive"), ("units", "metric"), ("lang", "pt-BR"),
            ("details", "instruction_details"), ("traffic", "approximated"), ("format", "geojson")], key: key, routing: true)
        return try parseRoute(json, destination: destination)
    }

    public func reverse(point: GeoPoint) async throws -> String? {
        let key = try validatedKey()
        try validatePoint(point)
        let json = try await request(path: "/v1/geocode/reverse", params: [
            ("lat", String(point.latitude)), ("lon", String(point.longitude)),
            ("lang", "pt"), ("limit", "1"), ("format", "geojson")], key: key)
        let address = try parsePlaces(json).first?.address ?? ""
        return address.isEmpty ? nil : address
    }

    private func validatedKey() throws -> String {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, key.count <= 512, !key.contains(where: { $0.isWhitespace }) else {
            throw TypedProviderException(kind: .configuration)
        }
        return key
    }

    private func request(path: String, params: [(String, String)], key: String, routing: Bool = false) async throws -> [String: Any] {
        try Task.checkCancellation()
        var components = URLComponents()
        components.scheme = "https"
        components.host = "api.geoapify.com"
        components.path = path
        components.queryItems = (params + [("apiKey", key)]).map { URLQueryItem(name: $0.0, value: $0.1) }
        // A literal '+' must survive servers that decode query strings as form data.
        components.percentEncodedQuery = components.percentEncodedQuery?.replacingOccurrences(of: "+", with: "%2B")
        guard let url = components.url else { throw TypedProviderException(kind: .configuration) }
        let response: HTTPResponse
        do { response = try await transport.get(url) }
        catch is CancellationError { throw CancellationError() }
        catch { throw safeNetworkError(error) }
        try Task.checkCancellation()
        guard response.body.count <= maximumGeoapifyBodyBytes else { throw invalid() }
        if !(200...299).contains(response.statusCode) {
            let message = String(decoding: response.body.prefix(8192), as: UTF8.self).lowercased()
            let kind: ProviderErrorKind
            if response.statusCode == 429 || (response.statusCode == 403 && ["quota", "credit", "rate limit", "daily limit"].contains(where: message.contains)) {
                kind = .rateLimit
            } else if [401, 403].contains(response.statusCode) {
                kind = .authentication
            } else if routing && [400, 404, 422].contains(response.statusCode) && ["unconnected", "route", "routable", "waypoint", "could not find"].contains(where: message.contains) {
                kind = .noRoute
            } else { kind = .unavailable }
            throw TypedProviderException(kind: kind, retryAfterSeconds: retryAfter(response))
        }
        do {
            let json = try object(JSONSerialization.jsonObject(with: response.body))
            guard json["type"] as? String == "FeatureCollection", json["features"] is [Any] else { throw invalid() }
            return json
        } catch { throw invalid() }
    }

    private func retryAfter(_ response: HTTPResponse) -> Int64? {
        guard let value = response.headers.first(where: { $0.key.caseInsensitiveCompare("Retry-After") == .orderedSame })?.value.trimmingCharacters(in: .whitespacesAndNewlines) else { return nil }
        if let seconds = Int64(value) { return seconds >= 0 ? seconds : nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "EEE, dd MMM yyyy HH:mm:ss zzz"
        guard let date = formatter.date(from: value) else { return nil }
        let interval = (date.timeIntervalSince1970 * 1000 - Double(clock())) / 1000
        guard interval.isFinite, interval < Double(Int64.max) else { return nil }
        return Int64(max(0, interval))
    }

    private func parsePlaces(_ json: [String: Any]) throws -> [Destination] {
        try array(json["features"]).map { value in
            let feature = try object(value)
            let geometry = try object(feature["geometry"])
            guard geometry["type"] as? String == "Point" else { throw invalid() }
            let point = try parsePoint(geometry["coordinates"])
            let properties = try object(feature["properties"])
            var address = text(properties, "formatted")
            if address.isEmpty { address = [text(properties, "address_line1"), text(properties, "address_line2")].filter { !$0.isEmpty }.joined(separator: ", ") }
            var name = text(properties, "name")
            if name.isEmpty { name = text(properties, "address_line1") }
            if name.isEmpty { name = address }
            guard !name.isEmpty else { throw invalid() }
            var id = text(properties, "place_id")
            if id.isEmpty { id = "geoapify:" + nameUUID("\(point.latitude),\(point.longitude):\(name)") }
            return Destination(id: id, name: name, point: point, address: address)
        }
    }

    private func parseRoute(_ json: [String: Any], destination: Destination) throws -> NavRoute {
        let features = try array(json["features"])
        guard let first = features.first else { throw TypedProviderException(kind: .noRoute) }
        let feature = try object(first)
        let geometry = try object(feature["geometry"])
        guard geometry["type"] as? String == "MultiLineString" else { throw invalid() }
        let coordinates = try array(geometry["coordinates"])
        let properties = try object(feature["properties"])
        let legs = try array(properties["legs"])
        guard !legs.isEmpty, legs.count == coordinates.count else { throw invalid() }
        let totalDistance = try nonnegative(properties["distance"])
        let totalTime = try nonnegative(properties["time"])
        guard totalDistance > 0, totalTime > 0 else { throw invalid() }
        var points: [GeoPoint] = []
        var steps: [RouteStep] = []
        for legIndex in legs.indices {
            let legCoordinates = try array(coordinates[legIndex])
            guard legCoordinates.count >= 2 else { throw invalid() }
            var indexMap: [Int] = []
            for index in legCoordinates.indices {
                let point = try parsePoint(legCoordinates[index])
                if index == 0, legIndex > 0, let last = points.last, !samePoint(last, point) { throw invalid() }
                if points.last.map({ samePoint($0, point) }) != true { points.append(point) }
                indexMap.append(points.count - 1)
            }
            let leg = try object(legs[legIndex])
            _ = try nonnegative(leg["distance"])
            _ = try nonnegative(leg["time"])
            let legSteps = try array(leg["steps"])
            guard !legSteps.isEmpty else { throw invalid() }
            var previousEnd = 0
            for value in legSteps {
                let raw = try object(value)
                let from = try index(raw["from_index"])
                let to = try index(raw["to_index"])
                guard indexMap.indices.contains(from), indexMap.indices.contains(to), to >= from, from == previousEnd else { throw invalid() }
                previousEnd = to
                let distance = try nonnegative(raw["distance"])
                let duration = try nonnegative(raw["time"])
                let instruction = try object(raw["instruction"])
                let instructionText = text(instruction, "text")
                guard !instructionText.isEmpty else { throw invalid() }
                let kind = maneuver(text(instruction, "type"))
                if kind == .arrive, legIndex < legs.count - 1 { continue }
                var street = (instruction["streets"] as? [String] ?? []).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }.joined(separator: " / ")
                if street.isEmpty { street = text(raw, "name") }
                var exitNumber: Int?
                if let exit = instruction["roundabout_exit"], !(exit is NSNull) {
                    let value = try index(exit)
                    if value > 0 { exitNumber = value }
                }
                steps.append(RouteStep(id: steps.count, instruction: instructionText, streetName: street, kind: kind,
                    startIndex: indexMap[from], endIndex: indexMap[to], distanceMeters: distance, durationSeconds: duration, exitNumber: exitNumber))
            }
            guard previousEnd == legCoordinates.count - 1 else { throw invalid() }
        }
        guard points.count >= 2, !steps.isEmpty else { throw invalid() }
        guard abs(steps.reduce(0) { $0 + $1.durationSeconds } - totalTime) <= max(1, totalTime * 0.05),
              abs(steps.reduce(0) { $0 + $1.distanceMeters } - totalDistance) <= max(10, totalDistance * 0.05) else { throw invalid() }
        if steps.last?.kind != .arrive || steps.last?.startIndex != points.count - 1 {
            steps.append(RouteStep(id: steps.count, instruction: "Você chegou ao destino.", streetName: "", kind: .arrive,
                startIndex: points.count - 1, endIndex: points.count - 1, distanceMeters: 0, durationSeconds: 0, exitNumber: nil))
        }
        return NavRoute(id: "geoapify:\(UUID().uuidString)", geometry: points, steps: steps, distanceMeters: totalDistance,
            durationSeconds: totalTime, destination: destination, calculatedAtMs: clock())
    }

    private func maneuver(_ type: String) -> ManeuverKind {
        switch type {
        case "StartAt", "StartAtRight", "StartAtLeft": return .depart
        case "DestinationReached", "DestinationReachedRight", "DestinationReachedLeft": return .arrive
        case "Straight": return .straight
        case "SlightLeft": return .slightLeft
        case "Left": return .left
        case "SharpLeft": return .sharpLeft
        case "SlightRight": return .slightRight
        case "Right": return .right
        case "SharpRight": return .sharpRight
        case "TurnAroundLeft", "TurnAroundRight": return .uturn
        case "Roundabout": return .roundabout
        case "Merge", "MergeRight", "MergeLeft": return .merge
        case "StayRight", "StayLeft": return .fork
        case "ExitRight", "ExitLeft": return .exit
        default: return .unknown
        }
    }

    private func parsePoint(_ value: Any?) throws -> GeoPoint {
        let coordinates = try array(value)
        guard coordinates.count >= 2 else { throw invalid() }
        let point = GeoPoint(latitude: try number(coordinates[1]), longitude: try number(coordinates[0]))
        try validatePoint(point)
        return point
    }
    private func validatePoint(_ point: GeoPoint) throws {
        guard point.latitude.isFinite, point.longitude.isFinite,
              (-90...90).contains(point.latitude), (-180...180).contains(point.longitude) else { throw invalid() }
    }
    private func samePoint(_ a: GeoPoint, _ b: GeoPoint) -> Bool { abs(a.latitude - b.latitude) < 1e-8 && abs(a.longitude - b.longitude) < 1e-8 }
    private func object(_ value: Any?) throws -> [String: Any] { guard let value = value as? [String: Any] else { throw invalid() }; return value }
    private func array(_ value: Any?) throws -> [Any] { guard let value = value as? [Any] else { throw invalid() }; return value }
    private func text(_ value: [String: Any], _ name: String) -> String { (value[name] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
    private func number(_ value: Any?) throws -> Double {
        guard let value = value as? NSNumber, CFGetTypeID(value) != CFBooleanGetTypeID(), value.doubleValue.isFinite else { throw invalid() }
        return value.doubleValue
    }
    private func nonnegative(_ value: Any?) throws -> Double { let value = try number(value); guard value >= 0 else { throw invalid() }; return value }
    private func index(_ value: Any?) throws -> Int {
        let value = try nonnegative(value)
        guard value <= 2_147_483_647, value.rounded(.towardZero) == value else { throw invalid() }
        return Int(value)
    }
    private func invalid() -> TypedProviderException { TypedProviderException(kind: .invalidResponse) }
}

/// Java UUID.nameUUIDFromBytes compatibility for place features without place_id.
/// MD5 is used only to derive stable identifiers, never for authentication or integrity.
private func nameUUID(_ value: String) -> String {
    var bytes = Array(value.utf8)
    let bitCount = UInt64(bytes.count) &* 8
    bytes.append(0x80)
    while bytes.count % 64 != 56 { bytes.append(0) }
    bytes += (0..<8).map { UInt8(truncatingIfNeeded: bitCount >> ($0 * 8)) }
    let shifts: [UInt32] = [7,12,17,22,7,12,17,22,7,12,17,22,7,12,17,22,5,9,14,20,5,9,14,20,5,9,14,20,5,9,14,20,4,11,16,23,4,11,16,23,4,11,16,23,4,11,16,23,6,10,15,21,6,10,15,21,6,10,15,21,6,10,15,21]
    let constants: [UInt32] = (0..<64).map { UInt32(abs(sin(Double($0 + 1))) * 4_294_967_296) }
    var state: [UInt32] = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476]
    for block in stride(from: 0, to: bytes.count, by: 64) {
        var words = [UInt32](repeating: 0, count: 16)
        for index in 0..<16 {
            for shift in 0..<4 { words[index] |= UInt32(bytes[block + index * 4 + shift]) << (shift * 8) }
        }
        var a = state[0], b = state[1], c = state[2], d = state[3]
        for index in 0..<64 {
            let f: UInt32
            let g: Int
            switch index {
            case 0..<16: f = (b & c) | (~b & d); g = index
            case 16..<32: f = (d & b) | (~d & c); g = (5 * index + 1) % 16
            case 32..<48: f = b ^ c ^ d; g = (3 * index + 5) % 16
            default: f = c ^ (b | ~d); g = (7 * index) % 16
            }
            let sum = a &+ f &+ constants[index] &+ words[g]
            let rotated = (sum << shifts[index]) | (sum >> (32 - shifts[index]))
            let next = b &+ rotated
            a = d; d = c; c = b; b = next
        }
        state[0] &+= a; state[1] &+= b; state[2] &+= c; state[3] &+= d
    }
    var digest = state.flatMap { word in (0..<4).map { UInt8(truncatingIfNeeded: word >> ($0 * 8)) } }
    digest[6] = (digest[6] & 0x0f) | 0x30
    digest[8] = (digest[8] & 0x3f) | 0x80
    let hex = digest.map { String(format: "%02x", $0) }
    return hex[0..<4].joined() + "-" + hex[4..<6].joined() + "-" + hex[6..<8].joined() + "-" + hex[8..<10].joined() + "-" + hex[10..<16].joined()
}
