/** MapLibre requires an absolute sprite URL when a style is provided as an object. */
export function resolveStyleAssets(style,baseURL) {
  return {...style,sprite:new URL(style.sprite,baseURL).href};
}
