# Goiânia / San Andreas — Implementation Plan

> **For agentic workers:** Use executing-plans for the application, with independent reference and data research in parallel. Execution is explicitly requested in the user's supplied brief; no additional design approval is needed.

**Goal:** Local, navigable real Goiânia map for visual comparison with the original GTA SA pause map.

**Architecture:** MapLibre GL JS renders four local GeoJSON sources. An independent style builder consumes editable JSON tokens; the UI switches styling without replacing source geometry or moving the camera. Research, data provenance and screenshots remain in the project.

**Tech Stack:** JavaScript modules, MapLibre 6.16.0, Vite 8.3.5, Node built-in tests.

**Spec:** User's complete supplied brief, copied to docs/brief.txt.

## Global constraints

- Original PS2-era pause-map reference; minimap only supplementary, provenance explicit.
- No fabricated geographic geometry, raster-filter map, paid credentials, or product backend.
- Local OSM data includes Centro, Praça Cívica, Setor Bueno, Vaca Brava and Flamboyant.
- Same data/camera in both styles; three presets, reset, collapsible reference/tuning panel.

## Review focus

- Bridges and tunnels: level groups must render in ascending order.
- Intersections: all casings of a level precede its fills.
- Offline operation: no runtime tile/font/CDN requests.
- Dense urban network: zoom-based visibility, road widths, limited label density.
- Data failures: readable loading/error state, attribution and exact provenance.

### Task 1: Verifiable visual specification

- [x] Research and inspect original references, clearly grade provenance.
- [x] Save docs/references.md and public/styles/tokens.json with estimates, rules and adaptations.

### Task 2: Local real geography

- [x] Verify five named anchors using OSM, download raw data, preserve ways/relations.
- [x] Export public/data/{roads,areas,waterways,places}.geojson and metadata.json with schema and sources.
- [x] Save reproducible fetch/validation scripts and docs/data.md.

### Task 3: Map and comparison interface

- [x] Write meaningful invariant tests for style builder, observe failure, implement src/style.js.
- [x] Implement index.html, src/main.js, src/app.css; editable tokens separate from logic.
- [x] Run tests and build. Show local preview.

### Task 4: Inspect, refine and deliver

- [x] Inspect three identical cameras in GTA SA and neutral; correct observed issues.
- [x] Exercise zoom/pan, switching, reset, presets, controls and panel; check console/network.
- [x] Save six screenshots and docs/validation.md, short README and remaining limitations.
- [x] Independent code/data review, fix material findings and rerun relevant checks.

## Decisions

- Empty folder, no existing repository; work directly in requested workspace, no Git setup required.
- No texture overlay or game icon assets without verified evidence. Typography is a documented local-font adaptation.
- Map rendering dependency installed via npm with lockfile. Public data snapshot runs locally after download.
