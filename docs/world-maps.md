# Mapa mundial NavSkin

O modo mundial usa a instância pública de produção do [OpenFreeMap](https://openfreemap.org/), com geometria real do OpenStreetMap no [esquema OpenMapTiles](https://openmaptiles.org/docs/schema/). O serviço autoriza uso comercial e integração com MapLibre Native em apps; a página oficial informa ausência de chave, cadastro e cobrança por visualizações ou requisições. A [integração oficial](https://openfreemap.org/quick_start/) e os [termos](https://openfreemap.org/tos/) foram consultados em 27/10/2026. O serviço funciona sem garantia de disponibilidade e pode mudar.

O mapa online requer rede para a base de tiles e fontes, amplia a área navegável ao mundo em Web Mercator e preserva os três temas, cada um com dia e noite. Dentro da cobertura empacotada de Goiânia, reutiliza os tiles locais de edificações Overture; a opção Goiânia mantém a base completa offline. O detalhe fora desse recorte depende dos dados existentes em cada local. A cobertura do mapa não altera a cobertura ou o custo do serviço de rotas.

## Recursos

| Recurso | Endereço ou identificação |
|---|---|
| TileJSON estável | `https://tiles.openfreemap.org/planet` |
| Source MapLibre | `world`, tipo `vector`, zoom 0–14, com overzoom pelo renderer |
| Edificações locais no modo Mundo | `buildings-local`, tiles `asset://maps/tiles/buildings/{z}/{x}/{y}.pbf`, zoom 11–16, desenho a partir de zoom 14 |
| Limites | `[-180, -85.05113, 180, 85.05113]` |
| Fontes | `https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf` |
| Fontstack online | `Noto Sans Bold` |
| Sprites locais | `asset://maps/sprites/{gta,rosa,rdr2}` |
| Estilos gerados | `android/app/src/main/assets/maps/styles/world-{gta,rosa,rdr2}{,-night}.json` |
| Metadados auditáveis | `android/maps/world-source.json` |
| Créditos preparados | `maps/credits/world-sources.json` e `maps/credits/world-attribution.html` |

O URL TileJSON resolve a versão atual do planeta. O código não fixa o caminho datado observado na validação e não usa mapa de demonstração como fallback. Todos os recursos remotos usam HTTPS. A preparação é determinística e funciona sem rede:

```sh
node scripts/prepare-world-maps.mjs
node --test tests/world-maps.test.mjs
```

Os tokens em `android/maps/theme-tokens.json` mantêm a identidade dos temas: desenho GTA SA, rosa e menta no Rosa Pop e papel/traço escuro/vermelho no RDR2. A fonte online foi escolhida após consultar o [código oficial do serviço de fontes](https://github.com/hyperknot/openfreemap-styles/tree/main/fonts) e verificar os PBFs. O endpoint de `Noto Serif Bold` no OpenFreeMap respondeu 404. Para preservar nomes internacionais, os três temas online usam o Noto Sans Bold verificado. O RDR2 offline conserva o serif local. Os testes de glifos cobrem amostras em latim, árabe e CJK; não afirmam validação de todos os caracteres ou de shaping em todos os dispositivos.

## Filtros e geografia

As seis camadas `poi-parks`, `poi-fuel`, `poi-food`, `poi-parking`, `poi-health` e `poi-shops` são símbolos independentes sobre `world`/`poi`. A classificação usa o `subclass` original OSM para conservar as mesmas categorias do pacote de Goiânia: parques/jardins; postos; restaurantes/cafés/lanchonetes; estacionamento; hospital/clínica/médicos/farmácia; supermercado/conveniência. Isso evita confundir o `class=shop` amplo do OpenMapTiles com a categoria restrita de mercados. Desativar as seis camadas retira os marcadores e seus nomes sem retirar áreas verdes, prédios ou textos geográficos. Locais salvos são uma sobreposição própria do app.

As seis variantes compartilham sources, filtros e geometria. As cores mudam; vias continuam a usar `transportation`, nomes usam `transportation_name` e `place`, áreas usam `landcover`, `landuse`, `park` e `water`. Prédios usam `world`/`building` fora do recorte local e `buildings-local`/`buildings` dentro dele. Túneis, nível do solo e pontes respeitam `brunnel`; em cada grupo, todos os contornos são desenhados antes dos preenchimentos para manter cruzamentos abertos. Não há prédios, ruas ou relevo fictícios.

## Densidade das edificações no modo Mundo

Na v0.6 o modo Mundo declarava apenas a fonte OpenFreeMap e ignorava os 729 tiles de edificações já incluídos no aplicativo. Por isso, em Goiânia aparecia a cobertura OSM esparsa, mesmo com o pacote Overture disponível. O recorte local contém 269.728 contornos reais de origem contra 2.963 edificações do recorte OSM anterior, cerca de 91 vezes mais feições. Essa razão compara os dois recortes locais; não é uma contagem auditada dos tiles mundiais atuais nem uma medida de precisão.

As seis variantes mundiais agora declaram a fonte vetorial local com os limites `[-49.325, -16.755, -49.205, -16.64]`, camada `buildings`, zoom 11–16 e atribuições de Overture, Google e Microsoft. Os limites, edição e contagem são lidos de `public/data/buildings-metadata.json`. O desenho começa no mesmo zoom dos prédios mundiais, conserva as cores e contornos de cada tema e fica abaixo das vias. O carregamento é por tile visível, sem abrir os 107 MB do GeoJSON inteiro.

A expressão [`distance`](https://maplibre.org/maplibre-style-spec/expressions/#distance) mantém a camada mundial apenas quando o polígono está separado do recorte. Assim, os prédios mundiais que interceptam a cobertura local são substituídos pelos contornos Overture, cuja consulta preservou inteiras as geometrias na borda. `within` não serve a esse filtro: avalia apenas pontos e linhas. A correção não modifica coordenadas nem amplia a área de dados disponível. Fora do recorte, inclusive em São Paulo, a densidade continua limitada à fonte mundial.

Os resolvers nativos existentes aceitam os dois tipos de URL no mesmo estilo: Android carrega `asset://` diretamente; iOS transforma esses caminhos locais em `file://`, preservando os endereços HTTPS da base mundial. Não há troca automática do modo de mapa nem alteração da câmera para mostrar os prédios locais.

## Evidência e limites

Em 27/10/2026, o TileJSON retornou limites mundiais, zoom 0–14 e as 16 camadas descritas em `world-source.json`. Duas amostras foram baixadas e decodificadas:

| Local | Tile z/x/y | HTTP | Bytes | Prédios | Vias | POIs |
|---|---|---:|---:|---:|---:|---:|
| São Paulo | `14/6069/9295` | 200 | 621.305 | 199 | 1.356 | 4.331 |
| Tóquio | `14/14552/6451` | 200 | 747.142 | 132 | 888 | 6.148 |

Os PBFs de `Noto Sans Bold` contêm os IDs dos glifos `ã`, `ا` e `中`. O arquivo de metadados registra horário, versão observada do planeta e hashes das amostras. As amostras não são empacotadas como mapa offline. Os testes locais validam o schema MapLibre, os filtros das seis categorias, a preservação de áreas/prédios ao ocultar POIs e a ordem dos cruzamentos. Essas verificações não confirmam pixels renderizados em Android Auto ou CarPlay.

O crédito visível é OpenFreeMap · © OpenMapTiles · Data from OpenStreetMap contributors, com links oficiais. Licenças dos dados e do esquema continuam aplicáveis. A [política de privacidade](https://openfreemap.org/privacy/) descreve o processamento das requisições ao serviço. Não foi adicionado um downloader mundial; os termos do provedor limitam cada download offline a 3.000 tiles ou 50 MB, quatro requisições simultâneas e User-Agent próprio do app, com contato prévio para volumes maiores.
