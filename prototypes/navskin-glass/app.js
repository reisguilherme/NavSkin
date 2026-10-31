import { createPrototypeMap } from './map.js';

const $ = (selector) => document.querySelector(selector);
const iconPaths = {
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5 9-5Zm-9 9 9 5 9-5M3 16l9 5 9-5"/>',
  sliders: '<path d="M4 6h5m4 0h7M4 18h9m4 0h3M4 12h11m4 0h1"/><circle cx="11" cy="6" r="2"/><circle cx="17" cy="12" r="2"/><circle cx="15" cy="18" r="2"/>',
  bookmark: '<path d="M6 4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v17l-6-4-6 4V4Z"/>',
  settings: '<path d="m10 3-1 3-3 1-2-1-2 4 2 2v2l-1 2 3 3 2-1 2 1 1 2h4l1-2 2-1 2 1 2-4-2-2v-2l1-2-3-3-2 1-2-1-1-3Z" transform="translate(0 -1) scale(.95)"/><circle cx="12" cy="12" r="3"/>',
  target: '<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2.7"/><path d="M12 2v3m0 14v3M2 12h3m14 0h3"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  stop: '<rect x="5" y="5" width="14" height="14" rx="3"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  pin: '<path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2.3"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  parks: '<path d="M12 3a4 4 0 0 0-4 3 4 4 0 0 0-4 4 4 4 0 0 0 2 7h12a4 4 0 0 0 2-7 4 4 0 0 0-4-4 4 4 0 0 0-4-3Zm0 14v5m-4 0h8"/>',
  fuel: '<path d="M4 21V4h10v17H4ZM4 9h10m0 5h2a2 2 0 0 1 2 2v2a2 2 0 0 0 4 0V9l-4-4M2 21h14m4-14v4h2"/>',
  food: '<path d="M4 3v6a3 3 0 0 0 6 0V3M7 3v18M18 21V3c-4 2-5 9 0 9"/>',
  parking: '<rect x="3" y="3" width="18" height="18" rx="5"/><path d="M9 17V7h4a3 3 0 0 1 0 6H9"/>',
  health: '<path d="M9 3h6v6h6v6h-6v6H9v-6H3V9h6Z"/>',
  shops: '<path d="M5 7h14l2 14H3L5 7Zm3 0V5a4 4 0 0 1 8 0v2"/>',
  education: '<path d="m2 8 10-5 10 5-10 5L2 8Zm4 3v6l6 3 6-3v-6m4-3v9"/>',
  culture: '<path d="m3 7 9-5 9 5H3Zm2 3v9m5-9v9m4-9v9m5-9v9M3 22h18M3 19h18"/>',
  religion: '<path d="M10 3h4v5h6v4h-6v10h-4V12H4V8h6Z"/>',
  voice: '<path d="m11 4-6 5H2v6h3l6 5V4Zm5 4a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/>'
};
const icon = (name) => '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (iconPaths[name] || iconPaths.pin) + '</svg>';
document.querySelectorAll('[data-icon]').forEach(node => { node.innerHTML = icon(node.dataset.icon); });

const themes = [
  { id: 'gta', name: 'GTA SA', subtitle: 'As ruas, em outro ritmo.' },
  { id: 'rosa', name: 'Rosa Pop', subtitle: 'Um caminho mais colorido.' },
  { id: 'rdr2', name: 'RDR2', subtitle: 'Uma nova fronteira.' }
];
const filters = [['parks','Parques'], ['fuel','Postos'], ['food','Comida'], ['parking','Estacionar'], ['health','Saúde'], ['shops','Compras'], ['education','Educação'], ['culture','Cultura'], ['religion','Religião']];
const places = [
  { id:'praca', name:'Praça Universitária', address:'Setor Universitário · Goiânia', distance:'1,2 km', kind:'parks' },
  { id:'posto', name:'Posto Universitário', address:'Parada de exemplo · Setor Universitário', distance:'450 m', kind:'fuel' },
  { id:'cafe', name:'Café do Campus', address:'Parada de exemplo · Setor Universitário', distance:'600 m', kind:'food' },
  { id:'hospital', name:'Hospital das Clínicas', address:'1ª Avenida · Setor Leste Universitário', distance:'1,6 km', kind:'health' },
  { id:'museu', name:'Museu Antropológico', address:'Praça Universitária · UFG', distance:'900 m', kind:'culture' }
];
const state = { navigating:true, theme:'gta', menu:false, panel:null, searchMode:'destination', destination:places[0], stop:null, filters:new Set(filters.map(([id])=>id)), voice:true, autoZoom:true, follow:true };
let mapController, toastTimeout, lastFocus, themeChange = 0;

function notify(message) {
  clearTimeout(toastTimeout); $('#toast').textContent=message; $('#toast').hidden=false;
  toastTimeout=setTimeout(()=>{$('#toast').hidden=true;},2400);
}
function sync() {
  $('#app').classList.toggle('navigating',state.navigating);
  $('#direction').setAttribute('aria-label',state.navigating?'Próxima manobra':'Menu do mapa');
  $('#app').dataset.theme=state.theme;
  $('#trip-card').hidden=!state.navigating;
  $('#search-trigger').hidden=state.navigating;
  $('#speed-value').textContent=state.navigating?'38':'0';
  $('#speed').setAttribute('aria-label','Velocidade simulada: '+(state.navigating?'38':'0')+' quilômetros por hora');
  $('#stop-chip').hidden=!state.stop || !state.navigating;
  $('#stop-chip-text').textContent=state.stop ? state.stop.name : '';
  $('#stop-chip').setAttribute('aria-label',state.stop ? 'Remover parada '+state.stop.name : 'Remover parada');
  $('#eta-duration').innerHTML=(state.stop?'16':'12')+' <span>min</span>';
  $('#eta-distance').innerHTML=(state.stop?'5,6':'4,8')+' <span>km</span>';
  $('#eta-arrival').textContent=state.stop?'18:46':'18:42';
  $('#scene-caption').textContent=state.navigating?'Navegação simulada · toque nos controles':'Explorar · arraste e gire o mapa';
  const current=state.panel==='themes'?'themes':state.panel==='search'&&state.searchMode==='stop'?'stop':state.menu?'menu':state.navigating?'navigate':'explore';
  document.querySelectorAll('[data-scene]').forEach(button=>{button.classList.toggle('active',button.dataset.scene===current);button.setAttribute('aria-pressed',String(button.dataset.scene===current));});
}
function toggleMenu(open=!state.menu) {
  state.menu=open;$('#menu').hidden=!open;$('#menu-backdrop').hidden=!open;
  $('#more').setAttribute('aria-expanded',String(open));$('#more').setAttribute('aria-label',open?'Fechar menu':'Abrir menu');sync();
  if(open) $('#menu button').focus({preventScroll:true});
}
function closeSheet(restoreFocus=true) {
  state.panel=null;$('#sheet').hidden=true;$('#sheet-backdrop').hidden=true;sync();
  if(restoreFocus && lastFocus?.isConnected && !lastFocus.closest('[hidden]')) lastFocus.focus({preventScroll:true});
  else if(restoreFocus) $('#more').focus({preventScroll:true});
}
function setNavigating(value) {
  state.navigating=value;state.follow=true;$('#recenter').classList.remove('is-drifted');
  if(!value) state.stop=null;
  toggleMenu(false);closeSheet(false);sync();mapController?.setNavigating(value);
}
function resultMarkup(place, mode) {
  return '<button class="result-item" data-place="'+place.id+'" aria-label="'+(mode==='stop'?'Adicionar parada em ':'Selecionar ')+place.name+'"><span class="result-icon">'+icon(place.kind)+'</span><span class="result-copy"><strong>'+place.name+'</strong><small>'+place.address+'</small></span><span class="result-end">'+(mode==='stop'?icon('plus'):place.distance)+'</span></button>';
}
function renderResults(query='') {
  const normalized=query.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
  const results=places.filter(place=>(place.name+' '+place.address).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().includes(normalized));
  $('#search-results').innerHTML=results.length?results.map(p=>resultMarkup(p,state.searchMode)).join(''):'<p class="empty-results">Nenhum lugar nesta seleção. Experimente “Praça” ou “Café”.</p>';
}
function openPanel(panel) {
  lastFocus=document.activeElement;toggleMenu(false);state.panel=panel;
  const titles={themes:['Temas','Escolha o estilo do seu mapa.'],filters:['No seu caminho','Escolha os lugares que aparecem no mapa.'],saved:['Seus lugares','Seus próximos destinos, por perto.'],settings:['Configurações','Preferências desta demonstração.'],search:state.searchMode==='stop'?['Adicionar parada','Seu destino final continua na viagem.']:['Para onde?','Escolha um destino para experimentar.'],destination:['Seu destino','Uma prévia antes de seguir.'],credits:['Sobre o mapa','Dados e recursos usados neste protótipo.']};
  const [title,description]=titles[panel];$('#sheet-title').textContent=title;$('#sheet-description').textContent=description;
  let html='';
  if(panel==='themes') {
    html='<div class="theme-carousel" aria-label="Carrossel de temas">'+themes.map(t=>'<button class="theme-card '+(state.theme===t.id?'active':'')+'" data-theme="'+t.id+'" aria-label="Aplicar tema '+t.name+'" aria-pressed="'+(state.theme===t.id)+'"><div class="theme-preview"><img src="/assets/maps/previews/'+t.id+'.webp" alt="Prévia real do mapa '+t.name+'"><span class="theme-check">✓</span></div><span class="theme-label"><strong>'+t.name+'</strong><small>'+t.subtitle+'</small></span></button>').join('')+'</div><p class="sheet-footnote">Deslize para conhecer os temas. Toque para aplicar.</p><button class="sheet-primary" data-action="close">Voltar ao mapa</button>';
  } else if(panel==='filters') {
    html='<div class="filter-grid">'+filters.map(([id,label])=>'<button class="filter-option '+(state.filters.has(id)?'active':'')+'" data-filter="'+id+'" aria-pressed="'+state.filters.has(id)+'"><span class="filter-check">✓</span>'+icon(id)+'<span>'+label+'</span></button>').join('')+'</div><button class="sheet-primary" data-action="close">Ver mapa</button>';
  } else if(panel==='saved') {
    html='<div class="result-list">'+[places[0],places[3],places[4]].map(p=>resultMarkup(p,'destination')).join('')+'</div><p class="sheet-footnote">Seleção de exemplo. Seus favoritos do aplicativo não são alterados.</p>';
  } else if(panel==='search') {
    html=(state.searchMode==='stop'?'<div class="selected-place"><span>Destino final</span>'+state.destination.name+'</div>':'')+'<label class="search-field">'+icon('search')+'<input id="place-search" type="search" placeholder="Busque um lugar" autocomplete="off" aria-label="Buscar lugar"></label><div id="search-results" class="result-list"></div><p class="sheet-footnote">Lugares de demonstração · busca local</p>';
  } else if(panel==='destination') {
    html='<div class="destination-summary"><span class="result-icon">'+icon(state.destination.kind)+'</span><span class="result-copy"><strong>'+state.destination.name+'</strong><small>'+state.destination.address+'</small></span></div><div class="destination-meta"><div><strong>12 min</strong><small>Tempo estimado</small></div><div><strong>4,8 km</strong><small>Distância</small></div><div><strong>18:42</strong><small>Chegada</small></div></div><p class="sheet-footnote">Viagem ilustrativa para experimentar a interface.</p><button class="sheet-primary" data-action="start">Iniciar demonstração</button>';
  } else if(panel==='settings') {
    const settings=[['voice','Orientações por voz','Preferência visual; não reproduz áudio.'],['autoZoom','Zoom automático','Aproximar ao chegar às manobras.']];
    html='<div class="settings-list">'+settings.map(([id,label,detail])=>'<div class="setting-row"><div><strong>'+label+'</strong><small>'+detail+'</small></div><button class="switch" role="switch" aria-checked="'+state[id]+'" aria-label="'+label+'" data-setting="'+id+'"></button></div>').join('')+'<div class="setting-row"><div><strong>Estilo do mapa</strong><small>'+themes.find(t=>t.id===state.theme).name+'</small></div><button class="close-sheet" data-action="themes" aria-label="Alterar estilo">'+icon('chevron')+'</button></div></div><button class="credit-link" data-action="credits">Créditos e fontes do mapa</button><button class="sheet-primary secondary" data-action="close">Pronto</button>';
  } else if(panel==='credits') {
    html='<div class="credits"><p>© <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap contributors</a>. Edifícios: <a href="https://docs.overturemaps.org/attribution/" target="_blank" rel="noopener">Overture Maps</a>, Google Open Buildings e Microsoft · ODbL.</p><p>MapLibre GL JS. Estilos, fontes, sprites e dados locais do NavSkin. Recursos dos jogos mantêm a procedência registrada no projeto.</p><p>Este protótipo não acessa seu GPS. A rota, velocidade e ETA são exemplos; adicionar uma parada demonstra o fluxo de interface, sem consultar um serviço de rotas.</p></div><button class="sheet-primary secondary" data-action="close">Voltar ao mapa</button>';
  }
  $('#sheet-content').innerHTML=html;$('#sheet').hidden=false;$('#sheet-backdrop').hidden=false;
  if(panel==='search') {renderResults();$('#place-search').addEventListener('input',event=>renderResults(event.target.value));}
  sync();
  requestAnimationFrame(()=>{const target=panel==='search'?$('#place-search'):$('#close-sheet');target?.focus({preventScroll:true});});
}
$('#more').addEventListener('click',()=>toggleMenu());
$('#menu-backdrop').addEventListener('click',()=>toggleMenu(false));
$('#menu').addEventListener('click',event=>{const button=event.target.closest('[data-panel]');if(button)openPanel(button.dataset.panel);});
$('#close-sheet').addEventListener('click',()=>closeSheet());
$('#sheet-backdrop').addEventListener('click',()=>closeSheet());
$('#stop-navigation').addEventListener('click',()=>{setNavigating(false);notify('Navegação encerrada.');});
$('#add-stop').addEventListener('click',()=>{state.searchMode='stop';openPanel('search');});
$('#search-trigger').addEventListener('click',()=>{state.searchMode='destination';openPanel('search');});
$('#stop-chip').addEventListener('click',()=>{state.stop=null;sync();notify('Parada removida.');});
$('#recenter').addEventListener('click',()=>{state.follow=true;mapController?.recenter();$('#recenter').classList.remove('is-drifted');notify('Mapa recentralizado.');});
$('#sheet-content').addEventListener('click',async event=>{
  const button=event.target.closest('button');if(!button)return;
  if(button.dataset.action==='close')closeSheet();
  if(button.dataset.action==='start')setNavigating(true);
  if(button.dataset.action==='themes')openPanel('themes');
  if(button.dataset.action==='credits')openPanel('credits');
  if(button.dataset.theme){
    const next=button.dataset.theme,request=++themeChange;
    try{await mapController?.setTheme(next);if(request!==themeChange)return;state.theme=next;sync();document.querySelectorAll('.theme-card').forEach(card=>{card.classList.toggle('active',card.dataset.theme===next);card.setAttribute('aria-pressed',String(card.dataset.theme===next));});}
    catch{notify('Não foi possível carregar esse tema.');}
  }
  if(button.dataset.filter){const id=button.dataset.filter,enabled=!state.filters.has(id);enabled?state.filters.add(id):state.filters.delete(id);button.classList.toggle('active',enabled);button.setAttribute('aria-pressed',String(enabled));mapController?.setCategory(id,enabled);}
  if(button.dataset.setting){const id=button.dataset.setting;state[id]=!state[id];button.setAttribute('aria-checked',String(state[id]));}
  if(button.dataset.place){
    const place=places.find(p=>p.id===button.dataset.place);
    if(state.panel==='search'&&state.searchMode==='stop'){state.stop=place;closeSheet();sync();notify('Parada adicionada à demonstração.');}
    else {state.destination=place;openPanel('destination');}
  }
});
document.addEventListener('keydown',event=>{
  if(event.key==='Escape'){if(state.panel)closeSheet();else if(state.menu){toggleMenu(false);$('#more').focus();}}
  if(event.key==='Tab'&&state.panel){const focusables=[...$('#sheet').querySelectorAll('button,input,a[href]')].filter(el=>!el.disabled&&!el.hidden);const first=focusables[0],last=focusables.at(-1);if(event.shiftKey&&document.activeElement===first){event.preventDefault();last?.focus();}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first?.focus();}}
});
document.querySelectorAll('[data-scene]').forEach(button=>button.addEventListener('click',()=>{
  const scene=button.dataset.scene;closeSheet(false);toggleMenu(false);
  if(scene==='explore')setNavigating(false);
  if(scene==='navigate')setNavigating(true);
  if(scene==='menu'){setNavigating(true);toggleMenu(true);}
  if(scene==='themes')openPanel('themes');
  if(scene==='stop'){setNavigating(true);state.searchMode='stop';openPanel('search');}
}));
$('#reset-demo').addEventListener('click',async()=>{
  state.stop=null;state.destination=places[0];state.filters=new Set(filters.map(([id])=>id));state.theme='gta';state.voice=true;state.autoZoom=true;
  await mapController?.setTheme('gta');filters.forEach(([id])=>mapController?.setCategory(id,true));setNavigating(true);
});
sync();
try{
  mapController=await createPrototypeMap($('#map'));
  mapController.map.on('dragstart',()=>{state.follow=false;$('#recenter').classList.add('is-drifted');});
  mapController.map.on('rotatestart',event=>{if(event.originalEvent){state.follow=false;$('#recenter').classList.add('is-drifted');}});
  await mapController.ready;
  if(state.theme!=='gta')await mapController.setTheme(state.theme);
  filters.forEach(([id])=>mapController.setCategory(id,state.filters.has(id)));
  mapController.setNavigating(state.navigating);$('#map-status').hidden=true;
  new ResizeObserver(()=>mapController.resize()).observe($('#app'));
  window.navskinPrototype={state,controller:mapController};
}catch(error){$('#map-status').textContent=error.message;$('#map-status').classList.add('error');}
