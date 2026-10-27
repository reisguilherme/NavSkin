# GameMap Android / Android Auto — Implementation Plan

> **For agentic workers:** Execute the minimum Surface gate before migrating the complete phone/car UI. Independent data/style preparation is allowed. The user explicitly requests implementation; no additional design sign-off is needed.

**Goal:** Kotlin APK for real Goiânia POIs with GTA SA and original Rosa Pop themes, six persisted category filters and native Android Auto map controls; no GPS, routes or location permission.

**Architecture:** Separate Android module, preserving web PoC. Car App Library POI/API7 templates render host controls; MapLibre Native OpenGL draws an independent map through TextureView/VirtualDisplay/Presentation. Same packaged geodata and local preference store serve phone and car. New assets/POI package are prepared reproducibly from the existing project and current OSM.

**Spec:** `docs/android-brief.txt`.

## Constraints and evidence

- No NAVIGATION category, GPS permissions, WebView, phone mirroring, routes or backend.
- Trusted installation source needed for real car (Play internal sharing/testing); APK sideload alone is not proof.
- User reports no Android phone currently available. DHU and physical car tests cannot be claimed.
- User explicitly authorized Android SDK license acceptance for local platform-tools, Android36, build-tools35 and DHU.
- Minimum Surface probe uses actual OSM geometry and visible attribution. Instrumented native pixels are separate evidence from a DHU host test.

## Tasks

- [x] Research official POI category/template/Surface/test distribution rules (`docs/android-auto-feasibility.md`).
- [x] Prepare portable JDK/Gradle/SDK tools, obtain explicit license consent, install approved SDK components.
- [x] Implement minimum CarAppService/MapWithContentTemplate + real local map Surface renderer, phone probe and ImageReader Surface instrumentation.
- [x] Compile the probe and test native Surface rendering if a local emulator can run; record exact evidence before full UI migration.
- [x] Prepare independent six-category real OSM dataset and geometry/dedup tests.
- [x] Prepare native-compatible GTA SA / original Rosa Pop styles, glyphs, sprites and editable assets.
- [x] Implement shared persisted preferences, phone preview/settings and host-template theme/category screens after minimum gate.
- [x] Verify camera preservation, icon filtering vs cartographic fills, reconnect lifecycle, build/test, APK and reproducible instructions.
- [x] Capture real native phone/surface output where runnable; clearly list DHU/hardware limits and car test instructions.

## Execution outcome

34 Node checks, 14 Android JVM tests and 4 real emulator instrumented tests passed. Native Surface resize/recreate is tested; Android Auto physical reconnection is not. APK and debug AAB generated; final APK signature, permissions, installation, cold launch and preferences after process termination verified. Lint passes with 43 warnings and no baseline. Exact artifacts, hashes and evidence boundaries are recorded in `android/docs/VALIDATION.md`.

**Acceptance still pending:** No Android phone is available, so DHU discovery, actual host controls, Google Play trusted-source installation and physical-car operation remain untested. The completed implementation/checklist does not mean the end-to-end car acceptance criterion has been achieved.

## Review focus

- Surface replace/resize/density and lifecycle; no accumulated displays or released-Surface reuse.
- Native styles need offline glyphs and schema compatibility; web-only local font fallback is unavailable.
- All six categories semantic and independent; disabling parks affects icons, not green polygons.
- Async style replacements retain camera and reapply preferences; phone/car observers use same local store.
- Host template action limits and min API7; no custom Surface buttons to bypass restrictions.
