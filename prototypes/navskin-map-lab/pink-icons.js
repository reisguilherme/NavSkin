/** Candy/toy symbols for the Pink World browser proposal; vehicle drawn from the user's Hello Kitty toy-car reference. */
const icons = [
  ['parks', 'Parques', 'Bosque de algodão-doce', 'Copas esculpidas e uma ilha de menta.', '#C9F3DC'],
  ['health', 'Saúde', 'Clínica do coração', 'Uma clínica de brinquedo, feita para cuidar.', '#FFE1E8'],
  ['food', 'Alimentação', 'Café de cupcake', 'Glacê, confeitos e uma xícara bem pequena.', '#FFF0D6'],
  ['fuel', 'Postos', 'Posto de brinquedo', 'Bomba arredondada com visor de menta.', '#F7DDEB'],
  ['shops', 'Lojas', 'Boutique de presentes', 'Toldo listrado e um presente na vitrine.', '#FCE8D5'],
  ['parking', 'Estacionamento', 'Garagem de carrinho', 'Um arco lilás protege o pequeno carro.', '#E5DCF7'],
  ['education', 'Educação', 'Escola de histórias', 'A escola nasce de um livro aberto.', '#FFF1D8'],
  ['culture', 'Cultura', 'Teatro de máscaras doces', 'Duas máscaras com expressões diferentes.', '#EADCF6'],
  ['religion', 'Locais de culto', 'Santuário de luz', 'Uma porta acolhedora e uma pequena chama.', '#F5E5F4'],
  ['saved-home', 'Casa', 'Minha casinha de glacê', 'Telhado de morango e coração na fachada.', '#FFE1EC'],
  ['saved-work', 'Trabalho', 'Maleta dos sonhos', 'Uma maleta macia com fecho de menta.', '#E9E0F6'],
  ['saved-heart', 'Favoritos', 'Coração de jujuba', 'Um pingente de coração, polido como doce.', '#FFE0EC'],
  ['vehicle', 'Meu veículo', 'Carrinho Hello Kitty', 'Vista de cima: capô branco com orelhinhas, laço rosa e banco conversível.', '#DDF2E6'],
];

export const PINK_ICON_CATALOG = Object.freeze(icons.map(([category, label, title, description, background]) => Object.freeze({
  id: `pink-${category}`, category, label, title, description, background,
  role: category.startsWith('saved-') ? 'saved' : category === 'vehicle' ? 'vehicle' : 'poi',
  src: new URL(`./pink-icons/${category}.svg`, import.meta.url).href,
  viewBox: '0 0 64 64',
})));

/** 96 physical px / pixelRatio 3 = 32 logical px in MapLibre. */
export const PINK_ICON_SIZE = 96;
export const PINK_ICON_PIXEL_RATIO = 3;
let pendingImages;

function rasterize(entry) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const fail = reason => {
      clearTimeout(timeout);
      image.onload = image.onerror = null;
      reject(new Error(`Ícone ${entry.id}: ${reason}`));
    };
    const timeout = setTimeout(() => fail('o arquivo SVG não respondeu.'), 15000);
    image.onload = () => {
      try {
        const canvas = document.createElement('canvas');
        canvas.width = canvas.height = PINK_ICON_SIZE;
        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) throw new Error('Canvas 2D indisponível.');
        context.drawImage(image, 0, 0, PINK_ICON_SIZE, PINK_ICON_SIZE);
        const pixels = context.getImageData(0, 0, PINK_ICON_SIZE, PINK_ICON_SIZE);
        clearTimeout(timeout);
        image.onload = image.onerror = null;
        resolve([entry.id, { width: PINK_ICON_SIZE, height: PINK_ICON_SIZE, data: new Uint8Array(pixels.data) }]);
      } catch (error) { fail(error.message || 'não foi possível ler os pixels.'); }
    };
    image.onerror = () => fail('não foi possível carregar o SVG.');
    image.src = entry.src;
  });
}

/**
 * Load once per page, including concurrent callers. A failed batch is removed
 * from the cache so a later call can retry. Rejection is intentionally visible
 * to the caller; the proposed style must not silently render missing symbols.
 * @returns {Promise<Record<string, {width: number, height: number, data: Uint8Array}>>}
 */
export function loadPinkIcons() {
  if (!pendingImages) {
    if (typeof Image === 'undefined' || typeof document === 'undefined') {
      return Promise.reject(new Error('Os ícones Pink World precisam de um navegador com Canvas 2D.'));
    }
    pendingImages = Promise.all(PINK_ICON_CATALOG.map(rasterize))
      .then(entries => Object.fromEntries(entries))
      .catch(error => { pendingImages = undefined; throw error; });
  }
  return pendingImages;
}

const poiImages = new Map(PINK_ICON_CATALOG.filter(icon => icon.role === 'poi').map(icon => [`poi-${icon.category}`, icon.id]));
const savedImages = new Map(PINK_ICON_CATALOG.filter(icon => icon.role !== 'poi').flatMap(icon => [
  [icon.category, icon.id], [`poi-${icon.category}`, icon.id], [`marker-${icon.category}`, icon.id],
]));

/**
 * Returns a deep copy. Replaces the old sprite/subtype match expression only on
 * the nine known category layers. Source, filter, IDs, labels, zoom bounds and
 * collision rules are untouched. The caller applies this ONLY to proposed rosa.
 */
export function applyPinkIcons(style) {
  const result = structuredClone(style);
  for (const layer of result.layers || []) {
    if (layer.type !== 'symbol' || !layer.layout?.['icon-image']) continue;
    const replacement = poiImages.get(layer.id) || savedImages.get(layer.layout['icon-image']);
    if (replacement) layer.layout['icon-image'] = replacement;
  }
  return result;
}
