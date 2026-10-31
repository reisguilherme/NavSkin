import { MercatorCoordinate, Marker } from '/vendor/maplibre-gl.mjs';
import { PINK_ICON_CATALOG } from './pink-icons.js';
import { setBuildingVolumes } from './map-perspective.js';
import { avatarFrame, loadAvatarCatalog } from './navigation-avatar.js';

const PINK_VEHICLE = PINK_ICON_CATALOG.find(icon => icon.role === 'vehicle');

export const INITIAL_CAMERA = { center: [-49.241999, -16.6789193], zoom: 17, bearing: -17, pitch: 48 };
export const SCENE_COLORS = {
  gta: { body: '#4bd9cb', edge: '#a4fff0', route: '#36dfe6', halo: '#073c46' },
  rosa: { body: '#ff76c7', edge: '#fff0fb', route: '#ed1687', halo: '#fff4fb' },
  rdr2: { body: '#c2914e', edge: '#fff0bc', route: '#a73228', halo: '#f9e7b9' },
  smw: { body: '#77e9bd', edge: '#edffad', route: '#ffb91f', halo: '#4c642c' },
  cyberpunk: { body: '#21a2bd', edge: '#5cf6ed', route: '#fff16a', halo: '#063844' },
  pipboy: { body: '#558e4b', edge: '#c3ff9b', route: '#d4ff98', halo: '#102510' },
  hollow: { body: '#748c9e', edge: '#e2eee7', route: '#edddb2', halo: '#172b3c' },
  crossy: { body: '#bec89c', edge: '#fff7d3', route: '#f7b839', halo: '#334b37' },
};
const EMPTY = { type: 'FeatureCollection', features: [] };
const IDS = { buildings: 'navlab-buildings', body: 'navlab-hologram-body', rims: 'navlab-hologram-roofs', wire: 'navlab-hologram-wire',
  route: 'navlab-route', routeHalo: 'navlab-route-halo', routeLine: 'navlab-route-line',
  remaining: 'navlab-remaining', remainingLine: 'navlab-remaining-line', destination: 'navlab-destination', destinationDot: 'navlab-destination-dot' };
const clamp = (x, low, high) => Math.max(low, Math.min(high, x));
const radians = x => x * Math.PI / 180;
const degrees = x => x * 180 / Math.PI;
const wrappedAngle = angle => ((angle + 540) % 360) - 180;
const distance = (a, b) => {
  const dy = radians(b[1] - a[1]), dx = radians(b[0] - a[0]);
  const t = Math.sin(dy / 2) ** 2 + Math.cos(radians(a[1])) * Math.cos(radians(b[1])) * Math.sin(dx / 2) ** 2;
  return 6371008.8 * 2 * Math.atan2(Math.sqrt(t), Math.sqrt(1 - t));
};
const bearing = (a, b) => {
  const aLat = radians(a[1]), bLat = radians(b[1]), lon = radians(b[0] - a[0]);
  return degrees(Math.atan2(Math.sin(lon) * Math.cos(bLat), Math.cos(aLat) * Math.sin(bLat) - Math.sin(aLat) * Math.cos(bLat) * Math.cos(lon)));
};
const line = coordinates => ({ type: 'Feature', properties: { demonstration: true }, geometry: { type: 'LineString', coordinates } });
const rgb = hex => hex.slice(1).match(/../g).map(part => parseInt(part, 16) / 255);

let sceneData;
async function loadScene() {
  if (!sceneData) sceneData = Promise.all(['/data/scene-buildings.geojson', '/data/scene.json'].map(async url => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`Não foi possível abrir a cena 3D (${response.status}).`);
    return response.json();
  })).catch(error => { sceneData = null; throw error; });
  return sceneData;
}

/** Static GPU lines follow each actual roof, corner and middle-floor footprint. */
function createWireLayer(features, colors, options) {
  const anchor = MercatorCoordinate.fromLngLat(INITIAL_CAMERA.center);
  const vertices = [];
  const append = (a, az, b, bz) => {
    const ma = MercatorCoordinate.fromLngLat(a, az), mb = MercatorCoordinate.fromLngLat(b, bz);
    vertices.push(ma.x - anchor.x, ma.y - anchor.y, ma.z, mb.x - anchor.x, mb.y - anchor.y, mb.z);
  };
  for (const feature of features) {
    const h = feature.properties.lab_height;
    const polygons = feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates;
    for (const polygon of polygons) for (const ring of polygon) {
      for (let i = 0; i < ring.length - 1; i++) {
        append(ring[i], h, ring[i + 1], h);
        append(ring[i], 0, ring[i], h);
        if (h >= 14) append(ring[i], h * .5, ring[i + 1], h * .5);
      }
    }
  }
  const data = new Float32Array(vertices);
  const layer = {
    id: IDS.wire, type: 'custom', renderingMode: '3d', visible: true,
    onAdd(map, gl) {
      this.map = map;
      const compile = (type, source) => {
        const shader = gl.createShader(type);
        gl.shaderSource(shader, source); gl.compileShader(shader);
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
        return shader;
      };
      const vert = compile(gl.VERTEX_SHADER, `#version 300 es
        in vec3 a_pos; uniform mat4 u_matrix; uniform float u_scale;
        void main(){ gl_Position = u_matrix * vec4(a_pos.xy, a_pos.z * u_scale, 1.0); }`);
      const frag = compile(gl.FRAGMENT_SHADER, `#version 300 es
        precision mediump float; uniform vec4 u_color; out vec4 fragColor;
        void main(){ fragColor = u_color; }`);
      this.program = gl.createProgram(); gl.attachShader(this.program, vert); gl.attachShader(this.program, frag); gl.linkProgram(this.program);
      if (!gl.getProgramParameter(this.program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(this.program));
      gl.deleteShader(vert); gl.deleteShader(frag);
      this.buffer = gl.createBuffer(); this.vao = gl.createVertexArray();
      gl.bindVertexArray(this.vao); gl.bindBuffer(gl.ARRAY_BUFFER, this.buffer); gl.bufferData(gl.ARRAY_BUFFER, data, gl.STATIC_DRAW);
      const position = gl.getAttribLocation(this.program, 'a_pos');
      gl.enableVertexAttribArray(position); gl.vertexAttribPointer(position, 3, gl.FLOAT, false, 0, 0); gl.bindVertexArray(null);
      this.matrixLocation = gl.getUniformLocation(this.program, 'u_matrix'); this.scaleLocation = gl.getUniformLocation(this.program, 'u_scale'); this.colorLocation = gl.getUniformLocation(this.program, 'u_color');
    },
    render(gl, args) {
      if (!this.visible || !this.program || !data.length) return;
      // CPU anchor translation avoids Float32 precision loss at street zoom.
      const original = args.defaultProjectionData.mainMatrix;
      const matrix = new Float32Array(original);
      for (let row = 0; row < 4; row++) matrix[12 + row] = original[12 + row] + original[row] * anchor.x + original[4 + row] * anchor.y;
      gl.useProgram(this.program); gl.bindVertexArray(this.vao);
      gl.uniformMatrix4fv(this.matrixLocation, false, matrix);
      gl.uniform1f(this.scaleLocation, options.heightScale);
      gl.uniform4f(this.colorLocation, ...rgb(colors.edge), .56);
      gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
      gl.disable(gl.DEPTH_TEST); gl.depthMask(false); gl.lineWidth(1);
      gl.drawArrays(gl.LINES, 0, data.length / 3); gl.bindVertexArray(null);
    },
    onRemove(map, gl) {
      if (this.buffer) gl.deleteBuffer(this.buffer);
      if (this.vao) gl.deleteVertexArray(this.vao);
      if (this.program) gl.deleteProgram(this.program);
      this.buffer = this.vao = this.program = null;
    },
  };
  return layer;
}

/** Interactive scene only: no GPS permission, API requests, routing or app state. */
export class NavigationScene {
  constructor(map, { onFrame = () => {}, onState = () => {} } = {}) {
    this.map = map; this.onFrame = onFrame; this.onState = onState;
    this.options = { pitch: 48, opacity: .96, heightScale: 1, edges: false, buildings: true };
    this._theme = 'cyberpunk'; this._playing = false; this._follow = true; this._distance = 0; this._heading = -17;
    this._route = []; this._lengths = [0]; this._total = 1; this._generation = 0; this._destroyed = false; this._last = 0; this._lastFrame = 0;
    this._gesture = event => {
      if (!event.originalEvent) return;
      this._follow = false; this.onState(this.state);
    };
    for (const event of ['dragstart', 'rotatestart', 'zoomstart', 'pitchstart']) map.on(event, this._gesture);
    this._cameraChanged = () => this._updateAvatar();
    map.on('move', this._cameraChanged);
    this._visibility = () => { if (document.hidden) this.pause(); };
    document.addEventListener('visibilitychange', this._visibility);
    this._tick = this._tick.bind(this);
  }

  async install(theme = this._theme) {
    const generation = ++this._generation;
    const [[buildings, metadata], avatarCatalog] = await Promise.all([loadScene(), loadAvatarCatalog().catch(() => null)]);
    if (this._destroyed || generation !== this._generation) return;
    this.metadata = metadata; this._theme = SCENE_COLORS[theme] ? theme : 'cyberpunk';
    this._avatarCatalog = avatarCatalog;
    this._route = metadata.route.coordinates; this._lengths = [0];
    for (let i = 1; i < this._route.length; i++) this._lengths.push(this._lengths.at(-1) + distance(this._route[i - 1], this._route[i]));
    this._total = this._lengths.at(-1); this._distance = clamp(this._distance, 0, this._total);
    this._removeLayers();
    const map = this.map, colors = SCENE_COLORS[this._theme];
    const before = map.getStyle().layers.find(layer => layer.type === 'symbol')?.id;
    map.addSource(IDS.buildings, { type: 'geojson', data: buildings, tolerance: 0 });
    // The regular building layers now provide volumes throughout the map.
    // Optional wire art uses the original local route study without duplicating
    // another set of filled buildings over it.
    this._wire = createWireLayer(buildings.features, colors, this.options);
    map.addLayer(this._wire, before);
    map.addSource(IDS.route, { type: 'geojson', data: line(this._route) });
    map.addSource(IDS.remaining, { type: 'geojson', data: line(this._route) });
    map.addLayer({ id: IDS.routeHalo, type: 'line', source: IDS.route, layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': colors.halo, 'line-width': 12, 'line-opacity': .8, 'line-blur': 2 } }, before);
    map.addLayer({ id: IDS.routeLine, type: 'line', source: IDS.route, layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': colors.route, 'line-width': 5.5, 'line-opacity': .26 } }, before);
    map.addLayer({ id: IDS.remainingLine, type: 'line', source: IDS.remaining, layout: { 'line-cap': 'round', 'line-join': 'round' },
      paint: { 'line-color': colors.route, 'line-width': 5.5, 'line-opacity': .98 } }, before);
    map.addSource(IDS.destination, { type: 'geojson', data: { type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: this._route.at(-1) } } });
    map.addLayer({ id: IDS.destinationDot, type: 'circle', source: IDS.destination, paint: {
      'circle-radius': 6, 'circle-color': colors.route, 'circle-stroke-color': colors.halo, 'circle-stroke-width': 3 } });
    if (!this._marker) {
      this._markerElement = document.createElement('div');
      Object.assign(this._markerElement.style, { width: '42px', height: '42px', display: 'grid', placeItems: 'center', pointerEvents: 'none' });
      this._markerImage = document.createElement('img');
      Object.assign(this._markerImage.style, { width: '36px', height: '36px', objectFit: 'contain' });
      this._markerImage.alt = 'Veículo da demonstração';
      this._markerElement.append(this._markerImage);
      this._marker = new Marker({ element: this._markerElement, rotationAlignment: 'map', pitchAlignment: 'map' }).setLngLat(this._route[0]).addTo(map);
    }
    this._avatarKey = null;
    this._updateAvatar();
    this.setOptions(this.options, false); this._renderFrame(); this.onState(this.state);
  }

  _updateAvatar() {
    if (!this._marker || this._destroyed) return;
    const frame = avatarFrame(this._avatarCatalog, this._theme, this._heading, this.map.getBearing(), this.map.getPitch());
    if (frame) {
      if (this._avatarKey === frame.path) return;
      this._avatarKey = frame.path;
      const size = this._avatarCatalog.displaySize;
      this._markerImage.src = frame.src;
      this._markerImage.alt = frame.label;
      this._markerImage.style.filter = 'drop-shadow(0 1px 1px #0005)';
      for (const element of [this._markerImage, this._markerElement]) Object.assign(element.style, { width: `${size}px`, height: `${size}px` });
      this._marker.setRotationAlignment('viewport').setPitchAlignment('viewport').setRotation(0);
      this._marker.setOffset([(0.5 - this._avatarCatalog.anchor[0]) * size, (0.5 - this._avatarCatalog.anchor[1]) * size]);
      return;
    }
    const pink = this._theme === 'rosa', key = `flat-${this._theme}`;
    if (this._avatarKey !== key) {
      this._avatarKey = key;
      this._markerImage.src = pink ? PINK_VEHICLE.src : `/assets/navigation-icons/${this._theme}/vehicles/arrow${this._theme === 'gta' ? '-original' : ''}.png`;
      this._markerImage.alt = pink ? 'Carrinho Hello Kitty visto de cima' : 'Veículo da demonstração';
      for (const element of [this._markerImage, this._markerElement]) Object.assign(element.style, { width: pink ? '56px' : '36px', height: pink ? '56px' : '36px' });
      this._marker.setRotationAlignment('map').setPitchAlignment('map').setOffset([0,0]);
    }
    this._marker.setRotation(this._heading);
  }

  get state() {
    const sample = this._sample(this._distance);
    return { playing: this._playing, progress: this._distance / this._total, speed: this._playing ? this._speed() : 0,
      distanceRemaining: Math.max(0, this._total - this._distance), minutesRemaining: Math.max(0, Math.ceil((this._total - this._distance) / 6.4 / 60)),
      heading: this._heading, point: sample.point, follow: this._follow, metadata: this.metadata,
      theme: this._theme, totalDistance: this._total, options: { ...this.options } };
  }

  setOptions(next, moveCamera = true) {
    if (Number.isFinite(Number(next.pitch))) this.options.pitch = clamp(Number(next.pitch), 0, 65);
    if (Number.isFinite(Number(next.opacity))) this.options.opacity = clamp(Number(next.opacity), 0, 1);
    if (Number.isFinite(Number(next.heightScale))) this.options.heightScale = clamp(Number(next.heightScale), .2, 2.5);
    if (next.edges !== undefined) this.options.edges = Boolean(next.edges);
    if (next.buildings !== undefined) this.options.buildings = Boolean(next.buildings);
    const map = this.map;
    setBuildingVolumes(map, this.options.buildings && this.options.pitch > 0, { opacity: this.options.opacity, heightScale: this.options.heightScale });
    if (this._wire) this._wire.visible = this.options.edges && this.options.buildings && this.options.pitch > 0;
    if (moveCamera) map.easeTo({ pitch: this.options.pitch, duration: 240 });
    map.triggerRepaint(); this.onState(this.state);
  }

  _sample(meters) {
    if (!this._route.length) return { point: [...INITIAL_CAMERA.center], index: 0 };
    let index = 1;
    while (index < this._lengths.length - 1 && this._lengths[index] < meters) index++;
    const start = this._route[index - 1], end = this._route[index];
    const fraction = clamp((meters - this._lengths[index - 1]) / Math.max(.001, this._lengths[index] - this._lengths[index - 1]), 0, 1);
    return { point: [start[0] + (end[0] - start[0]) * fraction, start[1] + (end[1] - start[1]) * fraction], index };
  }

  _speed() {
    const here = this._sample(this._distance).point, ahead = this._sample(this._distance + 15).point, further = this._sample(this._distance + 40).point;
    const curve = Math.abs(wrappedAngle(bearing(ahead, further) - bearing(here, ahead)));
    return Math.round(clamp(31 - curve * .15, 14, 31));
  }

  _renderFrame() {
    if (!this._route.length || this._destroyed) return;
    const sample = this._sample(this._distance);
    const ahead = this._sample(Math.min(this._total, this._distance + 18)).point;
    const target = distance(sample.point, ahead) > .1 ? bearing(sample.point, ahead) : this._heading;
    this._heading += wrappedAngle(target - this._heading) * .18;
    this._marker?.setLngLat(sample.point);
    this._updateAvatar();
    this.map.getSource(IDS.remaining)?.setData(line([sample.point, ...this._route.slice(sample.index)]));
    if (this._playing && this._follow && performance.now() > (this._cameraGraceUntil || 0)) {
      const center = this._sample(Math.min(this._total, this._distance + 38)).point;
      this.map.jumpTo({ center, bearing: this._heading, pitch: this.options.pitch });
    }
    this.onFrame(this.state);
  }

  _tick(now) {
    if (!this._playing || this._destroyed) return;
    this._raf = requestAnimationFrame(this._tick);
    if (now - this._lastFrame < 1000 / 24) return;
    const elapsed = this._last ? Math.min((now - this._last) / 1000, .15) : 0;
    this._last = now; this._lastFrame = now;
    this._distance = Math.min(this._total, this._distance + this._speed() / 3.6 * elapsed);
    this._renderFrame();
    if (this._distance >= this._total) this.pause();
  }

  play() {
    if (this._destroyed || !this._route.length || this._playing) return;
    if (this._distance >= this._total) this._distance = 0;
    this._playing = true; this._last = this._lastFrame = 0; this._raf = requestAnimationFrame(this._tick); this.onState(this.state);
  }

  pause() { this._playing = false; cancelAnimationFrame(this._raf); this._last = 0; this.onState(this.state); this.onFrame(this.state); }
  reset() { this.pause(); this._distance = 0; this._heading = -17; this._renderFrame(); this.recenter(); }
  seek(fraction) { this._distance = clamp(Number(fraction) || 0, 0, 1) * this._total; this._renderFrame(); if (this._follow) this.recenter(); this.onState(this.state); }
  recenter() {
    this._follow = true; this._cameraGraceUntil = performance.now() + 650;
    this.map.easeTo({ center: this._sample(this._distance + 38).point, zoom: 17, bearing: this._heading, pitch: this.options.pitch, duration: 600 });
    this.onState(this.state);
  }

  _removeLayers() {
    for (const id of [IDS.destinationDot, IDS.remainingLine, IDS.routeLine, IDS.routeHalo, IDS.wire, IDS.rims, IDS.body]) if (this.map.getLayer(id)) this.map.removeLayer(id);
    for (const id of [IDS.destination, IDS.remaining, IDS.route, IDS.buildings]) if (this.map.getSource(id)) this.map.removeSource(id);
  }

  destroy() {
    if (this._destroyed) return;
    this.pause(); this._destroyed = true; this._generation++;
    for (const event of ['dragstart', 'rotatestart', 'zoomstart', 'pitchstart']) this.map.off(event, this._gesture);
    this.map.off('move', this._cameraChanged);
    document.removeEventListener('visibilitychange', this._visibility);
    this._marker?.remove(); this._removeLayers(); this._wire = null;
  }
}
