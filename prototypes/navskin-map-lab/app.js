import { THEMES } from './theme-designs.js';
import { createMapView, REGIONS, CATEGORIES, maplibre } from './map.js';
import { NavigationScene } from './navigation-scene.js';
import { readPerspective, savePerspective } from './map-perspective.js';

const page=document.body.dataset.page || 'maps';
const params=new URLSearchParams(location.search);
const perspectiveContext=page==='navigation'?'navigation':'browse';
let threeD=readPerspective(perspectiveContext);
let theme=THEMES.find(t=>t.id===(params.get('tema')||document.body.dataset.theme))?.id || (page==='navigation'?'cyberpunk':'smw');
let night=params.get('noite')==='1',version='proposal',views=[],scene=null,sceneInitialized=false,sync=true,stopMarker=null;
const $=id=>document.getElementById(id);
const symbols={ layers:'<path d="m12 3 10 6-10 6L2 9zm-9 10 9 5 9-5M3 17l9 5 9-5"/>', compare:'<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M12 4v16"/>', navigation:'<path d="m12 2 9 20-9-5-9 5Z"/>', plus:'<path d="M12 5v14M5 12h14"/>', minus:'<path d="M5 12h14"/>', target:'<circle cx="12" cy="12" r="7"/><circle cx="12" cy="12" r="2"/><path d="M12 1v3m0 16v3M1 12h3m16 0h3"/>', arrow:'<path d="M6 21V11a5 5 0 0 1 5-5h9m-6-5 6 5-6 5"/>', sun:'<circle cx="12" cy="12" r="4"/><path d="M12 1v2m0 18v2M1 12h2m18 0h2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2"/>', moon:'<path d="M20 15A8 8 0 0 1 9 4a8 8 0 1 0 11 11Z"/>', dots:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>', play:'<path d="m8 4 13 8-13 8Z"/>', pause:'<path d="M8 5v14M16 5v14"/>', reset:'<path d="M3 10a9 9 0 1 1 1 8M3 3v7h7"/>', external:'<path d="M14 3h7v7m-1-6-9 9M10 4H4v16h16v-6"/>', pin:'<path d="M19 10c0 6-7 12-7 12S5 16 5 10a7 7 0 0 1 14 0Z"/><circle cx="12" cy="10" r="2"/>', check:'<path d="m5 12 4 4L20 5"/>', close:'<path d="m6 6 12 12M6 18 18 6"/>' };
const icon=name=>`<svg viewBox="0 0 24 24" aria-hidden="true">${symbols[name]||symbols.layers}</svg>`;
const navLink=(id,label,href,symbol)=>`<a class="nav-link ${page===id?'active':''}" href="${href}?tema=${theme}${night?'&noite=1':''}">${icon(symbol)}<span>${label}</span></a>`;
const current=()=>THEMES.find(t=>t.id===theme);
document.getElementById('root').innerHTML=`
<header class="topbar"><a href="./index.html" class="brand"><span class="brand-mark">N<span>↗</span></span><span>NavSkin <small>MAP LAB</small></span></a><nav aria-label="Painéis">${navLink('maps','Mapas','./index.html','layers')}${navLink('compare','Comparar','./comparar.html','compare')}${navLink('navigation','Navegação 3D','./navegacao.html','navigation')}<a class="nav-link" href="./novos-mapas.html" aria-label="Novos mapas: Pip-Boy, Hollow Knight e Crossy Road">${icon('plus')}<span>Novos mapas</span></a><a class="nav-link" href="./markers-3d.html"><span>Garagem 3D</span></a><a class="nav-link" href="./propostas.html" aria-label="Outros conceitos de mapas">${icon('external')}<span>Conceitos</span></a></nav><span class="study-badge"><i></i> ESTUDO VISUAL <b>02</b></span></header>
<div class="workspace">
<aside class="sidebar">
  <div class="eyebrow">DIREÇÃO DE ARTE</div><h1>${page==='navigation'?'Outro ponto<br>de vista.':page==='compare'?'Veja o que<br>muda.':'Um lugar.<br>Oito mundos.'}</h1>
  <p class="intro">${page==='navigation'?'Volume, transparência e perspectiva na sua próxima curva.':page==='compare'?'O desenho atual e a proposta, sobre a mesma geografia.':'Explore Goiânia com a linguagem visual de cada universo.'}</p>
  <div class="theme-list" role="group" aria-label="Temas do mapa">${THEMES.map((t,i)=>`<button class="theme-card ${theme===t.id?'selected':''}" data-theme="${t.id}" aria-pressed="${theme===t.id}" style="--swatch:${t.accent}"><span class="theme-art art-${t.id}"><i></i><i></i><i></i></span><span><strong>${t.title}</strong><small>${t.kicker}</small></span><span class="theme-number">0${i+1}</span></button>`).join('')}</div>
  <div class="sidebar-section"><div class="section-label">LUZ DO MAPA</div><div class="segmented"><button id="day" aria-pressed="${!night}">${icon('sun')}Dia</button><button id="night" aria-pressed="${night}">${icon('moon')}Noite</button></div></div>
  <div class="sidebar-section"><div class="section-label">PERSPECTIVA</div><div class="segmented"><button id="map-2d" aria-pressed="${!threeD}">2D</button><button id="map-3d" aria-pressed="${threeD}">3D</button></div></div>
  ${page!=='navigation'?`<div class="sidebar-section"><label class="section-label" for="region">RECORTE DE GOIÂNIA</label><select id="region">${Object.entries(REGIONS).map(([id,r])=>`<option value="${id}">${r.name}</option>`).join('')}</select></div>`:''}
  <div class="concept-note"><span id="concept-kicker"></span><p id="concept-description"></p><ul id="concept-features"></ul></div>
  <div class="lab-links"><a href="./pink-icons/index.html">Ícones do Pink World ↗</a><a href="./busca.html">Testar o × da busca ↗</a></div><div class="sidebar-foot"><span class="local-dot"></span> Funciona com dados locais<span>Catálogo integrado · APK preservado</span></div>
</aside>
<main class="main-stage ${page}">
  <div class="stage-toolbar"><div><span class="eyebrow">${page==='navigation'?'CÂMERA / VOLUMES':page==='compare'?'ANTES / PROPOSTA':'CARTOGRAFIA INTERATIVA'}</span><h2 id="stage-title"></h2></div><div class="toolbar-actions">${page==='maps'?'<div class="segmented compact"><button id="proposal" aria-pressed="true">Proposta</button><button id="original" aria-pressed="false">Recursos do app</button></div>':page==='compare'?'<label class="switch-label"><input id="sync" type="checkbox" checked> Câmeras sincronizadas</label>':'<span class="simulation-label"><i></i> VIAGEM SIMULADA</span>'}<button id="reset-view" class="icon-btn" title="Restaurar enquadramento" aria-label="Restaurar enquadramento">${icon('target')}</button></div></div>
  <div class="map-stage ${page==='compare'?'split-stage':''}">
    ${page==='compare'?'<section class="map-pane"><div class="pane-label"><i></i> RECURSOS DO APP</div><div id="map-original" class="map-canvas" aria-label="Mapa atual do aplicativo"></div></section>':''}
    <section class="map-pane ${page==='navigation'?'navigation-pane':''}">
      ${page==='compare'?'<div class="pane-label proposed"><i></i> NOVA PROPOSTA</div>':''}
      <div id="map" class="map-canvas" aria-label="Mapa interativo de Goiânia"></div>
      ${page==='maps'?'<div class="map-caption"><span class="caption-dot"></span><span id="map-caption">DESENHO PROPOSTO</span></div>':''}
      ${page==='navigation'?`<div class="nav-vignette"></div><div class="nav-hud"><div class="direction-card glass"><div class="direction-symbol">${icon('arrow')}</div><div><strong id="maneuver-distance">180 <small>m</small></strong><span>Av. Universitária</span></div><button id="nav-menu" class="icon-btn" aria-label="Opções da cena 3D" aria-expanded="false">${icon('dots')}</button></div><div class="nav-menu glass" id="nav-menu-panel" hidden><strong>Opções da cena</strong><button id="menu-flat">Alternar 2D / 3D</button><button id="menu-hologram">Mostrar / ocultar projeções</button><button id="menu-focus">Recentralizar câmera</button></div><div class="nav-bottom"><div class="nav-floats"><div class="nav-circle glass speed"><strong id="speed-value">0</strong><span>km/h</span></div><button id="recenter" class="nav-circle glass" aria-label="Recentralizar navegação">${icon('target')}</button></div><div class="trip-card glass"><div class="trip-summary"><div><strong id="remaining-time">2 <small>min</small></strong><span>Tempo</span></div><div><strong id="remaining-distance">540 <small>m</small></strong><span>Distância</span></div><div><strong id="arrival-time">18:42</strong><span>Chegada simulada</span></div></div><div class="trip-actions"><button id="stop-trip"><span class="stop-square"></span>Parar</button><button id="add-stop">${icon('plus')}Adicionar parada</button></div></div></div></div><div class="nav-scene-badge">CONTORNOS REAIS <span>·</span> VOLUMES ILUSTRATIVOS</div>`:''}
      <div class="zoom-controls"><button id="zoom-in" aria-label="Aproximar mapa">${icon('plus')}</button><button id="zoom-out" aria-label="Afastar mapa">${icon('minus')}</button></div>
      <div id="map-status" class="map-status" role="status">Preparando os dados locais…</div>
    </section>
  </div>
  ${page==='navigation'?`<section class="navigation-controls"><div class="play-controls"><button id="play" class="play-button">${icon('play')}<span>Reproduzir trajeto</span></button><button id="rewind" class="icon-btn" aria-label="Voltar ao início">${icon('reset')}</button><label class="progress-label" for="progress">PERCURSO <b id="progress-value">0%</b></label><input id="progress" type="range" min="0" max="100" step="0.1" value="0" aria-label="Posição no percurso"></div><div class="tuning-grid"><label>Inclinação <output id="pitch-value">48°</output><input id="pitch" type="range" min="0" max="62" value="48" step="1"></label><label>Transparência <output id="opacity-value">4%</output><input id="opacity" type="range" min="0.08" max="1" value="0.96" step="0.01"></label><label>Escala de altura <output id="height-value">1,0×</output><input id="height" type="range" min="0.4" max="2.2" value="1" step="0.1"></label><div class="volume-toggles"><label><input id="buildings" type="checkbox" checked> Volumes</label><label><input id="edges" type="checkbox"> Contornos de luz</label></div></div><p class="scene-note" id="scene-note">Contornos reais de edifícios. Alturas sem medição são simuladas; trajeto, velocidade e ETA são demonstrativos.</p></section>`:`<section class="map-inspector"><div class="inspector-label">LUGARES</div><div class="category-list">${CATEGORIES.map(([id,label])=>`<label><input type="checkbox" data-category="${id}" checked><span>${label}</span></label>`).join('')}</div><span class="gesture-tip">Arraste para explorar · botão direito para girar</span></section>`}
  <footer class="stage-footer"><span id="ready-label"><i></i> Carregando…</span><span>Goiânia, GO <b>·</b> <button id="credits-open">Dados e origem dos recursos ↗</button></span></footer>
</main></div>
<dialog id="credits-dialog"><form method="dialog"><button class="dialog-close icon-btn" aria-label="Fechar">${icon('close')}</button></form><span class="eyebrow">SOBRE ESTE ESTUDO</span><h2>Desenho novo.<br>Geografia preservada.</h2><p>Ruas, parques, água e contornos de edifícios vêm dos dados locais do NavSkin: © OpenStreetMap contributors, Overture Maps Foundation, Google Open Buildings e Microsoft Global ML Building Footprints.</p><p>Fontes e ícones dos temas reutilizam o pacote local e seus avisos de origem. Materiais, sombras e volumes deste painel são estudos visuais. Rosa Pop é uma direção autoral; não representa um jogo específico.</p><p><strong>Não é uma navegação real.</strong> O trajeto é um exemplo, sem GPS, cálculo de rota, tráfego ou consulta a APIs. Nenhum APK ou configuração do celular é modificado.</p><a href="./README.md" target="_blank" rel="noopener">Leia como usar e editar estes HTMLs ↗</a></dialog><div id="toast" class="toast" role="status" hidden></div>`;

function updatePerspectiveUI(){for(const id of ['map-2d','map-3d'])$(id)?.setAttribute('aria-pressed',String(id==='map-3d'?threeD:!threeD));}
function setPerspective(enabled){threeD=Boolean(enabled);savePerspective(perspectiveContext,threeD);views.forEach(view=>view.setThreeD(threeD,{moveCamera:!scene}));if(scene){$('pitch').value=threeD?48:0;$('pitch-value').textContent=threeD?'48°':'0°';scene.setOptions({pitch:threeD?48:0});}updatePerspectiveUI();}
function updateThemeUI(){const t=current();document.documentElement.style.setProperty('--theme-accent',t.accent);$('stage-title').textContent=t.title;$('concept-kicker').textContent=t.kicker;$('concept-description').textContent=t.description;$('concept-features').innerHTML=t.features.map(x=>`<li>${x}</li>`).join('');document.querySelectorAll('button[data-theme]').forEach(b=>{const active=b.dataset.theme===theme;b.classList.toggle('selected',active);b.setAttribute('aria-pressed',String(active));});document.querySelectorAll('.nav-link').forEach(a=>{const u=new URL(a.href);u.searchParams.set('tema',theme);night?u.searchParams.set('noite','1'):u.searchParams.delete('noite');a.href=u.href;});$('day').setAttribute('aria-pressed',String(!night));$('night').setAttribute('aria-pressed',String(night));document.title=`${t.title} · ${page==='navigation'?'Navegação 3D':'Estudo do mapa'} · NavSkin`;}
let lastLoadFailed=false;
function status(text,error=false){if(text.startsWith('Preparando')||text.startsWith('Carregando'))lastLoadFailed=false;if(error)lastLoadFailed=true;if(!error&&text.startsWith('Pronto')&&lastLoadFailed)return;$('map-status').textContent=text;$('map-status').classList.toggle('error',error);$('map-status').hidden=!error&&text.startsWith('Pronto');$('ready-label').innerHTML=error?'Falha ao carregar':text.startsWith('Pronto')?'<i></i> Mapa local pronto':'<i class="loading-dot"></i> Preparando mapa';}
let toastTimer;
function toast(message){clearTimeout(toastTimer);$('toast').textContent=message;$('toast').hidden=false;toastTimer=setTimeout(()=>$('toast').hidden=true,3200);}
let themeChangeId=0;
async function changeTheme(next){const generation=++themeChangeId;theme=next;updateThemeUI();try{if(scene)scene.pause();await Promise.all(views.map((view,index)=>view.setTheme(next,{night,proposed:page==='compare'?index===1:version==='proposal'})));if(generation!==themeChangeId)return;const u=new URL(location.href);u.searchParams.set('tema',theme);night?u.searchParams.set('noite','1'):u.searchParams.delete('noite');history.replaceState(null,'',u);}catch(error){if(generation===themeChangeId)status(error.message,true);}}
function onFrame(s){if(!$('speed-value'))return;$('speed-value').textContent=Math.round(s.speed||0);$('progress').value=(s.progress||0)*100;$('progress-value').textContent=`${Math.round((s.progress||0)*100)}%`;$('remaining-distance').innerHTML=`${Math.max(0,Math.round(s.distanceRemaining||0))} <small>m</small>`;$('remaining-time').innerHTML=`${Math.max(0,Math.ceil(s.minutesRemaining||0))} <small>min</small>`;$('maneuver-distance').innerHTML=`${Math.max(0,Math.round((s.distanceRemaining||0)/10)*10)} <small>m</small>`;$('play').innerHTML=`${icon(s.playing?'pause':'play')}<span>${s.playing?'Pausar trajeto':s.progress>=1?'Repetir trajeto':'Reproduzir trajeto'}</span>`;$('play').setAttribute('aria-pressed',String(!!s.playing));}
updateThemeUI();
try {
  if(page==='compare')views.push(await createMapView($('map-original'),{theme,night,proposed:false,threeD,onStatus:status}));
  const main=await createMapView($('map'),{theme,night,proposed:true,threeD,onStatus:status,onStyle:async settings=>{if(sceneInitialized)await scene.install(settings.theme);}});
  views.push(main);
  if(page==='navigation')scene=new NavigationScene(main.map,{onFrame,onState:onFrame});
  await Promise.all(views.map(v=>v.ready));
  if(scene){scene.options.pitch=threeD?48:0;await scene.install(theme);sceneInitialized=true;scene.recenter();onFrame(scene.state);$('pitch').value=scene.options.pitch;$('pitch-value').textContent=`${scene.options.pitch}°`;}
  if(page==='compare'){
    let copying=false;
    views.forEach((v,i)=>v.map.on('move',()=>{if(!sync||copying)return;copying=true;views[1-i].map.jumpTo({center:v.map.getCenter(),zoom:v.map.getZoom(),bearing:v.map.getBearing(),pitch:v.map.getPitch()});copying=false;}));
  }
  status('Pronto · dados locais');
  window.navskinLab={views,scene,get theme(){return theme;},get page(){return page;},changeTheme};
}catch(error){console.error(error);status(error.message,true);}

document.querySelectorAll('button[data-theme]').forEach(button=>button.addEventListener('click',()=>changeTheme(button.dataset.theme)));
$('map-2d').onclick=()=>setPerspective(false);$('map-3d').onclick=()=>setPerspective(true);
$('day').onclick=()=>{night=false;changeTheme(theme);};$('night').onclick=()=>{night=true;changeTheme(theme);};
$('region')?.addEventListener('change',event=>views.forEach(v=>v.goTo(event.target.value)));
$('sync')?.addEventListener('change',event=>{sync=event.target.checked;if(sync&&views.length===2){const m=views[1].map;views[0].map.jumpTo({center:m.getCenter(),zoom:m.getZoom(),bearing:m.getBearing(),pitch:m.getPitch()});}});
$('reset-view').onclick=()=>scene?scene.recenter():views.forEach(v=>v.goTo($('region')?.value||'universidade'));
$('zoom-in').onclick=()=>views.at(-1)?.map.zoomIn();$('zoom-out').onclick=()=>views.at(-1)?.map.zoomOut();
document.querySelectorAll('[data-category]').forEach(input=>input.onchange=()=>views.forEach(v=>v.setCategory(input.dataset.category,input.checked)));
for(const choice of ['proposal','original'])if($(choice))$(choice).onclick=()=>{version=choice;for(const id of ['proposal','original'])$(id).setAttribute('aria-pressed',String(id===choice));$('map-caption').textContent=choice==='proposal'?'DESENHO PROPOSTO':'DESENHO RECURSOS DO APP';changeTheme(theme);};
if(scene){
  $('play').onclick=()=>scene.state.playing?scene.pause():scene.play();$('rewind').onclick=()=>scene.reset();$('recenter').onclick=()=>scene.recenter();
  $('progress').oninput=event=>scene.seek(Number(event.target.value)/100);
  $('pitch').oninput=event=>{const pitch=Number(event.target.value);threeD=pitch>0;savePerspective(perspectiveContext,threeD);views.forEach(view=>view.setThreeD(threeD,{moveCamera:false}));updatePerspectiveUI();$('pitch-value').textContent=`${pitch}°`;scene.setOptions({pitch});};
  $('opacity').oninput=event=>{$('opacity-value').textContent=`${Math.round((1-Number(event.target.value))*100)}%`;scene.setOptions({opacity:Number(event.target.value)});};
  $('height').oninput=event=>{$('height-value').textContent=`${Number(event.target.value).toFixed(1).replace('.',',')}×`;scene.setOptions({heightScale:Number(event.target.value)});};
  $('buildings').onchange=event=>scene.setOptions({buildings:event.target.checked});$('edges').onchange=event=>scene.setOptions({edges:event.target.checked});
  $('stop-trip').onclick=()=>{scene.pause();scene.reset();toast('Demonstração parada. Use Reproduzir para recomeçar.');};
  $('add-stop').onclick=()=>{if(stopMarker){stopMarker.remove();stopMarker=null;$('add-stop').innerHTML=`${icon('plus')}Adicionar parada`;toast('Parada de exemplo removida.');return;}stopMarker=new maplibre.Marker({color:'#ffce73'}).setLngLat(scene.state.point).addTo(views[0].map);$('add-stop').innerHTML=`${icon('close')}Remover parada`;toast('Parada de exemplo na posição atual. Este painel não calcula rotas.');};
  $('nav-menu').onclick=()=>{const open=$('nav-menu-panel').hidden;$('nav-menu-panel').hidden=!open;$('nav-menu').setAttribute('aria-expanded',String(open));};
  const closeMenu=()=>{$('nav-menu-panel').hidden=true;$('nav-menu').setAttribute('aria-expanded','false');};
  $('menu-flat').onclick=()=>{setPerspective(!threeD);closeMenu();};
  $('menu-hologram').onclick=()=>{$('buildings').checked=!$('buildings').checked;$('buildings').dispatchEvent(new Event('change'));closeMenu();};
  $('menu-focus').onclick=()=>{scene.recenter();closeMenu();};
  document.addEventListener('keydown',e=>{if(e.key==='Escape')closeMenu();});
}
$('credits-open').onclick=()=>$('credits-dialog').showModal();
$('credits-dialog').addEventListener('click',event=>{if(event.target===$('credits-dialog'))$('credits-dialog').close();});
window.addEventListener('pagehide',event=>{if(event.persisted){scene?.pause();return;}scene?.destroy();stopMarker?.remove();views.forEach(v=>v.destroy());});
