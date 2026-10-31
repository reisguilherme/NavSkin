# NavSkin · Map Lab

Painéis HTML interativos para analisar o recorte de Goiânia no computador. O catálogo principal agora reúne oito estilos, todos com 2D/3D; a navegação inicia em 3D e lembra sua escolha separadamente da exploração. Pip-Boy, Hollow Knight e Crossy Road também foram incorporados aos recursos e fontes Android/iOS. Os recursos aprovados estão incluídos no APK 0.11.0; consulte [Vibing e instalação](../../docs/vibing-v0.11.md).

## Abrir

Na raiz do projeto, dê dois cliques em **Abrir-paineis-NavSkin.cmd**. Ele inicia um servidor local discreto e abre o navegador. Requer o Node.js e as dependências já instaladas neste projeto.

Também é possível executar na raiz:

```powershell
node prototypes/navskin-map-lab/server.mjs
```

Abra **http://127.0.0.1:4176/**. Nesse modo, Ctrl+C encerra o servidor. Os HTMLs dependem desse servidor para ler os dados locais: abrir diretamente com file:// não carrega mapas e fontes.

## Painéis

- [markers-3d.html](http://127.0.0.1:4176/markers-3d.html): garagem dos oito avatares 3D, com giro manual/automático, três inclinações, amostras no tamanho do app e links para testar cada um na navegação.
- [novos-mapas.html](http://127.0.0.1:4176/novos-mapas.html): **Pip-Boy, Hollow Knight e Crossy Road**, três mapas interativos lado a lado. Permite ampliar cada recorte, escolher Bosque dos Buritis / Praça Universitária / Praça Cívica, mostrar lugares e nomes, girar e aproximar. Os três permitem alternar 2D/3D.
- [index.html](http://127.0.0.1:4176/index.html): explorar oito desenhos, dia/noite, perspectiva 2D/3D, categorias e três enquadramentos de Goiânia.
- [comparar.html](http://127.0.0.1:4176/comparar.html): desenho atual do app e proposta lado a lado, com câmeras sincronizadas.
- [navegacao.html](http://127.0.0.1:4176/navegacao.html): trajeto demonstrativo com câmera inclinada, volumes translúcidos e contornos luminosos. Permite ajustar inclinação, transparência, altura ilustrativa, pausar, arrastar o percurso e recentralizar.
- Atalhos por tema: [gta.html](http://127.0.0.1:4176/gta.html), [rdr2.html](http://127.0.0.1:4176/rdr2.html), [rosa.html](http://127.0.0.1:4176/rosa.html), [smw.html](http://127.0.0.1:4176/smw.html), [cyberpunk.html](http://127.0.0.1:4176/cyberpunk.html).

- [pink-icons/index.html](http://127.0.0.1:4176/pink-icons/index.html): 13 ícones do Pink World, com amostras de tamanho e fundo dia/noite. Os nove ícones de categorias aparecem no mapa proposto rosa; favoritos estão no catálogo para avaliar. O carrinho Hello Kitty também aparece na navegação demonstrativa do Pink World.
- [propostas.html](http://127.0.0.1:4176/propostas.html): cinco estudos conceituais (Zelda, Fallout, Animal Crossing, Hollow Knight e Sea of Thieves), desenhados em SVG sobre o mesmo recorte real do Bosque dos Buritis. Não são temas implementados no aplicativo.
- [busca.html](http://127.0.0.1:4176/busca.html): teste do × na barra de busca após selecionar um destino, com remoção da rota e retorno à exploração. Durante a navegação de exemplo, usa “Parar”.

Arraste o mapa, use a roda para zoom e o botão direito para girar/inclinar. A navegação pausa o acompanhamento ao manipular a câmera; o botão circular de recentralização retoma o acompanhamento.

## Direções visuais

- **GTA SA:** ruas pretas marcadas, quadras claras, vegetação com contorno e impressão gráfica.
- **RDR2:** papel com fibras, hachuras de edifícios, vegetação pontilhada e desenho de margens.
- **Pink World / Rosa Pop:** maquete de papel com ícones originais de brinquedos e doces, camadas deslocadas, ruas suaves e detalhes brilhantes. É um tema autoral.
- **Super Mario World:** preserva o desenho aprovado, com materiais de água e vegetação no painel web.
- **Cyberpunk:** planta tática, arquitetura contornada, malha técnica e vias com bordas luminosas.

As propostas usam a mesma geografia, classificação e hierarquia de vias. “Recursos do app” lê os mesmos estilos incluídos no APK 0.11.0. A instalação no telefone é feita separadamente.

## O que é real e o que é ilustrativo

Ruas, parques e contornos de edifícios são os dados locais já presentes no projeto. A cena 3D contém **2.284 contornos reais** próximos à Praça Universitária; **15** possuem altura na fonte e **2.269** usam alturas ilustrativas determinísticas. A escala 1,0× mantém essas alturas; o controle amplifica todos os volumes como efeito visual.

O trajeto de cerca de 526 m, o movimento, a velocidade e os horários são uma demonstração. Não há acesso ao GPS, cálculo de rota ou tráfego em tempo real. “Adicionar parada” coloca um marcador de exemplo sem recalcular o trajeto.

As projeções usam volumes de MapLibre e linhas 3D dos mesmos polígonos. A cena começa pausada. São até 30 atualizações de animação por segundo, sem reconstruir os polígonos a cada quadro; isso é uma prévia web e não comprova desempenho em um celular.

## Arquivos para editar

- `novos-mapas.html` / `novos-mapas.css` / `novos-mapas.js`: coleção experimental de três mapas.
- `collection/pipboy.js`, `collection/hollow.js`, `collection/crossy.js`: materiais, símbolos e estilos locais desta coleção. Cada módulo exporta `definition`, `camera`, `createStyle` e `getImages`.
- `theme-designs.js`: estilos propostos e materiais de cada tema.
- `navigation-scene.js`: volumes, contornos, câmera e reprodução.
- `app.js` / `styles.css`: painéis e controles.
- `data/scene-buildings.geojson` / `data/scene.json`: recorte 3D e trajeto.
- `prepare-scene.mjs`: regenera somente o recorte deste protótipo a partir dos dados locais.
- `server.mjs`: servidor somente em 127.0.0.1, porta 4176.

O servidor reutiliza, apenas para leitura, `android/app/src/main/assets` e o MapLibre instalado em `node_modules`. Não precisa de chave de API nem de conexão externa para carregar este recorte.

## Origem dos dados e recursos

Dados cartográficos: © OpenStreetMap contributors, Overture Maps Foundation, Google Open Buildings e Microsoft Global ML Building Footprints, conforme os dados e avisos já presentes no projeto. Fontes, sprites e ícones são os recursos locais existentes, com sua documentação de origem; estes HTMLs não alteram suas licenças. Texturas de estudo e linhas dos volumes são produzidas pelo código do painel.

## Verificação da primeira entrega de painéis · 29/10/2026

Validado no Chromium com WebGL2: cinco temas em dia/noite, volumes e contornos 3D, reprodução/pausa, avanço no trajeto, câmera, alternância 2D/3D, controles de transparência/altura, parada demonstrativa e retorno entre páginas. Nenhum erro de JavaScript, WebGL ou carregamento de recursos nos cenários verificados. O painel também foi conferido em largura de 393 px, sem transbordamento horizontal.

As capturas reais do navegador estão em `screenshots/`. Os hashes de 2.879 arquivos protegidos do projeto, incluindo fontes nativas, recursos e APK/AAB v0.10, foram comparados com o início desta etapa e permaneceram iguais. Não houve build de aplicativo nesta entrega.

## Revisão: Pink World, propostas e limpar destino

Os ícones propostos e os cinco conceitos permanecem somente no laboratório. Os SVGs editáveis ficam em `pink-icons/` e `proposals/`; `proposals/prepare-previews.mjs` gera apenas estas prévias, sem alterar recursos nativos.

O veículo do Pink World foi redesenhado em SVG a partir da fotografia do carrinho Hello Kitty enviada pelo usuário: vista superior, carroceria branca, orelhas no capô, laço rosa e banco conversível. É uma adaptação desenhada para este painel, não um recurso extraído de um jogo. A frente da arte aponta para cima e acompanha o rumo da cena; a amostra usa 56 px para manter rosto e laço legíveis. Veja [o veículo no catálogo](http://127.0.0.1:4176/pink-icons/index.html#vehicle) ou [na navegação](http://127.0.0.1:4176/navegacao.html?tema=rosa). O carrinho também integra o APK 0.11.0 como veículo Temático do Pink.

O × de limpar destino foi acrescentado aos fontes Android/iOS. Ele cancela a preparação, limpa busca, destino, rota, paradas e retomada, e invalida respostas pendentes. A navegação ativa continua usando “Parar”. Foram aprovados 196 testes unitários Android e 128 testes do núcleo iOS. A interface SwiftUI ainda requer validação em Mac/Xcode. Essa correção foi incluída no APK 0.11.0.

## Coleção experimental: Pip-Boy, Hollow Knight e Crossy Road

Estes três mapas foram aprovados e incorporados ao catálogo e aos recursos Android/iOS. O painel continua lendo os dados locais sem modificá-los. Os contornos e a classificação das ruas são preservados. Alturas sem medição e as marcações gráficas do Crossy Road são ilustrativas: não indicam altura medida, número de faixas ou tráfego ao vivo.

- **Pip-Boy:** contornos em fósforo verde, pictogramas monocromáticos, hachura de água e visor CRT estático. [Referência Bethesda](https://help.bethesda.net/app/answers/detail/a_id/31464/~/what-can-the-fallout-4-pip-boy-app-do%3F).
- **Hollow Knight:** papel azul profundo, prédios como salas hachuradas, folhagens a nanquim, margens gravadas e ícones delicados. [Referência Team Cherry](https://www.teamcherry.com.au/blog/hollow-knight-then-and-now).
- **Crossy Road:** cidade vista em ângulo, construções sobre os contornos reais, asfalto escuro, parques em blocos e ícones voxel. [Referência Hipster Whale](https://www.crossyroad.com/crossy-road-classic-press-kit).

Os símbolos e materiais foram desenhados em código para o estudo; as fontes usam o pacote local já existente. Não foram incorporados assets extraídos dos três jogos. Este painel não representa navegação real e não modifica o aplicativo instalado.

Verificação desta coleção: seis variantes de estilo sem erros na validação MapLibre; 36 verificações no Chromium aprovadas, incluindo desktop, largura de 393 px, troca de recorte durante o carregamento, zoom, foco e filtros. As capturas reais estão em `screenshots/collection-three.png`, `collection-pipboy.png`, `collection-hollow.png`, `collection-crossy.png` e `collection-mobile.png`. Nenhum erro de JavaScript ou carregamento de recursos nos cenários verificados.

## Revisão: oito temas e perspectiva em todos os mapas

O catálogo principal e a navegação oferecem GTA SA, Pink World, RDR2, Super Mario World, Cyberpunk, Pip-Boy, Hollow Knight e Crossy Road. A opção **Perspectiva → 2D / 3D** mantém a escolha ao mudar de tema. Exploração e navegação têm preferências próprias: exploração começa plana; navegação, inclinada a 48°. Os volumes sólidos acompanham a paleta de cada tema; transparência, altura ilustrativa e contornos luminosos continuam ajustáveis no painel de navegação.

Os recursos nativos têm 32 variantes de estilo, com a camada 3D controlada pelo app. A prévia de rota nativa continua plana e iniciar a viagem ativa a preferência de navegação. A reprodução no painel é apenas uma demonstração. Veja [recursos e compatibilidade](../../docs/eight-theme-assets.md) e [preferências no iOS](../../ios/docs/MAP-DIMENSIONS.md).

Verificação integrada: 30 verificações de navegador aprovadas (oito temas, volume, navegação, persistência, dia/noite e largura de 393 px), sem erros de JavaScript ou recursos. As capturas desta revisão são `catalog-*-3d.png` e `collection-all-three-3d.png`.
