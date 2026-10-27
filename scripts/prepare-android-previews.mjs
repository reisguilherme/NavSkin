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
  const relative = `android/screenshots/v0.3/native-v3-${theme}.png`;
  const input = await readFile(path.join(root, relative));
  const image = sharp(input);
  const { width, height, format } = await image.metadata();
  if (width !== 800 || height !== 480 || format !== 'png') {
    throw new Error(`Expected an actual 800×480 native PNG capture for ${theme}`);
  }
  const bytes = await image.webp({ quality: 88, effort: 5 }).toBuffer();
  const name = `${theme}.webp`;
  await writeFile(path.join(source, name), bytes);
  await copyFile(path.join(source, name), path.join(assets, name));
  const style = await readFile(path.join(root, `android/app/src/main/assets/maps/styles/${theme}.json`));
  previews.push({ theme, file: name, width, height, bytes: bytes.length, sha256: digest(bytes),
    captureSource: relative, captureSha256: digest(input), styleSha256: digest(style) });
}
const manifest = {
  format: 1,
  kind: 'Native map captures; not Android Auto or DHU screenshots',
  renderer: 'MapLibre Native OpenGL 13.7.1, Android 16/API 36 x86_64, GPU host',
  camera: { longitude: -49.2562748, latitude: -16.6809224, zoom: 14.4, bearing: 0, tilt: 0 },
  categories: 'All six enabled; day variants',
  attribution: '© OpenStreetMap contributors · Overture Maps Foundation · Google Open Buildings · Microsoft Global ML Building Footprints',
  previews,
};
const json = JSON.stringify(manifest, null, 2) + '\n';
await writeFile(path.join(source, 'index.json'), json);
await writeFile(path.join(assets, 'index.json'), json);
console.log(`Packaged ${previews.length} true native previews (${previews.reduce((n, p) => n + p.bytes, 0)} bytes).`);
