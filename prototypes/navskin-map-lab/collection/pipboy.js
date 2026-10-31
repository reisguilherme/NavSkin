/**
 * Browser-only drawing study. Every polygon, road and POI is from the original
 * Goiânia pack. The phosphor graphics below are original, generated in memory.
 */
export const definition = {
  id: 'pipboy', title: 'Pip-Boy', kicker: 'FÓSFORO VERDE / MAPA LOCAL',
  description: 'Goiânia no visor de um terminal de pulso: arquitetura em fio, água hachurada e sinais verdes sobre vidro escuro.',
  details: ['Prédios em contornos de fósforo', 'Lagos com hachura cruzada', 'Nove silhuetas de terminal'],
  accent: '#a3f78c',
  referenceUrl: 'https://help.bethesda.net/app/answers/detail/a_id/31464/~/what-can-the-fallout-4-pip-boy-app-do%3F',
  referenceLabel: 'Pip-Boy · Bethesda',
};

export const camera = { center: [-49.2618, -16.684], zoom: 16.4, pitch: 0, bearing: 0 };

const clone = value => structuredClone(value);
const zoom = (a, b, start = 13, end = 18) => ['interpolate', ['linear'], ['zoom'], start, a, end, b];
const building = layer => layer.type === 'fill' && /^building-(?:footprints(?:-local)?|osm)$/.test(layer.id);
const vegetation = layer => /^(vegetation-|protected-areas)/.test(layer.id);

function scaledWidth(value, factor, extra = 0) {
  if (typeof value === 'number') return Math.max(.2, value * factor + extra);
  if (!Array.isArray(value) || value[0] !== 'interpolate') return value;
  return value.map((item, index) => index >= 4 && index % 2 === 0 && typeof item === 'number'
    ? Math.max(.2, item * factor + extra) : item);
}

function contour(layer, name, paint) {
  const result = { id: `pipboy-${name}-${layer.id}`, type: 'line', source: layer.source,
    minzoom: layer.minzoom ?? 0, paint };
  if (layer['source-layer']) result['source-layer'] = layer['source-layer'];
  if (layer.filter) result.filter = clone(layer.filter);
  if (layer.maxzoom !== undefined) result.maxzoom = layer.maxzoom;
  if (layer.layout?.visibility) result.layout = { visibility: layer.layout.visibility };
  return result;
}

export function createStyle(base, { night = false } = {}) {
  const style = clone(base);
  // Nothing in this study references the pink sprite atlas or its illustration.
  delete style.sprite;
  const p = night
    ? { ground: '#020a04', land: '#061108', park: '#0a1c0c', building: '#061509', line: '#3b7934', bright: '#90e87a', text: '#a4ed88' }
    : { ground: '#061008', land: '#09150a', park: '#10250e', building: '#0a1c0d', line: '#4a8a3e', bright: '#a3f78c', text: '#b5f198' };
  const layers = [];
  for (const layer of style.layers.filter(item => !/^(art-|lab-|pipboy-)/.test(item.id))) {
    const id = layer.id;
    const oldPaint = layer.paint ?? {};
    const layout = layer.layout ??= {};

    // Rebuild each paint object so no pink-specific patterns, halos or gradients
    // survive. Geometry, classification filters and stacking grades stay intact.
    if (layer.type === 'background') layer.paint = { 'background-color': p.ground };
    if (layer.type === 'fill') {
      layer.paint = { 'fill-color': p.land, 'fill-opacity': 1, 'fill-antialias': true };
      if (id === 'landuse-industrial') layer.paint['fill-color'] = '#112011';
      if (id === 'public-squares') layer.paint['fill-color'] = '#142b12';
      if (vegetation(layer)) {
        layer.paint['fill-color'] = p.park;
        layers.push(layer);
        layers.push({ ...clone(layer), id: `pipboy-pattern-${id}`,
          paint: { 'fill-pattern': 'pipboy-park-stipple', 'fill-opacity': night ? .42 : .58 } });
        layers.push(contour(layer, 'park-boundary', {
          'line-color': '#508f40', 'line-width': zoom(.5, 1.1), 'line-opacity': .75,
          'line-dasharray': [5, 2],
        }));
        continue;
      }
      if (id === 'water-fill') {
        layer.paint['fill-color'] = '#0b250c';
        layers.push(layer);
        layers.push({ ...clone(layer), id: 'pipboy-water-crosshatch',
          paint: { 'fill-pattern': 'pipboy-water-hatch', 'fill-opacity': night ? .52 : .78 } });
        layers.push(contour(layer, 'shoreline', {
          'line-color': p.bright, 'line-width': zoom(.7, 1.3), 'line-opacity': .8,
        }));
        continue;
      }
      if (building(layer)) {
        layer.paint = { 'fill-color': p.building, 'fill-opacity': .55 };
        layers.push(layer);
        layers.push(contour(layer, 'building-wire', {
          'line-color': p.line, 'line-width': zoom(.38, .84, 14, 18),
          'line-opacity': zoom(.36, .86, 14, 18),
        }));
        continue;
      }
    }

    if (layer.type === 'line') {
      if (id === 'water-outline') continue; // Shoreline is redrawn above the hatch.
      layer.paint = { 'line-color': p.line, 'line-width': zoom(.5, 1.2), 'line-opacity': .75 };
      if (layer.metadata?.road) {
        const casing = layer.metadata.part === 'casing';
        const major = /-(primary|trunk|motorway)-/.test(id);
        const medium = /-(secondary|tertiary)-/.test(id);
        const grade = layer.metadata.grade ?? 0;
        layout['line-cap'] = 'round';
        layout['line-join'] = 'round';
        layer.paint = {
          'line-color': casing ? '#589b42' : major ? '#b7f19a' : medium ? '#8bc76a' : '#648f4a',
          'line-width': scaledWidth(oldPaint['line-width'], casing ? .2 : .12, casing ? 1.5 : .35),
          'line-opacity': grade < 0 ? (casing ? .08 : .3) : casing ? .2 : major ? .95 : .85,
          ...(casing ? { 'line-blur': 1.35 } : {}),
          ...(grade < 0 ? { 'line-dasharray': [2, 2.5] } : {}),
        };
      } else if (id.startsWith('paths-')) {
        layer.paint = { 'line-color': '#75ab56', 'line-width': zoom(.45, 1.1),
          'line-dasharray': [2, 3], 'line-opacity': .64 };
      } else if (id.startsWith('railway-')) {
        layer.paint = { 'line-color': '#86ad65', 'line-width': zoom(.5, 1.3),
          'line-dasharray': [5, 2, 1, 2], 'line-opacity': .7 };
      } else if (id.startsWith('motorway-')) {
        layer.paint = { 'line-color': '#caef9c', 'line-width': zoom(.35, .65),
          'line-dasharray': [1, 5], 'line-opacity': .55 };
      } else if (id === 'waterways') {
        layer.paint = { 'line-color': '#72b657', 'line-width': zoom(.55, 1.8), 'line-opacity': .72 };
      }
    }

    if (layer.type === 'symbol') {
      layer.paint = {};
      if (id === 'road-oneway-directions') {
        // The base filter and reverse-oneway rotation remain untouched. This
        // replacement arrow points EAST, matching line placement's zero angle.
        layout['icon-image'] = 'pipboy-direction-east';
        layout['icon-size'] = zoom(.58, .85, 16.5, 18);
        layout['symbol-spacing'] = 185;
        layer.paint['icon-opacity'] = .8;
      } else if (id.startsWith('poi-')) {
        layout['icon-image'] = `pipboy-${id}`;
        layout['icon-size'] = zoom(.75, 1.12, 13, 18);
        layout['icon-padding'] = 9;
        layout['icon-allow-overlap'] = false;
        layout['icon-ignore-placement'] = false;
        layout['text-field'] = ['upcase', ['get', 'name']];
        layout['text-font'] = ['NavSkinCyberpunk'];
        layout['text-size'] = ['step', ['zoom'], 0, id === 'poi-parks' ? 15 : 16.7, 12.5];
        layout['text-letter-spacing'] = .08;
        layout['text-offset'] = [0, 1.55];
        layout['text-line-height'] = 1.15;
        layer.paint = { 'text-color': p.text, 'text-halo-color': p.ground,
          'text-halo-width': 1.6, 'text-halo-blur': .15, 'icon-opacity': .95 };
      }
      if (layout['text-field'] && !id.startsWith('poi-')) {
        layout['text-font'] = ['NavSkinCyberpunk'];
        layout['text-field'] = ['upcase', ['get', 'name']];
        layout['text-letter-spacing'] = .1;
        layer.paint = { 'text-color': p.text, 'text-halo-color': p.ground,
          'text-halo-width': 1.4, 'text-halo-blur': .15 };
        if (id === 'road-labels') {
          layer.minzoom = 15.5;
          layout['text-size'] = zoom(11, 14.5, 15, 18);
          layout['symbol-spacing'] = 260;
          layout['text-padding'] = 8;
          layer.paint['text-color'] = '#95bf78';
        } else if (id === 'district-labels') {
          layout['text-size'] = 13;
          layout['text-letter-spacing'] = .22;
          layer.paint['text-opacity'] = .65;
        } else {
          layout['text-size'] = zoom(14, 20, 13, 18);
          layout['text-letter-spacing'] = .14;
          layout['text-padding'] = 10;
        }
      }
    }
    layers.push(layer);
  }
  style.layers = layers;
  style.name = `Pip-Boy · Goiânia / mapa local${night ? ' / baixa emissão' : ''}`;
  style.metadata = { ...style.metadata, navskinLab: { theme: 'pipboy', browserOnly: true,
    geography: 'Original Goiânia sources and feature coordinates; no fictional features.',
    illustration: 'Original phosphor pictograms, water hatch and wireframe buildings.',
  } };
  return style;
}

// Small, deterministic RGBA assets, generated once. No canvas, network, animation
// or font rasterizer is involved. Coordinates are 32px; exported pixels are 2×.
function painter(size = 64) {
  const data = new Uint8Array(size * size * 4);
  const colors = { green: [168, 245, 134, 255], dim: [97, 165, 70, 255], black: [6, 16, 8, 255] };
  const dot = (x, y, color = 'green') => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    data.set(Array.isArray(color) ? color : colors[color], (y * size + x) * 4);
  };
  const shape = (inside, color = 'green') => {
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++)
      if (inside((x + .5) / 2, (y + .5) / 2)) dot(x, y, color);
  };
  const rect = (x, y, w, h, color) => shape((a, b) => a >= x && a <= x + w && b >= y && b <= y + h, color);
  const circle = (x, y, r, color) => shape((a, b) => (a - x) ** 2 + (b - y) ** 2 <= r ** 2, color);
  const line = (x, y, x2, y2, width = 1.5, color) => shape((a, b) => {
    const dx = x2 - x, dy = y2 - y;
    const t = Math.max(0, Math.min(1, ((a - x) * dx + (b - y) * dy) / (dx * dx + dy * dy || 1)));
    return (a - x - t * dx) ** 2 + (b - y - t * dy) ** 2 <= (width / 2) ** 2;
  }, color);
  const poly = (points, color) => shape((x, y) => {
    let within = false;
    for (let i = 0, j = points.length - 1; i < points.length; j = i++) {
      const [xi, yi] = points[i], [xj, yj] = points[j];
      if ((yi > y) !== (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) within = !within;
    }
    return within;
  }, color);
  return { data, width: size, height: size, pixelRatio: 2, rect, circle, line, poly, dot };
}

const drawings = {
  religion: ({ rect, poly, circle }) => {
    rect(7, 17, 19, 10); poly([[5, 18], [16, 9], [28, 18]]);
    rect(14, 5, 3, 10); rect(11, 8, 9, 2); rect(14, 21, 4, 6, 'black');
    circle(16, 17, 1.6, 'black'); rect(8, 23, 3, 2, 'black'); rect(21, 23, 3, 2, 'black');
  },
  culture: ({ rect, poly }) => {
    poly([[3, 12], [16, 5], [29, 12]]); rect(4, 13, 24, 2);
    for (const x of [6, 13, 21]) rect(x, 15, 4, 10);
    rect(3, 25, 26, 3); rect(6, 29, 20, 1, 'dim');
  },
  education: ({ rect, poly, line }) => {
    poly([[2, 12], [16, 6], [30, 12], [16, 18]]);
    poly([[7, 16], [16, 20], [25, 16], [25, 23], [16, 27], [7, 23]]);
    line(28, 13, 28, 23, 1.5); rect(27, 22, 3, 4);
  },
  shops: ({ rect, poly }) => {
    poly([[6, 8], [26, 8], [29, 16], [3, 16]]); rect(5, 17, 22, 11);
    rect(8, 19, 7, 6, 'black'); rect(19, 19, 5, 9, 'black');
    for (const x of [8, 15, 22]) rect(x, 9, 2, 7, 'black');
    rect(8, 5, 16, 2, 'dim');
  },
  health: ({ rect }) => {
    rect(5, 10, 22, 18); rect(11, 5, 10, 5);
    rect(13, 12, 6, 12, 'black'); rect(10, 15, 12, 6, 'black');
    rect(14, 13, 4, 10); rect(11, 16, 10, 4);
  },
  parking: ({ rect, poly, circle }) => {
    poly([[5, 19], [9, 10], [23, 10], [27, 19]]); rect(4, 18, 24, 8);
    poly([[9, 17], [12, 12], [20, 12], [23, 17]], 'black');
    rect(6, 25, 4, 4); rect(22, 25, 4, 4);
    circle(8, 21, 1.5, 'black'); circle(24, 21, 1.5, 'black');
  },
  food: ({ rect, line, poly }) => {
    rect(8, 7, 2, 8); rect(12, 7, 2, 8); rect(16, 7, 2, 8);
    poly([[8, 14], [18, 14], [15, 19], [11, 19]]); rect(11, 17, 4, 12);
    poly([[24, 6], [26, 6], [26, 29], [22, 29], [22, 20], [20, 20], [20, 13]]);
    line(6, 30, 28, 30, 1, 'dim');
  },
  fuel: ({ rect, line, poly }) => {
    rect(5, 6, 15, 22); rect(8, 9, 9, 7, 'black'); rect(3, 28, 19, 2);
    line(20, 16, 24, 18, 2.2); line(24, 18, 24, 25, 2.2);
    line(24, 25, 28, 25, 2.2); line(28, 25, 28, 12, 2.2);
    poly([[24, 6], [29, 11], [27, 15], [24, 11]]);
  },
  parks: ({ poly, rect, line }) => {
    poly([[12, 4], [4, 15], [8, 15], [2, 23], [22, 23], [16, 15], [20, 15]]);
    rect(10, 22, 4, 7); poly([[24, 10], [18, 19], [21, 19], [17, 25], [31, 25], [27, 19], [30, 19]]);
    rect(23, 24, 3, 5); line(2, 30, 30, 30, 1, 'dim');
  },
};

let cachedImages;
export async function getImages() {
  if (cachedImages) return cachedImages;
  const images = {};
  const finish = (name, art) => {
    const { width, height, data, pixelRatio } = art;
    images[name] = { width, height, data, pixelRatio };
  };
  for (const [category, draw] of Object.entries(drawings)) {
    const art = painter(); draw(art); finish(`pipboy-poi-${category}`, art);
  }
  const arrow = painter();
  arrow.poly([[4, 14.5], [19, 14.5], [19, 10], [28, 16], [19, 22], [19, 17.5], [4, 17.5]]);
  finish('pipboy-direction-east', arrow);

  const water = painter();
  for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
    if ((x + y) % 16 < 2 || ((x - y + 128) % 16 < 2)) water.dot(x, y, [111, 182, 79, 150]);
  }
  finish('pipboy-water-hatch', water);
  const park = painter();
  for (const [x, y] of [[7, 8], [24, 24]]) {
    park.line(x - 2, y, x + 2, y, .6, [95, 151, 68, 110]);
    park.line(x, y - 2, x, y + 2, .6, [95, 151, 68, 110]);
  }
  finish('pipboy-park-stipple', park);
  cachedImages = images;
  return images;
}
