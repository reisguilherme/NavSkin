# Portable original inputs

This folder preserves the six active source fonts, their original notices, the
original GTA/RDR2 sprites used by the importer and the GTA player marker. Paths
in `import-manifest.json` are relative to this directory. The source TTFs and
notices are byte-identical copies of the inputs used for the prepared resources.
Historical notices are retained verbatim; packaging does not change their terms.
The project code license does not cover these fonts or game assets.

The existing prepared resources can be built and edited without rerunning the
font importer. To intentionally regenerate the game inputs, run from the project
root after `npm ci`:

```sh
npm run assets:game
```

A custom manifest is supported with
`node scripts/prepare-game-assets.mjs path/to/import-manifest.json`.
Relative source paths and `fontMaker` resolve against that manifest; absolute
paths in historical manifests remain supported on their original platform.

`font-maker/sdfglyph.mjs` and `sdfglyph.wasm` are the existing copies downloaded
from the [official MapLibre font-maker](https://maplibre.org/font-maker/), with
JavaScript renamed from `sdfglyph.js` to `.mjs`. The converter license is copied
from [its upstream repository](https://github.com/maplibre/font-maker/blob/main/LICENSE.txt).
The cached Noto Sans Bold glyph blocks retain the Noto font license. The supplied
active fonts need no downloads; importing different fonts may fetch additional
Noto blocks into the ignored `.runtime/font-maker-cache/` directory.

`source-manifest.json` records sizes and SHA-256 values for these portable inputs.
Prepared Android/iOS resources remain separate from this editable source folder.
