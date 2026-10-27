# Validação da PoC

## Atualização: edificações Overture e ícones

Em 26/10/2026 foram integrados 269.728 contornos Overture (release 2026-10-21.0), 729 tiles vetoriais locais e 1.789 POIs OSM. As datas de cada fonte permanecem distintas: o endpoint de POIs respondeu com snapshot de 15/07/2026, explicitado no painel.

- **18 testes automatizados** passaram, incluindo classificação de POIs, pontos dentro de polígonos/furos, deduplicação com proveniência, estilos com sprites locais e exclusividade OSM/Overture.
- Validações geométricas Overture e de tiles passaram: zero polígonos inválidos, todos os IDs presentes no zoom 16 e todos os 729 tiles do bbox disponíveis. A borda nordeste inclui um MVT vazio válido, evitando fallback HTML/erro de protobuf.
- Build de produção examinado em aba separada: Centro e Vaca Brava, modo neutro e GTA SA, zoom 17,4 com nomes de serviços legíveis, filtros de categoria, botão global e seletor de fonte. Console sem erros ou warnings após essas operações.
- Clique no símbolo do Parque Vaca Brava abriu nome, categoria e link correto `openstreetmap.org/way/159590062`.
- Troca de fonte Overture → OSM manteve a câmera `16.6809° S / 49.2563° O`, zoom `15.1`. Novas capturas: `screenshots/05-centro-overture.jpg` e `screenshots/05-centro-osm.jpg`.
- Rótulos de serviços foram afastados de seus símbolos para evitar colisão com o próprio ícone. Os três parques/praça de referência mantêm símbolos explícitos ao lado dos nomes. Créditos Google/Microsoft/Overture são exibidos junto ao OSM.

## Registro da primeira versão

Executada em **26/10/2026**, horário de São Paulo, com o build de produção servido localmente pelo Vite. Renderer **MapLibre GL JS 6.16.0**, navegador integrado do Codex. As capturas finais foram produzidas pela API de screenshot do navegador, sem pós-processamento.

## Verificações executadas

- `npm test`: **9 testes**, incluindo identidade das fontes nos dois estilos, ordem de contornos/preenchimentos, níveis de travessia, corredores BRT, ajustes sem alteração dos filtros/geometria, seleção correta de Flamboyant, schema MapLibre e cobertura de todas as feições de vias/áreas por camadas de desenho.
- `node scripts/validate-geodata.mjs`: **28.465 feições** validadas, com origem e vértices conferidos contra o cache bruto OSM, fechamento de polígonos, furos, classes, metadados e cinco lugares obrigatórios.
- `npm run build`: compilação de produção concluída. Aviso informativo de bundle maior que 500 kB devido ao renderer; não é erro de build.
- Os dois estilos retornaram **zero erros** no validador de estilo da versão instalada do MapLibre.
- Console da aba de produção: **nenhum erro ou warning** depois da carga e dos testes funcionais. O problema inicial de URL do worker na integração Vite/MapLibre foi corrigido com um worker local explícito e conferido novamente no build.
- Três câmeras examinadas nos dois estilos; painel fechado para comparação. Zoom, arraste real, alternância, presets, retorno, painel, edição de cor, largura de 100% a 160%, ocultação de rótulos e restauração exercitados no navegador.
- A edição de cor alterou solo urbano para `#AAAAAA` e a restauração voltou aos tokens. As capturas finais usam os valores padrão.
- Layout a 390×844 conferido no DOM: largura do documento e do conteúdo de **390 px**, mapa de **370 px**, sem overflow horizontal. A inspeção visual documentada nas capturas é de desktop.

## Câmeras e capturas

Todos os seis arquivos têm **1280×720 px**. Cada par foi capturado após o mapa informar o fim da movimentação e da renderização; a alternância preservou as coordenadas e o zoom apresentados abaixo. O registro também está em `screenshots/cameras.json`.

| Enquadramento | Centro aproximado (WGS84) | Zoom | GTA SA | Neutro |
|---|---|---|---|---|
| Geral | -49.2650, -16.6912 | 12.8 | `screenshots/01-geral-gta.jpg` | `screenshots/01-geral-neutro.jpg` |
| Centro / Praça Cívica | -49.2563, -16.6809 | 15.1 | `screenshots/02-centro-gta.jpg` | `screenshots/02-centro-neutro.jpg` |
| Vaca Brava | -49.2708, -16.7097 | 15.4 | `screenshots/03-vaca-brava-gta.jpg` | `screenshots/03-vaca-brava-neutro.jpg` |

Galeria estática: `screenshots/index.html`, também servida em `/comparacoes/`. Captura adicional do painel: `screenshots/04-painel-referencias.jpg`.

## Correções feitas após inspeção e revisão

1. **Worker:** o empacotamento ESM do MapLibre 6 precisava de URL local explícita no Vite. O worker agora integra o build; não depende de CDN.
2. **Densidade:** a primeira visão mostrava muitos pontos de parques e rótulos de bairros. Os marcadores agora se limitam aos marcos verificados; bairros entram a partir de zoom 14,2, com colisão de textos.
3. **Toponímia:** seleção pelo trecho “Flamboyant” inicialmente podia encontrar o bairro homônimo. Os marcos `featured=true` têm prioridade; há teste com o ID real do parque `way/220688361`. Centro preserva o alias documentado de Setor Central.
4. **BRT:** `highway=busway` ganhou classe explícita, preservando os 244 segmentos existentes nos dados.
5. **Passarelas e trilhos:** caminhos e ferrovias passaram a respeitar a ordem física de nível, juntamente com vias. Exemplo conferido no código/dados: passarela `way/247186724` sobre a Rodovia Transbrasiliana, perto de -49.22938, -16.67376. Não houve captura dedicada dessa travessia.
6. **Limites e inicialização:** o renderer usa o `bbox` real dos metadados. Erros de inicialização WebGL são tratados e a espera de carga tem prazo de 30 segundos. O caminho de erro foi revisado no código; não foi simulado desabilitando a GPU do navegador do usuário.
7. **Referência de pausa:** a pesquisa adicional encontrou a screenshot urbana do guia oficial BradyGames PS2, página 18. Ela passou a ser a referência principal; a medição de HEX no mosaico do radar continua explicitamente complementar.

## Avaliação visual e limites

**Paleta e contraste:** preto viário, cinza urbano, verdes planos e água azul acinzentada formam uma leitura diferente do mapa neutro e compatível com características observadas da pausa original. Os HEX continuam sendo estimativas de uma imagem publicada, não constantes oficiais PS2.

**Vias:** hierarquia visível, cruzamentos contínuos e classes menores entrando por zoom. As larguras são adaptações em pixels à cidade real; não foram medidas de uma textura original em escala equivalente. Vias com duas pistas mantêm seus dois eixos reais, podendo produzir corredores visualmente mais pesados.

**Áreas e composição:** parques, lagos e edifícios mantêm posições e contornos reais. A principal perda de semelhança está na proporção de massas claras: há só 2.963 edifícios mapeados neste recorte, e os espaços urbanos cinza não foram preenchidos com quarteirões fictícios. Na visão geral, edifícios são omitidos para reduzir ruído; aparecem ao aproximar.

**Fidelidade geral:** interpretação reconhecível de características cartográficas, com fidelidade parcial. Não é reprodução pixel a pixel, nem conversão de Goiânia em um cenário fictício. A comparação visual deve continuar sendo o critério do usuário. Melhor cobertura real de edifícios, generalização controlada de conjuntos existentes e uma referência digital PS2 de varejo com procedência comprovada são os passos que mais podem reduzir a diferença.

Nenhuma textura, fonte ou ícone proprietário foi extraído. Rótulos Arial, marcadores simples e interface foram adaptados para avaliação. Não foram adicionados relevo, ruído, granulação ou decoração de jogo.
