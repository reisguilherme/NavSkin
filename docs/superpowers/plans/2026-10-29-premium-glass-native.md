# NavSkin Premium Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. The user approved the interactive prototype and explicitly authorized implementation; continue without another approval gate.

**Goal:** Implement the approved glass UI in the native Android and iOS app, with real intermediate-stop routing and a new Android APK.

**Architecture:** Retain native Android Views/MapLibre and SwiftUI/MapLibre. Reuse shared navigation sessions; add waypoint support behind both phone UIs without replacing the final destination. Glass uses inexpensive translucent surfaces on Android and native materials on iOS, not per-frame screenshots of the map.

**Tech Stack:** Kotlin, Android SDK 36/min26, SwiftUI iOS17+, existing Geoapify and MapLibre integrations.

**Spec:** User-approved `prototypes/navskin-glass/index.html`, `styles.css`, `app.js`, and follow-up request to implement it. Map/route values from that prototype must never be copied as real GPS/navigation data.

## Global Constraints

- No app logo/header toolbar on map. One ellipsis button: inside guidance card while navigating; floating top right when exploring.
- Themes, filters, saved places and settings live in this menu. Preserve traffic/key/coverage/credits access.
- Speed and recenter circles have identical size (64dp/pt portrait; same shared diameter compact).
- Active navigation replaces bottom search with red `Parar` and `Adicionar parada`. ETA has only time, distance and arrival.
- Stop uses existing cancellation and service cleanup. Add-stop uses real provider data; no fixed search results or ETA.
- Preserve fonts, game sprites, bright Rosa Pop, dense map buildings, camera gestures, adaptive camera and current Android Auto integration. No CarPlay claim.
- Prefer legible native UI typography consistent with approved prototype while retaining existing game resources and map typography.
- Reuse existing application IDs, data stores and debug certificate; no credential output or credential changes.
- Existing checkout has prepared ignored native resources and toolchains. Work in place on a task branch; no second checkout/copy of these private assets.

## Review Focus

- Add/remove a stop fails or GPS weak: retain coherent final destination, itinerary and route; no phantom success.
- Rerouting after passing an intermediate waypoint must not send the driver backwards to that waypoint.
- Stop/cancel while route request is pending: stale response must not restart navigation.
- Compact/landscape/system insets/font scale: no clipped actions; equal circles and usable tap targets.
- Menu/theme/config interactions preserve current route, camera and API settings; app restart retains coherent resume data.

## Task 1: Android itinerary model, provider and runtime

Files: navigation/model/NavModels.kt, NavigationSnapshot, provider/GeoapifyProvider.kt, NavigationRuntime.kt, storage/LocalPlacesStore.kt and scoped JVM tests.

Interfaces for phone UI: `NavigationSnapshot.intermediateStops: List<Destination>`; `NavigationRuntime.addStop(destination: Destination)`; `NavigationRuntime.removeStop(id: String)`. Existing `stopNavigation()` unchanged. Add immutable defaults to data models so existing fixture constructors remain compatible.

- [x] Add meaningful provider/runtime tests for ordered waypoints, final destination retention, failure handling, stale responses and passed-stop reroutes.
- [x] Extend Geoapify request and existing multi-leg parser. Associate intermediate boundaries with route progress; preserve final-arrival semantics.
- [x] Implement add/remove/retry, cancellation, persistence/resume coherence and duplicate/invalid-stop handling.
- [x] Run focused JVM tests; report exact commands/results and any missing UI integration constraints.

## Task 2: Android native glass interface

Files: phone/MainActivity.kt, PhoneNavigationUi.kt, phone component helpers/resources and focused layout/instrumentation tests.

- [x] Replace brand toolbar with contextual ellipsis, compact glass cards and menu entries.
- [x] Use equal speed/recenter controls, pure ETA row, stop/add actions and intermediate-stop display/removal.
- [x] Search/saved/map selection supports adding a stop without replacing destination. Keep errors/waiting/preview/resume states and traffic controls reachable.
- [x] Verify layout sizes/insets and navigation actions; avoid heavy map readback blur.

## Task 3: iOS native parity

Files: ios/NavSkin/UI, App/AppStore.swift, ios/Packages/NavSkinCore routing/session/models/storage, corresponding Core tests.

- [x] Implement equivalent native material UI and real intermediate-stop semantics.
- [x] Preserve personal signing/build instructions; no CarPlay or signed-IPA claims.
- [x] Run available Swift Core tests and source parse checks; record Mac-only checks as pending.

## Task 4: Integration, verification and delivery

- [x] Review all diffs for layout fidelity, route semantics and stale callbacks. Repair findings.
- [x] Run Android JVM/lint/build, relevant instrumented native UI checks and web data tests where necessary.
- [x] Inspect actual native screenshots; compare to approved prototype.
- [x] Bump app version to 0.9.0/build9 consistently. Preserve signing identity.
- [x] Produce APK/AAB and prepared iOS source ZIP with updated readme, validation and hashes. Test archive contents.
- [x] Deliver clickable artifacts with truthful tested/platform limitations.


## Verification record

Android: 187 JVM tests; 14 distinct instrumented tests green after correcting obsolete test startup expectations; lint no errors; APK/AAB built and signature preserved. iOS: 120 Core tests, project/resources/source parsing passed; Apple SDK build remains pending on Mac. Independent review findings repaired and verified. APK and source ZIP delivered locally, with hashes and archive integrity checks.
