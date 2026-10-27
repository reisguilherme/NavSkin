import * as maplibregl from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?url';
import { buildStyle, tokens } from './style.js';
import { findPlace as selectPlace } from './places.js';
import { POI_CATEGORIES } from './poi-categories.js';
import { resolveStyleAssets } from './style-assets.js';
import './app.css';
import './layers.css';

const $ = selector => document.querySelector(selector);
let mode = 'gta';
let overrides = {};
const layerSettings={buildingSource:'overture',poisEnabled:true,poiKinds:POI_CATEGORIES.map(c=>c.id)};
const currentStyle=()=>resolveStyleAssets(buildStyle(mode,{...(mode==='gta'?overrides:{}),...layerSettings}),document.baseURI);
let metadata;
let places;
let currentPreset = 'overview';
const errors = [];
let loaded = false;
async function boot() {
maplibregl.setWorkerUrl(workerUrl);
maplibregl.setWorkerCount(2);
const map = new maplibregl.Map({container:'map',style:currentStyle(),center:[-49.266,-16.697],zoom:13.1,minZoom:11.7,maxZoom:17.5,
  pitch:0,bearing:0,dragRotate:false,pitchWithRotate:false,touchPitch:false,attributionControl:false,canvasContextAttributes:{preserveDrawingBuffer:true},fadeDuration:0});
map.touchZoomRotate.disableRotation();
map.addControl(new maplibregl.NavigationControl({showCompass:false}),'top-right');
map.addControl(new maplibregl.ScaleControl({maxWidth:105,unit:'metric'}),'bottom-left');
map.addControl(new maplibregl.AttributionControl({compact:false}),'bottom-right');

function fail(message) {
  $('#loading').hidden = true;
  $('#map-error').textContent = message;
  $('#map-error').hidden = false;
}
map.on('error', event => {
  errors.push(String(event.error?.message || event.error || 'Erro desconhecido'));
  console.error(event.error);
  fail(`Não foi possível desenhar o mapa. ${event.error?.message || 'Verifique os dados locais e o suporte a WebGL.'}`);
});
// Register before fetching metadata: the load event may precede those fetches.
const mapReady = new Promise((resolve,reject)=>{
  const timeout=setTimeout(()=>reject(new Error('Tempo de carregamento excedido (30 s). Verifique WebGL e os arquivos locais.')),30000);
  map.once('load',()=>{clearTimeout(timeout);resolve();});
  map.once('error',event=>{clearTimeout(timeout);reject(event.error || new Error('Falha no renderer'));});
});
mapReady.catch(()=>{});

async function loadJson(path) {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}
function findPlace(name) {return selectPlace(places,name);}
function gotoPreset(preset, animate = true) {
  currentPreset = preset;
  const duration = animate && !matchMedia('(prefers-reduced-motion: reduce)').matches ? 700 : 0;
  if (preset === 'overview') {
    // Five verified landmarks define the initial camera, with room for surrounding city.
    const anchors = ['Central','Cívica','Bueno','Vaca Brava','Flamboyant'].map(findPlace).filter(Boolean);
    const bounds = new maplibregl.LngLatBounds();
    anchors.forEach(feature => bounds.extend(feature.geometry.coordinates));
    if (anchors.length >= 4) map.fitBounds(bounds,{padding:{top:130,bottom:110,left:90,right:90},maxZoom:13.1,duration});
    else map.fitBounds([[-49.32,-16.745],[-49.21,-16.65]],{padding:75,duration});
  } else {
    const target = findPlace(preset === 'center' ? 'Cívica' : 'Vaca Brava');
    if (target) map.easeTo({center:target.geometry.coordinates,zoom:preset === 'center' ? 15.1 : 15.4,duration});
  }
  document.querySelectorAll('[data-preset]').forEach(button=>{const active=button.dataset.preset===preset;button.classList.toggle('selected',active);button.setAttribute('aria-pressed',String(active));});
}

function syncLabels() {
  const visibility = $('#labels-toggle').checked ? 'visible' : 'none';
  for (const id of ['road-labels','place-labels','district-labels','place-points']) if (map.getLayer(id)) map.setLayoutProperty(id,'visibility',visibility);
  if(map.getLayer('poi-labels'))map.setLayoutProperty('poi-labels','visibility',$('#labels-toggle').checked&&layerSettings.poisEnabled?'visible':'none');
}
map.on('style.load', syncLabels);

function applyStyle(nextMode = mode) {
  mode = nextMode;
  $('#map').setAttribute('aria-busy','true');
  map.setStyle(currentStyle());
  for (const key of ['gta','neutral']) {const active=key === mode; $(`#style-${key}`).classList.toggle('active',active);$(`#style-${key}`).setAttribute('aria-pressed',String(active));}
  $('#mode-caption').textContent = mode === 'gta' ? 'LINGUAGEM GTA SA · ORIGINAL / 2004' : 'ESTILO NEUTRO · MESMAS GEOMETRIAS';
  const palette = {...tokens.palettes[mode],...(mode === 'gta' ? overrides : {})};
  for (const key of ['road','urban','park','water','building']) $(`.swatch.${key}`).style.background = palette[key];
  syncLabels();
  map.triggerRepaint();
}
$('#style-gta').addEventListener('click',()=>applyStyle('gta'));
$('#style-neutral').addEventListener('click',()=>applyStyle('neutral'));
document.querySelectorAll('[data-preset]').forEach(button=>button.addEventListener('click',()=>gotoPreset(button.dataset.preset)));
$('#home').addEventListener('click',()=>gotoPreset('overview'));
function togglePanel(open) {$('#research-panel').hidden=!open;$('#panel-toggle').setAttribute('aria-expanded',String(open));requestAnimationFrame(()=>map.resize());}
$('#panel-toggle').addEventListener('click',()=>togglePanel($('#research-panel').hidden));
$('#panel-close').addEventListener('click',()=>togglePanel(false));
$('#poi-shortcut').addEventListener('click',()=>{togglePanel(true);$('[data-tab="layers"]').click();});
$('#building-source').addEventListener('change',event=>{layerSettings.buildingSource=event.target.value;applyStyle();});
$('#pois-toggle').addEventListener('change',event=>{layerSettings.poisEnabled=event.target.checked;applyStyle();});
document.addEventListener('keydown',event=>{if(event.key==='Escape')togglePanel(false);});
document.querySelectorAll('[data-tab]').forEach(button=>button.addEventListener('click',()=>{
  for(const candidate of document.querySelectorAll('[data-tab]')){const active=candidate===button;candidate.classList.toggle('active',active);candidate.setAttribute('aria-pressed',String(active));$(`#tab-${candidate.dataset.tab}`).hidden=!active;}
}));

for (const [key,label] of [['urban','Solo urbano'],['park','Parques e vegetação'],['water','Água'],['road','Rede viária']]) {
  const control = document.createElement('label');control.className='color-control';
  const input=document.createElement('input');input.type='color';input.value=tokens.palettes.gta[key];input.setAttribute('aria-label',`Cor: ${label}`);input.dataset.color=key;
  const text=document.createElement('span');text.textContent=label;
  const code=document.createElement('code');code.textContent=input.value.toUpperCase();
  control.append(input,text,code);$('#palette-controls').append(control);
  input.addEventListener('input',()=>{overrides[key]=input.value;code.textContent=input.value.toUpperCase();if(mode==='gta')applyStyle();});
}
$('#road-width').addEventListener('input',event=>{overrides.roadScale=Number(event.target.value)/100;$('#width-value').textContent=`${event.target.value}%`;if(mode==='gta')applyStyle();});
$('#labels-toggle').addEventListener('change',syncLabels);
$('#restore').addEventListener('click',()=>{overrides={};$('#road-width').value='100';$('#width-value').textContent='100%';document.querySelectorAll('[data-color]').forEach(input=>{input.value=tokens.palettes.gta[input.dataset.color];input.parentElement.querySelector('code').textContent=input.value.toUpperCase();});$('#labels-toggle').checked=true;applyStyle();});
$('#export-style').addEventListener('click',()=>{
  const style=currentStyle();
  const url=URL.createObjectURL(new Blob([JSON.stringify(style,null,2)],{type:'application/json'}));
  const a=document.createElement('a');a.href=url;a.download=`goiania-${mode}-style.json`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
});

function updateStatus() {const center=map.getCenter();$('#zoom-level').textContent=`ZOOM ${map.getZoom().toFixed(1)}`;$('#coordinates').textContent=`${Math.abs(center.lat).toFixed(4)}° S / ${Math.abs(center.lng).toFixed(4)}° O`;}
map.on('move',updateStatus);
map.on('click',event=>{
  const ids=[...POI_CATEGORIES.map(c=>`poi-${c.id}`),'landmark-icons','place-points','place-labels','building-footprints','building-osm','vegetation-parks','road-labels'];
  const features=map.queryRenderedFeatures(event.point,{layers:ids.filter(id=>map.getLayer(id))});
  const poi=features.find(feature=>feature.source==='pois'||feature.layer.id==='landmark-icons');
  if(poi){
    $('#feature-info').hidden=true;
    const content=document.createElement('div');content.className='poi-popup';
    const heading=document.createElement('strong');heading.textContent=poi.properties.name||'Local sem nome no OSM';
    const category=document.createElement('p');category.textContent=poi.layer.id==='landmark-icons'?'Parques e praças':POI_CATEGORIES.find(c=>c.id===poi.properties.kind)?.label||poi.properties.kind;
    const source=document.createElement('a');source.href=`https://www.openstreetmap.org/${poi.properties.osm_id}`;source.target='_blank';source.rel='noopener';source.textContent='Ver origem no OpenStreetMap ↗';
    content.append(heading,category);
    if(poi.properties.address){const address=document.createElement('p');address.textContent=poi.properties.address;content.append(address);}
    content.append(source);new maplibregl.Popup({maxWidth:'280px',closeButton:true}).setLngLat(event.lngLat).setDOMContent(content).addTo(map);return;
  }
  const feature=features.find(f=>f.properties.name);
  if(!feature){$('#feature-info').hidden=true;return;}
  const box=$('#feature-info');box.replaceChildren();const strong=document.createElement('strong');strong.textContent=feature.properties.name;const detail=document.createElement('span');detail.textContent=feature.source==='buildings'?`Overture · ${feature.properties.source_datasets || 'edificação'}`:`OSM ${feature.properties.osm_id || ''} · ${feature.properties.kind}`;box.append(strong,detail);box.hidden=false;
});
map.on('movestart',()=>{$('#feature-info').hidden=true;$('#map').setAttribute('aria-busy','true');});
map.on('idle',()=>$('#map').setAttribute('aria-busy','false'));

try {
  const [osmMetadata,placeData,poiMetadata,buildingMetadata] = await Promise.all([loadJson('/data/metadata.json'),loadJson('/data/places.geojson'),loadJson('/data/pois-metadata.json'),loadJson('/data/buildings-metadata.json'),mapReady]);
  metadata=osmMetadata;places=placeData;
  if(errors.length) throw new Error(errors[0]);
  const bounds=metadata.bbox;
  if(Array.isArray(bounds)&&bounds.length===4)map.setMaxBounds([[bounds[0],bounds[1]],[bounds[2],bounds[3]]]);
  gotoPreset('overview',false);
  updateStatus();
  const date=metadata.fetched_at || metadata.fetchedAt || metadata.downloaded_at || metadata.generated_at;
  const formattedDate=date ? new Date(date).toLocaleDateString('pt-BR',{timeZone:'America/Sao_Paulo'}) : 'snapshot local';
  $('#data-status').textContent=`OSM + Overture · ${formattedDate} · local`;
  const details=[['Renderização','MapLibre GL JS 6.16'],['Coordenadas','WGS84 / Web Mercator'],['Vias e áreas',formattedDate],['Prédios',`Overture ${buildingMetadata.release} ou OSM`],['POIs','OSM · 15/07/2026'],['Camadas','Vias · água · áreas · lugares · prédios · POIs'],['Operação','Dados, fontes e renderer locais']];
  for(const [key,value] of details){const dt=document.createElement('dt');dt.textContent=key;const dd=document.createElement('dd');dd.textContent=value;$('#data-details').append(dt,dd);}
  const buildingCount=buildingMetadata.count??buildingMetadata.featureCount??buildingMetadata.counts?.total;
  $('#building-coverage').textContent=buildingCount?`${Number(buildingCount).toLocaleString('pt-BR')} contornos Overture · 2.963 edifícios OSM`:'Overture amplia a cobertura de contornos; OSM original disponível para comparação.';
  const counts=poiMetadata.counts?.byKind??poiMetadata.counts??poiMetadata.byKind??{};
  for(const category of POI_CATEGORIES){
    const label=document.createElement('label');label.className='poi-category';
    const input=document.createElement('input');input.type='checkbox';input.checked=true;input.value=category.id;input.setAttribute('aria-label',`Ícones: ${category.label}`);
    const icon=document.createElement('img');icon.src=`/icons/poi-${category.id}.svg`;icon.alt='';
    const name=document.createElement('span');name.textContent=category.label;
    const count=document.createElement('small');count.textContent=String(counts[category.id]??'');
    if(counts[category.id]===0){input.checked=false;input.disabled=true;}
    input.addEventListener('change',()=>{layerSettings.poiKinds=[...document.querySelectorAll('#poi-categories input:checked')].map(item=>item.value);applyStyle();});
    label.append(input,icon,name,count);$('#poi-categories').append(label);
  }
  const poiDate=new Date(poiMetadata.dataTimestamp).toLocaleDateString('pt-BR',{timeZone:'America/Sao_Paulo'});
  $('#poi-summary').textContent=`${Number(poiMetadata.counts?.total??poiMetadata.total??0).toLocaleString('pt-BR')} locais · base OSM ${poiDate} · clique nos símbolos para consultar a origem`;
  for(const name of ['Central','Cívica','Bueno','Vaca Brava','Flamboyant']){const feature=findPlace(name);if(!feature)continue;const li=document.createElement('li');const button=document.createElement('button');button.textContent=feature.properties.name;const arrow=document.createElement('span');arrow.textContent='LOCALIZAR ↗';button.append(arrow);button.addEventListener('click',()=>{map.easeTo({center:feature.geometry.coordinates,zoom:15.1,duration:600});if(innerWidth<=700)togglePanel(false);});li.append(button);$('#anchors').append(li);}
  map.once('idle',()=>{$('#loading').hidden=true;loaded=true;});
  if(map.isStyleLoaded()&&map.areTilesLoaded()){$('#loading').hidden=true;loaded=true;}
} catch(error){console.error(error);errors.push(error.message);fail(`Falha ao abrir os dados locais: ${error.message}. Execute npm run dev a partir da pasta do projeto.`);}

// Read-only handle for reproducible camera capture and diagnostics in local QA.
window.__poc={map,get mode(){return mode;},get metadata(){return metadata;},get errors(){return [...errors];},get ready(){return loaded;},get preset(){return currentPreset;},gotoPreset,applyStyle};
}
boot().catch(error=>{
  console.error(error);
  errors.push(error.message);
  $('#loading').hidden=true;
  $('#map-error').textContent=`Não foi possível iniciar o mapa. ${error.message} Este renderer requer WebGL2 habilitado no navegador.`;
  $('#map-error').hidden=false;
});
