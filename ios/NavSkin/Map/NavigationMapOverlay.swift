import Foundation
import UIKit
import CoreLocation
import MapLibre
import NavSkinCore

@MainActor
final class NavigationMapOverlay {
    private(set) var resourceError: String?
    private weak var style: MLNStyle?
    private struct RouteKey: Equatable { let id: String; let calculated: Int64; let count: Int }
    private struct AccuracyKey: Equatable { let point: GeoPoint; let radius: Double }
    private var renderedRouteKey: RouteKey?
    private var geometry: [GeoPoint] = []
    private var geometryIntact = true
    private var renderedSegment: Int?
    private var renderedHead: GeoPoint?
    private var renderedPalette: MapTheme?
    private var renderedPosition: GeoPoint?
    private var renderedBearing: Double?
    private var renderedOpacity: Double?
    private var renderedAccuracy: AccuracyKey?
    private var renderedSaved: [SavedPlace]?
    private var renderedSavedVisible: Bool?
    private var renderedDestination: GeoPoint?
    private var renderedVehicle: String?
    private var avatarRoot: URL?
    private var avatarCatalog: NavigationAvatarCatalog?
    private var avatarTheme = ""
    private var avatarEnabled = false
    private var renderedAvatarPath: String?
    private var renderedAvatar3D = false
    private var renderedIconRotation: Double?
    private var unavailableAvatarFrames = Set<String>()
    private struct AvatarImageRequest: Equatable {
        let path: String
        let url: URL
        let generation: UInt64
    }
    private let avatarDecodeQueue = DispatchQueue(label: "br.com.navskin.avatar-decode", qos: .userInitiated)
    private var avatarDecodeInFlight = false
    private var avatarStyleGeneration: UInt64 = 0
    private var desiredAvatarRequest: AvatarImageRequest?
    private var fallbackVehicle: (url: URL, name: String)?
    private var cameraBearing = 0.0
    private var cameraPitch = 0.0
    private var vehicleTimer: Timer?
    private var vehicleTarget: GeoPoint?
    private var vehicleTargetBearing: Double?
    private var vehicleFixTimestamp: Int64?
    private var vehicleSession: Int64?
    private var vehicleAnimationGeneration: UInt64 = 0
    private var backgroundObserver: NSObjectProtocol?
    private let images = NSCache<NSString, UIImage>()
    private let prefix = "navskin-nav-"

    init() {
        images.countLimit = 24
        images.totalCostLimit = 12 * 1_024 * 1_024
        backgroundObserver = NotificationCenter.default.addObserver(forName: UIApplication.willResignActiveNotification,
            object: nil, queue: .main) { [weak self] _ in
                Task { @MainActor [weak self] in self?.stopVehicleAnimation() }
            }
    }

    deinit {
        vehicleTimer?.invalidate()
        if let backgroundObserver { NotificationCenter.default.removeObserver(backgroundObserver) }
    }

    func styleLoading() {
        stopVehicleAnimation()
        avatarStyleGeneration &+= 1
        desiredAvatarRequest = nil
        vehicleTarget = nil; vehicleTargetBearing = nil; vehicleFixTimestamp = nil
        style = nil
    }

    func detach() { styleLoading() }

    private func stopVehicleAnimation() {
        vehicleAnimationGeneration &+= 1
        vehicleTimer?.invalidate(); vehicleTimer = nil
    }

    func styleLoaded(_ style: MLNStyle) {
        stopVehicleAnimation()
        avatarStyleGeneration &+= 1
        desiredAvatarRequest = nil
        self.style = style
        resourceError = nil
        renderedRouteKey = nil; geometry = []; renderedSegment = nil; renderedHead = nil
        renderedPalette = nil; renderedPosition = nil; renderedBearing = nil; renderedOpacity = nil
        renderedAccuracy = nil; renderedSaved = nil
        renderedSavedVisible = nil; renderedDestination = nil; renderedVehicle = nil
        renderedAvatarPath = nil; renderedAvatar3D = false; renderedIconRotation = nil
        install(style)
    }

    /// Camera callbacks update only a quantized avatar view, never route geometry.
    func updateCamera(bearing: Double, pitch: Double) {
        guard bearing.isFinite, pitch.isFinite else { return }
        cameraBearing = bearing
        cameraPitch = pitch
        updateVehicleAppearance()
    }

    func update(store: AppStore) {
        guard let style else { return }
        let snapshot = store.snapshot
        let theme = store.theme
        if renderedPalette != theme {
            let palette = routePalette(theme)
            for id in ["route-casing", "remaining-casing", "remaining-head-casing"] {
                (style.layer(withIdentifier: prefix + id) as? MLNLineStyleLayer)?.lineColor = NSExpression(forConstantValue: palette.casing)
            }
            for id in ["route-line", "remaining-line", "remaining-head-line"] {
                (style.layer(withIdentifier: prefix + id) as? MLNLineStyleLayer)?.lineColor = NSExpression(forConstantValue: palette.route)
            }
            let destinationLayer = style.layer(withIdentifier: prefix + "destination-circle") as? MLNCircleStyleLayer
            destinationLayer?.circleColor = NSExpression(forConstantValue: palette.route)
            destinationLayer?.circleStrokeColor = NSExpression(forConstantValue: palette.casing)
            (style.layer(withIdentifier: prefix + "accuracy-fill") as? MLNFillStyleLayer)?.fillColor = NSExpression(forConstantValue: palette.route)
            renderedPalette = theme
        }
        // Route IDs identify immutable provider results. Filter/build their long geometry once.
        let routeKey = snapshot.route.map { RouteKey(id: $0.id, calculated: $0.calculatedAtMs, count: $0.geometry.count) }
        if renderedRouteKey != routeKey {
            let requested = snapshot.route?.geometry ?? []
            geometry = requested.filter(\.isValidMapPoint)
            geometryIntact = geometry.count == requested.count
            source("route")?.shape = line(geometry)
            renderedRouteKey = routeKey
            renderedSegment = nil
            renderedHead = nil
        }
        let progress = snapshot.progress.flatMap { value -> RouteProgress? in
            guard geometryIntact,
                  snapshot.hasCurrentMapFix, snapshot.location.quality == .good, value.onRoute,
                  value.routeId == snapshot.route?.id, value.segmentIndex >= 0,
                  value.segmentIndex < geometry.count - 1, value.projectedPoint.isValidMapPoint else { return nil }
            return value
        }
        let segment = progress?.segmentIndex ?? -1
        if renderedSegment != segment {
            source("remaining")?.shape = line(segment >= 0 ? Array(geometry.dropFirst(segment + 1)) : geometry)
            renderedSegment = segment
            renderedHead = nil
            source("remaining-head")?.shape = nil
        }
        if let progress, renderedHead != progress.projectedPoint {
            source("remaining-head")?.shape = line([progress.projectedPoint, geometry[progress.segmentIndex + 1]])
            renderedHead = progress.projectedPoint
        }
        let destination = (snapshot.destination ?? snapshot.route?.destination)?.point
        let validDestination = destination.flatMap { $0.isValidMapPoint ? $0 : nil }
        if renderedDestination != validDestination {
            source("destination")?.shape = point(validDestination)
            renderedDestination = validDestination
        }
        if renderedSaved != store.places.savedPlaces || renderedSavedVisible != store.navigationPreferences.savedPlacesVisible {
            var features: [MLNPointFeature] = []
            if store.navigationPreferences.savedPlacesVisible {
                for saved in store.places.savedPlaces where saved.point.isValidMapPoint {
                    guard let icon = store.resources.savedIcon(id: saved.iconId),
                          let url = store.resources.imageURL(for: icon, theme: theme, preview: false) else { continue }
                    let name = "saved-\(theme.rawValue)-\(icon.id)"
                    guard ensureImage(url: url, name: name, style: style) else { continue }
                    let feature = point(saved.point)!
                    feature.attributes = ["image": name, "savedId": saved.id, "name": saved.name]
                    features.append(feature)
                }
            }
            source("saved")?.shape = MLNShapeCollectionFeature(shapes: features)
            renderedSaved = store.places.savedPlaces
            renderedSavedVisible = store.navigationPreferences.savedPlacesVisible
        }
        if avatarRoot != store.resources.root {
            avatarRoot = store.resources.root
            avatarCatalog = NavigationAvatarCatalog.load(root: store.resources.root)
            unavailableAvatarFrames.removeAll()
            renderedAvatarPath = nil
            desiredAvatarRequest = nil
        }
        avatarEnabled = NavigationAvatarCatalog.isEnabled(navigationActive: snapshot.navigationActive, vibing: store.vibing)
        avatarTheme = theme.rawValue
        fallbackVehicle = store.resources.vehicleIcon(id: store.vehicleID).flatMap { icon in
            store.resources.imageURL(for: icon, theme: theme, preview: false).map { ($0, "vehicle-\(theme.rawValue)-\(icon.id)") }
        }
        updateVehicleAppearance()
        updateVehicle(snapshot)
        let vehicleLayer = style.layer(withIdentifier: prefix + "vehicle-symbol") as? MLNSymbolStyleLayer
        if renderedOpacity != snapshot.mapVehicleOpacity {
            vehicleLayer?.iconOpacity = NSExpression(forConstantValue: snapshot.mapVehicleOpacity)
            renderedOpacity = snapshot.mapVehicleOpacity
        }
        let accuracy: AccuracyKey?
        if !store.vibing, snapshot.hasCurrentMapFix, let fix = snapshot.location.fix,
           fix.accuracyMeters.isFinite, fix.accuracyMeters > 0, fix.accuracyMeters <= 2_000 {
            accuracy = AccuracyKey(point: fix.point, radius: fix.accuracyMeters)
        } else { accuracy = nil }
        if renderedAccuracy != accuracy {
            source("accuracy")?.shape = accuracy.map { accuracyCircle(center: $0.point, radius: $0.radius) }
            renderedAccuracy = accuracy
        }
    }

    private func updateVehicle(_ snapshot: NavigationSnapshot) {
        let destination = snapshot.mapRenderPosition
        let bearing = snapshot.location.bearingDegrees.flatMap { $0.isFinite ? GeoMath.normalizeBearing($0) : nil } ?? renderedBearing ?? 0
        let timestamp = snapshot.location.fix?.elapsedRealtimeMs
        let reliable = snapshot.hasCurrentMapFix && snapshot.location.quality == .good &&
            UIApplication.shared.applicationState == .active && !UIAccessibility.isReduceMotionEnabled
        let sessionChanged = vehicleSession != snapshot.sessionId
        if !reliable || sessionChanged { stopVehicleAnimation() }
        let previousTimestamp = vehicleFixTimestamp
        vehicleFixTimestamp = timestamp
        vehicleSession = snapshot.sessionId
        guard sessionChanged || vehicleTarget != destination || vehicleTargetBearing != bearing else {
            // An interrupted transition must finish at the last observed fix, never beyond it.
            if vehicleTimer == nil { renderVehicle(destination, bearing: bearing) }
            return
        }
        vehicleTarget = destination
        vehicleTargetBearing = bearing
        stopVehicleAnimation()
        guard let destination, let start = renderedPosition else { renderVehicle(destination, bearing: bearing); return }
        let interval = timestamp.flatMap { timestamp in previousTimestamp.map { Double(timestamp) - Double($0) } } ?? 0
        let duration = VehicleInterpolation.duration(fixIntervalMs: interval,
            distanceMeters: GeoMath.distanceMeters(start, destination), reliable: reliable && !sessionChanged)
        guard duration > 0 else { renderVehicle(destination, bearing: bearing); return }
        let startBearing = renderedBearing ?? bearing
        let startTime = ProcessInfo.processInfo.systemUptime
        let expectedGeneration = vehicleAnimationGeneration
        let timer = Timer(timeInterval: 1.0 / 30, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in
                guard let self, self.vehicleAnimationGeneration == expectedGeneration else { return }
                guard self.style != nil, UIApplication.shared.applicationState == .active else { self.stopVehicleAnimation(); return }
                let progress = min(1, max(0, (ProcessInfo.processInfo.systemUptime - startTime) / duration))
                let eased = progress * progress * (3 - 2 * progress)
                self.renderVehicle(VehicleInterpolation.position(from: start, to: destination, fraction: eased),
                    bearing: VehicleInterpolation.bearing(from: startBearing, to: bearing, fraction: eased))
                if progress >= 1 { self.stopVehicleAnimation() }
            }
        }
        timer.tolerance = 0.006
        vehicleTimer = timer
        RunLoop.main.add(timer, forMode: .common)
    }

    private func renderVehicle(_ position: GeoPoint?, bearing: Double) {
        if renderedPosition != position {
            source("vehicle")?.shape = point(position)
            renderedPosition = position
        }
        renderedBearing = bearing
        updateVehicleAppearance()
    }

    private func updateVehicleAppearance() {
        guard let style, let layer = style.layer(withIdentifier: prefix + "vehicle-symbol") as? MLNSymbolStyleLayer else {
            desiredAvatarRequest = nil
            return
        }
        if avatarEnabled, let catalog = avatarCatalog, let root = avatarRoot,
           let frame = catalog.frame(theme: avatarTheme, heading: renderedBearing ?? 0,
                                     cameraBearing: cameraBearing, pitch: cameraPitch),
           !unavailableAvatarFrames.contains(frame.path) {
            let url = root.appendingPathComponent("navigation-3d").appendingPathComponent(frame.path)
            let request = AvatarImageRequest(path: frame.path, url: url, generation: avatarStyleGeneration)
            desiredAvatarRequest = request
            if renderedAvatarPath != frame.path {
                if let decoded = images.object(forKey: url.path as NSString) {
                    // Camera callbacks only upload a decoded cache hit, with one atlas slot.
                    style.setImage(decoded, forName: prefix + "vehicle-3d")
                    renderedAvatarPath = frame.path
                } else { requestAvatarDecode(request) }
            }
            if renderedAvatarPath == frame.path {
                if !renderedAvatar3D {
                    layer.iconImageName = NSExpression(forConstantValue: prefix + "vehicle-3d")
                    layer.iconScale = NSExpression(forConstantValue: catalog.iconScale)
                    layer.iconOffset = NSExpression(forConstantValue: NSValue(cgVector: CGVector(dx: catalog.offsetX, dy: catalog.offsetY)))
                    layer.iconRotationAlignment = NSExpression(forConstantValue: "viewport")
                    layer.iconPitchAlignment = NSExpression(forConstantValue: "viewport")
                    layer.iconRotation = NSExpression(forConstantValue: 0)
                    renderedIconRotation = 0
                    renderedAvatar3D = true
                    renderedVehicle = nil
                }
                return
            }
            // Keep the preceding angle while the next view loads; do not flash the flat icon.
            if renderedAvatar3D, renderedAvatarPath?.hasPrefix(avatarTheme + "/") == true { return }
        } else { desiredAvatarRequest = nil }
        if let fallbackVehicle, renderedVehicle != fallbackVehicle.name,
           ensureImage(url: fallbackVehicle.url, name: fallbackVehicle.name, style: style) {
            layer.iconImageName = NSExpression(forConstantValue: fallbackVehicle.name)
            renderedVehicle = fallbackVehicle.name
        }
        if renderedAvatar3D {
            layer.iconScale = NSExpression(forConstantValue: 1)
            layer.iconOffset = NSExpression(forConstantValue: NSValue(cgVector: .zero))
            layer.iconRotationAlignment = NSExpression(forConstantValue: "map")
            layer.iconPitchAlignment = NSExpression(forConstantValue: "map")
            renderedAvatar3D = false
        }
        let rotation = renderedBearing ?? 0
        if renderedIconRotation != rotation {
            layer.iconRotation = NSExpression(forConstantValue: rotation)
            renderedIconRotation = rotation
        }
    }

    private func requestAvatarDecode(_ request: AvatarImageRequest) {
        guard !avatarDecodeInFlight else { return }
        avatarDecodeInFlight = true
        avatarDecodeQueue.async { [weak self] in
            let decoded = Self.decodeAvatar(request.url)
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.avatarDecodeInFlight = false
                if let decoded {
                    self.images.setObject(decoded, forKey: request.url.path as NSString,
                        cost: (decoded.cgImage?.bytesPerRow ?? 0) * (decoded.cgImage?.height ?? 0))
                } else if self.desiredAvatarRequest == request, request.generation == self.avatarStyleGeneration {
                    self.unavailableAvatarFrames.insert(request.path)
                    self.resourceError = "Não foi possível abrir um marcador 3D local. O ícone padrão continua disponível."
                }
                // A stale task may warm the bounded cache, but only the latest live context
                // selects an image. No reference to the old MLNStyle crosses the queue.
                if let desired = self.desiredAvatarRequest,
                   desired.generation == self.avatarStyleGeneration, self.avatarEnabled, self.style != nil {
                    self.updateVehicleAppearance()
                }
            }
        }
    }

    nonisolated private static func decodeAvatar(_ url: URL) -> UIImage? {
        autoreleasepool {
            guard let image = UIImage(contentsOfFile: url.path), let source = image.cgImage,
                  let context = CGContext(data: nil, width: source.width, height: source.height,
                    bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
                    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
            // Drawing into a bitmap forces PNG decompression off the render/UI thread.
            context.draw(source, in: CGRect(x: 0, y: 0, width: CGFloat(source.width), height: CGFloat(source.height)))
            guard let decoded = context.makeImage() else { return nil }
            return UIImage(cgImage: decoded, scale: 1, orientation: .up)
        }
    }

    private func install(_ style: MLNStyle) {
        for id in ["accuracy", "route", "remaining", "remaining-head", "saved", "destination", "vehicle"] {
            style.addSource(MLNShapeSource(identifier: prefix + id, shape: nil, options: nil))
        }
        let accuracy = MLNFillStyleLayer(identifier: prefix + "accuracy-fill", source: source("accuracy")!)
        accuracy.fillOpacity = NSExpression(forConstantValue: 0.12)
        style.addLayer(accuracy)
        addLine(style, id: "route-casing", source: "route", width: 10, opacity: 0.5)
        addLine(style, id: "route-line", source: "route", width: 6, opacity: 0.4)
        addLine(style, id: "remaining-casing", source: "remaining", width: 10, opacity: 0.95)
        addLine(style, id: "remaining-head-casing", source: "remaining-head", width: 10, opacity: 0.95)
        addLine(style, id: "remaining-line", source: "remaining", width: 6, opacity: 1)
        addLine(style, id: "remaining-head-line", source: "remaining-head", width: 6, opacity: 1)
        let saved = MLNSymbolStyleLayer(identifier: prefix + "saved-symbol", source: source("saved")!)
        saved.iconImageName = NSExpression(forKeyPath: "image")
        saved.iconAllowsOverlap = NSExpression(forConstantValue: true)
        saved.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        saved.iconScale = NSExpression(forConstantValue: 0.8)
        saved.iconRotationAlignment = NSExpression(forConstantValue: "viewport")
        style.addLayer(saved)
        let destination = MLNCircleStyleLayer(identifier: prefix + "destination-circle", source: source("destination")!)
        destination.circleRadius = NSExpression(forConstantValue: 10)
        destination.circleStrokeWidth = NSExpression(forConstantValue: 3)
        style.addLayer(destination)
        let center = MLNCircleStyleLayer(identifier: prefix + "destination-center", source: source("destination")!)
        center.circleRadius = NSExpression(forConstantValue: 3)
        center.circleColor = NSExpression(forConstantValue: UIColor.white)
        style.addLayer(center)
        let vehicle = MLNSymbolStyleLayer(identifier: prefix + "vehicle-symbol", source: source("vehicle")!)
        vehicle.iconAllowsOverlap = NSExpression(forConstantValue: true)
        vehicle.iconIgnoresPlacement = NSExpression(forConstantValue: true)
        vehicle.iconScale = NSExpression(forConstantValue: 1)
        vehicle.iconRotationAlignment = NSExpression(forConstantValue: "map")
        vehicle.iconPitchAlignment = NSExpression(forConstantValue: "map")
        style.addLayer(vehicle)
    }

    private func addLine(_ style: MLNStyle, id: String, source sourceID: String, width: Double, opacity: Double) {
        let layer = MLNLineStyleLayer(identifier: prefix + id, source: source(sourceID)!)
        layer.lineCap = NSExpression(forConstantValue: "round")
        layer.lineJoin = NSExpression(forConstantValue: "round")
        layer.lineWidth = NSExpression(forConstantValue: width)
        layer.lineOpacity = NSExpression(forConstantValue: opacity)
        style.addLayer(layer)
    }

    private func source(_ id: String) -> MLNShapeSource? { style?.source(withIdentifier: prefix + id) as? MLNShapeSource }

    private func ensureImage(url: URL, name: String, style: MLNStyle) -> Bool {
        let key = url.path as NSString
        let image: UIImage
        if let cached = images.object(forKey: key) { image = cached }
        else if let loaded = UIImage(contentsOfFile: url.path) {
            image = loaded
            images.setObject(loaded, forKey: key, cost: (loaded.cgImage?.bytesPerRow ?? 0) * (loaded.cgImage?.height ?? 0))
        }
        else {
            resourceError = "Não foi possível abrir um ícone local do mapa. Confira os recursos do aplicativo."
            return false
        }
        style.setImage(image, forName: name)
        return true
    }

    private func point(_ point: GeoPoint?) -> MLNPointFeature? {
        guard let point, point.isValidMapPoint else { return nil }
        let feature = MLNPointFeature()
        feature.coordinate = point.coordinate
        return feature
    }

    private func line(_ points: [GeoPoint]) -> MLNPolylineFeature? {
        guard points.count >= 2 else { return nil }
        var coordinates = points.map(\.coordinate)
        return MLNPolylineFeature(coordinates: &coordinates, count: UInt(coordinates.count))
    }

    private func accuracyCircle(center: GeoPoint, radius: Double) -> MLNPolygonFeature {
        let latitude = center.latitude * .pi / 180
        let longitude = center.longitude * .pi / 180
        let distance = radius / 6_371_008.8
        var ring: [CLLocationCoordinate2D] = (0...40).map { index in
            let bearing = Double(index) * .pi * 2 / 40
            let lat = asin(sin(latitude) * cos(distance) + cos(latitude) * sin(distance) * cos(bearing))
            let lon = longitude + atan2(sin(bearing) * sin(distance) * cos(latitude), cos(distance) - sin(latitude) * sin(lat))
            return CLLocationCoordinate2D(latitude: lat * 180 / .pi, longitude: (lon * 180 / .pi + 540).truncatingRemainder(dividingBy: 360) - 180)
        }
        return MLNPolygonFeature(coordinates: &ring, count: UInt(ring.count))
    }

    private func routePalette(_ theme: MapTheme) -> (route: UIColor, casing: UIColor) {
        switch theme {
        case .gta: return (UIColor(red: 0.278, green: 0.847, blue: 1, alpha: 1), UIColor(red: 0.027, green: 0.067, blue: 0.067, alpha: 1))
        case .rosa: return (UIColor(red: 0.208, green: 0.341, blue: 0.910, alpha: 1), .white)
        case .rdr2: return (UIColor(red: 0.906, green: 0.749, blue: 0.333, alpha: 1), UIColor(red: 0.094, green: 0.063, blue: 0.039, alpha: 1))
        case .smw: return (UIColor(red: 0.925, green: 0.204, blue: 0.204, alpha: 1), .white)
        case .cyberpunk: return (UIColor(red: 0.957, green: 0.910, blue: 0.290, alpha: 1), UIColor(red: 0.063, green: 0.094, blue: 0.153, alpha: 1))
        case .pipboy: return (UIColor(red: 0.855, green: 1, blue: 0.682, alpha: 1), UIColor(red: 0.024, green: 0.098, blue: 0.047, alpha: 1))
        case .hollow: return (UIColor(red: 0.839, green: 0.922, blue: 1, alpha: 1), UIColor(red: 0.090, green: 0.145, blue: 0.247, alpha: 1))
        case .crossy: return (UIColor(red: 0.965, green: 0.388, blue: 0.212, alpha: 1), .white)
        }
    }
}

extension GeoPoint {
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}
