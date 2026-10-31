# Two new game maps and GTA SA readability

> Execution: superpowers:subagent-driven-development. The user requested starting this expansion of the existing native app. Continue the authorized implementation with existing technology and approved Vidro UI; no new approval gate.

**Goal:** Add selectable Super Mario World and Cyberpunk 2077 maps on Android/iOS, improve GTA SA map label legibility, and deliver an updated APK plus iOS source. The final active set is GTA SA, Rosa Pop, RDR2, Super Mario World and Cyberpunk 2077.

**Scope revision:** The user explicitly withdrew Minecraft and GTA V from this delivery. Their draft SVGs and palettes remain as source material, but they must not appear in active selectors, runtime catalogs, packaged styles, fonts or previews. This plan retains its original filename for continuity.

**Architecture:** Extend the existing shared generated map assets and semantic icon catalogs; retain native Kotlin/SwiftUI MapLibre clients. Five active themes each have day/night and world/offline styles: 20 styles and five native previews. Real geography, routes, filters, saved IDs and vehicle IDs remain intact.

**Design decisions:** New active IDs are `smw` and `cyberpunk`; titles are Super Mario World and Cyberpunk 2077. SMW references the SNES overworld with illustrated terrain, pixel materials and colored sprites. Cyberpunk 2077 uses dark blue/cyan/red navigation graphics, architectural linework and terminal pictograms. Preserve the neutral Vidro controls. Readable street names take priority over decorative display typography; document authentic sources versus authored adaptations honestly. No new API keys, geometry fabrication or navigation behavior changes.

**Validation status:** Complete for the five-theme revision: 74 shared tests, 191 JVM tests, 10 Android instrumented tests and 123 Swift Core tests passed. APK/AAB and iOS source ZIP were built and inspected. Apple SDK compilation and iPhone testing remain explicitly outside this Windows validation; the friend can build the prepared source on a Mac.

## Review focus

- Theme switching preserves route, camera, stops, categories and saved icons; older preferences still decode.
- Five themes appear in phone and Android Auto selectors; car host list limits are respected. Previously saved withdrawn theme IDs fall back safely.
- New styles contain all nine categories, matching sprites/catalogs and fallback glyphs; both map coverages and night variants load.
- GTA SA labels remain legible at street zoom, with moderate halos, mixed case and adequate spacing; POI captions do not overwhelm roads.
- Five native previews depict the actual map styles; the two-new-theme comparison uses `android/screenshots/v0.10/two-new-themes.png`. Generated asset checks remain strict and sources/licenses/provenance are explicit.
- Runtime assets contain exactly 20 styles and 90 navigation choices with 180 PNG variants; Minecraft/GTA V remain excluded despite retained draft sources.

## Tasks

- [x] Shared theme assets: extend palettes, generators, sprites and semantic saved/vehicle catalogs, with schema/style tests (agent).
- [x] Android integration: enum, exhaustive theme cases, selectors/previews/Auto and focused compatibility/render tests (agent).
- [x] iOS integration: enum, exhaustive cases, resource validation and theme persistence/tests; preserve native UI (agent).
- [x] GTA SA typography: diagnose current glyph/size/halo settings; correct the cartographic font roles and validate actual rendering (root).
- [x] Integrate generators/resources, inspect native screenshots for all themes and GTA legibility, repair issues, independent review.
- [x] Version 0.10.0/build10, relevant JVM/JS/Swift/native checks, APK/AAB and iOS source packaging with updated usage/validation.

## Ownership

Root owns typography catalog/loader, game font import decisions, build/version/package scripts, release docs and final execution. Shared-assets agent owns theme-tokens.json, prepare-android-maps.mjs, prepare-world-maps.mjs, prepare-navigation-icons.mjs and new theme SVG sources/catalog. Native agents own platform source and tests only, not generators or generated resources. Coordinate generator execution and Gradle; do not run concurrently against shared outputs. Existing 0.9 changes must be preserved.
