# NavSkin iOS 0.9.0 — validação em 29/10/2026

Projeto preparado para Xcode, versão **0.9.0 / build 9**, com recursos locais. A entrega é um pacote de fontes; não é um IPA instalável.

## Verificações executadas

- **120 testes XCTest**, zero falhas, com Swift nativo em Linux. Cobrem navegação existente e paradas ordenadas, falhas transacionais, cancelamento, respostas atrasadas, duplicatas, consumo por progresso e persistência/retomada. [Log](logs/v0.9/swift-core-tests.txt).
- Foundation/ProjectProbe verificou projeto Xcode, recursos, estilos, catálogos e versões. O parser verificou a sintaxe de todos os fontes Swift/SwiftUI e testes Apple. [Log](logs/v0.9/source-validation.txt).
- Revisão independente identificou e corrigiu chegada à rota antiga durante edição de paradas e recálculo por desvio que descartava a edição. Regressões falharam antes das correções e passaram depois. O menu foi reduzido às quatro entradas aprovadas.
- A interface usa material nativo, menu contextual, controles circulares iguais, ETA de três valores e ações Parar/Adicionar parada. Fontes, ícones e cartografia temática foram preservados.

## Verificação necessária no Mac

Não houve compilação/typecheck com SDK Apple, execução SwiftUI/Metal/URLProtocol no iPhone, IPA assinada ou teste de rota autenticada em campo. Os testes Linux e o parser não comprovam esses itens. O amigo deve seguir o [README](../README.md#para-quem-vai-compilar-no-mac) e executar `RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh` no Mac.

Conferir em iPhone: orientação/recentralização, paradas adicionadas por busca e salvos, menu e folhas em retrato/paisagem, texto ampliado, permissões, plano de fundo e recuperação após falha de rede. O ETA continua Geoapify sem trânsito ao vivo; TomTom permanece uma camada opcional. CarPlay não foi implementado.

[Uso e limites](../../docs/navigation-v0.9.md) · [Validação anterior](VALIDATION-v0.8.md).
