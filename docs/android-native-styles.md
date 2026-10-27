# Cartografia Android local: GTA SA, Rosa Pop e RDR2

Este pacote prepara três temas para MapLibre Native 13.7.1, sem servidores de mapas, fontes ou sprites em tempo de execução. Cada tema tem uma variante diurna e uma noturna para acompanhar a configuração de dia/noite do host: GTA SA, Rosa Pop e RDR2. Validação de JSON e inspeção de sprites não comprovam renderização nativa, funcionamento no DHU ou funcionamento em uma central física; a integração registra essa verificação separadamente.

## Contrato para o aplicativo

| Recurso | Caminho no APK / URI |
| --- | --- |
| GTA SA | `asset://maps/styles/gta.json` |
| Rosa Pop | `asset://maps/styles/rosa.json` |
| RDR2 | `asset://maps/styles/rdr2.json` |
| GTA SA, host noturno | `asset://maps/styles/gta-night.json` |
| Rosa Pop, host noturno | `asset://maps/styles/rosa-night.json` |
| RDR2, host noturno | `asset://maps/styles/rdr2-night.json` |
| Dados comuns | `asset://maps/data/{areas,roads,waterways,places,pois}.geojson` |
| Edificações | `asset://maps/tiles/buildings/{z}/{x}/{y}.pbf`, `source-layer: buildings` |
| Sprite GTA / Rosa / RDR2 | `asset://maps/sprites/gta` / `asset://maps/sprites/rosa` / `asset://maps/sprites/rdr2` |
| Glifos | `asset://maps/glyphs/{fontstack}/{range}.pbf` |
| Fonte GTA / Rosa | `text-font: ["GameMapSans"]` — Noto Sans Bold |
| Fonte RDR2 | `text-font: ["GameMapSerif"]` — Noto Serif Bold |
| Créditos legíveis localmente | `maps/credits/attribution.html` |
| Proveniência e licenças | `maps/credits/sources.json` e arquivos locais da mesma pasta |
| Inventário verificável | `maps/credits/asset-manifest.json` |

Os seis arquivos contêm exatamente as mesmas fontes geográficas, coordenadas, filtros semânticos e grupos físicos de vias. A integração seleciona `${theme}${night ? '-night' : ''}.json` a partir do tema salvo e do estado atual de dia/noite do contexto/host. A aparência noturna não cria outra preferência persistida. Nas variantes noturnas, `metadata.gamemap.theme` continua `gta`/`rosa`/`rdr2`, com `appearance: "night"` e o identificador técnico em `variant`. A mudança de aparência deve reaplicar os filtros e preservar a câmera, como a troca de tema.

As áreas de parque são a camada base `vegetation-parks`. Os seis filtros mudam somente `visibility` nas camadas a seguir:

| Categoria / camada | Tags OSM selecionadas | POIs no recorte | Zoom inicial |
| --- | --- | ---: | ---: |
| Parques — `poi-parks` | `leisure=park` ou `garden` | 571 | 12,2 |
| Combustível — `poi-fuel` | `amenity=fuel` | 201 | 13 |
| Restaurantes e cafés — `poi-food` | `amenity=restaurant`, `cafe`, `fast_food` | 308 | 14,5 |
| Estacionamentos — `poi-parking` | `amenity=parking` | 152 | 15 |
| Saúde — `poi-health` | `amenity=hospital`, `clinic`, `doctors`, `pharmacy` | 245 | 13,2 |
| Mercados e conveniência — `poi-shops` | `shop=supermarket`, `convenience` | 94 | 14,5 |

Cada camada reúne o ícone e o nome opcional do estabelecimento. O nome aparece a partir de zoom 16,8; desligar a camada oculta ambos. Colisão de ícones e texto permanece ativa; `text-optional` permite o ícone quando não cabe seu nome. O tamanho dos ícones cresce de aproximadamente 24 para 34 pixels de estilo entre zoom 12 e 18. Os rótulos geográficos de bairros/parques permanecem parte da cartografia; não são uma segunda camada de ícones de POI.

O gerador remove todas as camadas de círculo/ícone herdadas da PoC antes de criar os seis grupos nativos. Isso inclui os antigos anchors `place-points` e `landmark-icons`, mesmo se forem renomeados na versão web. Com as seis categorias desligadas, não resta nenhum círculo nem sprite no mapa; continuam os nomes geográficos e as áreas de parque. Um teste cobre esse estado nas seis variantes.

Ao carregar/trocar um estilo, a integração deve reaplicar `visibility` nas seis camadas depois de `onStyleLoaded`; as preferências e a câmera pertencem ao aplicativo. Estes JSONs iniciam todas as categorias como visíveis. Desligar `poi-parks` mantém `vegetation-parks` visível. Nenhum sprite usa nomes de camada das categorias antigas da PoC web.

## Dados e limites

O recorte cobre longitude **−49,325 a −49,205**, latitude **−16,755 a −16,640**, em Goiânia. A câmera da PoC usa centro `[-49.264, -16.693]`; a câmera inicial final é responsabilidade da integração Android. Fora do recorte pode haver apenas fundo ou feições que cruzem a borda. Não há GPS, localização do aparelho, rotas nem informações de abertura/funcionamento verificadas.

O empacotamento copia os bytes de `areas.geojson`, `roads.geojson`, `waterways.geojson`, `places.geojson` da PoC, e `android/maps/pois.geojson`, sem reprojetar, arredondar ou alterar coordenadas. Copia também os 729 MVTs preparados (117.785.969 bytes); não carrega nem reprocessa o GeoJSON de edificações de 107 MB. Os tiles contêm a projeção/quantização previamente documentada em `public/data/buildings-metadata.json`, com zoom 11–16; a camada visual só aparece a partir de **zoom 13,4**. Não se sobrepõem edificações OSM e Overture: `building-osm` fica oculta.

Os 1.571 POIs vêm de 1.576 elementos reais consultados no OSM. A preparação existente `scripts/android-pois.mjs` reaproveita `scripts/build-pois.mjs`: pontos OSM mantêm as coordenadas; áreas recebem ponto interior determinístico, fora de buracos e dentro do recorte. A deduplicação de nó/área mantém os IDs/tags originais em `source_ids`, `raw_tags` e `source_tags`; foram consolidados cinco pares. Dois elementos com combustível e loja usam a prioridade registrada (`fuel`, `parking`, `health`, `food`, `shops`, `parks`). O empacotador não reclassifica nem deduplica novamente.

`data/metadata.json`, `data/pois-metadata.json` e `data/buildings-metadata.json` preservam consultas, datas de download e do banco, fontes e limitações. A data de download não estabelece atualidade no terreno. As edificações são majoritariamente roofprints de aprendizado de máquina e podem ter omissões/desalinhamento; não são cadastro de imóveis.

## Identidade editável

Edite `android/maps/theme-tokens.json` e os SVGs de `android/maps/icons/{gta,rosa,rdr2}/`. Execute novamente o preparador para gerar os JSONs e PNGs. Ele preserva SVGs já existentes, permitindo edição manual.

**GTA SA:** mantém a paleta aprovada da PoC, as espessuras/hierarquia das vias e a ordenação física de túneis, superfície e pontes, de nível −5 a +5. Em cada nível, todos os contornos precedem todos os preenchimentos, preservando cruzamentos contínuos. Ícones usam geometria Maki 8.2.0 CC0, com contorno branco/preto e cor por categoria, incluindo `grocery` para mercados. Não há texturas, ícones ou fontes extraídos do jogo.

**Rosa Pop:** tecido urbano rosa de doce, comércio pink, indústria lilás, edifícios rosa-branco, parques em menta viva e água turquesa. As vias têm contorno magenta e hierarquia rosa/amarelo/lilás; rótulos usam ameixa com halo claro. Os seis símbolos originais são árvore com flor, bomba arredondada, xícara com coração, estacionamento com estrela, coração de saúde e sacola com laço. Não há personagens, logotipos ou assets oficiais de marcas. Os SVGs Rosa são dedicados a CC0-1.0.

**RDR2:** papel sépia, vegetação oliva, água cinza-esverdeada, edifícios ocre, vias em tinta marrom-escura e vermelho pontual nos símbolos e grandes corredores. Noto Serif Bold dá aos nomes a aparência de atlas impresso. Os seis símbolos originais de caneta representam árvore, bomba, xícara, estacionamento, cruz de saúde e loja, com a mesma semântica dos dados OSM. São dedicados a CC0-1.0. A referência visual consultada é [Charting the Wilds, no guia oficial Piggyback de RDR2](https://www.piggyback.com/us/guide/red-dead-redemption-2/), incluindo a [fotografia oficial do atlas](https://www.piggyback.com/us/wp-content/uploads/sites/6/2020/06/RDR2CE_E_FEATURE-ATLAS.jpg). A referência orienta cores, tinta e tipografia; sua imagem, ícones, nomes e geometria não entram no aplicativo. Não há curvas de nível decorativas, relevo inventado nem POIs ficcionais.

**Aparência noturna do host:** as paletas `gta-night`, `rosa-night` e `rdr2-night` ficam no mesmo arquivo de tokens. GTA reduz o brilho do cinza urbano, edifícios, verdes e azuis, mantendo vias pretas e rótulos claros. Rosa usa lilás e pink luminosos, parques menta e água azul-turquesa, com vias claras; o brilho dos preenchimentos fica abaixo do diurno. RDR2 mantém papel sépia mais discreto, tinta escura e rótulos claros com halo marrom. Os símbolos, arquivos de sprites, glifos, layouts e seis grupos semânticos são compartilhados com a variante diurna de cada tema. A cobertura geográfica não muda. Os testes exigem contraste mínimo de 7:1 entre texto e halo e 3:1 entre a via base e o fundo urbano nas três paletas noturnas.

As cores opacas abaixo são o contrato de renderização usado na inspeção nativa. Antialiasing, cobertura parcial e opacidade de camada podem misturar cores nos pixels finais.

| Variante | Parque | Via principal | Água | Edificação |
| --- | --- | --- | --- | --- |
| GTA SA | `#748837` | `#080A08` | `#7088AB` | `#F7FCF8` |
| GTA SA noite | `#53662D` | `#060805` | `#4B6E91` | `#B2BBAE` |
| Rosa Pop | `#6FE3BD` | `#FFDF79` | `#64D7EB` | `#FFF3FB` |
| Rosa Pop noite | `#41AB99` | `#EFC46F` | `#388EBC` | `#B888BB` |
| RDR2 | `#B5AF88` | `#5C4030` | `#A8B9AD` | `#B6A17A` |
| RDR2 noite | `#777D5A` | `#32271D` | `#718F89` | `#B19B75` |

Os textos nativos usam Noto Sans Bold ou Noto Serif Bold em PBF, com tamanhos um pouco maiores que os da PoC web. Os testes de contraste verificam os tokens; a legibilidade final na central depende de escala, densidade e host.

Sprites são PNGs transparentes de 216×36 (1×) e 432×72 (2×), com seis entradas de 32×32 ou 64×64 e margens entre símbolos. Não são SDF; as cores fazem parte do bitmap. A troca de estilo muda a base de sprite, preservando os mesmos IDs `poi-*`.

## Fontes completamente locais

MapLibre Native precisa de glifos explícitos; o fallback Arial usado pela PoC JavaScript não é suficiente. `GameMapSans` é um alias de caminho do APK para os PBFs **não modificados** de Noto Sans Bold servidos pelo repositório oficial [MapLibre demotiles](https://github.com/maplibre/demotiles/tree/601ae60796ceceda2cbd2ed3d2ea92d17a84be4b/font/Noto%20Sans%20Bold), fixado no commit `601ae60796ceceda2cbd2ed3d2ea92d17a84be4b`. O nome da fonte original e a licença SIL OFL 1.1 permanecem nos créditos; o arquivo binário não é renomeado internamente nem modificado.

Os inputs ficam em `android/maps/fonts/GameMapSans/`. Os blocos `0-255.pbf` e `8192-8447.pbf` cobrem todos os codepoints encontrados nos nomes reais de vias, lugares e POIs, incluindo a conversão para maiúsculas usada nos rótulos. Incluem os acentos portugueses e a aspa curva U+2019. O teste decodifica os IDs dos glifos dos PBFs para conferir cobertura, em vez de verificar apenas a existência de arquivos. A licença original acompanha `LICENSE.md` e a cópia no APK `credits/Noto-OFL-1.1.md`.

RDR2 usa **Noto Serif Bold**, com os dois blocos PBF compatíveis com MapLibre fornecidos pelo [serviço oficial do OpenStreetMap US](https://tiles.openstreetmap.us/fonts/), sem modificação. `GameMapSerif` é somente o alias da pasta local; o nome interno do fontstack continua Noto Serif Bold. A [fonte original pertence ao projeto Noto](https://github.com/notofonts/noto-fonts/tree/main/hinted/ttf/NotoSerif). A licença SIL OFL 1.1 foi obtida do [repositório oficial Noto](https://raw.githubusercontent.com/notofonts/noto-fonts/main/LICENSE), acompanha `android/maps/fonts/GameMapSerif/LICENSE.md` e `credits/Noto-Serif-OFL-1.1.md`. A obtenção inicial destes inputs exigiu rede; a preparação normal usa apenas os arquivos incluídos e não adiciona dependências.

| Input serifado | URL original | SHA-256 |
| --- | --- | --- |
| `0-255.pbf` | [OSM US Noto Serif Bold 0–255](https://tiles.openstreetmap.us/fonts/Noto%20Serif%20Bold/0-255.pbf) | `76f0ac6cbf7e5289be2e1a47472b686efa7b6085099fd0fe3446750d74a78fc1` |
| `8192-8447.pbf` | [OSM US Noto Serif Bold 8192–8447](https://tiles.openstreetmap.us/fonts/Noto%20Serif%20Bold/8192-8447.pbf) | `9c1d14ddf7cb0313e09350438ce7e90fca409bb27a422a82a1d05f774f50ed18` |
| `LICENSE.md` | [Licença oficial Noto](https://raw.githubusercontent.com/notofonts/noto-fonts/main/LICENSE) | `0dab92d0544f7b233403f14b84a663bdbfa746982eda629e7f4f9ffe1b036feb` |

Os hashes fixam os inputs incluídos mesmo se o provedor atualizar seu endpoint. `credits/sources.json` registra os mesmos URLs, hashes e a referência visual; o inventário também registra os hashes dos inputs e das cópias no APK. A cobertura de todos os nomes e variantes maiúsculas é verificada para **ambas** as fontes.

Se uma atualização introduzir outro bloco Unicode, o preparador termina com erro ao procurar esse input. Baixe o novo bloco no mesmo commit e confira a licença/origem antes de executar novamente. Os URLs de referência aparecem somente nos créditos/proveniência; não há endpoint remoto em `glyphs`, `sprite`, `sources.data` ou `sources.tiles`.

## Preparação e atualização

Com Node e as dependências do projeto instalados:

```powershell
node scripts/prepare-android-maps.mjs
node --test tests/android-native-assets.test.mjs
```

A preparação padrão funciona offline com os inputs incluídos e não altera arquivos web, Kotlin, Gradle nem `maps/probe-style.json`. O pacote gerado contém aproximadamente **131,9 MiB antes da compressão do APK**, dos quais cerca de 112,3 MiB são os MVTs de edificações já existentes. Os seis JSONs compartilham todos os recursos geográficos e usam três atlas de sprites e dois fontstacks locais. O tamanho final do APK depende da compressão e ABIs nativas incluídas.

Para atualizar POIs com rede, execute `node scripts/android-pois.mjs --fetch` e depois o preparador. Para reprocessar somente o cache já obtido: `node scripts/android-pois.mjs`. A atualização do mapa base segue `scripts/fetch-osm.mjs` e `scripts/build-geodata.mjs`; a de edifícios segue os scripts Overture e `scripts/tile-buildings.mjs` já existentes. Reavalie contagens, cobertura e inspeção visual após mudanças de dados.

`asset-manifest.json` contém tamanho e SHA-256 de todos os recursos gerados/copied, hashes dos inputs de estilo/ícones/fontes, e `sourcePath`/`sourceSha256` para os arquivos copiados. É determinístico: não acrescenta timestamp de execução. O próprio manifesto fica fora da sua lista de hashes. O arquivo de probe e recursos fora destas seis pastas não entram nesse inventário.

## Atribuição e validação

A atribuição comum é **© OpenStreetMap contributors · Overture Maps Foundation · Google Open Buildings · Microsoft Global ML Building Footprints**. Está em `metadata.gamemap.attribution`, no source vetorial e nos créditos HTML/JSON locais. A interface deve exibir a atribuição curta e permitir consultar os créditos. Uma Surface desenhada diretamente exige que a integração apresente os créditos pelos componentes permitidos do host; os metadados do JSON sozinhos não criam essa interface.

Os testes verificam esquema dos seis estilos, fontes geográficas idênticas entre temas, classificação única dos 1.571 POIs, independência das áreas verdes, cópia byte a byte dos dados, SHA-256 de todos os 729 MVTs, cobertura de glifos nas duas fontes, dimensões/índices dos três atlas e preservação da paleta/hierarquia GTA. Os testes de aparência exigem Rosa rosa/lilás com parques menta e água turquesa, RDR2 com vias escuras em papel sépia, paletas diurnas distintas e as mesmas camadas/layouts entre dia e noite. Um teste aplica os filtros aos 23.075 segmentos reais de vias em cada variante e confere que cada um corresponde exatamente à sua camada física, incluindo túneis e pontes; também verifica a inclusão do polígono real da Praça Cívica no preenchimento verde. A inspeção visual final dos mapas no mesmo enquadramento, texto no Native, alternância no DHU e central real deve ser registrada pela integração separadamente. Aprovar o esquema MapLibre GL não prova compatibilidade de todas as expressões no Native.

Na captura nativa, espere o processamento dos GeoJSONs e a renderização de um frame completo. O primeiro frame pode apresentar somente fontes menores e fundo enquanto vias e áreas ainda carregam. O critério visual deve reconhecer feições esperadas, como o preenchimento da Praça Cívica e vias contínuas, além de sprites/textos; uma simples contagem de cores pode passar com antialiasing de um único círculo.
