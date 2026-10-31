import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import sharp from 'sharp';
import { models } from '../scripts/navigation-3d/models.mjs';
import { projectModel, renderModelSVG, renderModelPixels } from '../scripts/navigation-3d/renderer.mjs';

const themes = ['gta', 'rosa', 'rdr2', 'smw', 'cyberpunk', 'pipboy', 'hollow', 'crossy'];
const root = new URL('../android/app/src/main/assets/navigation-3d/', import.meta.url);

test('all eight navigation avatars are real, finite triangle meshes with distinct geometry', () => {
  assert.deepEqual(Object.keys(models), themes);
  const geometries = new Set();
  for (const model of Object.values(models)) {
    assert.ok(model.vertices.length > 100, model.label);
    assert.ok(model.faces.length > 100, model.label);
    for (const vertex of model.vertices) assert.ok(vertex.length === 3 && vertex.every(Number.isFinite));
    for (const face of model.faces) {
      assert.equal(face.indices.length, 3);
      assert.match(face.color, /^#[0-9a-f]{6}$/i);
      for (const index of face.indices) assert.ok(Number.isInteger(index) && index >= 0 && index < model.vertices.length);
    }
    geometries.add(JSON.stringify(model.vertices));
  }
  assert.equal(geometries.size, themes.length);
});

test('baked viewpoints preserve the ground anchor and keep each avatar inside the transparent canvas', () => {
  for (const [theme, model] of Object.entries(models)) {
    for (const pitch of [0, 40, 55]) for (let yaw = 0; yaw < 360; yaw += 11.25) {
      const projection = projectModel(model, { yaw, pitch, size: 192 });
      assert.equal(projection.anchor[0], 96);
      assert.ok(Math.abs(projection.anchor[1] - 134.4) < 1e-8);
      for (const point of projection.vertices) {
        assert.ok(point[0] >= 5 && point[0] <= 187 && point[1] >= 5 && point[1] <= 187, `${theme} ${yaw}/${pitch}: ${point}`);
      }
    }
  }
});

test('depth buffer resolves intersecting faces independently of draw order', () => {
  const vertices = [[-1, -1, 1], [1, -1, 1], [0, 1, 1], [-1, -1, 0], [1, -1, 0], [0, 1, 0]];
  const front = { indices: [0, 1, 2], color: '#ff0000' };
  const back = { indices: [3, 4, 5], color: '#0000ff' };
  const first = renderModelPixels({ vertices, faces: [front, back], scale: 30 }, { pitch: 0 });
  const reverse = renderModelPixels({ vertices, faces: [back, front], scale: 30 }, { pitch: 0 });
  assert.deepEqual(first.data, reverse.data);
  const center = (Math.floor(192 * .7 * 2) * first.width + 192) * 4;
  assert.ok(first.data[center] > 150);
  assert.equal(first.data[center + 2], 0);
  assert.equal(first.data[center + 3], 255);
});

test('yaw changes the actual 3D view and 90 degrees points the forward axis right', () => {
  const a = renderModelSVG(models.rosa, { yaw: 0, pitch: 55, size: 192 });
  const b = renderModelSVG(models.rosa, { yaw: 90, pitch: 55, size: 192 });
  assert.notEqual(a, b);
  const model = { vertices: [[0, 0, 0], [0, 1, 0]], faces: [], scale: 30 };
  const view = projectModel(model, { yaw: 90, pitch: 55, size: 192 });
  assert.ok(view.vertices[1][0] > view.vertices[0][0]);
  assert.ok(Math.abs(view.vertices[1][1] - view.vertices[0][1]) < 1e-8);
});

test('runtime catalog contains every angle and pitch with transparent PNGs', async () => {
  const catalog = JSON.parse(await readFile(new URL('catalog.json', root), 'utf8'));
  assert.equal(catalog.frameCount, 32);
  assert.deepEqual(catalog.pitches, [0, 40, 55]);
  assert.deepEqual(catalog.anchor, [0.5, 0.7]);
  assert.equal(catalog.displaySize, 72);
  assert.deepEqual(Object.keys(catalog.themes), themes);
  for (const [theme, data] of Object.entries(catalog.themes)) {
    assert.equal(data.frames.length, 3);
    const hashes = new Set();
    for (const frames of data.frames) {
      assert.equal(frames.length, 32);
      for (const path of frames) {
        assert.ok(path.startsWith(`${theme}/`));
        const bytes = await readFile(new URL(path, root));
        const image = sharp(bytes);
        const metadata = await image.metadata();
        assert.equal(metadata.width, 192);
        assert.equal(metadata.height, 192);
        assert.ok(metadata.hasAlpha);
        const { data: rgba } = await image.raw().toBuffer({ resolveWithObject: true });
        assert.equal(rgba[3], 0);
        assert.equal(rgba[(192 * 192 - 1) * 4 + 3], 0);
        hashes.add(bytes.toString('base64'));
      }
    }
    assert.ok(hashes.size >= 80, `${theme} must have genuinely different rendered viewpoints`);
  }
});
