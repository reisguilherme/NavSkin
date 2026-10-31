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
const themes = ['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk', 'pipboy', 'hollow', 'crossy'];
const catalog = async () => JSON.parse(await readFile(path.join(assetRoot, 'catalog.json'), 'utf8'));

test('saved choices and vehicle choices keep stable semantic ids in all eight theme catalogs', async () => {
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
        for (const relativePath of [asset.image, asset.preview, asset.svg, ...(asset.rasterSource ? [asset.rasterSource.file] : [])]) {
          assert.ok(!relativePath.startsWith('/') && !relativePath.includes('..'), 'asset paths must stay in the offline catalog');
          await access(path.join(assetRoot, relativePath));
        }
      }
    }
  }
});

test('every runtime image has a local source and distinct visible transparent pixels', async () => {
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
        const rasterSource = item.themeAssets[theme].rasterSource;
        if (rasterSource) {
          assert.equal(`${theme}/${kind}/${item.id}`, 'gta/vehicles/arrow');
          const source = await readFile(path.join(sourceRoot, rasterSource.file));
          assert.equal(createHash('sha256').update(source).digest('hex'), rasterSource.sha256);
          assert.deepEqual(await readFile(path.join(assetRoot, rasterSource.file)), source);
        }
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
  assert.equal(hashes.size, 144);
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

test('GTA player raster overrides only its PNGs and preserves original pixels, source and provenance', async () => {
  const temporary = await mkdtemp(path.join(os.tmpdir(), 'gamemap-original-player-'));
  try {
    const input = path.join(temporary, 'source');
    const output = path.join(temporary, 'output');
    await cp(sourceRoot, input, { recursive: true });
    const data = JSON.parse(await readFile(path.join(input, 'catalog.json'), 'utf8'));
    // A tiny controlled raster makes padding, nearest scaling and recoloring observable.
    const pixels = Buffer.alloc(16 * 16 * 4);
    for (let y = 0; y < 16; y++) for (let x = 4; x < 12; x++) {
      pixels.set(x === 4 || x === 11 ? [0, 0, 0, 255] : [255, 255, 255, 255], (y * 16 + x) * 4);
    }
    const original = await sharp(pixels, { raw: { width: 16, height: 16, channels: 4 } }).png().toBuffer();
    const source = 'gta/vehicles/arrow-original.png';
    const sha256 = createHash('sha256').update(original).digest('hex');
    const rasterSource = {
      file: source, sha256, width: 16, height: 16, padding: 4,
      originalDictionary: 'models/hud.txd', originalTexture: 'radar_centre',
      sourceUrl: 'https://gtamidori.com/textdrawsprites/hud.txd/radar_centre.png',
      copyright: 'Rockstar Games', licenseStatus: 'Original game artwork; not CC0',
    };
    data.vehicles.find(item => item.id === 'arrow').themeAssets.gta.rasterSource = rasterSource;
    await writeFile(path.join(input, source), original);
    await writeFile(path.join(input, 'catalog.json'), JSON.stringify(data));
    const built = await prepareNavigationIcons({ sourceRoot: input, outputRoot: output });
    assert.deepEqual(built.vehicles.map(item => item.id), vehicleIds);
    const firstOutput = await sharp(path.join(output, 'gta/vehicles/arrow.png')).ensureAlpha().raw().toBuffer();
    assert.deepEqual(firstOutput.subarray((8 * 48 + 16) * 4, (8 * 48 + 17) * 4), Buffer.from([0, 0, 0, 255]), 'the raster source must replace the SVG output');
    assert.deepEqual(await readFile(path.join(output, source)), original);
    const provenance = JSON.parse(await readFile(path.join(output, 'provenance.json'), 'utf8'));
    assert.equal(provenance.importedSprites[0].sha256, sha256);
    assert.equal(provenance.importedSprites[0].originalTexture, 'radar_centre');
    assert.equal(provenance.importedSprites[0].resizeKernel, 'nearest');
    for (const [suffix, side, scale] of [['', 48, 2], ['@2x', 96, 4]]) {
      const { data: actual, info } = await sharp(path.join(output, `gta/vehicles/arrow${suffix}.png`)).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
      assert.equal(info.width, side);
      assert.equal(info.height, side);
      for (let y = 0; y < side; y++) for (let x = 0; x < side; x++) {
        const sourceX = Math.floor(x / scale) - 4;
        const sourceY = Math.floor(y / scale) - 4;
        const expected = sourceX >= 0 && sourceX < 16 && sourceY >= 0 && sourceY < 16
          ? pixels.subarray((sourceY * 16 + sourceX) * 4, (sourceY * 16 + sourceX + 1) * 4) : Buffer.alloc(4);
        assert.deepEqual(actual.subarray((y * side + x) * 4, (y * side + x + 1) * 4), expected);
      }
    }
    for (const kind of ['saved', 'vehicles']) for (const entry of data[kind]) for (const theme of themes) {
      if (kind === 'vehicles' && entry.id === 'arrow' && theme === 'gta') continue;
      for (const key of ['image', 'preview']) {
        const relative = entry.themeAssets[theme][key];
        assert.deepEqual(await readFile(path.join(output, relative)), await readFile(path.join(assetRoot, relative)), `${relative} must be unchanged`);
      }
    }
    // Source verification must happen before the first output is emitted.
    rasterSource.sha256 = '0'.repeat(64);
    await writeFile(path.join(input, 'catalog.json'), JSON.stringify(data));
    const invalidOutput = path.join(temporary, 'invalid-output');
    await assert.rejects(prepareNavigationIcons({ sourceRoot: input, outputRoot: invalidOutput }), /checksum/i);
    await assert.rejects(access(path.join(invalidOutput, 'catalog.json')), { code: 'ENOENT' });
    rasterSource.sha256 = sha256;
    rasterSource.file = '../outside.png';
    await writeFile(path.join(input, 'catalog.json'), JSON.stringify(data));
    await assert.rejects(prepareNavigationIcons({ sourceRoot: input, outputRoot: invalidOutput }), /raster.*source/i);
    rasterSource.file = source;
    data.vehicles.find(item => item.id === 'compact').themeAssets.gta.rasterSource = rasterSource;
    await writeFile(path.join(input, 'catalog.json'), JSON.stringify(data));
    await assert.rejects(prepareNavigationIcons({ sourceRoot: input, outputRoot: invalidOutput }), /only.*gta.*arrow/i);
  } finally {
    await rm(temporary, { recursive: true, force: true });
  }
});
