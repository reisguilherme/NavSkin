# Referências e revisão dos ícones SMW / Minecraft

Revisão de 29/10/2026, após o pedido de aproximar os temas da linguagem visual dos jogos. Esta revisão substitui as primeiras placas genéricas por sprites independentes em fundo transparente. Os SVGs foram desenhados para o NavSkin; não são arquivos extraídos dos jogos nem incluem imagens remotas.

## Referências primárias consultadas

- [Nintendo — Super Mario World, Mario Portal](https://www.nintendo.com/jp/character/mario/history/world/index.html). A página apresenta as transformações e itens do jogo. A [imagem oficial do Super Mushroom](https://www.nintendo.com/jp/character/mario/history/world/images/mv_item02.jpg) foi inspecionada visualmente no navegador: contorno escuro de um pixel, chapéu vermelho com manchas claras, base creme, formas feitas de degraus de pixels e contraste forte, sem um contêiner de interface ao redor.
- [Nintendo — manual de Super Mario World](https://www.nintendo.co.jp/clvs/manuals/common/pdf/CLV-P-SAAAE.pdf). Referência complementar para a relação entre itens, personagens e o mundo do jogo.
- [Mojang — Taking Inventory: Potion](https://www.minecraft.net/en-us/article/taking-inventory--potion). A [captura oficial da bancada de poções](https://www.minecraft.net/content/dam/minecraftnet/games/minecraft/screenshots/potion-ingame.jpg) foi inspecionada no navegador. Observação visual: gargalo estreito, rolha, vidro claro, conteúdo colorido, brilho separado em poucos pixels e silhueta própria do item.
- [Mojang — Block of the Week: Chest](https://www.minecraft.net/en-us/article/block-week-chest). A [captura oficial de baús](https://www.minecraft.net/content/dam/minecraftnet/archive/17635a2e65317c6c7a89537558ef55d3-cheststorage.jpg) foi inspecionada no navegador: madeira ocre, separação escura da tampa, trava metálica e diferenças claras de luminosidade entre faces.
- [Mojang — Taking Inventory: Minecart](https://www.minecraft.net/en-us/article/taking-inventory--minecart). A [captura oficial do inventário e receita](https://www.minecraft.net/content/dam/minecraftnet/minecraft/taking-inventory/minecart/ingame.jpg) foi inspecionada no navegador: borda metálica clara, interior escuro e variantes com carga. A orientação dos veículos do NavSkin continua apontando para o norte; os carrinhos foram adaptados para vista superior para manter a leitura da direção.
- [Minecraft Education — catálogo de blocos](https://education.minecraft.net/lessonsupportfiles/Minecraft-block-items.pdf). Referência complementar para a distinção entre materiais e objetos, incluindo madeira, baú, mesa de trabalho e fornalha.

## Decisões aplicadas

SMW usa contorno quase preto, paleta saturada, degraus em pixels e grandes manchas de cor. Parques viraram colinas com olhos; combustível, um cano verde com gota; saúde, um cogumelo vermelho; lojas, um bloco de interrogação; estacionamento, um interruptor azul com P; cultura, um castelo; educação, um livro; religião, uma estrela. Os subtipos continuam diferentes: café em caneca, comida rápida em sanduíche, farmácia em flor, clínica em coração e teatro em máscaras. Favoritos recebem casa-cogumelo, martelo, coração, ovo e estrela. Os veículos seguem a linguagem de karts; o veículo temático é uma montaria verde desenhada para o app.

Minecraft usa itens coloridos sobre transparência, sombreamento por faces, rampas curtas de cor e textura distribuída em pixels. Parques usam árvore blocada; combustível, fornalha acesa; comida, maçã; saúde, poção; lojas, baú; educação, livro; cultura, pintura; religião, sino. Subtipos incluem pão, carne, poções distintas, maçã dourada, estante e jukebox. Favoritos usam cama, mesa de trabalho, diamante, tigela, picareta, trilhos, esmeralda e pérola. Os veículos possuem metal, interior escuro e variantes de carga com vista superior.

Os motivos remetem aos jogos. As adaptações de combustível, estacionamento, saúde e veículos são símbolos cartográficos do NavSkin e não alegam existir como os mesmos itens nos jogos. Nomes de franquias e elementos reconhecíveis continuam associados aos respectivos titulares. O catálogo mantém a autoria declarada como `navskin-authored`; nenhuma alegação de arte original de Nintendo ou Mojang foi adicionada.

## Limites e conferência

- Mantidos os nove grupos, onze subtipos de POI, a seta de trânsito, os 12 IDs de locais salvos e os seis IDs de veículos.
- SVGs de mapa: 32 × 32. SVGs de navegação: 64 × 64 para rasterização existente em 48 × 48 e 96 × 96.
- Desenhos produzidos em uma grade inteira de 32 pixels, exportados como retângulos SVG com `shape-rendering="crispEdges"`.
- Margens, presença de pixels e centro de rotação dos veículos conferidos pelo teste `tests/four-game-maps.test.mjs`.
- Folhas temporárias de inspeção: `.tmp/four-themes/pixel-redesign-contact.png` e `.tmp/four-themes/pixel-redesign-native-sizes.png`.
- Nenhuma geometria de mapa, coordenada ou classificação de POI foi alterada nesta revisão dos ícones.

O utilitário de autoria da revisão permanece em `.runtime/redraw-pixel-theme-icons.mjs` como apoio local; os SVGs em `android/maps/icons/{smw,minecraft}` e `android/maps/catalogs/{smw,minecraft}` são as fontes consumidas pelos geradores existentes.
# Estado da entrega

Somente os ícones Super Mario World desta pesquisa integram a versão 0.10.0. Minecraft foi retirado a pedido do usuário; seus SVGs permanecem como rascunhos fora dos recursos do aplicativo.

