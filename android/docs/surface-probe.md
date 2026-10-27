# Native Android Auto surface probe

This is the first integration gate. Build and run this small map before moving the
full web style, buildings, labels, icons, themes, or phone controls into Android.
The implementation has not yet been validated in the Desktop Head Unit (DHU) or a car.

## Rendering path

`AppManager.setSurfaceCallback(CarSurfaceRenderer(...))` supplies the host's
`SurfaceContainer`. The renderer creates a private Android `VirtualDisplay` using
the host width, height, density, and `Surface`. A `Presentation` on that display
owns a separate MapLibre Native `MapView` with `textureMode(true)`. Android
composites that TextureView into the virtual display's output Surface.

This uses no WebView, MediaProjection, phone screenshot, or phone view. The only
additional view on the presentation is a noninteractive attribution label.
Interactive map actions belong to the Android Auto host template.

Use `org.maplibre.gl:android-sdk-opengl:13.7.1`. The explicit OpenGL artifact
matches the TextureView renderer. The plain `android-sdk` artifact now defaults
to Vulkan; the selected rendering backend must therefore be intentional.

## Integration contract

```kotlin
val renderer = CarSurfaceRenderer(carContext, onStatus = { message ->
    // Store this short status and invalidate the host screen on the main thread.
})
carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
// In the owning Session lifecycle's onDestroy:
renderer.close()
```

The default style is `assets/maps/probe-style.json`. It must contain a valid native
MapLibre style with real packaged Goiânia geometry. Inline GeoJSON sources avoid
web URL, glyph, sprite, and local-server compatibility concerns in this first
probe. If present, `metadata["gamemap:attribution"]` supplies the visible credit;
otherwise the map shows `© OpenStreetMap contributors · ODbL`.

`NativeMapRenderer` owns MapView creation state and forwards
`onCreate → onStart → onResume → onPause → onStop → onDestroy`.
`Presentation.onStart/onStop` start and stop map rendering. `close()` is idempotent.
The owning Session should forward `onStart/onStop` to the surface renderer's
`start()/stop()`; a retained host Surface remains attached while native rendering
is paused. Phone activities forward `start/resume/pause/stop` separately.
The session can also forward memory pressure to `CarSurfaceRenderer.onLowMemory()`.

Each surface availability callback tears down the previous display and recreates
the map using the new host dimensions/density, retaining the current camera. This
also handles size or DPI updates without a preceding destruction callback.
Every received Surface wrapper is released when it is detached or rejected.
No Surface is released while its virtual display still owns it. On reconnection,
a new display and MapView are created from the saved camera.

The intersection of nonempty stable/visible host rectangles controls camera
padding and attribution placement. Empty rectangles mean that area is unknown.
Host scroll, scale and fling events call native camera APIs. Negative scale focal
coordinates fall back to the center of the usable map area. Explicit template
zoom actions should call `zoomBy`, because some hosts do not send scale/fling.

The map supports `applyStyle` while preserving the camera, `zoomBy`, `panBy`,
`reset`, and a main-thread-only `cameraState`. Each display must own its own
MapView; a phone preview can use a separate `NativeMapRenderer` later.

## Verification gates

On 2026-10-26, `:app:compileDebugKotlin` and
`:app:compileDebugAndroidTestKotlin` passed against the resolved MapLibre OpenGL
13.7.1 dependency. The final verification completed 31 tasks (3 executed,
28 up-to-date) in 23 seconds. The toolchain reported an SDK XML schema warning.
The native Surface instrumentation passed on the AOSP Android 16/API 36 x86_64
emulator `emulator-5554`, using WHPX and SwiftShader. The final
`:app:connectedDebugAndroidTest` run passed 1 test in 38 seconds (71 tasks).
It verified actual green park-fill pixels and dark road pixels, viewport padding,
same-Surface DPI replacement, resize, retained camera, reconnect, and teardown.
DHU and physical car tests remain unexecuted.

The following are captures of the native Surface test harness, not DHU screens:

- [800 × 480 native map](../screenshots/native-surface-probe.png)
- [1024 × 600 resized map](../screenshots/native-surface-probe-resized.png)
- [800 × 480 reconnected map](../screenshots/native-surface-probe-reconnected.png)

The audit APK is `android/artifacts/gamemap-surface-probe-debug.apk`, with SHA-256
`E482177C91C60098B46B7CF0671265937F56BCFBCC049E5172E2BDC748DD3F9E`.
The passing JUnit XML is `android/artifacts/native-surface-probe-test.xml`.

1. Compile the app and the instrumentation APK with the actual SDK dependency.
2. Run `NativeSurfaceProbeTest` on an Android device or hardware-accelerated
   emulator. It captures the actual virtual display in an ImageReader and requires
   real park and road pixels in the map interior, excluding the attribution footer. It also checks
   map padding, repeated availability with the same Surface and new DPI, resize
   with a replacement Surface, camera retention, reconnect, and repeated close.
3. Run the app in DHU. Confirm roads and polygons appear, pan and template zoom
   work, template overlays leave the map centered in its usable area, resize
   works, and disconnect/reconnect produces a new map. Save screenshots and
   `adb logcat -s GameMapSurface` output with the test device/host versions.
4. Repeat on a physical Android Auto connection before claiming car support.

The instrumentation test is a native rendering test, not an Android Auto host
integration test. A build, a style-loaded callback, or a map frame callback alone
does not establish that pixels appeared on the car display. The test and DHU gate
must report their actual execution status separately.
The first complete-frame callback can also precede the larger GeoJSON sources
appearing. Pixel checks therefore wait for the actual park and road geometry.

## API sources

Checked on 2026-10-26:

- [MapLibre Android 13.7.1 release](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.7.1)
- [Official native Android artifacts/backends](https://maplibre.org/maplibre-native/android/examples/data/rendering-engine/)
- [MapLibre OpenGL Maven artifact](https://central.sonatype.com/artifact/org.maplibre.gl/android-sdk-opengl/versions)
- [MapLibreMapOptions.textureMode](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map-options/texture-mode.html)
- [13.7.1 MapView source and lifecycle](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/maps/MapView.java)
- [13.7.1 camera update API](https://github.com/maplibre/maplibre-native/blob/android-v13.7.1/platform/android/MapLibreAndroid/src/main/java/org/maplibre/android/camera/CameraUpdateFactory.kt)
- [SurfaceCallback ownership, area and gesture contract](https://developer.android.com/reference/androidx/car/app/SurfaceCallback)
- [SurfaceContainer dimensions and density](https://developer.android.com/reference/androidx/car/app/SurfaceContainer)
- [DisplayManager virtual display flags](https://developer.android.com/reference/android/hardware/display/DisplayManager)
- [Presentation display-specific resources](https://developer.android.com/reference/android/app/Presentation)

MapLibre Compose also provides an official
[AndroidMapPresentation Surface API](https://maplibre.org/maplibre-compose/presentation/)
and an Android Auto demonstration. It is an alternative if device testing reveals
a problem with the MapView/TextureView presentation path. Adopting it adds the
Compose state/runtime dependencies and should be evaluated as a separate change.
