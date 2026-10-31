# NavSkin map laboratory panels

**Goal:** Provide desktop HTML studies of the five active maps over Goiânia, strengthen their drawing language, and preview translucent 3D navigation. The user explicitly requests panels only; native apps and APKs must remain unchanged.

**Architecture:** An isolated `prototypes/navskin-map-lab/` folder with a local HTTP server. MapLibre GL JS uses the existing bundled map resources read-only. Browser-only style transformations produce proposals; a small real-footprint dataset supports illustrative 3D buildings. No map, route or traffic API is called.

**Design:** Mario is the approved reference. GTA SA uses bold radar cartography, RDR2 ink/engraving on parchment, Rosa Pop layered paper/diorama, Cyberpunk a tactical architectural plan. Preserve actual street/area coordinates and distinguish simulated building heights and trip metrics. Minecraft and GTA V remain excluded.

## Deliverables

- [x] Five themed HTML entry points, map inspection panel, current/proposal comparison with synchronized camera.
- [x] Navigation panel with gentle pitch, translucent volumes, edge detail, route replay and live visual controls.
- [x] Local server and Windows launcher, concise README, no network/API key requirement.
- [x] Browser visual and interaction checks, real screenshots, APK/native resource integrity check.

## Ownership

Root: HTML, CSS, application controller, map adapter, server, launcher and verification.
Art agent: `theme-designs.js` only; exports `THEMES`, `createThemeStyle`, `getThemeImages`.
Navigation agent: `navigation-scene.js`, `prepare-scene.mjs`, generated prototype data; exports `NavigationScene` with install/options/replay/cleanup methods.

This is a reviewable prototype, not a native release. No Gradle/Xcode build, mobile source change, shared resource generator, dependency install or new API integration is part of this step.
