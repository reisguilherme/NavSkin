# NavSkin — Vidro, protótipo interativo

Revisão 03 · 29/10/2026. Interface editável em HTML, CSS e JavaScript, com mapa vetorial MapLibre e recursos reais locais do projeto. Não usa telas ou fundos gerados por IA.

## Abrir

Na raiz do projeto, execute:

```powershell
node prototypes/navskin-glass/server.mjs
```

Abra **http://127.0.0.1:4175/** no mesmo computador. Também é possível executar `Abrir prototipo.cmd` nesta pasta. O servidor só escuta em localhost. Ele depende dos recursos já preparados em `android/app/src/main/assets` e do MapLibre presente em `node_modules`; não é necessário compilar o Android.

Não abra `index.html` diretamente com `file://`: módulos, estilos, fontes e tiles são servidos pelo servidor local.

## Interações

- **Parar:** encerra a demonstração, esconde rota/ETA e devolve a busca.
- **Adicionar parada:** abre a busca local; escolher um resultado adiciona um chip e mantém o destino final. Toque no chip para remover a parada.
- **Busca:** selecione um lugar e inicie a demonstração pelo painel do destino.
- **Menu ⋯:** dentro do card na navegação; isolado no topo ao explorar. Reúne temas, filtros, seus lugares e configurações.
- **Temas:** carrossel horizontal com previews reais; GTA SA, Rosa Pop e RDR2 mudam a cartografia, sprites e marcador.
- **Filtros:** alteram a visibilidade das categorias de POIs reais.
- **Mapa:** arraste, amplie e gire; recentralizar restaura o enquadramento da demonstração.
- **Painéis:** fecham pelo X, toque no fundo ou Escape. O foco de teclado fica dentro do painel aberto.
- **Cenas laterais:** atalhos para comparar estados em uma tela de computador.

Velocidade e recentralização usam a mesma variável de tamanho: 64 × 64 px em retrato e 54 × 54 px em paisagem compacta. A logo foi removida da interface do mapa. Durante navegação não há busca na barra inferior: há **Parar** vermelho e **Adicionar parada**.

## Escopo da demonstração

O mapa usa dados geográficos reais, estilos, glyphs, fontes e ícones locais. A rota é uma linha ilustrativa alinhada a ruas de Goiânia. GPS, velocidade, distâncias e ETA são simulados; nenhum serviço de busca ou cálculo de rotas é consultado. Adicionar parada demonstra o fluxo visual e não recalcula uma rota real. As preferências de voz e zoom são controles de demonstração; não ativam áudio nem navegação adaptativa.

A busca usa uma seleção fixa. “Posto Universitário” e “Café do Campus” são exemplos de parada, identificados na lista. Estado fica somente na memória desta página. Nenhum favorito, chave ou configuração do aplicativo instalado é lido ou modificado.

O APK e o projeto iOS não foram alterados. Recursos dos jogos mantêm os avisos e restrições de procedência registrados em `docs/game-assets-provenance.md`; este protótipo reutiliza o pacote local, sem redistribuí-lo.

## Arquivos editáveis

- `index.html`: estrutura e cenas.
- `styles.css`: superfícies, tamanhos, tipografia e responsividade.
- `app.js`: navegação pelos estados e controles.
- `map.js`: adaptação dos recursos cartográficos locais para o navegador.
- `server.mjs`: servidor local limitado ao protótipo, recursos de mapa e distribuição MapLibre.
- `navigation.png`: captura real do protótipo no navegador.

## Verificação realizada

JavaScript validado com `node --check`. No Chrome: abrir menu, buscar um destino e iniciar, adicionar e remover parada, parar/restaurar busca, trocar GTA SA/Rosa Pop/RDR2, ocultar parques e fechar por Escape. A rota permaneceu visível nas trocas de tema. Console sem erros após carregar os recursos.

Medidas conferidas no DOM: círculos idênticos em 360 × 780 e 393 × 852; sem overflow horizontal. Em 852 × 393, os círculos têm 54 × 54 px e os painéis cabem na tela. Essas verificações são do protótipo web, não do APK ou de uma central de veículo.
