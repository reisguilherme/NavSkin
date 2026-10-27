# Pontos de interesse de Goiânia

Os ícones usam pontos extraídos do OpenStreetMap. O arquivo `public/data/pois.geojson` contém **1.789 pontos** de 19 categorias consultadas, dentro de `[-49.325, -16.755, -49.205, -16.640]` (longitude oeste, latitude sul, longitude leste, latitude norte). A cobertura depende do que os colaboradores mapearam; um item pode representar um equipamento, uma unidade, um prédio ou uma parte de campus, conforme suas etiquetas no OSM.

## Origem e datas

- Fonte: OpenStreetMap via [Overpass API](https://overpass.private.coffee/api/interpreter).
- Download: **27/10/2026, 00:16:09 UTC** (26/10/2026, 21:16:09 em Brasília).
- Data da base informada pelo servidor: **15/07/2026, 15:22:01 UTC**. O download é posterior à base; não constitui verificação atual de funcionamento, horário ou disponibilidade dos estabelecimentos.
- Atribuição: **© OpenStreetMap contributors**, [direitos autorais e atribuição](https://www.openstreetmap.org/copyright).
- Licença: [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
- Consulta reproduzível: `data/osm/pois.overpassql`.
- Resposta original: `data/osm/pois.json`; procedência: `data/osm/pois.provenance.json`.
- Metadados completos, contagens, IDs originais, exemplos e processamento: `public/data/pois-metadata.json`.

## Categorias e contagens

As contagens são de pontos exibíveis, após unir quatro representações duplicadas seguras de um mesmo local. Nomes e classificações abaixo são etiquetas da fonte OSM.

| `kind` | Categoria | Pontos | Exemplos de nomes na fonte |
| --- | --- | ---: | --- |
| `park` | Parques, praças e jardins com `leisure=park/garden` | 566 | Parque Vaca Brava; Parque Flamboyant; Bosque dos Buritis |
| `pharmacy` | Farmácias | 90 | Alex Farma; Droga Raia; Droga Mar |
| `fuel` | Postos de combustível | 200 | Auto Posto 68; Auto Posto Alameda; Alternativa Auto Posto |
| `hospital` | Equipamentos com `amenity=hospital` | 65 | Hospital Araújo Jorge; Hospital Geral de Goiânia Alberto Rassi; HUGO - Hospital de Urgências de Goiânia |
| `clinic` | Clínicas e consultórios, `clinic/doctors` | 91 | Bueno Medical Center; CAIS Campinas; CAIS Novo Horizonte |
| `theatre` | Teatros | 9 | Teatro Goiânia; Teatro Madre Esperança Garrido; Teatro do IFG |
| `cinema` | Cinemas | 5 | Cine Cultura; Cinemark; Kinoplex |
| `culture` | Centros de artes, `arts_centre` | 4 | Centro Cultural Oscar Niemeyer; Martim Cererê; Vila Cultural Cora Coralina |
| `museum` | Museus, `tourism=museum` | 13 | Memorial do Cerrado; Museu Antropológico; Museu da Imagem e do Som |
| `school` | Escolas | 123 | Arena Ensino Médio; Ateneu Dom Bosco; CEI Anália Franco |
| `university` | Universidades e unidades de ensino superior | 48 | Centro Universitário Alves Faria - UNIALFA; Centro de Aulas D |
| `police` | Polícia | 30 | 13º DP; 17º DP; 19º DP |
| `fire_station` | Bombeiros | 6 | 1º Batalhão Bombeiro Militar; 2º Grupamento de Incêndio |
| `bank` | Bancos | 88 | Banco do Brasil |
| `atm` | Caixas eletrônicos independentes | 0 | Nenhum `amenity=atm` retornado nesta consulta; sem inferir caixas a partir dos bancos |
| `parking` | Estacionamentos | 152 | Estacionamento - Serra Dourada; Estacionamento Sabor de Goiás |
| `restaurant` | Restaurantes | 161 | Abruzzo; Antônio Bistrô; Arabic Cozinha Árabe |
| `cafe` | Cafés | 14 | Biomas - Cafés Especiais; Café Conceito; Coruja Café |
| `fast_food` | Lanchonetes e alimentação rápida | 124 | A Quicheria; Bob's; McDonald's |

As categorias preservam a classificação OSM: `hospital` permanece separado de `clinic`, sem reclassificar unidades pelo texto de seus nomes. Não há nomes de estabelecimentos inventados. Os pontos sem `name` continuam sem nome; o aplicativo pode apresentar apenas o nome genérico da categoria. Jardins e estacionamentos podem ter acesso privado, registrado quando a fonte informa `access`.

## Geometria e duplicatas

Nós usam suas coordenadas OSM. Áreas usam um ponto representativo calculado a partir de todos os anéis completos. O centroide só é aceito quando fica dentro da área, fora dos buracos e dentro do recorte; nos demais casos, um intervalo horizontal interno fornece um ponto válido e determinístico. Não se usa o centro da caixa delimitadora como substituto de geometria ausente. Relações multipolígono mantêm seus buracos. A conversão desta resposta não omitiu geometrias e não gerou avisos.

A união de duplicatas exige a mesma categoria e um nome normalizado não vazio. Um nó e uma área só são unidos quando há contenção ou distância de até 12 metros; uma relação e seu membro etiquetado também podem ser unidos quando os nomes e categorias coincidem. Nós distintos de uma rede e itens sem nome permanecem separados. Áreas distintas com o mesmo nome podem ser partes mapeadas de um parque e continuam separadas. A coordenada de um nó tem preferência sobre o ponto calculado de uma área. Todos os IDs e links unidos ficam preservados em `source_ids` e `source_urls`.

Quatro pares foram unidos: CEPI José Honorato, Pit Dog Asa de Águia, Posto de Saúde da Família Vila Santa Helena e McDonald's. A lista de IDs está nos metadados.

## Esquema e reprodução

Cada feição é `Point`, com `osm_id`, `source_id`, `source_ids`, `source_url`, `source_urls`, `kind`, `name` e `coordinate_source`. Endereço, site HTTP(S), marca e acesso só são incluídos quando presentes na fonte. O popup pode abrir o registro original usando `source_url`.

```powershell
node scripts/fetch-pois.mjs
node scripts/build-pois.mjs
node --test tests/pois-data.test.mjs
```

É possível selecionar outro servidor público com a variável `OSM_OVERPASS_ENDPOINT`. Uma falha de download conserva a resposta e a procedência anteriores; uma resposta parcial da API é rejeitada. A compilação local e o aplicativo usam os arquivos já salvos, sem depender de consultas externas durante a navegação.

Os testes verificam pontos internos de polígonos côncavos e com buracos, recorte, relações, regras de duplicação e todos os pontos publicados contra seus IDs, nomes e categorias da resposta OSM original.
