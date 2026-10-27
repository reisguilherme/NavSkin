# Navegação Android e Android Auto

Referência da implementação **v0.4.0**, com pesquisa de plataforma em 27/10/2026. Este documento descreve a integração e seus limites; não constitui evidência de teste em carro. As APIs abaixo foram confrontadas com os JARs `app-1.7.0-api.jar` e `app-1.7.0-runtime.jar` já presentes em `.runtime/gradle-user-home/caches/8.13/transforms/`, usando o `javap` do JDK portátil. A implementação mantém Car App Library 1.7.0. Consulte [configuração e uso](../android/docs/NAVIGATION.md) e [validação v0.4.0](../android/docs/VALIDATION-v0.4.md) para os procedimentos e resultados desta entrega.

## Ponto de partida e decisão

O projeto usa Kotlin, Android API mínima 26/alvo 36, MapLibre OpenGL 13.7.1 e Car App Library (CAL) `app`/`app-projected` 1.7.0. A central exige Car App API 7. A v0.4.0 substitui a antiga categoria `POI` por `NAVIGATION`, habilita localização e reutiliza o renderizador nativo com áreas visível/estável. `CarNavigationScreen` apresenta exploração/prévia com `MapWithContentTemplate` e condução com `NavigationTemplate`; o antigo `MapScreen` foi preservado como componente anterior.

Um único `NavigationRuntime` no processo principal possui a localização e o estado de navegação; `NavigationService` mantém o GPS em primeiro plano durante a viagem. A Activity e a sessão CAL enviam comandos e observam snapshots. Geoapify fornece geometria, manobras e durações; o motor Kotlin local calcula progresso e decide recálculos. O renderizador desenha os resultados. [Fornecedor, termos e custos](navigation-provider-decision.md) registra a escolha. Nenhuma chave acompanha o app e não houve validação autenticada com chave do usuário; mapa local, GPS e simulação explícita de desenvolvimento não comprovam busca/rotas reais pelo serviço.

## Localização, permissões e continuidade

### Manifesto mínimo

A v0.4.0 remove a exclusão anterior de localização aproximada/precisa e declara:

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="androidx.car.app.ACCESS_SURFACE" />
<uses-permission android:name="androidx.car.app.NAVIGATION_TEMPLATES" />

<!-- Dentro de application; sem android:process separado. -->
<service android:name=".navigation.NavigationService"
    android:exported="false" android:foregroundServiceType="location" />
```

O tipo `location`, a permissão de serviço correspondente e localização previamente concedida são exigidos no Android 14+. Navegação é um uso previsto desse tipo. Não se deve acrescentar `mediaPlayback`, Bluetooth ou permissão de velocidade do carro para navegação visual que usa GPS do telefone. [Tipos de foreground service](https://developer.android.com/develop/background-work/services/fgs/service-types#location).

Pedir `FINE` e `COARSE` juntos quando o usuário aciona sua localização ou inicia o fluxo de rota. Se conceder apenas aproximada, permitir busca, favoritos e exploração; explicar que condução curva a curva depende de posição precisa. Verificar o resultado real, não presumir precisão. No Android 12+, a troca para aproximada pode reiniciar o processo. [Permissões de localização em execução](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).

No carro, `CarContext.requestPermissions(listOf(FINE, COARSE), listener)` é a API oficial; no Android Auto a confirmação aparece no telefone. CAL 1.7.0 já contém a correção necessária para diálogos no Android 14+. O carro deve mostrar uma mensagem curta para concluir a autorização no telefone quando estacionado. [Permissões na Car App Library](https://developer.android.com/training/cars/apps/library/request-permissions).

Pedir `POST_NOTIFICATIONS` no Android 13+ para notificações de condução. Negá-la não impede tecnicamente iniciar um foreground service: ainda é obrigatório fornecer sua notificação, mas a exibição pode ficar limitada ao gerenciador de tarefas, prejudicando orientações fora da tela de navegação. Não confundir essa negação com permissão de GPS. [Permissão de notificações](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

### Início seguro e celular bloqueado

Iniciar o serviço enquanto a Activity do telefone está visível e após a concessão. Dentro do serviço, promover imediatamente:

```kotlin
ServiceCompat.startForeground(
    this,
    NAV_NOTIFICATION_ID, // ID positivo e estável.
    navigationNotification.build(),
    if (Build.VERSION.SDK_INT >= 29)
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
)
```

O serviço já iniciado corretamente continua acessando localização quando a tela é bloqueada. Essa arquitetura não precisa de `ACCESS_BACKGROUND_LOCATION` para manter a viagem iniciada pelo usuário. [Localização em primeiro plano](https://developer.android.com/develop/sensors-and-location/location/permissions).

Uma tela no Android Auto não torna a aplicação visível no telefone para fins de localização; o próprio `CarAppService` recomenda foreground service e mesmo processo. [Contrato de CarAppService](https://developer.android.com/reference/androidx/car/app/CarAppService).

**Limite de inicialização:** não presumir que qualquer callback da central autoriza criar um serviço de localização com telefone bloqueado. Android 12+ restringe início de FGS em segundo plano; Android 14+ também verifica permissões while-in-use no início. As exceções são específicas, por exemplo interação com notificação ou PendingIntent enviado por outro app visível. `checkSelfPermission == GRANTED` sozinho não prova elegibilidade naquele instante. [Restrições de início em segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

Política implementada: a ação estacionada de GPS na central solicita permissões e tenta ativar o runtime. Se o início do serviço falha, o estado muda para `NEEDS_PHONE_START` e o fluxo pede ativação no telefone. O destino é preservado; início de navegação exige serviço ativo e posição precisa atual. O bridge não chama `navigationStarted()` sem serviço ou replay explícito. O usuário pode ativar no telefone antes de bloquear e depois escolher destinos salvos pela central. Início direto em cada host ainda exige validação; não foi acrescentada permissão de localização permanente.

### GPS sem Google Play Services

`AndroidLocationSource` usa `LocationManager`: `GPS_PROVIDER` com aproximadamente 1 segundo/0 metro e provedor de rede quando disponível. O serviço mantém um único source; telefone e central observam o runtime. A overload compatível com API 26 é:

```kotlin
locationManager.requestLocationUpdates(
    LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper()
)
```

Tratar provedor ausente/desativado e `SecurityException`; não exigir o provedor de rede em imagens AOSP. `getLastKnownLocation` pode ajudar o enquadramento inicial, mas precisa passar pelo mesmo teste de idade e precisão antes de valer para condução. Em API 26+, `Location` fornece `hasSpeed`, `hasSpeedAccuracy`, `speedAccuracyMetersPerSecond`, `hasBearingAccuracy` e `elapsedRealtimeNanos`. [Location](https://developer.android.com/reference/android/location/Location), [LocationManager](https://developer.android.com/reference/android/location/LocationManager).

Os limiares atuais do motor usam idade monotônica: sinal fraco após 5 s ou precisão acima de 35 m; perdido após 15 s; primeira aquisição permanece indisponível/aguardando. Coordenadas inválidas e tempos regressivos são rejeitados. Com qualidade insuficiente, velocidade e progresso ficam indisponíveis; o mapa identifica a última posição conhecida. A orientação anterior é preservada parado, e velocidade GPS válida é convertida de m/s para km/h com filtragem leve. Esses limiares são decisões do aplicativo, não garantias do Android nem evidência de calibração em trânsito real.

### Dono único da sessão

O runtime pertence à `GameMapApplication` e mantém executor de rede, geração de rota, destino e snapshot. A Activity e o `Session` CAL registram/desregistram observers sem criar motores próprios. `Session.onStop` pausa a renderização; a desconexão encerra a ligação de metadados com o host e preserva a viagem compartilhada. Sem navegação nem consumidor visível, o runtime agenda a parada do serviço. `NavigationService` usa `START_NOT_STICKY`; somente o destino pendente é persistido para retomada com novo cálculo. Não há restauração silenciosa de instruções antigas nem armazenamento contínuo de posições.

## Integração Android Auto com CAL 1.7.0

### Categoria e templates

O serviço declara `androidx.car.app.category.NAVIGATION`, usa `NAVIGATION_TEMPLATES` e preserva `ACCESS_SURFACE`. `NavigationTemplate` oferece manobra/estimativa em condução; `MapWithContentTemplate` apresenta destinos e prévia. Os antigos `MapTemplate`, `PlaceListNavigationTemplate` e `RoutePreviewNavigationTemplate` estão depreciados no Car API 7. O fluxo de intents valida coordenadas/texto e abre seleção ou prévia, sem iniciar condução silenciosamente. [Construir app de navegação](https://developer.android.com/training/cars/apps/navigation).

Não incorporar APIs que aparecem hoje na referência mas requerem 1.9/Car API 9, como `NavigationVoiceAssistantCapabilities`; elas não existem no JAR 1.7.0 do projeto. As chamadas abaixo existem nessa versão. Elas são uma ponte de integração; os nomes `runtime.*` representam comandos/snapshots do aplicativo.

### Sessão oficial

Todas as chamadas do manager devem ocorrer no main thread:

```kotlin
val manager = carContext.getCarService(NavigationManager::class.java)
manager.setNavigationManagerCallback(object : NavigationManagerCallback {
    override fun onStopNavigation() {
        runtime.stopNavigation() // Para também telefone, GPS e requests.
    }
    override fun onAutoDriveEnabled() {
        // No debug, habilita replay identificado; iniciar após ação na prévia.
    }
})

// Após ação do usuário, serviço elegível e rota/posição válidas:
manager.navigationStarted()
manager.updateTrip(currentTrip)

// Ao cancelar/chegar:
manager.navigationEnded()
// Ao desmontar o bridge, após navigationEnded():
manager.clearNavigationManagerCallback()
```

`navigationStarted` exige callback já registrado. `updateTrip` antes de iniciar lança exceção. O host pode parar a navegação quando outro app obtém a sessão; esse evento deve encerrar a viagem compartilhada. Ao reconectar, um novo bridge registra callback e comunica a sessão corrente, sem refazer rota ou coletor. Desanexar o bridge não deve cancelar o runtime do telefone. [NavigationManager](https://developer.android.com/reference/androidx/car/app/navigation/NavigationManager).

### Dados de manobra, estimativa e progresso

Exemplo de montagem com assinaturas verificadas em 1.7.0 (dados são do snapshot, nunca constantes de demonstração na execução real):

```kotlin
val step = Step.Builder(cue)
    .setRoad(nextRoad)
    .setManeuver(Maneuver.Builder(maneuverType).build())
    .build()
val remaining = Distance.create(remainingMeters, Distance.UNIT_METERS)
val arrival = DateTimeWithZone.create(
    System.currentTimeMillis() + remainingSeconds * 1_000L,
    TimeZone.getDefault(),
)
val estimate = TravelEstimate.Builder(remaining, arrival)
    .setRemainingTimeSeconds(remainingSeconds)
    .build()
val stepEstimate = TravelEstimate.Builder(
    Distance.create(toManeuverMeters, Distance.UNIT_METERS),
    DateTimeWithZone.create(
        System.currentTimeMillis() + toManeuverSeconds * 1_000L,
        TimeZone.getDefault(),
    ),
).setRemainingTimeSeconds(toManeuverSeconds).build()

val trip = Trip.Builder()
    .addDestination(
        Destination.Builder().setName(destinationName)
            .setAddress(destinationAddress).build(), estimate,
    )
    .addStep(step, stepEstimate)
    .setCurrentRoad(currentRoad)
    .build()
val routing = RoutingInfo.Builder()
    .setCurrentStep(step,
        Distance.create(toManeuverMeters, Distance.UNIT_METERS))
    .build()
val template = NavigationTemplate.Builder()
    .setNavigationInfo(routing)
    .setDestinationTravelEstimate(estimate)
    .setActionStrip(mainActions)
    .setMapActionStrip(mapActions)
    .setPanModeListener { _ -> screen.invalidate() }
    .build()
```

Usar duração acumulada dos trechos restantes, proporcional ao progresso, para `remainingSeconds`; `toManeuverSeconds` é duração até a manobra, não o ETA total. Mapear tipos reais do fornecedor para `Maneuver.TYPE_*`, com fallback `TYPE_UNKNOWN`, e número de saída quando necessário para rotatórias. Não inventar faixas se o fornecedor não as trouxer.

Durante recálculo, a combinação válida é `RoutingInfo.Builder().setLoading(true).build()` sem step, distância ou junction image. `Trip.Builder().setLoading(true).build()` remove instruções antigas no host; destinos/estimativas podem ser enviados se ainda tiverem significado, mas steps não podem coexistir com loading. Após falha ou sinal perdido, usar `MessageInfo.Builder("GPS perdido").setText("Aguardando posição válida").build()` como `navigationInfo`, omitir estimativa incerta e não continuar instruções stale. Esses invariantes foram conferidos diretamente no bytecode 1.7.0.

### Velocidade e identidade visual

Uma indicação pequena, não interativa, de velocidade atual no mapa é permitida expressamente pelo design de `NavigationTemplate`. Deve respeitar áreas estável/visível, contraste e tamanho legível; mostrar “— km/h” quando indisponível. Manobra, ETA, ações e encerramento permanecem nos componentes oficiais. Não há necessidade de desenhar um painel arbitrário por cima do mapa. [Navigation template — requisitos](https://developers.google.com/cars/design/create-apps/apps-for-drivers/templates/navigation-template).

Alternativa compatível: `TravelEstimate.Builder.setTripText(CarText.create(speedText))`, disponível desde Car API 5 e presente em 1.7.0. O host decide a apresentação, podendo reordenar/substituir componentes; não prometer posição nem visibilidade constante. [TravelEstimate.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/TravelEstimate.Builder).

Preservar GTA SA e Rosa Pop no estilo, rota, ícones e marcador. Respeitar modo claro/escuro enviado pelo host. Não prometer alterar tipografia e cores dos componentes controlados pelo Android Auto. Elementos desenhados no mapa devem ser de mapa/condução e atender contraste. [Requisitos de apps com templates](https://developers.google.com/cars/design/create-apps/ux-requirements/templated-apps).

### Ações e câmera

`NavigationTemplate` exige `setActionStrip`. Permite até quatro ações de template com título e/ou ícone; diferentemente de templates mais restritos, não há limite de somente um título nessa action strip em 1.7.0. A strip de mapa também admite quatro, exclusivamente com ícones. Incluir `Action.PAN` para receber gestos; sem ela o host não envia pan/scale. [NavigationTemplate.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/NavigationTemplate.Builder).

Distribuição implementada para manter os controles acessíveis:

| Área | Ações |
|---|---|
| Strip principal durante condução | Encerrar, Destinos, Aparência |
| Strip de mapa | PAN, Recentralizar, Rota completa, Norte/direção |
| Aparência — lista curta | Tema, filtros, marcador, visibilidade de salvos |
| Destinos — lista curta | Casa, Trabalho, Salvos, Recentes, Pesquisa |

Zoom permanece por gestos/pan; não acrescentar quinta ação à strip. Gestos efetivos suspendem follow; entrar/sair do modo pan sozinho não deve sobrepor uma escolha anterior de exploração. Recentralizar volta ao veículo e escala de condução. Rota completa entra em overview; recentralizar restaura follow. Tema e marcador apenas reaplicam layers/imagens ao renderizador, preservando destino, progresso, estado de câmera e geração de rota. Reaplicar rota/veículo/salvos quando `Style` termina de carregar, pois a troca de estilo remove layers de execução.

### Entrada de destino e restrições de movimento

Usar `SearchTemplate` e seus callbacks oficiais `onSearchTextChanged`/`onSearchSubmitted`, com debounce, resultados com nome e região e limite do `ConstraintManager`. O teclado do host fica indisponível em movimento; o host oferece voz e resultados anteriores/sugeridos. A ausência de instruções de voz no aplicativo não impede entrada por voz do sistema. Não criar teclado, WebView ou captura de texto própria na Surface. [Search template](https://developers.google.com/cars/design/create-apps/apps-for-drivers/templates/search-template).

Fluxos do projeto: mapa → Salvos/Recentes → prévia com Iniciar/Cancelar → NavigationTemplate; pesquisa → resultado → prévia → NavigationTemplate. Edição de favorito, renomeação, seleção livre no mapa e catálogos extensos ficam no telefone. O carro oferece listas curtas de seleção e prévias reais dos ícones, sem dependência da edição no telefone para acessar salvos existentes. Onde uma ação realmente exige estacionamento, usar `ParkedOnlyOnClickListener.create { ... }`; não simular detecção de movimento. Manter tarefa dentro de cinco templates e voltar/pop/reset corretamente; refresh de progresso não cria novas Screens. [Restrições de templates](https://developer.android.com/training/cars/apps/library/template-restrictions).

### Notificação oficial de navegação

Usar notificação ongoing, categoria `NotificationCompat.CATEGORY_NAVIGATION` e `CarAppExtender`, atualizada com manobra/distância. Um canal silencioso permite apenas orientação visual. Manter o mesmo ID do FGS evita duas notificações persistentes. Incluir conteúdo que reabre o app e ação Encerrar; PendingIntents explícitos, imutáveis. `CarNotificationManager.from(context).notify(id, builder)` está disponível para atualizações. [Notificações na navegação](https://developer.android.com/training/cars/apps/navigation#provide-turn-by-turn-notifications), [CarNotificationManager](https://developer.android.com/reference/androidx/car/app/notification/CarNotificationManager).

```kotlin
val builder = NotificationCompat.Builder(context, NAV_CHANNEL)
    .setSmallIcon(R.drawable.ic_app)
    .setContentTitle(instructionOrGpsState)
    .setContentText(distanceOrRecoveryText)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setSilent(true)
    .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
    .setContentIntent(openPhoneIntent)
    .extend(CarAppExtender.Builder()
        .setContentTitle(instructionOrGpsState)
        .setContentText(distanceOrRecoveryText)
        .setImportance(NotificationManagerCompat.IMPORTANCE_LOW)
        .build())
```

## Testes e fronteira da evidência

1. Unitários determinísticos do runtime: progresso com caminhos que se cruzam e vias paralelas, GPS stale, velocidades/bearings ausentes, desvio confirmado por múltiplas leituras, chegada apenas no trecho final, geração de requests e persistência. Simulação injeta um `LocationSource` separado, com rótulo permanente “SIMULAÇÃO”; não passa a ser o modo padrão.
2. AOSP Emulator: testar `LocationManager`, permissões, providers, tela bloqueada, processo encerrado e pontos/rotas GPS enviados pelos Extended Controls ou `adb emu geo fix`. Coordenadas no comando são longitude, latitude. Playback é simulação; não valida antena GPS real. [Extended Controls](https://developer.android.com/studio/run/emulator-extended-controls).
3. CAL app-testing 1.7.0: validar templates e ordens de chamada com `TestNavigationManager`; reconstituir bridge ao reconectar sem novo runtime. `onAutoDriveEnabled` habilita replay explicitamente identificado no build debug após uma prévia selecionada. Qualificação de distribuição release no Google Play não é alegada.
4. DHU: usar Android Auto instalado/atualizado em telefone compatível ou ambiente efetivamente disponível; AOSP puro não contém host Android Auto. Testar parked/driving, limites de template, manobras, notificações, insets, desconexão/reconexão e bloqueio. DHU emula a central e não prova funcionamento de uma central física. [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu).
5. Carro/telefone físico, ainda necessário: aquisição GPS em céu aberto, 20+ min com tela bloqueada, economia de bateria, retomada/parada, desvio real, recuperação de rede, call/host pause e início direto pela central. Registrar modelos, Android/Android Auto, data e resultados, sem afirmar ensaio que não ocorreu.

O host validator permite todos os hosts somente em debug. O release usa `HostValidator.Builder.addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)`, além dos hosts privilegiados reconhecidos pela biblioteca. A lista deve ser conferida para uma distribuição de produção; não há allow-all em release. Os artefatos desta etapa são debug, e não foi feita validação em DHU ou central física. O mapa empacotado permanece limitado ao recorte local de Goiânia; busca/rotas podem depender do provedor fora dele, sem ampliar os tiles disponíveis.
