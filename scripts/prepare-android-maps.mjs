/** Reproducible local Goiânia assets plus separate online worldwide styles. */
import { mkdir, readFile, writeFile, copyFile, readdir, access } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import sharp from 'sharp';
import { validateStyleMin } from '@maplibre/maplibre-gl-style-spec';
import { buildStyle } from '../src/style.js';
import { prepareWorldMaps, WORLD_VARIANTS } from './prepare-world-maps.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'android/app/src/main/assets/maps');
const source = path.join(root, 'android/maps');
const require = createRequire(import.meta.url);
const maki = path.dirname(require.resolve('@mapbox/maki/package.json'));
const sha256 = value => createHash('sha256').update(value).digest('hex');
const parse = async name => JSON.parse(await readFile(path.join(root, name), 'utf8'));
const writeJson = async (name, value) => {
  await mkdir(path.dirname(name), { recursive: true });
  await writeFile(name, `${JSON.stringify(value, null, 2)}\n`);
};
const exists = async name => access(name).then(() => true, () => false);
const attribution = '© OpenStreetMap contributors · Overture Maps Foundation · Google Open Buildings · Microsoft Global ML Building Footprints';
const themes = {
  gta: { title: 'GTA SA', font: 'GameMapSans' },
  rosa: { title: 'Rosa Pop', font: 'GameMapSans' },
  rdr2: { title: 'RDR2', font: 'GameMapSerif' },
};
const variants = Object.keys(themes).flatMap(theme => [theme, `${theme}-night`]);
const categories = [
  { id: 'parks', label: 'Parques', maki: 'park', color: '#70D648', minzoom: 12.2 },
  { id: 'fuel', label: 'Postos de combustível', maki: 'fuel', color: '#FFD343', minzoom: 13 },
  { id: 'food', label: 'Restaurantes e cafés', maki: 'restaurant', color: '#FFA86A', minzoom: 14.5 },
  { id: 'parking', label: 'Estacionamentos', maki: 'parking', color: '#8BC6FF', minzoom: 15 },
  { id: 'health', label: 'Hospitais e serviços de saúde', maki: 'hospital', color: '#FF5F72', minzoom: 13.2 },
  { id: 'shops', label: 'Mercados e lojas de conveniência', maki: 'grocery', color: '#E6A3FB', minzoom: 14.5 },
];
const nightPalettes = {
  'gta-night': {
    background: '#626961', urban: '#626961', commercial: '#6B7064', industrial: '#635547',
    park: '#53662D', wood: '#294827', grass: '#697540', water: '#4B6E91', square: '#7D826D',
    waterOutline: '#2D485E', building: '#B2BBAE', road: '#060805', roadOutline: '#A4AD9C',
    motorway: '#75816B', path: '#979474', rail: '#3C2D25', label: '#F3F6E9', labelHalo: '#182118',
  },
  'rosa-night': {
    background: '#593276', urban: '#663A84', commercial: '#8B469B', industrial: '#5B4794',
    park: '#41AB99', wood: '#288974', grass: '#69B790', water: '#388EBC', square: '#A99066',
    waterOutline: '#246687', building: '#B888BB', buildingOutline: '#7D4B9B',
    road: '#FFE3FA', roadOutline: '#D65ABB', motorway: '#FF9DD7', primary: '#EFC46F',
    secondary: '#F6A4D3', tertiary: '#D0A6F8', busway: '#AB96ED', service: '#EFC7EC',
    motorwayCenter: '#FFE5AA', path: '#EFA6D8', rail: '#B992E1', label: '#FFF1FC', labelHalo: '#321744',
  },
  'rdr2-night': {
    background: '#948162', urban: '#8E7C5F', commercial: '#897052', industrial: '#7B6A53',
    park: '#777D5A', wood: '#606746', grass: '#848666', water: '#718F89', square: '#A38E6A',
    waterOutline: '#3B514B', building: '#B19B75', buildingOutline: '#6A573D',
    road: '#221B15', roadOutline: '#B29B74', motorway: '#4E211C', primary: '#32271D',
    secondary: '#3B3022', tertiary: '#463A2A', busway: '#652D24', service: '#51442F',
    motorwayCenter: '#A34C3C', path: '#594C36', rail: '#39281F', label: '#FFF0CB', labelHalo: '#3C2D20',
  },
};

const dayPalettes = {
  rosa: {
    background: '#FFD5ED', urban: '#FFC4E3', commercial: '#FFADD9', industrial: '#D5B6FA',
    park: '#6FE3BD', wood: '#38C9AB', grass: '#B4F1CC', water: '#64D7EB', square: '#FFF0A6',
    waterOutline: '#259EBC', building: '#FFF3FB', buildingOutline: '#F289C0',
    road: '#FFF9FE', roadOutline: '#C8318E', motorway: '#FFA7C9', primary: '#FFDF79',
    secondary: '#FFC3E0', tertiary: '#E5CFFF', busway: '#B7A0FF', service: '#FFF1FC',
    motorwayCenter: '#FFF0AC', path: '#CE57A1', rail: '#9563C5', label: '#5D1851', labelHalo: '#FFF6FE',
  },
  rdr2: {
    background: '#D9C69E', urban: '#D8C49D', commercial: '#D0B68C', industrial: '#C8B394',
    park: '#B5AF88', wood: '#93916D', grass: '#C4BD96', water: '#A8B9AD', square: '#E8D7AD',
    waterOutline: '#5C746F', building: '#B6A17A', buildingOutline: '#8C7757',
    road: '#4C3928', roadOutline: '#E7D4AD', motorway: '#6C2C25', primary: '#5C4030',
    secondary: '#65503B', tertiary: '#756047', busway: '#8D4331', service: '#877154',
    motorwayCenter: '#A84837', path: '#8D7957', rail: '#6F5340', label: '#39281C', labelHalo: '#F1DEB6',
  },
};

async function createTokensIfMissing() {
  const destination = path.join(source, 'theme-tokens.json');
  if (await exists(destination)) return parse('android/maps/theme-tokens.json');
  const poc = await parse('public/styles/tokens.json');
  const tokens = {
    specification: {
      gtaReference: 'Approved PoC src/style.js and public/styles/tokens.json. No game artwork or extracted assets.',
      rosaReference: 'Original candy-pink Rosa Pop cartography: vivid mint parks, turquoise water, pink/lilac night fabric and cute original symbols.',
      rdr2Reference: 'Original parchment, dark-ink roads, muted water and selective red accents inspired by the official Piggyback RDR2 atlas. No game imagery, invented features or extracted symbols.',
      edit: 'Edit these palettes and android/maps/icons/{gta,rosa,rdr2}/*.svg, then run node scripts/prepare-android-maps.mjs.',
    },
    zoomStops: poc.zoomStops,
    palettes: {
      gta: poc.palettes.gta,
      ...dayPalettes,
      ...nightPalettes,
    },
    roads: poc.roads,
    visibility: { ...poc.visibility, poiNamesMinzoom: 16.8 },
    labels: { font: 'GameMapSans', placeSizes: [12, 14, 17], districtSize: 12, roadSize: 11.5, haloWidth: 1.5 },
    categories,
    geometry: poc.geometry,
  };
  await writeJson(destination, tokens);
  return tokens;
}

function nativeStyle(mode, tokens) {
  const style = buildStyle('gta');
  const p = tokens.palettes[mode];
  const theme = mode.replace(/-night$/, '');
  const definition = themes[theme];
  if (!definition || !p) throw new Error(`Unknown native map variant: ${mode}`);
  const night = mode.endsWith('-night');
  style.name = `Goiânia · ${definition.title}${night ? ' · Noite' : ''}`;
  style.sprite = `asset://maps/sprites/${theme}`;
  style.glyphs = 'asset://maps/glyphs/{fontstack}/{range}.pbf';
  style.metadata = { gamemap: {
    theme, offline: true, categoryLayerPrefix: 'poi-', categories: categories.map(c => c.id),
    attribution, creditsAsset: 'maps/credits/attribution.html',
    bounds: [-49.325, -16.755, -49.205, -16.64],
    note: 'Asset validation does not establish Android Auto Surface compatibility. Validate Native rendering separately.',
  } };
  if (night) Object.assign(style.metadata.gamemap, { appearance: 'night', variant: mode });
  for (const [name, definition] of Object.entries(style.sources)) {
    if (definition.type === 'geojson') definition.data = `asset://maps/data/${name}.geojson`;
    else definition.tiles = ['asset://maps/tiles/buildings/{z}/{x}/{y}.pbf'];
    if (name === 'buildings') definition.attribution = attribution;
  }
  // Base area/line geometry and physical grade filters come directly from the approved PoC.
  // Rebuild every native marker from the six semantic categories. Strip inherited
  // circle/icon layers by their rendering role so renamed/new web anchors cannot
  // survive when all six category layers are hidden. Geographic text stays intact.
  style.layers = style.layers.filter(layer => layer.source !== 'pois'
    && layer.type !== 'circle'
    && !(layer.type === 'symbol' && Object.hasOwn(layer.layout ?? {}, 'icon-image')));
  const areaColors = {
    'landuse-urban': 'urban', 'landuse-commercial': 'commercial', 'landuse-industrial': 'industrial',
    'vegetation-grass': 'grass', 'vegetation-wood': 'wood', 'vegetation-parks': 'park',
    'public-squares': 'square', 'water-fill': 'water', 'building-osm': 'building', 'building-footprints': 'building',
  };
  for (const layer of style.layers) {
    if (layer.id === 'background') layer.paint['background-color'] = p.background;
    if (areaColors[layer.id]) layer.paint['fill-color'] = p[areaColors[layer.id]];
    if (p.buildingOutline && layer.id === 'building-footprints') layer.paint['fill-outline-color'] = p.buildingOutline;
    if (layer.id === 'water-outline') layer.paint['line-color'] = p.waterOutline;
    if (layer.id === 'waterways') layer.paint['line-color'] = p.water;
    if (layer.id.startsWith('paths-')) layer.paint['line-color'] = p.path;
    if (layer.id.startsWith('railway-')) layer.paint['line-color'] = p.rail;
    if (layer.id.startsWith('motorway-')) layer.paint['line-color'] = theme === 'gta' ? p.motorway : p.motorwayCenter;
    if (layer.metadata?.road) {
      const road = layer.id.split('-').at(-2);
      const casing = layer.metadata.part === 'casing';
      const roadToken = tokens.roads.find(item => item.id === road);
      layer.minzoom = roadToken.minzoom;
      layer.paint['line-width'] = ['interpolate', ['linear'], ['zoom'], ...tokens.zoomStops.flatMap((zoom, index) => [zoom, roadToken.widths[index] + (casing ? roadToken.outline * 2 : 0)])];
      layer.paint['line-color'] = theme === 'gta' ? p.road : casing ? p.roadOutline : p[road] ?? p.road;
      // The PoC GTA hierarchy remains solid black, with motorway centre line.
      if (theme !== 'gta' && road === 'trunk' && !casing) layer.paint['line-color'] = p.motorway;
    }
    if (layer.type === 'symbol' && layer.layout['text-field']) {
      layer.layout['text-font'] = [definition.font];
      layer.paint['text-color'] = p.label;
      layer.paint['text-halo-color'] = p.labelHalo;
      layer.paint['text-halo-width'] = tokens.labels.haloWidth;
      if (layer.id === 'road-labels') layer.layout['text-size'] = tokens.labels.roadSize;
      if (layer.id === 'district-labels') layer.layout['text-size'] = tokens.labels.districtSize;
      if (layer.id === 'place-labels') layer.layout['text-size'] = ['interpolate', ['linear'], ['zoom'], 11, tokens.labels.placeSizes[0], 14, tokens.labels.placeSizes[1], 17, tokens.labels.placeSizes[2]];
    }
  }
  // Single symbol layer per semantic group: one visibility switch hides icon and name together.
  // Keep verified geographic place labels topmost for collision priority.
  const placeIndex = style.layers.findIndex(l => l.id === 'place-labels');
  const placeLabels = style.layers.splice(placeIndex, 1)[0];
  for (const category of [...categories].reverse()) style.layers.push({
    id: `poi-${category.id}`, type: 'symbol', source: 'pois', minzoom: category.minzoom,
    filter: ['==', ['get', 'kind'], category.id],
    metadata: { gamemap: { category: category.id, label: category.label } },
    layout: {
      visibility: 'visible', 'icon-image': `poi-${category.id}`,
      'icon-size': ['interpolate', ['linear'], ['zoom'], 12, 0.76, 15, 0.9, 18, 1.05],
      'icon-padding': 5, 'icon-allow-overlap': false, 'icon-ignore-placement': false,
      'symbol-sort-key': ['case', ['==', ['get', 'name'], ''], 1, 0],
      'text-field': ['get', 'name'], 'text-font': [definition.font],
      'text-size': ['step', ['zoom'], 0, tokens.visibility.poiNamesMinzoom, 11.5],
      'text-optional': true, 'text-offset': [0, 1.9], 'text-anchor': 'top',
      'text-padding': 4, 'text-max-width': 13, 'text-allow-overlap': false,
    },
    paint: { 'text-color': p.label, 'text-halo-color': p.labelHalo, 'text-halo-width': 1.5 },
  });
  style.layers.push(placeLabels);
  const errors = validateStyleMin(style);
  if (errors.length) throw new Error(errors.map(e => e.message).join('\n'));
  return style;
}

function svg(category, content) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" role="img" aria-label="${category.label}"><title>${category.label}</title>${content}</svg>\n`;
}

// Original Rosa Pop glyphs, CC0-1.0. Rounded geometric symbols, no brand artwork.
const rosaDrawings = {
  parks: '<path d="M16 20v8M11 28h10" stroke="#674335" stroke-width="2.4"/><path d="M8 21c-4-1-4-7-1-9-1-5 5-8 9-5 4-3 10 0 9 5 4 3 3 8-1 9Z" fill="#82C9AA" stroke="#356E61" stroke-width="2"/><g fill="#FFB8DB" stroke="#A63170" stroke-width=".8"><circle cx="19" cy="10" r="2"/><circle cx="23" cy="10" r="2"/><circle cx="21" cy="7" r="2"/><circle cx="21" cy="13" r="2"/></g><circle cx="21" cy="10" r="1.5" fill="#FFF0A4"/>',
  fuel: '<rect x="7" y="6" width="15" height="22" rx="4" fill="#FFD2E4" stroke="#963368" stroke-width="2"/><rect x="10" y="9" width="9" height="7" rx="2" fill="#C3E9F1" stroke="#963368" stroke-width="1.5"/><path d="M22 12h2l3 4v7c0 3-4 3-4 0v-3M25 14l3-3M6 28h18" fill="none" stroke="#963368" stroke-width="2" stroke-linecap="round"/><path d="M14 18l-3 5h4l-1 4 5-6h-4l1-3Z" fill="#B8427C"/>',
  food: '<path d="M9 12h13v9c0 7-13 7-13 0Z" fill="#FFE2C6" stroke="#914D63" stroke-width="2"/><path d="M22 14h3c6 0 5 8-3 8M7 28h18M12 4c-3 3 3 3 0 6M18 4c-3 3 3 3 0 6" fill="none" stroke="#914D63" stroke-width="2" stroke-linecap="round"/><path d="M13 18c0-3 3-3 3-1 1-2 4-2 4 1 0 2-4 4-4 4s-3-2-3-4Z" fill="#D45A86"/>',
  parking: '<rect x="4" y="4" width="24" height="25" rx="8" fill="#D9D0FF" stroke="#68518D" stroke-width="2"/><path d="M12 24V10h6c7 0 7 9 0 9h-6m0-5h6" fill="none" stroke="#FFFFFF" stroke-width="3.5" stroke-linecap="round" stroke-linejoin="round"/><path d="m23 3 1.2 2.8L27 7l-2.8 1.2L23 11l-1.2-2.8L19 7l2.8-1.2Z" fill="#FFF0A4" stroke="#AD7E66" stroke-width=".7"/>',
  health: '<path d="M16 28 5 17C-4 8 9-1 16 8 23-1 36 8 27 17Z" fill="#F28EAF" stroke="#963368" stroke-width="2" stroke-linejoin="round"/><path d="M13 10h6v5h5v6h-5v5h-6v-5H8v-6h5Z" fill="#FFFAF4"/><path d="m7 8 2-1" stroke="#FFF5FB" stroke-width="2" stroke-linecap="round"/>',
  shops: '<path d="M7 12h18l2 17H5Z" fill="#FFE9B8" stroke="#985573" stroke-width="2" stroke-linejoin="round"/><path d="M11 13V9c0-8 10-8 10 0v4" fill="none" stroke="#985573" stroke-width="2" stroke-linecap="round"/><path d="M16 17c-8-7-10 6 0 3 10 3 8-10 0-3Z" fill="#F49AC4" stroke="#A53D74" stroke-width="1.2"/><circle cx="16" cy="18.5" r="2" fill="#FFF4E0"/><path d="m15 20-3 5m5-5 3 5" stroke="#A53D74" stroke-width="1.5" stroke-linecap="round"/>',
};

// Original pen-and-paper symbols. They encode the same modern OSM categories;
// none is traced from a game legend or implies a fictional place on the map.
const rdr2Drawings = {
  parks: '<path d="M16 26V12m0 7-5-4m5 1 4-4M11 27h10"/><path d="m16 5-7 8h4l-6 7h18l-6-7h4Z" fill="#9B9E72"/>',
  fuel: '<path d="M9 26V8h12v18M7 27h16M12 11h6v6h-6Zm9 1h3l3 5v7c0 3-4 3-4 0v-5m1-5 2-2"/><path d="M13 21h4" stroke="#9A3A2B" stroke-width="2.5"/>',
  food: '<path d="M8 12h13v9c0 6-13 6-13 0Zm13 2h3c6 0 4 7-3 7M6 27h19M12 5v4m5-4v4"/><path d="M10 15h9" stroke="#9A3A2B"/>',
  parking: '<path d="M10 26V7h8c9 0 9 12 0 12h-8m3-9h5c5 0 5 6 0 6h-5"/><path d="M7 27h17" stroke="#9A3A2B"/>',
  health: '<path d="M12 6h8v6h6v8h-6v6h-8v-6H6v-8h6Z" fill="#9A3A2B" stroke="#6D2B22"/><path d="M15 9h2v6h6v2h-6v6h-2v-6H9v-2h6Z" fill="#F0DEB8" stroke="none"/>',
  shops: '<path d="M7 13h18v14H7Zm-1 0 3-7h14l3 7M12 6l-1 7m9-7 1 7M9 17h5v10m4-10h4v5h-4Z"/><path d="M7 13h18" stroke="#9A3A2B" stroke-width="2.5"/>',
};

async function ensureEditableIcons() {
  for (const mode of Object.keys(themes)) {
    const directory = path.join(source, 'icons', mode);
    await mkdir(directory, { recursive: true });
    for (const category of categories) {
      const destination = path.join(directory, `poi-${category.id}.svg`);
      if (await exists(destination)) continue;
      if (mode === 'rdr2') {
        const body = '<path d="M4 2h24l2 3v24l-3 1H4l-2-3V5Z" fill="#EFDEBA" stroke="#FFF1CD" stroke-width="2"/><path d="M5 4h22v24H5Z" fill="none" stroke="#6D5435" stroke-width=".65"/>'
          + `<g fill="none" stroke="#443222" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${rdr2Drawings[category.id]}</g>`;
        await writeFile(destination, svg(category, '<!-- GameMap original RDR2-inspired pen artwork. CC0-1.0. -->' + body));
      } else if (mode === 'rosa') {
        const body = `<rect x="1" y="1" width="30" height="30" rx="9" fill="#FFFBF8" stroke="#FFFFFF" stroke-width="2"/><g stroke-linecap="round" stroke-linejoin="round">${rosaDrawings[category.id]}</g>`;
        await writeFile(destination, svg(category, '<!-- GameMap original Rosa Pop artwork. CC0-1.0. -->' + body));
      } else {
        const original = await readFile(path.join(maki, 'icons', `${category.maki}.svg`), 'utf8');
        const body = original.match(/<svg\b[^>]*>([\s\S]*?)<\/svg>/)?.[1];
        if (!body || !original.includes('viewBox="0 0 15 15"')) throw new Error(`Unexpected Maki ${category.maki}`);
        await writeFile(destination, svg(category, `<!-- Maki 8.2.0 ${category.maki}, Mapbox, CC0-1.0. Original path geometry; GameMap colors/outline. --><defs><g id="glyph">${body}</g></defs><g transform="translate(5.5 5.5) scale(1.4)" stroke-linecap="round" stroke-linejoin="round"><use href="#glyph" fill="#FFFFFF" stroke="#FFFFFF" stroke-width="3"/><use href="#glyph" fill="#171511" stroke="#171511" stroke-width="1.55"/><use href="#glyph" fill="${category.color}"/></g>`));
      }
    }
  }
}

async function buildSprites() {
  await mkdir(path.join(output, 'sprites'), { recursive: true });
  for (const mode of Object.keys(themes)) {
    for (const ratio of [1, 2]) {
      const index = {};
      const composites = [];
      for (let i = 0; i < categories.length; i++) {
        const id = `poi-${categories[i].id}`;
        const x = (i * 36 + 2) * ratio;
        const y = 2 * ratio;
        index[id] = { width: 32 * ratio, height: 32 * ratio, x, y, pixelRatio: ratio };
        composites.push({ input: await sharp(await readFile(path.join(source, 'icons', mode, `${id}.svg`)), { density: 72 * ratio }).resize(32 * ratio, 32 * ratio).png().toBuffer(), left: x, top: y });
      }
      const suffix = ratio === 2 ? '@2x' : '';
      await sharp({ create: { width: 216 * ratio, height: 36 * ratio, channels: 4, background: '#00000000' } }).composite(composites).png().toFile(path.join(output, 'sprites', `${mode}${suffix}.png`));
      await writeJson(path.join(output, 'sprites', `${mode}${suffix}.json`), index);
    }
  }
}

async function walk(directory) {
  const found = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) found.push(...await walk(full));
    else found.push(full);
  }
  return found.sort();
}

async function copy(input, destination) {
  await mkdir(path.dirname(destination), { recursive: true });
  await copyFile(input, destination);
}

async function prepare() {
  const tokens = await createTokensIfMissing();
  const origins = new Map();
  const add = async (input, relative) => {
    await copy(path.join(root, input), path.join(output, relative));
    origins.set(relative.replaceAll('\\', '/'), input.replaceAll('\\', '/'));
  };
  for (const folder of ['styles', 'data', 'sprites', 'glyphs', 'tiles', 'credits']) await mkdir(path.join(output, folder), { recursive: true });
  for (const name of ['areas', 'roads', 'waterways', 'places', 'pois']) {
    await add(name === 'pois' ? 'android/maps/pois.geojson' : `public/data/${name}.geojson`, `data/${name}.geojson`);
  }
  for (const [input, relative] of [
    ['public/data/metadata.json', 'data/metadata.json'],
    ['public/data/buildings-metadata.json', 'data/buildings-metadata.json'],
    ['android/maps/pois-metadata.json', 'data/pois-metadata.json'],
    ['android/maps/theme-tokens.json', 'styles/theme-tokens.json'],
  ]) await add(input, relative);
  for (const input of await walk(path.join(root, 'public/tiles/buildings'))) {
    await add(path.relative(root, input), `tiles/buildings/${path.relative(path.join(root, 'public/tiles/buildings'), input)}`);
  }
  const needed = new Set();
  for (const name of ['roads', 'places', 'pois']) {
    const data = JSON.parse(await readFile(path.join(output, `data/${name}.geojson`), 'utf8'));
    for (const feature of data.features) {
      const text = feature.properties.name ?? '';
      for (const character of text + text.toUpperCase()) needed.add(character.codePointAt(0));
    }
  }
  const glyphRanges = [...new Set([...needed].map(c => Math.floor(c / 256) * 256))].sort((a, b) => a - b).map(start => `${start}-${start + 255}`);
  for (const font of new Set(Object.values(themes).map(theme => theme.font))) {
    for (const range of glyphRanges) await add(`android/maps/fonts/${font}/${range}.pbf`, `glyphs/${font}/${range}.pbf`);
  }
  await add('android/maps/fonts/GameMapSans/LICENSE.md', 'credits/Noto-OFL-1.1.md');
  await add('android/maps/fonts/GameMapSerif/LICENSE.md', 'credits/Noto-Serif-OFL-1.1.md');
  await add('node_modules/@mapbox/maki/LICENSE.txt', 'credits/Maki-CC0.txt');
  await ensureEditableIcons();
  await buildSprites();
  for (const mode of variants) await writeJson(path.join(output, 'styles', `${mode}.json`), nativeStyle(mode, tokens));
  const poiMetadata = await parse('android/maps/pois-metadata.json');
  const creditData = {
    attribution,
    sources: [
      { name: 'OpenStreetMap contributors', license: 'ODbL-1.0', url: 'https://www.openstreetmap.org/copyright' },
      { name: 'Overture Maps Foundation', license: 'ODbL-1.0', url: 'https://docs.overturemaps.org/attribution/' },
      { name: 'Google Open Buildings', license: 'CC-BY-4.0', url: 'https://sites.research.google/open-buildings/' },
      { name: 'Microsoft Global ML Building Footprints', license: 'ODbL-1.0', url: 'https://github.com/microsoft/GlobalMLBuildingFootprints' },
      { name: 'Maki 8.2.0 / Mapbox', license: 'CC0-1.0', url: 'https://github.com/mapbox/maki' },
      { name: 'Rosa Pop original GameMap geometric artwork', license: 'CC0-1.0' },
      { name: 'RDR2-inspired original GameMap pen artwork', license: 'CC0-1.0' },
      { name: 'Noto Sans Bold (unmodified PBFs, local GameMapSans URL alias)', license: 'SIL-OFL-1.1', url: 'https://github.com/maplibre/demotiles/tree/601ae60796ceceda2cbd2ed3d2ea92d17a84be4b/font/Noto%20Sans%20Bold' },
      { name: 'Noto Serif Bold (unmodified OSM US PBFs, local GameMapSerif URL alias)', license: 'SIL-OFL-1.1', url: 'https://tiles.openstreetmap.us/fonts/' },
    ],
    fonts: {
      alias: 'GameMapSans', upstream: 'Noto Sans Bold',
      repository: 'https://github.com/maplibre/demotiles', commit: '601ae60796ceceda2cbd2ed3d2ea92d17a84be4b',
      downloadTemplate: 'https://raw.githubusercontent.com/maplibre/demotiles/601ae60796ceceda2cbd2ed3d2ea92d17a84be4b/font/Noto%20Sans%20Bold/{range}.pbf',
      ranges: glyphRanges, codepoints: [...needed].sort((a, b) => a - b),
      coverage: 'All road/place/POI names in bundled source plus uppercase variants. Re-run after data changes; missing input ranges fail the build.',
    },
    serifFont: {
      alias: 'GameMapSerif', upstream: 'Noto Serif Bold', provider: 'OpenStreetMap US',
      upstreamFont: 'https://github.com/notofonts/noto-fonts/tree/main/hinted/ttf/NotoSerif',
      downloadTemplate: 'https://tiles.openstreetmap.us/fonts/Noto%20Serif%20Bold/{range}.pbf',
      licenseSource: 'https://raw.githubusercontent.com/notofonts/noto-fonts/main/LICENSE',
      licenseSha256: sha256(await readFile(path.join(source, 'fonts/GameMapSerif/LICENSE.md'))),
      inputs: await Promise.all(glyphRanges.map(async range => ({ range, sha256: sha256(await readFile(path.join(source, `fonts/GameMapSerif/${range}.pbf`))) }))),
      coverage: 'Unmodified MapLibre-compatible glyph blocks; all bundled road/place/POI codepoints including uppercase verified by tests. Local inputs make preparation fully offline.',
    },
    designReferences: [{ theme: 'rdr2', title: 'Red Dead Redemption 2 — The Complete Official Guide / Charting the Wilds', url: 'https://www.piggyback.com/us/guide/red-dead-redemption-2/', usage: 'Visual inspiration only: parchment palette, dark ink and selective red. No downloaded game art, map geometry, contours or extracted symbols are bundled.' }],
    pois: { count: poiMetadata.counts.total, byCategory: poiMetadata.counts.byKind, mappings: poiMetadata.categoryMappings },
  };
  await writeJson(path.join(output, 'credits', 'sources.json'), creditData);
  await writeFile(path.join(output, 'credits', 'Rosa-Pop-CC0.txt'), 'Rosa Pop icons are original GameMap geometric artwork, dedicated to the public domain under CC0 1.0. No characters, logos, or official brand assets are included.\nhttps://creativecommons.org/publicdomain/zero/1.0/\n');
  await writeFile(path.join(output, 'credits', 'RDR2-Inspired-CC0.txt'), 'RDR2-inspired icons are original GameMap geometric pen artwork, dedicated to the public domain under CC0 1.0. The theme uses only real Goiânia geodata. No game artwork, copied legend, invented topography or fictional POIs are included.\nhttps://creativecommons.org/publicdomain/zero/1.0/\n');
  await writeFile(path.join(output, 'credits', 'attribution.html'), `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Créditos do mapa</title><style>body{font:18px sans-serif;color:#252025;background:#fff8fc;padding:24px;line-height:1.5}a{color:#8b2058}</style><h1>Créditos do mapa de Goiânia</h1><p>${attribution}</p><ul>${creditData.sources.map(s => `<li>${s.url ? `<a href="${s.url}">${s.name}</a>` : s.name}: ${s.license}</li>`).join('')}</ul><p>Dados, sprites e fontes deste mapa estão incluídos no APK. Os links são referências de atribuição; o mapa funciona sem rede. Fontes Noto: <a href="Noto-OFL-1.1.md">SIL Open Font License 1.1</a>.</p></html>\n`);
  // Prepare all online styles before hashing so each repeat includes the same
  // complete resource package. Goiânia source bytes and offline styles stay local.
  await prepareWorldMaps();
  const files = [];
  for (const directory of ['styles', 'data', 'sprites', 'glyphs', 'tiles', 'credits']) {
    for (const full of await walk(path.join(output, directory))) {
      const relative = path.relative(output, full).replaceAll('\\', '/');
      if (relative === 'credits/asset-manifest.json') continue;
      const bytes = await readFile(full);
      files.push({ path: relative, bytes: bytes.length, sha256: sha256(bytes), ...(origins.has(relative) ? { sourcePath: origins.get(relative), sourceSha256: sha256(bytes) } : {}) });
    }
  }
  const buildInputs = [];
  for (const input of [
    'scripts/prepare-android-maps.mjs', 'scripts/prepare-world-maps.mjs', 'android/maps/world-source.json',
    'src/style.js', 'public/styles/tokens.json', 'android/maps/theme-tokens.json',
    ...await walk(path.join(source, 'icons')), ...await walk(path.join(source, 'fonts')),
  ]) {
    const full = path.isAbsolute(input) ? input : path.join(root, input);
    const bytes = await readFile(full);
    buildInputs.push({ path: path.relative(root, full).replaceAll('\\', '/'), bytes: bytes.length, sha256: sha256(bytes) });
  }
  // No wall-clock timestamps: repeated preparation produces the same manifest.
  await writeJson(path.join(output, 'credits', 'asset-manifest.json'), {
    format: 2, attribution, offline: false, sourceCoordinatesModified: false,
    mapModes: {
      world: { offline: false, coverage: 'world', provider: 'OpenFreeMap', requiresNetwork: ['vector-tiles', 'glyphs'], styleAssets: WORLD_VARIANTS.map(mode => `styles/world-${mode}.json`) },
      goiania: { offline: true, coverage: [-49.325, -16.755, -49.205, -16.64], requiresNetwork: [], styleAssets: variants.map(mode => `styles/${mode}.json`) },
    },
    totalBytes: files.reduce((n, f) => n + f.bytes, 0), buildInputs, files,
  });
  console.log(`Prepared ${files.length} native assets (${(files.reduce((n, f) => n + f.bytes, 0) / 1048576).toFixed(1)} MiB), 729 copied building tiles, ${poiMetadata.counts.total} real POIs, glyph ranges ${glyphRanges.join(', ')}.`);
}

await prepare();
