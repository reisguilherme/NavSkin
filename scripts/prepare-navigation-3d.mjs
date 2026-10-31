import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { models } from './navigation-3d/models.mjs';
import { toOBJ } from './navigation-3d/geometry.mjs';
import { renderModelPixels } from './navigation-3d/renderer.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'android/app/src/main/assets/navigation-3d');
const modelOutput = path.join(root, 'scripts/navigation-3d/models');
const pitches = [0, 40, 55];
const count = 32;

export async function prepareNavigation3D() {
  await fs.mkdir(output, { recursive: true });
  await fs.mkdir(modelOutput, { recursive: true });
  const catalog = { schemaVersion: 1, frameCount: count, pitches, imageSize: 192, displaySize: 72, anchor: [.5, .7], themes: {} };
  const previews = [];
  for (const [theme, model] of Object.entries(models)) {
    const frames = [];
    for (const pitch of pitches) {
      const row = [];
      await fs.mkdir(path.join(output, theme, `p${pitch}`), { recursive: true });
      // Serial bounds rasterization memory. No frame or texture work is done on the mobile UI thread.
      for (let angle = 0; angle < count; angle++) {
        const relative = `${theme}/p${pitch}/a${String(angle).padStart(2, '0')}.png`;
        const pixels = renderModelPixels(model, { yaw: angle * 360 / count, pitch, size: 192 });
        await sharp(pixels.data, { raw: { width: pixels.width, height: pixels.height, channels: 4 } }).resize(192, 192).png({ compressionLevel: 9 }).toFile(path.join(output, relative));
        row.push(relative);
      }
      frames.push(row);
    }
    catalog.themes[theme] = { label: model.label, frames };
    const directory = path.join(modelOutput, theme);
    await fs.mkdir(directory, { recursive: true });
    const { obj, mtl } = toOBJ(model);
    await fs.writeFile(path.join(directory, 'model.obj'), obj);
    await fs.writeFile(path.join(directory, 'model.mtl'), mtl);
    await fs.writeFile(path.join(directory, 'model.json'), JSON.stringify(model));
    previews.push({ theme, label: model.label, frames: [frames[2][0], frames[2][4], frames[2][8], frames[2][16], frames[0][0]] });
    console.log(`${theme}: ${model.faces.length} triangles, ${count * pitches.length} views`);
  }
  await fs.writeFile(path.join(output, 'catalog.json'), `${JSON.stringify(catalog, null, 2)}\n`);
  await fs.writeFile(path.join(output, 'provenance.json'), `${JSON.stringify({
    schemaVersion: 1,
    technique: 'Original low-poly triangle meshes, orthographic multi-angle renders; no original game model extraction.',
    sources: ['scripts/navigation-3d/models.mjs', 'scripts/navigation-3d/geometry.mjs', 'scripts/navigation-3d/renderer.mjs'],
    coordinateSystem: { forward: '+Y (north)', up: '+Z', yaw: 'clockwise heading minus camera bearing', pitch: 'map tilt from top-down' },
    groundAnchor: [.5, .7],
    themes: Object.fromEntries(Object.entries(models).map(([theme, model]) => [theme, {
      label: model.label,
      source: `scripts/navigation-3d/models/${theme}/model.obj`,
      inspiration: { gta: 'Grand Theft Auto: San Andreas — CJ and BMX', rosa: 'Hello Kitty toy convertible, user reference photo and approved vehicle.svg', rdr2: 'Red Dead Redemption 2 — saddled horse with frontier tack', smw: 'Super Mario World — Yoshi', cyberpunk: 'Cyberpunk 2077 — Yaiba Kusanagi motorcycle', pipboy: 'Fallout — Vault Boy', hollow: 'Hollow Knight — the Knight', crossy: 'Crossy Road — voxel chicken' }[theme],
      triangles: model.faces.length,
    }])),
    note: 'Authored fan-inspired assets. Character and franchise names belong to their respective owners. These files are not official game assets.',
  }, null, 2)}\n`);
  // Contact sheet stays beside editable sources rather than increasing the runtime bundle.
  const width = 1120, height = previews.length * 224;
  const labels = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="#18202d"/>${previews.map((item, row) => `<rect x="8" y="${row * 224 + 8}" width="1104" height="208" rx="16" fill="${row % 2 ? '#263043' : '#222c3b'}"/><text x="24" y="${row * 224 + 30}" font-family="Arial" font-size="17" fill="#f5f2ef">${item.label}</text>${['Navegação', '3/4', 'Lateral', 'Frente', '2D'].map((label, column) => `<text x="${column * 220 + 27}" y="${row * 224 + 204}" font-family="Arial" font-size="13" fill="#b7c5db">${label}</text>`).join('')}`).join('')}</svg>`;
  const composites = [];
  for (let row = 0; row < previews.length; row++) for (let column = 0; column < 5; column++) {
    composites.push({ input: await fs.readFile(path.join(output, previews[row].frames[column])), left: column * 220 + 10, top: row * 224 + 19 });
  }
  await sharp(Buffer.from(labels)).composite(composites).png().toFile(path.join(modelOutput, 'preview-sheet.png'));
  console.log(`Baked ${Object.keys(models).length * count * pitches.length} transparent sprites → navigation-3d/catalog.json`);
  return catalog;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await prepareNavigation3D();
