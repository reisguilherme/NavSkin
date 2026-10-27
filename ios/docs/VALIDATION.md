# Verificação do port iOS — 27/10/2026

**Entrega:** fontes SwiftUI/CoreLocation/Keychain/MapLibre, projeto Xcode e esquema de testes, pacote Swift puro, recursos locais preparados e instruções de build/assinatura. **Não foi gerado IPA.** Não há macOS/Xcode/Apple SDK neste host; Swift Linux não compila esses frameworks.

## Verificado neste ambiente

- **80 testes XCTest aprovados**, zero falhas: geometria/progresso/ETA, GPS/rumo/velocidade, chegada no acesso viário, desvios/recálculo, respostas antigas/cancelamento, provider HTTPS/erros/quota, storage atômico/migração/recuperação e sessão/simulação. Executados com Swift 6.4.1 Ubuntu 24.04 no WSL local, linguagem Swift 5. O provedor usa fixtures nesses testes; nenhuma chave pessoal ou chamada autenticada foi usada.
- **Três testes Node de empacotamento iOS aprovados:** bytes originais de GeoJSON/PBF, 729 tiles, seis estilos, 12 ícones salvos e seis veículos por tema, prévias PNG e vínculo de todos os fontes/recursos no projeto.
- A suíte npm completa também passou: **42 testes**, incluindo as regressões existentes de cartografia/arte Android e web. [Log Node](logs/node-tests.txt), [log XCTest](logs/core-tests.txt), [verificação estrutural/sintaxe](logs/source-checks.txt), [requisito do build Apple](logs/apple-build-prerequisite.txt).
- Compilação e execução de `Validation/ProjectProbe.swift` junto com os fontes reais Foundation de AppTypes/MapResourceRepository: seis estilos resolvidos para `file://`, fontes/sprites/GeoJSON e catálogos encontrados, templates de tiles/glyphs preservados; projeto OpenStep e XMLs de configuração lidos por Foundation com referências válidas.
- Todos os fontes do app e dos testes Apple passaram pelo parser Swift em modo 5. **Parse não é typecheck dos frameworks Apple.** Revisão independente conferiu seletores/propriedades no SDK MapLibre 6.31.0 e os contratos de CoreLocation/Keychain.
- O script de build identifica o host incompatível e informa que requer macOS/Xcode, sem produzir arquivo com extensão IPA.

O compilador portátil e a biblioteca de sistema necessária foram instalados apenas em `.runtime`, com assinatura PGP/checksums oficiais verificados, sem instalar pacotes no sistema. A configuração inicial SwiftBuild não propagou uma dependência local ao linker; o backend `--build-system native` permitiu compilar/testar. Ele emite aviso de depreciação sob Swift 6.4.1. [Pesquisa e comandos do ambiente](../../docs/ios-platform-research.md).

## Dependências externas e pendências

1. **Mac com Xcode e SDK iOS:** compilar a interface e os testes GameMapTests, resolver o XCFramework, executar simulador e inspecionar pixels/permissões/rotação. O projeto foi validado estruturalmente, mas ainda pode exigir correções na primeira compilação Apple.
2. **Conta/team e iPhone:** provisionamento/assinatura e instalação no dispositivo. Não há certificado, senha ou conta configurada no projeto; `Local.xcconfig` é privado e excluído do pacote de entrega.
3. **Chave Geoapify do usuário:** testar endereços/estabelecimentos/rotas reais, quota e instruções em Goiânia. Nenhuma consulta autenticada foi alegada.
4. **Teste de navegação real:** precisão/rumo/velocidade, desvios/recálculo, chegada, perda/recuperação, continuidade com tela bloqueada e retomada controlada. Não houve teste em iPhone, trânsito ou CarPlay. CarPlay não integra este escopo.
5. **Distribuição:** revisão de privacidade/provedor, licenças, SDKs e regras Apple antes de TestFlight/App Store; nenhum upload ou publicação ocorreu.

Mapa e arte são os mesmos do Android, com cobertura cartográfica parcial de Goiânia. Buscar/calcular fora desse recorte não fornece cartografia mundial. O debug inclui reprodução identificada como SIMULAÇÃO; não serve como evidência de navegação real.

## Reprodução

No Mac, siga [README](../README.md) e execute `swift test --package-path ios/Packages/GameMapCore`, depois o esquema GameMap no Xcode. `bash scripts/check-ios-source.sh` valida Foundation/projeto/sintaxe quando Swift estiver no PATH. Os comandos Apple não foram substituídos por capturas de navegador ou mockups.
