# Validação do MVP Android

> Histórico da versão 0.2.0. A atualização de interface e três temas está documentada em [VALIDATION-v0.3.md](VALIDATION-v0.3.md).

Entrega de **26/10/2026** (Brasília). Versão `0.2.0-mvp-debug`, pacote `br.com.gamemap.goiania.dev`.

**O APK foi compilado, assinado, instalado e aberto no emulador. O mapa nativo foi testado no telefone emulado e em uma Surface de teste. Não houve teste no DHU nem em central física.** O usuário informou não ter telefone Android disponível; o AVD AOSP não contém o host Android Auto. Portanto, o critério de aceite de conectar ao carro permanece pendente.

## Artefatos

| Arquivo | Uso | Tamanho |
| --- | --- | ---: |
| [gamemap-goiania-mvp-debug.apk](../artifacts/gamemap-goiania-mvp-debug.apk) | Instalação direta para desenvolvimento | 116.382.691 bytes (111 MiB) |
| [gamemap-goiania-mvp-debug.aab](../artifacts/gamemap-goiania-mvp-debug.aab) | Bundle de desenvolvimento; não se instala diretamente | 85.092.151 bytes (81,2 MiB) |
| [native-mvp-tests.xml](../artifacts/native-mvp-tests.xml) | Quatro testes instrumentados, zero falhas | JUnit XML |
| [unit-tests/](../artifacts/unit-tests/) | 14 testes JVM, zero falhas | JUnit XML |
| [lint-results-debug.xml](../artifacts/lint-results-debug.xml) | Zero erros e 43 avisos | Android Lint |

SHA-256 do APK:

```text
9250FF72A351CE62A78EC5D1AD133316CEA7163167BD3EDE23EFEEC66836EB57
```

SHA-256 do AAB:

```text
6B8D02A617C13EFF442A2B8A6672C7DD3E6A1CB0D5AC4AD48A01C651429BE1AB
```

`apksigner verify --verbose` confirmou assinatura APK v2 com um signatário de desenvolvimento. O APK universal contém `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`; declara Android mínimo 26 e alvo 36. A instalação por ADB retornou `Success`; a abertura da Activity principal retornou `Status: ok`, `LaunchState: COLD`.

O AAB foi gerado por `:app:bundleDebug`. Não houve upload para o Google Play, revisão ou publicação. O bundle facilita uma futura instalação por origem confiável usando entrega por arquitetura, enquanto o APK universal serve à instalação local. Consulte o [roteiro DHU/carro](TESTING.md) antes de tentar a central.

## Matriz de evidências

| Item | Resultado observado | Limite |
| --- | --- | --- |
| Projeto Kotlin, serviço POI e templates Car App API 7 | Compilados; builders reais da CAL 1.7.0 exercitados em testes | Nenhum host Android Auto conectado |
| Surface nativa | MapLibre OpenGL → TextureView → VirtualDisplay → ImageReader produz pixels reais | Surface criada pelo teste, sem DHU |
| Dois temas no mesmo enquadramento | GTA SA e Rosa Pop inspecionados em 800 × 480 | Escala/legibilidade da central física pendentes |
| Modo noturno | Rosa Pop noturno capturado; quatro variantes verificadas nos assets | Mudança de modo enviada pelo host real pendente |
| Seis filtros | Cada toggle e camada semântica verificados; todos desligados/religados no Native | Interação pelo host real pendente |
| Áreas verdes | Permanecem com todos os ícones desligados | Não confundir ícone de parque com polígono cartográfico |
| Câmera | Centro/zoom mantidos ao trocar estilos/filtros; movimento nativo durante carregamento preservado | Gestos/entrada rotativa do host real pendentes |
| Zoom/pan/reset | APIs nativas, zoom pelo telefone e quatro ações do template conferidos | Ações executadas pela central ainda pendentes |
| Preferências | Compartilhamento local e invalidação dos templates testados; telefone recriado | Sincronia com duas interfaces físicas ativas pendente |
| Encerrar e abrir | APK final manteve Rosa Pop e 5/6 categorias após `am force-stop` e abertura fria | Não equivale à reconexão Android Auto |
| Redimensionamento/recriação | Surface 800 × 480/160 dpi, mesmo wrapper a 240 dpi, 1024 × 600/200 dpi e reconexão a 800 × 480 | Ciclo do harness, sem cabo/central |
| GPS | Nenhuma permissão de localização no manifesto do APK; nenhum pedido na execução | Sem acompanhamento do veículo por escolha do escopo |
| Internet | Estilos, fontes, sprites, geometrias e tiles usam assets locais | Não foi simulado modo avião; não há dependência remota de desenho |
| DHU | Não executado; ferramenta instalada e versão consultada | Requer telefone com Android Auto |
| Carro real | Não executado | Requer telefone, central compatível e origem de instalação confiável |

## Testes executados

`npm test`: **34 aprovados**, zero falhas. Abrange a PoC preservada, geometria/deduplicação OSM, seis categorias Android, sprites, cobertura de glifos, fontes locais, quatro estilos, filtros e integridade dos 729 tiles. Os 1.571 POIs têm IDs/procedência OSM; não há lugares fictícios. Pontos de polígonos respeitam contornos e furos. Isso valida os dados preparados, sem prometer atualização comercial dos estabelecimentos.

`:app:testDebugUnitTest`: **14 aprovados**, zero falhas. Inclui seleção de tema, cada um dos seis toggles do host, preferências persistidas, atualização de telas ativas, construção do mapa com quatro ações permitidas, linhas de POIs reais, estado vazio e visibilidade independente das camadas nos dois temas. Esses testes usam Robolectric e `TestCarContext`, não o host Android Auto.

`:app:connectedDebugAndroidTest`: **4 aprovados**, zero erros/falhas/ignorados, em **Android 16/API 36 x86_64**. O XML registra início em `2026-10-27T01:49:54 UTC` (26/10, 22:49:54 em Brasília):

1. `NativeCameraRaceTest`: altera a câmera nativa durante o início da substituição de estilo e verifica que a posição nova permanece.
2. `NativeMvpRenderingTest`: espera a geração exata do estilo ficar pronta; lê as seis visibilidades e o sprite no objeto `Style` nativo, captura GTA, Rosa, todos os ícones ocultos e Rosa noturno, mantendo a câmera.
3. `NativeMvpRenderingTest`: usa os controles reais do telefone emulado para zoom, tema e categoria, recria a Activity, verifica persistência e inspeciona as permissões do APK instalado.
4. `NativeSurfaceProbeTest`: valida pixels de parques/vias reais, área útil, pan/zoom, troca de DPI, redimensionamento, retenção de câmera, desconexão/recriação da Surface e fechamento repetido.

`:app:lintDebug :app:assembleDebug :app:bundleDebug`: **BUILD SUCCESSFUL**, zero erros de lint e 43 avisos. Os avisos tratam de versões mais novas de dependências, textos portugueses em código, sugestões KTX/logging, regras modernas de backup e serviço exportado. O serviço precisa ser descoberto pelo host e implementa `HostValidator`; o debug permite hosts para desenvolvimento. Não foi criado baseline para esconder erros. O único erro encontrado, um atributo visual de API 27 em recursos de API 26, foi corrigido antes de gerar os artefatos finais.

Após essa correção de recurso, o APK final foi instalado e exercitado novamente pelo ADB/UI Automator. Tema Rosa Pop e remoção de Parques foram escolhidos pela interface. Depois de encerrar o processo e abrir novamente, a tela exibiu **“Rosa Pop · 5/6 categorias ativas”**; o arquivo local de preferências confirmou `rosa` e os outros cinco grupos. Evidências: [hierarquia após reabrir](../artifacts/phone-reopened-ui.xml) e [captura](../screenshots/phone-reopened.png).

O `aapt dump permissions` do APK final lista apenas `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_SURFACE`, `MAP_TEMPLATES` e a permissão de receptor interno criada pelo AndroidX. As permissões fine/coarse herdadas da biblioteca foram removidas no merger; não há localização em segundo plano, categoria NAVIGATION, GPS, rotas, busca ou voz no aplicativo.

## Capturas reais

As imagens abaixo são do **emulador/harness nativo**, sem simulação visual de uma central. Não são capturas do DHU nem do Android Auto.

| Imagem | Evidência |
| --- | --- |
| [GTA SA](../screenshots/native-mvp-gta.png) | Surface 800 × 480, enquadramento inicial |
| [Rosa Pop](../screenshots/native-mvp-rosa.png) | Mesma câmera e dados, sprites próprios |
| [Rosa sem ícones](../screenshots/native-mvp-rosa-icons-hidden.png) | Verde, água, vias, prédios e nomes geográficos preservados |
| [Rosa noturno](../screenshots/native-mvp-rosa-night.png) | Variante noturna do mesmo tema |
| [Telefone GTA](../screenshots/phone-gta.png) / [Rosa](../screenshots/phone-rosa.png) | Preview e controles nativos |
| [Temas](../screenshots/phone-theme-dialog.png) / [Categorias](../screenshots/phone-categories-dialog.png) | Controles complementares no telefone |
| [Parques ocultos](../screenshots/phone-rosa-parks-hidden.png) | Estado persistido após recriar a Activity |
| [Surface redimensionada](../screenshots/native-surface-probe-resized.png) / [recriada](../screenshots/native-surface-probe-reconnected.png) | Prova mínima de ciclo de vida |

No zoom inicial 14,4, parques, postos e saúde já têm ícones; alimentação/mercados aparecem a partir de 14,5 e estacionamentos a partir de 15. Aumente o zoom para esses grupos. Colisão e padding limitam a densidade; nomes de estabelecimentos aparecem a partir de 16,8. Não se alteram posições reais para preencher a tela.

## Ambiente gráfico e limitações restantes

A prova mínima de polígonos/vias passou inicialmente em SwiftShader. A inspeção completa revelou símbolos transparentes nesse backend: corresponde à falha conhecida do MapLibre OpenGL no Android x86_64, [PR oficial #4625](https://github.com/maplibre/maplibre-native/pull/4625). O APK não recebeu patch binário. Os testes completos e as capturas com ícones foram feitos com **`-gpu host`**, NVIDIA RTX 5060, OpenGL ES 3.1 e CPU WHPX. Os [logs e argumentos do emulador](../../docs/emulator-status.md) distinguem as duas execuções. Esse resultado não caracteriza o desempenho nem garante compatibilidade da GPU de um telefone ainda não testado.

Os dados são um recorte de Goiânia `[-49.325, -16.755, -49.205, -16.640]`. Fora dele, a cartografia pode ficar vazia. OSM e Overture têm datas próprias; contornos inferidos não são cadastro municipal. Créditos e licenças acompanham o APK e permanecem visíveis.

Faltam testar descoberta do serviço no Android Auto, ações/radio/toggles efetivamente renderizados pelo host, modo de deslocamento, restrições em movimento, dia/noite enviado pela central, reconexão física e diferentes telefones/resoluções. Não foram produzidas capturas artificiais para substituir esses testes. O [roteiro de teste](TESTING.md) explica DHU e instalação por Google Play para um veículo real; instalar o APK por USB sozinho não garante que aparecerá na central.

O histórico da prova mínima está em [surface-probe.md](surface-probe.md); o APK antigo `gamemap-surface-probe-debug.apk` é somente um artefato de auditoria anterior ao MVP. Use o APK `gamemap-goiania-mvp-debug.apk` desta entrega.
