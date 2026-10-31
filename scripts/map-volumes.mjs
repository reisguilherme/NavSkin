/** Optional volumes use the existing visible footprint buckets, never the hidden OSM duplicate. */
export const VOLUME_PREFIX = 'navskin-3d-';
const identity = ['to-string', ['coalesce', ['get', 'gers_id'], ['get', 'osm_id'], ['id'], '7']];
const seed = ['max', 0, ['index-of', ['slice', identity, 0, 1], '0123456789abcdef']];
const number = property => ['to-number', ['get', property], 0];
export const buildingHeight = ['min', 120, ['max', 5, ['case',
  ['>', number('height'), 0], number('height'),
  ['>', number('render_height'), 0], number('render_height'),
  ['>', number('levels'), 0], ['*', number('levels'), 3],
  ['>', number('building:levels'), 0], ['*', number('building:levels'), 3],
  ['+', 6, ['%', ['*', seed, 7], 20]],
]]];

export function applyMapVolumes(style, theme, night, palette) {
  const colors = {
    pipboy: night ? '#295d2e' : '#4d8b48', hollow: night ? '#465a66' : '#737e86',
    crossy: ['match', ['%', seed, 5], 0, night ? '#9caaa0' : '#dbd8c4', 1, night ? '#91a7a0' : '#c3d3c4',
      2, night ? '#b8a693' : '#e2c9b4', 3, night ? '#a5a2b0' : '#d0c7cf', night ? '#c3baa0' : '#e6dfc5'],
  };
  const footprints = style.layers.filter(layer => layer.type === 'fill'
    && /^building-footprints(?:-local)?$/.test(layer.id) && layer.layout?.visibility !== 'none');
  const layers = footprints.map(layer => ({
    id: `${VOLUME_PREFIX}${layer.id}`, type: 'fill-extrusion', source: layer.source,
    ...(layer['source-layer'] ? { 'source-layer': layer['source-layer'] } : {}),
    ...(layer.filter ? { filter: structuredClone(layer.filter) } : {}),
    minzoom: Math.max(14, layer.minzoom ?? 0), layout: { visibility: 'none' },
    metadata: { navskin3d: { footprintLayer: layer.id, height: 'Source height or levels where present; deterministic illustrative fallback, not surveyed geometry.' } },
    paint: { 'fill-extrusion-color': colors[theme] ?? layer.paint['fill-color'] ?? palette.building,
      'fill-extrusion-height': structuredClone(buildingHeight), 'fill-extrusion-base': 0,
      'fill-extrusion-opacity': .96, 'fill-extrusion-vertical-gradient': theme !== 'crossy' },
  }));
  const firstSymbol = style.layers.findIndex(layer => layer.type === 'symbol');
  style.layers.splice(firstSymbol < 0 ? style.layers.length : firstSymbol, 0, ...layers);
  (style.metadata.navskin ?? style.metadata.gamemap).volumes = {
    layerPrefix: VOLUME_PREFIX, defaultVisibility: 'none', navigationPitch: 48,
    geometry: 'Existing real footprints; same filters and local/world replacement as the flat map.',
    heights: 'Existing height/render_height or levels × 3; displayed 5–120 m. Missing values use deterministic illustrative 6–25 m volumes, not measurements.',
  };
  return style;
}
