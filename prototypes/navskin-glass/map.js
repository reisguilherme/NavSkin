import * as maplibre from '/vendor/maplibre-gl.mjs';

maplibre.setWorkerUrl(new URL('/vendor/maplibre-gl-worker.mjs', location.href).href);
maplibre.setWorkerCount(2);

export const categories = ['parks', 'fuel', 'food', 'parking', 'health', 'shops', 'education', 'culture', 'religion'];
const route = [
  [-49.241999, -16.6789193], [-49.2423778, -16.6777175], [-49.2424604, -16.6774921],
  [-49.2424696, -16.6774179], [-49.2424619, -16.6773357], [-49.2424435, -16.677311],
  [-49.242298, -16.6772164], [-49.2421639, -16.6771445], [-49.2419701, -16.6769102],
  [-49.2418883, -16.6765066], [-49.2418726, -16.6764487], [-49.2418203, -16.6764327],
  [-49.2411407, -16.6762287], [-49.2407741, -16.6761233], [-49.2406792, -16.676096],
  [-49.2405367, -16.6760542], [-49.2399123, -16.6758722]
];
// This static UI demonstration follows locally packaged OSM road coordinates.
// It is not a routing response, GPS fix or instruction to drive this route.
const routeData = { type: 'Feature', properties: { demonstration: true }, geometry: { type: 'LineString', coordinates: route } };

async function loadStyle(theme) {
  const response = await fetch(`/assets/maps/styles/${theme}.json`);
  if (!response.ok) throw new Error(`Não foi possível abrir o mapa (${response.status}).`);
  const json = await response.text();
  return JSON.parse(json.replaceAll('asset://', `${location.origin}/assets/`));
}

export async function createPrototypeMap(container) {
  let theme = 'gta', navigating = false, generation = 0;
  const selected = new Set(categories);
  const reducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
  const map = new maplibre.Map({
    container, style: await loadStyle(theme), center: [-49.2434, -16.67595], zoom: 16.65,
    bearing: -17, pitch: 0, minZoom: 12, maxZoom: 19,
    maxBounds: [[-49.325, -16.755], [-49.205, -16.64]],
    attributionControl: false, dragRotate: true, touchPitch: false,
    canvasContextAttributes: { preserveDrawingBuffer: true }, fadeDuration: 160
  });
  map.touchZoomRotate.enableRotation();
  const markerImage = document.createElement('img');
  markerImage.src = '/assets/navigation-icons/gta/vehicles/arrow-original.png';
  markerImage.alt = 'Posição ilustrativa do veículo';
  Object.assign(markerImage.style, { width: '34px', height: '34px', objectFit: 'contain', filter: 'drop-shadow(0 2px 5px #0007)', pointerEvents: 'none' });
  const marker = new maplibre.Marker({ element: markerImage, rotationAlignment: 'map', rotation: -17 }).setLngLat(route[0]).addTo(map);

  function syncLayers(styleEvent = false) {
    // style.load fires before every source tile has finished loading; new layers
    // may be attached at that point even while isStyleLoaded() is still false.
    if (!styleEvent && !map.getLayer('prototype-route-line') && !map.isStyleLoaded()) return;
    for (const category of categories) {
      const id = `poi-${category}`;
      if (map.getLayer(id)) map.setLayoutProperty(id, 'visibility', selected.has(category) ? 'visible' : 'none');
    }
    if (!map.getSource('prototype-route')) map.addSource('prototype-route', { type: 'geojson', data: routeData });
    if (!map.getLayer('prototype-route-outline')) {
      const before = map.getStyle().layers.find(layer => layer.type === 'symbol')?.id;
      map.addLayer({ id: 'prototype-route-outline', type: 'line', source: 'prototype-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': 10, 'line-opacity': .95 } }, before);
      map.addLayer({ id: 'prototype-route-line', type: 'line', source: 'prototype-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#00a6ed', 'line-width': 6 } }, before);
    }
    for (const id of ['prototype-route-outline', 'prototype-route-line']) map.setLayoutProperty(id, 'visibility', navigating ? 'visible' : 'none');
    map.setPaintProperty('prototype-route-line', 'line-color', theme === 'rosa' ? '#ef1682' : theme === 'rdr2' ? '#a02d24' : '#00a6ed');
  }
  map.on('style.load', () => syncLayers(true));
  let readyTimeout;
  const ready = new Promise((resolve, reject) => {
    readyTimeout = setTimeout(() => reject(new Error('O mapa demorou a carregar. Verifique o suporte WebGL.')), 30000);
    map.once('load', () => { clearTimeout(readyTimeout); syncLayers(); resolve(); });
  });
  // Surface map-loading errors in the host without disguising mock data as GPS.
  map.on('error', event => {
    console.warn('Prototype map:', event.error?.message || event.error);
    container.dispatchEvent(new CustomEvent('prototype-map-error', { detail: event.error?.message || 'Erro de mapa', bubbles: true }));
  });

  function recenter() {
    map.easeTo(navigating
      ? { center: route[0], zoom: 16.4, bearing: -17, pitch: 0, offset: [0, Math.min(110, map.getContainer().clientHeight * .15)], duration: reducedMotion() ? 0 : 650 }
      : { center: [-49.2434, -16.67595], zoom: 16.65, bearing: -17, pitch: 0, offset: [0, 0], duration: reducedMotion() ? 0 : 650 });
  }
  return {
    map, ready,
    async setTheme(next) {
      if (!['gta', 'rosa', 'rdr2'].includes(next)) return;
      const request = ++generation;
      const style = await loadStyle(next);
      if (request !== generation) return;
      theme = next;
      map.setStyle(style, { diff: false });
      markerImage.src = `/assets/navigation-icons/${next}/vehicles/arrow${next === 'gta' ? '-original' : ''}.png`;
    },
    setNavigating(value) { navigating = Boolean(value); syncLayers(); recenter(); },
    setCategory(id, enabled) { if (!categories.includes(id)) return; enabled ? selected.add(id) : selected.delete(id); syncLayers(); },
    recenter,
    resize() { map.resize(); },
    destroy() { clearTimeout(readyTimeout); marker.remove(); map.remove(); }
  };
}
