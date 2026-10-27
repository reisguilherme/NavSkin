# Especificação visual e adaptações

Arquivo de autoridade: `public/styles/tokens.json`. Implementação cartográfica: `src/style.js`. A aplicação apenas escolhe um modo e envia ajustes; a geometria não muda. Os arquivos completos `gta-sa.json` e `neutral.json` são exportações reproduzíveis.

## Evidência → estimativa → regra

| Evidência observada | Valor estimado na imagem complementar | Regra adotada na PoC |
|---|---|---|
| Vias pretas, contínuas, conferidas na pausa do guia PS2 | Preto `#000000` no mosaico | Quase preto `#080A08`; contorno da mesma cor mantém a silhueta preta, sem halo claro artificial |
| Solo urbano cinza | `#999999` | Fundo e landuse residencial `#999999`; fundo não é polígono de urbanização |
| Massas de quadras/edificações quase brancas | `#F7FCF8`–`#FFFFFF` | `#F7FCF8` somente nos edifícios OSM; sem inferir quarteirões |
| Vegetação chapada oliva e verde escuro | `#748837`, `#38682C` | Parques/áreas de lazer oliva; bosques/florestas verde escuro; gramados `#879548` são adaptação intermediária |
| Água azul acinzentada | `#7088AB` | Polígonos e linhas de água com esse tom; margem `#475C70` é adaptação para legibilidade |
| Solo terroso em outras regiões do jogo | `#978572` | Tom adotado para área industrial/brownfield, como diferenciação cartográfica adaptada; não afirma solo desértico em Goiânia |
| Corredores com interior mais claro em alguns trechos | Sem constante oficial medida | Filete `#969A87` apenas em motorway; adaptação da hierarquia |

`commercial`, `square`, `path`, `rail`, `label`, `labelHalo` e cores do estilo neutro são escolhas de projeto. Não são cores oficiais extraídas. A paleta de referência, o método RGB e as procedências estão em `docs/references.md`. A decisão de representar uma categoria OSM é baseada na tag real, não na coincidência de cor com uma forma no jogo.

## Proporção e hierarquia viária

As ruas e alças mantêm seus eixos originais. Largura em pixels é uma decisão visual, não uma medida física de pista. Interpolação linear entre os zooms **11, 13, 15 e 17**:

| Categoria | Larguras base (px) | Contorno por lado (px) | Zoom mínimo |
|---|---|---|---|
| Serviço | 0.25 / 0.5 / 1.6 / 4 | 0.3 | 14.2 |
| Local | 0.5 / 1.15 / 3.4 / 8 | 0.35 | 11 |
| Tertiary | 0.7 / 1.7 / 4.3 / 10 | 0.45 | 11 |
| Secondary | 1 / 2.4 / 5.7 / 13 | 0.55 | 11 |
| Busway | 0.9 / 2 / 4.8 / 11 | 0.5 | 11 |
| Primary | 1.3 / 3 / 7 / 15 | 0.65 | 11 |
| Trunk | 1.8 / 3.8 / 8.5 / 18 | 0.8 | 11 |
| Motorway | 2.3 / 4.7 / 10.5 / 22 | 1 | 11 |

Largura externa = largura base × fator do painel + 2 × contorno. No GTA SA, os dois traços são pretos; no neutro, o contorno separa as vias claras do fundo. Extremidades e junções arredondadas evitam fissuras entre segmentos. Todos os contornos de um nível precedem todos os preenchimentos desse nível, evitando bordas atravessando cruzamentos.

Pontes, túneis, passarelas e trilhos usam nível derivado de `layer`, `tunnel` e `bridge`. Desenho em ordem crescente de -5 a +5; dados fora desse intervalo seriam limitados aos extremos. O snapshot contém somente níveis -1, 0, 1 e 2. Túneis usam opacidade menor e traço descontínuo como adaptação explícita de leitura; essa convenção não foi comprovada no jogo.

## Áreas, rótulos e densidade

- Uso do solo real primeiro; depois vegetação, praças, água, edifícios, cursos d’água e rede viária por nível. Marcadores e textos ficam por cima.
- Edifícios aparecem a partir de **13.4**, caminhos a partir de **14.4** e serviços a partir de **14.2**. Não há extrusão 3D.
- Os cinco marcos verificados têm prioridade de rótulo. Outros bairros entram a partir de **14.2**; nomes de avenidas selecionadas, a partir de **15.5**, com espaçamento de 330 px. Colisão de símbolos controla a densidade.
- Arial local com contorno escuro substitui a tipografia do jogo. Letras brancas, nomes de Goiânia e pequenos círculos de referência são adaptações da PoC; não há fonte nem sprite extraído.
- Coordenadas-fonte não foram simplificadas. O particionamento GeoJSON do MapLibre usa tolerância **0.3** no renderer; isso pode simplificar subpixels na visualização, sem alterar os arquivos-fonte.
- Não há textura adicional, granulação, sombreamento, relevo, filtro sobre tiles raster ou geração de imagem da geografia.

A maior diferença de composição é a falta de massas brancas contínuas devido à cobertura real de edifícios. Essa ausência é mantida como limite da prova, não preenchida com polígonos fictícios.
