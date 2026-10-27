import Foundation
import CoreLocation
import NavSkinCore

/// One manager for the application session; no location subscriptions live in a View.
@MainActor
final class IOSLocationSource: NSObject, CLLocationManagerDelegate {
    var onFix: ((LocationFix) -> Void)?
    var onStatus: ((LocationAccess, Bool, String?) -> Void)?
    private let manager = CLLocationManager()
    private var wanted = false
    private(set) var running = false
    private var navigationActive = false
    override init() {
        super.init()
        manager.delegate = self
        manager.activityType = .automotiveNavigation
        manager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        manager.distanceFilter = kCLDistanceFilterNone
        manager.pausesLocationUpdatesAutomatically = false
    }
    func start() {
        wanted = true
        guard CLLocationManager.locationServicesEnabled() else {
            stopUpdates(); onStatus?(.disabled, false, "Ative a localização nos Ajustes do iPhone."); return
        }
        switch manager.authorizationStatus {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
            onStatus?(.waiting, false, "Permita a localização para usar o GPS.")
        case .restricted, .denied:
            stopUpdates()
            onStatus?(.denied, false, "Permita a localização nos Ajustes do iPhone.")
        case .authorizedAlways, .authorizedWhenInUse:
            guard !running else { return }
            updateBackgroundMode()
            running = true
            manager.startUpdatingLocation()
            onStatus?(manager.accuracyAuthorization == .fullAccuracy ? .waiting : .weak, true,
                manager.accuracyAuthorization == .fullAccuracy ? "Aguardando posição GPS atual." : "Ative Localização Precisa nos Ajustes para navegar.")
        @unknown default:
            stopUpdates(); onStatus?(.denied, false, "Não foi possível obter autorização de localização.")
        }
    }
    func stop() { wanted = false; stopUpdates(); onStatus?(.waiting, false, nil) }
    private func stopUpdates() {
        manager.stopUpdatingLocation()
        manager.allowsBackgroundLocationUpdates = false
        manager.showsBackgroundLocationIndicator = false
        running = false
    }
    func setNavigationActive(_ active: Bool) {
        guard active != navigationActive else { return }
        navigationActive = active
        updateBackgroundMode()
    }
    private func updateBackgroundMode() {
        manager.allowsBackgroundLocationUpdates = navigationActive
        manager.showsBackgroundLocationIndicator = navigationActive
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if wanted {
            // Precision may change while the manager continues running.
            if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
                onStatus?(manager.accuracyAuthorization == .fullAccuracy ? .waiting : .weak, running,
                    manager.accuracyAuthorization == .fullAccuracy ? nil : "Ative Localização Precisa nos Ajustes para navegar.")
            }
            start()
        }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard running else { return }
        let precise = manager.accuracyAuthorization == .fullAccuracy
        for location in locations {
            guard location.horizontalAccuracy >= 0, CLLocationCoordinate2DIsValid(location.coordinate) else { continue }
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let timestamp = Int64(location.timestamp.timeIntervalSince1970 * 1000)
            guard timestamp <= now + 1000 else { continue }
            let age = max(0, now - timestamp)
            // CoreLocation timestamps are wall time; map sample age onto the session's monotonic epoch.
            let elapsed = NavigationSession.elapsedMilliseconds() - age
            let bearing = location.course >= 0 && location.courseAccuracy >= 0 && location.courseAccuracy <= 45 ? location.course : nil
            onFix?(LocationFix(point: GeoPoint(latitude: location.coordinate.latitude, longitude: location.coordinate.longitude),
                accuracyMeters: precise ? location.horizontalAccuracy : max(1000, location.horizontalAccuracy),
                bearingDegrees: bearing, speedMps: location.speed >= 0 ? location.speed : nil,
                speedAccuracyMps: location.speedAccuracy >= 0 ? location.speedAccuracy : nil,
                elapsedRealtimeMs: elapsed, wallTimeMs: timestamp,
                isMock: location.sourceInformation?.isSimulatedBySoftware == true))
        }
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard wanted else { return }
        if (error as? CLError)?.code == .denied {
            stopUpdates(); onStatus?(.denied, false, "Localização indisponível. Verifique as permissões nos Ajustes.")
        } else {
            onStatus?(.lost, running, "Sinal de localização indisponível; aguardando recuperação.")
        }
    }
}
