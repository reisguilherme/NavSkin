import test from 'node:test';
import assert from 'node:assert/strict';
import { buildStyle } from '../src/style.js';
import { resolveStyleAssets } from '../src/style-assets.js';
test('inline styles resolve their sprite against the local HTTP origin',()=>{
  const style=buildStyle('gta');
  const resolved=resolveStyleAssets(style,'http://127.0.0.1:4173/');
  assert.equal(resolved.sprite,'http://127.0.0.1:4173/sprites/poi');
  assert.equal(style.sprite,'/sprites/poi');
  assert.deepEqual(resolved.sources,style.sources);
});
