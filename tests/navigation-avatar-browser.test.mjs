import test from 'node:test';
import assert from 'node:assert/strict';
import { avatarFrame } from '../prototypes/navskin-map-lab/navigation-avatar.js';

const catalog = { frameCount: 32, pitches: [0,40,55], themes: { gta: { label:'CJ', frames: [0,40,55].map(p => Array.from({length:32}, (_,i) => `gta/p${p}/a${i}.png`)) } } };

test('avatar remains rear-facing when camera follows its heading and wraps north without a jump', () => {
  assert.equal(avatarFrame(catalog,'gta',181,181,48).yawIndex,0);
  assert.equal(avatarFrame(catalog,'gta',359,0,48).yawIndex,0);
  assert.equal(avatarFrame(catalog,'gta',0,359,48).yawIndex,0);
  assert.equal(avatarFrame(catalog,'gta',90,0,48).yawIndex,8);
  assert.equal(avatarFrame(catalog,'gta',0,90,48).yawIndex,24);
});

test('top view and tilted camera use distinct mesh views; bad pose falls back', () => {
  assert.equal(avatarFrame(catalog,'gta',0,0,0).pitchIndex,0);
  assert.equal(avatarFrame(catalog,'gta',0,0,40).pitchIndex,1);
  assert.equal(avatarFrame(catalog,'gta',0,0,48).pitchIndex,2);
  assert.equal(avatarFrame(catalog,'gta',0,0,65).pitchIndex,2);
  assert.equal(avatarFrame(catalog,'missing',0,0,48),null);
  assert.equal(avatarFrame(catalog,'gta',NaN,0,48),null);
});
