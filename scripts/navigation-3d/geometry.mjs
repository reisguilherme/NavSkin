/** Editable, dependency-free triangle geometry. Forward is +Y; Z is height. */
export const add = (a, b) => a.map((v, i) => v + b[i]);
export const sub = (a, b) => a.map((v, i) => v - b[i]);
export const mul = (a, n) => a.map(v => v * n);
export const dot = (a, b) => a.reduce((v, n, i) => v + n * b[i], 0);
export const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const unit = a => mul(a, 1 / (Math.hypot(...a) || 1));

export function mesh(label) {
  return { label, vertices: [], faces: [], parts: [] };
}

function vertex(model, point) {
  model.vertices.push(point);
  return model.vertices.length - 1;
}

function triangle(model, points, color, outward) {
  if (outward && dot(cross(sub(points[1], points[0]), sub(points[2], points[0])), outward) < 0) points = [points[0], points[2], points[1]];
  model.faces.push({ indices: points.map(point => vertex(model, point)), color });
}

function quad(model, points, color, outward) {
  triangle(model, [points[0], points[1], points[2]], color, outward);
  triangle(model, [points[0], points[2], points[3]], color, outward);
}

export function box(model, center, size, color, rotation = 0) {
  const cosine = Math.cos(rotation), sine = Math.sin(rotation);
  const point = ([x, y, z]) => add(center, [x * cosine - y * sine, x * sine + y * cosine, z]);
  const [x, y, z] = size.map(value => value / 2);
  const corners = [[-x, -y, -z], [x, -y, -z], [x, y, -z], [-x, y, -z], [-x, -y, z], [x, -y, z], [x, y, z], [-x, y, z]].map(point);
  for (const indices of [[0, 3, 2, 1], [4, 5, 6, 7], [0, 1, 5, 4], [3, 7, 6, 2], [0, 4, 7, 3], [1, 2, 6, 5]]) {
    const points = indices.map(i => corners[i]);
    const outward = sub(mul(points.reduce((a, b) => add(a, b), [0, 0, 0]), .25), center);
    quad(model, points, color, outward);
  }
}

export function ellipsoid(model, center, radius, color, segments = 14, rings = 9) {
  const at = (longitude, latitude) => add(center, [radius[0] * Math.sin(latitude) * Math.cos(longitude), radius[1] * Math.sin(latitude) * Math.sin(longitude), radius[2] * Math.cos(latitude)]);
  for (let ring = 0; ring < rings; ring++) for (let section = 0; section < segments; section++) {
    const a = section * 2 * Math.PI / segments, b = (section + 1) * 2 * Math.PI / segments;
    const c = ring * Math.PI / rings, d = (ring + 1) * Math.PI / rings;
    const points = [at(a, c), at(b, c), at(b, d), at(a, d)];
    const outward = sub(mul(points.reduce((sum, p) => add(sum, p), [0, 0, 0]), .25), center);
    quad(model, points, color, outward);
  }
}

export function cylinder(model, from, to, radius, color, segments = 10, endRadius = radius) {
  const axis = unit(sub(to, from));
  const side = unit(cross(axis, Math.abs(axis[2]) > .9 ? [0, 1, 0] : [0, 0, 1]));
  const up = cross(axis, side);
  const at = (center, angle, r) => add(center, add(mul(side, Math.cos(angle) * r), mul(up, Math.sin(angle) * r)));
  for (let i = 0; i < segments; i++) {
    const a = i * 2 * Math.PI / segments, b = (i + 1) * 2 * Math.PI / segments;
    const points = [at(from, a, radius), at(from, b, radius), at(to, b, endRadius), at(to, a, endRadius)];
    quad(model, points, color, sub(mul(add(points[0], points[1]), .5), from));
    triangle(model, [from, points[1], points[0]], color, mul(axis, -1));
    triangle(model, [to, points[3], points[2]], color, axis);
  }
}

export function ring(model, center, axis, radius, thickness, color, sections = 24, sides = 6) {
  const normal = unit(axis);
  const across = unit(cross(normal, Math.abs(normal[2]) > .9 ? [0, 1, 0] : [0, 0, 1]));
  const up = cross(normal, across);
  const at = (angle, slice) => {
    const radial = add(mul(across, Math.cos(angle)), mul(up, Math.sin(angle)));
    return add(center, add(mul(radial, radius + thickness * Math.cos(slice)), mul(normal, thickness * Math.sin(slice))));
  };
  for (let i = 0; i < sections; i++) for (let j = 0; j < sides; j++) {
    const a = i * 2 * Math.PI / sections, b = (i + 1) * 2 * Math.PI / sections;
    const c = j * 2 * Math.PI / sides, d = (j + 1) * 2 * Math.PI / sides;
    const points = [at(a, c), at(b, c), at(b, d), at(a, d)];
    const centerLine = add(center, mul(add(mul(across, Math.cos((a + b) / 2)), mul(up, Math.sin((a + b) / 2))), radius));
    const outward = sub(mul(points.reduce((sum, p) => add(sum, p), [0, 0, 0]), .25), centerLine);
    quad(model, points, color, outward);
  }
}

/** Closed tapered extrusion with independent lower/upper polygons. */
export function prism(model, bottom, top, color) {
  const center = mul([...bottom, ...top].reduce((sum, point) => add(sum, point), [0, 0, 0]), 1 / (bottom.length * 2));
  for (let i = 0; i < bottom.length; i++) {
    const next = (i + 1) % bottom.length;
    const points = [bottom[i], bottom[next], top[next], top[i]];
    const outward = sub(mul(points.reduce((sum, p) => add(sum, p), [0, 0, 0]), .25), center);
    quad(model, points, color, outward);
  }
  for (let i = 1; i < bottom.length - 1; i++) {
    triangle(model, [bottom[0], bottom[i + 1], bottom[i]], color, [0, 0, -1]);
    triangle(model, [top[0], top[i], top[i + 1]], color, [0, 0, 1]);
  }
}

export function polygonPlate(model, xy, base, height, color) {
  prism(model, xy.map(([x, y]) => [x, y, base]), xy.map(([x, y]) => [x, y, base + height]), color);
}

export function join(model, part, position = [0, 0, 0], angle = 0) {
  const base = model.vertices.length;
  const cosine = Math.cos(angle), sine = Math.sin(angle);
  model.vertices.push(...part.vertices.map(([x, y, z]) => add(position, [x * cosine - y * sine, x * sine + y * cosine, z])));
  model.faces.push(...part.faces.map(face => ({ indices: face.indices.map(index => index + base), color: face.color })));
}

export function toOBJ(model) {
  const palette = [...new Set(model.faces.map(face => face.color))];
  const lines = [`# ${model.label} — NavSkin authored navigation model`, 'mtllib model.mtl', 'o NavigationAvatar'];
  lines.push(...model.vertices.map(vertex => `v ${vertex.map(v => v.toFixed(5)).join(' ')}`));
  let previous;
  for (const face of model.faces) {
    if (previous !== face.color) { lines.push(`usemtl color${palette.indexOf(face.color)}`); previous = face.color; }
    lines.push(`f ${face.indices.map(index => index + 1).join(' ')}`);
  }
  const materials = palette.map((color, i) => `newmtl color${i}\nKd ${[1, 3, 5].map(index => (parseInt(color.slice(index, index + 2), 16) / 255).toFixed(4)).join(' ')}\nKa 0.25 0.25 0.25\nd 1\n`).join('\n');
  return { obj: `${lines.join('\n')}\n`, mtl: materials };
}
