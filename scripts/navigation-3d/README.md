# Navigation avatars

Eight original low-poly models inspired by the selected game worlds. They are authored triangle meshes, not extracted game assets. Character/franchise names and designs belong to their respective owners.

| Theme | Avatar |
|---|---|
| GTA SA | CJ riding a red BMX, white vest and blue jeans |
| Pink World | White Hello Kitty convertible, sculpted hood, pink bow and open cockpit |
| RDR2 | Brown horse with saddle, blanket roll, reins and saddlebags |
| Super Mario World | Yoshi, green body, orange shoes and red saddle |
| Cyberpunk | Red Kusanagi-inspired motorcycle |
| Pip-Boy | Vault Boy, blue/yellow suit, Pip-Boy and raised thumb |
| Hollow Knight | The Knight, horned mask, dark cloak and nail |
| Crossy Road | White voxel chicken, red comb and yellow beak |

`models.mjs` contains the editable model construction. `geometry.mjs` provides primitive triangle meshes and OBJ/MTL export. The `models/` directory contains a portable JSON mesh and editable Wavefront OBJ/MTL files for each character, plus a contact sheet showing navigation, three-quarter, side, front and top views.

Regenerate the runtime catalog from the project root:

```sh
node scripts/prepare-navigation-3d.mjs
node --test tests/navigation-3d-assets.test.mjs
```

The rasterizer uses an orthographic camera, directional flat lighting, a real per-pixel depth buffer and 2× supersampling. Its transparent sprites are baked ahead of time. There is no mesh rasterization in the mobile UI thread. The runtime is a multiview billboard, not a realtime native 3D model renderer.

Each theme has 32 heading views at three camera tilts: 0°, 40° and 55°. Images are 192×192 pixels, displayed in a 72-point box. The fixed ground anchor is 50% across and 70% down. Model +Y is forward/north and +Z is up. Sprite yaw is clockwise `vehicle heading − map camera bearing`; view 0 shows the back of the avatar as it drives toward the top of the screen. The rendered silhouette is fitted consistently across all headings within each tilt, and its ground anchor never moves.

`renderModelPixels(model, { yaw, pitch, size, supersample })` returns a raw RGBA array, width and height. `renderModelSVG` is a vector diagnostic projection; use the depth-buffered PNGs for production because intersecting model parts require correct occlusion. The source JSON shape is `{ label, vertices: [[x, y, z]], faces: [{ indices: [a, b, c], color }] }`.

The output catalog and its provenance live in `android/app/src/main/assets/navigation-3d/`. Shared preparation copies that folder to iOS. Saved-place icons and the six manually selectable browsing vehicle icons are separate and remain intact.
