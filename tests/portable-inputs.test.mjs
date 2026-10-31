import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { createHash } from 'node:crypto';
import { gzipSync } from 'node:zlib';

const root = path.resolve(import.meta.dirname, '..');
const hash = bytes => createHash('sha256').update(bytes).digest('hex');

test('portable game inputs resolve without the original machine runtime or working directory', async () => {
  const { loadGameAssetInputs } = await import('../scripts/game-asset-inputs.mjs');
  const { input, wasmRoot } = await loadGameAssetInputs();
  assert.deepEqual(Object.keys(input.fonts).sort(), ['cyberpunk', 'gta', 'gtaread', 'rdr2map', 'rdr2ui', 'smw']);
  for (const font of Object.values(input.fonts)) {
    assert.equal(hash(await fs.readFile(font.path)), font.sha256);
    assert.ok(font.path.startsWith(path.join(root, 'android/maps/game-assets/source') + path.sep));
    for (const notice of font.licenseFiles) assert.ok((await fs.stat(notice)).size > 0);
  }
  for (const file of ['sdfglyph.mjs', 'sdfglyph.wasm']) assert.ok((await fs.stat(path.join(wasmRoot, file))).size > 0);
  for (const [folder, filenames] of [
    [input.spritesGta, ['radar_dateFood.png', 'radar_hostpital.png', 'radar_impound.png', 'radar_diner.png', 'radar_burgerShot.png', 'radar_dateDrink.png']],
    [input.spritesRdr, ['blip_supplies_food.png', 'blip_shop_doctor.png', 'blip_shop_store.png', 'blip_saloon.png', 'blip_ambient_theatre.png']],
  ]) for (const filename of filenames) assert.ok((await fs.stat(path.join(folder, filename))).size > 0);
});

test('custom importer paths are relative to their manifest and absolute legacy paths still work', async () => {
  const { loadGameAssetInputs } = await import('../scripts/game-asset-inputs.mjs');
  const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'navskin-input-paths-'));
  try {
    const absoluteFont = path.join(temporary, 'absolute.ttf');
    const manifest = path.join(temporary, 'manifest.json');
    await fs.writeFile(manifest, JSON.stringify({ fonts: {
      relative: { path: 'fonts/example.ttf', licenseFiles: ['notices/example.txt'] },
      legacy: { path: absoluteFont, licenseFiles: [] },
    }, spritesGta: 'sprites/gta', spritesRdr: 'sprites/rdr2', fontMaker: 'converter' }));
    const { input, wasmRoot } = await loadGameAssetInputs(manifest);
    assert.equal(input.fonts.relative.path, path.join(temporary, 'fonts/example.ttf'));
    assert.equal(input.fonts.relative.licenseFiles[0], path.join(temporary, 'notices/example.txt'));
    assert.equal(input.fonts.legacy.path, absoluteFont);
    assert.equal(input.spritesGta, path.join(temporary, 'sprites/gta'));
    assert.equal(wasmRoot, path.join(temporary, 'converter'));
  } finally { await fs.rm(temporary, { recursive: true, force: true }); }
});

test('source archive restore verifies content, restores missing files and preserves changed local data', async () => {
  const { restoreSourceData } = await import('../scripts/restore-source-data.mjs');
  const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'navskin-source-restore-'));
  try {
    const original = Buffer.from('{"type":"FeatureCollection","features":[]}\n');
    const compressed = gzipSync(original);
    const archive = path.join(temporary, 'archive');
    await fs.mkdir(archive);
    await fs.writeFile(path.join(archive, 'source.geojson.gz'), compressed);
    const manifestPath = path.join(archive, 'manifest.json');
    await fs.writeFile(manifestPath, JSON.stringify({ schemaVersion: 1, files: [{
      archive: 'source.geojson.gz', originalPath: 'public/data/source.geojson',
      bytes: original.length, sha256: hash(original), compressedBytes: compressed.length, compressedSha256: hash(compressed),
    }] }));
    const outputRoot = path.join(temporary, 'clone');
    assert.equal((await restoreSourceData({ manifestPath, outputRoot, verifyOnly: true }))[0].status, 'verified');
    await assert.rejects(fs.access(outputRoot), { code: 'ENOENT' });
    assert.equal((await restoreSourceData({ manifestPath, outputRoot }))[0].status, 'restored');
    assert.deepEqual(await fs.readFile(path.join(outputRoot, 'public/data/source.geojson')), original);
    assert.equal((await restoreSourceData({ manifestPath, outputRoot }))[0].status, 'unchanged');
    await fs.writeFile(path.join(outputRoot, 'public/data/source.geojson'), 'local edits');
    await assert.rejects(restoreSourceData({ manifestPath, outputRoot }), /different content/);
    assert.equal(await fs.readFile(path.join(outputRoot, 'public/data/source.geojson'), 'utf8'), 'local edits');
    await fs.writeFile(path.join(archive, 'source.geojson.gz'), gzipSync(Buffer.from('corruption')));
    await assert.rejects(restoreSourceData({ manifestPath, outputRoot, verifyOnly: true }), /archive checksum/);
  } finally { await fs.rm(temporary, { recursive: true, force: true }); }
});
