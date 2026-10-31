# NavSkin 0.10.0 · cinco temas

Abra **⋯ → Temas** para escolher GTA SA, Rosa Pop, RDR2, **Super Mario World** ou **Cyberpunk 2077**. O carrossel usa cinco capturas reais do renderizador Android. Cada tema tem versões dia/noite para **Mundo online** e **Goiânia offline**: 20 estilos ao todo.

**Minecraft e GTA V foram retirados desta entrega a pedido do usuário.** Suas paletas e seus SVGs permanecem como rascunhos no projeto; eles não aparecem nos seletores, nas tabelas de recursos atuais nem nos catálogos ativos.

| Tema novo | Direção visual |
|---|---|
| Super Mario World | Terrenos verdes com bordas e faces laterais, telhados em tijolos, água com ondas pixeladas, caminhos claros/pontilhados e sprites coloridos de colinas, cogumelos, canos, blocos e castelos |
| Cyberpunk 2077 | Construções vermelhas com linhas arquitetônicas, fundo quase preto, vias azuis com bordas ciano e pictogramas de terminal em branco/ciano/amarelo |

Os mapas representam a geografia real existente. Estilos mudam cores, fontes e símbolos; não inventam construções ou modificam as vias. Filtros, câmera, destino, paradas e navegação continuam ativos ao trocar o tema. Casa, Trabalho, favoritos e veículo conservam seus IDs e recebem a representação correspondente ao novo estilo. São 12 ícones de locais salvos e seis veículos por tema.

Cada tema tem regras próprias de desenho para áreas, materiais, contornos e ruas, além da paleta. Faces laterais são ilustração sobre os polígonos reais, não alturas medidas. [Referências cartográficas](design/game-cartography-references.md), [registro de desenho dos sprites](design/new-theme-icon-references.md) e [comparação dos dois novos mapas](../android/screenshots/v0.10/two-new-themes.png). Os registros de pesquisa também conservam os estudos retirados.

No recorte de Goiânia offline, parques e água usam preenchimentos sólidos com os contornos do tema. Isso evita uma falha do renderizador nativo ao alternar de um tema sem textura para outro com textura em polígonos GeoJSON. Os prédios vetoriais e o mapa mundial mantêm seus materiais.

## GTA SA mais legível

Ruas e locais usam **Bank Gothic Medium**, com letras mais abertas, tamanho maior, espaçamento moderado e contorno mais fino. **Pricedown permanece nos nomes maiores de localidades e no título do tema.** A fonte não força a transformação dos nomes em caixa alta; a própria Bank Gothic desenha várias letras como versaletes. Legendas de POIs respeitam o zoom de entrada, para não competir com todas as ruas de uma vez. A interface Vidro continua com a fonte nativa do sistema.

## Origem das fontes e desenhos

| Recurso | Origem e natureza |
|---|---|
| Bank Gothic Medium | Arquivo `sabankgothic.ttf` publicado no [repositório oficial do MTA:SA](https://github.com/multitheftauto/mtasa-blue/blob/master/Shared/data/MTA%20San%20Andreas/MTA/cgui/sabankgothic.ttf). Adaptação cartográfica para leitura; não afirma ser a fonte exata do mapa original. Copyright Bitstream preservado. |
| SMW Whole-Pixel Spacing | [Reconstrução de Eric Kaiser/wye](https://www.eric-kaiser.net/project/super-mario-world-font), [versão 1.1](https://github.com/vvye/smw-font/releases/tag/v1.1), com ajustes do autor e licença CC BY-NC 4.0. Não é uma extração binária da ROM. |
| Blender Pro Bold | WOFF usado pelo [site oficial Cyberpunk 2077](https://www.cyberpunk.net/us/en/), convertido em TTF com FontTools, mantendo nomes internos e contornos. É a família do site; não foi extraída dos arquivos do jogo. |

Os dois conjuntos novos de ícones são desenhos NavSkin inspirados nos estilos, não sprites extraídos dos jogos. Os recursos originais GTA SA/RDR2 já existentes conservam sua procedência separada. O pacote local contém fontes, avisos, URLs, hashes dos arquivos de origem e conversões; a licença do código não altera a dos recursos de terceiros. Veja [procedência e reprodução](game-assets-provenance.md) e [catálogos](navigation-icon-catalogs.md).

## Entrega e validação

O APK Android atualiza a instalação anterior com a mesma assinatura debug. O AAB é um bundle de desenvolvimento e não se instala diretamente. O projeto iOS reúne os mesmos estilos, fontes, ícones e cinco prévias; deve ser compilado pelo amigo no Mac conforme o [README iOS](../ios/README.md#para-quem-vai-compilar-no-mac).

A revisão de cinco temas passou 191 testes JVM, 10 testes Android instrumentados, 74 testes de recursos compartilhados e 123 testes Swift Core. O APK e o AAB foram gerados; a compilação Apple permanece para o Mac.

Capturas e verificações Android: [validação 0.10](../android/docs/VALIDATION-v0.10.md). Validação de código/recursos Swift e limites do ambiente Windows: [relatório iOS](../ios/docs/VALIDATION.md). A expansão visual não modifica a distribuição Android Auto nem acrescenta CarPlay.
