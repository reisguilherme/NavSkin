# Testar o MVP Android

Este guia descreve como reproduzir as verificações. A navegação atual está em [VALIDATION-v0.4.md](VALIDATION-v0.4.md) e o uso em [NAVIGATION.md](NAVIGATION.md). [VALIDATION-v0.3.md](VALIDATION-v0.3.md) preserva a atualização visual anterior; [VALIDATION.md](VALIDATION.md) preserva a primeira entrega. Não houve teste deste ambiente no DHU nem em carro real. O AVD AOSP é um ambiente para testes Android e de pixels da Surface, sem host Android Auto.

## 1. Dados, assets, testes locais e APK

Executar na raiz do repositório:

```powershell
npm test
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
```

Os testes Node conferem o pacote POI, as seis categorias, a geometria e procedência, os estilos, sprites, fontes e integridade dos assets. Os testes JVM usam Robolectric e os utilitários da Car App Library para conferir preferências, repositório de estilos e construção dos templates. Aprovação nesses testes não prova renderização na central.

Também existem regressões de cruzamentos, vias paralelas, laços, ETA por duração, chegada no acesso pela via, sinal fraco/perdido, debounce de desvio, respostas HTTP fora de ordem, cancelamento, quota, recuperação do serviço/processo, persistência e fallback dos catálogos. Os adaptadores de rede usam fixtures; não usam uma chave pessoal nem confirmam cobertura autenticada.

Saídas Gradle:

- APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
- JUnit local: `android/app/build/test-results/testDebugUnitTest/`.
- Relatório local: `android/app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: `android/app/build/reports/lint-results-debug.html`.

## 2. Instrumentação no emulador AOSP

O ambiente de validação completa usa `GameMap_AOSP_API36`, Android 16/API 36 `x86_64`, WHPX e `-gpu host` (GPU NVIDIA). A prova mínima também rodou em SwiftShader, mas esse backend apresenta uma falha conhecida do SDK 13.7.1 que deixa símbolos transparentes; use a GPU para conferir textos e ícones. Veja [diretórios, inicialização e diagnóstico do AVD](../../docs/emulator-status.md). Ele deve aparecer como `device` no ADB e retornar `1` para `sys.boot_completed`.

Com o AVD dedicado conectado, compile e execute a instrumentação:

```powershell
.\scripts\android-toolchain.ps1 -Action Devices
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(':app:connectedDebugAndroidTest')
```

Para executar somente a prova mínima, use:

```powershell
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(
    ':app:connectedDebugAndroidTest',
    '-Pandroid.testInstrumentationRunnerArguments.class=br.com.gamemap.render.NativeSurfaceProbeTest'
)
```

`NativeSurfaceProbeTest` usa `ImageReader` para observar pixels de geometria real produzidos pelo caminho MapLibre/TextureView/VirtualDisplay. Verifica área útil, DPI, redimensionamento, retenção de câmera, reconexão e liberação.

`NativeMvpRenderingTest` exercita os três estilos, seis variantes dia/noite, filtros, interface clara sob modo noturno e controles do telefone. As imagens atuais são gravadas em `Pictures/GameMapV3/`; a prova mínima usa `Pictures/GameMapSurfaceProbe/`. Não chame essas imagens de capturas do Android Auto: a Surface é fornecida pelo próprio teste.

`NativeNavigationTest` verifica KeyStore/AES-GCM real, edições sucessivas de favoritos com AtomicFile real e as camadas nativas de uma reprodução identificada como **SIMULAÇÃO**. Troca tema, desliga filtros, escolhe veículo, recria/gira a Activity e exige continuidade de fase e instruções. Salva imagens em `/sdcard/Android/data/br.com.gamemap.goiania.dev/files/nav-*.png`. A execução direta por `adb shell am instrument -w -r -e class br.com.gamemap.render.NativeNavigationTest br.com.gamemap.goiania.dev.test/androidx.test.runner.AndroidJUnitRunner` fornece resultado textual; os logs desta entrega estão em `logs/v0.4/`.

Resultados ficam em `android/app/build/outputs/androidTest-results/connected/` e `android/app/build/reports/androidTests/connected/`. Não deduza aprovação apenas por haver um XML ou uma imagem: confira falhas, dispositivo, data e conclusão da execução.

## 3. Conferência manual no telefone ou AVD

Instale o APK debug e abra a Activity principal conforme o [README](../README.md). Confira:

1. O mapa aparece no Centro de Goiânia sem permissão de localização; pan, zoom e o botão `Goiânia` funcionam.
2. No carrossel de Temas, trocar GTA SA/Rosa Pop/RDR2 mantém o enquadramento e muda a cartografia; o último card deve ficar inteiro à vista quando selecionado.
3. Cada uma das seis categorias liga/desliga seus ícones; desligar parques não remove o preenchimento cartográfico dos parques.
4. Fechar/reabrir a Activity preserva tema e filtros; rotação e modo noturno não deixam a view preta.
5. Sem conectividade de dados, as fontes, nomes, edifícios e ícones empacotados continuam carregando no recorte.

Anote a diferença entre testar esse comportamento manualmente e tê-lo coberto por um teste automatizado. Os links de atribuição são referências externas; o desenho do mapa usa assets locais.

### Navegação com chave pessoal e GPS real — validar no telefone

1. Configure sua chave Geoapify, permita GPS preciso, verifique posição/precisão e procure endereço e estabelecimento conhecidos dentro do recorte.
2. Confira sentidos únicos, conversões e duração da prévia antes de iniciar. Use um passageiro para operar e registrar o teste.
3. Confira próxima manobra/via, ETA, velocidade e chegada, incluindo um parque cujo acesso viário difere do centro do local pesquisado.
4. Desvie deliberadamente em condições seguras: o app deve suspender a orientação antiga, recalcular uma vez e continuar na nova rota. Ruído parado ou GPS fraco não deve gerar recálculos sucessivos.
5. Arraste o mapa; veja **Recentralizar**. Troque tema/filtro/veículo e gire a tela: a viagem continua e a câmera manual não deve saltar. Compare a câmera independente no carro.
6. Bloqueie o telefone e confirme notificação/continuidade da navegação; desconecte/reconecte o carro. Encerrar em qualquer tela encerra a sessão compartilhada.
7. Desative GPS e internet separadamente, negue permissão, teste chave inválida/limite no ambiente da sua conta e recupere. Não devem aparecer instruções, velocidade ou ETA antigos como atuais.
8. Encerre o processo durante uma viagem e reabra: apenas o destino pendente deve ser oferecido para recalcular. Não restaure instruções antigas automaticamente.
9. Salve/edite/exclua lugares, defina Casa/Trabalho, reabra, desative todos os POIs e confira os favoritos independentes. Inspecione 12 ícones de local e seis veículos em cada um dos três temas.

Esses passos continuam distintos da instrumentação por replay. Não foram executados com chave pessoal, condução real ou tela bloqueada durante uma rota autenticada nesta entrega.

## 4. Android Auto no DHU — etapa pendente

Este roteiro requer um telefone compatível com Android Auto, aplicativo Android Auto atualizado e depuração USB autorizada. Na primeira utilização, o fluxo do telefone pode solicitar revisão dos termos/configurações. Não use a imagem AOSP sem Google Play como substituta desse telefone. [Procedimento oficial DHU](https://developer.android.com/training/cars/testing/dhu).

1. Instale o APK de desenvolvimento no telefone e confirme que abre.
2. Nas configurações do Android Auto, toque repetidamente na versão para habilitar o modo de desenvolvimento.
3. No menu de desenvolvimento, inicie **Start head unit server**.
4. Conecte o telefone por USB, mantenha-o desbloqueado para a conexão e confirme o serial no ADB.
5. Na raiz do projeto, execute, substituindo o serial:

```powershell
$adb = Join-Path (Get-Location) '.runtime\android-sdk\platform-tools\adb.exe'
$phoneSerial = 'SERIAL_DO_TELEFONE'
& $adb -s $phoneSerial forward tcp:5277 tcp:5277
.\scripts\android-toolchain.ps1 -Action Dhu -ToolArguments @('--adb=5277')
```

O pacote DHU está em `.runtime/android-sdk/extras/google/auto/`. A ajuda local é `.\scripts\android-toolchain.ps1 -Action Dhu -ToolArguments @('--help')`. Não foi necessário instalar drivers ou alterar recursos do Windows na preparação desta entrega.

Confirme no host Car App API 7 ou superior:

- Descoberta do GameMap como Navegação; configure chave e GPS no telefone antes da conexão se o Android restringir o início pela central.
- Vias, preenchimentos, edifícios, nomes, ícones e créditos presentes na Surface.
- Tema e categorias acessíveis pela própria central, com seus estados refletidos também no telefone.
- Pan/zoom por toque e pelos controles oferecidos; teste também entrada rotativa usando `--input=rotary`.
- Dia/noite, área coberta pelo template, reconexão e mudança de tamanho sem mapa preto ou perda indevida do enquadramento.
- Busca/salvos/recentes, prévia, início e encerramento; próximas manobras e ETA nos templates oficiais. Verifique perda de GPS, recálculo, velocidade e a sessão compartilhada com o telefone.

No console do DHU, `screenshot arquivo.png` grava a tela. Em outro terminal, capture os logs:

```powershell
& $adb -s $phoneSerial logcat -s GameMapSurface AndroidRuntime > '.\android\artifacts\dhu-logcat.txt'
```

Registre versões do telefone, Android, Android Auto e DHU, modo de entrada, resolução/DPI, APK/hash, passos, resultado e capturas. Atualize o relatório somente depois de executar o cenário.

## 5. Veículo real — etapa pendente

Para aplicativos da **Android for Cars App Library**, o teste em veículo exige instalação por origem confiável, como Google Play. As opções oficiais de desenvolvimento incluem **Internal App Sharing** ou **Internal Test Track**. A opção **Unknown sources / fontes desconhecidas** do Android Auto **não vale para esta biblioteca**. Instalar por ADB ou abrir o mapa no telefone não estabelece que o aplicativo aparecerá na central. [Regra oficial](https://developer.android.com/training/cars/testing#test-in-real-vehicles).

Antes de preparar uma variante release para distribuição, configure a assinatura e revise os hosts em `GameMapCarAppService`. O debug utiliza `ALLOW_ALL_HOSTS_VALIDATOR`. O release utiliza `HostValidator.Builder`, os hosts privilegiados com `TEMPLATE_RENDERER` e a lista de hosts empacotada na CAL 1.7.0. Confira os certificados para a distribuição pretendida. [Referência oficial](https://developer.android.com/reference/androidx/car/app/validation/HostValidator).

Depois da instalação por um canal confiável, conecte o telefone ao carro compatível e repita os cenários do DHU, registrando modelo/versão da central e conexão usada. A aceitação em uma faixa de testes, a revisão Google Play e o funcionamento em um carro são evidências distintas.

Há um AAB debug atual em `android/artifacts/gamemap-goiania-v0.4.0-debug.aab`, gerado por `:app:bundleDebug`, para facilitar uma futura tentativa de Compartilhamento Interno de Apps. Ele permite entrega por arquitetura, enquanto o APK universal inclui quatro ABIs. Habilite Compartilhamento Interno de Apps na Play Store do telefone e instale pelo link gerado pela sua conta autorizada. Nenhum upload foi feito nesta entrega. Consulte as [instruções oficiais de compartilhamento interno](https://support.google.com/googleplay/android-developer/answer/9844679).

## Critério de relato

| Evidência | O que permite afirmar |
| --- | --- |
| Compilação e análise local | O APK foi produzido e os checks indicados concluíram |
| Teste AOSP com pixels | O renderer nativo desenhou na Surface do harness nas condições registradas |
| Teste DHU | O app integrou-se ao host Android Auto nas configurações executadas |
| Teste em carro | A combinação concreta de APK, telefone e central funcionou nos cenários registrados |

Esta versão usa a categoria NAVIGATION e deve ser avaliada pelos requisitos de aplicativos de navegação antes de distribuição. [Guia oficial de navegação](https://developer.android.com/training/cars/apps/navigation).
