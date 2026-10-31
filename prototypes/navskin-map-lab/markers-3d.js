import { avatarFrame, loadAvatarCatalog } from './navigation-avatar.js';

const names = { gta:'San Andreas',rosa:'Pink World',crossy:'Crossy Road',rdr2:'Red Dead Redemption',smw:'Super Mario World',cyberpunk:'Cyberpunk',pipboy:'Pip-Boy',hollow:'Hollow Knight' };
const colors = {gta:['#45543d','#5b775359'],rosa:['#5a344a','#f198c043'],crossy:['#3f5341','#bcd65f32'],rdr2:['#53402e','#bd935333'],smw:['#2c5346','#8dda7940'],cyberpunk:['#324455','#22d9e839'],pipboy:['#254a32','#71e34e35'],hollow:['#323c50','#a4c5e933']};
const yaw = document.querySelector('#yaw'), yawValue = document.querySelector('#yaw-value'), orbit = document.querySelector('#orbit');
let pitch = 55, catalog, spinning = false, animation, lastTime, orbitAngle = 0;
function update() {
  yawValue.textContent = `${Math.round(yaw.value)}°`;
  if (!catalog) return;
  document.querySelectorAll('[data-avatar]').forEach(image => {
    const frame = avatarFrame(catalog,image.dataset.avatar,0,Number(yaw.value),pitch);
    if (image.dataset.frame !== frame.path) { image.dataset.frame = frame.path; image.src = frame.src; }
  });
}
yaw.addEventListener('input',() => { orbitAngle = Number(yaw.value); update(); });
for (const button of document.querySelectorAll('[data-pitch]')) button.addEventListener('click',() => {
  pitch = Number(button.dataset.pitch);
  document.querySelectorAll('[data-pitch]').forEach(other => other.setAttribute('aria-pressed',String(other === button)));
  update();
});
function tick(time) {
  if (!spinning) return;
  if (lastTime) { orbitAngle = (orbitAngle + Math.min(50,time-lastTime) * .025) % 360; yaw.value = orbitAngle; }
  lastTime = time; update(); animation = requestAnimationFrame(tick);
}
function stop() { spinning = false; cancelAnimationFrame(animation); orbit.setAttribute('aria-pressed','false'); orbit.textContent = 'Girar automaticamente'; }
orbit.addEventListener('click',() => {
  if (spinning) return stop();
  spinning = true; lastTime = null; orbitAngle = Number(yaw.value); orbit.setAttribute('aria-pressed','true'); orbit.textContent = 'Pausar rotação'; animation = requestAnimationFrame(tick);
});
document.addEventListener('visibilitychange',() => { if (document.hidden) stop(); });
try {
  catalog = await loadAvatarCatalog();
  for (const [index,theme] of Object.keys(names).entries()) {
    const item = catalog.themes[theme], card = document.createElement('article');
    card.className = 'avatar-card'; card.style.setProperty('--ground',colors[theme][0]); card.style.setProperty('--glow',colors[theme][1]);
    const label = item.label.replace(/[&<>"']/g,c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    card.innerHTML = `<div class="scene"><span class="number">0${index+1}</span><span class="tag">NAVEGAÇÃO + VIBING</span><img class="avatar-large" data-avatar="${theme}" alt="${label}" width="192" height="192"></div><div class="card-info"><div><h2>${label}</h2><p>${names[theme]}</p></div><div class="actual"><img data-avatar="${theme}" alt="" width="72" height="72"><span>NO APP</span></div></div><a href="./navegacao.html?tema=${theme}">VER NO MAPA ↗</a>`;
    document.querySelector('#collection').append(card);
  }
  document.querySelector('#status').hidden = true; update();
  window.navskinAvatars = {catalog,update,get pitch(){return pitch;}};
} catch(error) { document.querySelector('#status').textContent = `Não foi possível carregar os marcadores: ${error.message}. Execute npm run assets:avatars e abra este painel pelo servidor local.`; }
