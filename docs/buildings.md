# Edificações: cobertura Overture

O mapa local recebeu **269.728 contornos de edificações** da edição Overture **2026-10-21.0**, contra 2.963 edificações do recorte OSM anterior: cerca de 91 vezes mais feições. A comparação mede quantidade, não comprova completude, atualização ou precisão. A Overture já combina fontes e prioriza o OSM; a camada deve substituir as edificações OSM na visualização, evitando desenhar as duas juntas.

O recorte é `[-49.325, -16.755, -49.205, -16.640]`, em longitude/latitude, WGS84. São 269.724 Polygon e quatro MultiPolygon, com 559 nomes e 46 alturas disponíveis. As alturas ausentes continuam ausentes.

| Fonte presente no recorte | Feições | Natureza | Licença da fonte |
| --- | ---: | --- | --- |
| Google Open Buildings | 202.610 | Contornos de telhados detectados por aprendizado de máquina | CC BY 4.0 |
| Microsoft ML Buildings | 64.157 | Contornos de telhados detectados por aprendizado de máquina | ODbL 1.0 |
| OpenStreetMap | 2.961 | Mapeamento colaborativo | ODbL 1.0 |

O tema **buildings** distribuído pela Overture é **ODbL 1.0**; não é CDLA. A descrição de fontes e licenças está na [documentação oficial de atribuição](https://docs.overturemaps.org/attribution/) e no [guia oficial de edificações](https://docs.overturemaps.org/guides/buildings/). A licença da coleção está também preservada no STAC local.

Atribuição utilizada: **© OpenStreetMap contributors, Overture Maps Foundation; Google Open Buildings; Microsoft Global ML Building Footprints**. Manter a atribuição e os links de licença ao redistribuir os dados ou o mapa.

## Aquisição e rastreabilidade

A edição foi confirmada no [catálogo STAC oficial](https://stac.overturemaps.org/catalog.json), e o download foi feito com o cliente oficial `overturemaps` 1.0.2. O [guia do cliente Python](https://docs.overturemaps.org/getting-data/overturemaps-py/) descreve a extração por área. A coleta terminou em **2026-10-27 00:12:55 UTC** — **26/10/2026 21:12:55 em Goiânia**. A hora exata de geração de cada artefato está nos metadados.

- Coleção: `https://stac.overturemaps.org/2026-10-21.0/buildings/building/collection.json`.
- Dados: `s3://overturemaps-us-west-2/release/2026-10-21.0/theme=buildings/type=building/`.
- Consulta: tipo `building`; bbox oeste/sul/leste/norte; seleção de arquivos pelo STAC e filtro espacial pelo cliente; interseção geométrica exata aplicada em seguida.
- O cliente trouxe 269.741 feições por envelope. Treze envelopes tocavam o recorte sem que os contornos o interceptassem; foram excluídos. Os demais contornos foram mantidos inteiros, inclusive suas partes externas na borda do recorte.
- `data/overture/catalog.json`, `building-collection.json`, `query.json` e o arquivo `.state` registram catálogo, licença, edição, cliente, consulta e coleta.
- `data/overture/buildings.raw.geojson` preserva as geometrias e propriedades recebidas, incluindo todos os registros `sources`: identificador da fonte, confiança quando fornecida, campo ao qual se aplica, atualização, provedor, recurso e versão.
- `public/data/buildings.geojson` mantém as coordenadas originais e um conjunto menor de propriedades. O `gers_id` permite relacionar cada feição ao original. O SHA-256 do arquivo bruto está em `public/data/buildings-metadata.json`.

Não houve geração de quarteirões ou edifícios, arredondamento de coordenadas, reparo geométrico ou simplificação do GeoJSON. A simplificação necessária ao desenho ocorre apenas nos tiles de exibição.

## Datas e limites de precisão

**A data de publicação da Overture e a data do download não são a data da imagem aérea.** O campo `imageryCaptureDate` permanece nulo. A versão Google informada pela fonte é `2024-04-16`, com `update_time` de `2023-05-01`; a versão Microsoft é `2026-08-11`, sem `update_time`; a versão da base OSM utilizada pela Overture é `2026-09-06`. Esses valores foram preservados conforme recebidos e não foram reinterpretados como datas de captura ou vistorias.

Os contornos derivados de imagens são previsões: podem omitir prédios, incluir objetos parecidos com telhados, apresentar deslocamentos e representar construções antigas. Um contorno pode representar o telhado, não a projeção cadastral da parede. São feições para mapa, **não um cadastro oficial validado**, limite de lote, propriedade, ocupação confirmada ou contagem garantida de imóveis. A própria Overture [descreve limitações de qualidade das fontes de aprendizado de máquina](https://docs.overturemaps.org/guides/buildings/).

## Tiles locais e desempenho

A aplicação deve carregar os tiles MVT locais, não os 107 MB do GeoJSON inteiro:

```text
/tiles/buildings/{z}/{x}/{y}.pbf
source-layer: buildings
minzoom: 11
maxzoom: 16
```

Os 729 tiles ocupam 117.785.969 bytes no conjunto, porém são buscados apenas conforme a área visível. Os níveis 11–16 têm respectivamente 2, 6, 12, 36, 144 e 529 tiles. Todo tile que intercepta os limites declarados tem um arquivo MVT válido; o único sem edificações contém uma camada `buildings` vazia de 18 bytes. Isso evita que um servidor estático com fallback de aplicação devolva HTML no lugar de um tile ausente. Os maiores arquivos de cada nível têm aproximadamente 16,9 MB, 10,1 MB, 2,9 MB, 902 kB, 318 kB e 89 kB. O estilo pode começar a desenhar as edificações em zoom 14 para evitar carregar detalhes de centenas de milhares de telhados em um panorama muito aberto. O nível 16 pode ser ampliado pela renderização em zooms superiores.

Cada feição no MVT contém `gers_id`, `source_datasets` (nomes separados por `; `), `name` quando disponível e `height` em metros quando disponível. Não se inventam nomes nem alturas. O formato de tile não carrega arrays de proveniência: a origem completa permanece nos arquivos GeoJSON e nos metadados.

`scripts/tile-buildings.mjs` usa `geojson-vt` e `vt-pbf`, extensão 4096, buffer 64, `indexMaxZoom: 12`, `maxZoom: 16` e tolerância 0,5 unidade de tile. A projeção Web Mercator, a quantização e o recorte de tiles alteram apenas o desenho. A tolerância corresponde a uma fração de pixel; o nível máximo conserva os vértices antes da quantização. O manifesto registra cada arquivo e tamanho; o TileJSON descreve a camada e o intervalo de zoom. Os metadados registram tempo de geração, bytes, parâmetros e contrato de atributos.

## Reproduzir e validar

Usar Python 3.12+ e Node 20+. A instalação abaixo fica no projeto. O diretório `.runtime/` não deve ser versionado.

```powershell
python -m pip install --target .runtime/overture -r scripts/requirements-buildings.txt
python scripts/fetch-buildings.py --release 2026-10-21.0
node --max-old-space-size=4096 scripts/tile-buildings.mjs
python scripts/validate-buildings.py
node scripts/validate-building-tiles.mjs
```

Para regenerar apenas a partir do material já coletado:

```powershell
python scripts/fetch-buildings.py --offline
node --max-old-space-size=4096 scripts/tile-buildings.mjs
```

Omitir `--release` consulta a edição mais recente. A Overture conserva apenas edições recentes no serviço; o bruto local permite reproduzir este recorte quando essa edição deixar de estar disponível.

Validação executada: zero geometrias inválidas, vazias ou IDs GERS duplicados; todas as 269.728 geometrias interceptam o recorte; todas as coordenadas no GeoJSON são idênticas às originais. Todos os 729 tiles previstos nos limites declarados existem e foram decodificados, incluindo a camada vazia; as feições contêm polígonos, identificadores e origem; os tiles de zoom 16 contêm todos os 269.728 IDs distintos.

## Possível fonte municipal

A Prefeitura oferece o [Mapa Geo 360° Goiânia](https://www.goiania.go.gov.br/sing_servicos/geoprocessamento/) e descreve o uso do SIGGO pela [Gerência de Geoprocessamento](https://www.goiania.go.gov.br/sefaz/departamentos/gerencia-de-geoprocessamento/). São caminhos para buscar uma futura base municipal de edificações com metadados e condições de reutilização claras. Nesta atualização não foi estabelecido um pacote aberto de contornos com licença de redistribuição adequada; nenhuma camada cadastral municipal nem informação de proprietário foi incorporada. Dados municipais cadastrais, quando obtidos licitamente e com qualidade documentada, exigem avaliação própria de data, geometria, licença e atribuição.
