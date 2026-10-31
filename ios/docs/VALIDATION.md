# Histórico de validação iOS

Validação atual: [0.12.0 — marcadores 3D](VALIDATION-v0.12.md). O relatório abaixo preserva a entrega 0.10.0.

## NavSkin iOS 0.10.0 — validação em 29/10/2026

Projeto destinado ao Xcode, versão **0.10.0 / build 10**, com **1.153 recursos**, **20 estilos** (dez mundiais e dez de Goiânia offline), **cinco prévias**, **seis arquivos de fonte** e **quatro perfis de fonte temática**. A compilação e assinatura para iPhone continuam pendentes.

Os temas ativos são GTA SA, Rosa Pop, RDR2, Super Mario World e Cyberpunk 2077. As adições da 0.10 são SMW e Cyberpunk; Minecraft e GTA V foram removidos. Os três identificadores anteriores permanecem válidos; uma preferência pelos temas removidos usa GTA SA. Os novos temas preservam os IDs semânticos de lugares salvos e veículos, e Rosa Pop mantém o fallback de fonte de sistema/Noto.

## Verificações executadas

- **123 testes XCTest, zero falhas**, com Swift 6.4.1 em Linux/WSL, após a remoção de Minecraft/GTA V. Incluem navegação, câmera, paradas ordenadas, persistência, tráfego, validação de fontes e o caso que impede roteamento de glifos dos temas removidos. [Log completo](logs/v0.10/swift-core-tests.txt).
- **Sintaxe Swift e typecheck Foundation aprovados** para a configuração de cinco temas. O parser cobriu os fontes do app e testes Apple; o typecheck cobriu `AppTypes`, `MapResourceRepository` e `ProjectProbe`, sem SDK Apple.
- **Validação estrita do bundle e três testes JavaScript de empacotamento aprovados.** `ProjectProbe` verificou dez estilos por cobertura, cinco temas persistidos e fallback dos IDs removidos, além de fontes, sprites, catálogos, filtros e referências do projeto. `tests/ios-package.test.mjs` verificou exatamente 20 estilos e cinco prévias, os hashes das imagens e dos estilos, e a ausência de recursos Minecraft/GTA V. [Log Foundation/sintaxe](logs/v0.10/ios-source-validation.txt) · [Log do pacote](logs/v0.10/ios-package-tests.txt).

As prévias compartilhadas vêm de capturas no **MapLibre Native para Android**, com todas as categorias ativadas. O [índice das capturas](../NavSkin/Resources/maps/previews/index.json) registra origem, câmera e hashes dos cinco temas. Essas imagens não representam uma execução do aplicativo no iPhone. Arquivos de log com sufixo `pending-previews` conservam rodadas intermediárias; os logs ligados acima são os da configuração de cinco temas.

## Verificação necessária no Mac

Não houve compilação ou typecheck com SDK Apple, execução SwiftUI/Metal/URLProtocol no iPhone, IPA assinada ou teste de rota autenticada em campo. Os testes Linux, a inspeção dos recursos e o parser não comprovam esses itens. Seguir o [README](../README.md#para-quem-vai-compilar-no-mac) e executar `RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh` em um Mac com Xcode.

No simulador e no iPhone, conferir a seleção dos cinco temas, o registro dos nomes PostScript reais, a renderização dos rótulos e glifos online/offline, e a preservação de câmera, rota, paradas, categorias e ícones salvos ao trocar o tema. Validar também orientação/recentralização, menu e folhas em retrato/paisagem, texto ampliado, permissões, plano de fundo e recuperação após falha de rede. A interface Vidro e o fluxo de navegação foram preservados. O ETA continua Geoapify sem trânsito ao vivo; TomTom permanece uma camada opcional. CarPlay não foi implementado.

[Uso e limites da navegação](../../docs/navigation-v0.9.md) · [Validação anterior](VALIDATION-v0.9.md).
