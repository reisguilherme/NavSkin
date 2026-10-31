# NavSkin Android 0.10.0 — validação em 29/10/2026

Entrega local **0.10.0-debug / versionCode 10**, pacote `br.com.gamemap.goiania.dev`, minSdk 26/targetSdk 36.

## Resultado

- **191 testes JVM**, zero falhas/erros, em 28 suítes.
- **10 testes instrumentados**, zero falhas/erros, em Android API 36 com GPU host. Cobrem os cinco temas mundiais, todas as variantes offline dia/noite na Surface, controles do telefone, filtros, câmera, locais salvos e navegação em replay. [XML](logs/v0.10/final-native-results.xml).
- APK, AAB e lint: **BUILD SUCCESSFUL**. Lint tem zero erros e 47 avisos não bloqueantes. [Log final](logs/v0.10/final-build-validation.txt).
- **74 testes JavaScript**, zero falhas, incluindo geografia, materiais, fontes, atlas, catálogos, hashes das prévias e paridade dos recursos iOS/Android. [Log](logs/v0.10/shared-tests.txt).
- APK inspecionado: 20 estilos, cinco prévias, seis fontes e cinco temas no catálogo de ícones. Minecraft e GTA V não estão nos recursos empacotados. Preferências antigas desses temas usam GTA SA sem apagar lugares salvos.
- Revisão independente dos seletores, catálogos e correção de preenchimentos concluída sem achados. [Capturas nativas](../screenshots/v0.10/README.md).

## Correção de renderização

A primeira rodada de cinco temas passou 8/10: parques e lagos SMW desapareciam no recorte offline após trocar de outro tema. Um experimento isolado confirmou que a carga inicial funcionava, mas a transição RDR2 → SMW falhava com texturas em polígonos GeoJSON. O antialias e uma camada sólida inferior não corrigiam a falha.

O preenchimento sólido nesses polígonos restabeleceu os parques e lagos na mesma câmera; as texturas dos prédios vetoriais e do mapa mundial permanecem. A rodada final passou os 10 testes com os limiares originais de pixels, sem relaxar a verificação. O código temporário de diagnóstico ficou fora da suíte e do aplicativo. [Experimento](logs/v0.10/fill-transition-pixels.txt) · [Rodada anterior](logs/v0.10/five-theme-native-validation.txt).

## Artefatos

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `NavSkin-v0.10.0-debug.apk` | 125413447 | `3279bedfe97b1741a0d234a68df366200df6d88205b928ccf154e4b6b4e3914b` |
| `NavSkin-v0.10.0-debug.aab` | 91875972 | `e2690b5ab699e96a04c8c47fd7f6199b65514dda4c5269fb1c9cc586c413bb31` |

`apksigner` verificou a assinatura com o mesmo certificado debug das entregas anteriores: SHA-256 `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. `aapt2` confirmou ID e versão. O APK pode atualizar a instalação anterior com esse certificado; o AAB é um bundle de desenvolvimento, não um arquivo instalável diretamente. [Assinatura](logs/v0.10/apk-signature.txt) · [Metadados](logs/v0.10/apk-metadata.txt).

## Limites

As imagens são renderizações reais do MapLibre Native, não renders de IA. Os testes de navegação usam replay identificado e respostas controladas; não houve direção em campo, consulta com chave pessoal Geoapify/TomTom ou validação em central Android Auto/DHU nesta etapa. A descoberta do aplicativo no Android Auto continua sujeita às condições documentadas anteriormente. CarPlay não foi implementado.

O iOS tem projeto e recursos preparados, 123 testes Core e verificações Foundation/sintaxe, mas não foi compilado com SDK Apple nem executado no iPhone. Consulte a [validação iOS](../../ios/docs/VALIDATION.md).

[Uso da versão](../../docs/maps-v0.10.md) · [Versão anterior](VALIDATION-v0.9.md).
