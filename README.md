# NavSkin · 0.6.0

Mapas com os temas GTA SA, Rosa Pop e RDR2, GPS e navegação em aplicativos nativos para Android e iOS. O **NavSkin 0.6.0** abre com **mapa mundial online** e mantém o recorte de **Goiânia offline** como alternativa. O repositório do projeto é [reisguilherme/NavSkin](https://github.com/reisguilherme/NavSkin).

Os dois modos oferecem temas dia/noite, filtros de lugares, favoritos, Casa/Trabalho, recentes e marcadores de veículo. Busca e rotas de automóvel usam a chave Geoapify configurada pelo usuário. A navegação inclui manobras visuais, velocidade, distância e tempo restantes, chegada e recálculo por desvio; trocar o estilo preserva a sessão.

A versão 0.6 reúne nove filtros: parques, combustível, alimentação, estacionamento, saúde, mercados e conveniências, educação, cultura e locais de culto. GTA SA usa a Pricedown histórica; RDR2 usa Hapna na interface e RDR Lino nos rótulos selecionados do mapa. Sprites dos jogos aparecem nos subtipos equivalentes; categorias adicionais recebem desenhos próprios NavSkin. A [procedência dos assets](docs/game-assets-provenance.md) identifica arquivos, versões, hashes, conversões e avisos preservados. Noto cobre caracteres ausentes nas fontes dos jogos. No Android Auto, os textos dos templates continuam sob controle do host.

| Plataforma | Projeto e instalação |
|---|---|
| Android / Android Auto | [Aplicativo Kotlin e instruções de build](android/README.md) |
| iPhone e iPad com iOS 17+ | [Projeto preparado para compilar no Mac](ios/README.md#para-quem-vai-compilar-no-mac) |
| iPhone usando um PC Windows | [Receber o IPA de um amigo e instalar com AltServer](ios/docs/SEM-MAC.md) |

## Cobertura e conexão

O mapa mundial usa tiles vetoriais do [OpenFreeMap](https://openfreemap.org/), com dados OpenStreetMap e schema OpenMapTiles. O serviço público não exige chave de mapa. A conexão é necessária para carregar tiles e blocos adicionais de caracteres; as fontes temáticas GTA/RDR2 ficam locais. A densidade dos detalhes varia conforme os dados de cada região. Os temas mudam a apresentação da mesma geografia.

A opção **Goiânia offline**, em **Temas e mapa**, usa o recorte cartográfico incluído no aplicativo. Ela cobre longitude −49,325 a −49,205 e latitude −16,755 a −16,640. Busca, cálculo e recálculo continuam online nos dois modos. O mapa local não inclui navegação offline completa. Os créditos OpenFreeMap/OpenMapTiles/OSM acompanham o mapa mundial; OSM/Overture acompanham o recorte local.

Configure sua chave Geoapify nas configurações do aplicativo e ative o GPS para navegar. Nenhuma chave pessoal acompanha o projeto. O ETA não inclui trânsito ao vivo; não há instruções por voz. A versão iOS não inclui CarPlay. Veja [configuração Android](android/docs/NAVIGATION.md), [configuração iOS](ios/README.md) e a [decisão de provedor](docs/navigation-provider-decision.md).

## Compilar e conferir

O código está no repositório; o pacote de assets originais é entregue localmente, separado da licença do código. Para o Mac, prefira o ZIP iOS completo, que já contém os recursos preparados. Um clone isolado precisa receber esse pacote antes do build; siga a [procedência e importação](docs/game-assets-provenance.md).

Para obter o código:

```sh
git clone https://github.com/reisguilherme/NavSkin.git
cd NavSkin
```

O Android usa Gradle. Para o iOS, o caminho desta entrega é **um amigo compilar no Mac com Xcode**, seguindo o [README preparado para ele](ios/README.md#para-quem-vai-compilar-no-mac). O comando `RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh` também roda nesse Mac local: testa, compila e gera `ios/artifacts/cloud/NavSkin-unsigned.ipa` sem login Apple. O amigo envia o IPA ao Guilherme, que usa sua própria Apple Account no AltServer do Windows para assinar e instalar. [Passos no Windows](ios/docs/SEM-MAC.md).

APKs, AABs, IPAs e saídas de build não fazem parte do código versionado. O nome local previsto para o APK desta versão é `NavSkin-v0.6.0-debug.apk`. O projeto iOS está preparado, mas a compilação Apple ainda precisa ser executada no Mac. O [workflow do GitHub Actions](.github/workflows/ios-cloud.yml) permanece como alternativa manual; sua execução nesta conta foi bloqueada pelo GitHub e não é necessária para esse caminho. Consulte a [documentação Android](android/README.md) e a [validação iOS](ios/docs/VALIDATION.md) para as evidências e os testes ainda pendentes.

Os [catálogos de ícones](docs/navigation-icon-catalogs.md) são locais e editáveis. Os registros da [versão Android 0.4.0](android/docs/VALIDATION-v0.4.md), da [0.3.0](android/docs/VALIDATION-v0.3.md) e suas capturas permanecem como histórico.

O pacote local de fontes e sprites solicitado é separado da licença do código. Seus arquivos originais, conversões e avisos acompanham a preparação local; a documentação de [importação e geração dos glyphs](docs/game-assets-provenance.md#reproduzir-a-importação-local) explica o manifesto de entrada e o conversor oficial MapLibre. A PoC web abaixo mantém seus assets históricos próprios.

## PoC web histórica · Goiânia / San Andreas

A PoC web preserva o estudo inicial de cartografia vetorial de Goiânia inspirado no mapa de pausa do GTA San Andreas original. Ela usa MapLibre GL JS e dados locais, sem credenciais. Esta página de demonstração continua independente dos aplicativos nativos e mantém seu recorte local.

O snapshot reúne **269.728 contornos Overture** em tiles vetoriais e **1.789 pontos de interesse OSM**. Use **Locais e prédios → Camadas** para comparar com os 2.963 edifícios OSM originais e filtrar os serviços. A fonte dos prédios e os filtros são preservados ao alternar GTA SA/neutro.

Fontes, licenças e reprodução: [edificações](docs/buildings.md), [POIs](docs/pois.md) e [ícones](docs/icons.md). Os símbolos adaptam a biblioteca aberta Maki (CC0); não são sprites extraídos do GTA.

### Abrir

No Windows, dê dois cliques em **`Iniciar.cmd`**. Requer Node.js **22.12+**. O script instala as dependências se necessário e abre o navegador.

Ou, no terminal nesta pasta:

```sh
npm ci
npm run dev
```

Abra **http://127.0.0.1:4173/**. Para parar, pressione `Ctrl+C` no terminal. Se a porta estiver ocupada, uma instância já pode estar aberta; feche-a antes de iniciar outra.

Os dados já estão incluídos. A instalação inicial precisa de internet; depois, o mapa usa arquivos locais, inclusive renderer, worker e fontes do sistema. Links de pesquisa abrem sites externos somente quando acionados.

### Avaliar

- Alterne **Estilo GTA SA / Estilo neutro**: mesmas fontes, centro e zoom.
- Use os três enquadramentos: visão geral, Centro/Praça Cívica e Parque Vaca Brava.
- Arraste, amplie e use o botão de enquadramento inicial no canto superior direito.
- Abra **Referências & ajustes** para consultar a pausa do guia oficial PS2, a paleta, os controles de cor/espessura, os dados e os cinco lugares verificados. Parque Flamboyant pode ser localizado nessa aba.
- As [seis capturas comparativas](screenshots/index.html) também ficam em **http://127.0.0.1:4173/comparacoes/** após iniciar a aplicação.

### Arquivos principais

| Arquivo | Função |
|---|---|
| `public/styles/tokens.json` | Paleta HEX, hierarquia, larguras por zoom, visibilidade, tipografia e ordem de desenho |
| `src/style.js` | Constrói os dois estilos MapLibre, com filtros explícitos das categorias OSM |
| `public/styles/gta-sa.json` / `neutral.json` | Estilos completos exportados automaticamente ao iniciar/buildar |
| `src/main.js` / `src/app.css` | Interface e controles |
| `public/data/*.geojson` | Geografia local: vias, áreas/edifícios, água e lugares |
| `public/data/metadata.json` | Fonte, data, contagens, recorte e IDs dos lugares verificados |
| `data/osm/` | Respostas brutas Overpass, consultas e proveniência |
| [Pesquisa visual](docs/references.md) | Links, natureza das referências, observações e método de estimativa |
| [Especificação visual](docs/style.md) | Observado × estimado × adaptado, proporção, hierarquia, larguras e composição |
| [Dados e reprodução](docs/data.md) | Schema, tags, licença e comandos para atualizar os dados |
| [Validação](docs/validation.md) | Inspeções, correções, testes e câmeras das capturas |

Edite `tokens.json` e recarregue a página para ajustes persistentes. Os controles do painel são temporários e não sobrescrevem o arquivo. O botão de exportação salva o estilo ajustado; os caminhos `/data/…` pressupõem o mesmo servidor local. Os estilos exportados automaticamente são derivados dos tokens e serão regenerados.

### Resultado e limites

A combinação de **vias pretas contínuas, superfícies cinzas, edifícios claros, verdes chapados e água azul acinzentada** reproduz aspectos reconhecíveis da linguagem original. A Overture ampliou muito as massas claras em relação à primeira versão OSM. A fidelidade continua parcial: os contornos adicionais incluem telhados inferidos de imagens por Google/Microsoft, sujeitos a erros de delimitação e data. Não equivalem a um levantamento cadastral; não foram inventados quarteirões para preencher espaços.

- **Observado/estimado:** composição e contraste conferidos em uma screenshot de pausa do guia oficial BradyGames PS2; HEX medidos em mosaico do radar original publicado por terceiros, usado como complemento. A origem exata dos pixels PS2 não foi certificada.
- **Recriado:** regras de renderização vetorial e interface. Não há sprites, fonte, logotipo ou textura extraídos do jogo. As três imagens locais de referência são cópias documentais de terceiros, com créditos e origem em `docs/references.md`.
- **Adaptado:** tipografia local Arial, nomes e marcadores de Goiânia, espessuras interpoladas por zoom e redução de rótulos. Edifícios reais aparecem a partir de zoom 13,4; serviços/caminhos entram ao aproximar. Sem textura decorativa ou filtro raster.
- **Preservado:** vértices e relações geométricas dos dados OSM, inclusive furos de multipolígonos, vias separadas, classificação e níveis de pontes/túneis quando mapeados.

Os próximos ajustes cartográficos com maior potencial seriam confrontar os contornos com cadastro municipal de licença confirmada, estudar uma generalização controlada de conjuntos existentes e calibrar cores/larguras com capturas digitais PS2 comprovadas. A PoC web não oferece GPS, rotas, ETA ou integração veicular; esses recursos pertencem aos aplicativos nativos descritos acima.

### Verificar e reproduzir

```sh
npm test
npm run data:validate
npm run build
npm run preview
```

São 28.465 feições: 23.075 vias, 4.448 áreas (2.963 edifícios), 398 cursos d’água e 544 lugares. Snapshot OSM: **26/10/2026, 23:47:59 UTC**; coleta via Overpass, incluída localmente. Para atualizar, siga `docs/data.md`; servidores públicos podem ter fila. A exploração do mapa local não depende deles.

Esse recorte-base foi mantido. A ele se somam a camada alternativa de **269.728 contornos Overture, release 2026-10-21.0**, e **1.789 POIs**. O endpoint de POIs respondeu com base OSM de **15/07/2026**, apesar do download em 26/10; isso está exposto no painel e nos metadados, sem afirmar verificação atual dos estabelecimentos. Contagem de contornos não é medida de precisão ou completude.

O arquivo completo de edifícios tem cerca de 107 MB, fica disponível para reprodução e **não é carregado pelo mapa**. O renderer pede apenas tiles locais da área visível; prédios aparecem a partir do zoom 13,4 para evitar os tiles grandes de panorama. São 729 tiles, incluindo bordas vazias válidas. Regeneração: `npm run icons:build`, `npm run buildings:tiles`, `npm run pois:fetch`. Dependências opcionais Python e validações estão nos documentos de cada fonte.

Requer navegador com **WebGL2**. O build pode informar um aviso de tamanho de bundle do MapLibre; não é falha de compilação. Dados © **OpenStreetMap contributors**, [ODbL](https://www.openstreetmap.org/copyright). Referências do jogo pertencem aos respectivos titulares; projeto independente, sem vínculo com Rockstar Games.

O pacote local `NavSkin-game-assets-0.6.0.zip` contém a árvore de recursos preparada: extraia seu conteúdo na raiz do clone, mantendo as pastas `android` e `ios`. O ZIP iOS completo já está preparado para abrir no Mac e dispensa essa etapa. Os arquivos de assinatura pessoal continuam excluídos.
