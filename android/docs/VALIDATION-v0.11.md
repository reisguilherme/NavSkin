# NavSkin Android 0.11.0 — validação em 29/10/2026

APK `br.com.gamemap.goiania.dev`, versão **0.11.0-debug / versionCode 11**, minSdk 26 e targetSdk 36.

## Resultado

- **206 testes JVM** em 29 suítes, zero falhas/erros. Build e lint concluídos; lint com zero erros e 48 avisos. [Build](logs/v0.11/build.txt) · [Resumo JVM](logs/v0.11/jvm-summary.json).
- **16 cenários instrumentados distintos aprovados**, considerando a última execução de cada caso. Android API 36 em emulador com GPU host. Cobrem entrada real no Vibing pelo menu, recriação, toque, saída por botão/Voltar, câmera com/sem rota, preferência 2D recuperada, oito temas 3D, filtros, superfície nativa, mapas mundiais em São Paulo, locais, armazenamento e navegação em replay.
- **86 testes Node**, zero falhas: geografia, fontes, atlas, legibilidade GTA, orientação Mario, carrinho Pink, catálogos, manifests e paridade de recursos. [Log](logs/v0.11/shared-tests.txt).
- **142 testes Swift Core**, zero falhas, mais verificação de recursos, projeto e sintaxe Swift. Isso não equivale a compilar UIKit/MapLibre no Xcode. [Core](logs/v0.11/swift-core.txt) · [Recursos/sintaxe](logs/v0.11/ios-resources-source.txt).
- Auditoria do APK: **1.349 arquivos de recursos**, incluindo 32 estilos, idênticos byte a byte aos fontes preparados; nenhum arquivo faltando, extra ou divergente. [Auditoria](logs/v0.11/apk-audit.json).

## Rodadas nativas e correções

A primeira rodada executou 16 casos, com seis falhas. Corrigimos o rótulo acessível da busca e o aviso de carregamento após um quadro completo durante movimento. A validação passou a considerar a renderização completa sem exigir que a câmera em navegação fique ociosa. As verificações de cores agora usam a arte final e sua opacidade; a amostragem de dois pixels resolve os traços finos do Pip-Boy sem aumentar a tolerância de cor. A comparação de coordenadas usa tolerância de 1e-6 para a projeção nativa. O teste de troca de cobertura passou a esperar Crossy Road, último dos oito temas, em vez do antigo último tema Cyberpunk.

A segunda rodada aprovou oito dos nove casos repetidos. O teste de Voltar ainda estava enviando a tecla ao tutorial inicial de tela cheia do Android, identificado por captura e diagnóstico de foco. A chamada direta ao dispatcher funcionava. O teste final reconhece esse tutorial pelos IDs do SystemUI, aguarda o foco do app e verifica as duas saídas pelos controles reais. **Passou**, mantendo todas as verificações de visibilidade, recriação e estado.

[Primeira rodada](logs/v0.11/native-first-pass.txt) · [Segunda rodada](logs/v0.11/native-second-pass.txt) · [Diagnóstico do aviso Android](logs/v0.11/back-system-dialog-diagnostic.jsonl) · [Saída final](logs/v0.11/native-exit-final.txt) · [Resumo por cenário](logs/v0.11/native-summary.json).

## APK

- Arquivo: [NavSkin-v0.11.0-debug.apk](../artifacts/NavSkin-v0.11.0-debug.apk)
- Tamanho: **126.312.949 bytes**.
- SHA-256: `d9f882c892c9c97d6af19724002ec3ff5bb99c2e2d88df36c77d5415de446504`.
- Certificado debug SHA-256: `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`, igual às entregas anteriores. Assinatura verificada por apksigner; ID e versão por aapt2. Permite atualização da instalação debug anterior sem desinstalar. [Assinatura](logs/v0.11/apk-signature.txt) · [Metadados](logs/v0.11/apk-metadata.txt).

## Limites

As [capturas 3D](../screenshots/v0.11/v011-catalog/) são renderizações reais do MapLibre Native. Alturas ausentes nos dados são ilustrativas. Os testes usam emulador, GPS controlado e replay; não houve direção em campo nem consulta usando chaves pessoais Geoapify/TomTom. Android Auto/DHU não foi validado nesta etapa e as condições de descoberta/distribuição permanecem. Não foi gerado IPA nem implementado CarPlay.

[Como usar Vibing](../../docs/vibing-v0.11.md) · [Arte aprovada](../../docs/approved-map-art-v0.11.md).
