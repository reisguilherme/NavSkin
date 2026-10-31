/**
 * Approved first-five drawing studies promoted for native map generation. All geometry, sources, road grades and
 * place identities come from the installed map pack. Procedural materials and contours preserve the real cartographic features.
 */
export const THEMES = [
  { id: 'gta', title: 'GTA San Andreas', kicker: 'RADAR / LOS SANTOS', accent: '#95c65e',
    description: 'Quadras recortadas, asfalto preto e símbolos de radar. Um mapa com a força gráfica de San Andreas.',
    features: ['Quadras com sombra de impressão', 'Parques com contorno de tinta', 'Vias pretas e letreiros legíveis'] },
  { id: 'rosa', title: 'Pink World', kicker: 'CANDY / TOY CITY', accent: '#fa70b6',
    description: 'Uma cidade de papel e brinquedos: telhados de glacê, parques de menta e uma família própria de ícones.',
    features: ['13 ícones originais de brinquedo e doces', 'Prédios em camadas de papel', 'Ruas arredondadas como fitas'] },
  { id: 'rdr2', title: 'Red Dead Redemption 2', kicker: 'INK / FRONTIER ATLAS', accent: '#d3b58a',
    description: 'Cartografia de fronteira desenhada à tinta. Papel, hachuras e margens gravadas fazem parte do desenho.',
    features: ['Papel e hachuras de gravura', 'Vias finas e trilhas tracejadas', 'Margens e vegetação a nanquim'] },
  { id: 'smw', title: 'Super Mario World', kicker: 'PIXEL / OVERWORLD', accent: '#92df47',
    description: 'O overworld aprovado: terrenos em degraus, telhados de tijolos, água pixelada e sprites coloridos.',
    features: ['Terrenos com bordas e faces', 'Tijolos e ondas em pixels', 'Caminhos de overworld'] },
  { id: 'cyberpunk', title: 'Cyberpunk 2077', kicker: 'SCAN / NIGHT CITY', accent: '#5fe1ea',
    description: 'Uma leitura tática da cidade: volumes arquitetônicos, malha técnica e vias com bordas luminosas.',
    features: ['Volumes e plantas arquitetônicas', 'Linhas técnicas e malha de leitura', 'Contraste ciano, vermelho e âmbar'] },
  
];

const ids = new Set(THEMES.map(theme => theme.id));
const clone = value => structuredClone(value);
const zoom = (low, high, start = 13, end = 18) => ['interpolate', ['linear'], ['zoom'], start, low, end, high];
const patternId = (theme, material, night) => `lab-${theme}-${material}-${night ? 'night' : 'day'}`;
const isBuilding = layer => layer.type === 'fill' && /^building-(?:footprints(?:-local)?|osm)$/.test(layer.id);
const isVegetation = layer => layer.type === 'fill' && /^(vegetation-(?:grass|wood|parks)|protected-areas)$/.test(layer.id);

/** Scale only numeric stop outputs; zoom stops and data selectors stay intact. */
function width(value, factor = 1, addition = 0) {
  if (typeof value === 'number') return Math.max(.1, value * factor + addition);
  if (!Array.isArray(value) || value[0] !== 'interpolate') return value;
  return value.map((item, index) => index >= 4 && index % 2 === 0 && typeof item === 'number'
    ? Math.max(.1, item * factor + addition) : item);
}

function polygonDrawing(layer, suffix, type, paint, minzoom = layer.minzoom ?? 0) {
  return { id: `lab-${suffix}-${layer.id}`, type, source: layer.source,
    ...(layer['source-layer'] ? { 'source-layer': layer['source-layer'] } : {}),
    ...(layer.filter ? { filter: clone(layer.filter) } : {}), minzoom,
    ...(layer.maxzoom != null ? { maxzoom: layer.maxzoom } : {}),
    ...(layer.layout?.visibility ? { layout: { visibility: layer.layout.visibility } } : {}),
    metadata: { navskinLab: { illustration: true, follows: layer.id } }, paint };
}

const colors = {
  gta: { paper: '#a6a3a8', roof: '#e9e9df', ink: '#101012', park: '#326b2b', water: '#719bb8', shadow: '#66636b', text: '#ffffff' },
  'gta-night': { paper: '#55595d', roof: '#bfc3b5', ink: '#060808', park: '#29472e', water: '#416d86', shadow: '#303736', text: '#f7f5e8' },
  rosa: { paper: '#ffd5e8', roof: '#fff7fc', ink: '#ce5a9b', park: '#93e7c2', water: '#98e5ef', shadow: '#e887b7', text: '#6e275e' },
  'rosa-night': { paper: '#6f3c83', roof: '#e9b8df', ink: '#ff8bc9', park: '#48b99c', water: '#4baac9', shadow: '#41224f', text: '#fff2fb' },
  rdr2: { paper: '#e6d4ac', roof: '#d4bf94', ink: '#51402d', park: '#c4bf96', water: '#b7c7b3', shadow: '#8f7856', text: '#423025' },
  'rdr2-night': { paper: '#877558', roof: '#aa9673', ink: '#30271e', park: '#7c8060', water: '#78928a', shadow: '#493c2a', text: '#f9e8bf' },
  smw: { paper: '#80d838', roof: '#e8b078', ink: '#704828', park: '#48c820', water: '#5898f0', shadow: '#704828', text: '#ffffff' },
  'smw-night': { paper: '#284e35', roof: '#bb956d', ink: '#263727', park: '#387c35', water: '#3f659b', shadow: '#1c3025', text: '#fff6ca' },
  cyberpunk: { paper: '#0b1420', roof: '#492537', ink: '#d76572', park: '#173a3c', water: '#102b45', shadow: '#050b14', text: '#92f4ee' },
  'cyberpunk-night': { paper: '#070e18', roof: '#391b30', ink: '#bd4f70', park: '#102e32', water: '#0c223a', shadow: '#02060d', text: '#93f9f4' },
};

export function createThemeStyle(baseStyle, theme, { night = false, proposed = true } = {}) {
  if (!ids.has(theme)) throw new RangeError(`Unknown map lab theme: ${theme}`);
  const style = clone(baseStyle);
  if (!proposed) return style;
  const p = colors[theme + (night ? '-night' : '')];
  const image = material => patternId(theme, material, night);
  // SMW is the approved map. The other drawings replace their prior illustrative
  // passes instead of accumulating shadows, edges and translucent roof copies.
  const input = style.layers.filter(layer => !layer.id.startsWith('lab-') && !layer.id.startsWith('navskin-3d-') && (theme === 'smw' || !layer.id.startsWith('art-')));
  const layers = [];
  for (const layer of input) {
    const paint = layer.paint ??= {};
    const layout = layer.layout ??= {};
    if (layer.type === 'background' && theme !== 'smw') {
      paint['background-color'] = p.paper;
      paint['background-pattern'] = image(theme === 'cyberpunk' ? 'grid' : 'paper');
    }
    if (layer.id === 'landuse-urban' && theme !== 'smw') {
      paint['fill-color'] = p.paper;
      paint['fill-pattern'] = image(theme === 'cyberpunk' ? 'grid' : 'paper');
    }

    if (isVegetation(layer)) {
      if (theme === 'smw') {
        // The web study can show these materials without the Native GeoJSON
        // style-transition workaround used by the APK.
        paint['fill-pattern'] = image('grass');
      } else {
        paint['fill-color'] = p.park;
        delete paint['fill-pattern'];
        if (theme === 'rdr2') paint['fill-pattern'] = image(layer.id.includes('wood') ? 'forest' : 'grass');
        if (theme === 'cyberpunk') paint['fill-pattern'] = image('park');
        if (layer.id === 'vegetation-parks') {
          if (theme === 'rosa') layers.push(polygonDrawing(layer, 'park-paper-edge', 'fill', {
            'fill-color': night ? '#28795f' : '#50ba91', 'fill-translate': [0, 3], 'fill-translate-anchor': 'map',
          }));
        }
      }
    }

    if (layer.id === 'water-fill') {
      paint['fill-color'] = p.water;
      paint['fill-pattern'] = image('water');
      if (theme === 'rdr2') {
        // Two engraving strokes follow only the real shoreline geometry.
        layers.push(polygonDrawing(layer, 'shore-outer', 'line', {
          'line-color': night ? '#8fa79b' : '#829985', 'line-width': zoom(2, 5), 'line-opacity': .44,
        }));
      }
    }
    if (layer.id === 'water-outline') {
      paint['line-color'] = theme === 'cyberpunk' ? '#49b5d4' : theme === 'rosa' ? '#46b8ce' : p.ink;
      paint['line-width'] = zoom(theme === 'gta' ? 1.2 : .5, theme === 'gta' ? 3 : 1.8);
    }

    if (isBuilding(layer) && theme !== 'smw') {
      delete paint['fill-pattern'];
      paint['fill-color'] = p.roof;
      paint['fill-opacity'] = 1;
      paint['fill-antialias'] = true;
      const offset = theme === 'rosa' ? [3, 4] : theme === 'cyberpunk' ? [3, 3] : theme === 'gta' ? [1.5, 2] : [.7, 1];
      layers.push(polygonDrawing(layer, 'roof-shadow', 'fill', {
        'fill-color': p.shadow, 'fill-translate': offset, 'fill-translate-anchor': 'map',
        'fill-opacity': theme === 'rdr2' ? .55 : .85,
      }));
      if (theme !== 'gta') paint['fill-pattern'] = image('roof');
      paint['fill-outline-color'] = theme === 'rosa' ? (night ? '#f5c5e9' : '#ffffff') : p.ink;
    }

    if (layer.metadata?.road && theme !== 'smw') {
      const casing = layer.metadata.part === 'casing';
      const arterial = /-(primary|trunk|motorway)-/.test(layer.id);
      const local = /-(service|local)-/.test(layer.id);
      const factors = { gta: casing ? 1.15 : 1.12, rosa: casing ? 1.06 : .91, rdr2: casing ? .65 : .42, cyberpunk: casing ? 1.02 : .84 };
      paint['line-width'] = width(paint['line-width'], factors[theme], casing && theme === 'rosa' ? .9 : 0);
      layout['line-cap'] = theme === 'rdr2' || theme === 'rosa' ? 'round' : 'butt';
      layout['line-join'] = theme === 'gta' || theme === 'cyberpunk' ? 'miter' : 'round';
      if (theme === 'gta') paint['line-color'] = casing ? '#2c2c2f' : p.ink;
      if (theme === 'rosa') paint['line-color'] = casing ? p.ink : night ? '#ffe3f7' : arterial ? '#fff0b7' : '#fffafc';
      if (theme === 'rdr2') {
        paint['line-color'] = casing ? p.paper : p.ink;
        // Local streets read like pen strokes; major roads retain a wider ink line.
        if (!casing && local) paint['line-opacity'] = .76;
      }
      if (theme === 'cyberpunk') {
        paint['line-color'] = casing ? (arterial ? '#4db0c3' : '#23596d') : '#0a1b2a';
        if (casing) paint['line-opacity'] = .9;
      }
    }
    if (layer.id.startsWith('paths-') && theme !== 'smw') {
      paint['line-width'] = zoom(.45, theme === 'rosa' ? 2.1 : 1.6);
      paint['line-color'] = theme === 'cyberpunk' ? '#3c91a0' : theme === 'rosa' ? '#e981b5' : p.ink;
      paint['line-dasharray'] = theme === 'rdr2' ? [3, 2] : theme === 'cyberpunk' ? [1, 2.5] : [1, 1.5];
      paint['line-opacity'] = theme === 'gta' ? .65 : .85;
    }
    if (layer.id.startsWith('railway-') && theme !== 'smw') {
      paint['line-color'] = theme === 'cyberpunk' ? '#526675' : p.ink;
      paint['line-width'] = zoom(.4, theme === 'rdr2' ? 1.7 : 1.3);
      paint['line-dasharray'] = theme === 'rdr2' ? [4, 1.5] : [2, 2];
    }
    if (layer.id.startsWith('motorway-') && theme !== 'smw') {
      paint['line-color'] = theme === 'cyberpunk' ? '#d0be55' : theme === 'rosa' ? '#ffffff' : theme === 'rdr2' ? '#a86548' : '#c8c6b9';
      paint['line-width'] = zoom(.4, theme === 'gta' ? 1.25 : .8);
      paint['line-dasharray'] = theme === 'cyberpunk' ? [1.5, 5] : [5, 3];
    }

    if (layer.type === 'symbol' && layer.source === 'roads' && layout['text-field'] && theme !== 'smw') {
      if (layer.id === 'road-labels') layer.filter = ['all', ['has', 'name'], ['!=', ['get', 'name'], ''], ['in', ['get', 'kind'], ['literal', ['service', 'residential', 'unclassified', 'living_street', 'road', 'tertiary', 'secondary', 'busway', 'primary', 'trunk', 'motorway']]]];
      layout['text-size'] = zoom(theme === 'rdr2' ? 12.5 : 13.5, theme === 'gta' ? 18.5 : 17.5, 14, 18);
      layout['text-letter-spacing'] = theme === 'rdr2' ? .09 : theme === 'cyberpunk' ? .075 : .025;
      layout['symbol-spacing'] = theme === 'rdr2' ? 220 : 190;
      paint['text-color'] = theme === 'gta' ? p.text : theme === 'rdr2' ? p.text : theme === 'cyberpunk' ? '#90dfe3' : p.text;
      paint['text-halo-color'] = theme === 'gta' ? '#101012' : theme === 'rdr2' ? p.paper : theme === 'cyberpunk' ? '#07121e' : night ? '#612f6e' : '#fff7fc';
      paint['text-halo-width'] = theme === 'gta' ? .9 : theme === 'rdr2' ? 1.1 : 1.25;
      paint['text-halo-blur'] = 0;
    }
    if (layer.id.startsWith('poi-') && theme !== 'smw') {
      layout['icon-padding'] = theme === 'rdr2' ? 7 : 5;
      layout['icon-size'] = zoom(theme === 'cyberpunk' ? .66 : .73, theme === 'gta' ? 1.25 : theme === 'rosa' ? 1.12 : 1.03, 13, 18);
      paint['text-color'] = p.text;
      paint['text-halo-color'] = theme === 'gta' ? '#101012' : theme === 'rdr2' ? p.paper : theme === 'cyberpunk' ? '#07121e' : night ? '#612f6e' : '#fff7fc';
      paint['text-halo-width'] = theme === 'rdr2' ? .9 : 1.3;
    }
    if (layer.id === 'place-labels' && theme !== 'smw') {
      layout['text-letter-spacing'] = theme === 'rdr2' ? .15 : theme === 'cyberpunk' ? .12 : .025;
      layout['text-size'] = zoom(theme === 'rdr2' ? 17 : 16, theme === 'gta' ? 24 : 22);
      paint['text-color'] = p.text;
      paint['text-halo-color'] = theme === 'gta' ? '#101012' : theme === 'rdr2' ? p.paper : theme === 'cyberpunk' ? '#07121e' : night ? '#612f6e' : '#fff7fc';
      paint['text-halo-width'] = theme === 'gta' ? 1.5 : 1;
    }

    layers.push(layer);
    if (isBuilding(layer) && theme !== 'smw') {
      layers.push(polygonDrawing(layer, 'roof-edge', 'line', {
        'line-color': theme === 'rosa' ? (night ? '#ffd3ed' : '#ffffff') : p.ink,
        'line-width': zoom(theme === 'cyberpunk' ? .4 : .15, theme === 'rosa' ? 1.3 : theme === 'cyberpunk' ? 1.1 : .7),
        'line-opacity': theme === 'gta' ? .35 : .9,
      }, Math.max(14.2, layer.minzoom ?? 0)));
    }
    if (layer.id === 'vegetation-parks' && theme !== 'smw') {
      layers.push(polygonDrawing(layer, 'park-edge', 'line', {
        'line-color': theme === 'rosa' ? (night ? '#7ae4be' : '#f1fff4') : theme === 'cyberpunk' ? '#2d777a' : p.ink,
        'line-width': zoom(theme === 'gta' ? .7 : .35, theme === 'gta' ? 2.8 : theme === 'rosa' ? 2.4 : 1.2),
        'line-opacity': theme === 'rdr2' ? .65 : 1,
        ...(theme === 'rdr2' ? { 'line-dasharray': [6, 1] } : {}),
      }));
    }
  }
  style.layers = layers;
  style.name = `${THEMES.find(item => item.id === theme).title} · estudo de desenho${night ? ' noturno' : ''}`;
  style.metadata ??= {};
  style.metadata.navskinLab = {
    theme, proposed: true, nativeAssetsChanged: false,
    concept: THEMES.find(item => item.id === theme).description,
    geography: 'Original sources, feature coordinates, classifications and road grades preserved.',
    illustration: 'Texture, contour and offset shadows are illustration, not measured height or extra POIs.',
  };
  return style;
}

// Small deterministic RGBA tiles. Producing pixels directly also lets the
// browser register a missing style image synchronously, without loading a URL.
function rgba(hex) {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16), 255];
}
function tile(background, draw, grain = 0) {
  const size = 64, data = new Uint8Array(size * size * 4), base = rgba(background);
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const index = (y * size + x) * 4;
    const change = grain ? (((x * 31 + y * 17 + x * y * 7) % 19) / 18 - .5) * grain : 0;
    data[index] = Math.max(0, Math.min(255, base[0] + change));
    data[index + 1] = Math.max(0, Math.min(255, base[1] + change));
    data[index + 2] = Math.max(0, Math.min(255, base[2] + change));
    data[index + 3] = 255;
  }
  const pixel = (x, y, color, alpha = 1) => {
    x = ((Math.round(x) % size) + size) % size; y = ((Math.round(y) % size) + size) % size;
    const index = (y * size + x) * 4, c = rgba(color);
    for (let i = 0; i < 3; i++) data[index + i] = data[index + i] * (1 - alpha) + c[i] * alpha;
  };
  const rect = (x, y, w, h, color, alpha = 1) => {
    for (let row = y; row < y + h; row++) for (let column = x; column < x + w; column++) pixel(column, row, color, alpha);
  };
  const line = (x0, y0, x1, y1, color, alpha = 1) => {
    const length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
    for (let step = 0; step <= length; step++) pixel(x0 + (x1 - x0) * step / Math.max(1, length), y0 + (y1 - y0) * step / Math.max(1, length), color, alpha);
  };
  draw?.({ pixel, rect, line, size });
  return { width: size, height: size, data };
}

/** No network, document, fonts or global mutable canvas required. */
export function getThemeImages(theme, night = false) {
  if (!ids.has(theme)) return {};
  const p = colors[theme + (night ? '-night' : '')], result = {};
  const add = (name, background, painter, grain = 0) => { result[patternId(theme, name, night)] = tile(background, painter, grain); };
  if (theme === 'gta') {
    add('paper', p.paper, ({ pixel }) => { for (let i = 0; i < 26; i++) pixel(i * 13, i * 29, '#111111', .06); }, 3);
    add('water', p.water, ({ line }) => { line(4, 17, 18, 17, '#c6dee2', .22); line(37, 49, 55, 49, '#c6dee2', .22); });
  } else if (theme === 'rdr2') {
    add('paper', p.paper, ({ line }) => { line(3, 11, 8, 11, p.ink, .08); line(41, 52, 49, 53, p.ink, .06); }, 9);
    add('roof', p.roof, ({ line }) => { for (let n = -64; n < 128; n += 7) line(n, 0, n + 64, 64, p.ink, .24); }, 7);
    add('grass', p.park, ({ pixel, line }) => {
      for (let n = 0; n < 13; n++) { const x = n * 19 % 64, y = n * 31 % 64; pixel(x, y, p.ink, .29); line(x + 2, y + 1, x + 4, y - 1, p.ink, .2); }
    }, 7);
    add('forest', night ? '#697458' : '#afb28a', ({ line }) => {
      for (let n = 0; n < 9; n++) { const x = n * 23 % 64, y = n * 37 % 64; line(x, y - 3, x - 3, y + 2, p.ink, .4); line(x, y - 3, x + 3, y + 2, p.ink, .4); line(x - 3, y + 2, x + 3, y + 2, p.ink, .3); line(x, y + 2, x, y + 4, p.ink, .25); }
    }, 6);
    add('water', p.water, ({ line }) => {
      for (let y = 5; y < 64; y += 11) { line(0, y, 26, y, p.ink, .13); line(35, y + 1, 63, y + 1, p.ink, .13); }
    }, 5);
  } else if (theme === 'rosa') {
    add('paper', p.paper, ({ pixel }) => { for (let i = 0; i < 20; i++) pixel(i * 17, i * 29, '#ffffff', .15); }, 2);
    add('roof', p.roof, ({ line }) => { for (let n = -64; n < 128; n += 16) line(n, 0, n + 64, 64, night ? '#fff0fb' : '#f4c7e1', night ? .12 : .2); });
    add('water', p.water, ({ line, rect }) => {
      for (const [x, y] of [[15, 18], [47, 50]]) { line(x - 3, y, x + 3, y, '#ffffff', .6); line(x, y - 3, x, y + 3, '#ffffff', .6); rect(x - 1, y - 1, 2, 2, '#ffffff', .55); }
    });
  } else if (theme === 'smw') {
    add('grass', p.park, ({ rect }) => { for (const [x, y] of [[8, 10], [46, 38]]) { rect(x, y, 2, 4, '#228a16', night ? .25 : .3); rect(x + 4, y + 2, 2, 3, '#228a16', night ? .25 : .3); } });
    add('water', p.water, ({ rect }) => { for (const [x, y] of [[6, 12], [38, 44]]) { rect(x, y, 12, 2, '#b5d9ff', .5); rect(x + 2, y + 2, 8, 2, '#b5d9ff', .5); } });
  } else if (theme === 'cyberpunk') {
    add('grid', p.paper, ({ line, pixel }) => {
      for (let n = 0; n < 64; n += 32) { line(n, 0, n, 63, '#5a91a5', .1); line(0, n, 63, n, '#5a91a5', .1); }
      pixel(32, 32, '#74bec6', .25);
    });
    add('roof', p.roof, ({ line, rect }) => {
      for (let y = 0; y < 64; y += 8) line(0, y, 63, y, '#e46c7e', .22);
      line(16, 0, 16, 63, '#e46c7e', .23); line(48, 0, 48, 63, '#e46c7e', .23);
      rect(15, 15, 3, 3, '#f49a9b', .25); rect(47, 47, 3, 3, '#f49a9b', .25);
    });
    add('park', p.park, ({ line }) => { for (let n = -64; n < 128; n += 16) line(n, 0, n + 64, 64, '#72d2bc', .13); });
    add('water', p.water, ({ line }) => { for (let y = 0; y < 64; y += 16) line(0, y, 63, y, '#399cb8', .25); });
  }
  return result;
}
