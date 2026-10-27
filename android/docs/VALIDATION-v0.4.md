# Validação — GameMap 0.4.0

Data: 27/10/2026. Implementação Android nativa, Kotlin/MapLibre Native OpenGL 13.7.1, Car App Library 1.7.0; Android mínimo 26, alvo 36. Pacote debug `br.com.gamemap.goiania.dev`, `versionCode 3`.

## Entrega implementada

| Incremento | Comportamento entregue |
| --- | --- |
| GPS e câmera | LocationManager GPS e serviço de localização em primeiro plano; precisão, velocidade, rumo estável parado, qualidade/frescura, perda/recuperação; follow/manual/overview e recentralização por tela |
| Destinos e rotas | Geoapify HTTPS configurável no aparelho; endereços/estabelecimentos, salvos/recentes/mapa/posição; prévia automóvel, início/cancelamento, erros e aviso de cobertura parcial |
| Navegação | Próxima manobra/via, progresso com continuidade, distância/duração/ETA, chegada no acesso navegável, recálculo após desvio consistente, cooldown e rejeição de respostas antigas |
| Locais e arte | Favoritos editáveis, Casa/Trabalho, 20 recentes, camada independente dos POIs; 12 ícones de local e seis veículos para cada um de GTA SA, Rosa Pop e RDR2; SVGs originais e prévias reais |
| Android Auto | Categoria NAVIGATION, NavigationTemplate/RoutingInfo/MessageInfo/Trip, lifecycle do NavigationManager, busca e listas do host, aparência e mapas independentes observando a sessão única |
| Continuidade | Serviço desacoplado da Activity, desconexão do host não encerra a viagem; persistência apenas do destino para retomada com recálculo; sem trajetória contínua ou contas |

Uso, chave, custos e limites: [NAVIGATION.md](NAVIGATION.md). Procedimentos de reprodução e teste no carro: [TESTING.md](TESTING.md).

## Evidência executada

- `npm test`: **39 testes aprovados**, incluindo catálogos, rasterização a partir dos SVGs editáveis, procedência e integridade dos mapas.
- `:app:testDebugUnitTest`: **111 testes aprovados, zero falhas/erros**, resultado consolidado por suíte em [junit-summary.json](logs/v0.4/junit-summary.json). Cobertura de geometria/progresso, cruzamentos/laços/paralelas, sinal, velocidade, rumo, ETA, chegada, desvio, cancelamento, requisições fora de ordem, falhas/quota/retry, retomada, storage e ponte de navegação do carro.
- `:app:lintDebug`: **zero erros e 33 avisos**. [Saída integral](logs/v0.4/lint.txt). Avisos incluem dependências fixadas, componentes antigos de UI, strings em código/localização, recurso de allowlist privado da CAL e serviço exportado do host. O serviço do carro usa HostValidator; allow-all é exclusivo do debug. Os avisos não são apresentados como resolvidos.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest` e `:app:bundleDebug`: concluídos; APK instalado com `adb install -r` no AVD existente.
- Instrumentação **NativeNavigationTest: 3 aprovados**, no AOSP API 36: KeyStore/AES-GCM real sem chave em claro nas preferências; AtomicFile real com edições/reabertura/exclusão; overlays nativos e favoritos independentes dos filtros, veículo/tema/orientação e sessão/instruções após recriar e girar a Activity. [Log](logs/v0.4/navigation-instrumentation.txt).
- Instrumentação de cartografia/câmera/Surface: **5 aprovados**, incluindo seis variantes dia/noite, filtros, controles do telefone, câmera movida durante troca de estilo, resize/DPI/reconexão/liberação de Surface. [Log](logs/v0.4/cartography-instrumentation.txt). Rodaram na mesma atualização antes dos ajustes finais de mensagens de GPS e posição dos controles, que foram verificados novamente pelos três testes de navegação.
- Conferência pelo ADB: LocationManager recebeu a coordenada de teste `−16.6811, −49.2564`, informou precisão de 5 m e velocidade parada de 0 km/h. `dumpsys` confirmou **uma assinatura GPS de 1 s**, serviço `isForeground=true`, tipo localização `0x8`, notificação silenciosa 4701. Desativar GPS retirou a velocidade; religar recuperou o fix. Logs: [serviço](logs/v0.4/location-service.txt), [fix](logs/v0.4/location-fix.txt), [desativado](logs/v0.4/location-disabled.txt), [recuperado](logs/v0.4/location-restored.txt).

O AVD é `GameMap_AOSP_API36`, Android 16/API 36 x86_64, WHPX, 2 GB, `-gpu host` NVIDIA. A imagem é AOSP sem Android Auto. Coordenadas do ADB vêm do emulador; não caracterizam antena GPS física nem condução real.

## Inspeção visual e correções

[Capturas atuais](../screenshots/v0.4/README.md) mostram manobra, via, ETA, velocidade, rota e marcadores nos layouts vertical/horizontal. Os controles ficam na lateral no retrato, com as opções de rota/orientação recolhidas quando não se aplicam; a instrumentação confirma que sua área não cobre o veículo. **SIMULAÇÃO** identifica a reprodução fixa de desenvolvimento; sua geometria não é uma consulta autenticada ao provedor.

A revisão encontrou e corrigiu chegada em acesso viário distante do centro do destino, reenquadramento indevido da prévia após rotação e interrupção da simulação ao atualizar permissões. A inspeção do GPS do emulador também encontrou mensagem antiga de espera/desativação depois de um fix válido; há regressões para limpar apenas mensagens de localização, preservando erros de rota/provedor. Revisão final dos dois achados funcionais confirmou as correções.

Os testes de persistência em Windows precisaram adaptar apenas a primitiva de rename do AtomicFile para a semântica POSIX do Android. A produção mantém AtomicFile e confirma o conteúdo gravado antes de publicar sucesso. O teste instrumentado independente usa o AtomicFile real do Android, sem esse adaptador.

## Limites e pendências concretas

1. **Chave pessoal não fornecida:** integração HTTP validada com fixtures, sem uma busca ou rota autenticada na conta do usuário. Configure a chave e execute os casos do roteiro; isso continua necessário para validar cobertura e instruções reais do provedor.
2. **Sem telefone Android compatível/host disponível ao agente:** NavigationManager/modelos e Surface foram testados separadamente. Não foram testados DHU, assistente, central real, tela bloqueada durante rota autenticada ou condução em trânsito. Há fallback explícito para ativar GPS no telefone quando o Android bloqueia início só pela central.
3. **Cobertura cartográfica parcial de Goiânia:** rotas externas não ampliam os tiles locais. Fora do recorte faltam vias/edifícios no fundo. Busca e cálculo/recálculo exigem internet; não há navegação offline completa nem trânsito ao vivo.
4. **Distribuição:** APK/AAB são debug. Não houve upload, publicação, configuração de assinatura release nem aprovação Google Play para navegação. Modo AUTO_DRIVE do host está habilitado apenas no debug e precisa ser revisto na qualificação de distribuição.

Essas pendências não foram substituídas por chaves de exemplo, servidores de demonstração ou relatos de testes em carro que não aconteceram.

## Artefatos

Arquivos versionados em [artifacts/](../artifacts/). Assinatura APK v2 válida, mesma chave de desenvolvimento da v0.3: SHA-256 do certificado `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. [Verificação](logs/v0.4/signature.txt), [metadados e permissões](logs/v0.4/apk-metadata.txt).

| Arquivo | Tamanho em bytes | SHA-256 |
| --- | ---: | --- |
| [APK instalável](../artifacts/gamemap-goiania-v0.4.0-debug.apk) | 121206617 | `6F88D736E546ED045D345059B5543B1322E44D289F9ADD6812D6E53F3F93A25B` |
| [Bundle debug](../artifacts/gamemap-goiania-v0.4.0-debug.aab) | 89197474 | `1F6D717CFDB81E0BFCB3A665635FA4F48B37E13A4C33C12C939075DDE43D515F` |

O manifesto final inclui FINE/COARSE, FGS/FGS_LOCATION e notificações; não solicita ACCESS_BACKGROUND_LOCATION. Chave pessoal não é incluída no APK. O teste de KeyStore usa um valor sentinela local e o remove, sem enviá-lo à rede.
