# Dados locais do OpenStreetMap

O mapa exibe os arquivos vetoriais locais de `public/data/`. A navegação pelo
mapa não solicita imagens de mapas, geocodificação ou dados em tempo real ao
Overpass. A interface deve exibir `© OpenStreetMap contributors`, com um link
para <https://www.openstreetmap.org/copyright>.

A base geográfica é derivada do OpenStreetMap e distribuída sob a
[Open Database License 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
O código da aplicação e a base geográfica derivada têm condições de
licenciamento próprias. Preserve os créditos e os metadados da licença da base.

## Reprodução

É necessário Node.js 20 ou mais recente. Os scripts usam apenas a biblioteca
padrão, sem dependências adicionais.

```sh
node scripts/fetch-osm.mjs places
node scripts/fetch-osm.mjs extract
node scripts/build-geodata.mjs
node scripts/validate-geodata.mjs
```

O primeiro comando verifica os cinco locais de referência em objetos reais do
OSM. O segundo baixa as geometrias selecionadas de vias, cobertura do solo,
edificações e água. Servidores públicos do Overpass podem estar ocupados: o
script tenta três instâncias, rejeita respostas parciais ou com erro e nunca
substitui os dados armazenados por uma página de erro.

Execute esses comandos ao atualizar a cópia dos dados. Eles não fazem parte da
compilação nem do carregamento de cada página. Apenas os dois comandos de
download precisam de internet. As consultas exatas, as respostas JSON completas
do Overpass e os registros de procedência ficam em `data/osm/`. O processamento
e a validação posteriores funcionam sem conexão.

A sintaxe das consultas está documentada na
[referência do Overpass QL no OpenStreetMap](https://wiki.openstreetmap.org/wiki/Overpass_API/Overpass_QL).

## Data dos dados e abrangência

A cópia atual usa os seguintes horários registrados em `metadata.json`:

| Registro | UTC exato | Horário de São Paulo (`America/Sao_Paulo`, UTC−03:00) |
| --- | --- | --- |
| Estado da base OSM (`dataTimestamp`) | `2026-10-26T23:47:59Z` | 26/10/2026 às 20:47:59 |
| Download concluído (`fetchedAt`) | `2026-10-26T23:49:07.859Z` | 26/10/2026 às 20:49:07,859 |
| GeoJSON gerado (`generatedAt`) | `2026-10-26T23:49:57.221Z` | 26/10/2026 às 20:49:57,221 |

A instância utilizada foi `https://overpass-api.de/api/interpreter`. Os horários
descrevem esta cópia local; atualizar os dados exige executar os comandos acima.

| Arquivo | Quantidade de feições |
| --- | ---: |
| `roads.geojson` | 23.075 |
| `areas.geojson` | 4.448, incluindo 2.963 feições de edificações |
| `waterways.geojson` | 398 |
| `places.geojson` | 544 |

## Limites e verificação

Os limites da visualização são oeste `-49.325`, sul `-16.755`, leste `-49.205`
e norte `-16.640`, em longitude/latitude (EPSG:4326). O recorte contém Centro,
Praça Cívica, Setor Bueno, Parque Vaca Brava e Parque Flamboyant.
`metadata.json` registra os IDs, os nomes e as etiquetas de origem no OSM,
coordenadas representativas, horários do download e da base, a instância
consultada, os limites exatos e as quantidades de feições.

Centro é o nome de exibição do **Setor Central** no OSM. O Parque Flamboyant
corresponde a `way/220688361`; o bairro homônimo mais ao sul é outra feição.
As coordenadas dos rótulos de parques e praças são centroides calculados a
partir dos polígonos reais do OSM. Os nós de bairros mantêm as coordenadas
originais. Esses centroides não alteram a geometria dos polígonos.

A seleção usa o filtro retangular de limites do OSM. As geometrias selecionadas
são preservadas integralmente, inclusive os vértices fora do enquadramento.
A interface recorta a visualização. As vias e os polígonos não são
simplificados, retificados, ajustados a uma grade ou deduzidos de uma rede
viária fictícia. O terreno sem mapeamento mantém a cor de fundo do mapa.

As formas brancas de edificações são contornos reais cadastrados no OSM. As
2.963 feições disponíveis representam somente a cobertura existente no recorte
na data registrada. Elas não constituem um cadastro completo das edificações de
Goiânia, e esta base não informa a porcentagem de cobertura. A ausência de uma
forma branca não significa que o terreno esteja vazio. Nenhuma edificação ou
quadra é gerada para preencher lacunas do mapeamento.

## Estrutura do GeoJSON

Os quatro arquivos contêm objetos `FeatureCollection`. O `id` de cada feição é
igual à string `properties.osm_id`, no formato OSM `type/id`. Todas as
coordenadas usam `[longitude, latitude]` em EPSG:4326.

Propriedades compartilhadas:

| Propriedade | Tipo | Significado |
| --- | --- | --- |
| `osm_id` | `string` | `node/…`, `way/…` ou `relation/…` |
| `kind` | `string` | Classe visual derivada de etiquetas explícitas do OSM |
| `name` | `string` | Nome no OSM ou string vazia; Centro usa o nome de exibição documentado |
| `highway`, `railway`, `landuse`, `natural`, `leisure`, `waterway`, `water`, `place` | `string` ou `null` | Etiquetas originais de classificação do OSM |
| `bridge`, `tunnel` | `string` ou `null` | Etiquetas originais, como `yes`, `no` ou um tipo específico de estrutura |
| `layer` | `number` | Camada original numérica; o padrão é `0` |
| `grade` | `number` | Camada diferente de zero; caso contrário, túnel `-1`, ponte `1`, nível do solo `0` |
| `oneway`, `junction`, `surface` | `string` ou `null` | Etiquetas originais do OSM disponíveis para ajustes visuais posteriores |

`bridge=no`, `tunnel=no`, `false` e `0` não indicam separação de níveis.
A camada tem precedência sobre o túnel, que tem precedência sobre a ponte.

### roads.geojson

Feições `LineString`. Os valores originais de `highway=*` e `railway=*`
continuam disponíveis. A hierarquia visual é derivada destas regras:

| Etiquetas OSM | `kind` |
| --- | --- |
| `highway=motorway` ou `motorway_link` | `motorway` |
| `highway=trunk` ou `trunk_link` | `trunk` |
| `highway=primary` ou `primary_link` | `primary` |
| `highway=secondary` ou `secondary_link` | `secondary` |
| `highway=tertiary` ou `tertiary_link` | `tertiary` |
| `residential`, `living_street`, `unclassified`, `road` | `residential` |
| `service` | `service` |
| `busway` | `busway` |
| `track`, `raceway` | `track` |
| `footway`, `cycleway`, `pedestrian`, `path`, `steps`, `bridleway`, `corridor` | `path` |
| Outros valores de `highway` baixados | `service` (o valor original permanece em `highway`) |
| Valores selecionados de `railway=rail/light_rail/tram/subway/narrow_gauge/preserved` | `rail` |

A consulta exclui vias com `highway=construction`, `proposed` ou `abandoned`.
Áreas fechadas de `highway=*` explicitamente marcadas com `area=yes` tornam-se
feições de área, sem a criação de um eixo viário artificial. Pistas separadas
continuam como vias reais distintas do OSM. As etiquetas de camada, ponte e
túnel são mantidas para exibir corretamente os cruzamentos em níveis distintos.

### areas.geojson

Feições `Polygon` e `MultiPolygon`. Contornos de edificações usam
`kind=building` e acrescentam `building` (string original), `height` (string
original ou `null`) e `levels` (`number` ou `null`). Os membros externos de
multipolígonos são unidos apenas por extremidades idênticas. Os anéis internos
permanecem como furos. Anéis incompletos são omitidos e registrados nos avisos
dos metadados; nenhuma aresta artificial é inserida para fechá-los.

A classificação das demais áreas usa a primeira regra aplicável:

| Etiquetas OSM | `kind` |
| --- | --- |
| `natural=water`, `water=*`, `landuse=reservoir/basin`, `waterway=riverbank` | `water` |
| `leisure=park/garden/nature_reserve` | `park` |
| `natural=wood`, `landuse=forest` | `wood` |
| `natural=grassland/heath`, `landuse=grass/meadow/village_green/greenfield` | `grass` |
| `natural=scrub` | `scrub` |
| `natural=wetland` | `wetland` |
| Outros valores selecionados de `landuse=*` | Valor original, como `residential`, `commercial`, `retail`, `industrial`, `cemetery`, `farmland`, `farmyard`, `brownfield`, `allotments`, `orchard`, `plant_nursery`, `recreation_ground` |
| `leisure=golf_course` | `grass` |
| `leisure=pitch/playground/sports_centre/recreation_ground` | Valor original |
| `place=square` ou `highway=pedestrian` + `area=yes` | `square` |
| Outras áreas viárias explícitas | `road_area` |

### waterways.geojson

Feições `LineString`, com `kind` igual ao valor original de `waterway`, como
`stream`, `river`, `canal`, `drain` ou `ditch`. Margens de rios representadas
como polígonos e superfícies de água ficam em `areas.geojson`.

### places.geojson

Feições `Point` para lugares e parques com nome no OSM. `kind` pode ser `city`,
`district`, `park` ou `square`. Propriedades adicionais:

| Propriedade | Tipo | Significado |
| --- | --- | --- |
| `rank` | `number` | `0` cidade; `1` cinco locais destacados; `2` bairro com `place=suburb`; `3` demais rótulos locais |
| `featured` | `boolean` | Indica um dos cinco locais de referência verificados |
| `osm_name` | `string`, somente nas feições destacadas | Nome original no OSM; permite identificar o nome de exibição de Centro |

Os rótulos representam uma cópia local dos dados. Não oferecem geocodificação
ou navegação.

## Validação

`node scripts/validate-geodata.mjs` confere cada feição com os dados
armazenados: IDs de origem no OSM, vértices originais preservados, coordenadas
finitas e válidas, quantidade de vértices das linhas, fechamento dos anéis dos
polígonos, quantidades das coleções, hierarquia viária, parques, água, presença
de edificações, campos de nível, os cinco locais de referência, o objeto
correto do Parque Flamboyant, créditos e horários da cópia dos dados.
