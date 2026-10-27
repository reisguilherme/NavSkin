import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { validateStyleMin, featureFilter } from '@maplibre/maplibre-gl-style-spec';

const module = await import('../src/style.js').catch(() => ({}));

test('style builder exists for local GeoJSON cartography', () => {
  assert.equal(typeof module.buildStyle, 'function', 'buildStyle is not implemented');
});

test('both visual modes use identical local geographic sources and no remote assets', () => {
  assert.ok(module.buildStyle, 'builder required');
  const gta = module.buildStyle('gta');
  const neutral = module.buildStyle('neutral');
  assert.deepEqual(gta.sources, neutral.sources);
  assert.equal(gta.glyphs, undefined);
  assert.equal(gta.sprite, '/sprites/poi');
  assert.equal(Object.keys(gta.sources).length, 6);
  for (const source of Object.values(gta.sources)) {
    if(source.type==='geojson') assert.match(source.data, /^\/data\/.+\.geojson$/);
    else assert.ok(source.tiles.every(url=>url.startsWith('/tiles/')));
  }
});

test('crossings stay open: all road casings precede all fills within each grade', () => {
  assert.ok(module.buildStyle, 'builder required');
  const layers = module.buildStyle('gta').layers;
  const grades = [...new Set(layers.filter(l => l.metadata?.road).map(l => l.metadata.grade))];
  assert.ok(grades.length >= 3, 'tunnel, ground and elevated layers required');
  let previousEnd = -1;
  for (const grade of grades) {
    const casings = layers.map((l, i) => l.metadata?.grade === grade && l.metadata?.part === 'casing' ? i : -1).filter(i => i >= 0);
    const fills = layers.map((l, i) => l.metadata?.grade === grade && l.metadata?.part === 'fill' ? i : -1).filter(i => i >= 0);
    assert.ok(Math.min(...casings) > previousEnd, 'grade order must be ascending');
    assert.ok(Math.max(...casings) < Math.min(...fills), 'casings must not cut across fills');
    previousEnd = Math.max(...fills);
  }
});

test('visual overrides change paint but preserve source, filters and topology', () => {
  assert.ok(module.buildStyle, 'builder required');
  const base = module.buildStyle('gta');
  const adjusted = module.buildStyle('gta', { urban: '#aabbcc', roadScale: 1.4 });
  assert.deepEqual(base.sources, adjusted.sources);
  assert.deepEqual(base.layers.map(l => l.filter), adjusted.layers.map(l => l.filter));
  assert.notDeepEqual(base.layers.map(l => l.paint), adjusted.layers.map(l => l.paint));
});

test('unknown style is rejected instead of silently rendering another preset', () => {
  assert.ok(module.buildStyle, 'builder required');
  assert.throws(() => module.buildStyle('unknown'), /style|estilo/i);
});

test('bus corridors are visible and elevated footbridges draw above ground roads', () => {
  const layers = module.buildStyle('gta').layers;
  const bus = layers.find(l => l.id === 'road-0-busway-fill');
  assert.ok(bus, 'real BRT corridors must not disappear');
  const ground = layers.findIndex(l => l.id === 'road-0-motorway-fill');
  const bridge = layers.findIndex(l => l.id === 'paths-1');
  assert.ok(bridge > ground, 'elevated paths must be above ground motorway');
});

test('both generated styles satisfy the installed MapLibre schema',()=>{
  for(const mode of ['gta','neutral']) assert.deepEqual(validateStyleMin(module.buildStyle(mode)).map(e=>e.message),[]);
});

test('actual OSM road and area classes retain drawing rules, including the OSM building fallback',async()=>{
  const style=module.buildStyle('gta');
  for(const source of ['roads','areas']) {
    const data=JSON.parse(await readFile(new URL(`../public/data/${source}.geojson`,import.meta.url),'utf8'));
    const filters=style.layers.map((layer,index)=>({layer,index})).filter(({layer:l})=>l.source===source && l.type!=='symbol' && (l.minzoom??0)<=17).map(({layer:l,index})=>featureFilter(l.filter,`layers[${index}].filter`).filter);
    for(const feature of data.features) assert.ok(filters.some(filter=>filter({zoom:17},{type:source==='roads'?2:3,properties:feature.properties})),`${feature.id} (${feature.properties.kind}) has no drawing layer`);
  }
});

test('building sources are exclusive and real service categories have icon layers',()=>{
  const style=module.buildStyle('gta');
  assert.equal(style.sources.buildings?.type,'vector','dense footprints need local vector tiles');
  assert.equal(style.layers.find(l=>l.id==='building-footprints')?.['source-layer'],'buildings');
  assert.equal(style.layers.find(l=>l.id==='building-osm')?.layout.visibility,'none');
  for(const kind of ['park','pharmacy','fuel','hospital','theatre']) assert.ok(style.layers.some(l=>l.id===`poi-${kind}`),'missing '+kind);
  const osm=module.buildStyle('gta',{buildingSource:'osm',poisEnabled:false});
  assert.equal(osm.layers.find(l=>l.id==='building-osm').layout.visibility,'visible');
  assert.equal(osm.layers.find(l=>l.id==='building-footprints').layout.visibility,'none');
  assert.ok(osm.layers.filter(l=>l.source==='pois').every(l=>l.layout.visibility==='none'));
  assert.equal(osm.layers.find(l=>l.id==='landmark-icons').layout.visibility,'none');
});
