import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { PbfReader } from 'pbf';
import { buildWorldStyle } from '../scripts/prepare-world-maps.mjs';
const root = path.resolve('android/maps/game-assets');
const catalog = JSON.parse(await fs.readFile(path.join(root, 'catalog.json')));
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
  assert.equal(provenance.fonts.length, 3);
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
    assert.notDeepEqual(actual.get(71), noto.get(71), `${alias}: renamed Noto is not an authentic game font`);
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
