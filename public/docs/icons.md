# Ícones dos pontos de interesse

Os 19 símbolos de POI são derivados de **Maki 8.2.0**, da Mapbox, distribuído
sob a licença **CC0 1.0 Universal**. A geometria vem dos SVGs do pacote npm
`@mapbox/maki`, instalado como dependência de desenvolvimento. Cores, escala e
contornos foram adaptados neste projeto.

- Catálogo oficial: <https://labs.mapbox.com/maki-icons/>
- Código e licença: <https://github.com/mapbox/maki>
- Texto da licença: <https://github.com/mapbox/maki/blob/main/LICENSE.txt>
- CC0: <https://creativecommons.org/publicdomain/zero/1.0/>

O tratamento visual usa preenchimento colorido, uma borda escura e um contorno
branco para manter os símbolos legíveis sobre o mapa. Ele evoca os blips de
cartografia de jogos. **Os arquivos não são assets originais de GTA: San Andreas
nem foram extraídos do jogo.**

## Regerar os arquivos

```sh
npm ci
node scripts/build-poi-sprites.mjs
```

O script usa Node.js, Sharp e os arquivos SVG da versão instalada do Maki. Não
consulta serviços externos durante a geração. Não depende de caminhos absolutos
da máquina. O catálogo compartilhado fica em `src/poi-categories.js`.

Arquivos gerados:

- `public/sprites/poi.png` e `poi.json`: atlas e metadados em resolução 1×.
- `public/sprites/poi@2x.png` e `poi@2x.json`: atlas e metadados em resolução 2×.
- `public/icons/poi-<id>.svg`: os mesmos símbolos vetoriais para os filtros e a legenda.

O estilo do MapLibre usa a URL base local `/sprites/poi` e nomes de imagem
`poi-<id>`. Cada imagem ocupa 26 × 26 pixels lógicos, sem fundo ou moldura de
badge, com dois pixels de margem transparente entre células do atlas. Em 2×,
as dimensões e coordenadas físicas dobram, e `pixelRatio` vale 2.

## Correspondência com o Maki

| ID do POI | Categoria | SVG do Maki |
| --- | --- | --- |
| `park` | Parques | `park` |
| `hospital` | Hospitais | `hospital` |
| `pharmacy` | Farmácias | `pharmacy` |
| `clinic` | Clínicas | `doctor` |
| `fuel` | Postos de combustível | `fuel` |
| `theatre` | Teatros | `theatre` |
| `cinema` | Cinemas | `cinema` |
| `culture` | Centros culturais | `art-gallery` |
| `museum` | Museus | `museum` |
| `school` | Escolas | `school` |
| `university` | Universidades | `college` |
| `police` | Polícia | `police` |
| `fire_station` | Bombeiros | `fire-station` |
| `bank` | Bancos | `town-hall` |
| `atm` | Caixas eletrônicos | `bank` |
| `parking` | Estacionamentos | `parking` |
| `restaurant` | Restaurantes | `restaurant` |
| `cafe` | Cafés | `cafe` |
| `fast_food` | Lanchonetes | `fast-food` |

Maki não fornece um símbolo específico de caixa eletrônico: o desenho de uma
cédula (`bank`) identifica esses pontos. O prédio com colunas (`town-hall`)
identifica agências bancárias. Os símbolos de escola, universidade e serviços
usam os respectivos pictogramas do catálogo, sem logotipos de estabelecimentos.
