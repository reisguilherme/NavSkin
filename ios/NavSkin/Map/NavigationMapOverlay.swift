import Foundation
import UIKit
import CoreLocation
import MapLibre
import NavSkinCore

@MainActor
final class NavigationMapOverlay {
    private(set) var resourceError: String?
    private weak var style: MLNStyle?
    private var renderedRoute: [GeoPoint]?
    private var renderedRemaining: [GeoPoint]?
    private var renderedSaved: [SavedPlace]?
    private var renderedSavedVisible: Bool?
    private var renderedDestination: GeoPoint?
    private var renderedVehicle: String?
    private let images = NSCache<NSString, UIImage>()
    private let prefix = "navskin-nav-"

    func styleLoading() { style = nil }

    func styleLoaded(_ style: MLNStyle) {
        self.style = style
        resourceError = nil
        renderedRoute = nil; renderedRemaining = nil; renderedSaved = nil
        renderedSavedVisible = nil; renderedDestination = nil; renderedVehicle = nil
        install(style)
    }

    func update(store: AppStore) {
        guard let style else { return }
        let snapshot = store.snapshot
        let theme = store.theme
        let palette = routePalette(theme)
        for id in ["route-casing", "remaining-casing"] {
            (style.layer(withIdentifier: prefix + id) as? MLNLineStyleLayer)?.lineColor = NSExpression(forConstantValue: palette.casing)
        }
        for id in ["route-line", "remaining-line"] {
            (style.layer(withIdentifier: prefix + id) as? MLNLineStyleLayer)?.lineColor = NSExpression(forConstantValue: palette.route)
        }
        let destinationLayer = style.layer(withIdentifier: prefix + "destination-circle") as? MLNCircleStyleLayer
        destinationLayer?.circleColor = NSExpression(forConstantValue: palette.route)
        destinationLayer?.circleStrokeColor = NSExpression(forConstantValue: palette.casing)
        (style.layer(withIdentifier: prefix + "accuracy-fill") as? MLNFillStyleLayer)?.fillColor = NSExpression(forConstantValue: palette.route)

        let requestedGeometry = snapshot.route?.geometry ?? []
        let geometry = requestedGeometry.filter(\.isValidMapPoint)
        if renderedRoute != geometry { source("route")?.shape = line(geometry); renderedRoute = geometry }
        let progress = snapshot.progress.flatMap { value -> RouteProgress? in
            guard requestedGeometry.count == geometry.count,
                  snapshot.hasCurrentMapFix, snapshot.location.quality == .good, value.onRoute,
                  value.routeId == snapshot.route?.id, value.segmentIndex >= 0,
                  value.segmentIndex < geometry.count - 1, value.projectedPoint.isValidMapPoint else { return nil }
            return value
        }
        let remaining = progress.map { [$0.projectedPoint] + Array(geometry.dropFirst($0.segmentIndex + 1)) } ?? geometry
        if renderedRemaining != remaining { source("remaining")?.shape = line(remaining); renderedRemaining = remaining }
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
        if let icon = store.resources.vehicleIcon(id: store.navigationPreferences.vehicleId),
           let url = store.resources.imageURL(for: icon, theme: theme, preview: false) {
            let name = "vehicle-\(theme.rawValue)-\(icon.id)"
            if renderedVehicle != name, ensureImage(url: url, name: name, style: style) {
                (style.layer(withIdentifier: prefix + "vehicle-symbol") as? MLNSymbolStyleLayer)?.iconImageName = NSExpression(forConstantValue: name)
                renderedVehicle = name
            }
        }
        source("vehicle")?.shape = point(snapshot.mapRenderPosition)
        let vehicleLayer = style.layer(withIdentifier: prefix + "vehicle-symbol") as? MLNSymbolStyleLayer
        vehicleLayer?.iconRotation = NSExpression(forConstantValue: snapshot.location.bearingDegrees.flatMap { $0.isFinite ? $0 : nil } ?? 0)
        vehicleLayer?.iconOpacity = NSExpression(forConstantValue: snapshot.mapVehicleOpacity)
        if snapshot.hasCurrentMapFix, let fix = snapshot.location.fix,
           fix.accuracyMeters.isFinite, fix.accuracyMeters > 0, fix.accuracyMeters <= 2_000 {
            source("accuracy")?.shape = accuracyCircle(center: fix.point, radius: fix.accuracyMeters)
        } else { source("accuracy")?.shape = nil }
    }

    private func install(_ style: MLNStyle) {
        for id in ["accuracy", "route", "remaining", "saved", "destination", "vehicle"] {
            style.addSource(MLNShapeSource(identifier: prefix + id, shape: nil, options: nil))
        }
        let accuracy = MLNFillStyleLayer(identifier: prefix + "accuracy-fill", source: source("accuracy")!)
        accuracy.fillOpacity = NSExpression(forConstantValue: 0.12)
        style.addLayer(accuracy)
        addLine(style, id: "route-casing", source: "route", width: 10, opacity: 0.5)
        addLine(style, id: "route-line", source: "route", width: 6, opacity: 0.4)
        addLine(style, id: "remaining-casing", source: "remaining", width: 10, opacity: 0.95)
        addLine(style, id: "remaining-line", source: "remaining", width: 6, opacity: 1)
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
        else if let loaded = UIImage(contentsOfFile: url.path) { image = loaded; images.setObject(loaded, forKey: key) }
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
        }
    }
}

extension GeoPoint {
    var isValidMapPoint: Bool { GeoMath.isValid(self) && (-85.051129...85.051129).contains(latitude) }
    var coordinate: CLLocationCoordinate2D { CLLocationCoordinate2D(latitude: latitude, longitude: longitude) }
}

extension NavigationSnapshot {
    var hasCurrentMapFix: Bool {
        location.fix?.point.isValidMapPoint == true &&
            (location.quality == .good || location.quality == .weak) && location.ageMs <= 5_000
    }
    var mapRenderPosition: GeoPoint? {
        guard let actual = location.fix?.point, actual.isValidMapPoint else { return nil }
        if navigationActive, hasCurrentMapFix, location.quality == .good, let progress,
           progress.routeId == route?.id, progress.onRoute, progress.projectedPoint.isValidMapPoint {
            return progress.projectedPoint
        }
        return actual
    }
    var mapVehicleOpacity: Double {
        if location.fix == nil || location.quality == .unavailable { return 0 }
        if location.quality == .lost || location.ageMs > 15_000 { return 0.35 }
        if location.quality == .weak || location.ageMs > 5_000 { return 0.65 }
        return 1
    }
    func mapFollowBearing(_ orientation: MapOrientation) -> Double {
        guard orientation == .movementUp, hasCurrentMapFix,
              let bearing = location.bearingDegrees, bearing.isFinite else { return 0 }
        return GeoMath.normalizeBearing(bearing)
    }
}
