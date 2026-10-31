# Eight themes and map perspective

**Goal:** Incorporate approved Pip-Boy, Hollow Knight and Crossy Road into the catalog; offer 2D/3D for every theme; use 3D by default in navigation.

**Scope:** Android/iOS app source, reproducible map assets and HTML lab. No APK/IPA generation or installation requested. Existing unrelated work is preserved.

**Architecture:** IDs `pipboy`, `hollow`, `crossy` extend the existing five active themes. Native style assets expose hidden `navskin-3d-` building extrusion layers. Native renderers choose visibility and pitch using persisted browse/navigation preferences (browse defaults 2D, navigation defaults 3D). Route overview remains flat; recenter resumes the chosen navigation perspective. The browser lab offers the same choice and an eight-theme catalog.

## Work

- [x] Reproducible offline/world styles, sprite atlases, saved/vehicle catalogs, previews, provenance and iOS bundle manifests.
- [x] Android themes, preferences, menu control, adaptive-camera perspective and tests.
- [x] iOS themes, preferences, menu control, camera perspective and tests.
- [x] Browser eight-theme catalog, dimension toggle everywhere and navigation solid volumes matching each style.
- [x] Check tests, browser captures, original road direction, hidden duplicate footprints and package integrity.

## Invariants

Do not restore Minecraft or GTA5. Keep real footprint geometry; explain illustrative height. Default navigation3D never overrides an explicit 2D preference on GPS updates. Route preview must not change stored preferences. Never duplicate the hidden OSM building source. Preserve original SMW road arrow east orientation and north-facing vehicle. New app features require a subsequent binary build to reach the installed app.

## Verification

83 Node tests passed; 203 Android JVM tests passed and instrumented-test Kotlin compiled; 136 Swift core tests passed and iOS source syntax/resource/project validation passed; 30 integrated browser checks passed. Independent source review approved Android/iOS/browser contracts. Known Native GeoJSON pattern-switch issue addressed with the existing solid-area safeguard, retaining contours, vector-building textures and POI art. Device rendering and an Xcode build are still required before claiming native visual validation. Existing APK/AAB hashes remain unchanged.
