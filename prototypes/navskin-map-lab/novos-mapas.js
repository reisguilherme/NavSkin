import * as maplibre from '/vendor/maplibre-gl.mjs';
import * as pipboy from './collection/pipboy.js';
import * as hollow from './collection/hollow.js';
import * as crossy from './collection/crossy.js';
import { withBuildingVolumes, setBuildingVolumes } from './map-perspective.js';

maplibre.setWorkerUrl(new URL('/vendor/maplibre-gl-worker.mjs', location.href).href);
maplibre.setWorkerCount(2);
const designs = [pipboy, hollow, crossy];
const dimensions = new Map(designs.map(design => [design.definition.id, (design.camera?.pitch || 0) > 0]));
const regions = {
  bosque: { center: [-49.2618, -16.6840], zoom: 16.4 },
  universidade: { center: [-49.2430, -16.67555], zoom: 16.5 },
  centro: { center: [-49.2565, -16.68055], zoom: 16.4 },
};
const grid = document.getElementById('map-grid');
const views = new Map();
const failed = new Set();
const completed = new Set();
const textOpacities = new WeakMap();
let disposed = false;
let focus = null;
const $ = id => document.getElementById(id);
const escapeHtml = text => String(text).replace(/[&<>"']/g, value => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[value]));
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)').matches;

for (const [index, design] of designs.entries()) {
  const d = design.definition;
  const card = document.createElement('article');
  card.className = `map-card map-theme-${d.id}`;
  card.id = `card-${d.id}`;
  const hud = d.id === 'pipboy' ? ['LOCAL MAP', 'GYN / 16.68° S'] : d.id === 'hollow' ? ['Cartografia dos Buritis', '✧'] : ['CIDADE EM BLOCOS', 'GYN'];
  card.innerHTML = `<div class="card-heading"><span class="index">0${index+1}</span><div><h2>${escapeHtml(d.title)}</h2><p>${escapeHtml(d.kicker)}</p></div><button class="expand" data-focus="${d.id}" aria-expanded="false" aria-label="Ampliar mapa ${escapeHtml(d.title)}">Ampliar ↗</button></div>
    <div class="map-window"><div id="map-${d.id}" class="map-canvas" role="region" aria-label="Mapa de Goiânia no estilo ${escapeHtml(d.title)}"></div><div class="map-overlay" aria-hidden="true"></div><div class="map-hud" aria-hidden="true"><span class="terminal-tag">${hud[0]}</span><span>${hud[1]}</span></div><div class="map-actions"><button class="dimension" id="${d.id}-dimension" data-dimension="${d.id}" aria-pressed="${dimensions.get(d.id)}" aria-label="Alternar vista 2D ou 3D de ${escapeHtml(d.title)}">${dimensions.get(d.id)?'3D':'2D'}</button><button data-action="in" data-map="${d.id}" aria-label="Aproximar ${escapeHtml(d.title)}">+</button><button data-action="out" data-map="${d.id}" aria-label="Afastar ${escapeHtml(d.title)}">−</button><button data-action="reset" data-map="${d.id}" aria-label="Restaurar ${escapeHtml(d.title)}">↺</button></div><div id="status-${d.id}" class="map-status" role="status">Preparando ${escapeHtml(d.title)}…</div></div>
    <div class="card-content"><p class="description">${escapeHtml(d.description)}</p><ul class="detail-list">${d.details.map(detail=>`<li>${escapeHtml(detail)}</li>`).join('')}</ul><div class="card-foot"><span id="ready-${d.id}"><i class="load-dot"></i>Carregando recorte</span><a href="${escapeHtml(d.referenceUrl)}" target="_blank" rel="noopener">${escapeHtml(d.referenceLabel)} ↗</a></div></div>`;
  grid.append(card);
}

const basePromise = fetch('/assets/maps/styles/rosa.json').then(async response => {
  if (!response.ok) throw new Error(`Mapa local indisponível (HTTP ${response.status}).`);
  return JSON.parse((await response.text()).replaceAll('asset://', `${location.origin}/assets/`));
});

function cameraFor(design) {
  return { bearing: 0, ...design.camera, ...regions[$('region').value], pitch: dimensions.get(design.definition.id) ? design.camera?.pitch || 48 : 0 };
}
function setStatus(id, message, error = false) {
  const status = $(`status-${id}`);
  if (error) failed.add(id);
  status.hidden = !message;
  status.textContent = message;
  status.classList.toggle('error', error);
  if (error) {
    $(`ready-${id}`).textContent = 'Falha ao carregar';
    $('overall-status').textContent = 'Um dos mapas precisa de atenção. O erro aparece no recorte.';
  }
}
function updateFilters(map) {
  for (const layer of map.getStyle().layers) {
    if (layer.type !== 'symbol') continue;
    if (layer.source === 'pois' || layer.id.startsWith('poi-')) {
      map.setLayoutProperty(layer.id, 'visibility', $('show-places').checked ? 'visible' : 'none');
    }
    if (layer.layout?.['text-field']) {
      map.setPaintProperty(layer.id, 'text-opacity', $('show-names').checked ? textOpacities.get(map)?.get(layer.id) ?? 1 : 0);
    }
  }
}

async function createView(design) {
  const id = design.definition.id;
  try {
    const [base, images] = await Promise.all([basePromise, design.getImages()]);
    if (disposed) return;
    const style = withBuildingVolumes(design.createStyle(structuredClone(base)), id);
    const initialRegion = $('region').value;
    const map = new maplibre.Map({ container: `map-${id}`, style, ...cameraFor(design),
      minZoom: 13.2, maxZoom: 19, maxPitch: 65,
      maxBounds: [[-49.325,-16.755],[-49.205,-16.64]],
      attributionControl: false, dragRotate: true, fadeDuration: 0,
      canvasContextAttributes: { preserveDrawingBuffer: true },
    });
    textOpacities.set(map, new Map(style.layers.filter(layer => layer.layout?.['text-field']).map(layer => [layer.id, layer.paint?.['text-opacity'] ?? 1])));
    const view = { map, design, images, ready: false };
    views.set(id, view);
    map.touchZoomRotate.enableRotation();
    map.addControl(new maplibre.ScaleControl({ maxWidth: 90, unit: 'metric' }), 'bottom-left');
    const addImage = name => {
      if (!images[name] || map.hasImage(name)) return;
      const { pixelRatio = 2, ...pixels } = images[name];
      map.addImage(name, pixels, { pixelRatio });
    };
    map.on('styleimagemissing', event => addImage(event.id));
    map.on('style.load', () => { Object.keys(images).forEach(addImage); updateFilters(map); setBuildingVolumes(map, dimensions.get(id)); });
    map.on('error', event => {
      console.error(`Map Lab ${id}:`, event.error);
      setStatus(id, event.error?.message || 'Não foi possível desenhar este recorte.', true);
    });
    await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('O recorte não terminou de carregar. Recarregue o painel.')), 45000);
      map.once('load', () => { clearTimeout(timer); resolve(); });
    });
    if (disposed) return;
    // A crop selected while tiles were loading must become the visible crop.
    if ($('region').value !== initialRegion) map.jumpTo(cameraFor(design));
    view.ready = true;
    Object.keys(images).forEach(addImage);
    updateFilters(map);
    setBuildingVolumes(map, dimensions.get(id));
    completed.add(id);
    if (!failed.has(id)) {
      setStatus(id, '');
      $(`ready-${id}`).innerHTML = '<i class="load-dot"></i>Recorte local pronto';
      $(`card-${id}`).classList.add('ready');
    }
    if (completed.size === designs.length && !failed.size) $('overall-status').textContent = '● Três mapas interativos · dados locais';
  } catch (error) { console.error(error); setStatus(id, error.message, true); }
}

function resizeViews() { requestAnimationFrame(() => views.forEach(view => view.map.resize())); }
function setFocus(id, { restoreFocus = false } = {}) {
  const previous = focus;
  focus = designs.some(design => design.definition.id === id) ? id : null;
  grid.classList.toggle('focused', !!focus);
  for (const design of designs) {
    const active = design.definition.id === focus;
    const card = $(`card-${design.definition.id}`);
    card.classList.toggle('selected', active);
    const button = card.querySelector('[data-focus]');
    button.setAttribute('aria-expanded', String(active));
    button.textContent = active ? 'Reduzir ↙' : 'Ampliar ↗';
    button.setAttribute('aria-label', `${active ? 'Reduzir' : 'Ampliar'} mapa ${design.definition.title}`);
  }
  $('back-to-all').hidden = !focus;
  const url = new URL(location.href);
  focus ? url.searchParams.set('tema', focus) : url.searchParams.delete('tema');
  history.replaceState(null, '', url);
  resizeViews();
  if (restoreFocus && previous) $(`card-${previous}`).querySelector('[data-focus]').focus();
}
grid.querySelectorAll('[data-focus]').forEach(button => button.addEventListener('click', () => setFocus(focus === button.dataset.focus ? null : button.dataset.focus)));
$('back-to-all').addEventListener('click', () => setFocus(null, { restoreFocus: true }));
document.addEventListener('keydown', event => { if (event.key === 'Escape' && focus) setFocus(null, { restoreFocus: true }); });
function resetView(view) {
  view.map.easeTo({ ...cameraFor(view.design), duration: reducedMotion ? 0 : 600 });
}
function updateDimension(id, is3d) {
  dimensions.set(id, is3d);
  const button = $(`${id}-dimension`);
  button.setAttribute('aria-pressed', String(is3d)); button.textContent = is3d ? '3D' : '2D';
}
document.querySelectorAll('[data-action]').forEach(button => button.addEventListener('click', () => {
  const view = views.get(button.dataset.map);
  if (!view?.ready) return;
  if (button.dataset.action === 'reset') resetView(view);
  else if (button.dataset.action === 'in') view.map.zoomIn({ duration: reducedMotion ? 0 : 250 });
  else view.map.zoomOut({ duration: reducedMotion ? 0 : 250 });
}));
document.querySelectorAll('[data-dimension]').forEach(button => button.addEventListener('click', () => {
  const id = button.dataset.dimension, view = views.get(id); if (!view?.ready) return;
  const is3d = !dimensions.get(id);
  updateDimension(id, is3d);
  setBuildingVolumes(view.map, is3d);
  view.map.easeTo({ pitch: is3d ? view.design.camera?.pitch || 48 : 0, duration: reducedMotion ? 0 : 600 });
}));
$('reset-all').addEventListener('click', () => views.forEach(view => { if (view.ready) resetView(view); }));
$('region').addEventListener('change', () => {
  views.forEach(view => { if (view.ready) resetView(view); });
  document.querySelector('.map-theme-hollow .terminal-tag').textContent = { bosque: 'Cartografia dos Buritis', universidade: 'Praça Universitária', centro: 'Praça Cívica' }[$('region').value];
});
for (const id of ['show-places', 'show-names']) $(id).addEventListener('change', () => views.forEach(view => { if (view.ready) updateFilters(view.map); }));
const requestedTheme = new URLSearchParams(location.search).get('tema');
if (requestedTheme) setFocus(requestedTheme);
window.navskinCollection = { views, designs, regions, setFocus, get focus() { return focus; }, get failed() { return [...failed]; } };
await Promise.allSettled(designs.map(createView));
grid.setAttribute('aria-busy', 'false');
resizeViews();
window.addEventListener('pagehide', event => { if (event.persisted) return; disposed = true; views.forEach(view => view.map.remove()); });
