import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import sharp from 'sharp';
import {createExpression, featureFilter} from '@maplibre/maplibre-gl-style-spec';
import {buildWorldStyle, roadDirectionLayer} from '../scripts/prepare-world-maps.mjs';

const source = new URL('../android/maps/icons/smw/road-oneway-forward.svg', import.meta.url);
const raster = async () => sharp(await readFile(source)).resize(32,32).ensureAlpha().raw().toBuffer();
const alpha = (pixels,x,y,width=32) => pixels[(y*width+x)*4+3];

test('SMW traffic arrow points east for line placement, rather than using the north-facing vehicle orientation', async () => {
  const pixels=await raster();
  assert.ok(alpha(pixels,27,15)>128, 'the arrow tip must occupy the east (+X) side');
  assert.equal(alpha(pixels,15,4),0, 'there must not be a north-facing tip');
});

test('SMW vehicle artwork remains north-facing for GPS heading rotation', async () => {
  const vehicle=new URL('../android/maps/catalogs/smw/vehicles/arrow.svg',import.meta.url);
  const pixels=await sharp(await readFile(vehicle)).resize(32,32).ensureAlpha().raw().toBuffer();
  assert.ok(alpha(pixels,15,4)>128,'vehicle tip remains north');
  assert.equal(alpha(pixels,27,15),0,'road correction must not rotate the vehicle');
});

test('SMW forward and reverse restrictions retain their line rotation and never flip with the camera', () => {
  for(const layer of [buildWorldStyle('smw').layers.find(l=>l.id==='road-oneway-directions'),roadDirectionLayer({source:'roads',roadProperty:'kind',roadValues:['residential'],rawOsm:true})]){
    const compiled=createExpression(layer.layout['icon-rotate'],'layers[0].layout.icon-rotate');
    assert.equal(compiled.result,'success');
    const filter=featureFilter(layer.filter,'layers[0].filter');
    for(const value of [1,-1,'1','-1']){
      const feature={type:2,properties:{class:'minor',kind:'residential',oneway:value}};
      assert.ok(filter.filter({zoom:18},feature));
      assert.equal(compiled.value.evaluate({zoom:18},feature),String(value)==='-1'?180:0);
    }
    if(layer.source==='roads')for(const value of ['yes','true','reverse']){
      const feature={type:2,properties:{kind:'residential',oneway:value}};
      assert.ok(filter.filter({zoom:18},feature));
      assert.equal(compiled.value.evaluate({zoom:18},feature),value==='reverse'?180:0);
    }
    assert.equal(layer.layout['icon-rotation-alignment'],'map');
    assert.equal(layer.layout['icon-keep-upright'],false);
  }
});

for(const folder of ['android/app/src/main/assets/maps/sprites','ios/NavSkin/Resources/maps/sprites']){
  for(const ratio of [1,2])test(`SMW ${folder} @${ratio}x uses the corrected source pixels without changing the atlas layout`,async()=>{
    const base=new URL('../'+folder+'/smw'+(ratio===2?'@2x':''),import.meta.url);
    const index=JSON.parse(await readFile(new URL(base.href+'.json'),'utf8'));
    const entry=index['road-oneway-forward'];
    const pixels=await sharp(await readFile(new URL(base.href+'.png'))).extract({left:entry.x,top:entry.y,width:entry.width,height:entry.height}).ensureAlpha().raw().toBuffer();
    const expected=await sharp(await readFile(source),{density:72*ratio}).resize(32*ratio,32*ratio).ensureAlpha().raw().toBuffer();
    assert.deepEqual(pixels,expected);
    assert.ok(alpha(pixels,27*ratio,15*ratio,32*ratio)>128,'bundled arrow also points east');
  });
}
