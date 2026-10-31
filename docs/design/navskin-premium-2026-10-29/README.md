# NavSkin — protótipos visuais premium

Exploração visual solicitada em 29/10/2026. Estas telas são conceitos estáticos, não capturas do aplicativo e não alteram o APK.

## Propostas

- **01 · Vidro:** superfícies claras translúcidas, contornos finos e sombras discretas; explorar e navegar no GTA SA.
- **02 · Grafite:** controles escuros foscos sobre a cartografia GTA SA clara; explorar e navegar.
- **03 · Claro:** superfícies brancas compactas, com adaptação da interface para Rosa Pop vibrante.

## Decisões comuns

### Revisão da proposta 01: menu integrado

`01-vidro-revisao-menu.png` mostra três estados: explorar, navegar e menu aberto. A logo e a faixa superior foram removidas. Temas, filtros, seus lugares e configurações ficam no menu de três pontos. Sem navegação, o botão é um círculo no canto superior direito. Durante a navegação, o botão fica dentro do card de direção. Busca, velocidade, recentralização e ETA mantêm as posições anteriores. O prompt desta edição com image_gen está em `01-vidro-revisao-menu-prompt.txt`; o protótipo original foi preservado.

Nas três propostas iniciais, marca, temas, filtros e menu formavam uma faixa compacta no topo; a revisão 01 acima substitui essa faixa pelo botão de três pontos. Em todas as versões, o mapa ocupa a tela inteira, a busca fica na parte inferior e velocidade/recentralização são círculos em lados opostos. Manobra tem seta, distância e rua. ETA tem apenas duração, distância restante e horário de chegada. O mapa conserva edifícios densos e nomes de ruas legíveis.

Geometria, rotas e valores são ilustrativos. As imagens geradas exploram tipografia; não demonstram o uso dos arquivos exatos de fontes do app. Na implementação, os arquivos e ícones existentes do projeto devem ser preservados e a legibilidade validada no aparelho.

## Produção

Gerado com a ferramenta nativa image_gen. Os prompts completos estão em prompts.json. Nenhuma alteração foi feita nos aplicativos Android e iOS.

