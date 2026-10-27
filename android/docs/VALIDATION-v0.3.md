# Android 0.3.0 — interface e três temas

Atualização de **27/10/2026**, pacote `br.com.gamemap.goiania.dev`, `versionCode 2`. O usuário confirmou que a versão anterior funcionou no Android. As verificações abaixo foram realizadas neste ambiente, em emulador; não representam um novo teste em telefone físico ou central Android Auto.

## Entrega

- [APK 0.3.0 instalável](../artifacts/gamemap-goiania-v0.3.0-debug.apk), universal para quatro arquiteturas, aproximadamente 116 MiB.
- [AAB debug 0.3.0](../artifacts/gamemap-goiania-v0.3.0-debug.aab), para uma futura distribuição de teste; não se instala diretamente.
- [Tamanhos e SHA-256 exatos](../artifacts/v0.3/artifacts.json).
- [Capturas dos mapas e da interface](../screenshots/v0.3/README.md).

O identificador do app, a chave de assinatura de desenvolvimento e o arquivo de preferências foram preservados. A assinatura APK v2 foi verificada; a impressão SHA-256 do certificado é `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`, igual à da versão 0.2.0. O APK mantém Android mínimo 26, alvo 36, com `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`.

## Resultado visível

O telefone agora abre com o mapa atrás de um carrossel de três cards. Cada prévia foi capturada do mapa nativo real, com os mesmos dados de Goiânia; não é uma imagem ilustrativa nem depende de rede. Tocar num card aplica e salva o tema e traz o card inteiro à vista. “Explorar mapa”, fechar, voltar ou arrastar recolhe o painel.

Com os painéis recolhidos, o MapView ocupa toda a área disponível. Os controles são flutuantes: identidade/local, informações, zoom/enquadramento e o menu compacto Temas/Filtros. O painel de filtros tem seis switches, contagens e ações Todos/Nenhum. As áreas verdes continuam desenhadas quando os ícones de parques estão desativados.

Rosa Pop usa interface clara, rosa vivo, menta, turquesa e detalhes de flor/coração. Seu modo noturno mantém lilás e rosa saturados, com brilho reduzido no mapa e painéis claros. RDR2 usa papel sépia, vias em tinta escura, vermelho pontual, ícones originais e Noto Serif Bold local. A paleta GTA aprovada foi preservada. Consulte [paletas, referências e licenças](../../docs/android-native-styles.md).

No Android Auto, as interfaces continuam nos templates permitidos da Car App Library. A lista de temas ganha RDR2 pela mesma preferência compartilhada; os menus flutuantes do telefone não são desenhados na Surface do carro.

## Verificações

| Verificação | Evidência |
| --- | --- |
| Dados, geometrias, fontes, ícones e seis estilos | **35 testes Node aprovados**, sem falhas |
| Preferências, três temas nos templates e filtros independentes | **14 testes JVM aprovados**, sem falhas |
| Câmera, mapas, telefone, modo noturno e Surface | **5 testes instrumentados aprovados**, sem erros/falhas/ignorados |
| Análise Android | `lintDebug` sem erros; 27 avisos de manutenção/estilo, sem baseline |
| Build | `assembleDebug` e `bundleDebug` concluídos |
| Instalação e atualização | APK instalado e aberto; atualização da 0.2.0 preservou Rosa Pop e 5/6 categorias |
| Inspeção visual | Três mapas na mesma câmera, carrossel, filtros, Rosa noturno e paisagem com fonte ampliada |

Os relatórios ficam em [native-tests.xml](../artifacts/v0.3/native-tests.xml), [testes JVM](../artifacts/v0.3/unit-tests/) e [lint-results-debug.xml](../artifacts/v0.3/lint-results-debug.xml). O cenário do telefone foi repetido após o ajuste da área segura de abertura; [final-ui-tests.txt](../artifacts/v0.3/final-ui-tests.txt) registra a conferência final direta pelo AndroidJUnitRunner do carrossel/filtros e Rosa noturno. Repetições não são contadas como novos testes distintos.

Os cinco cenários instrumentados verificam:

1. Movimento da câmera nativa durante a substituição de estilo, sem restaurar uma posição antiga.
2. Os três estilos diurnos e noturnos, seis camadas semânticas, sprites locais, manutenção da câmera e restauração dos filtros após trocar de tema.
3. O carrossel real, último card completamente visível, zoom, cada um dos seis switches, recolhimento dos painéis, tema/filtros após recriar a Activity e preservação da câmera. Abertura do carrossel também considera a barra de navegação para deixar o botão acessível.
4. Rosa Pop sob modo noturno, mapa com a paleta noturna e barra/painéis claros, sem escurecimento automático do Android.
5. Pixels de parques e vias na Surface, área útil, DPI, redimensionamento e recriação da Surface, mantendo a câmera.

O teste ampliado de zoom encontrou um deslocamento por arredondamento do centro de uma viewport com dimensão ímpar. Os botões agora usam o zoom nativo sem ponto focal explícito, preservando o alvo exato; gestos com ponto focal continuam suportados.

O APK final foi instalado sobre a versão anterior com `adb install -r`, após escolher Rosa Pop e desligar Parques na interface antiga. A versão 0.3.0 abriu com as mesmas escolhas: [hierarquia da interface](../artifacts/v0.3/upgrade-ui.xml) e [preferências mantidas](../artifacts/v0.3/upgrade-preferences.xml). Não foi necessário desinstalar. Os testes de orientação/fonte foram feitos apenas no AVD dedicado; fonte 1,0 e rotação livre foram restauradas.

## Reprodução

Na raiz do projeto:

```powershell
npm test
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(
    ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:bundleDebug'
)
```

Com o AVD dedicado online:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(':app:connectedDebugAndroidTest')
```

As prévias podem ser reconstruídas com `node scripts/prepare-android-previews.mjs`, depois de obter as capturas do cenário nativo e salvá-las em `android/screenshots/v0.3/`. Os três WebPs, suas dimensões, hashes e imagens de origem constam em `android/maps/previews/index.json`. O APK foi aberto como ZIP para confirmar a inclusão efetiva das três prévias e do índice.

## Limites

O ambiente é Android 16/API 36 x86_64 com GPU do host NVIDIA e WHPX; a inspeção de layout não é uma matriz completa de versões e fabricantes. O defeito conhecido do MapLibre 13.7.1 em SwiftShader permanece documentado no [ambiente de testes](../../docs/emulator-status.md); as capturas finais usam a GPU do host.

Uma repetição prolongada foi interrompida por pressão de memória/reinício de serviços do Android emulado. O log registrou OOM e encerramentos de processos de sistema. Depois de encerrar os compiladores locais, os dois testes finais de interface foram executados diretamente pelo AndroidJUnitRunner e concluíram `OK (2 tests)`. Essa ocorrência fica distinta da suíte de cinco testes já aprovada e não constitui um ensaio de desempenho ou garantia em aparelhos com pouca memória.

Não houve novo download/alteração dos dados geográficos: o recorte e os 1.571 POIs reais são os mesmos da versão anterior. Fontes, sprites, prévias, estilos e tiles estão no APK. Não foram acrescentados GPS, busca, rotas, login ou backend, nem permissões de localização no manifesto final.

Não houve teste com host Android Auto/DHU, reconexão física ou central real nesta atualização. O roteiro continua em [TESTING.md](TESTING.md). As capturas do telefone e do harness nativo são identificadas separadamente; não foram produzidas telas artificiais de Android Auto. O [relatório 0.2.0](VALIDATION.md) preserva a evidência histórica da primeira entrega.
