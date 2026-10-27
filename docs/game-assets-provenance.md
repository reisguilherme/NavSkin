# Fontes e sprites dos jogos · NavSkin 0.7

Esta versão usa arquivos de famílias autênticas e exports públicos de sprites no pacote local solicitado. A licença do código NavSkin e a dos conversores não se estendem à arte dos jogos. Copyrights, contratos históricos e registros de origem acompanham os recursos; nenhum arquivo foi declarado CC0 por ter sido convertido.

## Fontes selecionadas

| Uso no NavSkin | Família / nome PostScript | Versão e origem |
|---|---|---|
| Interface e rótulos GTA SA | Pricedown / `Pricedown` | Ray Larabie, `3.200 2004`; [TTF histórico](https://www.princexml.com/fonts/larabie2/pricedow.ttf) e [contrato correspondente](https://www.princexml.com/fonts/larabie2/read_me.html) |
| Interface e POIs RDR2 | Hapna Slab Serif DemiBold / `HapnaSlabSerif-DemiBold` | Mariya V. Pigoulevskaya, The Northern Block, `1.10`; conversão extraída atribuída a Peter1501 |
| Vias e localidades RDR2 | RDR Lino / `RDRLino-Regular` | Rockstar Games, Euan Duncan, `3.000`; mesmo [pack público de conversões](https://www.worldofmods.com/rdr-2/tools/56923-rdr-2-font-russian-cyrillic-latin.html) |

SHA-256 dos TTFs selecionados:

```text
Pricedown  973c5294ea6e0fe07e58b986b38c0393244bc835cc65889ca9f7b39b8c89ba1e
Hapna      468a87efc589dc45ab1142b28083d8a109192d53af619a7598921e861157ff64
RDR Lino   f1c1d83487c9607e5d9fb22ba6fc74464947134302ceb97aabb4a5e2bafea99f
```

O [autor da Pricedown](https://typodermicfonts.com/between-the-lines/stories/pricedown/) confirma seu uso pela Rockstar e a mudança do nome do estilo original para Pricedown Black. O arquivo selecionado é a edição histórica, com seu próprio contrato, e não a edição desktop de 2026. O contrato histórico contém condições de redistribuição/conversão e sua FAQ pede contato antes de vender software com as fontes; esses textos foram preservados. A cópia anterior 3.1/2001 foi conferida no [pacote Debian](https://deb.debian.org/debian/pool/non-free/f/fonts-larabie/fonts-larabie_20011216.orig.tar.bz2) e no [host que declara permissão do autor](https://www.princexml.com/fonts/larabie/), com SHA-256 `8bdcc5e040b7b437e03eae1ed06de25938dfd30945a0bbc95130fcfc8c14b1fb`. Avisos diferentes do pacote antigo também foram mantidos.

Pricedown é uma fonte real da série; aplicá-la à interface inteira é uma escolha desta versão. San Andreas tem estilos distintos de menu, gótico, subtítulos e Pricedown, documentados em [Font.cpp](https://github.com/gta-reversed/gta-reversed/blob/master/source/game_sa/Font.cpp).

O pack RDR2 e seu README de 20/11/2019 descrevem conversão a partir do jogo. Os metadados mantêm os autores e copyrights acima. São conversões extraídas, sem verificação de igualdade binária com os arquivos distribuídos no jogo. O pacote não fornece autorização de redistribuição externa verificada. Seu ZIP tem SHA-256 `311790b203504048947174d63732713f48650e7760e6b303ffd144e6c74a3608`. A escolha de RDR Lino para vias/localidades não afirma que ela era a fonte geográfica exata do mapa original. Chinese Rocks não substitui Hapna nessa interface.

## Sprites e adaptações

- GTA SA: 65 exports PNG do `models/hud.txd`, incluindo 61 `radar_*` em 16×16, disponíveis no [catálogo GTAMidori](https://gtamidori.com/textdrawsprites). Os nomes originais são preservados, inclusive `radar_hostpital.png`.
- RDR2: 321 exports do dicionário `BLIPS`, listados pelo [pesquisador femga](https://github.com/femga/rdr3_discoveries/blob/master/useful_info_from_rpfs/textures/blips/README.md). O [ZIP transparente](https://femga.com:8080/images/samples/ui_textures_no_bg/blips.zip) tem SHA-256 `55c81b6e4f8ae9c59b35f31dea69e110af1dfda4ebdfcff6112e0dfb61cbd6ba`.

Sprites originais são usados nos subtipos equivalentes, como restaurante, bar, hospital/serviço médico, loja e teatro, conforme o mapeamento de importação. Os pixels são preservados; a ampliação GTA para 32×32 usa vizinho mais próximo. Parques, combustível, educação, cultura, religião e outras categorias sem equivalente específico recebem adaptações desenhadas pelo NavSkin. Esses desenhos novos não são apresentados como arte extraída da Rockstar. A disponibilidade de um export público não muda seu copyright Rockstar.

Os nove filtros agrupam parques, combustível, alimentação, estacionamento, saúde, mercados/conveniências, educação, cultura e locais de culto. Os IDs semânticos distinguem as categorias e seus subtipos. Os marcadores de veículo e a arte própria NavSkin mantêm sua procedência separada.

O marcador padrão GTA SA (`vehicleId: arrow`) usa o sprite original `radar_centre` de `models/hud.txd`, [exportado pelo GTAMidori](https://gtamidori.com/textdrawsprites/hud.txd/radar_centre.png). Representa o jogador no radar, a pé ou em veículo; `radar_north` é a letra N da bússola. O PNG original 16×16 tem SHA-256 `2a7f822a3493cc0840823bc6d241ec84b2a220fafe3235637ad721dd3a7c237e`. O preparo acrescenta 4 pixels transparentes em cada lado e amplia por vizinho mais próximo para 48×48 e 96×96, preservando pixels e âncora central. Origem, hash e transformação acompanham `navigation-icons/provenance.json`. O original e seus derivados permanecem no pacote local separado, excluídos da licença CC0. As demais escolhas e seus IDs continuam preservados.

## Reproduzir a importação local

O manifesto de entrada padrão é `.runtime/font-research/import-manifest.json`. Ele contém `fonts.gta`, `fonts.rdr2ui`, `fonts.rdr2map`, caminhos locais, nomes internos, SHA-256, `licenseFiles` e diretórios de sprites. As notas de cada família, o README original RDR2 e o manifesto por arquivo ficam junto do pacote local.

O conversor é o [font-maker oficial MapLibre](https://github.com/maplibre/font-maker), também disponível na [aplicação oficial](https://maplibre.org/font-maker/). Seus arquivos públicos são [sdfglyph.js](https://maplibre.org/font-maker/sdfglyph.js) e [sdfglyph.wasm](https://maplibre.org/font-maker/sdfglyph.wasm); o pipeline local espera o JavaScript salvo como `.runtime/font-maker/sdfglyph.mjs` e o WASM como `.runtime/font-maker/sdfglyph.wasm`.

Com esses arquivos e o pacote de origem disponíveis, execute na raiz:

```sh
npm ci
node scripts/prepare-game-assets.mjs .runtime/font-research/import-manifest.json
node scripts/prepare-android-maps.mjs
node scripts/prepare-ios.mjs
```

`prepare-game-assets.mjs` confere os hashes das fontes, copia avisos, gera glyphs SDF PBF reais e prepara sprites/adaptações. Produz `android/maps/game-assets/catalog.json`, `provenance.json`, fontes locais e os stacks `NavSkinGtaRoad`, `NavSkinGtaDisplay`, `NavSkinRdrUi` e `NavSkinRdrMap`. A preparação Android reúne os recursos; a etapa iOS copia os recursos compartilhados e atualiza o projeto Xcode. O pacote-fonte iOS já preparado dispensa essa regeneração para abrir no Mac.

Nos temas GTA/RDR2, a fonte selecionada tem prioridade por caractere. Noto Sans Bold entra apenas para glifos ausentes e blocos adicionais de outros idiomas; não substitui os caracteres presentes na fonte do jogo. PBFs guardam glyphs e métricas, sem transportar a tabela de kerning do TTF. A [especificação MapLibre](https://maplibre.org/maplibre-style-spec/glyphs/) descreve o carregamento por stack e intervalo Unicode.

As fontes temáticas alcançam a interface renderizada pelo aplicativo e o mapa. No Android Auto, o mapa é desenhado pelo NavSkin; tipografia e controles dos templates oficiais são controlados pelo host.

O pacote original e seus derivados locais permanecem separados do código versionado, com os avisos correspondentes. O preparo técnico não transfere direitos dos titulares nem representa uma decisão de publicar os binários.
