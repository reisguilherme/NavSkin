# NavSkin 0.7.0 · interface e densidade dos prédios

Validado em 27/10/2026. APK `0.7.0-debug`, código 6. Mantidos application ID, certificado debug e dados de atualização das versões anteriores.

## Resultado

- NavSkin, Temas/Filtros e menu no topo; busca inferior. Círculos de velocidade à esquerda e recentralização à direita, sem N/Rumo ou textos no controle de câmera.
- Salvos, GPS, orientação, zoom, chave e créditos ficam no menu retrátil. Não há card ocioso de posição nem faixa de atribuições sobre o mapa.
- Card de viagem com somente tempo, distância e chegada. Velocidade não é duplicada e precisão/diagnósticos ficam no menu. Textos e blocos centralizados na área segura, inclusive em paisagem com barra lateral do Android.
- GTA aumenta vias de 12,5 para 14,5 e nomes de locais de 13 para 15, preservando fonte original e halo. Rosa/RDR2 mantêm suas famílias.
- Marcador GTA usa radar_centre original. Os PNGs mantêm pixels, padding e hash; a densidade de imagem Android foi fixada em 160 para preservar o tamanho lógico em telas densas. Outros IDs e temas continuam disponíveis.
- Corrigida a ausência de Overture no modo Mundo em Goiânia. Os 729 tiles locais com 269.728 footprints substituem a camada mundial dentro da cobertura; o restante do planeta mantém os dados OpenFreeMap. Não foram inventados contornos nem baixado um novo pacote mundial.

## Evidências

- **127 testes JVM** em 21 suítes, zero falhas/erros.
- **10 testes nativos**, zero falhas/skip, em API36 com GPU host. Incluem disposição dos controles em pé/deitado, centralização pela área segura, acesso aos créditos no menu, filtros, três temas dia/noite, rotação, câmera, salvos e navegação identificada como SIMULAÇÃO. O card de ETA é inspecionado para excluir velocidade/GPS/diagnóstico.
- Cenário mundial consulta o serviço real e renderiza São Paulo e Praça Universitária, Goiânia. Na praça exige mais de 100 feições locais decodificadas, mais de 30 renderizadas e zero prédios da camada mundial duplicados na viewport.
- **62 testes Node** aprovados: estilos, categorias, arquivos e glyphs, limites da cobertura local e comparação de cada pixel do marcador GTA. Outros 53 desenhos mantêm os PNGs originais.
- APK/AAB compilados e assinados; lint: **zero erros e 35 avisos**. [Log final completo](logs/v0.7/release-validation.txt).
- Uma repetição focada mundial fechou o painel de ponto que o gesto automatizado podia abrir antes da captura do telefone; [log](logs/v0.7/world-capture.txt). O mapa continuou com o mesmo enquadramento e passou novamente.
- iOS: preflight de fontes/ícones, resolução Foundation e parser Swift passaram. A interface Apple não foi compilada neste Windows; [limites e próximos testes](../../ios/docs/VALIDATION.md).

A primeira tentativa do teste novo de menu recolhia o painel ao tentar voltar automaticamente ao início. O teste passou a procurar a opção com gestos curtos para frente; a suíte final completa acima passou após todos os ajustes pedidos. Logs anteriores permanecem identificados como histórico do diagnóstico.

## Entrega

| Artefato | Bytes | SHA256 |
|---|---:|---|
| `NavSkin-v0.7.0-debug.apk` | 122783823 | `277EF00FC914695EDE299EAE6E2FFCC79152D510EA70DBF4F6AC353483979DCB` |
| `NavSkin-v0.7.0-debug.aab` | 90648172 | `EB3551A6CEAAD245A2D660B9719686F1A18A73D6DABA44043F9A3330E011698C` |

Certificado SHA256 `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. [Capturas reais](../screenshots/v0.7/README.md). Fontes e sprites originais continuam no pacote local separado, incluindo o novo radar_centre. O ZIP iOS completo já contém todos os recursos para o Mac.

Não houve direção física, DHU, central Android Auto ou consulta Geoapify autenticada nesta versão. Cenas de navegação das capturas são SIMULAÇÃO; velocidade e rota não são prova de GPS real. No Android Auto, fontes dos menus oficiais são controladas pelo host.
