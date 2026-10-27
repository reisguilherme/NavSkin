# Validação NavSkin iOS 0.6.0 · 27/10/2026

Entrega preparada para compilar em um Mac com Xcode: `ios/NavSkin.xcodeproj`, esquema **NavSkin**, pacote local **NavSkinCore**, recursos preparados, testes e guia [para quem vai compilar no Mac](../README.md#para-quem-vai-compilar-no-mac).

**A interface ainda não foi compilada pelo SDK Apple. Não há IPA gerado nem teste em simulador/iPhone nesta entrega.** O GitHub não iniciou o runner por um bloqueio de cobrança da conta, antes de qualquer etapa do job. [Execução registrada](https://github.com/reisguilherme/NavSkin/actions/runs/40559033001). O usuário escolheu compilar no Mac de um amigo; Actions permanece somente manual.

## Verificações executadas

- **85 testes XCTest do NavSkinCore passaram** no Swift 6.4.1/Linux, modo Swift 5. Incluem geometria, progresso, ETA, qualidade do GPS, chegada, desvio, recálculo, respostas antigas, erros/quota do provedor, persistência e sessão. Consultas Geoapify usam fixtures; nenhuma chave pessoal foi fornecida.
- **58 testes Node passaram**, incluindo 6 estilos mundiais, 6 offline, categorias, geometria e recursos. Os três testes de recursos iOS confirmam 729 tiles locais, bytes geográficos originais, prévias, ícones e referências do projeto.
- **31 testes Python passaram** para o empacotador unsigned. São arquivos Mach-O sintéticos de teste; não são um aplicativo iOS. O empacotador rejeita simulador/Mac Catalyst e valida arm64 iPhoneOS, framework MapLibre, Payload, modos de arquivo, links internos e manifesto SHA256.
- O `ProjectProbe.swift` foi compilado com os fontes reais Foundation de AppTypes/MapResourceRepository. Confirmou modo mundial padrão, cache separado por cobertura, URLs HTTPS intactas, templates de tiles/fontes, resolução dos seis estilos offline, catálogos, projeto OpenStep, XML e nomes NavSkin.
- Todos os fontes do aplicativo e dos cinco testes Apple passaram pelo parser Swift em modo 5. **Parse não verifica tipos de UIKit, CoreLocation, Keychain, SwiftUI ou MapLibre.**
- Os scripts Bash e o workflow YAML foram verificados estruturalmente. O script identifica Windows/Linux como ambiente sem Xcode e não produz IPA nesses hosts. Os resultados `.xcresult` usam nomes únicos para permitir repetir o build no mesmo Mac. O preflight confere os hashes das fontes, glifos e atlas originais antes de compilar.
- Revisão independente encontrou e corrigiu a restauração indevida de câmera após gesto durante troca de estilo. A validação desse gesto com o renderer iOS ainda precisa ser feita no Mac.

O compilador Linux e sua dependência local ficam somente em `.runtime`. O backend SwiftPM `--build-system native` emite aviso de depreciação; os 85 testes são XCTest executados, não o resumo separado de zero testes Swift Testing. O [relatório 0.4](VALIDATION-v0.4.md) e seus logs permanecem identificados como histórico.

## Fontes e símbolos autênticos

O pacote local inclui Pricedown histórica para GTA, Hapna para interface/POIs RDR2 e RDR Lino para vias/localidades. As fontes RDR2 são conversões extraídas com autoria preservada; não se alega que RDR Lino seja a identificação exata do lettering geográfico original. Catálogo e glifos reais foram verificados por hash, acentos e diferença dos contornos Noto. O roteamento usa URLProtocol no iOS e permanece pendente de execução Apple. Os novos testes Core de catálogo/URLs estão incluídos nos 85 aprovados. Leia [procedência e avisos](../../docs/game-assets-provenance.md).

Os nove filtros incluem ensino, cultura e locais de culto. O snapshot offline único mantém 1.571 POIs: os três grupos novos não estão incluídos nele e são avisados pela interface. No mapa mundial, eles vêm dos dados OpenMapTiles. Setas seguem apenas o campo real oneway.

## Cobertura e limites

NavSkin 0.6 usa OpenFreeMap mundial online por padrão, com OpenMapTiles/OSM. Tiles e glifos adicionais precisam de internet; as fontes temáticas preparadas ficam locais. A disponibilidade de detalhes depende dos dados de cada região. Goiânia offline continua uma opção com os recursos locais anteriores. Busca/cálculo/recálculo Geoapify exigem conexão e a chave configurada pelo usuário nos dois modos. Não há trânsito ao vivo, voz, CarPlay ou navegação totalmente offline.

Os testes do serviço mundial incluem respostas TileJSON e MVT de São Paulo e Tóquio, além de glifos latinos, árabes e CJK. Isso confirma os recursos do provedor; não comprova renderização iOS. Consulte [fontes e cobertura](../../docs/world-maps.md).

## Executar no Mac

Na raiz do repositório:

```bash
bash scripts/build-ios.sh doctor
swift test --package-path ios/Packages/NavSkinCore
bash scripts/build-ios.sh simulator
xcrun simctl list devices available
bash scripts/build-ios.sh test SIMULATOR_UDID
RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh
```

O último comando usa o Xcode local, sem GitHub ou login Apple, e só conclui após testes, build para dispositivo e validação do pacote. Após sucesso, envia-se `ios/artifacts/cloud/NavSkin-unsigned.ipa` e `.json` para assinatura pessoal pelo usuário no Windows. Não é um IPA diretamente instalável sem assinatura. Veja [instalação no Windows](SEM-MAC.md).

Pendentes no ambiente Apple: typecheck/build completo, cinco testes NavSkinTests, renderização dos 12 estilos, gestos durante carga, câmera/rotação e ícones; no iPhone, permissões, precisão reduzida, bloqueio de tela, interrupções e navegação real com chave Geoapify. O pacote não contém senha, certificado, perfil pessoal nem chave do usuário.
