import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const places = JSON.parse(await readFile(new URL('../public/data/places.geojson',import.meta.url),'utf8'));
const selectors = await import('../src/places.js').catch(()=>({}));
test('Flamboyant resolves the verified park rather than its homonymous district',()=>{
  assert.equal(typeof selectors.findPlace,'function','verified place selection required');
  assert.equal(selectors.findPlace(places,'Flamboyant').properties.osm_id,'way/220688361');
  assert.equal(selectors.findPlace(places,'Central').properties.name,'Centro');
});
