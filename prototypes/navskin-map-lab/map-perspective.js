/** Shared browser-only solid-volume rendering. Source building footprints are unchanged. */
export const VOLUME_PREFIX = 'lab-volume-';
const palette = {
  gta: ['#979d93', '#e5e5db'], rosa: ['#f3a4ce', '#fff3fc'], rdr2: ['#a58e68', '#dbc59d'],
  smw: ['#b87b40', '#efc68d'], cyberpunk: ['#632b46', '#bd5b77'],
  pipboy: ['#204c21', '#659956'], hollow: ['#354456', '#829291'], crossy: ['#ccd2be', '#f5eed7'],
};
const building = layer => /^(?:building-(?:footprints(?:-local)?|osm)|world-buildings(?:-local)?)$/.test(layer.id);
const identity = ['to-string', ['coalesce', ['get', 'gers_id'], ['get', 'osm_id'], ['id'], '7']];
const seed = ['max', 0, ['index-of', ['slice', identity, 0, 1], '0123456789abcdef']];
const height = ['min', 28, ['max', 5, ['case',
  ['>', ['to-number', ['get', 'height'], 0], 0], ['to-number', ['get', 'height'], 0],
  ['>', ['to-number', ['get', 'render_height'], 0], 0], ['to-number', ['get', 'render_height'], 0],
  ['>', ['to-number', ['get', 'levels'], 0], 0], ['*', ['to-number', ['get', 'levels'], 0], 3],
  ['+', 6, ['%', ['*', seed, 3], 11]],
]]];

export function withBuildingVolumes(input, theme) {
  const style = structuredClone(input), [body, roof] = palette[theme] || palette.gta;
  const volumes = [], layers = [];
  // Native volume layers are reproduced here only for this editable browser study.
  for (const layer of style.layers.filter(item => !item.id.startsWith(VOLUME_PREFIX) && !item.id.startsWith('navskin-3d-'))) {
    if (layer.type === 'fill-extrusion' && (building(layer) || layer.id.startsWith('crossy-roof-'))) {
      const copy = structuredClone(layer);
      copy.id = VOLUME_PREFIX + layer.id;
      copy.layout = { ...copy.layout, visibility: 'none' };
      if (layer.layout?.visibility !== 'none') volumes.push(copy);
      if (building(layer)) layers.push({ ...layer, type: 'fill', paint: { 'fill-color': roof, 'fill-opacity': 1 } });
      continue;
    }
    layers.push(layer);
    if (layer.type !== 'fill' || !building(layer) || layer.layout?.visibility === 'none') continue;
    const basis = { source: layer.source, ...(layer['source-layer'] ? { 'source-layer': layer['source-layer'] } : {}),
      ...(layer.filter ? { filter: structuredClone(layer.filter) } : {}),
      minzoom: Math.max(14, layer.minzoom || 0), ...(layer.maxzoom == null ? {} : { maxzoom: layer.maxzoom }),
      type: 'fill-extrusion', layout: { visibility: 'none' },
      metadata: { navskinLab: { follows: layer.id, illustrativeHeight: true } },
    };
    volumes.push({ ...basis, id: `${VOLUME_PREFIX}${layer.id}`, paint: { 'fill-extrusion-color': body,
      'fill-extrusion-height': height, 'fill-extrusion-base': 0, 'fill-extrusion-opacity': 1, 'fill-extrusion-vertical-gradient': false } });
    volumes.push({ ...basis, id: `${VOLUME_PREFIX}roof-${layer.id}`, paint: { 'fill-extrusion-color': roof,
      'fill-extrusion-height': ['+', height, .35], 'fill-extrusion-base': height, 'fill-extrusion-opacity': 1, 'fill-extrusion-vertical-gradient': false } });
  }
  const symbolIndex = layers.findIndex(layer => layer.type === 'symbol');
  layers.splice(symbolIndex < 0 ? layers.length : symbolIndex, 0, ...volumes);
  style.layers = layers;
  style.light = { anchor: 'viewport', color: '#fff6df', intensity: .45, position: [1.5, 205, 40] };
  return style;
}

const originalHeights = new WeakMap();
export function setBuildingVolumes(map, enabled, { opacity = 1, heightScale = 1 } = {}) {
  if (!map.getStyle()) return;
  let saved = originalHeights.get(map);
  if (!saved) originalHeights.set(map, saved = new Map());
  for (const layer of map.getStyle().layers) {
    if (!layer.id.startsWith(VOLUME_PREFIX)) continue;
    map.setLayoutProperty(layer.id, 'visibility', enabled ? 'visible' : 'none');
    map.setPaintProperty(layer.id, 'fill-extrusion-opacity', opacity);
    // Capture each concrete layer instance so style replacement cannot retain a
    // previous theme's dimensions. Maps expose fresh objects in getStyle().
    const stamp = `${layer.id}:${map.getStyle().name}`;
    if (!saved.has(stamp)) saved.set(stamp, { height: layer.paint['fill-extrusion-height'], base: layer.paint['fill-extrusion-base'] || 0 });
    const original = saved.get(stamp);
    for (const [property, value] of [['height', original.height], ['base', original.base]]) {
      map.setPaintProperty(layer.id, `fill-extrusion-${property}`, heightScale === 1 ? value : ['*', value, heightScale]);
    }
  }
}

export function readPerspective(context = 'browse') {
  try { const value = localStorage.getItem(`navskin-lab-perspective-${context}`); return value === null ? context === 'navigation' : value === '3d'; }
  catch { return context === 'navigation'; }
}
export function savePerspective(context, enabled) { try { localStorage.setItem(`navskin-lab-perspective-${context}`, enabled ? '3d' : '2d'); } catch {} }
