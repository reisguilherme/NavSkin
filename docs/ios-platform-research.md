# GameMap iOS platform research

Verified 2026-10-27. Scope: native iPhone/iPad port of the existing Android map, themes, location, Geoapify routes, saved places and icons. CarPlay is outside this scope. This research does not constitute a successful iOS build.

## Recommended dependency and platform

- Use the official SPM repository `https://github.com/maplibre/maplibre-gl-native-distribution` with an **exact 6.31.0** requirement and product/module **MapLibre**. Its package downloads `MapLibre.dynamic.xcframework.zip` from the `ios-v6.31.0` native release. The binary checksum in that manifest is `de3aaa435dd86768b06d90245e630d068dd7eef1491afae7217d1654c52c462a`. [Pinned package manifest](https://raw.githubusercontent.com/maplibre/maplibre-gl-native-distribution/6.31.0/Package.swift)
- 6.31.0 is marked Latest stable; the newer 7.0.0 and globe releases visible during research are prereleases. [Stable release](https://github.com/maplibre/maplibre-gl-native-distribution/releases/tag/6.31.0), [release history](https://github.com/maplibre/maplibre-native/releases)
- Recommend app deployment target **iOS 17.0**, with Swift 5 language mode initially. This is an app choice for contemporary SwiftUI APIs; the pinned SDK's published podspec declares iOS 12.0. Use Xcode on a compatible macOS machine for the Apple-platform build. [Pinned podspec](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/ios/MapLibre.podspec), [Apple Xcode requirements](https://developer.apple.com/xcode/system-requirements)

## Reuse the offline map assets

Existing assets are under `android/app/src/main/assets/maps/`:

- Styles: `gta.json`, `rosa.json`, `rdr2.json` and their `-night` counterparts.
- Each style has `sprite`, `glyphs`, five GeoJSON `sources.*.data` URLs and the buildings vector `sources.buildings.tiles` template, all using `asset://maps/...`.
- Preserve folder hierarchy for `data`, `tiles/buildings/{z}/{x}/{y}.pbf`, `sprites`, `glyphs/GameMapSans`, `glyphs/GameMapSerif`, and navigation PNG/catalog assets. Copy these as bundle resources with directory structure intact; ordinary Xcode groups that flatten resources can break paths or create duplicate filenames.
- Preserve source attribution and the asset credit files.

**Absolute `file://` URLs are suitable.** The pinned native `LocalFileSource` accepts that scheme, strips it, percent-decodes the resulting path and reads the local file; it does not restrict resource kinds. This supports the GeoJSON, individual MVT tiles, sprite atlas JSON/PNG and glyph PBF files after URL construction. An in-app HTTP server is unnecessary. This conclusion follows from the pinned loader source; actual rendering still needs simulator/device verification. [Pinned local file loader](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/default/src/mln/storage/local_file_source.cpp)

Recommended style transformation:

1. Resolve the bundled `maps` directory using `Bundle.main`.
2. Parse the selected style JSON and rewrite every `asset://maps/` prefix to the maps directory's absolute file URL plus `/`.
3. Rewrite URL-valued fields only: top-level `sprite` and `glyphs`, each source's string `data`/`url`, and each `tiles` element. Keep GeoJSON objects and HTTP links unchanged.
4. Construct the encoded directory prefix using `URL.absoluteString`, then append the raw relative template string. Preserve `{z}`, `{x}`, `{y}`, `{fontstack}` and `{range}` literally; encoding braces as `%7B...%7D` before MapLibre substitutes them can prevent substitution. Sprite values remain extensionless; the renderer requests `.json`, `.png` and Retina variants.
5. Serialize valid JSON and load it with `mapView.styleJSON = json`.

MapLibre explicitly documents local resource files for style URLs and absolute file URLs for shape sources. The vector source also accepts string tile URL templates; its JSON source must retain `minzoom`, `maxzoom`, `bounds` and `source-layer` identifiers. [Pinned MLNMapView header](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/ios/src/MLNMapView.h), [pinned MLNShapeSource header](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/darwin/src/MLNShapeSource.h), [pinned MLNVectorTileSource header](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/darwin/src/MLNVectorTileSource.h)

## Native map and overlay APIs

Verified in the 6.31.0 public headers:

| Task | Native API |
| --- | --- |
| Create map from rewritten JSON | `MLNMapView(frame:styleJSON:)` |
| Create map from a bundled style file | `MLNMapView(frame:styleURL:)` |
| Replace theme | `mapView.styleJSON = json` or `mapView.styleURL = url` |
| Wait before adding layers/images/sources | `func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle)` |
| Dynamic shape source | `MLNShapeSource(identifier:shape:options:)`, then update `source.shape` |
| Point and route data | `MLNPointFeature`, `MLNPolylineFeature(coordinates:count:)`, `MLNShapeCollectionFeature(shapes:)` |
| GeoJSON decoding | `MLNShape(data:encoding:)` throwing initializer |
| Add/look up sources | `style.addSource(...)`, `style.source(withIdentifier:)` |
| Add/look up layers | `style.addLayer(...)`, `style.layer(withIdentifier:)` |
| Register a PNG icon | `style.setImage(image, forName: name)` |
| Layer classes | `MLNLineStyleLayer`, `MLNFillStyleLayer`, `MLNCircleStyleLayer`, `MLNSymbolStyleLayer`, each with `init(identifier:source:)` |

There is no `styleJSONURL` property. `styleJSON` replacement is asynchronous, and `style` is nil while loading. Reinstall dynamic sources/layers and images after every style load, then apply the latest navigation snapshot. Do not replace the base style on each GPS update. [MLNMapView](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/ios/src/MLNMapView.h), [MLNStyle](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/darwin/src/MLNStyle.h), [MLNMapViewDelegate](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/ios/src/MLNMapViewDelegate.h)

Port the Android `NavigationMapOverlay` design: separate mutable sources for full route, remaining route, saved places, destination, vehicle and accuracy polygon. Retain casing/route draw order and theme palette. Place the accuracy polygon at the measured fix and the vehicle at the approved render/snapped position. Use full/remaining polyline sources for progress without recreating the style. Shape sources are intended for changing data that fits in memory. [MLNShapeSource](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/darwin/src/MLNShapeSource.h)

Symbol properties use `NSExpression`, including `iconImageName`, `iconRotation`, `iconOpacity`, `iconScale`, `iconRotationAlignment`, `iconPitchAlignment`, `iconAllowsOverlap`, and `iconIgnoresPlacement`. Use `NSExpression(forKeyPath: "image")` for each saved feature's registered image name, and constant expressions for individual vehicle settings. For north-pointing vehicle artwork, map rotation alignment matches the Android implementation. [Symbol layer API](https://maplibre.org/maplibre-native/ios/latest/documentation/maplibre/mlnsymbolstylelayer/)

The exact Swift style callback is `func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle)`. This spelling is also present in the official Swift examples. [Swift getting-started example](https://maplibre.org/maplibre-native/ios/latest/documentation/maplibre-native-for-ios/gettingstarted/)

Camera operations for the renderer:

- `mapView.setCenter(coordinate, zoomLevel: 14, direction: heading, animated: false)` sets center, zoom and bearing together. The Objective-C selector is `setCenterCoordinate:zoomLevel:direction:animated:`.
- `let camera = mapView.camera`, then assign `camera.centerCoordinate`, `camera.pitch`, `camera.heading`; commit using `mapView.setCamera(camera, animated: false)`. Use `mapView.setZoomLevel(zoom, animated: false)` for the zoom value. The camera object is a copy, so assigning fields without committing the camera does not update the view.
- `MLNMapCamera(lookingAtCenter: coordinate, altitude: 1200, pitch: 45, heading: 0)` is the supported altitude initializer. Avoid deprecated `fromDistance`.
- Fit a route using the header's `setVisibleCoordinates:count:edgePadding:animated:` (Swift `setVisibleCoordinates(&coordinates, count: UInt(coordinates.count), edgePadding: insets, animated: true)`). Call animated camera operations after the map has loaded.

Sources: [Pinned map-view camera selectors](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/ios/src/MLNMapView.h), [pinned camera header](https://raw.githubusercontent.com/maplibre/maplibre-native/ios-v6.31.0/platform/darwin/src/MLNMapCamera.h), [Swift camera example](https://maplibre.org/maplibre-native/ios/latest/documentation/maplibre-native-for-ios/poialongrouteexample/), [Swift altitude initializer example](https://maplibre.org/maplibre-native/ios/latest/documentation/maplibre-native-for-ios/buildinglightexample/).

## Location, background activity and API key

- Use `CLLocationManager` with `CLLocationManagerDelegate`, `locationManagerDidChangeAuthorization(_:)`, `locationManager(_:didUpdateLocations:)` and `locationManager(_:didFailWithError:)`. Configure `activityType = .automotiveNavigation`, `desiredAccuracy`, and `distanceFilter` according to the navigation state. Check timestamp, horizontal accuracy, course/speed validity and reduced-accuracy authorization before feeding engine updates. [CLLocationManager](https://developer.apple.com/documentation/corelocation/cllocationmanager)
- Request `requestWhenInUseAuthorization()` while foregrounded, with a Portuguese `NSLocationWhenInUseUsageDescription`. When In Use can continue a foreground-started navigation session in the background with background updates enabled; Always permission is not inherently required for this session model. [When In Use](https://developer.apple.com/documentation/corelocation/cllocationmanager/requestwheninuseauthorization()), [authorization choice](https://developer.apple.com/documentation/bundleresources/choosing-the-location-services-authorization-to-request)
- To support active navigation after screen lock, set `UIBackgroundModes = ["location"]` and toggle `allowsBackgroundLocationUpdates` only while navigation needs it. Setting this property true without that Info.plist mode terminates the app. Enable `showsBackgroundLocationIndicator` for this user-visible session. Stop location updates when no feature needs them. [Background flag](https://developer.apple.com/documentation/corelocation/cllocationmanager/allowsbackgroundlocationupdates), [background guide](https://developer.apple.com/documentation/corelocation/handling-location-updates-in-the-background)
- Store the user-supplied Geoapify key as a generic-password Keychain item, addressed by service/account. Use `SecItemAdd`, `SecItemCopyMatching`, `SecItemUpdate`, and `SecItemDelete`; propagate `OSStatus` errors. Keep it out of `UserDefaults`, source, diagnostics, URL logs and bundled resources. [Keychain storage](https://developer.apple.com/documentation/security/adding-a-password-to-the-keychain)
- For rerouting after device lock, `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` permits key access after the first unlock until restart and does not migrate the item to another device. This is appropriate for the active background navigation model. [Apple accessibility constant](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly)

## Verified build limitations on this Windows host

Environment checks found no `swift` or `xcodebuild` in Windows PATH. The initial sandboxed `wsl --list --quiet` returned `Wsl/E_ACCESSDENIED`; the same read-only check with approved sandbox escalation succeeded and listed `Ubuntu-24.04` and `docker-desktop`. The Ubuntu distro is x86_64 and initially had no Swift compiler. `docker.exe` exists, but server version and image inventory failed because `dockerDesktopLinuxEngine` was unavailable.

The official **Swift 6.4.1 Ubuntu 24.04 x86_64 toolchain** was subsequently downloaded and extracted entirely under `.runtime/swift-linux/` to make pure Swift core tests possible. The archive is 1,124,788,555 bytes; PGP verification returned `GOODSIG` and `VALIDSIG` for the official Swift 6.x key fingerprint `52BB7E3DE28A71BE22EC05FFEF80A866B47A981F`. Its SHA256 is `7d246c7114077ad01dba4ad897628446ada4c489d6c7dbcf39ae3f25cbe17e8b`. [Official Ubuntu download listing](https://www.swift.org/install/linux/ubuntu/24_04/), [official verification procedure](https://www.swift.org/install/linux/tarball/), [official signing-key fingerprints](https://www.swift.org/keys/active/)

The single missing runtime dependency was `libncurses.so.6`. The official Ubuntu `libncurses6_6.4+20240113-1ubuntu2.2_amd64.deb` was downloaded and extracted under `.runtime/swift-linux/dependencies`, without installing a system package. Its SHA256 matched the already configured Ubuntu apt package metadata: `59e8527dfd14473393960bfb7c69566f1ad9d576423421dcb8b8dcdbb3e7fca1`. `LD_LIBRARY_PATH` is scoped to the command wrapper. No apt installation, service start, shell profile change, or WSL configuration change occurred.

Verified successfully:

- `swift --version`: `Swift version 6.4.1 (swift-6.4.1-RELEASE)`, target `x86_64-unknown-linux-gnu`.
- Compiled and ran `.runtime/swift-linux/smoke.swift`, importing Foundation and FoundationNetworking and decoding JSON.
- `swift test --help` supports the project-local scratch/cache/config/security flags below.
- Compiled, linked and executed `ios/Packages/GameMapCore` with `--build-system native`: **73 XCTest tests, zero failures**, exit code 0, on 2026-10-27 at 14:15 America/Sao_Paulo. Suites: GeoMath 5, GeoapifyProvider 14, NavigationEngine 36, NavigationSession 9, PlacesStore 9. Build took 27.65 seconds and test execution 0.595 seconds.

The `.runtime/swift-linux/swift.sh` wrapper sets the toolchain PATH, local dependency search path, XDG cache, and Clang/SwiftPM module caches. Invoke through WSL using an approved sandbox escalation:

```powershell
wsl -d Ubuntu-24.04 --exec bash /mnt/c/Users/Guilherme/Downloads/GameMap/.runtime/swift-linux/swift.sh test --build-system native --package-path /mnt/c/Users/Guilherme/Downloads/GameMap/ios/Packages/GameMapCore --scratch-path /mnt/c/Users/Guilherme/Downloads/GameMap/.runtime/swift-linux/build/core-native --cache-path /mnt/c/Users/Guilherme/Downloads/GameMap/.runtime/swift-linux/cache/packages --config-path /mnt/c/Users/Guilherme/Downloads/GameMap/.runtime/swift-linux/config --security-path /mnt/c/Users/Guilherme/Downloads/GameMap/.runtime/swift-linux/security --manifest-cache local
```

Swift 6.4.1 defaults to the new `swiftbuild` backend. In this portable environment that backend compiled the package but its linker child failed to load the project-local `libncurses.so.6`; its generated manifest did not contain `LD_LIBRARY_PATH`. Using the same wrapper with the `native` backend preserved the working runtime lookup and passed all tests. The native flag produces a deprecation warning, so keep the compiler pinned and recheck this workaround when upgrading. No product source change was needed for this environment issue.

This Linux compiler can test a Foundation-only navigation package, but cannot validate UIKit/SwiftUI, CoreLocation, Security, the Apple MapLibre XCFramework, simulator rendering, or produce a signed iPhone app. The iOS deliverable must be built with the Apple SDK on macOS/Xcode. The first actual Apple-platform build must resolve the pinned package, compile both app and core tests, run simulator tests, and visually verify all six map styles plus overlays with airplane mode enabled. [Xcode supported systems](https://developer.apple.com/xcode/system-requirements)

Simulator builds do not establish device signing. For a personal iPhone, the user selects their Apple account/team and a unique bundle identifier in Xcode. A free Personal Team supports device tests with provisioning that expires after seven days; TestFlight/App Store distribution requires the appropriate Developer Program membership and distribution setup. Credentials and signing assets should be configured on the Mac. [Apple account and Personal Team limits](https://developer.apple.com/help/account/basics/about-your-developer-account)
