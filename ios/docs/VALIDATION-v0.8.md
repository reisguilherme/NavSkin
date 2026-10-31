# NavSkin iOS 0.8.0 — validação em 29/10/2026

Projeto preparado para Xcode, com recursos locais e versão 0.8.0. Não é um IPA instalável.

## Verificações executadas

- **107 testes XCTest do núcleo Swift**, zero falhas, no backend nativo Swift 6.4.1/Linux. Incluem câmera adaptativa, rumo, posições interpoladas, limites e decodificação TomTom e regressões da sessão de navegação. [Log](logs/v0.8/swift-core-tests.txt).
- Validação Foundation de recursos, estilos, catálogos, referências OpenStep do projeto e XML; parser dos fontes Swift/SwiftUI e testes Apple. [Log](logs/v0.8/source-validation.txt).
- Revisão independente da API MapLibre 6.31.0 e do código de tráfego/câmera. Corrigidos incidentes após zoom-out, recuperação de falha temporária, callbacks de configuração anterior e geometria de incidentes lineares.
- Projeto Xcode regenerado com todos os fontes do app. O ZIP inclui as fontes e os ícones originais preparados, sem configuração de assinatura pessoal.

## Comportamento preparado

Rotação por gesto, câmera com rumo/zoom adaptativos e marcador interpolado entre posições realmente recebidas, sem extrapolação. Gestos, visão geral, recentralização, troca de tema e ciclo de vida preservam a propriedade da câmera. Próxima manobra maior e tempo/distância/chegada em três campos.

TomTom opcional com chave separada no Keychain, autenticação por header, fluxo raster e incidentes, limitação de consultas, expiração e cancelamento ao desligar/sair do primeiro plano. A revisão validou compatibilidade dos nomes de APIs com os headers oficiais; não executou MapLibre/URLProtocol no iPhone.

## Ainda depende do Mac e da conta do usuário

Não houve build/typecheck com SDK Apple, execução SwiftUI/Metal/URLProtocol em iPhone, IPA assinado ou consulta TomTom autenticada. Testes Linux e parser não comprovam esses itens. O amigo deve seguir o [README](../README.md#para-quem-vai-compilar-no-mac), compilar e testar a interface/ciclo de vida/trânsito com Xcode antes de distribuir o IPA.

O ETA continua Geoapify sem trânsito ao vivo; não foram adicionados radares, posições de semáforos ou fases verde/vermelho. [Uso e limites](../../docs/navigation-v0.8.md). [Validação anterior](VALIDATION-v0.7.1.md).
