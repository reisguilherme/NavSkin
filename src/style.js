import tokens from '../public/styles/tokens.json' with { type: 'json' };
import { POI_CATEGORIES } from './poi-categories.js';

export { tokens };
const eq = (key, value) => ['==', ['get', key], value];
const kinds = values => ['in', ['get', 'kind'], ['literal', values]];
const gradeExpression = ['case', ['!=', ['coalesce', ['get', 'layer'], 0], 0], ['get', 'layer'],
  ['!', ['in', ['coalesce', ['get', 'tunnel'], 'no'], ['literal', ['no', 'false', '0', '']]]], -1,
  ['!', ['in', ['coalesce', ['get', 'bridge'], 'no'], ['literal', ['no', 'false', '0', '']]]], 1, 0];

export function buildStyle(mode = 'gta', overrides = {}) {
  if (!tokens.palettes[mode]) throw new Error(`Estilo desconhecido: ${mode}`);
  const p = { ...tokens.palettes[mode] };
  for (const key of ['urban', 'park', 'water', 'road']) if (/^#[0-9a-f]{6}$/i.test(overrides[key] ?? '')) p[key] = overrides[key];
  if (overrides.urban) p.background = p.urban;
  const scale = Math.max(0.5, Math.min(2, Number(overrides.roadScale) || 1));
  const width = (road, casing = false) => ['interpolate', ['linear'], ['zoom'], ...tokens.zoomStops.flatMap((z, i) => [z, road.widths[i] * scale + (casing ? road.outline * 2 : 0)])];
  const layers = [{ id: 'background', type: 'background', paint: { 'background-color': p.background } }];
  const fill = (id, values, color, minzoom = 0) => layers.push({id, type:'fill', source:'areas', minzoom, filter:kinds(values), paint:{'fill-color':color,'fill-opacity':1}});
  fill('landuse-urban', ['residential','urban','retail','construction'], p.urban);
  fill('landuse-commercial', ['commercial'], p.commercial);
  fill('landuse-industrial', ['industrial','brownfield','quarry'], p.industrial);
  fill('vegetation-grass', ['grass','meadow','farmland','orchard','cemetery','allotments','recreation_ground','village_green','scrub'], p.grass);
  fill('vegetation-wood', ['wood','forest','wetland','heath'], p.wood);
  fill('vegetation-parks', ['park','garden','golf_course','nature_reserve','pitch','playground','sports_centre'], p.park);
  fill('public-squares', ['square'], p.square);
  layers.push({id:'water-outline',type:'line',source:'areas',filter:kinds(['water','reservoir','basin','riverbank']),paint:{'line-color':p.waterOutline,'line-width':['interpolate',['linear'],['zoom'],11,0.6,17,1.6]}});
  fill('water-fill', ['water','reservoir','basin','riverbank'], p.water);
  fill('building-osm', ['building'], p.building, tokens.visibility.buildingsMinzoom);
  layers.at(-1).layout={visibility:overrides.buildingSource==='osm'?'visible':'none'};
  layers.push({id:'building-footprints',type:'fill',source:'buildings','source-layer':'buildings',minzoom:tokens.visibility.buildingsMinzoom,layout:{visibility:overrides.buildingSource==='osm'?'none':'visible'},paint:{'fill-color':p.building,'fill-opacity':1}});
  layers.push({id:'waterways',type:'line',source:'waterways',paint:{'line-color':p.water,'line-width':['interpolate',['linear'],['zoom'],11,0.7,14,2,17,5]},layout:{'line-cap':'round','line-join':'round'}});

  // Group by physical grade, then ALL casings before ALL fills: continuous junctions.
  for (let grade = -5; grade <= 5; grade++) {
    const atGrade = ['==',['max',-5,['min',5,gradeExpression]],grade];
    layers.push({id:`paths-${grade}`,type:'line',source:'roads',minzoom:tokens.visibility.pathsMinzoom,filter:['all',kinds(['path','footway','cycleway','steps','pedestrian','track','bridleway']),atGrade],layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':p.path,'line-width':['interpolate',['linear'],['zoom'],14,0.6,17,2.1],'line-opacity':grade < 0 ? 0.5 : 1}});
    layers.push({id:`railway-${grade}`,type:'line',source:'roads',filter:['all',kinds(['rail','railway','light_rail']),atGrade],paint:{'line-color':p.rail,'line-width':['interpolate',['linear'],['zoom'],11,0.8,17,3.2]}});
    for (const part of ['casing', 'fill']) {
      for (const road of tokens.roads) {
        const casing = part === 'casing';
        const color = casing ? (mode === 'gta' ? p.road : p.roadOutline) : (mode === 'gta' ? p.road : (['motorway','trunk'].includes(road.id) ? p.motorway : p.road));
        layers.push({id:`road-${grade}-${road.id}-${part}`,type:'line',source:'roads',minzoom:road.minzoom,
          metadata:{road:true,grade,part},filter:['all',kinds(road.kinds),['==',['max',-5,['min',5,gradeExpression]],grade]],
          layout:{'line-cap':'round','line-join':'round'},
          paint:{'line-color':color,'line-width':width(road,casing),'line-opacity':grade < 0 ? 0.55 : 1,...(grade < 0 ? {'line-dasharray':[2,1.4]} : {})}});
      }
    }
    if (mode === 'gta') {
      layers.push({id:`motorway-${grade}-center`,type:'line',source:'roads',filter:['all',kinds(['motorway']),['==',['max',-5,['min',5,gradeExpression]],grade]],minzoom:12,
        layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':p.motorway,'line-width':['interpolate',['linear'],['zoom'],12,0.6,15,2.8,17,5],'line-opacity':grade < 0 ? 0.5 : 1}});
    }
  }
  const textPaint = {'text-color':p.label,'text-halo-color':p.labelHalo,'text-halo-width':mode === 'gta' ? tokens.labels.haloWidth : 1.3};
  layers.push({id:'road-labels',type:'symbol',source:'roads',minzoom:tokens.visibility.roadLabelsMinzoom,
    filter:['all',['has','name'],kinds(['primary','secondary','tertiary'])],layout:{'symbol-placement':'line','symbol-spacing':tokens.visibility.labelSpacing,'text-field':['get','name'],'text-font':tokens.labels.fonts,'text-size':tokens.labels.roadSize,'text-max-angle':30},paint:textPaint});
  layers.push({id:'place-points',type:'circle',source:'places',minzoom:12.5,filter:['all',eq('featured',true),kinds(['landmark','park','square'])],paint:{'circle-radius':3.5,'circle-color':mode === 'gta' ? '#E4E9D5' : '#6B7F59','circle-stroke-color':p.labelHalo,'circle-stroke-width':1.5}});
  layers.push({id:'place-labels',type:'symbol',source:'places',minzoom:tokens.visibility.placesMinzoom,
    filter:eq('featured',true),
    layout:{'symbol-sort-key':['get','rank'],'text-field':tokens.labels.uppercasePlaces?['upcase',['get','name']]:['get','name'],'text-font':tokens.labels.fonts,'text-size':['interpolate',['linear'],['zoom'],11,tokens.labels.placeSizes[0],14,tokens.labels.placeSizes[1],17,tokens.labels.placeSizes[2]],'text-letter-spacing':0.08,'text-max-width':15,'text-padding':3,'text-variable-anchor':['top','bottom','left','right'],'text-radial-offset':1.4,'text-allow-overlap':false},paint:textPaint});
  layers.push({id:'district-labels',type:'symbol',source:'places',minzoom:tokens.visibility.districtLabelsMinzoom,filter:['all',eq('featured',false),eq('kind','district'),['<=',['coalesce',['get','rank'],3],2]],
    layout:{'text-field':tokens.labels.uppercasePlaces?['upcase',['get','name']]:['get','name'],'text-font':tokens.labels.fonts,'text-size':tokens.labels.districtSize,'text-letter-spacing':0.06,'text-padding':22,'text-max-width':14},paint:{...textPaint,'text-opacity':0.9}});
  const selected=new Set(overrides.poiKinds ?? POI_CATEGORIES.map(c=>c.id));
  const poiVisible=overrides.poisEnabled!==false;
  layers.push({id:'poi-labels',type:'symbol',source:'pois',minzoom:16.8,filter:['all',['!=',['get','name'],''],kinds([...selected])],layout:{visibility:poiVisible?'visible':'none','text-field':['get','name'],'text-font':tokens.labels.fonts,'text-size':10,'text-offset':[0,2.2],'text-anchor':'top','text-padding':3,'text-max-width':13},paint:textPaint});
  // Placement tests topmost layers first: put major categories last for priority.
  const anchorParkIds=['way/159590062','way/220688361','way/670650221'];
  for(const category of [...POI_CATEGORIES].reverse()) layers.push({id:`poi-${category.id}`,type:'symbol',source:'pois',minzoom:category.minzoom,filter:category.id==='park'?['all',eq('kind','park'),['!', ['in',['get','osm_id'],['literal',anchorParkIds]]]]:eq('kind',category.id),
    layout:{visibility:poiVisible&&selected.has(category.id)?'visible':'none','icon-image':`poi-${category.id}`,'icon-size':['interpolate',['linear'],['zoom'],12,0.72,15,0.85,18,1],'icon-padding':4,'icon-allow-overlap':false,'symbol-sort-key':['case',['==',['get','name'],''],1,0]}});
  // Keep the five geographic anchors readable above the service-icon collision pass.
  layers.push(layers.splice(layers.findIndex(layer=>layer.id==='place-labels'),1)[0]);
  // The three verified park/square anchors keep a blip even beside their own label.
  layers.push({id:'landmark-icons',type:'symbol',source:'places',minzoom:12.2,filter:['all',eq('featured',true),kinds(['park','square'])],layout:{visibility:poiVisible&&selected.has('park')?'visible':'none','icon-image':'poi-park','icon-size':0.85,'icon-allow-overlap':true,'icon-ignore-placement':true}});
  const sources=Object.fromEntries(['areas','roads','waterways','places','pois'].map(name=>[name,{type:'geojson',data:`/data/${name}.geojson`,tolerance:tokens.geometry.geojsonTilingTolerance,attribution:name === 'areas' ? '© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors · ODbL' : undefined}]));
  sources.buildings={type:'vector',tiles:['/tiles/buildings/{z}/{x}/{y}.pbf'],minzoom:11,maxzoom:16,bounds:[-49.325,-16.755,-49.205,-16.64],attribution:'<a href="https://docs.overturemaps.org/attribution/" target="_blank" rel="noopener">Overture</a> · <a href="https://sites.research.google/open-buildings/" target="_blank" rel="noopener">Google Open Buildings</a> · <a href="https://github.com/microsoft/GlobalMLBuildingFootprints" target="_blank" rel="noopener">Microsoft</a> · ODbL'};
  return {version:8,sprite:'/sprites/poi',name:mode === 'gta' ? 'Goiânia · San Andreas' : 'Goiânia · Neutro',sources,layers,transition:{duration:0,delay:0}};
}
