let catalogPromise;

export function loadAvatarCatalog() {
  if (!catalogPromise) catalogPromise = fetch('/assets/navigation-3d/catalog.json')
    .then(response => { if (!response.ok) throw new Error('Catálogo 3D indisponível'); return response.json(); })
    .catch(error => { catalogPromise = null; throw error; });
  return catalogPromise;
}

/** The image already contains the view rotation: the marker stays viewport-aligned. */
export function avatarFrame(catalog, theme, heading, cameraBearing, cameraPitch) {
  const avatar = catalog?.themes?.[theme];
  if (!avatar || !Number.isFinite(heading) || !Number.isFinite(cameraBearing) || !Number.isFinite(cameraPitch)) return null;
  const angle = ((heading - cameraBearing) % 360 + 360) % 360;
  const yawIndex = Math.floor(angle / 360 * catalog.frameCount + 0.5) % catalog.frameCount;
  let pitchIndex = 0;
  for (let i = 1; i < catalog.pitches.length; i++) {
    if (Math.abs(catalog.pitches[i] - cameraPitch) < Math.abs(catalog.pitches[pitchIndex] - cameraPitch)) pitchIndex = i;
  }
  const path = avatar.frames[pitchIndex]?.[yawIndex];
  return path ? { path, src: `/assets/navigation-3d/${path}`, label: avatar.label, yawIndex, pitchIndex } : null;
}
