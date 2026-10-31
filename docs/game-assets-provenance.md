# Fontes e sprites dos jogos · NavSkin 0.10

Esta versão combina arquivos de fonte com origem documentada, uma reconstrução da tipografia SMW, exports públicos de sprites e arte própria NavSkin. Os cinco temas ativos são GTA SA, Rosa Pop, RDR2, Super Mario World e Cyberpunk 2077; os dois últimos são as adições da 0.10. Minecraft e GTA V foram retirados da lista de temas a empacotar.

O catálogo ativo tem **seis arquivos de fonte e quatro perfis temáticos**; Rosa Pop mantém sistema/Noto. A licença do código NavSkin e a dos conversores não se estendem à arte dos jogos. Copyrights, contratos históricos e registros de origem acompanham os recursos; nenhum arquivo foi declarado CC0 por ter sido convertido.

## Fontes selecionadas

| Uso no NavSkin | Família / nome PostScript | Versão e origem |
|---|---|---|
| Título do tema e localidades GTA SA | Pricedown / `Pricedown` | Ray Larabie, `3.200 2004`; [TTF histórico](https://www.princexml.com/fonts/larabie2/pricedow.ttf) e [contrato correspondente](https://www.princexml.com/fonts/larabie2/read_me.html) |
| Ruas e POIs GTA SA | Bank Gothic Medium / `BankGothicBT-Medium` | Arquivo publicado pelo [MTA:SA](https://github.com/multitheftauto/mtasa-blue/blob/master/Shared/data/MTA%20San%20Andreas/MTA/cgui/sabankgothic.ttf), copyright Bitstream. Escolha de legibilidade, sem afirmar equivalência ao mapa original. |
| Título do tema e POIs RDR2 | Hapna Slab Serif DemiBold / `HapnaSlabSerif-DemiBold` | Mariya V. Pigoulevskaya, The Northern Block, `1.10`; conversão extraída atribuída a Peter1501 |
| Vias e localidades RDR2 | RDR Lino / `RDRLino-Regular` | Rockstar Games, Euan Duncan, `3.000`; mesmo [pack público de conversões](https://www.worldofmods.com/rdr-2/tools/56923-rdr-2-font-russian-cyrillic-latin.html) |
| Super Mario World | SMW Whole-Pixel Spacing / `SMWWhole-PixelSpacing` | [Reconstrução v1.1 de Eric Kaiser/wye](https://github.com/vvye/smw-font/releases/tag/v1.1), CC BY-NC 4.0 |
| Cyberpunk 2077 | Blender Pro Bold / `BlenderPro-Bold` | [WOFF do site oficial](https://www.cyberpunk.net/build/fonts/BlenderPro-Bold-c11a01ca.woff), convertido em TTF com FontTools 4.68.0, mantendo nomes e contornos |

Blender Pro Bold foi publicada no site oficial Cyberpunk; não foi extraída dos arquivos do jogo. SMW é uma reconstrução do autor, e não um binário de fonte Nintendo. As conversões não fornecem licença de redistribuição. Os hashes dos seis arquivos ativos, URLs, notas, avisos e transformações ficam em `android/maps/game-assets/provenance.json` e no pacote local. Minecraft Seven e Chalet não integram o catálogo ativo. [Decisões da versão 0.10](maps-v0.10.md).

SHA-256 dos TTFs selecionados:

```text
Pricedown  973c5294ea6e0fe07e58b986b38c0393244bc835cc65889ca9f7b39b8c89ba1e
Bank Gothic 3a0e078160d7a0e9d0d17c246ba3937ebe4ae264c0a19955f59b2557c4500a95
Hapna      468a87efc589dc45ab1142b28083d8a109192d53af619a7598921e861157ff64
RDR Lino   f1c1d83487c9607e5d9fb22ba6fc74464947134302ceb97aabb4a5e2bafea99f
SMW        9d3318585c695fb22e8ef9e5d5f4908fac67b0734871addc31ed2d00f4aa4bc3
Blender    f4feaaa78d44a180432f6fe79564b14a0110a70a0c15feb559ef9aac74ad3262
```

O [autor da Pricedown](https://typodermicfonts.com/between-the-lines/stories/pricedown/) confirma seu uso pela Rockstar e a mudança do nome do estilo original para Pricedown Black. O arquivo selecionado é a edição histórica, com seu próprio contrato, e não a edição desktop de 2026. O contrato histórico contém condições de redistribuição/conversão e sua FAQ pede contato antes de vender software com as fontes; esses textos foram preservados. A cópia anterior 3.1/2001 foi conferida no [pacote Debian](https://deb.debian.org/debian/pool/non-free/f/fonts-larabie/fonts-larabie_20011216.orig.tar.bz2) e no [host que declara permissão do autor](https://www.princexml.com/fonts/larabie/), com SHA-256 `8bdcc5e040b7b437e03eae1ed06de25938dfd30945a0bbc95130fcfc8c14b1fb`. Avisos diferentes do pacote antigo também foram mantidos.

Pricedown é uma fonte real da série. Desde a 0.9, a interface Vidro usa a tipografia nativa do sistema; desde a 0.10, ruas e POIs GTA SA usam Bank Gothic. San Andreas tem estilos distintos de menu, gótico, subtítulos e Pricedown, documentados em [Font.cpp](https://github.com/gta-reversed/gta-reversed/blob/master/source/game_sa/Font.cpp).

O pack RDR2 e seu README de 20/11/2019 descrevem conversão a partir do jogo. Os metadados mantêm os autores e copyrights acima. São conversões extraídas, sem verificação de igualdade binária com os arquivos distribuídos no jogo. O pacote não fornece autorização de redistribuição externa verificada. Seu ZIP tem SHA-256 `311790b203504048947174d63732713f48650e7760e6b303ffd144e6c74a3608`. A escolha de RDR Lino para vias/localidades não afirma que ela era a fonte geográfica exata do mapa original. Chinese Rocks não substitui Hapna nessa interface.

## Sprites e adaptações

- GTA SA: 65 exports PNG do `models/hud.txd`, incluindo 61 `radar_*` em 16×16, disponíveis no [catálogo GTAMidori](https://gtamidori.com/textdrawsprites). Os nomes originais são preservados, inclusive `radar_hostpital.png`.
- RDR2: 321 exports do dicionário `BLIPS`, listados pelo [pesquisador femga](https://github.com/femga/rdr3_discoveries/blob/master/useful_info_from_rpfs/textures/blips/README.md). O [ZIP transparente](https://femga.com:8080/images/samples/ui_textures_no_bg/blips.zip) tem SHA-256 `55c81b6e4f8ae9c59b35f31dea69e110af1dfda4ebdfcff6112e0dfb61cbd6ba`.

Sprites originais são usados nos subtipos equivalentes, como restaurante, bar, hospital/serviço médico, loja e teatro, conforme o mapeamento de importação. Os pixels são preservados; a ampliação GTA para 32×32 usa vizinho mais próximo. Parques, combustível, educação, cultura, religião e outras categorias sem equivalente específico recebem adaptações desenhadas pelo NavSkin. Esses desenhos novos não são apresentados como arte extraída da Rockstar. A disponibilidade de um export público não muda seu copyright Rockstar.

Os nove filtros agrupam parques, combustível, alimentação, estacionamento, saúde, mercados/conveniências, educação, cultura e locais de culto. Os IDs semânticos distinguem as categorias e seus subtipos. Os marcadores de veículo e a arte própria NavSkin mantêm sua procedência separada.

Os dois temas novos da 0.10 usam ícones e materiais cartográficos próprios: pixel art de overworld para SMW e linhas de terminal/neon para Cyberpunk. Não são apresentados como sprites ou texturas extraídos desses jogos. Os desenhos usam a geografia real como base; os materiais e efeitos de contorno são adaptações visuais.

O marcador padrão GTA SA (`vehicleId: arrow`) usa o sprite original `radar_centre` de `models/hud.txd`, [exportado pelo GTAMidori](https://gtamidori.com/textdrawsprites/hud.txd/radar_centre.png). Representa o jogador no radar, a pé ou em veículo; `radar_north` é a letra N da bússola. O PNG original 16×16 tem SHA-256 `2a7f822a3493cc0840823bc6d241ec84b2a220fafe3235637ad721dd3a7c237e`. O preparo acrescenta 4 pixels transparentes em cada lado e amplia por vizinho mais próximo para 48×48 e 96×96, preservando pixels e âncora central. Origem, hash e transformação acompanham `navigation-icons/provenance.json`. O original e seus derivados estão versionados com seus avisos próprios e permanecem excluídos da licença CC0. As demais escolhas e seus IDs continuam preservados.

## Reproduzir a importação local

O manifesto de entrada padrão é `android/maps/game-assets/source/import-manifest.json`, com caminhos relativos à sua pasta. Fontes, notices, sprites de origem e o conversor acompanham o clone. O importador seleciona seis registros ativos: Pricedown, Bank Gothic, Hapna, RDR Lino, SMW e Blender. Os campos `fonts`, `extraFonts` e `themes` fornecem caminhos, nomes internos, SHA-256, `licenseFiles`, aliases e perfis; eventuais entradas Minecraft/GTA V são filtradas. O importador rejeita o manifesto antigo de três fontes antes de alterar arquivos. Manifestos locais antigos ainda podem ser passados explicitamente, mas não são necessários no outro computador.

O conversor é o [font-maker oficial MapLibre](https://github.com/maplibre/font-maker), também disponível na [aplicação oficial](https://maplibre.org/font-maker/). Seus arquivos públicos são [sdfglyph.js](https://maplibre.org/font-maker/sdfglyph.js) e [sdfglyph.wasm](https://maplibre.org/font-maker/sdfglyph.wasm); as cópias usadas pelo pipeline estão em `android/maps/game-assets/source/font-maker/`, com seu registro de origem.

Com esses arquivos e o pacote de origem disponíveis, execute na raiz:

```sh
npm ci
npm run assets:game
node scripts/prepare-android-maps.mjs
node scripts/prepare-navigation-icons.mjs
node scripts/prepare-ios.mjs
```

`prepare-game-assets.mjs` confere os hashes das fontes, copia avisos, gera glyphs SDF PBF reais e prepara sprites/adaptações. Produz `android/maps/game-assets/catalog.json`, `provenance.json`, fontes locais e os stacks `NavSkinGtaRoad`, `NavSkinGtaDisplay`, `NavSkinRdrUi`, `NavSkinRdrMap`, `NavSkinSmw` e `NavSkinCyberpunk`. Rosa Pop mantém a fonte de fallback. A preparação Android reúne os recursos; a etapa iOS copia os recursos compartilhados e atualiza o projeto Xcode. O pacote-fonte iOS já preparado dispensa essa regeneração para abrir no Mac.

Nos quatro temas com fonte própria, a fonte selecionada tem prioridade por caractere. Noto Sans Bold entra apenas para glifos ausentes e blocos adicionais de outros idiomas; não substitui os caracteres presentes na fonte selecionada. PBFs guardam glyphs e métricas, sem transportar a tabela de kerning do TTF. A [especificação MapLibre](https://maplibre.org/maplibre-style-spec/glyphs/) descreve o carregamento por stack e intervalo Unicode.

As fontes temáticas alcançam os títulos de prévias e o mapa; os controles Vidro usam a fonte do sistema. No Android Auto, o mapa é desenhado pelo NavSkin; tipografia e controles dos templates oficiais são controlados pelo host.

Os arquivos originais e seus derivados acompanham o repositório para permitir continuar o desenvolvimento em outro computador. Seus avisos e licenças permanecem separados da licença do código. O versionamento não transfere direitos dos titulares nem comprova autorização para distribuição comercial ou publicação na Play Store.
