import {createMapView} from './map.js';
import {Marker} from '/vendor/maplibre-gl.mjs';
const $=id=>document.getElementById(id);
const state={destination:'Praça Universitária',query:'Praça Universitária',phase:'preview'};
let view, marker, route, toastTimer;
function toast(message){clearTimeout(toastTimer);$('search-toast').textContent=message;$('search-toast').hidden=false;toastTimer=setTimeout(()=>$('search-toast').hidden=true,2100);}
function update(){
  const active=state.phase==='navigating', selected=state.phase==='preview';
  $('route-preview').hidden=!selected;
  $('search-bar').hidden=active;$('navigation-actions').hidden=!active;
  $('clear-destination').hidden=!(state.query||state.destination);
  $('demo-speed').textContent=active?'24':'0';
  $('state-label').textContent='Estado: '+(active?'navegação simulada':selected?'rota pronta':'explorar / sem rota');
  if(view?.map.getLayer('search-demo-route'))view.map.setLayoutProperty('search-demo-route','visibility',selected||active?'visible':'none');
  if(marker)marker.getElement().hidden=!(selected||active);
}
function clearDestination(){
  if(state.phase==='navigating')return;
  state.destination=null;state.query='';state.phase='explore';
  $('destination-search').value='';$('local-results').hidden=true;update();
  $('destination-search').focus({preventScroll:true});toast('Destino e rota removidos.');
}
function showResults(){
  if(state.phase==='navigating')return;
  const match=!state.query||'praca universitaria goiania'.includes(state.query.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase());
  $('local-results').hidden=false;$('choose').hidden=!match;$('empty').hidden=match;
}
function choose(){
  state.destination=state.query='Praça Universitária';state.phase='preview';
  $('destination-search').value=state.query;$('local-results').hidden=true;update();
}
$('clear-destination').onclick=clearDestination;
$('destination-search').oninput=e=>{state.query=e.target.value;state.destination=null;state.phase='explore';update();showResults();};
$('destination-search').onclick=showResults;
$('destination-search').onkeydown=e=>{if(e.key==='Escape'){$('local-results').hidden=true;e.target.blur();}if(e.key==='Enter'&&!$('choose').hidden)choose();};
$('choose').onclick=choose;
$('start').onclick=()=>{state.phase='navigating';$('local-results').hidden=true;update();toast('Navegação de exemplo iniciada.');};
$('stop').onclick=()=>{state.phase='explore';clearDestination();};
$('restore').onclick=()=>{choose();view?.goTo('universidade');};
$('recenter').onclick=()=>view?.goTo('universidade');
try{
  const response=await fetch('./data/scene.json');if(!response.ok)throw new Error('Não foi possível abrir o trajeto local.');
  route=(await response.json()).route.coordinates;
  view=await createMapView($('search-map'),{theme:'rosa',proposed:true});
  await view.ready;
  view.map.addSource('search-demo',{type:'geojson',data:{type:'Feature',properties:{demonstration:true},geometry:{type:'LineString',coordinates:route}}});
  view.map.addLayer({id:'search-demo-route',type:'line',source:'search-demo',layout:{'line-cap':'round','line-join':'round'},paint:{'line-color':'#d23285','line-width':6,'line-opacity':.9}});
  marker=new Marker({color:'#c93985'}).setLngLat(route.at(-1)).addTo(view.map);
  update();window.navskinSearchDemo={state,view,clearDestination};
}catch(error){toast(error.message);console.error(error);}
window.addEventListener('pagehide',e=>{if(!e.persisted){view?.destroy();marker?.remove();}});
