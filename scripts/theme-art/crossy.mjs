/** Browser-only art study. Original geography and POI identities stay intact. */
export const definition = {
  id: 'crossy',
  title: 'Crossy Road',
  kicker: 'VOXEL / LITTLE CITY',
  description: 'Goiânia vira uma pequena cidade de brinquedo: asfalto escuro, copas cúbicas e telhados de creme sob uma luz suave.',
  details: ['Prédios reais · alturas ilustrativas de 5–25 m', 'Parques com árvores em blocos e água de azulejo', 'Ícones originais · ruas e lugares reais'],
  accent: '#a4ce62',
  referenceUrl: 'https://www.crossyroad.com/crossy-road-classic-press-kit',
  referenceLabel: 'Crossy Road · referência oficial',
};

export const camera = { center: [-49.2618, -16.684], pitch: 52, bearing: -25, zoom: 16.7 };

const clone = value => structuredClone(value);
const zoom = (low, high, from = 13, to = 18) => ['interpolate', ['linear'], ['zoom'], from, low, to, high];
const imageId = (name, night) => `crossy-${name}-${night ? 'night' : 'day'}`;
const isBuilding = layer => layer.type === 'fill' && /^building-(?:osm|footprints(?:-local)?)$/.test(layer.id);
const vegetation = /^(vegetation-(?:grass|wood|parks)|protected-areas)$/;
const roadKinds = ['residential', 'unclassified', 'living_street', 'road', 'tertiary', 'secondary', 'busway', 'primary', 'trunk', 'motorway'];

const palettes = {
  day: { ground: '#a1c677', grass: '#a9d274', grassLight: '#b6dc82', grassDark: '#9bc96a', park: '#91bf59', forest: '#86b651',
    urban: '#a8c583', commercial: '#c8c7a0', industrial: '#b6c0ab', pavement: '#d8d1b5', curb: '#ddd8c1', asphalt: '#555961',
    major: '#4c5058', water: '#58b9ce', waterLight: '#70c9d7', waterDark: '#46a9c2', shoreline: '#9fe0dd',
    roof: '#f5eed7', shadow: '#516a4d', text: '#303d35', halo: '#eff2d8', leaf: '#84bd51', leafTop: '#b1d56c', leafSide: '#5e984b',
    buildings: ['#dbd8c4', '#c3d3c4', '#e2c9b4', '#d0c7cf', '#e6dfc5'] },
  night: { ground: '#536f57', grass: '#5f805b', grassLight: '#698c62', grassDark: '#587956', park: '#517c53', forest: '#456d4c',
    urban: '#627b62', commercial: '#8a8a70', industrial: '#717e77', pavement: '#939587', curb: '#a5a690', asphalt: '#363e4b',
    major: '#303844', water: '#387c96', waterLight: '#4a8ca1', waterDark: '#2c6d87', shoreline: '#77b6bb',
    roof: '#d2cdb9', shadow: '#293e38', text: '#eef1d8', halo: '#34443e', leaf: '#558e50', leafTop: '#83ae62', leafSide: '#3d7149',
    buildings: ['#9caaa0', '#91a7a0', '#b8a693', '#a5a2b0', '#c3baa0'] },
};

// A stable material variation from existing feature identity, never new geometry.
const identity = ['to-string', ['coalesce', ['get', 'gers_id'], ['get', 'osm_id'], ['id'], '7']];
const material = ['max', 0, ['index-of', ['slice', identity, 0, 1], '0123456789abcdef']];
const height = ['min', 25, ['max', 5, ['case',
  ['>', ['to-number', ['get', 'height'], 0], 0], ['to-number', ['get', 'height'], 0],
  ['>', ['to-number', ['get', 'levels'], 0], 0], ['*', ['to-number', ['get', 'levels'], 0], 3],
  ['>', ['to-number', ['get', 'building:levels'], 0], 0], ['*', ['to-number', ['get', 'building:levels'], 0], 3],
  ['+', 6, ['%', ['*', material, 3], 11]],
]]];

function geometryLayer(layer, id, type, paint, extra = {}) {
  return { id, type, source: layer.source,
    ...(layer['source-layer'] ? { 'source-layer': layer['source-layer'] } : {}),
    ...(layer.filter ? { filter: clone(layer.filter) } : {}),
    ...(layer.minzoom != null ? { minzoom: layer.minzoom } : {}),
    ...(layer.maxzoom != null ? { maxzoom: layer.maxzoom } : {}),
    ...(layer.layout?.visibility ? { layout: { visibility: layer.layout.visibility } } : {}),
    metadata: { navskinLab: { illustration: true, follows: layer.id } }, paint, ...extra };
}

function scaleWidth(value, multiplier, padding = 0) {
  if (typeof value === 'number') return value * multiplier + padding;
  if (!Array.isArray(value) || value[0] !== 'interpolate') return value;
  return value.map((item, i) => i >= 4 && i % 2 === 0 && typeof item === 'number' ? item * multiplier + padding : item);
}

export function createStyle(base, { night = false } = {}) {
  const style = clone(base), p = palettes[night ? 'night' : 'day'];
  const layers = [];
  const img = name => imageId(name, night);
  for (const layer of style.layers) {
    if (/^(art-|lab-|crossy-)/.test(layer.id)) continue;
    const paint = layer.paint ??= {}, layout = layer.layout ??= {};

    if (layer.type === 'background') {
      paint['background-color'] = p.ground;
      paint['background-pattern'] = img('lawn');
    }
    if (layer.id.startsWith('landuse-')) {
      paint['fill-color'] = layer.id === 'landuse-industrial' ? p.industrial : layer.id === 'landuse-commercial' ? p.commercial : p.urban;
      paint['fill-opacity'] = 1;
      delete paint['fill-pattern'];
    }
    if (vegetation.test(layer.id)) {
      paint['fill-color'] = layer.id === 'vegetation-wood' ? p.forest : p.park;
      paint['fill-pattern'] = img(layer.id === 'vegetation-grass' ? 'lawn' : 'grove');
      paint['fill-opacity'] = 1;
      if (layer.id === 'vegetation-parks') layers.push(geometryLayer(layer, 'crossy-park-edge', 'line', {
        'line-color': p.pavement, 'line-width': zoom(1.2, 5), 'line-opacity': .95,
      }));
    }
    if (layer.id === 'public-squares') {
      paint['fill-color'] = p.pavement;
      paint['fill-pattern'] = img('paving');
    }
    if (layer.id === 'water-fill') {
      paint['fill-color'] = p.water;
      paint['fill-pattern'] = img('water');
      paint['fill-opacity'] = 1;
    }
    if (layer.id === 'water-outline') {
      paint['line-color'] = p.shoreline;
      paint['line-width'] = zoom(2, 5);
    }
    if (layer.id === 'waterways') {
      paint['line-color'] = p.water;
      paint['line-width'] = zoom(.8, 6);
    }

    if (isBuilding(layer)) {
      layers.push(geometryLayer(layer, `crossy-shadow-${layer.id}`, 'fill', {
        'fill-color': p.shadow, 'fill-opacity': .28, 'fill-translate': [4, 5], 'fill-translate-anchor': 'map',
      }));
      const colors = ['match', ['%', material, 5], 0, p.buildings[0], 1, p.buildings[1], 2, p.buildings[2], 3, p.buildings[3], p.buildings[4]];
      // Flat rooftops remain in 2D. Native volumes are supplied by map-volumes.mjs.
      layers.push(geometryLayer(layer, layer.id, 'fill', {
        'fill-color': colors, 'fill-outline-color': p.roof, 'fill-opacity': 1,
      }));
      continue;
    }

    if (layer.metadata?.road) {
      const casing = layer.metadata.part === 'casing';
      const major = /-(?:primary|trunk|motorway)-/.test(layer.id);
      paint['line-color'] = casing ? p.curb : major ? p.major : p.asphalt;
      paint['line-width'] = scaleWidth(paint['line-width'], 1.25, casing ? 1.8 : 0);
      layout['line-cap'] = 'butt';
      layout['line-join'] = 'miter';
      // The original grade filters and tunnel opacity/dashes remain in place.
    }
    if (layer.id.startsWith('paths-')) {
      paint['line-color'] = night ? '#b5ad8b' : '#e6d7a5';
      paint['line-width'] = zoom(.7, 3);
      paint['line-opacity'] = .95;
      delete paint['line-dasharray'];
    }
    if (layer.id.startsWith('railway-')) {
      paint['line-color'] = night ? '#a8a893' : '#b6b59b';
      paint['line-width'] = zoom(.7, 2.1);
      paint['line-dasharray'] = [1.4, 2.8];
    }
    if (/^motorway-.*-center$/.test(layer.id)) {
      // Reuse each grade's centerline pass. These are graphic road accents,
      // not measured lane counts, crosswalks or road-surface survey markings.
      const roadProperty = layer.source === 'world' ? 'class' : 'kind';
      layer.filter = ['all', ...layer.filter.slice(1).map(part => JSON.stringify(part).includes('"class"') || JSON.stringify(part).includes('"kind"') ? ['in', ['get', roadProperty], ['literal', layer.source === 'world' ? ['minor', 'tertiary', 'secondary', 'busway', 'primary', 'trunk', 'motorway'] : roadKinds]] : part)];
      layer.minzoom = 16;
      layer.metadata = { navskinLab: { illustration: true, purpose: 'Stylized center accent; no lane count inferred.' } };
      paint['line-color'] = ['match', ['get', roadProperty], ['motorway', 'trunk'], '#e8c66b', '#edeeda'];
      paint['line-width'] = zoom(.65, 1.2, 16, 19);
      paint['line-dasharray'] = [4.5, 7];
      paint['line-opacity'] = .76;
      layout['line-cap'] = 'butt';
      layout['line-join'] = 'miter';
    }

    if (layer.id === 'road-oneway-directions') {
      layout['icon-image'] = 'crossy-oneway';
      layout['icon-size'] = zoom(.44, .62, 16.5, 19);
      layout['symbol-spacing'] = 190;
      // The east-facing sprite and original reverse/forward rotate expression
      // preserve the source's actual one-way direction.
      paint['icon-opacity'] = .8;
    }
    if (layer.type === 'symbol' && layout['text-field']) {
      paint['text-color'] = p.text;
      paint['text-halo-color'] = p.halo;
      paint['text-halo-width'] = 1.4;
      paint['text-halo-blur'] = 0;
    }
    if (layer.id === 'road-labels') {
      layout['text-size'] = zoom(11, 14, 15, 19);
      layout['text-letter-spacing'] = .025;
      layout['symbol-spacing'] = 245;
      paint['text-color'] = '#f4f0de';
      paint['text-halo-color'] = p.major;
      paint['text-halo-width'] = 1.8;
    }
    if (layer.id === 'district-labels') {
      layout['text-size'] = 12;
      layout['text-letter-spacing'] = .08;
      paint['text-opacity'] = .8;
    }
    if (layer.id === 'place-labels') {
      layout['text-size'] = zoom(14, 19, 14, 19);
      layout['text-letter-spacing'] = .04;
      layout['text-padding'] = 12;
    }
    if (layer.id.startsWith('poi-')) {
      layout['icon-image'] = `crossy-${layer.id}`;
      layout['icon-size'] = zoom(.58, .94, 13, 19);
      layout['icon-padding'] = 7;
      layout['icon-anchor'] = 'bottom';
      layout['icon-pitch-alignment'] = 'viewport';
      layout['icon-rotation-alignment'] = 'viewport';
      layout['text-offset'] = [0, .55];
      layout['text-size'] = ['step', ['zoom'], 0, 17, 11];
    }
    layers.push(layer);
  }
  style.layers = layers;
  delete style.sprite;
  style.name = `Crossy Road · cidade de brinquedo${night ? ' à noite' : ''}`;
  style.light = { anchor: 'map', position: [1.5, 205, 35], color: night ? '#d5e5f6' : '#fff9e7', intensity: .35 };
  style.metadata ??= {};
  style.metadata.navskinLab = { theme: 'crossy', nativeAssetsChanged: false,
    geography: 'Original road grades, building footprints, parks, water and POI identities preserved.',
    illustration: 'Building heights clamped to 5–25 m, with illustrative fallback. Tree texture and centerline accents are decorative; no measured trees, lane count or live traffic is asserted.' };
  return style;
}

/** Tiny local rasterizer: sharp, antialias-free solids, no canvas or network. */
function bitmap(size, background, draw) {
  const data = new Uint8Array(size * size * 4);
  const color = hex => [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16), 255];
  const dot = (x, y, hex, alpha = 1) => {
    x = Math.floor(x); y = Math.floor(y);
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4, c = color(hex), a = Math.round(alpha * 255);
    if (a === 255 || data[i + 3] === 0) { data.set(c, i); data[i + 3] = a; return; }
    const outAlpha = a + data[i + 3] * (1 - alpha);
    for (let j = 0; j < 3; j++) data[i + j] = (c[j] * a + data[i + j] * data[i + 3] * (1 - alpha)) / outAlpha;
    data[i + 3] = outAlpha;
  };
  const rect = (x, y, w, h, hex, alpha = 1) => {
    for (let yy = y; yy < y + h; yy++) for (let xx = x; xx < x + w; xx++) dot(xx, yy, hex, alpha);
  };
  const poly = (points, hex, alpha = 1) => {
    const minY = Math.max(0, Math.floor(Math.min(...points.map(p => p[1]))));
    const maxY = Math.min(size - 1, Math.ceil(Math.max(...points.map(p => p[1]))));
    for (let y = minY; y <= maxY; y++) {
      const intersections = [];
      for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
        const a = points[i], b = points[j], scan = y + .5;
        if ((a[1] > scan) !== (b[1] > scan)) intersections.push(a[0] + (scan - a[1]) * (b[0] - a[0]) / (b[1] - a[1]));
      }
      intersections.sort((a, b) => a - b);
      for (let i = 0; i < intersections.length; i += 2) for (let x = Math.ceil(intersections[i]); x < intersections[i + 1]; x++) dot(x, y, hex, alpha);
    }
  };
  const block = (x, y, w, h, d, front, side, top) => {
    rect(x, y, w, h, front);
    poly([[x + w, y], [x + w + d, y - d], [x + w + d, y + h - d], [x + w, y + h]], side);
    poly([[x, y], [x + d, y - d], [x + w + d, y - d], [x + w, y]], top);
  };
  if (background) rect(0, 0, size, size, background);
  draw({ rect, poly, block });
  return { width: size, height: size, data, pixelRatio: 2 };
}

function tree(draw, x, y, scale, p) {
  const { rect, poly, block } = draw, s = scale;
  poly([[x - 8 * s, y + 34 * s], [x + 25 * s, y + 34 * s], [x + 39 * s, y + 23 * s], [x + 7 * s, y + 23 * s]], p.shadow, .23);
  block(x + 10 * s, y + 12 * s, 6 * s, 20 * s, 4 * s, '#967957', '#74664e', '#b09a6a');
  block(x, y, 27 * s, 21 * s, 10 * s, p.leaf, p.leafSide, p.leafTop);
  rect(x + 3 * s, y + 3 * s, 8 * s, 3 * s, p.leafTop, .65);
}

function poi(kind) {
  return bitmap(96, null, draw => {
    const { rect, poly, block } = draw;
    poly([[15, 77], [68, 77], [84, 65], [31, 65]], '#304335', .22);
    if (kind === 'parks') { tree(draw, 27, 28, 1.25, palettes.day); return; }
    if (kind === 'food') {
      block(25, 58, 41, 10, 12, '#ce944d', '#a17143', '#f0ba66');
      block(23, 51, 45, 8, 12, '#77a944', '#4d7f3b', '#a8ce63');
      block(24, 44, 43, 9, 12, '#875b42', '#604638', '#af7950');
      block(22, 38, 45, 7, 12, '#edc860', '#c5a14b', '#ffe6a0');
      block(25, 28, 40, 11, 12, '#e5ad62', '#c38c4b', '#ffd18a');
      rect(37, 22, 5, 2, '#fff3bb'); rect(54, 20, 4, 2, '#fff3bb'); return;
    }
    if (kind === 'parking') {
      block(24, 27, 37, 37, 13, '#73a5ae', '#4e7889', '#a5d2d1');
      rect(34, 35, 6, 24, '#fff8df'); rect(39, 35, 13, 6, '#fff8df'); rect(48, 40, 5, 7, '#fff8df'); rect(39, 47, 13, 5, '#fff8df');
      block(29, 68, 33, 8, 10, '#b7baa7', '#899489', '#e2e1c9'); return;
    }
    if (kind === 'fuel') {
      block(29, 30, 28, 42, 11, '#e97d64', '#ad5952', '#ffb18b');
      rect(34, 36, 17, 14, '#e9ebd8'); rect(38, 40, 11, 6, '#627b80');
      rect(34, 56, 17, 10, '#d6d8c2'); rect(64, 39, 6, 26, '#424e53'); rect(57, 62, 12, 5, '#424e53');
      rect(64, 35, 10, 7, '#424e53'); return;
    }
    if (kind === 'shops') {
      block(23, 37, 43, 34, 12, '#ebd9b7', '#bdad92', '#fff0d0');
      rect(30, 49, 16, 17, '#8baeb0'); rect(51, 49, 9, 22, '#7f9c9c');
      block(20, 31, 48, 11, 13, '#cd7361', '#a95851', '#f0987d');
      for (let x = 25; x < 66; x += 15) rect(x, 31, 7, 11, '#f7dfb2'); return;
    }
    if (kind === 'culture') {
      block(18, 66, 51, 7, 12, '#c4c3ad', '#999f91', '#f2ebd0');
      block(23, 34, 42, 29, 10, '#adb7ac', '#87968c', '#dadbc5');
      for (const x of [23, 40, 57]) block(x, 35, 8, 30, 5, '#f3ecd5', '#c2c4ac', '#fff5dc');
      block(18, 29, 51, 9, 12, '#d1d4bd', '#9ba995', '#f6efd5');
      poly([[19, 29], [47, 13], [70, 29]], '#f4e4c8'); return;
    }
    if (kind === 'religion') {
      block(24, 42, 42, 31, 11, '#eee6d0', '#c1c3ad', '#fff4dc');
      poly([[20, 43], [44, 25], [69, 43]], '#91a8b3');
      poly([[44, 25], [56, 15], [81, 33], [69, 43]], '#b7ced2');
      block(39, 24, 13, 28, 8, '#f4e9d0', '#c9c4ac', '#fff4db');
      rect(43, 12, 5, 16, '#bb955e'); rect(39, 17, 13, 5, '#bb955e'); rect(40, 57, 12, 16, '#81969b'); return;
    }
    // Health and education share a compact civic-building silhouette.
    const health = kind === 'health';
    block(21, 39, 47, 33, 12, '#f0e6cb', '#bbc3ae', '#fff2d6');
    block(18, 32, 52, 10, 13, health ? '#d87566' : '#d3ae62', health ? '#a75a55' : '#a58e55', health ? '#f39f82' : '#f6d485');
    rect(29, 48, 9, 11, '#8bafb2'); rect(52, 48, 9, 11, '#8bafb2'); rect(40, 55, 10, 17, '#728f97');
    if (health) { rect(41, 21, 8, 23, '#fff5db'); rect(34, 28, 22, 8, '#fff5db'); }
    else { block(35, 17, 20, 14, 7, '#d9cfaa', '#b0ad91', '#fff0c6'); rect(44, 20, 3, 10, '#8d9c91'); }
  });
}

export async function getImages() {
  const images = {};
  for (const night of [false, true]) {
    const p = palettes[night ? 'night' : 'day'];
    images[imageId('lawn', night)] = bitmap(128, p.grass, ({ rect }) => {
      rect(0, 0, 64, 64, p.grassLight, .28); rect(64, 64, 64, 64, p.grassDark, .26);
      rect(16, 22, 9, 4, p.grassLight); rect(87, 51, 6, 4, p.grassDark); rect(48, 99, 8, 4, p.grassLight);
      rect(117, 17, 5, 3, p.grassDark); rect(94, 112, 7, 4, p.grassLight);
    });
    images[imageId('grove', night)] = bitmap(128, p.park, draw => {
      draw.rect(0, 0, 64, 64, p.grass, .23); draw.rect(64, 64, 64, 64, p.grassDark, .19);
      tree(draw, 11, 27, 1.1, p); tree(draw, 76, 89, 1.1, p);
      draw.rect(81, 28, 8, 4, p.grassLight); draw.rect(34, 110, 7, 4, p.grassLight);
    });
    images[imageId('water', night)] = bitmap(128, p.water, ({ rect }) => {
      rect(0, 0, 64, 64, p.waterLight, .35); rect(64, 64, 64, 64, p.waterDark, .3);
      rect(0, 0, 128, 2, p.waterLight, .45); rect(0, 64, 128, 2, p.waterLight, .45);
      rect(0, 0, 2, 128, p.waterLight, .45); rect(64, 0, 2, 128, p.waterLight, .45);
      rect(17, 25, 24, 3, p.shoreline, .6); rect(87, 89, 20, 3, p.shoreline, .5);
    });
    images[imageId('paving', night)] = bitmap(128, p.pavement, ({ rect }) => {
      for (const t of [0, 32, 64, 96]) { rect(t, 0, 1, 128, p.curb); rect(0, t, 128, 1, p.curb); }
    });
  }
  for (const kind of ['parks', 'health', 'food', 'shops', 'parking', 'fuel', 'education', 'culture', 'religion']) images[`crossy-poi-${kind}`] = poi(kind);
  images['crossy-oneway'] = bitmap(64, null, ({ rect, poly }) => {
    rect(13, 28, 25, 8, '#f5f1da');
    poly([[32, 20], [48, 32], [32, 44]], '#f5f1da');
  });
  return images;
}
