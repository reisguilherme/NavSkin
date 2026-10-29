# NavSkin: câmera adaptativa e trânsito opcional

> **For agentic workers:** use subagent-driven-development to implement the independent Android, iOS and traffic work below. Root owns integration, phone UI and verification.

**Goal:** permitir girar o mapa, acompanhar o rumo com suavidade e aproximar curvas/retornos, com navegação mais legível e trânsito TomTom opcional.

**Architecture:** política de câmera testável separada do MapLibre, gestos com prioridade sobre acompanhamento, renderização incremental dos overlays. Trânsito é camada independente da rota Geoapify, ativada somente com chave TomTom e opção ligada.

**Tech Stack:** Kotlin/Android Views/MapLibre Native; Swift/SwiftUI/MapLibre; serviços HTTPS TomTom Orbis; Android Keystore e iOS Keychain.

**Spec:** pedido do usuário em 29/10/2026, incluindo aceite explícito da chave TomTom separada. Decisões de implementação abaixo são do agente.

## Requisitos e limites

- Manter temas, fontes e ícones existentes, topo NavSkin/temas/filtros/menu, pesquisa inferior, velocidade e recentralização circulares.
- Card de ETA contém somente tempo, distância e chegada; orientação detalhada fica no topo.
- Rotação e pan manuais suspendem acompanhamento até recentralizar; nova sessão pode retomar acompanhamento. Recalcular ou trocar tema não deve roubar a câmera.
- Zoom usa velocidade e proximidade da manobra: baixa velocidade aproxima; via rápida afasta; curvas e retornos aproximam antes da manobra. Não classificar uma via como urbana sem evidência.
- Rumo estabilizado quando parado; interpolação pelo menor ângulo e transições sem reiniciar por ticks de idade do GPS.
- Sem posição GPS extrapolada ou indicação de trânsito/ETA fictícia. GPS ruim mantém comportamento de indisponibilidade.
- TomTom opcional, chave local, sem conta/plano contratado. Fluxo e incidentes independentes do ETA Geoapify. Sem promessa de radares completos ou fase de semáforos ao vivo.
- Não há Mac: testes Swift Core e análise de fontes não substituem build Xcode. Android Auto real continua condicionado à distribuição confiável já diagnosticada.

## Revisão prioritária

GPS parado ou rumo 359°→1°; gesto durante animação/troca de tema; recálculo com câmera manual; rota longa e ticks sem mudança; resposta de trânsito atrasada após desativação/troca de chave.

## Tarefas

- [x] Android câmera: testar política de velocidade/manobra/rumo; habilitar rotação e animação; preservar sessão/gestos; reduzir reconstrução de GeoJSON e estilos.
- [x] iOS: política equivalente com testes Core, câmera/overlays e apresentação de navegação.
- [x] Android UI: ícone vetorial da próxima manobra e distância maior; ETA em três colunas; evitar relayout e atribuições de texto inalterado a cada tick.
- [x] Trânsito: confirmar APIs oficiais, guardar chave separada, carregar fluxo/incidentes com limitação de frequência e área, limpar dados ao desativar, erros sem divulgar chave, configuração e créditos no menu.
- [x] Verificação: testes JVM/Swift/Node pertinentes, build APK, testes nativos/replay e screenshots quando emulador disponível; revisão independente; gerar pacote iOS e documentação com limites reais.

## Validação e entrega

Não medir fluidez por testes unitários. Usar replay nativo para conferir rotação, acompanhamento, manobras e controles; relatar separadamente otimizações de código e desempenho observado. Sem chave TomTom não afirmar validação de cobertura/conta em produção. Preservar APK anterior e gerar nova versão instalável com a mesma assinatura local.
