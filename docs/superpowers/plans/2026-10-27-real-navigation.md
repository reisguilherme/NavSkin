# Real GPS and navigation implementation plan

> **For agentic workers:** Implement the five requested increments in this workspace, preserving the working app. The user's detailed brief explicitly authorizes implementation, including GPS permissions and navigation. Parallelize independent core, provider, assets and persistence work; root coordinates integration and verification.

**Goal:** Real Android GPS, destination selection, automobile routes, visual navigation/ETA/speed/rerouting, saved places and per-theme catalogs, shared with Android Auto.

**Spec:** `docs/navigation-brief.txt`. Existing Kotlin/MapLibre/CAL app and GTA SA, Rosa Pop **and RDR2** remain. No voice, CarPlay, account/cloud-history backend or fabricated traffic/speed limits.

**Architecture:** One application-owned NavigationRuntime and location foreground service; phone/car observe snapshots and issue commands. Geoapify calculates routes/search; a pure Kotlin engine matches live positions, computes remaining step-duration ETA and detects arrival/deviation. MapLibre receives independent dynamic overlay sources, never a new full style for every GPS fix. Local stores retain semantic icon IDs, saved places, recents and a destination-only recovery prompt.

**External choice:** User approved Geoapify and will enter a personal key in the app. Free production plan and terms researched in `docs/navigation-provider-decision.md`. No key is bundled or obtained from public examples. Network integration without an entered key must be explicitly unavailable, with no demo-server fallback.

## Incremental work

- [x] GPS, quality, heading, filtered speed, permissions, foreground continuity and independent follow/manual/overview cameras.
- [x] HTTPS provider adapter, configured-key storage, address/POI search, map selection, route preview and explicit online errors.
- [x] Pure route-progress engine, visual maneuvers, duration-based ETA, stale/weak GPS, generation-safe recalculation and arrival.
- [x] Local favorites/home/work/recents and 12 saved-place icons +6 vehicle markers per theme, with real selectable previews.
- [x] CAL navigation category/templates, NavigationManager lifecycle, allowed destination/settings flows and speed badge; APK/evidence/testing guide.

Implementation and local verification are delivered in v0.4. Authenticated Geoapify validation requires the user's key; DHU/physical-car and locked-screen authenticated-route verification require the corresponding phone/host. These external validation steps remain pending and are itemized in `android/docs/VALIDATION-v0.4.md`; checkbox completion does not claim them.

## Shared contracts and ownership

- `navigation/model/NavModels.kt`, `navigation/core/`: pure models/engine, core worker.
- `navigation/NavigationState.kt`: snapshots/preferences/phases, root.
- `navigation/provider/`: blocking injectable Geoapify HTTPS adapter, provider worker.
- `navigation/storage/`: favorites/recents/resume and settings/key vault/catalog reader, storage worker.
- `navigation/NavigationRuntime.kt`, `navigation/NavigationService.kt`, `navigation/location/`: one live session and Android location source, core/runtime worker after pure engine.
- `render/`, phone navigation controls, Application/manifest/build wiring: root.
- Car templates/bridge: car worker after runtime contract is available.
- Editable catalog sources/build/tests: icon worker.

## Verification and boundary

Use deterministic unit fixtures for crossings, loops, parallel roads, stale fixes, weak GPS, noisy deviation, cooldown, out-of-order route responses, ETA, arrival and persistence. Development replay is visibly labeled **SIMULAÇÃO** on phone and car and is separate from normal GPS; it is evidence of the state machine, not real-world routing or driving.

Compile with the actual pinned Android/CAL APIs, inspect navigation overlays and catalogs on the GPU emulator, verify permission denial/recovery and process-recovery prompt, package/sign/install the versioned APK. Live Geoapify validation depends on the user's key. DHU and physical car tests require the corresponding phone/host and must be reported separately. Car-only cold foreground-service starts must handle Android restrictions with a phone-activation fallback, not silently claim success or request unnecessary permanent location.
