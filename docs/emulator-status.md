# Ambiente de teste Android emulado

**Estado ao encerrar a entrega:** o AVD dedicado foi desligado após os testes para liberar recursos. Os registros abaixo descrevem as execuções observadas, não um processo que precise continuar ativo. O APK instalado e as preferências de teste permanecem no disco do AVD. Resultados finais: [VALIDATION.md](../android/docs/VALIDATION.md).

## Atualização: GPU do host para investigar símbolos nativos

Em **27/10/2026, 01:43 UTC** (**26/10/2026, 22:43 em Brasília**), o mesmo AVD dedicado foi reiniciado com `-gpu host`. O ADB confirmou `emulator-5554` online e `sys.boot_completed=1`; o SurfaceFlinger identificou `Google (NVIDIA Corporation), Android Emulator OpenGL ES Translator (NVIDIA GeForce RTX 5060/PCIe/SSE2), OpenGL ES 3.1 (4.5.0 NVIDIA 610.88)`. A aceleração de CPU continua WHPX. Processo de lançamento: PID 16880; QEMU: PID 3972. Não foram alterados drivers nem recursos do Windows.

A mudança investiga um defeito conhecido do backend OpenGL com SwiftShader: símbolos e algumas linhas/padrões podem ficar transparentes mesmo após carregar fontes e completar o quadro. A [correção proposta no MapLibre Native, PR #4625](https://github.com/maplibre/maplibre-native/pull/4625), ainda aberta na consulta, reordena blocos de uniforms nos três shaders de símbolos e em outros dois shaders. A [PR #715 do MapLibre Native FFI](https://github.com/maplibre/maplibre-native-ffi/pull/715) incorpora essa correção e registra testes antes/depois no Android x86_64. O binário `libmaplibre.so` x86_64 de `android-sdk-opengl:13.7.1` usado aqui ainda contém o bloco fragment-only `SymbolTilePropsUBO` antes do bloco compartilhado `SymbolEvaluatedPropsUBO`, a ordem afetada. Os logs anteriores deste projeto identificavam `Google SwiftShader` e nenhum erro de carregamento de glyphs/sprites.

Isso estabelece a causa provável e uma mudança isolada de ambiente para a verificação; **não confirma, por si só, que os testes do aplicativo passaram ou que os símbolos apareceram**. Os resultados de instrumentação e capturas devem registrar a execução com a GPU NVIDIA.

Logs desta execução: `.runtime/android-avd/emulator-host.stdout.log`, `.runtime/android-avd/emulator-host.stderr.log`, `.runtime/android-avd/emulator-host.pid` e `.runtime/android-avd/status-host.json`. Os arquivos `status.json` e `emulator.stdout.log` abaixo preservam a preparação inicial com SwiftShader. Os diretórios locais e as demais opções de inicialização foram mantidos; a linha atual é:

```text
-avd GameMap_AOSP_API36 -no-window -no-audio -no-snapshot -no-boot-anim -no-metrics
-gpu host -accel on -cores 2 -memory 2048
-camera-back none -camera-front none -port 5554
```

## Registro da preparação inicial com SwiftShader

Verificação em **27/10/2026, 00:59:57 UTC** (**26/10/2026, 21:59:57 em Brasília**): o AVD está online no ADB, com `sys.boot_completed=1`. Ele foi deixado em execução para a instrumentação.

Este é um **ambiente AOSP para testar renderização nativa em Surface**. Ele não contém o host Android Auto e não representa validação com o DHU ou veículo real. Nenhum APK foi instalado ou aberto durante esta preparação.

## Configuração observada

| Item | Valor verificado |
| --- | --- |
| Serial ADB | `emulator-5554` |
| AVD | `GameMap_AOSP_API36` |
| Android | 16, API 36, `x86_64` |
| Imagem | `system-images;android-36;default;x86_64`, revisão 2 |
| Android Emulator | 37.2.12, build 16428233 |
| Perfil | Pixel 2, 1080 × 1920 |
| CPU virtual | 2 núcleos, WHPX |
| Gráficos | SwiftShader, sem janela |
| Processo de lançamento | PID 2228 |
| Processo QEMU | PID 7700, `qemu-system-x86_64-headless.exe` |

Fingerprint retornado pelo dispositivo:

```text
Android/sdk_phone64_x86_64/emu64x:16/BE2A.250530.026.D1/13818094:userdebug/test-keys
```

Os PIDs identificam esta execução e mudam após reiniciar. O registro completo da inspeção está em `.runtime/android-avd/status.json`. As consultas `pm path com.android.vending` e `pm path com.google.android.projection.gearhead` não retornaram pacote.

## Pacotes e licença

Foram consultados somente os catálogos oficiais:

- [Catálogo Android SDK](https://dl.google.com/android/repository/repository2-1.xml), salvo em `.runtime/android-avd/repository2-1.xml`.
- [Catálogo de imagens AOSP](https://dl.google.com/android/repository/sys-img/android/sys-img2-1.xml), salvo em `.runtime/android-avd/sys-img2-1.xml`.

O emulador estável e a imagem AOSP selecionados referenciam `android-sdk-license`. O SDK Manager reconheceu a licença já aceita, sem uma nova aceitação; sua entrada local permaneceu `24333f8a63b6825ea9c5514f83c2829b004d1fee`. A entrada de imagem AOSP e a licença do SDK instalado contêm o mesmo texto, com diferença apenas de espaço em branco. A instalação usou canal estável, sem aceitar a licença de prévia nem termos Google Play.

Os pacotes foram instalados em `.runtime/android-sdk/emulator` e `.runtime/android-sdk/system-images/android-36/default/x86_64`. Não foram alterados recursos do Windows, drivers, hipervisor ou contas.

## Aceleração e diagnóstico

Uma chamada inicial dentro do sandbox produziu `accel: 6`, com uma mensagem sobre Hyper-V/WHP. A mesma ferramenta executada com a autorização de execução externa retornou:

```text
accel:
0
WHPX(10.0.26200) is installed and usable.
accel
```

O log de inicialização também confirma:

```text
WHPX on Windows 10.0.26200 detected.
Windows Hypervisor Platform accelerator is operational
```

Portanto, a execução atual usa aceleração de CPU já disponível; não foi necessário tentar `-accel off`. SwiftShader fornece os gráficos por software, uma opção oficialmente suportada. Resultados de desempenho nesse backend não caracterizam uma GPU de telefone. [Aceleração do Android Emulator](https://developer.android.com/studio/run/emulator-acceleration).

O AVD Manager registrou a ausência de `devices.xml` na imagem durante a criação. Ainda assim, criou o perfil Pixel 2, o emulador iniciou e o boot foi confirmado pelo ADB. Não foi feita alteração na imagem para suprimir esse aviso.

## Diretórios locais

As variáveis de ambiente foram definidas somente nos processos usados para esta tarefa:

| Variável | Diretório no projeto |
| --- | --- |
| `ANDROID_HOME` | `.runtime/android-sdk` |
| `ANDROID_USER_HOME` | `.runtime/android-avd/user` |
| `ANDROID_EMULATOR_HOME` | `.runtime/android-avd/emulator-home` |
| `ANDROID_AVD_HOME` | `.runtime/android-avd/avd` |
| `TEMP` e `TMP` do processo do emulador | `.runtime/tmp` |

Essas variáveis mantêm configuração e discos do AVD dentro do workspace. [Variáveis oficiais do SDK e Emulator](https://developer.android.com/tools/variables).

Logs locais:

- `.runtime/android-avd/emulator.stdout.log`;
- `.runtime/android-avd/emulator.stderr.log`;
- `.runtime/android-avd/emulator.pid`.

## Comandos de inspeção

Para reiniciar o AVD já preparado, execute na raiz do projeto:

```powershell
$avdWorkspace = (Get-Location).Path
$avdRuntime = Join-Path $avdWorkspace '.runtime'
$env:ANDROID_HOME = Join-Path $avdRuntime 'android-sdk'
$env:ANDROID_USER_HOME = Join-Path $avdRuntime 'android-avd\user'
$env:ANDROID_EMULATOR_HOME = Join-Path $avdRuntime 'android-avd\emulator-home'
$env:ANDROID_AVD_HOME = Join-Path $avdRuntime 'android-avd\avd'
$env:TEMP = Join-Path $avdRuntime 'tmp'
$env:TMP = $env:TEMP
Start-Process -FilePath (Join-Path $env:ANDROID_HOME 'emulator\emulator.exe') `
    -ArgumentList @('-avd','GameMap_AOSP_API36','-no-window','-no-audio',
        '-no-snapshot','-no-boot-anim','-no-metrics','-gpu','host','-accel','on',
        '-cores','2','-memory','2048','-camera-back','none','-camera-front','none','-port','5554') `
    -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $avdRuntime 'android-avd\emulator-user.stdout.log') `
    -RedirectStandardError (Join-Path $avdRuntime 'android-avd\emulator-user.stderr.log')
```

As variáveis acima valem para esse terminal/processo. Este comando inicia sem janela, como na instrumentação; o Android Auto não está instalado nessa imagem. Aguarde `sys.boot_completed=1` antes de executar os testes. Para encerrar somente esse AVD, use `adb -s emulator-5554 emu kill`.

Executar no diretório do projeto, no mesmo contexto autorizado usado para iniciar o AVD:

```powershell
& '.runtime\android-sdk\platform-tools\adb.exe' devices -l
& '.runtime\android-sdk\platform-tools\adb.exe' -s emulator-5554 get-state
& '.runtime\android-sdk\platform-tools\adb.exe' -s emulator-5554 shell getprop sys.boot_completed
& '.runtime\android-sdk\emulator\emulator.exe' -accel-check
```

Argumentos usados na inicialização:

```text
-avd GameMap_AOSP_API36 -no-window -no-audio -no-snapshot -no-boot-anim
-gpu swiftshader -accel on -cores 2 -memory 2048
-camera-back none -camera-front none -port 5554
```

O processo foi iniciado por `Start-Process -WindowStyle Hidden`, com stdout/stderr redirecionados para os arquivos acima e os diretórios de ambiente já configurados. Para uma próxima inicialização, acrescentar `-no-metrics` evita o aviso opcional de coleta de métricas presente no primeiro log. [Opções oficiais de linha de comando](https://developer.android.com/studio/run/emulator-commandline).

## Limite da evidência

Esta preparação confirma instalação dos pacotes, aceleração WHPX utilizável e dispositivo AOSP online com boot concluído. Compilação, instalação do aplicativo, testes de Surface e capturas devem ser registrados pela etapa de instrumentação que consumir este ambiente. Mesmo uma instrumentação nativa bem-sucedida não substitui a validação do serviço, templates e gestos com o host Android Auto/DHU.
