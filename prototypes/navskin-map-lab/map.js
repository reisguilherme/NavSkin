import * as maplibre from '/vendor/maplibre-gl.mjs';
import { createThemeStyle, getThemeImages, COLLECTION_DESIGNS } from './theme-designs.js';
import {loadPinkIcons, applyPinkIcons, PINK_ICON_PIXEL_RATIO} from './pink-icons.js';
import { withBuildingVolumes, setBuildingVolumes } from './map-perspective.js';

maplibre.setWorkerUrl(new URL('/vendor/maplibre-gl-worker.mjs',location.href).href);
maplibre.setWorkerCount(2);
export { maplibre };
export const REGIONS = {
  universidade:{ name:'Praça Universitária', center:[-49.2430,-16.67555], zoom:16.55, bearing:-15 },
  centro:{ name:'Praça Cívica', center:[-49.2565,-16.68055], zoom:16.25, bearing:0 },
  bosque:{ name:'Bosque dos Buritis', center:[-49.2618,-16.6840], zoom:16.15, bearing:-18 },
};
export const CATEGORIES = [ ['parks','Parques'], ['health','Saúde'], ['food','Alimentação'], ['fuel','Postos'], ['shops','Lojas'], ['parking','Estacionamento'] ];
const styleCache = new Map();

async function sourceStyle(theme, night) {
  const key = theme + (night ? '-night' : '');
  if (!styleCache.has(key)) styleCache.set(key, fetch(`/assets/maps/styles/${key}.json`).then(async response => {
    if (!response.ok) throw new Error(`Estilo ${key}: HTTP ${response.status}`);
    const style=JSON.parse((await response.text()).replaceAll('asset://',`${location.origin}/assets/`));
    // The street sprite was corrected independently of the north-facing vehicle.
    // Version the atlas URL so a browser cannot reuse the previous 90°-offset art.
    if(theme==='smw'&&typeof style.sprite==='string')style.sprite+='?revision=smw-east-arrow-1';
    return style;
  }).catch(error => { styleCache.delete(key); throw error; }));
  return structuredClone(await styleCache.get(key));
}

export async function createMapView(container,{ theme='gta',night=false,proposed=true,threeD=false,onStatus=()=>{},onStyle=()=>{},camera }={}) {
  let request=0,alive=true, images={}, selected=new Set(CATEGORIES.map(([id])=>id));
  let current={theme,night,proposed};
  const prepare = async settings => {
    const base=await sourceStyle(settings.proposed&&COLLECTION_DESIGNS[settings.theme]?'rosa':settings.theme,settings.night);
    base.layers=base.layers.filter(layer=>!layer.id.startsWith('navskin-3d-'));
    const images=settings.proposed ? getThemeImages(settings.theme,settings.night) : {};
    if(settings.proposed&&COLLECTION_DESIGNS[settings.theme])Object.assign(images,await COLLECTION_DESIGNS[settings.theme].getImages());
    const promoted=(base.metadata?.gamemap??base.metadata?.navskin)?.artDirection?.source==='scripts/theme-art/five-worlds.mjs'&&!COLLECTION_DESIGNS[settings.theme];
    let style=createThemeStyle(base,settings.theme,{night:settings.night,proposed:settings.proposed&&!promoted});
    if(settings.proposed && settings.theme==='rosa'){Object.assign(images,await loadPinkIcons());style=applyPinkIcons(style);}
    return {images,style:withBuildingVolumes(style,settings.theme)};
  };
  const start = camera || REGIONS.universidade;
  const initial=await prepare(current);images=initial.images;
  const map=new maplibre.Map({container,style:initial.style,center:start.center,zoom:start.zoom,bearing:start.bearing,pitch:threeD?(start.pitch||48):0,minZoom:12.2,maxZoom:19,maxPitch:65,maxBounds:[[-49.325,-16.755],[-49.205,-16.64]],attributionControl:false,dragRotate:true,canvasContextAttributes:{preserveDrawingBuffer:true},fadeDuration:180});
  map.touchZoomRotate.enableRotation();
  map.addControl(new maplibre.ScaleControl({maxWidth:110,unit:'metric'}),'bottom-left');
  const addImage=id=>{const {pixelRatio=id.startsWith('pink-')?PINK_ICON_PIXEL_RATIO:1,...pixels}=images[id];map.addImage(id,pixels,{pixelRatio});};
  const installImages=()=>{for(const id of Object.keys(images))if(!map.hasImage(id))addImage(id);};
  map.on('styleimagemissing',({id})=>{if(images[id]&&!map.hasImage(id))addImage(id);});
  const filter=()=>{for(const [id] of CATEGORIES)if(map.getLayer(`poi-${id}`))map.setLayoutProperty(`poi-${id}`,'visibility',selected.has(id)?'visible':'none');};
  map.on('style.load',()=>{installImages();filter();setBuildingVolumes(map,threeD);Promise.resolve(onStyle(current,map)).catch(e=>onStatus(e.message,true));});
  map.on('error',e=>{const message=e.error?.message || 'Falha ao desenhar o mapa.';console.error('NavSkin map:',message);onStatus(message,true);});
  map.on('idle',()=>onStatus('Pronto · dados locais',false));
  onStatus('Preparando o mapa…',false);
  const ready=new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>reject(new Error('O mapa não terminou de carregar. É necessário WebGL2.')),45000);
    map.once('load',()=>{clearTimeout(timeout);installImages();filter();setBuildingVolumes(map,threeD);resolve();});
  });
  return {map,ready,get settings(){return {...current};},
    async setTheme(next,options={}) { const generation=++request;const settings={...current,...options,theme:next};onStatus('Carregando o desenho…',false);const prepared=await prepare(settings);if(!alive||generation!==request)return;current=settings;images=prepared.images;map.setStyle(prepared.style,{diff:false}); },
    setCategory(id,enabled){enabled?selected.add(id):selected.delete(id);if(map.getLayer(`poi-${id}`))filter();},
    setThreeD(enabled,{moveCamera=true}={}){threeD=Boolean(enabled);setBuildingVolumes(map,threeD);if(moveCamera)map.easeTo({pitch:threeD?48:0,duration:400});},
    get threeD(){return threeD;},
    goTo(region){const target=REGIONS[region]||REGIONS.universidade;map.easeTo({...target,pitch:threeD?48:0,duration:650});},
    resize(){map.resize();},destroy(){alive=false;request++;map.remove();},
  };
}
