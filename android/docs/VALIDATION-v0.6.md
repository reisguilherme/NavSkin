# NavSkin 0.6.0 · fontes, símbolos e calibração

Validado em 27/10/2026. APK `0.6.0-debug`, código 5, nome **NavSkin**. Application ID e certificado debug anteriores preservados para atualizar a instalação existente.

## Implementação

- GTA: Pricedown histórica autêntica na UI e nos rótulos, com contorno preto/branco e hierarquia de nomes. Urbano `#9C989C`, prédios `#E0E0DC`, vegetação `#28681C`, vias pretas. Nomes de ruas têm prioridade de colocação sobre legendas opcionais de POIs.
- RDR2: Hapna na UI/POIs e RDR Lino nas vias/localidades; são conversões extraídas documentadas. Não se afirma que RDR Lino seja a identificação exata do lettering geográfico original.
- Sprites originais dos jogos nos subtipos equivalentes; adaptações próprias para categorias sem equivalente. [Procedência, hashes e avisos](../../docs/game-assets-provenance.md).
- Fontes TTF para a UI e glifos SDF reais para o mapa. Noto cobre caracteres que faltam às fontes selecionadas. Android usa interceptor OkHttp para servir os glifos locais sem trocar o loader HTTPS por um loader de assets.
- Setas de mão única respeitam o campo original `oneway`, inclusive direção inversa. Ausência de restrição não gera seta.
- Nove filtros, preservando os seis IDs anteriores. Ensino, cultura e culto vêm do mapa mundial. O snapshot offline único mantém 1.571 POIs e informa explicitamente que esses três grupos não estão incluídos; não foram misturados snapshots de datas diferentes.
- Menus com conteúdo rolável e ação **Explorar mapa** fixa, inteira acima da navegação do sistema. Câmera, GPS, rota, salvos e filtros independentes preservados.

## Evidências finais

- **127 testes JVM**, 21 suítes, zero falhas.
- **9 testes nativos**, zero falhas/skip, em Android API36 com GPU host. Incluem os três temas, modos noturnos, fonte/POIs/direções em São Paulo, Praça Universitária em Goiânia, troca de cobertura/câmera/salvo, fluxo completo dos nove filtros, rotação e reprodução identificada como SIMULAÇÃO.
- APK e AAB compilados; lint com **zero erros e 33 avisos**. [Log final](logs/v0.6/release-validation.txt).
- **58 testes Node** aprovados para estilos/dados/arte, identidade real dos TTFs, glifos distintos do Noto e recursos iOS.
- **85 XCTest do núcleo Swift** aprovados no Linux e **31 testes Python** do empacotador. O projeto iOS passou por resolução Foundation e parser Swift; Apple SDK, simulador e iPhone permanecem pendentes no Mac.
- [Capturas reais](../screenshots/v0.6/README.md), produzidas pelo renderer nativo e não por mockup. As prévias do carrossel usam essas capturas.

As tentativas anteriores registram correções de teste e do painel: retorno ao primeiro filtro exigiu nested scrolling; o alvo de toque precisava excluir a barra do sistema; a ação final foi fixada fora do scroll. O log final acima, com os nove cenários completos, é a evidência de aprovação.

## Artefatos locais

| Arquivo | Bytes | SHA256 |
|---|---:|---|
| `NavSkin-v0.6.0-debug.apk` | 122842521 | `3A8AAF06DE28F95ED27BC55A9F64648FA7D7383B434BA890A59508119D191F64` |
| `NavSkin-v0.6.0-debug.aab` | 90644637 | `027EC83A8DE9372632DEB5B4EA792430B1431E6FDAEAE9C36D89226D89ADA2C5` |

Certificado SHA256 `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. Binários e pacote original permanecem na entrega local, separados do código versionado. O ZIP `NavSkin-game-assets-0.6.0.zip` restaura a árvore de recursos ao ser extraído na raiz do repositório. O ZIP iOS completo já inclui esses recursos.

Não houve teste físico de direção, DHU, central Android Auto ou consulta Geoapify autenticada nesta versão. Os templates Android Auto usam a fonte do host; o mapa utiliza as fontes temáticas do NavSkin. Não há voz ou trânsito ao vivo.
