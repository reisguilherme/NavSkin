/** Deterministic online worldwide MapLibre Native cartography; no map downloads. */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec';
import tokens from '../android/maps/theme-tokens.json' with { type: 'json' };

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'android/app/src/main/assets/maps');
const themes = { gta: 'GTA SA', rosa: 'Rosa Pop', rdr2: 'RDR2' };
export const WORLD_VARIANTS = Object.keys(themes).flatMap(theme => [theme, `${theme}-night`]);
export const WORLD_BOUNDS = [-180, -85.05113, 180, 85.05113];
export const WORLD_ATTRIBUTION = '<a href="https://openfreemap.org/">OpenFreeMap</a> · <a href="https://www.openmaptiles.org/">© OpenMapTiles</a> · Data from <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>';
export const WORLD_GLYPHS = 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf';

// Exactly the existing six app categories. Use the original OSM subclass, since
// OpenMapTiles combines clinic under hospital and convenience under shop.
const poiSubclasses = {
  parks: ['park', 'garden'], fuel: ['fuel'], food: ['restaurant', 'cafe', 'fast_food'],
  parking: ['parking'], health: ['hospital', 'clinic', 'doctors', 'pharmacy'], shops: ['supermarket', 'convenience'],
};
const hasClass = values => ['in', ['get', 'class'], ['literal', values]];
const lineGeometry = ['==', ['geometry-type'], 'LineString'];
const pointGeometry = ['==', ['geometry-type'], 'Point'];
const polygonGeometry = ['==', ['geometry-type'], 'Polygon'];
const label = ['coalesce', ['get', 'name:pt'], ['get', 'name'], ['get', 'name:latin'], ['get', 'name:en'], ''];
const grade = ['case', ['==', ['get', 'brunnel'], 'tunnel'], -1, ['==', ['get', 'brunnel'], 'bridge'], 1, 0];
const roadClasses = { local: ['minor'], busway: ['busway', 'bus_guideway'] };
const roadMinzoom = { motorway: 5, trunk: 6, primary: 7, secondary: 9, tertiary: 10, local: 12, busway: 11, service: 14 };

export function buildWorldStyle(mode) {
  const theme = mode?.replace(/-night$/, '');
  const p = tokens.palettes[mode];
  if (!themes[theme] || !p || !WORLD_VARIANTS.includes(mode)) throw new Error(`Unknown world map variant: ${mode}`);
  const night = mode.endsWith('-night');
  const layers = [{ id: 'background', type: 'background', paint: { 'background-color': p.background } }];
  const fill = (id, sourceLayer, color, filter, minzoom = 0) => layers.push({
    id, type: 'fill', source: 'world', 'source-layer': sourceLayer, minzoom,
    filter: ['all', polygonGeometry, ...(filter ? [filter] : [])],
    paint: { 'fill-color': color, 'fill-opacity': 1 },
  });
  fill('landuse-urban', 'landuse', p.urban, hasClass(['residential', 'suburb', 'neighbourhood', 'construction']));
  fill('landuse-commercial', 'landuse', p.commercial, hasClass(['commercial', 'retail']));
  fill('landuse-industrial', 'landuse', p.industrial, hasClass(['industrial', 'railway', 'brownfield', 'quarry']));
  fill('vegetation-grass', 'landcover', p.grass, hasClass(['grass', 'farmland', 'scrub', 'wetland']));
  fill('vegetation-wood', 'landcover', p.wood, hasClass(['wood']));
  fill('protected-areas', 'park', p.park);
  fill('vegetation-parks', 'landuse', p.park, hasClass(['park', 'garden', 'recreation_ground', 'village_green', 'golf_course', 'cemetery']));
  fill('public-squares', 'transportation', p.square, hasClass(['path', 'pedestrian']));
  fill('water-fill', 'water', p.water);
  layers.push({ id: 'water-outline', type: 'line', source: 'world', 'source-layer': 'water', filter: polygonGeometry,
    paint: { 'line-color': p.waterOutline, 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.4, 17, 1.4] } });
  layers.push({ id: 'waterways', type: 'line', source: 'world', 'source-layer': 'waterway',
    layout: { 'line-cap': 'round', 'line-join': 'round' },
    paint: { 'line-color': p.water, 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.4, 14, 2, 17, 5] } });
  fill('building-footprints', 'building', p.building, undefined, tokens.visibility.buildingsMinzoom);
  if (p.buildingOutline) layers.at(-1).paint['fill-outline-color'] = p.buildingOutline;
  layers.push({ id: 'administrative-boundaries', type: 'line', source: 'world', 'source-layer': 'boundary',
    filter: ['all', ['<=', ['coalesce', ['get', 'admin_level'], 99], 4], ['!=', ['get', 'maritime'], 1]],
    paint: { 'line-color': p.labelHalo, 'line-opacity': 0.3, 'line-width': ['interpolate', ['linear'], ['zoom'], 2, 0.5, 12, 1.2], 'line-dasharray': [3, 2] } });

  // Each physical grade draws every casing before any road fill. The provider
  // retains real bridge/tunnel topology; no fictional road geometry is added.
  for (const level of [-1, 0, 1]) {
    const atGrade = ['==', grade, level];
    layers.push({ id: `paths-${level}`, type: 'line', source: 'world', 'source-layer': 'transportation', minzoom: 14,
      filter: ['all', lineGeometry, hasClass(['path', 'track']), atGrade],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': p.path, 'line-width': ['interpolate', ['linear'], ['zoom'], 14, 0.6, 17, 2.1], 'line-opacity': level < 0 ? 0.5 : 1 } });
    layers.push({ id: `railway-${level}`, type: 'line', source: 'world', 'source-layer': 'transportation', minzoom: 9,
      filter: ['all', lineGeometry, hasClass(['rail', 'transit']), atGrade],
      paint: { 'line-color': p.rail, 'line-width': ['interpolate', ['linear'], ['zoom'], 9, 0.4, 17, 2.7], 'line-dasharray': [3, 1.2] } });
    for (const part of ['casing', 'fill']) for (const road of tokens.roads) {
      const casing = part === 'casing';
      const classes = roadClasses[road.id] ?? [road.id];
      const color = theme === 'gta' ? p.road : casing ? p.roadOutline : (road.id === 'trunk' ? p.motorway : p[road.id] ?? p.road);
      const extra = casing ? road.outline * 2 : 0;
      layers.push({ id: `road-${level}-${road.id}-${part}`, type: 'line', source: 'world', 'source-layer': 'transportation',
        minzoom: roadMinzoom[road.id], metadata: { road: true, grade: level, part },
        filter: ['all', lineGeometry, hasClass(classes), atGrade],
        layout: { 'line-cap': 'round', 'line-join': 'round' },
        paint: { 'line-color': color,
          'line-width': ['interpolate', ['linear'], ['zoom'], 5, 0.2 + extra * 0.2, 8, 0.45 + extra * 0.4, ...tokens.zoomStops.flatMap((z, i) => [z, road.widths[i] + extra])],
          'line-opacity': level < 0 ? 0.55 : 1, ...(level < 0 ? { 'line-dasharray': [2, 1.4] } : {}) },
      });
    }
    layers.push({ id: `motorway-${level}-center`, type: 'line', source: 'world', 'source-layer': 'transportation', minzoom: 12,
      filter: ['all', lineGeometry, hasClass(['motorway']), atGrade],
      layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': theme === 'gta' ? p.motorway : p.motorwayCenter,
        'line-width': ['interpolate', ['linear'], ['zoom'], 12, 0.6, 15, 2.8, 17, 5], 'line-opacity': level < 0 ? 0.5 : 1 } });
  }
  const textPaint = { 'text-color': p.label, 'text-halo-color': p.labelHalo, 'text-halo-width': 1.5 };
  const textLayout = { 'text-field': label, 'text-font': ['Noto Sans Bold'], 'text-allow-overlap': false };
  layers.push({ id: 'road-labels', type: 'symbol', source: 'world', 'source-layer': 'transportation_name', minzoom: 13,
    filter: ['all', lineGeometry, hasClass(['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor'])],
    layout: { ...textLayout, 'symbol-placement': 'line', 'symbol-spacing': tokens.visibility.labelSpacing, 'text-size': tokens.labels.roadSize, 'text-max-angle': 30 }, paint: { ...textPaint } });
  layers.push({ id: 'water-labels', type: 'symbol', source: 'world', 'source-layer': 'water_name', minzoom: 4,
    layout: { ...textLayout, 'text-size': 12, 'text-max-width': 12, 'text-padding': 8 }, paint: { ...textPaint, 'text-opacity': 0.8 } });
  layers.push({ id: 'district-labels', type: 'symbol', source: 'world', 'source-layer': 'place', minzoom: 12,
    filter: hasClass(['suburb', 'quarter', 'neighbourhood', 'borough']),
    layout: { ...textLayout, 'text-size': tokens.labels.districtSize, 'text-letter-spacing': 0.06, 'text-padding': 22, 'text-max-width': 14 },
    paint: { ...textPaint, 'text-opacity': 0.9 } });
  for (const category of [...tokens.categories].reverse()) layers.push({
    id: `poi-${category.id}`, type: 'symbol', source: 'world', 'source-layer': 'poi', minzoom: category.minzoom,
    metadata: { navskin: { category: category.id, label: category.label } },
    filter: ['all', pointGeometry, ['in', ['coalesce', ['get', 'subclass'], ['get', 'class']], ['literal', poiSubclasses[category.id]]]],
    layout: { ...textLayout, visibility: 'visible', 'icon-image': `poi-${category.id}`,
      'icon-size': ['interpolate', ['linear'], ['zoom'], 12, 0.76, 15, 0.9, 18, 1.05],
      'icon-padding': 5, 'icon-allow-overlap': false, 'icon-ignore-placement': false,
      'symbol-sort-key': ['coalesce', ['get', 'rank'], 100],
      'text-size': ['step', ['zoom'], 0, tokens.visibility.poiNamesMinzoom, 11.5],
      'text-optional': true, 'text-offset': [0, 1.9], 'text-anchor': 'top', 'text-padding': 4, 'text-max-width': 13 },
    paint: { ...textPaint },
  });
  layers.push({ id: 'place-labels', type: 'symbol', source: 'world', 'source-layer': 'place',
    filter: hasClass(['continent', 'country', 'state', 'province', 'city', 'town', 'village', 'hamlet', 'island']),
    layout: { ...textLayout, 'symbol-sort-key': ['coalesce', ['get', 'rank'], 100],
      'text-size': ['interpolate', ['linear'], ['zoom'], 2, 11, 7, 13, 12, 16, 17, 20],
      'text-letter-spacing': theme === 'rdr2' ? 0.1 : 0.06, 'text-max-width': 15, 'text-padding': 14 },
    paint: { ...textPaint },
  });
  const style = {
    version: 8, name: `NavSkin · Mundo · ${themes[theme]}${night ? ' · Noite' : ''}`,
    center: [-46.6333, -23.5505], zoom: 12, bearing: 0, pitch: 0,
    sprite: `asset://maps/sprites/${theme}`, glyphs: WORLD_GLYPHS,
    sources: { world: { type: 'vector', url: 'https://tiles.openfreemap.org/planet',
      minzoom: 0, maxzoom: 14, bounds: [...WORLD_BOUNDS], attribution: WORLD_ATTRIBUTION } },
    metadata: { navskin: { theme, appearance: night ? 'night' : 'day', variant: mode,
      offline: false, coverage: 'world', bounds: [...WORLD_BOUNDS], sourceCoordinatesModified: false,
      categoryLayerPrefix: 'poi-', categories: tokens.categories.map(category => category.id),
      attribution: WORLD_ATTRIBUTION, creditsAsset: 'maps/credits/world-attribution.html',
      provider: 'OpenFreeMap', schema: 'OpenMapTiles', font: 'Noto Sans Bold',
      note: 'Online tiles and glyphs require network. The optional bundled Goiânia package remains separate.' } },
    layers, transition: { duration: 0, delay: 0 },
  };
  const errors = validateStyleMin(style);
  if (errors.length) throw new Error(errors.map(error => error.message).join('\n'));
  return style;
}

export async function prepareWorldMaps() {
  const writeJson = async (relative, value) => {
    const destination = path.join(output, relative);
    await mkdir(path.dirname(destination), { recursive: true });
    await writeFile(destination, `${JSON.stringify(value, null, 2)}\n`);
  };
  for (const mode of WORLD_VARIANTS) await writeJson(`styles/world-${mode}.json`, buildWorldStyle(mode));
  const source = JSON.parse(await readFile(path.join(root, 'android/maps/world-source.json'), 'utf8'));
  await writeJson('credits/world-sources.json', source);
  await writeFile(path.join(output, 'credits/world-attribution.html'), `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>NavSkin · Créditos do mapa mundial</title><style>body{font:18px sans-serif;color:#252025;background:#fff8fc;padding:24px;line-height:1.5}a{color:#8b2058}</style><h1>Mapa mundial · NavSkin</h1><p>${WORLD_ATTRIBUTION}</p><p>Geometria mundial OpenStreetMap no esquema OpenMapTiles, distribuída por OpenFreeMap. Cobertura e atualização dependem dos dados disponíveis em cada região.</p><p>O mapa mundial usa tiles e fontes Noto Sans Bold pela internet. Os seis filtros de POIs, sprites originais e preferências são locais. O pacote de Goiânia é uma opção offline separada.</p><p><a href="https://openfreemap.org/tos/">Termos do serviço</a> · <a href="https://openfreemap.org/privacy/">Privacidade</a> · <a href="https://www.openstreetmap.org/copyright">ODbL e direitos dos dados</a> · <a href="Noto-OFL-1.1.md">Licença SIL OFL das fontes Noto</a></p></html>\n`);
  console.log(`Prepared ${WORLD_VARIANTS.length} worldwide online styles and production service credits.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) await prepareWorldMaps();
