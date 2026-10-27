# Serviço de busca e rotas para navegação real

Revisão: **27/10/2026**. Fontes oficiais consultadas nesta data. Escopo: automóvel, instruções visuais, mapa MapLibre existente e uso pessoal. Preços e limites podem mudar.

## Decisão

Adotar **Geoapify** para geocodificação, busca de estabelecimentos e cálculo/recálculo de rotas. Preservar o renderizador, os dados cartográficos e os estilos existentes. A plataforma declara compatibilidade com MapLibre e permite armazenar resultados; não exige substituir o mapa pelos seus tiles. [Compatibilidade e armazenamento](https://www.geoapify.com/).

O serviço precisa de **conta e chave do usuário**. Não foi fornecida uma chave, nenhuma conta foi criada e nenhuma requisição autenticada foi validada. A configuração da chave é uma dependência externa concreta para usar busca/rotas reais. O aplicativo deve identificar essa ausência e continuar oferecendo GPS, mapa e dados locais, sem inventar uma rota. [Obter chave](https://apidocs.geoapify.com/docs/routing/#authentication-and-api-key).

Não usar o playground, chaves copiadas de exemplos nem servidores públicos de demonstração como infraestrutura do aplicativo. Não foi identificada entre as alternativas uma oferta hospedada de produção sem chave. Hospedar um motor próprio elimina a chave comercial, mas exige infraestrutura, importação/manutenção do grafo viário e outro serviço/índice de busca; não é gratuito em operação nem equivale ao mapa offline já existente.

## Custos e condições

| Provedor hospedado | Uso pessoal e quota gratuita | Próximo plano publicado | Condições relevantes |
| --- | --- | --- | --- |
| **Geoapify, escolhido** | US$ 0; **3.000 créditos/dia**, compartilhados entre APIs; até **5 requisições/s**; sem cartão | API 10: **US$ 59/mês**, 10.000 créditos/dia; impostos à parte | Free permitido em produção. Exibir atribuição Geoapify e das fontes. Sem SLA gratuito. |
| **GraphHopper** | € 0; **500 créditos/dia**, até 5 pontos por requisição; Free para uso não comercial | Basic: **€ 69/mês**, 5.000 créditos/dia, cobrança mensal; impostos à parte | Conta/chave obrigatórias. Termos autorizam explicitamente software de navegação e uso com qualquer mapa ou sem mapa. Cache de resultados apenas temporário no cliente; redistribuição da API exige acordo específico. |
| **openrouteservice / HeiGIT** | Standard gratuito com chave; documentação atual confirma Directions **2.000/dia e 40/min** | Sem plano pago padrão confirmado nesta revisão; há colaboração e instalação própria | Chave por pessoa; aplicação deve usar proxy ou cada usuário inserir sua própria chave. A página principal estava temporariamente indisponível; não adotar sem rever os termos atuais acessíveis na conta. |

Fontes: [Geoapify preços](https://www.geoapify.com/pricing/), [Geoapify termos, versão 5 de 02/02/2024](https://www.geoapify.com/terms-and-conditions/), [GraphHopper preços](https://www.graphhopper.com/pricing/), [GraphHopper termos, seções 3 e 5](https://www.graphhopper.com/terms/), [ORS FAQ atual](https://giscience.github.io/openrouteservice/frequently-asked-questions), [estado do site ORS](https://openrouteservice.org/), [termos HeiGIT](https://account.heigit.org/info/tos).

No Geoapify, rota simples de dois pontos abaixo de 500 km custa normalmente 1 crédito. `route_details`, elevação, desvios e distâncias maiores podem aumentar o custo. Não solicitar detalhes de limite de velocidade nesta etapa. Geocodificação simples custa normalmente 1 crédito; a quota é compartilhada, não 3.000 por endpoint. Aplicar debounce e cancelamento à busca, e limitar recálculos. [Custos por API](https://www.geoapify.com/pricing-details/), [custos de rota](https://apidocs.geoapify.com/docs/routing/#pricing).

Os termos gerais Geoapify usam redação mais restritiva sobre produção comercial que o FAQ atual de preços. Para o uso pessoal solicitado, isso não impede a escolha; uma distribuição comercial futura exige rever essa divergência. Atribuição deve permanecer legível também com estilos próprios: dados © OpenStreetMap contributors e link/crédito Geoapify, além de outras fontes exigidas. Não confundir permissividade de armazenamento com garantia de funcionamento offline. [Termos](https://www.geoapify.com/terms-and-conditions/), [FAQ de preços e atribuição](https://www.geoapify.com/pricing/).

## Contrato HTTP selecionado

- Rotas: `GET https://api.geoapify.com/v1/routing`, com `waypoints=lat,lon|lat,lon`, `mode=drive`, `units=metric`, `lang=pt-BR`, `details=instruction_details`, `traffic=approximated`, `format=geojson` e chave. Resposta: `FeatureCollection`; geometria `MultiLineString`, uma linha por perna. Distância em metros; `time` da rota, pernas e passos em segundos. Passos usam `from_index`/`to_index` na geometria da perna e instruções humanas/estruturadas. Não depender só do texto traduzido para identificar a manobra. [Referência de entrada/saída](https://apidocs.geoapify.com/docs/routing/).
- Endereços e nomes: `GET https://api.geoapify.com/v1/geocode/search`, `text`, `lang=pt`, `bias=proximity:lon,lat`, limite de resultados e chave. Geocodificação usa idioma de duas letras. Mostrar nome e endereço/região retornados, conferir coordenadas e confiança. [Geocodificação](https://apidocs.geoapify.com/docs/geocoding/).
- Estabelecimentos por categoria/nome: `GET https://api.geoapify.com/v2/places`, categorias, `name` opcional, filtro/bias espacial, `lang=pt` e chave. Resultados GeoJSON incluem localização e dados de endereço; categorias podem complementar a pesquisa textual. [Places](https://apidocs.geoapify.com/docs/places/).

**A ordem das coordenadas difere:** parâmetros de rota usam latitude,longitude; GeoJSON e filtros/bias usam longitude,latitude. Validar unidades e índices na conversão para o modelo interno.

`traffic=approximated` aplica velocidades estimadas para vias potencialmente congestionadas; **não contém trânsito ao vivo**, incidentes atuais nem condições para um horário específico. Exibir “ETA estimado, sem trânsito em tempo real”. [Explicação oficial do modelo](https://apidocs.geoapify.com/how-to/routing/traffic-route-times/).

## Cálculo remoto e acompanhamento no dispositivo

O Geoapify fornece o **plano da viagem**: caminho, passos, durações e instruções. O componente Kotlin compartilhado entre celular/Android Auto deve manter a **sessão em tempo real**, usando posições reais do Android. Esse componente não é fornecido pelo endpoint HTTP.

O motor local deve projetar cada leitura válida sobre uma janela coerente do progresso anterior, usando precisão, direção e continuidade para evitar saltos em cruzamentos, vias paralelas e loops. O marcador pode acompanhar a rota apenas com confiança suficiente; evidência persistente de desvio libera a posição real e dispara um recálculo controlado. A API de map matching não substitui esse acompanhamento e não deve ser chamada a cada atualização GPS.

ETA restante = duração dos passos posteriores + fração restante do passo atual pela distância percorrida na sua geometria. Horário de chegada = relógio atual + tempo restante. É uma aproximação intrapasso, baseada nas durações fornecidas, e deve preservar a distinção entre tempo restante e horário. Velocidade instantânea serve ao indicador de km/h; não deve ser a única base do ETA. Com sinal perdido ou rota inválida, suspender progresso/ETA e identificar o estado.

Recálculos precisam de limite de frequência, identificador de geração, cancelamento e rejeição de respostas obsoletas. Manobras de uma rota confirmadamente abandonada não podem continuar aparentando validade. Resultados armazenados servem a favoritos/recentes e recuperação controlada; ao retomar uma sessão, obter posição atual e recalcular quando necessário. A navegação depende de internet para pesquisar e calcular/recálcular, mesmo com mapa offline.

## Alternativas e SDK

GraphHopper é a alternativa mais direta: API de rotas/geocodificação madura e termos particularmente explícitos sobre navegação e mapas livres. Retorna geometria, instruções indexadas, distância e duração (milissegundos); a integração deve converter unidades. O crédito gratuito é menor e a restrição de cache é menos adequada a resultados persistidos. [API hospedada](https://docs.graphhopper.com/openapi), [formato do motor oficial](https://github.com/graphhopper/graphhopper/blob/master/docs/web/api-doc.md), [termos](https://www.graphhopper.com/terms/).

ORS tem direções, geocodificação Pelias e POIs, com formatos JSON/GeoJSON. A página oficial de staging informa geocodificação 1.000/dia e 100/min e POIs 500/dia e 60/min; isso deve ser reconfirmado no dashboard atual. Termos históricos oficiais em staging licenciam resultados CC BY 4.0 com atribuição ORS/OSM, sem vínculo a um renderizador; não substituem a revisão dos termos HeiGIT atuais. Esta incerteza e a indisponibilidade da documentação pública principal favorecem Geoapify nesta etapa. [Plano em staging](https://staging.openrouteservice.org/plans/), [termos em staging](https://staging.openrouteservice.org/terms-of-service/), [direções](https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/), [geocoder](https://giscience.github.io/openrouteservice/api-reference/endpoints/geocoder/).

MapLibre Native renderiza mapas; MapLibre Navigation SDK é outro projeto, com lógica de navegação. O README oficial atualmente exemplifica `navigation-core:5.0.0-pre13`, licença MIT e ausência de telemetria; a linha KMP ainda é pré-lançamento. Ele continua exigindo um servidor de rotas próprio ou contratado e um adaptador de formato. Não foi verificada compatibilidade integral com o MapLibre Native 13.7.1 deste aplicativo. Para este incremento, manter o renderizador e um modelo Kotlin independente evita uma migração adicional; considerar o SDK separadamente após prova de integração. [Projeto oficial](https://github.com/maplibre/maplibre-navigation-android).

## Limite desta verificação

Pesquisa documental, sem chave, cobertura de estabelecimentos/endereços em Goiânia ou cálculo autenticado testados. A validação posterior deve incluir rotas reais locais, sentidos únicos, retornos, vias paralelas, chegada, quota/falhas e Android Auto. Nenhuma validação em trânsito real ou central física é alegada.
