/** Import a locally obtained game-asset manifest and prepare real font glyphs.
 * The original notices are retained. This script does not grant redistribution rights.
 * MapLibre's official WASM converter must be in .runtime/font-maker (see docs).
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { PbfReader, PbfWriter } from 'pbf';
import sharp from 'sharp';

const root = path.resolve(import.meta.dirname, '..');
const input = JSON.parse(await fs.readFile(path.resolve(process.argv[2] ?? '.runtime/font-research/import-manifest.json'), 'utf8'));
const output = path.join(root, 'android/maps/game-assets');
const hash = data => createHash('sha256').update(data).digest('hex');
const json = async (file, data) => { await fs.mkdir(path.dirname(file), { recursive: true }); await fs.writeFile(file, JSON.stringify(data, null, 2) + '\n'); };
const wasmRoot = path.join(root, '.runtime/font-maker');
const tool = await fs.readFile(path.join(wasmRoot, 'sdfglyph.mjs'), 'utf8');
const wasm = await new Promise(async (resolve, reject) => {
  try {
    const options = { wasmBinary: await fs.readFile(path.join(wasmRoot, 'sdfglyph.wasm')), onAbort: reject };
    options.onRuntimeInitialized = () => resolve(result);
    const run = new Function('Module', 'require', 'module', '__filename', '__dirname', tool + ';return Module;');
    const result = run(options, createRequire(path.join(wasmRoot, 'runner.cjs')), { exports: {} }, path.join(wasmRoot, 'sdfglyph.mjs'), wasmRoot);
  } catch (error) { reject(error); }
});

export function decodeGlyphs(data) {
  const glyphs = new Map();
  new PbfReader(data).readFields((tag, _, pbf) => {
    if (tag !== 1) return;
    pbf.readMessage((field, __, stack) => {
      if (field !== 3) return;
      const raw = stack.readBytes(); let id;
      new PbfReader(raw).readFields((key, ___, glyph) => { if (key === 1) id = glyph.readVarint(); }, {});
      if (id !== undefined) glyphs.set(id, raw);
    }, {});
  }, {});
  return glyphs;
}
function combineGlyphs(original, fallback, name, range) {
  const merged = new Map([...decodeGlyphs(fallback), ...decodeGlyphs(original)]);
  const writer = new PbfWriter();
  writer.writeMessage(1, (_, stack) => {
    stack.writeStringField(1, name); stack.writeStringField(2, range);
    for (const [, bytes] of [...merged].sort((a, b) => a[0] - b[0])) stack.writeBytesField(3, bytes);
  }, {});
  return Buffer.from(writer.finish());
}
const ranges = new Set([0, 256, 8192]);
for (const file of ['public/data/roads.geojson', 'public/data/places.geojson', 'android/maps/pois.geojson']) {
  for (const feature of JSON.parse(await fs.readFile(path.join(root, file), 'utf8')).features)
    for (const char of (feature.properties.name ?? '') + (feature.properties.name ?? '').toUpperCase()) ranges.add(Math.floor(char.codePointAt(0) / 256) * 256);
}
const catalog = { schemaVersion: 1, themes: {
  gta: { ui: { file: 'game-assets/gta/fonts/pricedow.ttf', postScriptName: input.fonts.gta.postScriptName }, map: { road: 'NavSkinGtaRoad', poi: 'NavSkinGtaDisplay', place: 'NavSkinGtaDisplay' } },
  rdr2: { ui: { file: 'game-assets/rdr2/fonts/HapnaSlabSerif-DemiBold.ttf', postScriptName: input.fonts.rdr2ui.postScriptName }, map: { road: 'NavSkinRdrMap', poi: 'NavSkinRdrUi', place: 'NavSkinRdrMap' } },
} };
const provenance = { schemaVersion: 1, fonts: [], sprites: [], conversion: { tool: 'MapLibre font-maker', url: 'https://github.com/maplibre/font-maker', kerning: 'Glyph PBF stores metrics, not TTF kerning tables.', unicodeFallback: 'Missing codepoints merge Noto Sans Bold; other world blocks use the online Noto font.' }, mapFontNotes: input.mapFontNotes };
for (const [id, theme, filename, aliases] of [
  ['gta', 'gta', 'pricedow.ttf', ['NavSkinGtaRoad', 'NavSkinGtaDisplay']],
  ['rdr2ui', 'rdr2', 'HapnaSlabSerif-DemiBold.ttf', ['NavSkinRdrUi']],
  ['rdr2map', 'rdr2', 'RDRLino-Regular.ttf', ['NavSkinRdrMap']],
]) {
  const record = input.fonts[id], bytes = await fs.readFile(record.path);
  if (hash(bytes) !== record.sha256) throw new Error(`Font checksum mismatch: ${id}`);
  const destination = path.join(output, 'runtime', theme, 'fonts', filename);
  await fs.mkdir(path.dirname(destination), { recursive: true }); await fs.writeFile(destination, bytes);
  const notices = [];
  for (const [i, notice] of record.licenseFiles.entries()) {
    const name = `${filename}-${i}-${path.basename(notice)}`;
    await fs.copyFile(notice, path.join(path.dirname(destination), name)); notices.push(`${theme}/fonts/${name}`);
  }
  const stack = wasm.ccall('create_fontstack', 'number', ['string'], [record.name]);
  const pointer = wasm._malloc(bytes.length); wasm.HEAPU8.set(bytes, pointer); wasm._fontstack_add_face(stack, pointer, bytes.length);
  try {
    // Cover every BMP block that the supplied font actually contains, so Cyrillic
    // and extended Latin do not fall back merely because Goiânia has no such names.
    for (let start = 0; start < 65536; start += 256) {
      const range = `${start}-${start + 255}`;
      const glyphBuffer = wasm._generate_glyph_buffer(stack, start);
      const at = wasm._glyph_buffer_data(glyphBuffer), count = wasm._glyph_buffer_size(glyphBuffer);
      const original = Buffer.from(wasm.HEAPU8.slice(at, at + count)); wasm._free_glyph_buffer(glyphBuffer);
      if (!ranges.has(start) && decodeGlyphs(original).size === 0) continue;
      let fallback;
      try { fallback = await fs.readFile(path.join(root, `android/maps/fonts/GameMapSans/${range}.pbf`)); }
      catch {
        const cache = path.join(wasmRoot, `noto-${range}.pbf`);
        try { fallback = await fs.readFile(cache); }
        catch { const response = await fetch(`https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/${range}.pbf`); if (!response.ok) throw new Error(`Noto range ${range}: ${response.status}`); fallback = Buffer.from(await response.arrayBuffer()); await fs.writeFile(cache, fallback); }
      }
      const data = combineGlyphs(original, fallback, record.name, range);
      for (const alias of aliases) { const directory = path.join(output, 'glyphs', alias); await fs.mkdir(directory, { recursive: true }); await fs.writeFile(path.join(directory, `${range}.pbf`), data); }
    }
  } finally { wasm._free_fontstack(stack); }
  provenance.fonts.push({ id, name: record.name, file: `${theme}/fonts/${filename}`, postScriptName: record.postScriptName, sha256: record.sha256, sourceUrl: record.sourceUrl, copyrightStatus: record.copyrightStatus, provenance: record.provenance ?? 'historical original author font release', notices, aliases });
}

// Original game pixels are preserved as embedded images, never traced or renamed CC0.
const mappings = {
  gta: { food: 'radar_dateFood.png', health: 'radar_hostpital.png', parking: 'radar_impound.png', restaurant: 'radar_diner.png', cafe: 'radar_dateFood.png', 'fast-food': 'radar_burgerShot.png', hospital: 'radar_hostpital.png', clinic: 'radar_hostpital.png', pharmacy: 'radar_hostpital.png', bar: 'radar_dateDrink.png' },
  rdr2: { food: 'blip_supplies_food.png', health: 'blip_shop_doctor.png', shops: 'blip_shop_store.png', restaurant: 'blip_supplies_food.png', cafe: 'blip_saloon.png', 'fast-food': 'blip_supplies_food.png', hospital: 'blip_shop_doctor.png', clinic: 'blip_shop_doctor.png', pharmacy: 'blip_shop_doctor.png', bar: 'blip_saloon.png', theatre: 'blip_ambient_theatre.png' },
};
const drawings = {
  parks: '<path d="M13 20h6v10h-6z" fill="#79502D"/><path d="M7 23C0 21 2 13 7 12 4 4 15 1 19 7 25 3 32 9 27 15 34 22 23 27 19 23Z" fill="#4E9B22"/>',
  fuel: '<path d="M6 29V5h15v24ZM9 9h9v8H9Z" fill="#F2BF42"/><path d="M22 10h3l4 6v9c0 5-5 5-5 0v-7M4 29h21" fill="none"/>',
  shops: '<path d="M6 12h20l2 18H4Z" fill="#82AD2D"/><path d="M11 13V8c0-9 10-9 10 0v5" fill="none"/>',
  parking: '<path d="M5 10h22v15H5Z" fill="#8AABC9"/><path d="M9 11V6h14l3 5M3 17h26M8 24v5m16-5v5" fill="none"/>',
  education: '<path d="m2 11 14-8 14 8-14 8ZM8 16v9c7 4 10 4 16 0v-9" fill="#17467E"/><path d="M27 13v13l-2 4h5l-2-4" fill="#F3CA42"/>',
  culture: '<path d="M2 11 16 3l14 8ZM4 28h24v3H4ZM6 14h4v13H6Zm8 0h4v13h-4Zm8 0h4v13h-4Z" fill="#D8C28D"/>',
  religion: '<path d="M13 2h6v10h10v6H19v13h-6V18H3v-6h10Z" fill="#EAC44F"/>',
  theatre: '<path d="M3 5h12v16C3 28 2 15 3 5Zm14 0h12c1 13-1 23-12 16Z" fill="#E2C6A0"/><path d="M5 11h7m8 0h7M5 17l7 2m8 0 7-2" fill="none"/>',
};
const aliases = { school: 'education', museum: 'culture', worship: 'religion', restaurant: 'food', cafe: 'food', 'fast-food': 'food', pharmacy: 'health', clinic: 'health', hospital: 'health', bar: 'food' };
for (const theme of ['gta', 'rosa', 'rdr2']) {
  const directory = path.join(root, 'android/maps/icons', theme); await fs.mkdir(directory, { recursive: true });
  for (const [id, drawing] of Object.entries(drawings)) {
    const filename = path.join(directory, `poi-${id}.svg`);
    if (theme !== 'gta') { try { await fs.access(filename); continue; } catch {} }
    const fill = theme === 'rdr2' ? '#D8C28D' : undefined;
    await fs.writeFile(filename, `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><title>NavSkin ${id}: original adaptation for a category without an exact game counterpart</title><g stroke="${theme === 'rosa' ? '#914D63' : '#111111'}" stroke-width="1.8" stroke-linejoin="round"${fill ? ` fill="${fill}"` : ''}>${drawing}</g></svg>\n`);
  }
  for (const [alias, base] of Object.entries(aliases)) {
    if (mappings[theme]?.[alias]) continue;
    const destination = path.join(directory, `poi-${alias}.svg`);
    try { await fs.copyFile(path.join(directory, `poi-${base}.svg`), destination); } catch { /* Base medical/food drawing is prepared by the main builder. */ }
  }
  await fs.writeFile(path.join(directory, 'road-oneway-forward.svg'), '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><path d="M4 16h23m-9-8 9 8-9 8" fill="none" stroke="#111" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 16h23m-9-8 9 8-9 8" fill="none" stroke="#FFF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/></svg>\n');
  for (const [id, originalName] of Object.entries(mappings[theme] ?? {})) {
    const source = path.join(theme === 'gta' ? input.spritesGta : input.spritesRdr, originalName);
    const original = await fs.readFile(source);
    const image = await sharp(original).resize(32, 32, { kernel: 'nearest' }).png().toBuffer();
    await fs.writeFile(path.join(directory, `poi-${id}.svg`), `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32"><title>${theme} original ${originalName}; copyright Rockstar Games; public export, not CC0</title><image width="32" height="32" href="data:image/png;base64,${image.toString('base64')}"/></svg>\n`);
    provenance.sprites.push({ theme, id: `poi-${id}`, originalName, sha256: hash(original), sourceUrl: theme === 'gta' ? input.spritesGtaSourceUrl : input.spritesRdrSourceUrl, copyright: input.spritesCopyright });
  }
}
await json(path.join(output, 'catalog.json'), catalog);
await json(path.join(output, 'provenance.json'), provenance);
console.log(`Prepared ${provenance.fonts.length} game-font files, ${provenance.sprites.length} original sprite mappings and semantic adaptations.`);
