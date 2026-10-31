/** Package real renderer captures; preserve per-image browser/native provenance. */
import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import sharp from 'sharp';
import { MAP_THEMES } from './prepare-world-maps.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = path.join(root, 'android/maps/previews');
const assets = path.join(root, 'android/app/src/main/assets/maps/previews');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
await mkdir(source, { recursive: true });
await mkdir(assets, { recursive: true });
const previews = [];
const browserCameras = JSON.parse(await readFile(path.join(source, 'captures/cameras.json'), 'utf8'));
for (const theme of Object.keys(MAP_THEMES)) {
  const collection = ['pipboy', 'hollow', 'crossy'].includes(theme);
  const browser = true;
  const relative = `android/maps/previews/captures/${theme}.png`;
  const input = await readFile(path.join(root, relative));
  const image = sharp(input);
  const { width, height, format } = await image.metadata();
  if (width < 640 || height < 480 || format !== 'png') {
    throw new Error(`Expected an actual renderer PNG capture for ${theme}`);
  }
  const bytes = await image.resize(800, 480, { fit: 'cover', position: 'centre' }).webp({ quality: 88, effort: 5 }).toBuffer();
  const name = `${theme}.webp`;
  await writeFile(path.join(source, name), bytes);
  await copyFile(path.join(source, name), path.join(assets, name));
  const style = await readFile(path.join(root, `android/app/src/main/assets/maps/styles/world-${theme}.json`));
  previews.push({ theme, file: name, width: 800, height: 480, captureWidth: width, captureHeight: height, bytes: bytes.length, sha256: digest(bytes),
    captureSource: relative, captureSha256: digest(input), styleSha256: digest(style),
    renderer: browser ? 'MapLibre GL JS browser; approved collection artwork, not a native validation screenshot' : 'MapLibre Native OpenGL 13.7.1, Android 16/API 36 x86_64, GPU host',
    camera: browser ? browserCameras[theme] : { center: [-46.6333, -23.5505], zoom: 16.8, pitch: 0, bearing: 0 },
    ...(browser ? { captureStyle: collection ? `prototypes/navskin-map-lab/collection/${theme}.js` : `android/app/src/main/assets/maps/styles/${theme}.json`, coverage: 'Goiânia',
      sourceArtSha256: digest(await readFile(path.join(root, `scripts/theme-art/${collection ? theme : 'five-worlds'}.mjs`))),
      note: 'Preview depicts the approved source artwork. Runtime style hash identifies the matching promoted pack; the capture does not establish Native rendering parity.' } : {}),
  });
}
const manifest = {
  format: 1,
  kind: 'Actual map captures; per-image Native/browser renderer provenance; not Android Auto or DHU screenshots',
  renderer: 'See each preview renderer field',
  camera: 'Per-image camera; approved production drawings and collection views in Goiânia',
  categories: 'All nine enabled; day variants',
  attribution: 'OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors; game symbols/fonts retain their own notices.',
  previews,
};
const json = JSON.stringify(manifest, null, 2) + '\n';
await writeFile(path.join(source, 'index.json'), json);
await writeFile(path.join(assets, 'index.json'), json);
console.log(`Packaged ${previews.length} renderer previews (${previews.reduce((n, p) => n + p.bytes, 0)} bytes).`);
