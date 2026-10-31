import { mesh, box, cylinder, ellipsoid, ring, polygonPlate, prism, join } from './geometry.mjs';

const WHITE = '#fffdf8', BLACK = '#171b24';
const tube = cylinder;

function wheel(model, x, y, radius, width, hub, tire = '#24262d') {
  ring(model, [x, y, radius], [1, 0, 0], radius * .77, radius * .23, tire, 24, 6);
  cylinder(model, [x - width / 2, y, radius], [x + width / 2, y, radius], radius * .67, hub, 20);
  cylinder(model, [x - width / 2 - .015, y, radius], [x + width / 2 + .015, y, radius], radius * .22, '#f0eff1', 12);
}

function bmx() {
  const m = mesh('CJ • BMX');
  const frame = '#ad3036', metal = '#c4d1d9', skin = '#8e5a3c', jeans = '#40789f';
  for (const y of [-.86, .86]) {
    ring(m, [0, y, .44], [1, 0, 0], .36, .075, '#1b2028', 28, 7);
    ring(m, [0, y, .44], [1, 0, 0], .32, .026, '#b6c4ce', 24, 5);
    for (let i = 0; i < 8; i++) {
      const angle = i * Math.PI / 4;
      cylinder(m, [0, y, .44], [0, y + Math.sin(angle) * .31, .44 + Math.cos(angle) * .31], .009, metal, 4);
    }
    cylinder(m, [-.10, y, .44], [.10, y, .44], .055, '#5b616d', 8);
  }
  const rear = [0, -.86, .44], crank = [0, -.10, .38], seat = [0, -.3, 1.04], stem = [0, .70, 1.03], front = [0, .86, .44];
  for (const [a, b] of [[rear, crank], [crank, seat], [seat, rear], [seat, stem], [stem, crank], [stem, front]]) tube(m, a, b, .046, frame, 8);
  for (const x of [-.07, .07]) tube(m, [x, .66, 1.11], [x, .86, .44], .035, metal);
  tube(m, [0, -.3, 1.04], [0, -.34, 1.19], .035, metal);
  ellipsoid(m, [0, -.4, 1.17], [.22, .22, .065], '#25252b', 12, 6);
  tube(m, [0, .65, 1.03], [0, .56, 1.31], .045, metal);
  tube(m, [-.39, .56, 1.32], [.39, .56, 1.32], .035, metal);
  for (const x of [-.35, .35]) tube(m, [x, .44, 1.32], [x, .64, 1.32], .055, '#202126');
  tube(m, [-.2, -.10, .38], [.2, -.10, .38], .025, metal);
  box(m, [-.24, -.12, .39], [.14, .25, .07], '#262933');
  box(m, [.24, -.12, .39], [.14, .25, .07], '#262933');
  // CJ leans over the BMX: white vest, blue denim, dark skin, cropped hair.
  ellipsoid(m, [0, -.33, 1.26], [.24, .23, .22], jeans);
  for (const side of [-1, 1]) {
    const hip = [side * .15, -.30, 1.25], knee = [side * .31, .18, .82], ankle = [side * .23, -.1, .47];
    tube(m, hip, knee, .13, jeans, 9, .11); tube(m, knee, ankle, .10, '#376686', 9, .08);
    ellipsoid(m, [side * .23, -.02, .44], [.12, .23, .09], '#eef0e8', 12, 6);
    box(m, [side * .23, -.02, .38], [.24, .40, .045], '#b9c3bf');
  }
  tube(m, [0, -.31, 1.3], [0, -.04, 1.76], .25, WHITE, 12, .28);
  // Separate skin shoulders and white shoulder straps keep the tank top unmistakable.
  for (const side of [-1, 1]) {
    ellipsoid(m, [side * .25, -.03, 1.72], [.12, .15, .16], skin);
    tube(m, [side * .26, .0, 1.71], [side * .38, .22, 1.48], .09, skin);
    tube(m, [side * .38, .22, 1.48], [side * .35, .55, 1.34], .075, skin);
    ellipsoid(m, [side * .35, .55, 1.34], [.085, .10, .075], skin, 10, 6);
    tube(m, [side * .16, -.10, 1.80], [side * .16, .13, 1.70], .053, WHITE, 8);
  }
  cylinder(m, [0, .02, 1.76], [0, .06, 1.91], .10, skin);
  ellipsoid(m, [0, .08, 2.04], [.18, .17, .23], skin, 14, 10);
  ellipsoid(m, [0, .06, 2.17], [.18, .17, .105], '#222323', 14, 7);
  ellipsoid(m, [0, .24, 2.04], [.065, .06, .07], skin, 8, 6);
  for (const x of [-.075, .075]) ellipsoid(m, [x, .224, 2.105], [.025, .014, .016], '#231d19', 8, 6);
  return m;
}

function helloKitty() {
  const m = mesh('Hello Kitty • Conversível');
  const pearl = '#fff9fc', pink = '#f357a4', darkPink = '#b9226c';
  for (const x of [-.64, .64]) for (const y of [-.75, .78]) {
    wheel(m, x, y, .33, .19, '#fffcff', '#313139');
    const side = Math.sign(x);
    cylinder(m, [x + side * .095, y, .33], [x + side * .12, y, .33], .135, '#ed4f9b', 16);
  }
  ellipsoid(m, [0, -.05, .48], [.68, 1.22, .29], pearl, 20, 10);
  box(m, [0, -.24, .55], [1.16, 1.66, .39], pearl);
  // Open cockpit: floor and deep fuchsia bucket seat framed by white toy body.
  box(m, [0, -.48, .755], [.92, 1.05, .08], darkPink);
  for (const x of [-.53, .53]) {
    box(m, [x, -.46, .81], [.16, 1.25, .35], pearl);
    ellipsoid(m, [x, -.46, .975], [.09, .64, .06], pearl, 12, 6);
  }
  ellipsoid(m, [0, -.86, .86], [.54, .20, .29], pearl);
  box(m, [0, -.62, .82], [.81, .48, .14], pink);
  box(m, [0, -.88, .98], [.80, .15, .38], '#d63887');
  for (const x of [-.28, -.14, 0, .14, .28]) tube(m, [x, -.787, .87], [x, -.787, 1.13], .013, '#ff9dc9', 5);
  box(m, [0, -.88, 1.17], [.82, .19, .085], '#fa68ae');
  cylinder(m, [-.25, .05, .74], [-.25, -.10, .99], .035, darkPink);
  ring(m, [-.25, -.12, 1.01], [0, -.6, .8], .16, .028, '#f28fbd', 18, 6);
  tube(m, [-.38, -.12, 1.01], [-.12, -.12, 1.01], .015, '#ad316e');
  // The hood itself is Kitty's sculpted head, with projecting ears and an oversized bow.
  ellipsoid(m, [0, .63, .92], [.66, .63, .27], pearl, 20, 12);
  const outline = [[-.61, .60], [-.61, 1.08], [-.48, 1.27], [-.22, 1.06], [.22, 1.06], [.48, 1.27], [.61, 1.08], [.61, .60]];
  polygonPlate(m, outline, .88, .12, pearl);
  for (const side of [-1, 1]) {
    prism(m, [[side * .29, .96, 1.02], [side * .59, 1.00, 1.02], [side * .48, 1.26, 1.02]], [[side * .29, .96, 1.05], [side * .59, 1.00, 1.05], [side * .48, 1.26, 1.28]], pearl);
    polygonPlate(m, [[side * .39, 1.05], [side * .51, 1.08], [side * .47, 1.19]], 1.16, .02, '#ffd7e9');
    // Black hood eyes and whiskers remain legible in the navigation camera's rear/top view.
    ellipsoid(m, [side * .26, .60, 1.178], [.051, .075, .015], '#28232a', 12, 6);
    for (let row = 0; row < 3; row++) tube(m, [side * .46, .43 + row * .14, 1.106], [side * .64, .40 + row * .16, 1.06], .018, '#302932', 6);
  }
  ellipsoid(m, [0, .40, 1.16], [.080, .054, .02], '#ffd841', 12, 6);
  // Kitty's left ear (driver's right), like the approved reference photo.
  ellipsoid(m, [.31, 1.02, 1.30], [.20, .14, .095], '#eb2e86', 12, 8);
  ellipsoid(m, [.62, 1.02, 1.30], [.18, .14, .095], '#f54598', 12, 8);
  ellipsoid(m, [.47, 1.01, 1.32], [.10, .11, .11], '#ff69ae', 12, 8);
  ring(m, [.31, 1.02, 1.37], [0, 0, 1], .082, .015, '#b32268', 14, 5);
  ring(m, [.62, 1.02, 1.37], [0, 0, 1], .070, .015, '#b32268', 14, 5);
  for (const side of [-1, 1]) {
    box(m, [side * .37, -1.085, .61], [.19, .045, .11], '#f54d96');
    ellipsoid(m, [side * .61, -.22, .89], [.022, .09, .035], '#ee529c', 10, 6);
  }
  box(m, [0, -1.12, .45], [.75, .07, .11], '#c7bdc8');
  return m;
}

function horse() {
  const m = mesh('Arthur • Cavalo');
  const coat = '#985e36', dark = '#442b23', mane = '#30241f';
  ellipsoid(m, [0, -.1, 1.2], [.43, .80, .43], coat, 16, 10);
  ellipsoid(m, [0, -.55, 1.15], [.45, .38, .40], '#8a4d2e');
  for (const side of [-1, 1]) for (const end of [-1, 1]) {
    const x = side * .26, y = end * .55;
    tube(m, [x, y, 1.15], [x, y + (end === 1 ? .06 : -.14), .63], .13, coat, 10, .09);
    tube(m, [x, y + (end === 1 ? .06 : -.14), .63], [x, y - .04, .16], .075, '#a8744d', 8, .055);
    ellipsoid(m, [x, y + .01, .12], [.13, .19, .13], dark, 10, 6);
  }
  tube(m, [0, .38, 1.25], [0, .68, 1.95], .29, coat, 12, .21);
  ellipsoid(m, [0, .84, 2.03], [.21, .39, .30], coat, 14, 10);
  ellipsoid(m, [0, 1.12, 1.89], [.19, .27, .19], '#76503a');
  for (const x of [-.13, .13]) {
    tube(m, [x, .67, 2.18], [x * 1.25, .67, 2.50], .09, coat, 7, .012);
    ellipsoid(m, [x * 1.51, .94, 2.11], [.018, .055, .040], '#181917', 10, 6);
    tube(m, [x * 1.50, .78, 2.25], [x * 1.65, 1.13, 1.96], .021, '#39271f', 6);
  }
  // Dark mane follows the neck; white forehead blaze is visible from above/front.
  for (let i = 0; i < 7; i++) ellipsoid(m, [0, .40 + i * .025, 1.55 + i * .10], [.07, .14, .15], mane, 8, 6);
  tube(m, [0, .84, 2.30], [0, 1.20, 2.00], .052, '#eee0c4', 7, .025);
  tube(m, [0, -.85, 1.27], [0, -1.02, .92], .12, mane, 9, .09);
  tube(m, [0, -1.02, .92], [0, -1.14, .42], .09, mane, 8, .025);
  // Arthur's worn saddle, rolled blanket and side bags give the horse its frontier silhouette.
  ellipsoid(m, [0, -.13, 1.57], [.40, .43, .08], '#505d68');
  ellipsoid(m, [0, -.12, 1.66], [.27, .33, .095], '#653e27');
  box(m, [0, -.40, 1.72], [.52, .08, .17], '#613c27');
  tube(m, [0, .14, 1.65], [0, .14, 1.84], .06, '#6e482c');
  ellipsoid(m, [0, .14, 1.85], [.10, .075, .04], '#b38c57');
  cylinder(m, [-.38, -.62, 1.59], [.38, -.62, 1.59], .14, '#b19b79', 12);
  for (const x of [-.23, .23]) ring(m, [x, -.62, 1.59], [1, 0, 0], .142, .015, '#58402a', 14, 5);
  for (const side of [-1, 1]) {
    box(m, [side * .42, -.43, 1.19], [.15, .34, .39], '#6a482f');
    box(m, [side * .425, -.43, 1.40], [.18, .37, .08], '#9b774b');
    box(m, [side * .51, -.43, 1.25], [.015, .08, .11], '#d3b16f');
    tube(m, [side * .27, -.08, 1.62], [side * .48, -.06, .86], .027, '#5a3826');
    ring(m, [side * .48, -.06, .83], [1, 0, 0], .12, .022, '#b99c63', 12, 5);
    tube(m, [side * .19, 1.07, 1.92], [side * .21, .04, 1.69], .013, '#36261f', 5);
  }
  return m;
}

function yoshi() {
  const m = mesh('Yoshi');
  const green = '#59c83a', shade = '#39a538', cream = '#fffbe8';
  for (const side of [-1, 1]) {
    ellipsoid(m, [side * .29, -.17, .45], [.22, .24, .31], green);
    ellipsoid(m, [side * .33, .13, .17], [.26, .42, .18], '#f4942d', 14, 8);
    ellipsoid(m, [side * .33, .14, .06], [.26, .40, .05], '#d4b06b', 14, 6);
    cylinder(m, [side * .30, -.12, .25], [side * .30, -.12, .38], .19, '#e26825', 12);
  }
  ellipsoid(m, [0, -.11, .88], [.43, .43, .61], green, 16, 11);
  ellipsoid(m, [0, .23, .89], [.34, .13, .49], cream, 14, 10);
  cylinder(m, [0, -.35, .58], [0, -.94, .30], .22, shade, 10, .025);
  ellipsoid(m, [0, .1, 1.53], [.37, .37, .46], green, 16, 10);
  ellipsoid(m, [0, .48, 1.62], [.49, .48, .34], green, 18, 11);
  ellipsoid(m, [0, .45, 1.39], [.39, .36, .16], cream, 14, 8);
  for (const side of [-1, 1]) {
    ellipsoid(m, [side * .17, .18, 1.94], [.17, .19, .30], cream, 14, 10);
    ellipsoid(m, [side * .17, .34, 1.96], [.066, .055, .14], '#1c3534', 12, 8);
    ellipsoid(m, [side * .15, .386, 2.02], [.024, .013, .045], WHITE, 8, 6);
    ellipsoid(m, [side * .23, .82, 1.79], [.038, .025, .023], '#398f2d', 8, 6);
    tube(m, [side * .32, .02, 1.20], [side * .54, .31, 1.02], .13, green, 10, .11);
    ellipsoid(m, [side * .54, .34, 1.01], [.15, .17, .15], green, 12, 8);
  }
  // Red saddle and dorsal orange spikes are clear from the navigation camera.
  ellipsoid(m, [0, -.41, 1.15], [.36, .12, .34], cream, 16, 9);
  ellipsoid(m, [0, -.49, 1.17], [.29, .10, .26], '#ed4439', 16, 9);
  for (const [y, z, radius] of [[-.24, 1.85, .13], [-.36, 1.59, .12], [-.63, .56, .10], [-.82, .41, .075]]) {
    cylinder(m, [0, y, z], [0, y - .18, z + .03], radius, '#e56c30', 7, .012);
  }
  return m;
}

function kusanagi() {
  const m = mesh('Kusanagi • Moto');
  const red = '#e53842', dark = '#202a32', silver = '#bec9c9';
  for (const y of [-.92, .96]) {
    wheel(m, 0, y, .40, .21, '#48525d', '#1b212b');
    for (const x of [-.125, .125]) {
      ring(m, [x, y, .40], [1, 0, 0], .23, .027, '#f33d42', 24, 5);
      for (let i = 0; i < 5; i++) {
        const angle = i * Math.PI * 2 / 5;
        tube(m, [x, y, .40], [x, y + Math.cos(angle) * .22, .40 + Math.sin(angle) * .22], .018, '#9aadb5', 5);
      }
    }
  }
  box(m, [0, -.18, .47], [.35, 1.20, .26], dark);
  cylinder(m, [-.20, -.16, .51], [.20, -.16, .51], .25, '#444d55', 12);
  for (const side of [-1, 1]) {
    tube(m, [side * .17, -.35, .55], [side * .11, -.92, .40], .075, '#617078', 8);
    tube(m, [side * .18, .57, 1.08], [side * .12, .96, .40], .055, silver, 10);
    tube(m, [side * .22, -.16, .42], [side * .29, -.94, .43], .09, '#a7bbc3', 10);
    cylinder(m, [side * .29, -.91, .43], [side * .29, -1.02, .44], .07, '#20232b', 10);
  }
  prism(m, [[-.28, -.24, .65], [.28, -.24, .65], [.34, .62, .67], [-.34, .62, .67]], [[-.18, -.20, .97], [.18, -.20, .97], [.25, .57, 1.17], [-.25, .57, 1.17]], red);
  ellipsoid(m, [0, .14, 1.00], [.25, .38, .13], '#e94049', 14, 8);
  ellipsoid(m, [0, .25, 1.119], [.07, .08, .012], '#c7d4ce', 10, 6);
  box(m, [0, -.50, .93], [.34, .49, .11], '#24252d');
  prism(m, [[-.22, -.97, .69], [.22, -.97, .69], [.25, -.55, .76], [-.25, -.55, .76]], [[-.15, -1.05, .96], [.15, -1.05, .96], [.22, -.58, .98], [-.22, -.58, .98]], red);
  box(m, [0, -1.06, .89], [.25, .024, .07], '#ff6a55');
  prism(m, [[-.23, .62, .81], [.23, .62, .81], [.19, 1.11, .79], [-.19, 1.11, .79]], [[-.23, .61, 1.26], [.23, .61, 1.26], [.19, .98, 1.08], [-.19, .98, 1.08]], red);
  box(m, [0, 1.07, .96], [.27, .025, .08], '#ccfbf6');
  prism(m, [[-.19, .61, 1.26], [.19, .61, 1.26], [.15, .93, 1.13], [-.15, .93, 1.13]], [[-.19, .59, 1.30], [.19, .59, 1.30], [.15, .91, 1.17], [-.15, .91, 1.17]], '#546d7b');
  tube(m, [-.38, .33, 1.08], [.38, .33, 1.08], .03, '#748a96');
  for (const side of [-1, 1]) {
    tube(m, [side * .35, .26, 1.08], [side * .35, .43, 1.08], .04, BLACK);
    tube(m, [side * .29, .62, .89], [side * .24, -.05, .76], .018, '#65f9ea', 5);
    box(m, [side * .27, .24, .86], [.015, .26, .12], '#fff2d3');
    for (let line = 0; line < 3; line++) box(m, [side * .282, .17 + line * .065, .86], [.008, .02, .08], '#d13740');
  }
  return m;
}

function vaultBoy() {
  const m = mesh('Vault Boy');
  const blue = '#2c71bd', yellow = '#f4cf40', skin = '#f5c493', hair = '#f2c34c';
  for (const side of [-1, 1]) {
    cylinder(m, [side * .20, 0, .36], [side * .18, -.04, .91], .145, blue, 10, .17);
    ellipsoid(m, [side * .20, .10, .17], [.18, .30, .16], '#69513a', 12, 8);
    box(m, [side * .2, .10, .07], [.34, .51, .05], '#41362c');
  }
  cylinder(m, [0, -.04, .81], [0, -.04, 1.45], .31, blue, 12, .36);
  box(m, [0, .28, 1.18], [.075, .05, .51], yellow);
  cylinder(m, [0, -.04, .90], [0, -.04, .99], .32, yellow, 12);
  cylinder(m, [0, -.04, 1.42], [0, -.04, 1.49], .24, yellow, 12);
  tube(m, [-.32, -.04, 1.41], [-.46, -.02, 1.05], .12, blue);
  ellipsoid(m, [-.46, -.02, .98], [.13, .14, .16], skin, 12, 8);
  // His raised right thumb reads clearly from every oblique view.
  tube(m, [.32, -.04, 1.41], [.58, .02, 1.16], .13, blue);
  tube(m, [.58, .02, 1.16], [.62, .17, 1.49], .115, blue, 10, .095);
  cylinder(m, [.62, .15, 1.44], [.62, .17, 1.52], .12, yellow, 10);
  ellipsoid(m, [.62, .18, 1.63], [.15, .13, .18], skin, 12, 8);
  tube(m, [.52, .24, 1.63], [.51, .24, 1.92], .067, skin, 9, .055);
  ellipsoid(m, [.52, .24, 1.90], [.057, .058, .068], skin, 10, 7);
  cylinder(m, [0, -.04, 1.46], [0, -.04, 1.64], .12, skin, 10);
  ellipsoid(m, [0, .015, 1.92], [.35, .29, .38], skin, 18, 12);
  for (const side of [-1, 1]) ellipsoid(m, [side * .34, .01, 1.91], [.07, .065, .11], skin, 10, 7);
  ellipsoid(m, [0, -.02, 2.17], [.36, .30, .22], hair, 16, 10);
  for (const [x, y, z, radius] of [[-.23, .19, 2.21, .17], [-.06, .25, 2.28, .16], [.11, .20, 2.27, .15], [.26, .13, 2.16, .13]]) ellipsoid(m, [x, y, z], [radius, radius * .7, radius * .7], hair, 12, 8);
  // Side-parted lock and smile: purposefully large at navigation icon scale.
  tube(m, [-.28, .20, 2.14], [-.13, .28, 2.20], .025, '#c39235', 6);
  for (const x of [-.12, .12]) {
    ellipsoid(m, [x, .281, 1.98], [.027, .017, .044], '#473d32', 10, 6);
    tube(m, [x - .045, .27, 2.07], [x + .05, .27, 2.08], .019, '#825f2d', 6);
  }
  ellipsoid(m, [0, .307, 1.92], [.045, .045, .06], '#e6a875', 10, 7);
  ellipsoid(m, [0, .271, 1.80], [.13, .025, .055], '#7e513d', 14, 7);
  ellipsoid(m, [0, .292, 1.82], [.108, .012, .027], WHITE, 12, 6);
  // Small Pip-Boy on the left forearm.
  box(m, [-.47, .06, 1.14], [.25, .17, .22], '#536441');
  box(m, [-.47, .155, 1.16], [.14, .023, .12], '#91d06b');
  return m;
}

function knight() {
  const m = mesh('O Cavaleiro');
  const cloak = '#343e64', edge = '#485580', mask = '#fff9e5';
  for (const side of [-1, 1]) {
    cylinder(m, [side * .13, 0, .15], [side * .12, 0, .65], .075, '#182339', 8);
    ellipsoid(m, [side * .13, .08, .13], [.09, .18, .10], '#141c2b', 10, 7);
  }
  // Bell-shaped cloak, its individually layered hems visible from the rear.
  cylinder(m, [0, 0, .47], [0, 0, 1.20], .47, cloak, 10, .23);
  for (let i = 0; i < 9; i++) {
    const angle = i * Math.PI * 2 / 9;
    const a = angle - .25, b = angle + .25;
    prism(m,
      [[Math.cos(a) * .46, Math.sin(a) * .46, .42], [Math.cos(b) * .46, Math.sin(b) * .46, .42], [Math.cos(angle) * .50, Math.sin(angle) * .50, .28]],
      [[Math.cos(a) * .22, Math.sin(a) * .22, 1.13], [Math.cos(b) * .22, Math.sin(b) * .22, 1.13], [Math.cos(angle) * .25, Math.sin(angle) * .25, .98]], i % 2 ? edge : cloak);
  }
  // Carried nail, slightly behind the cloak, catches the light at oblique angles.
  tube(m, [.28, -.10, .62], [.62, -.48, 1.39], .055, '#a2b5c5', 6, .025);
  tube(m, [.24, -.07, .54], [.33, -.17, .73], .045, '#5e6574');
  tube(m, [.17, -.10, .68], [.41, -.10, .68], .025, '#bac6c7');
  ellipsoid(m, [0, .02, 1.52], [.44, .30, .40], mask, 18, 12);
  // Iconic asymmetric curved horns built as tapering solid segments.
  for (const side of [-1, 1]) {
    tube(m, [side * .29, -.01, 1.75], [side * .42, -.04, 2.06], .14, mask, 10, .11);
    tube(m, [side * .42, -.04, 2.06], [side * .38, .02, 2.36], .11, mask, 10, .055);
    tube(m, [side * .38, .02, 2.36], [side * .25, .06, 2.55], .055, mask, 9, .004);
    ellipsoid(m, [side * .18, .292, 1.55], [.105, .028, .16], '#101b29', 14, 9);
  }
  return m;
}

function chicken() {
  const m = mesh('Galinha • Crossy Road');
  const feather = '#fffdf6', cream = '#e6e8df', red = '#e84844', yellow = '#ffc744';
  for (const side of [-1, 1]) {
    box(m, [side * .24, 0, .23], [.12, .14, .38], '#ef972b');
    box(m, [side * .24, .14, .07], [.30, .42, .13], '#f5ad35');
    for (let toe = -1; toe <= 1; toe++) box(m, [side * .24 + toe * .105, .34, .065], [.07, .14, .09], '#ffc54c');
  }
  box(m, [0, -.13, .75], [.88, 1.02, .88], feather);
  box(m, [0, .08, 1.40], [.72, .72, .58], feather);
  box(m, [0, .15, 1.75], [.60, .58, .22], feather);
  for (const side of [-1, 1]) {
    box(m, [side * .49, -.22, .82], [.18, .62, .46], cream);
    box(m, [side * .535, -.05, .91], [.11, .33, .25], '#f6f5ed');
    box(m, [side * .365, .24, 1.51], [.025, .13, .16], '#202631');
    box(m, [side * .38, .27, 1.56], [.013, .04, .045], WHITE);
  }
  box(m, [0, .59, 1.36], [.38, .38, .18], yellow);
  box(m, [0, .75, 1.34], [.30, .10, .12], '#eaa433');
  box(m, [0, .46, 1.13], [.20, .15, .28], red);
  box(m, [0, .51, 1.02], [.25, .20, .14], '#cc3c38');
  // Three stepped comb blocks and the rear stair-step tail define the original voxel silhouette.
  box(m, [0, .12, 1.97], [.19, .67, .20], red);
  for (const [y, z] of [[-.11, 2.10], [.12, 2.17], [.35, 2.10]]) box(m, [0, y, z], [.19, .17, .16], red);
  box(m, [0, -.70, 1.03], [.63, .30, .23], cream);
  box(m, [0, -.82, 1.22], [.47, .27, .18], feather);
  box(m, [0, -.90, 1.37], [.31, .20, .16], feather);
  return m;
}

export const models = {
  gta: bmx(),
  rosa: helloKitty(),
  rdr2: horse(),
  smw: yoshi(),
  cyberpunk: kusanagi(),
  pipboy: vaultBoy(),
  hollow: knight(),
  crossy: chicken(),
};
