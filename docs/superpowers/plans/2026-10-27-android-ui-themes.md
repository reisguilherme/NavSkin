# Android map UI and three themes — implementation plan

> **For agentic workers:** Execute independent phone UI, cartography and Android test tasks in parallel; root integrates and verifies. The user explicitly requested these reversible changes to the existing app. No additional design approval is needed.

**Goal:** A modern map-first Android app with an opening preview carousel, retractable theme/category sheets, lively Rosa Pop and a third RDR2-inspired cartographic theme.

**Architecture:** Preserve Kotlin Views, MapLibre Native, local preferences, real geodata and the separate Android Auto host templates. Material Components supplies accessible bottom sheets; a native horizontal carousel uses packaged previews captured from the actual styled map. All three themes share six semantic POI filters and day/night variants.

**Tech stack:** Existing pinned Android toolchain; Material Components 1.13.0; no Compose rewrite, backend, GPS, routes or runtime asset service.

**Spec:** User request dated 27/10/2026 in this conversation, extending `docs/android-brief.txt`. The previous APK was reported working on Android by the user; this does not establish Android Auto host testing.

## Design decisions

- Full-size map extends behind floating controls and system insets. A compact top location/identity pill, two bottom menu entries and rounded zoom/reset controls replace the two large button rows.
- Theme sheet opens on a fresh app launch with three preview cards and an explicit “Explorar mapa” dismissal. A selected card applies the persisted theme; the map remains visible behind the sheet.
- Filters live in a separate dismissible sheet, with six independent switches, counts and immediate application. Back, outside-tap, drag and rotation retain a consistent sheet state.
- Phone chrome is light, with candy-pink accents for Rosa Pop. Its night map remains a distinct, lower-luminance but saturated variant rather than muddy burgundy. Car day/night selection continues to follow the host.
- RDR2 uses parchment, sepia/ink roads, restrained red and original pictograms/serif lettering, while keeping actual Goiânia geometry. No fictional topographic contours or copied game artwork.
- Existing preference keys and application ID remain compatible. Increment versionCode to 2 and versionName to 0.3.0; use the same development signing identity for in-place update.
- Android Auto keeps its allowed host templates; the new theme becomes a third radio choice there. Phone bottom sheets do not replace host controls.

## Tasks

- [x] Cartographic assets: extend `scripts/prepare-android-maps.mjs`, editable palette/icons/fonts and native asset tests to six variants. Preserve GTA; refresh Rosa; build RDR2 and record licenses/provenance.
- [x] Phone UI: replace `phone/MainActivity.kt` presentation with focused native UI helpers and Material bottom sheets. Preserve renderer lifecycle and shared preferences; add accessible descriptions and fit small/landscape/large-text layouts.
- [x] Integration: add `MapTheme.RDR2`, Material dependency, version increment, packaged true-map preview images and compatible development artifacts.
- [x] Verification: update meaningful JVM/instrumented tests, test all themes/filters/camera persistence, capture real phone UI, inspect both day/night map output and rebuild/sign/install APK.
- [x] Delivery: keep 0.2.0 artifacts/reports as history; publish versioned 0.3.0 APK and new evidence/instructions. Document the limits of emulator and user-reported hardware results without inventing host validation.

## Review focus

- Horizontal carousel drag must not dismiss the bottom sheet; initial selected card must be reachable even when persisted as RDR2.
- Closing/reopening or rotating the phone must not reset the manual camera or reopen a dismissed sheet unexpectedly.
- All six categories and a new theme must survive replacing the native style; turning off parks must retain green polygons.
- Light phone chrome must remain light in OS night mode, with readable system bars and useful map viewport on small screens.
- Preview assets must show the actual current map theme and stay offline; package verification must confirm all three are present.
