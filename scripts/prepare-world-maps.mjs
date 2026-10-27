/** Deterministic online worldwide MapLibre Native cartography; no map downloads. */
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import path from 'node:path';
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec';
import tokens from '../android/maps/theme-tokens.json' with { type: 'json' };
import buildingMetadata from '../public/data/buildings-metadata.json' with { type: 'json' };
import { applyGameTypography } from './game-asset-typography.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'android/app/src/main/assets/maps');
const themes = { gta: 'GTA SA', rosa: 'Rosa Pop', rdr2: 'RDR2' };
export const WORLD_VARIANTS = Object.keys(themes).flatMap(theme => [theme, `${theme}-night`]);
export const WORLD_BOUNDS = [-180, -85.05113, 180, 85.05113];
export const WORLD_ATTRIBUTION = '<a href="https://openfreemap.org/">OpenFreeMap</a> · <a href="https://www.openmaptiles.org/">© OpenMapTiles</a> · Data from <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>';
export const WORLD_GLYPHS = 'https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf';
const buildingTiles = buildingMetadata.vectorTiles;
const [west, south, east, north] = buildingTiles.bounds;
const buildingCoverage = { type: 'Polygon', coordinates: [[[west, south], [east, south], [east, north], [west, north], [west, south]]] };
const buildingAttribution = '<a href="https://docs.overturemaps.org/attribution/">Overture Maps Foundation</a> · <a href="https://sites.research.google/open-buildings/">Google Open Buildings</a> · <a href="https://github.com/microsoft/GlobalMLBuildingFootprints">Microsoft Global ML Building Footprints</a>';
const combinedAttribution = `${WORLD_ATTRIBUTION} · ${buildingAttribution}`;
const bundledBuildings = {
  source: 'buildings-local', bounds: [...buildingTiles.bounds], release: buildingMetadata.release,
  count: buildingMetadata.counts.total, metadataAsset: 'maps/data/buildings-metadata.json',
  replacement: 'Bundled Overture footprints replace world building polygons intersecting these bounds. Outside the bounds, world buildings remain visible.',
};

// Stable visibility groups. Use the original OSM subclass, since
// OpenMapTiles combines clinic under hospital and convenience under shop.
const poiSubclasses = {
  parks: ['park', 'garden'], fuel: ['fuel'], food: ['restaurant', 'cafe', 'fast_food', 'bar', 'pub'],
  parking: ['parking'], health: ['hospital', 'clinic', 'doctors', 'pharmacy'], shops: ['supermarket', 'convenience'],
  education: ['school', 'university', 'college', 'kindergarten'],
  culture: ['museum', 'theatre', 'cinema', 'arts_centre'],
};
const hasClass = values => ['in', ['get', 'class'], ['literal', values]];
const lineGeometry = ['==', ['geometry-type'], 'LineString'];
const pointGeometry = ['==', ['geometry-type'], 'Point'];
const polygonGeometry = ['==', ['geometry-type'], 'Polygon'];
const label = ['coalesce', ['get', 'name:pt'], ['get', 'name'], ['get', 'name:latin'], ['get', 'name:en'], ''];
const grade = ['case', ['==', ['get', 'brunnel'], 'tunnel'], -1, ['==', ['get', 'brunnel'], 'bridge'], 1, 0];
const roadClasses = { local: ['minor'], busway: ['busway', 'bus_guideway'] };
const roadMinzoom = { motorway: 5, trunk: 6, primary: 7, secondary: 9, tertiary: 10, local: 12, busway: 11, service: 14 };

/** Draw restrictions only when the actual source explicitly states their direction. */
export function roadDirectionLayer({ source, sourceLayer, roadProperty, roadValues, rawOsm = false }) {
  const direction = ['to-string', ['coalesce', ['get', 'oneway'], '']];
  return {
    id: 'road-oneway-directions', type: 'symbol', source, ...(sourceLayer ? { 'source-layer': sourceLayer } : {}), minzoom: 16.5,
    metadata: { navskin: { role: 'traffic-direction', sourceDirectionRequired: true } },
    filter: ['all', lineGeometry, ['in', ['get', roadProperty], ['literal', roadValues]],
      ['in', direction, ['literal', rawOsm ? ['yes', 'true', '1', '-1', 'reverse'] : ['1', '-1']]]],
    layout: {
      'symbol-placement': 'line', 'symbol-spacing': 130, 'icon-image': 'road-oneway-forward',
      'icon-size': ['interpolate', ['linear'], ['zoom'], 16.5, 0.42, 18, 0.65],
      'icon-rotate': ['case', ['in', direction, ['literal', ['-1', 'reverse']]], 180, 0],
      'icon-rotation-alignment': 'map', 'icon-pitch-alignment': 'map', 'icon-keep-upright': false,
      'icon-padding': 4, 'icon-allow-overlap': false, 'icon-ignore-placement': false,
    },
    paint: { 'icon-opacity': 0.94 },
  };
}

/** Keep one visibility layer per category, while preserving real source subtypes. */
export function poiIconExpression(category, subtype) {
  if (category === 'food') return ['match', subtype,
    'cafe', 'poi-cafe', 'fast_food', 'poi-fast-food', 'restaurant', 'poi-restaurant', ['bar', 'pub'], 'poi-bar', 'poi-food'];
  if (category === 'health') return ['match', subtype,
    'pharmacy', 'poi-pharmacy', 'hospital', 'poi-hospital', ['clinic', 'doctors'], 'poi-clinic', 'poi-health'];
  if (category === 'education') return ['match', subtype, ['school', 'university', 'college', 'kindergarten'], 'poi-school', 'poi-education'];
  if (category === 'culture') return ['match', subtype, 'museum', 'poi-museum', ['theatre', 'cinema', 'arts_centre'], 'poi-theatre', 'poi-culture'];
  if (category === 'religion') return ['match', subtype, 'christian', 'poi-worship', 'poi-religion'];
  return `poi-${category}`;
}

export function buildWorldStyle(mode) {
  const theme = mode?.replace(/-night$/, '');
  const p = tokens.palettes[mode];
  if (!themes[theme] || !p || !WORLD_VARIANTS.includes(mode)) throw new Error(`Unknown world map variant: ${mode}`);
  const night = mode.endsWith('-night');
  const gta = theme === 'gta' ? tokens.labels.gta : null;
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
  // Overture already conflates OSM buildings. Replace the world footprint layer
  // in its exact query area instead of drawing both datasets on top of each other.
  // `within` cannot test Polygon features; distance=0 tests real intersections,
  // matching the local query that kept whole footprints crossing its boundary.
  fill('building-footprints', 'building', p.building, ['>', ['distance', buildingCoverage], 0], tokens.visibility.buildingsMinzoom);
  if (p.buildingOutline) layers.at(-1).paint['fill-outline-color'] = p.buildingOutline;
  layers.push({ id: 'building-footprints-local', type: 'fill', source: bundledBuildings.source,
    'source-layer': buildingTiles.sourceLayer, minzoom: tokens.visibility.buildingsMinzoom,
    filter: polygonGeometry, paint: { ...layers.at(-1).paint } });
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
  layers.push(roadDirectionLayer({ source: 'world', sourceLayer: 'transportation', roadProperty: 'class',
    roadValues: tokens.roads.flatMap(road => roadClasses[road.id] ?? [road.id]) }));
  const textPaint = { 'text-color': p.label, 'text-halo-color': p.labelHalo, 'text-halo-width': gta?.haloWidth ?? 1.5 };
  const textLayout = { 'text-field': label, 'text-font': ['Noto Sans Bold'], 'text-allow-overlap': false };
  layers.push({ id: 'road-labels', type: 'symbol', source: 'world', 'source-layer': 'transportation_name', minzoom: gta?.roadMinzoom ?? 13,
    filter: ['all', lineGeometry, hasClass(['motorway', 'trunk', 'primary', 'secondary', 'tertiary', 'minor'])],
    layout: { ...textLayout, 'symbol-placement': 'line', 'symbol-spacing': gta?.roadSpacing ?? tokens.visibility.labelSpacing,
      'text-size': gta?.roadSize ?? tokens.labels.roadSize, 'text-max-angle': 30,
      ...(gta ? { 'text-transform': 'uppercase', 'text-letter-spacing': gta.roadLetterSpacing } : {}) },
    paint: { ...textPaint, ...(gta ? { 'text-halo-width': gta.roadHaloWidth } : {}) } });
  layers.push({ id: 'water-labels', type: 'symbol', source: 'world', 'source-layer': 'water_name', minzoom: 4,
    layout: { ...textLayout, 'text-size': 12, 'text-max-width': 12, 'text-padding': 8 }, paint: { ...textPaint, 'text-opacity': 0.8 } });
  layers.push({ id: 'district-labels', type: 'symbol', source: 'world', 'source-layer': 'place', minzoom: 12,
    filter: hasClass(['suburb', 'quarter', 'neighbourhood', 'borough']),
    layout: { ...textLayout, 'text-size': tokens.labels.districtSize, 'text-letter-spacing': 0.06, 'text-padding': 22, 'text-max-width': 14 },
    paint: { ...textPaint, 'text-opacity': 0.9 } });
  for (const category of [...tokens.categories].reverse()) layers.push({
    id: `poi-${category.id}`, type: 'symbol', source: 'world', 'source-layer': 'poi', minzoom: category.minzoom,
    metadata: { navskin: { category: category.id, label: category.label } },
    filter: ['all', pointGeometry, category.id === 'religion' ? ['==', ['get', 'class'], 'place_of_worship']
      : ['in', ['coalesce', ['get', 'subclass'], ['get', 'class']], ['literal', poiSubclasses[category.id]]]],
    layout: { ...textLayout, visibility: 'visible',
      'icon-image': poiIconExpression(category.id, ['coalesce', ['get', 'subclass'], ['get', 'class'], '']),
      'icon-size': ['interpolate', ['linear'], ['zoom'], 12, 0.76, 15, 0.9, 18, 1.05],
      'icon-padding': 5, 'icon-allow-overlap': false, 'icon-ignore-placement': false,
      'symbol-sort-key': ['coalesce', ['get', 'rank'], 100],
      'text-size': ['step', ['zoom'], 0, gta?.poiNamesMinzoom ?? tokens.visibility.poiNamesMinzoom, gta?.poiSize ?? 11.5],
      'text-optional': true, 'text-offset': [0, gta ? 1.65 : 1.9], 'text-anchor': 'top', 'text-padding': 4, 'text-max-width': 13 },
    paint: { ...textPaint, ...(gta ? { 'text-halo-width': gta.poiHaloWidth } : {}) },
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
      minzoom: 0, maxzoom: 14, bounds: [...WORLD_BOUNDS], attribution: WORLD_ATTRIBUTION },
    [bundledBuildings.source]: { type: 'vector', tiles: ['asset://maps/tiles/buildings/{z}/{x}/{y}.pbf'],
      minzoom: buildingTiles.minzoom, maxzoom: buildingTiles.maxzoom, bounds: [...buildingTiles.bounds], attribution: buildingAttribution } },
    metadata: { navskin: { theme, appearance: night ? 'night' : 'day', variant: mode,
      offline: false, coverage: 'world', bounds: [...WORLD_BOUNDS], sourceCoordinatesModified: false,
      categoryLayerPrefix: 'poi-', categories: tokens.categories.map(category => category.id),
      attribution: combinedAttribution, creditsAsset: 'maps/credits/world-attribution.html',
      provider: 'OpenFreeMap', schema: 'OpenMapTiles', font: 'Noto Sans Bold',
      bundledBuildings: structuredClone(bundledBuildings),
      note: 'Online base tiles and glyphs require network. In the bundled Goiânia coverage, dense local Overture building tiles replace world buildings in every theme.' } },
    layers, transition: { duration: 0, delay: 0 },
  };
  applyGameTypography(style, theme, false);
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
  source.bundledBuildings = structuredClone(bundledBuildings);
  source.attributionHtml = combinedAttribution;
  await writeJson('credits/world-sources.json', source);
  await writeFile(path.join(output, 'credits/world-attribution.html'), `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>NavSkin · Créditos do mapa mundial</title><style>body{font:18px sans-serif;color:#252025;background:#fff8fc;padding:24px;line-height:1.5}a{color:#8b2058}</style><h1>Mapa mundial · NavSkin</h1><p>${combinedAttribution}</p><p>Geometria mundial OpenStreetMap no esquema OpenMapTiles, distribuída por OpenFreeMap. Cobertura e atualização dependem dos dados disponíveis em cada região.</p><p>Na cobertura local de Goiânia, os ${buildingMetadata.counts.total.toLocaleString('pt-BR')} contornos da edição Overture ${buildingMetadata.release} substituem as edificações do mapa mundial. Os tiles locais mantêm os contornos de origem, inclusive os que atravessam a borda do recorte. Fora dessa área, as edificações continuam vindo do OpenFreeMap.</p><p>O tema Overture buildings é distribuído sob <a href="https://opendatacommons.org/licenses/odbl/1-0/">ODbL 1.0</a>; inclui dados Google Open Buildings sob <a href="https://creativecommons.org/licenses/by/4.0/">CC BY 4.0</a>, Microsoft e OpenStreetMap. Consulte <a href="sources.json">fontes e licenças locais</a>. Os contornos podem conter omissões, deslocamentos ou detecções antigas; não são cadastro oficial.</p><p>O mapa mundial usa a base de tiles pela internet, fontes temáticas locais e Noto como fallback Unicode. Os nove filtros de POIs e preferências são locais. A opção Goiânia mantém a base completa offline.</p><p>Fontes e símbolos importados dos jogos preservam os direitos de seus titulares. Consulte <a href="game-assets-provenance.json">origem e avisos individuais</a>; CC0 cobre apenas adaptações vetoriais originais do NavSkin.</p><p><a href="https://openfreemap.org/tos/">Termos do serviço</a> · <a href="https://openfreemap.org/privacy/">Privacidade</a> · <a href="https://www.openstreetmap.org/copyright">ODbL e direitos dos dados</a> · <a href="Noto-OFL-1.1.md">Licença SIL OFL das fontes Noto de fallback</a></p></html>\n`);
  console.log(`Prepared ${WORLD_VARIANTS.length} worldwide online styles and production service credits.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) await prepareWorldMaps();
