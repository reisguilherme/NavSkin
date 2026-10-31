# Navigation avatars 3D implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement and review the independent asset, Android, and iOS tasks below.

**Goal:** Use recognizable themed 3D avatars during navigation and Vibing, then deliver an updated Android APK and an inspectable desktop gallery.

**Architecture:** Editable triangle meshes are baked into transparent multiview images. Native MapLibre displays the nearest heading/pitch view as a viewport-aligned symbol grounded at the GPS coordinate. A bounded cache avoids decoding on every animation tick; existing selected flat icons remain available when browsing.

**Tech stack:** JavaScript mesh generation, Sharp, MapLibre Native Android/iOS, Kotlin, Swift, local HTML gallery.

**Spec:** User request in this conversation: GTA SA CJ BMX; Pink Hello Kitty car; Crossy chicken; RDR2 Arthur's horse; Mario Yoshi; appropriate remaining avatars. Selected Cyberpunk Kusanagi, Pip-Boy Vault Boy, Hollow Knight Knight. Earlier authorization includes app integration and new APK.

## Global constraints

- Preserve the eight existing themes, navigation behavior, Vibing, and existing saved-place artwork.
- No Minecraft or GTA V reintroduction.
- Author new meshes; do not describe them as extracted original game assets.
- Frames: 192px, 32 headings, pitches 0/40/55, grounded anchor [0.5, 0.70], display size 72 logical pixels.
- The image encodes vehicle heading minus camera bearing; do not apply an additional map rotation or pitch to it.
- No native realtime mesh claim: this is multiview rendering of actual authored 3D geometry.

## Review focus

- Heading wraparound at 0/360 and negative camera bearings.
- Manual camera rotation while vehicle remains stationary.
- Entering/leaving Vibing, changing themes and replacing styles with cached images.
- Ground anchor remaining on GPS position and silhouette remaining legible at 72px.
- Missing/malformed catalog fallback and bounded memory use.

## Tasks

- [x] Assets: `scripts/navigation-3d/{geometry,models,renderer}.mjs`, `scripts/prepare-navigation-3d.mjs`, runtime `navigation-3d/catalog.json`. Test finite meshes, complete transparent frames, directional variation, inspect render sheet.
- [x] Android: catalog/frame selector, overlay integration, camera callback; unit tests and native integration test across themes and modes.
- [x] iOS: shared catalog/frame selector, overlay/camera integration, core tests and source validation. Xcode build remains unavailable on this Windows host.
- [x] Browser: reusable frame selector, gallery with yaw/pitch controls and actual navigation scene integration; browser smoke and visual review.
- [x] Packaging: add repeatable asset preparation and iOS resource copy, bump 0.12.0/code12, build/test Android, audit signed APK, document evidence and deliver files.

## Asset contract

`navigation-3d/catalog.json`: `{schemaVersion:1, frameCount:32, pitches:[0,40,55], imageSize:192, displaySize:72, anchor:[0.5,0.70], themes:{[theme]:{label,frames:[[path x32] x3]}}}`. Paths are relative to the `navigation-3d` directory. Geometry modules export `models[theme] = {label,vertices:[[x,y,z]],faces:[{indices:[a,b,c],color}]}` with north +Y and height +Z.
