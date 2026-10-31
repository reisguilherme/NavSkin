# Full-resolution geography source archive

The two gzip files preserve the exact existing source GeoJSON bytes. Gzip uses
level 6 with a zero timestamp and no filename header. `manifest.json` records the
original destinations, uncompressed/compressed sizes and SHA-256 hashes. The
collection response and extraction state are retained verbatim beside them.

Normal app builds and Map Lab use the prepared tiles/scene and do not need these
large files restored. To regenerate tiles or the Map Lab scene, run from the
project root with Node.js 22.12 or later:

```sh
node scripts/restore-source-data.mjs --verify
node scripts/restore-source-data.mjs
```

Verification decompresses in memory and checks both hashes without writing.
Restoration creates missing original files, skips matching files and refuses to
overwrite different local content. The two restored GeoJSON files remain ignored
by Git; keep the compressed archives for transfer. The original data provenance
and licenses are documented in `docs/buildings.md` and the prepared map credits.
