import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile, access, cp, mkdtemp, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import sharp from 'sharp';
import { prepareNavigationIcons } from '../scripts/prepare-navigation-icons.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const sourceRoot = path.join(root, 'android/maps/catalogs');
const assetRoot = path.join(root, 'android/app/src/main/assets/navigation-icons');
const savedIds = ['home', 'work', 'favorite', 'restaurant', 'cafe', 'park', 'shopping', 'gym', 'health', 'parking', 'meeting', 'special'];
const vehicleIds = ['arrow', 'compact', 'sport', 'suv', 'motorcycle', 'themed'];
const themes = ['gta', 'rosa', 'rdr2'];
const catalog = async () => JSON.parse(await readFile(path.join(assetRoot, 'catalog.json'), 'utf8'));

test('saved choices and vehicle choices keep stable semantic ids in all three theme catalogs', async () => {
  const data = await catalog();
  assert.equal(data.schemaVersion, 1);
  assert.deepEqual(data.themes, themes);
  assert.deepEqual(data.saved.map(item => item.id), savedIds);
  assert.deepEqual(data.vehicles.map(item => item.id), vehicleIds);
  assert.equal(data.fallbackSaved, 'special');
  assert.equal(data.fallbackVehicle, 'arrow');
  assert.deepEqual(data.vehicleAnchor, [0.5, 0.5]);
  assert.equal(data.vehicleHeading, 'north');
  assert.equal(data.routingProfile, 'automobile');
  for (const [kind, entries] of [['saved', data.saved], ['vehicles', data.vehicles]]) {
    for (const item of entries) {
      assert.ok(item.label.trim(), `${item.id} needs a visible label`);
      assert.deepEqual(Object.keys(item.themeAssets), themes);
      for (const theme of themes) {
        const asset = item.themeAssets[theme];
        assert.equal(asset.image, `${theme}/${kind}/${item.id}.png`);
        assert.equal(asset.preview, `${theme}/${kind}/${item.id}@2x.png`);
        assert.equal(asset.svg, `${theme}/${kind}/${item.id}.svg`);
        for (const relativePath of Object.values(asset)) {
          assert.ok(!relativePath.startsWith('/') && !relativePath.includes('..'), 'asset paths must stay in the offline catalog');
          await access(path.join(assetRoot, relativePath));
        }
      }
    }
  }
});

test('every runtime image has editable local SVG source and distinct visible transparent pixels', async () => {
  const data = await catalog();
  const hashes = new Set();
  for (const [kind, entries] of [['saved', data.saved], ['vehicles', data.vehicles]]) {
    for (const item of entries) {
      for (const theme of themes) {
        const { image, preview, svg } = item.themeAssets[theme];
        const original = await readFile(path.join(sourceRoot, svg), 'utf8');
        assert.equal(await readFile(path.join(assetRoot, svg), 'utf8'), original);
        assert.match(original, /viewBox="0 0 64 64"/);
        assert.ok(!/<(?:image|text|foreignObject)\b|https?:\/\/(?!www\.w3\.org\/2000\/svg)/.test(original), `${svg} must use original offline vector geometry`);
        for (const [asset, side] of [[image, 48], [preview, 96]]) {
          const png = sharp(await readFile(path.join(assetRoot, asset)));
          const meta = await png.metadata();
          assert.equal(meta.width, side, asset);
          assert.equal(meta.height, side, asset);
          assert.equal(meta.hasAlpha, true, asset);
          const { data: pixels, info } = await png.ensureAlpha().raw().toBuffer({ resolveWithObject: true });
          let visible = 0;
          let minX = side; let minY = side; let maxX = -1; let maxY = -1;
          for (let y = 0; y < side; y++) {
            for (let x = 0; x < side; x++) {
              if (pixels[(y * side + x) * info.channels + 3] > 16) {
                visible++; minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
              }
            }
          }
          assert.ok(visible > side * side * 0.09, `${asset} must be legible at map size`);
          assert.ok(visible < side * side * 0.75, `${asset} must retain a transparent margin`);
          assert.ok(minX > 0 && minY > 0 && maxX < side - 1 && maxY < side - 1, `${asset} is clipped`);
          if (kind === 'vehicles') {
            assert.ok(Math.abs((minX + maxX) / 2 - (side - 1) / 2) <= side * 0.045, `${asset} must remain horizontally centered for rotation`);
            assert.ok(Math.abs((minY + maxY) / 2 - (side - 1) / 2) <= side * 0.06, `${asset} must remain vertically centered for rotation`);
          }
          if (side === 48) {
            const hash = createHash('sha256').update(pixels).digest('hex');
            assert.ok(!hashes.has(hash), `${asset} must have a real theme-specific preview, not a duplicate`);
            hashes.add(hash);
          }
        }
      }
    }
  }
  assert.equal(hashes.size, 54);
});

test('raster builder rejects external SVG references before emitting runtime files', async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), 'gamemap-navigation-icons-'));
  try {
    const input = path.join(temporary, 'source');
    const output = path.join(temporary, 'output');
    await cp(sourceRoot, input, { recursive: true });
    const file = path.join(input, 'gta/saved/home.svg');
    const svg = await readFile(file, 'utf8');
    await writeFile(file, svg.replace('href="#art"', 'href="file:///outside-catalog.svg"'));
    await assert.rejects(prepareNavigationIcons({ sourceRoot: input, outputRoot: output }), /standalone/);
    await assert.rejects(access(path.join(output, 'catalog.json')), { code: 'ENOENT' });
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});

test('raster builder uses an edited SVG without overwriting the source or changing its semantic choice', async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), 'gamemap-navigation-edit-'));
  try {
    const input = path.join(temporary, 'source');
    const output = path.join(temporary, 'output');
    await cp(sourceRoot, input, { recursive: true });
    const sourceFile = path.join(input, 'gta/saved/home.svg');
    const original = await readFile(sourceFile, 'utf8');
    const edited = original.replace(/fill="#[a-fA-F0-9]{6}"/, 'fill="#00ffff"');
    assert.notEqual(edited, original);
    await writeFile(sourceFile, edited);
    const built = await prepareNavigationIcons({ sourceRoot: input, outputRoot: output });
    assert.equal(await readFile(sourceFile, 'utf8'), edited);
    assert.equal(await readFile(path.join(output, 'gta/saved/home.svg'), 'utf8'), edited);
    assert.equal(built.saved.find(item => item.id === 'home').themeAssets.gta.image, 'gta/saved/home.png');
    assert.notDeepEqual(await readFile(path.join(output, 'gta/saved/home.png')), await readFile(path.join(assetRoot, 'gta/saved/home.png')));
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});
