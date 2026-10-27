# Pacote POI das seis categorias Android

Este pacote de dados é independente dos POIs da versão web. Contém **1.571 pontos** OSM no recorte `[-49.325, -16.755, -49.205, -16.640]`, após unir cinco representações duplicadas. Sua preparação não comprova renderização no Android Auto. A integração completa com as telas do telefone e do carro fica condicionada à validação do mapa mínimo na superfície do host.

## Categorias

Cada elemento OSM recebe exatamente um `kind` semântico:

| `kind` | Grupo | Etiquetas selecionadas | Pontos |
| --- | --- | --- | ---: |
| `parks` | Parques e jardins | `leisure=park/garden` | 571 |
| `fuel` | Combustível | `amenity=fuel` | 201 |
| `food` | Alimentação | `amenity=restaurant/cafe/fast_food` | 308 |
| `parking` | Estacionamentos | `amenity=parking` | 152 |
| `health` | Saúde | `amenity=hospital/clinic/doctors/pharmacy` | 245 |
| `shops` | Mercados e conveniências | `shop=supermarket/convenience` | 94 |

Os grupos são uma adaptação para os filtros do aplicativo; as etiquetas OSM originais continuam preservadas. A consulta dedicada inclui os mercados e as conveniências, ausentes da seleção web anterior. Não se infere estabelecimento pelo nome. As etiquetas de comércio usadas estão descritas na documentação comunitária de [supermercados](https://wiki.openstreetmap.org/wiki/Tag:shop%3Dsupermarket) e [conveniências](https://wiki.openstreetmap.org/wiki/Tag:shop%3Dconvenience).

Se um mesmo elemento tiver etiquetas de grupos diferentes, a prioridade determinística é `fuel`, `parking`, `health`, `food`, `shops`, `parks`. Nesta resposta, dois elementos combinavam posto e conveniência; ambos aparecem uma única vez em `fuel`. IDs e decisões estão em `categoryConflicts` nos metadados. Isso não elimina a loja quando ela tem um elemento OSM próprio.

## Origem e datas

- Fonte: [OpenStreetMap via Overpass API](https://overpass-api.de/api/interpreter).
- Download: **27/10/2026, 00:50:44 UTC**, equivalente a **26/10/2026, 21:50:44 em Brasília**.
- Data da base informada pelo servidor: **27/10/2026, 00:49:32 UTC**.
- Resposta original: `data/android/pois.json`, com **1.576 elementos**.
- Consulta: `data/android/pois.overpassql`; procedência e hashes SHA-256: `data/android/pois.provenance.json`.
- Resultado: `android/maps/pois.geojson`; metadados completos: `android/maps/pois-metadata.json`.
- Atribuição: **© OpenStreetMap contributors**, sob [ODbL](https://www.openstreetmap.org/copyright).

Data de download, data da base e geração local são campos distintos. A presença de um ponto, seu nome, acesso ou horário são etiquetas de colaboradores; não houve verificação de funcionamento, disponibilidade atual ou abertura ao público. Não há GPS, coordenadas do aparelho, distância até o usuário, rota ou navegação nesse processo.

## Geometria, união e procedência

O adaptador chama as funções puras de `scripts/build-pois.mjs`, sem alterar a implementação web. Nós mantêm sua coordenada original. Polígonos e relações completos recebem um ponto interno, fora dos buracos e dentro do recorte; centro de caixa delimitadora não substitui geometria ausente. A resposta processada não teve omissões nem avisos.

A união exige nome normalizado não vazio e o mesmo subtipo normalizado. Ela aceita nó/área com contenção ou distância até 12 m, ou relação com membro etiquetado equivalente. Nós distintos de uma rede permanecem separados. `hospital` e `pharmacy` não são fundidos com `clinic/doctors`; o normalizador existente trata `doctors` como `clinic`. `restaurant`, `cafe` e `fast_food` preservam subtipos distintos mesmo dentro de `food`.

Para reaproveitar o construtor existente, `shop=supermarket/convenience` recebe uma etiqueta temporária `amenity=bank` somente na cópia em memória. Antes da exportação, o adaptador restaura todas as etiquetas reais e define `kind=shops`. Bancos reais não entram na seleção Android. As duas etiquetas de comércio compartilham a regra de união de `shops`; o teste cobre a ausência da etiqueta temporária no resultado.

As cinco uniões foram:

| Nome presente no OSM | Grupo | ID mantido | ID unido |
| --- | --- | --- | --- |
| Pit Dog Asa de Águia | `food` | `node/12445666758` | `way/1390766616` |
| Posto de Saúde da Família Vila Santa Helena | `health` | `node/13133955197` | `way/253368653` |
| Pro Brazilian | `shops` | `node/5635011579` | `way/590172297` |
| Supermecado Planalto | `shops` | `node/5913704445` | `way/626403033` |
| McDonald's | `food` | `node/12168588381` | `way/764268046` |

Grafias acima foram mantidas como retornadas pela fonte.

## Esquema

Cada feição é `Point`, com:

- `osm_id`, `source_id`, `source_ids`, `source_url` e `source_urls`;
- `kind` pertencente aos seis grupos, `name` original ou vazio e `coordinate_source`;
- etiquetas OSM originais também expostas como propriedades, como `amenity`, `leisure`, `shop`, `access` e `addr:street`;
- `raw_tags`, contendo todas as etiquetas originais do elemento mantido;
- `source_tags`, contendo `{osm_id, tags}` para cada representação mantida ou unida.

Os campos reservados do esquema têm precedência sobre uma etiqueta OSM de mesmo nome; seu valor original continua em `raw_tags`. A resposta bruta preserva também versão, usuário e timestamps informados pelo OSM. Conteúdo textual e links da fonte são dados externos e devem ser tratados como tal pelo consumidor; `source_url` é construído a partir do tipo/ID OSM.

## Reprodução e validação

```powershell
# Nova consulta pública dedicada e reconstrução do pacote:
node scripts/android-pois.mjs --fetch

# Reconstrução sem rede, a partir da resposta/procedência salvas:
node scripts/android-pois.mjs

# Regras do adaptador, dados publicados e regressão do construtor reutilizado:
node --test tests/android-pois.test.mjs tests/pois-data.test.mjs
```

É possível escolher um servidor público pela variável `OSM_OVERPASS_ENDPOINT`. Respostas parciais com `remark`, respostas vazias e IDs repetidos são recusados antes de substituir o cache. A reconstrução confere os hashes da consulta e da resposta e a data da base contra a procedência.

Validação executada: **14 testes aprovados**, incluindo mapeamento dos subtipos, prioridade única, preservação de etiquetas, união segura, polígonos com buracos, geometria incompleta e correspondência de todos os pontos Android à resposta original. Nenhum arquivo em `public/data` foi alterado por este adaptador.
