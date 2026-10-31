import { cross, dot, sub, unit } from './geometry.mjs';

const fitCache = new WeakMap();
const DEG = Math.PI / 180;

function rotate([x, y, z], yaw) {
  const cosine = Math.cos(yaw * DEG), sine = Math.sin(yaw * DEG);
  return [x * cosine + y * sine, -x * sine + y * cosine, z];
}

function coordinates(point, yaw, pitch) {
  const [x, y, z] = rotate(point, yaw), p = pitch * DEG;
  return [x, -(y * Math.cos(p) + z * Math.sin(p)), y * Math.sin(p) - z * Math.cos(p)];
}

function fitScale(model, pitch) {
  if (model.scale) return model.scale;
  if (fitCache.get(model)?.has(pitch)) return fitCache.get(model).get(pitch);
  const bounds = { left: 0, right: 0, top: 0, bottom: 0 };
  for (let yaw = 0; yaw < 360; yaw += 11.25) for (const point of model.vertices) {
    const [x, y] = coordinates(point, yaw, pitch);
    bounds.left = Math.max(bounds.left, -x); bounds.right = Math.max(bounds.right, x);
    bounds.top = Math.max(bounds.top, -y); bounds.bottom = Math.max(bounds.bottom, y);
  }
  const scale = Math.min(86 / bounds.left, 86 / bounds.right, 124.4 / bounds.top, 47.6 / bounds.bottom);
  if (!fitCache.has(model)) fitCache.set(model, new Map());
  fitCache.get(model).set(pitch, scale);
  return scale;
}

export function projectModel(model, { yaw = 0, pitch = 55, size = 192 } = {}) {
  const scale = fitScale(model, pitch) * size / 192;
  const anchor = [size * .5, size * .7];
  return { anchor, scale, vertices: model.vertices.map(point => {
    const [x, y, depth] = coordinates(point, yaw, pitch);
    return [anchor[0] + x * scale, anchor[1] + y * scale, depth];
  }) };
}

function colorLight(hex, amount) {
  return `#${[1, 3, 5].map(i => Math.max(0, Math.min(255, Math.round(parseInt(hex.slice(i, i + 2), 16) * amount))).toString(16).padStart(2, '0')).join('')}`;
}

/** Orthographic mesh render, baked into transparent sprites for fast MapLibre billboards. */
export function renderModelSVG(model, options = {}) {
  const { yaw = 0, pitch = 55, size = 192, shadow = true } = options;
  const { vertices, anchor, scale } = projectModel(model, { yaw, pitch, size });
  const eye = [0, -Math.sin(pitch * DEG), Math.cos(pitch * DEG)];
  const light = unit([-0.55, -0.6, 1.3]);
  const faces = model.faces.map(face => {
    const original = face.indices.map(index => model.vertices[index]);
    const normal = rotate(unit(cross(sub(original[1], original[0]), sub(original[2], original[0]))), yaw);
    const points = face.indices.map(index => vertices[index]);
    return { points, normal, color: face.color, depth: points.reduce((sum, point) => sum + point[2], 0) / 3 };
  }).filter(face => dot(face.normal, eye) > .001).sort((a, b) => b.depth - a.depth);
  const paths = faces.map(face => {
    const color = colorLight(face.color, .70 + .3 * Math.max(0, dot(face.normal, light)));
    const path = `M${face.points.map(point => `${point[0].toFixed(2)},${point[1].toFixed(2)}`).join('L')}Z`;
    return `<path d="${path}" fill="${color}" stroke="${color}" stroke-width=".4" stroke-linejoin="round"/>`;
  }).join('');
  const ground = shadow ? `<defs><radialGradient id="shadow"><stop offset="0" stop-color="#07101b" stop-opacity=".34"/><stop offset="1" stop-color="#07101b" stop-opacity="0"/></radialGradient></defs><ellipse cx="${anchor[0]}" cy="${anchor[1] + 1}" rx="${Math.min(60, scale * .95)}" ry="${Math.max(12, scale * .42)}" fill="url(#shadow)"/>` : '';
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}"><title>${model.label}</title>${ground}${paths}</svg>`;
}

/** Depth-buffered flat-light rasterizer. Correctly occludes intersecting model parts. */
export function renderModelPixels(model, { yaw = 0, pitch = 55, size = 192, supersample = 2 } = {}) {
  const width = size * supersample, height = width;
  const data = new Uint8ClampedArray(width * height * 4);
  const depth = new Float32Array(width * height).fill(Infinity);
  const { vertices, anchor, scale } = projectModel(model, { yaw, pitch, size: width });
  const eye = [0, -Math.sin(pitch * DEG), Math.cos(pitch * DEG)];
  const light = unit([-0.55, -0.6, 1.3]);
  const shadowX = Math.min(60 * supersample, scale * .95), shadowY = Math.max(12 * supersample, scale * .42);
  for (let y = Math.max(0, Math.floor(anchor[1] - shadowY)); y < Math.min(height, anchor[1] + shadowY); y++) for (let x = Math.max(0, Math.floor(anchor[0] - shadowX)); x < Math.min(width, anchor[0] + shadowX); x++) {
    const radius = Math.hypot((x - anchor[0]) / shadowX, (y - anchor[1] - supersample) / shadowY);
    if (radius < 1) {
      const i = (y * width + x) * 4;
      data[i] = 7; data[i + 1] = 16; data[i + 2] = 27; data[i + 3] = Math.round(80 * (1 - radius) ** 1.6);
    }
  }
  for (const face of model.faces) {
    const original = face.indices.map(index => model.vertices[index]);
    const normal = rotate(unit(cross(sub(original[1], original[0]), sub(original[2], original[0]))), yaw);
    if (dot(normal, eye) <= .001) continue;
    const [a, b, c] = face.indices.map(index => vertices[index]);
    const denominator = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1]);
    if (Math.abs(denominator) < 1e-8) continue;
    const minX = Math.max(0, Math.floor(Math.min(a[0], b[0], c[0]))), maxX = Math.min(width - 1, Math.ceil(Math.max(a[0], b[0], c[0])));
    const minY = Math.max(0, Math.floor(Math.min(a[1], b[1], c[1]))), maxY = Math.min(height - 1, Math.ceil(Math.max(a[1], b[1], c[1])));
    const amount = .70 + .3 * Math.max(0, dot(normal, light));
    const rgb = [1, 3, 5].map(i => Math.round(parseInt(face.color.slice(i, i + 2), 16) * amount));
    for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
      const u = ((b[1] - c[1]) * (x + .5 - c[0]) + (c[0] - b[0]) * (y + .5 - c[1])) / denominator;
      const v = ((c[1] - a[1]) * (x + .5 - c[0]) + (a[0] - c[0]) * (y + .5 - c[1])) / denominator;
      const w = 1 - u - v;
      if (u < -1e-6 || v < -1e-6 || w < -1e-6) continue;
      const z = u * a[2] + v * b[2] + w * c[2];
      const pixel = y * width + x;
      if (z >= depth[pixel]) continue;
      depth[pixel] = z;
      const index = pixel * 4;
      data[index] = rgb[0]; data[index + 1] = rgb[1]; data[index + 2] = rgb[2]; data[index + 3] = 255;
    }
  }
  return { data, width, height };
}
