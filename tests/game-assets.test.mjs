import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { PbfReader } from 'pbf';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import os from 'node:os';
import { buildWorldStyle } from '../scripts/prepare-world-maps.mjs';
const root = path.resolve('android/maps/game-assets');
const catalog = JSON.parse(await fs.readFile(path.join(root, 'catalog.json')));

test('legacy font manifest cannot silently replace the current theme pack', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'navskin-font-manifest-'));
  const before = await fs.readFile(path.join(root, 'catalog.json'));
  try {
    const manifest = path.join(directory, 'old.json');
    await fs.writeFile(manifest, JSON.stringify({ fonts: { gta: {}, rdr2ui: {}, rdr2map: {} } }));
    await assert.rejects(promisify(execFile)(process.execPath, ['scripts/prepare-game-assets.mjs', manifest]),
      error => /expanded six-font import manifest/.test(error.stderr));
    assert.deepEqual(await fs.readFile(path.join(root, 'catalog.json')), before);
  } finally { await fs.rm(directory, { recursive: true, force: true }); }
});
function glyphs(bytes) {
  const result = new Map();
  new PbfReader(bytes).readFields((tag, _, p) => {
    if (tag === 1) p.readMessage((field, __, q) => {
      if (field === 3) { const raw = q.readBytes(); let id; new PbfReader(raw).readFields((t, ___, r) => { if (t === 1) id = r.readVarint(); }, {}); result.set(id, Buffer.from(raw)); }
    }, {});
  }, {});
  return result;
}
test('game UI files retain exact source hashes and notices instead of renamed system fonts', async () => {
  const provenance = JSON.parse(await fs.readFile(path.join(root, 'provenance.json')));
  assert.equal(provenance.fonts.length, 6);
  for (const entry of provenance.fonts) {
    const bytes = await fs.readFile(path.join(root, 'runtime', entry.file));
    assert.equal(createHash('sha256').update(bytes).digest('hex'), entry.sha256);
    assert.equal(bytes.readUInt32BE(0), 0x00010000, 'actual TrueType header');
    for (const notice of entry.notices) assert.ok((await fs.stat(path.join(root, 'runtime', notice))).size > 0);
    assert.ok(entry.sourceUrl.startsWith('https://'));
  }
  assert.equal(catalog.themes.gta.ui.postScriptName, 'Pricedown');
  assert.equal(catalog.themes.rdr2.ui.postScriptName, 'HapnaSlabSerif-DemiBold');
});
test('map glyphs have real game outlines, Portuguese accents and fallback glyph coverage', async () => {
  const noto = glyphs(await fs.readFile('android/maps/fonts/GameMapSans/0-255.pbf'));
  for (const alias of [...new Set(Object.values(catalog.themes).flatMap(theme => Object.values(theme.map)))]) {
    const actual = glyphs(await fs.readFile(path.join(root, 'glyphs', alias, '0-255.pbf')));
    for (const char of 'NavSkinGoiâniaSãoPauloFarmáciaÁÉÇãéç0123456789') assert.ok(actual.has(char.codePointAt(0)), `${alias}: missing ${char}`);
    if (alias === 'GameMapSans') assert.deepEqual(actual.get(71), noto.get(71), 'Crossy explicitly uses the approved Noto fallback, not a claimed game font');
    else assert.notDeepEqual(actual.get(71), noto.get(71), `${alias}: renamed Noto is not an authentic game font`);
  }
});
test('original sprites carry game names and copyrights while missing categories are explicit adaptations', async () => {
  const provenance = JSON.parse(await fs.readFile(path.join(root, 'provenance.json')));
  assert.ok(provenance.sprites.some(sprite => sprite.originalName === 'radar_hostpital.png'));
  assert.ok(provenance.sprites.some(sprite => sprite.originalName === 'blip_shop_doctor.png'));
  for (const sprite of provenance.sprites) {
    const svg = await fs.readFile(`android/maps/icons/${sprite.theme}/${sprite.id}.svg`, 'utf8');
    assert.ok(svg.includes(sprite.originalName)); assert.ok(svg.includes('copyright Rockstar'));
    assert.ok(svg.includes('data:image/png;base64,'));
  }
  const park = await fs.readFile('android/maps/icons/gta/poi-parks.svg', 'utf8');
  assert.ok(park.includes('original adaptation'));
});
test('GTA street names have placement priority over discretionary POI captions and direction hints', () => {
  const style = buildWorldStyle('gta');
  assert.equal(style.layers.at(-1).id, 'road-labels');
  assert.ok(style.layers.at(-1).layout['symbol-spacing'] <= 180);
});

test('GTA readable labels use a separate font face with open spacing and restrained outlines', async () => {
  const style = buildWorldStyle('gta');
  const road = style.layers.find(layer => layer.id === 'road-labels');
  assert.deepEqual(road.layout['text-font'], ['NavSkinGtaRoad']);
  assert.equal(road.layout['text-transform'], 'none');
  assert.ok(road.layout['text-letter-spacing'] <= 0.05);
  assert.ok(road.paint['text-halo-width'] <= 1.25);
  const poi = style.layers.find(layer => layer.id.startsWith('poi-') && layer.layout?.['text-field']);
  assert.deepEqual(poi.layout['text-font'], ['NavSkinGtaRoad']);
  const roadGlyphs = glyphs(await fs.readFile(path.join(root, 'glyphs/NavSkinGtaRoad/0-255.pbf')));
  const display = glyphs(await fs.readFile(path.join(root, 'glyphs/NavSkinGtaDisplay/0-255.pbf')));
  assert.notDeepEqual(roadGlyphs.get(97), display.get(97), 'road font must not reuse decorative Pricedown outlines');
});

test('each new map has a sourced local font family and fallback glyphs for Portuguese', async () => {
  for (const id of ['smw', 'cyberpunk']) {
    const profile = catalog.themes[id];
    assert.ok(profile?.ui?.postScriptName, `${id} missing local font profile`);
    for (const role of ['road', 'poi', 'place']) assert.ok(profile.map[role].startsWith('NavSkin'));
    assert.ok((await fs.stat(path.join(root, 'runtime', profile.ui.file.replace(/^game-assets\//, '')))).size > 1000);
  }
});
