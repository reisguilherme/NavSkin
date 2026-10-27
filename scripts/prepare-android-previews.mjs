/** Package true native map captures as offline theme-card previews. */
import { readFile, writeFile, mkdir, copyFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import sharp from 'sharp';

const root = fileURLToPath(new URL('../', import.meta.url));
const source = path.join(root, 'android/maps/previews');
const assets = path.join(root, 'android/app/src/main/assets/maps/previews');
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
await mkdir(source, { recursive: true });
await mkdir(assets, { recursive: true });
const previews = [];
for (const theme of ['gta', 'rosa', 'rdr2']) {
  const relative = `android/screenshots/v0.6/world-sao-paulo-${theme}.png`;
  const input = await readFile(path.join(root, relative));
  const image = sharp(input);
  const { width, height, format } = await image.metadata();
  if (width < 640 || height < 480 || format !== 'png') {
    throw new Error(`Expected an actual native PNG capture for ${theme}`);
  }
  const bytes = await image.resize(800, 480, { fit: 'cover', position: 'centre' }).webp({ quality: 88, effort: 5 }).toBuffer();
  const name = `${theme}.webp`;
  await writeFile(path.join(source, name), bytes);
  await copyFile(path.join(source, name), path.join(assets, name));
  const style = await readFile(path.join(root, `android/app/src/main/assets/maps/styles/world-${theme}.json`));
  previews.push({ theme, file: name, width: 800, height: 480, captureWidth: width, captureHeight: height, bytes: bytes.length, sha256: digest(bytes),
    captureSource: relative, captureSha256: digest(input), styleSha256: digest(style) });
}
const manifest = {
  format: 1,
  kind: 'Native map captures; not Android Auto or DHU screenshots',
  renderer: 'MapLibre Native OpenGL 13.7.1, Android 16/API 36 x86_64, GPU host',
  camera: { longitude: -46.6333, latitude: -23.5505, zoom: 16.8, bearing: 0, tilt: 0 },
  categories: 'All nine enabled; day variants',
  attribution: 'OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors; game symbols/fonts retain their own notices.',
  previews,
};
const json = JSON.stringify(manifest, null, 2) + '\n';
await writeFile(path.join(source, 'index.json'), json);
await writeFile(path.join(assets, 'index.json'), json);
console.log(`Packaged ${previews.length} true native previews (${previews.reduce((n, p) => n + p.bytes, 0)} bytes).`);
