# Approved artwork in the v0.11 app

The APK now includes all eight approved themes. GTA SA, Pink World, RDR2 and Cyberpunk use the drawing studies previously confined to the HTML lab: footprint shadows and roof contours, paper or technical materials, park edges, and revised road drawing. Super Mario World retains its approved overworld art and corrected east-facing oneway sprite. Pip-Boy, Hollow Knight and Crossy Road retain their approved collection drawings.

The production first-five source is `scripts/theme-art/five-worlds.mjs`; `scripts/approved-map-art.mjs` adapts its styles for native/world schemas. Text fonts, real feature filters, grades and geographic sources are retained. Offline GeoJSON areas use solid colors and contours because patterned fill buckets can disappear during MapLibre Native theme changes. Vector roofs, background materials and world area materials remain textured. Building volumes use the approved flat roof color where a theme has no specific volumetric palette.

Pink World uses nine toy-city POI drawings plus matching home, work and favorite choices. Their canonical SVGs are in `scripts/theme-art/pink-icons`, reproduced by `scripts/prepare-pink-assets.mjs`. Subtypes retain their original OSM selectors and use the appropriate toy-category image. The `rosa/vehicles/themed` choice is the approved north-facing Hello Kitty toy convertible, drawn as editable SVG from the user's reference; it is a character adaptation, not an official imported game sprite. Sanrio character rights are excluded from the project's CC0 artwork dedication, as recorded in the navigation provenance and license.

Run `npm run assets:collection` to regenerate the complete package. Preview cards use actual MapLibre browser captures of the promoted production drawings in Goiânia. Capture origin, source hashes and camera are retained per image; browser screenshots do not establish native-renderer parity.

The HTML map panel detects production drawings already present in the style and avoids applying the pass twice. The original frame/simulation controls remain in the lab; the previews packaged for theme cards contain only the map.
