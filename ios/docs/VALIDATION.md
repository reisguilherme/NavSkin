# Validação NavSkin iOS 0.7.0 · 27/10/2026

Projeto pronto para abrir em um Mac: `ios/NavSkin.xcodeproj`, esquema **NavSkin**, pacote **NavSkinCore**, recursos locais e [README para quem vai compilar](../README.md#para-quem-vai-compilar-no-mac).

**Sem compilação com SDK Apple, IPA, simulador iOS ou teste em iPhone nesta entrega.** O usuário escolheu usar o Mac de um amigo. O GitHub Actions continua manual; o bloqueio anterior da conta está registrado no [relatório 0.6](VALIDATION-v0.6.md).

## Mudanças desta versão

- Topo compacto com NavSkin, Temas, Filtros e menu retrátil; salvos, GPS, opções, orientação, zoom e atribuições ficam no menu.
- Busca inferior centralizada, círculos de velocidade/recentralização e mapa ocupando a tela até a borda. O card da viagem contém tempo, distância e horário de chegada; precisão e velocidade não são duplicadas nele.
- Rótulos GTA maiores: vias 14,5 e nomes de POIs 15. Famílias autênticas mantidas, com fallback por caractere.
- Marcador GTA padrão usa radar_centre original no ID arrow, com pixels e origem preservados. Os outros IDs não mudam.
- Mundo também carrega os 729 tiles Overture locais na cobertura de Goiânia; fora dela continua OpenFreeMap. Os prédios mundiais dentro da cobertura são substituídos para evitar sobreposição.

## Verificações executadas

- **62 testes Node passaram**, incluindo recursos iOS, estilos, categorias, fonte/glyphs, cobertura de prédios e identidade de cada pixel do marcador original.
- **Preflight dos recursos passou:** hashes das três fontes, glifos, atlas e marcador GTA original/preparado.
- **Foundation ProjectProbe passou** com os fontes reais de AppTypes/MapResourceRepository: recursos, 12 estilos, URLs locais/HTTPS, catálogos e referências OpenStep/XML do Xcode.
- **Parser Swift passou** para aplicativo e cinco testes Apple após a revisão final de ETA/alinhamento. Parse não verifica os tipos de SwiftUI, UIKit, CoreLocation ou MapLibre.
- NavSkinCore e o empacotador unsigned não foram alterados. Os resultados anteriores de **85 XCTest Core** e **31 testes Python** constam do [relatório 0.6](VALIDATION-v0.6.md); não são testes Apple da interface 0.7.

Android foi usado para evidência GPU do mapa compartilhado e da navegação simulada; [relatório Android](../../android/docs/VALIDATION-v0.7.md). Capturas Android não comprovam renderização iOS.

## Limites e teste no Mac

Mundo precisa de internet para tiles e glifos adicionais. Goiânia offline cobre só o recorte incluído; ensino, cultura e culto ainda não constam desse snapshot. Busca/rotas/recálculo usam conexão e chave Geoapify do usuário. Sem trânsito ao vivo, voz ou CarPlay.

```bash
bash scripts/build-ios.sh doctor
swift test --package-path ios/Packages/NavSkinCore
bash scripts/build-ios.sh simulator
xcrun simctl list devices available
bash scripts/build-ios.sh test SIMULATOR_UDID
RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh
```

O último comando usa o Mac local e gera o IPA unsigned após testes e build. O amigo envia IPA/JSON para a assinatura pessoal do usuário no Windows; veja [instalação](SEM-MAC.md).

No Xcode/iPhone ainda é necessário verificar typecheck/build, os cinco testes Apple, URLProtocol dos glifos, os 12 estilos, gestos, rotação/Dynamic Type, recálculo, GPS real, permissões e tela bloqueada. O ZIP completo inclui os recursos originais com avisos e [procedência](../../docs/game-assets-provenance.md), sem senha, certificado, perfil pessoal ou chave do usuário.
