import SwiftUI
import UIKit
import CoreLocation
import MapLibre
import NavSkinCore

@MainActor
struct NavSkinView: UIViewRepresentable {
    @ObservedObject var store: AppStore
    @Environment(\.colorScheme) private var colorScheme

    func makeCoordinator() -> Coordinator { Coordinator(store: store) }

    func makeUIView(context: Context) -> MLNMapView {
        let initialJSON: String
        do {
            initialJSON = try store.mapRepository.styleJSON(theme: store.theme, night: colorScheme == .dark, baseMapMode: store.baseMapMode)
        } catch {
            initialJSON = MapResourceRepository.emptyStyle
            context.coordinator.styleFailure = error.localizedDescription
            context.coordinator.reportStatus(error.localizedDescription)
        }
        let map = MLNMapView(frame: .zero, styleJSON: initialJSON)
        map.delegate = context.coordinator
        map.showsUserLocation = false
        map.isRotateEnabled = true
        let offline = store.baseMapMode == .goianiaOffline
        map.setCenter(CLLocationCoordinate2D(latitude: offline ? -16.6869 : -23.5505, longitude: offline ? -49.2648 : -46.6333), zoomLevel: offline ? 13 : 12, direction: 0, animated: false)
        map.compassView.isHidden = true
        map.accessibilityLabel = "Mapa NavSkin. Mantenha pressionado para selecionar um destino."
        let longPress = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.selectPoint(_:)))
        longPress.minimumPressDuration = 0.65
        longPress.delegate = context.coordinator
        map.addGestureRecognizer(longPress)
        context.coordinator.map = map
        context.coordinator.loadedTheme = store.theme
        context.coordinator.loadedBaseMapMode = store.baseMapMode
        context.coordinator.loadedDark = colorScheme == .dark
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.update(store: store, dark: colorScheme == .dark)
    }

    static func dismantleUIView(_ map: MLNMapView, coordinator: Coordinator) {
        map.delegate = nil
        coordinator.detach()
        coordinator.map = nil
    }

    @MainActor
    final class Coordinator: NSObject, MLNMapViewDelegate, UIGestureRecognizerDelegate {
        enum CameraMode: Equatable { case follow, manual, overview }
        private var store: AppStore
        weak var map: MLNMapView?
        var loadedTheme: MapTheme?
        var loadedBaseMapMode: MapBaseMode?
        var loadedDark = false
        var styleFailure: String?
        private let overlay = NavigationMapOverlay()
        private let trafficOverlay = TrafficMapOverlay()
        private var ready = false
        private var mode: CameraMode = .follow
        private var sessionID: Int64?
        private var previewRouteID: String?
        private var commandID: UUID?
        private var renderedCategories: Set<String>?
        private var pendingCamera: MLNMapCamera?
        private var pendingZoom: Double?
        private var cameraPolicy = NavigationCameraPolicy()
        private var renderedCamera: NavigationCameraPosition?
        private var lastOrientation: MapOrientation?
        private var reportedFollowing: Bool?
        private var reportedStatus: String?
        private var statusReported = false
        private var hasCenteredGPS = false
        private var needsOverview = false

        init(store: AppStore) { self.store = store }

        func detach() { overlay.detach(); trafficOverlay.styleLoading() }

        func update(store: AppStore, dark: Bool) {
            self.store = store
            guard let map else { return }
            if loadedTheme != store.theme || loadedDark != dark || loadedBaseMapMode != store.baseMapMode {
                do {
                    let json = try store.mapRepository.styleJSON(theme: store.theme, night: dark, baseMapMode: store.baseMapMode)
                    styleFailure = nil
                    pendingCamera = map.camera
                    pendingZoom = map.zoomLevel
                    ready = false
                    overlay.styleLoading()
                    trafficOverlay.styleLoading()
                    loadedTheme = store.theme
                    loadedBaseMapMode = store.baseMapMode
                    loadedDark = dark
                    renderedCategories = nil
                    map.styleJSON = json
                } catch { styleFailure = error.localizedDescription; reportStatus(styleFailure) }
                return
            }
            guard ready, let style = map.style else { return }
            if renderedCategories != store.enabledCategories {
                for category in PoiCategory.allCases {
                    style.layer(withIdentifier: "poi-" + category.id)?.isVisible = store.enabledCategories.contains(category.id)
                }
                renderedCategories = store.enabledCategories
            }
            overlay.update(store: store)
            trafficOverlay.update(store.traffic)
            if let error = overlay.resourceError { reportStatus(error) }
            updateCamera(map)
        }

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            guard mapView.style === style else { return }
            ready = true
            overlay.styleLoaded(style)
            trafficOverlay.styleLoaded(style)
            if let pendingCamera {
                mapView.setCamera(pendingCamera, animated: false)
                if let pendingZoom { mapView.setZoomLevel(pendingZoom, animated: false) }
                self.pendingCamera = nil
                self.pendingZoom = nil
            }
            reportStatus(styleFailure)
            update(store: store, dark: loadedDark)
            updateTrafficViewport(mapView)
        }

        func mapView(_ mapView: MLNMapView, regionDidChangeAnimated animated: Bool) {
            updateTrafficViewport(mapView)
        }

        private func updateTrafficViewport(_ map: MLNMapView) {
            guard ready else { return }
            let coordinate = map.centerCoordinate
            let zoom = map.zoomLevel
            DispatchQueue.main.async { [weak self] in
                self?.store.traffic.viewport(center: GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude), zoom: zoom)
            }
        }

        func mapViewDidFailLoadingMap(_ mapView: MLNMapView, withError error: Error) {
            ready = false
            // The native error may contain paths; display a stable message instead.
            reportStatus(store.baseMapMode == .world
                ? "Não foi possível carregar o mapa mundial. Confira sua conexão ou escolha Goiânia offline em Temas e mapa."
                : "Não foi possível renderizar o mapa offline. Confira os recursos do aplicativo.")
        }

        // MapLibre calls this only for a user gesture, never for our programmatic camera updates.
        func mapView(_ mapView: MLNMapView, shouldChangeFrom oldCamera: MLNMapCamera, to newCamera: MLNMapCamera) -> Bool {
            // A gesture during style loading supersedes the camera captured before the reload.
            pendingCamera = nil
            pendingZoom = nil
            needsOverview = false
            mode = .manual
            reportFollowing(false)
            return true
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool { true }

        @objc func selectPoint(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began, let map else { return }
            mode = .manual
            needsOverview = false
            reportFollowing(false)
            let coordinate = map.convert(gesture.location(in: map), toCoordinateFrom: map)
            let point = GeoPoint(latitude: coordinate.latitude, longitude: coordinate.longitude)
            guard point.isValidMapPoint else { return }
            DispatchQueue.main.async { [weak self] in self?.store.selectMapPoint(point) }
        }

        private func updateCamera(_ map: MLNMapView) {
            let snapshot = store.snapshot
            if snapshot.navigationActive && snapshot.sessionId != sessionID {
                sessionID = snapshot.sessionId
                mode = .follow
                hasCenteredGPS = false
                needsOverview = false
                cameraPolicy.reset()
                renderedCamera = nil
                reportFollowing(true)
            }
            if let command = store.mapCommand, command.id != commandID {
                commandID = command.id
                switch command.kind {
                case .recenter:
                    mode = .follow
                    hasCenteredGPS = false
                    needsOverview = false
                    cameraPolicy.reset()
                    renderedCamera = nil
                    reportFollowing(true)
                case .overview:
                    mode = .overview
                    reportFollowing(false)
                    needsOverview = true
                case .offlineCoverage:
                    mode = .manual
                    needsOverview = false
                    previewRouteID = snapshot.route?.id
                    reportFollowing(false)
                    map.setCenter(CLLocationCoordinate2D(latitude: -16.6869, longitude: -49.2648), zoomLevel: 13, direction: 0, animated: true)
                    return
                case .zoom(let delta):
                    mode = .manual
                    needsOverview = false
                    reportFollowing(false)
                    map.setZoomLevel(min(20, max(1, map.zoomLevel + delta)), animated: true)
                }
            }
            if mode != .follow {
                if let lastOrientation, lastOrientation != store.navigationPreferences.orientation {
                    let camera = map.camera
                    camera.heading = snapshot.mapFollowBearing(store.navigationPreferences.orientation)
                    map.setCamera(camera, animated: false)
                }
                lastOrientation = store.navigationPreferences.orientation
            }
            if snapshot.phase == .preview, let route = snapshot.route, route.id != previewRouteID {
                mode = .overview
                reportFollowing(false)
                if fitRoute(map) { previewRouteID = route.id }
            }
            if needsOverview, fitRoute(map) { needsOverview = false }
            guard mode == .follow,
                  let target = cameraPolicy.update(snapshot, orientation: store.navigationPreferences.orientation,
                                                   fallbackBearing: map.direction),
                  target != renderedCamera || !hasCenteredGPS else { return }
            map.setCenter(target.point.coordinate, zoomLevel: target.zoom, direction: target.bearing,
                          animated: hasCenteredGPS && !UIAccessibility.isReduceMotionEnabled)
            renderedCamera = target
            lastOrientation = store.navigationPreferences.orientation
            hasCenteredGPS = true
            reportFollowing(true)
        }

        @discardableResult
        private func fitRoute(_ map: MLNMapView) -> Bool {
            guard let route = store.snapshot.route, map.bounds.width > 0, map.bounds.height > 0 else { return false }
            var coordinates = route.geometry.filter(\.isValidMapPoint).map(\.coordinate)
            guard coordinates.count >= 2 else { return false }
            let landscape = map.bounds.width > map.bounds.height
            let insets = landscape ? UIEdgeInsets(top: min(80, map.bounds.height * 0.2), left: min(400, map.bounds.width * 0.46), bottom: 30, right: min(120, map.bounds.width * 0.15)) :
                UIEdgeInsets(top: min(160, map.bounds.height * 0.23), left: 30, bottom: min(260, map.bounds.height * 0.37), right: min(112, map.bounds.width * 0.23))
            map.setVisibleCoordinates(&coordinates, count: UInt(coordinates.count), edgePadding: insets, animated: true)
            return true
        }

        private func reportFollowing(_ value: Bool) {
            guard reportedFollowing != value else { return }
            reportedFollowing = value
            DispatchQueue.main.async { [weak self] in self?.store.reportFollowing(value) }
        }

        func reportStatus(_ message: String?) {
            guard !statusReported || reportedStatus != message else { return }
            statusReported = true
            reportedStatus = message
            DispatchQueue.main.async { [weak self] in self?.store.reportMapStatus(message) }
        }
    }
}
