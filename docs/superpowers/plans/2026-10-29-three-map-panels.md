# Three map panels implementation plan

**Goal:** Show Pip-Boy, Hollow Knight and Crossy Road inspired maps of Goiânia in the existing HTML lab before changing the application.

**Architecture:** Three independent style modules transform the existing offline map sources. One local browser panel compares the three interactive maps and expands each for closer inspection. Native assets, app code and packages remain untouched.

**Tech stack:** Existing MapLibre GL JS, HTML/CSS, JavaScript and original SVG/canvas symbols; no new dependencies.

**Spec:** User request of 29 October 2026: add these three map designs, show a geographic crop in the panel first. Prior feedback requires distinctive drawing systems, not merely different palettes.

## Delivery

- [x] `collection/pipboy.js`: phosphor vector cartography, monochrome silhouettes, CRT treatment.
- [x] `collection/hollow.js`: hand-drawn ivory cartography, hatch marks, deep blue paper and delicate symbols.
- [x] `collection/crossy.js`: tilted block city, real building footprints with illustrative volumes, asphalt and voxel-like icons.
- [x] `novos-mapas.html/css/js`: comparison, focus, crop selection, category/name controls and 2D/3D control for Crossy Road.
- [x] Link the new panel from the existing lab and document its scope and visual references.
- [x] Validate styles, browser rendering, map interactions, desktop/mobile layout and old-panel navigation. Capture actual browser images.

## Review focus

- No native files or package builds; do not run asset preparation scripts.
- Keep real road geometry and direction. No fictional traffic, landmarks or measured heights.
- Image registration and font ranges must work entirely from local resources.
- Loading failures must stay visible, without pretending that a map rendered.
- Controls remain operable in focus mode and on a narrow screen.

**Ruling:** Use a separate collection panel linked into Map Lab. The existing app comparison and native style list must not imply these proposals are installed app themes.

**Verification:** Six day/night styles pass MapLibre validation. Browser QA passed 36/36 checks, including selecting a crop during load, restoring text opacity, 2D/3D, focus, controls, mobile and existing panels. Preserved hidden OSM-building visibility to avoid double rendering. No JS/console/resource errors; cancelled obsolete tile requests were recorded separately. APK SHA256 unchanged. Screenshots and `.runtime/three-map-browser-qa-report.json` contain the evidence.
