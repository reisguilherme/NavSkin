import Foundation
import UIKit
import MapLibre
import NavSkinCore

/// Registered on MapLibre's own URLSession configuration. API keys never enter tile URLs.
final class TrafficTileURLProtocol: URLProtocol {
    private static let lock = NSLock()
    private static var key = ""
    private static var generation = 0
    private static var tasks: [UUID: URLSessionTask] = [:]
    private static var onResult: ((Bool) -> Void)?
    private let token = UUID()
    private var session: URLSession?

    static func configure(key: String, onResult: ((Bool) -> Void)? = nil) {
        lock.lock()
        self.key = key
        self.onResult = onResult
        generation += 1
        let pending = Array(tasks.values)
        tasks.removeAll()
        lock.unlock()
        pending.forEach { $0.cancel() }
    }

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.scheme == "https" && request.url?.host == "api.tomtom.com" &&
            request.url?.path.hasPrefix("/maps/orbis/traffic/flow/raster/tile/") == true
    }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        let key = Self.key
        let generation = Self.generation
        Self.lock.unlock()
        guard !key.isEmpty else { client?.urlProtocol(self, didFailWithError: URLError(.cancelled)); return }
        var authorized = request
        authorized.setValue(key, forHTTPHeaderField: "TomTom-Api-Key")
        authorized.timeoutInterval = 15
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = []
        let session = URLSession(configuration: configuration, delegate: TrafficRedirectPolicy.shared, delegateQueue: nil)
        self.session = session
        let task = session.dataTask(with: authorized) { [weak self] data, response, error in
            guard let self else { return }
            Self.lock.lock()
            let current = generation == Self.generation && Self.tasks.removeValue(forKey: self.token) != nil
            let result = Self.onResult
            Self.lock.unlock()
            defer { session.finishTasksAndInvalidate() }
            guard current else { self.client?.urlProtocol(self, didFailWithError: URLError(.cancelled)); return }
            guard error == nil, let data, data.count <= 2_000_000, let response = response as? HTTPURLResponse,
                  response.statusCode == 200 else {
                // Do not propagate native request errors, which can contain request details.
                self.client?.urlProtocol(self, didFailWithError: URLError(.cannotLoadFromNetwork))
                result?(false)
                return
            }
            self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            self.client?.urlProtocol(self, didLoad: data)
            self.client?.urlProtocolDidFinishLoading(self)
            result?(true)
        }
        Self.lock.lock()
        let current = generation == Self.generation
        if current { Self.tasks[token] = task }
        Self.lock.unlock()
        if current { task.resume() }
        else { session.invalidateAndCancel(); client?.urlProtocol(self, didFailWithError: URLError(.cancelled)) }
    }

    override func stopLoading() {
        Self.lock.lock()
        let task = Self.tasks.removeValue(forKey: token)
        Self.lock.unlock()
        task?.cancel()
        session?.invalidateAndCancel()
    }
}

private final class TrafficRedirectPolicy: NSObject, URLSessionTaskDelegate {
    static let shared = TrafficRedirectPolicy()
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse,
                    newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        completionHandler(nil)
    }
}

@MainActor
final class TrafficMapOverlay {
    private weak var style: MLNStyle?
    private var active = false
    private var renderedRevision: UUID?
    private var installedConfiguration: UUID?
    private let flowID = "navskin-traffic-flow"
    private let incidentID = "navskin-traffic-incidents"

    func styleLoading() { style = nil; active = false; renderedRevision = nil }
    func styleLoaded(_ style: MLNStyle) { self.style = style; active = false; renderedRevision = nil }

    func update(_ store: TrafficStore) {
        guard let style else { return }
        if !store.active {
            if active { remove(style) }
            return
        }
        if active, installedConfiguration != store.configurationRevision { remove(style) }
        if !active {
            let flow = MLNRasterTileSource(identifier: flowID,
                tileURLTemplates: ["https://api.tomtom.com/maps/orbis/traffic/flow/raster/tile/{z}/{x}/{y}?apiVersion=2&style=dark&tileSize=256"],
                options: [.tileSize: 256, .minimumZoomLevel: 12, .maximumZoomLevel: 22,
                          .attributionHTMLString: "© <a href='https://www.tomtom.com/'>TomTom</a> · © <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a>"])
            style.addSource(flow)
            let layer = MLNRasterStyleLayer(identifier: flowID, source: flow)
            layer.rasterOpacity = NSExpression(forConstantValue: 0.8)
            layer.minimumZoomLevel = 12
            insertBelowNavigation(layer, style: style)
            let incidents = MLNShapeSource(identifier: incidentID, shape: nil, options: nil)
            style.addSource(incidents)
            let lineSource = MLNShapeSource(identifier: incidentID + "-lines", shape: nil, options: nil)
            style.addSource(lineSource)
            let lines = MLNLineStyleLayer(identifier: incidentID + "-lines", source: lineSource)
            lines.minimumZoomLevel = 12
            lines.lineWidth = NSExpression(forConstantValue: 5)
            lines.lineColor = NSExpression(forConstantValue: UIColor.systemOrange)
            lines.lineOpacity = NSExpression(forConstantValue: 0.9)
            lines.lineCap = NSExpression(forConstantValue: "round")
            lines.lineJoin = NSExpression(forConstantValue: "round")
            insertBelowNavigation(lines, style: style)
            let points = MLNCircleStyleLayer(identifier: incidentID, source: incidents)
            points.minimumZoomLevel = 12
            points.circleRadius = NSExpression(forConstantValue: 7)
            points.circleColor = NSExpression(forConstantValue: UIColor.systemOrange)
            points.circleStrokeWidth = NSExpression(forConstantValue: 2)
            points.circleStrokeColor = NSExpression(forConstantValue: UIColor.white)
            insertBelowNavigation(points, style: style)
            for (id, symbol) in [("accident", "car.fill"), ("jam", "car.2.fill"), ("roadClosed", "xmark.octagon.fill"),
                                 ("roadWorks", "hammer.fill"), ("unknown", "exclamationmark.triangle.fill")] {
                if let image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .bold)) {
                    style.setImage(image.withTintColor(.systemOrange, renderingMode: .alwaysOriginal), forName: "traffic-" + id)
                }
            }
            let symbols = MLNSymbolStyleLayer(identifier: incidentID + "-symbols", source: incidents)
            symbols.minimumZoomLevel = 12
            symbols.iconImageName = NSExpression(forKeyPath: "image")
            symbols.iconAllowsOverlap = NSExpression(forConstantValue: false)
            symbols.iconRotationAlignment = NSExpression(forConstantValue: "viewport")
            insertBelowNavigation(symbols, style: style)
            active = true
            installedConfiguration = store.configurationRevision
        }
        guard renderedRevision != store.revision else { return }
        let features = store.incidents.compactMap { incident -> MLNPointFeature? in
            guard let first = incident.points.first else { return nil }
            let middleIndex = incident.points.count / 2
            let point = incident.points.count == 2 ? VehicleInterpolation.position(from: first, to: incident.points[1], fraction: 0.5) : incident.points[middleIndex]
            let feature = MLNPointFeature()
            feature.coordinate = point.coordinate
            let icon = ["accident", "jam", "roadClosed", "roadWorks"].contains(incident.category) ? incident.category : "unknown"
            feature.attributes = ["name": incident.description, "category": incident.category, "image": "traffic-" + icon]
            return feature
        }
        (style.source(withIdentifier: incidentID) as? MLNShapeSource)?.shape = MLNShapeCollectionFeature(shapes: features)
        let lines = store.incidents.compactMap { incident -> MLNPolylineFeature? in
            guard incident.points.count >= 2 else { return nil }
            var coordinates = incident.points.map(\.coordinate)
            let feature = MLNPolylineFeature(coordinates: &coordinates, count: UInt(coordinates.count))
            feature.attributes = ["name": incident.description, "category": incident.category]
            return feature
        }
        (style.source(withIdentifier: incidentID + "-lines") as? MLNShapeSource)?.shape = MLNShapeCollectionFeature(shapes: lines)
        renderedRevision = store.revision
    }

    private func insertBelowNavigation(_ layer: MLNStyleLayer, style: MLNStyle) {
        if let route = style.layer(withIdentifier: "navskin-nav-route-casing") { style.insertLayer(layer, below: route) }
        else { style.addLayer(layer) }
    }
    private func remove(_ style: MLNStyle) {
        if let symbols = style.layer(withIdentifier: incidentID + "-symbols") { style.removeLayer(symbols) }
        for id in [incidentID, incidentID + "-lines", flowID] {
            if let layer = style.layer(withIdentifier: id) { style.removeLayer(layer) }
            if let source = style.source(withIdentifier: id) { style.removeSource(source) }
        }
        active = false
        renderedRevision = nil
    }
}
