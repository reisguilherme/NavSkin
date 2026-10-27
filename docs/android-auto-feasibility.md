# Viabilidade do visualizador POI no Android Auto

Consulta a fontes oficiais em **26 de outubro de 2026**. Este documento verifica contratos e regras; não comprova renderização no DHU nem em veículo real.

## Decisão de categoria

O enquadramento correto é **POI**, com mapa próprio em `MapWithContentTemplate`. A documentação permite essa combinação com `MAP_TEMPLATES`; não é necessário declarar `NAVIGATION` ou `NAVIGATION_TEMPLATES`. `PlaceListMapTemplate` é outra opção POI, mas seu mapa é desenhado pelo host, portanto não atende ao objetivo de usar o estilo MapLibre do projeto. [Guia POI](https://developer.android.com/training/cars/apps/poi).

Há uma condição funcional: o critério **PF-1** exige utilidade significativa para dirigir. O critério **NF-1**, que exige instruções curva a curva, pertence à categoria Navigation. Não há exigência de GPS, permissão de localização ou cálculo de rota no critério POI consultado. A aprovação comercial continua condicionada à experiência concreta e à revisão. Um mapa genérico de demonstração, sozinho, não demonstra conformidade. [Qualidade para carros, critérios PF-1 e NF-1](https://developer.android.com/docs/quality-guidelines/car-app-quality#car-optimized).

A definição de POI inclui encontrar estabelecimentos como estacionamentos, carregadores e postos. A interpretação adotada é: explorar lugares reais úteis ao motorista, filtrar por categoria e consultar seus nomes/detalhes pode cumprir a proposta sem navegação interna. Coordenadas de estabelecimentos são dados cartográficos e não significam obter a localização do usuário. A menor adaptação, se necessária, é tornar a descoberta de serviços ao motorista e seus detalhes a função principal, preservando centro de mapa manual, ausência de GPS e ausência de rotas. Não se deve adicionar uma categoria falsa para contornar requisitos. [Categorias e configuração](https://developer.android.com/training/cars/apps/library/set-up-project#supported-app-categories).

## Declarações mínimas

Trecho ilustrativo para o aplicativo projetado pelo telefone, a integrar ao manifesto completo:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="androidx.car.app.MAP_TEMPLATES" />
    <uses-permission android:name="androidx.car.app.ACCESS_SURFACE" />

    <application android:hardwareAccelerated="true">
        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />
        <meta-data
            android:name="androidx.car.app.minCarApiLevel"
            android:value="7" />
        <service
            android:name=".car.PoiCarAppService"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.POI" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

`MAP_TEMPLATES` dá acesso aos templates POI; `ACCESS_SURFACE` dá acesso à superfície para desenhar o mapa. Nenhuma delas concede localização. O aplicativo deve omitir `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` e serviços de rastreamento; `INTERNET` atende às fontes online que forem usadas. [Permissões da Car App Library](https://developer.android.com/reference/androidx/car/app/CarAppPermission).

O recurso `res/xml/automotive_app_desc.xml` precisa conter:

```xml
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

Esses metadados habilitam a descoberta pelo Android Auto. O nível 7 é da **Car App API do host**, distinto de `minSdk`/`targetSdk` do Android. Hosts abaixo desse nível precisam ser recusados ou receber uma experiência alternativa compatível. [Suporte ao Android Auto](https://developer.android.com/training/cars/apps/auto), [nível mínimo do host](https://developer.android.com/training/cars/apps/library/set-up-project#car-app-api-level).

`CarAppService.createHostValidator()` também precisa ser implementado. `ALLOW_ALL_HOSTS_VALIDATOR` destina-se somente a desenvolvimento; a variante de distribuição deve validar os hosts permitidos. Essa validação de host não substitui a regra de origem da instalação. [HostValidator](https://developer.android.com/reference/androidx/car/app/validation/HostValidator).

## Template e controles

`MapWithContentTemplate` exige Car App API **7** e combina mapa do aplicativo com conteúdo do host; não é uma tela de mapa arbitrária em tela cheia. [Referência do template](https://developer.android.com/reference/androidx/car/app/navigation/model/MapWithContentTemplate).

| Elemento | Contrato e decisão para o projeto |
| --- | --- |
| Conteúdo obrigatório | Usar `ListTemplate`, `PaneTemplate`, `GridTemplate` ou `MessageTemplate` na API 7. A prova inicial pode usar uma lista curta de POIs ou uma mensagem identificando o teste. |
| Ações gerais | `MapWithContentTemplate.Builder.setActionStrip`: no máximo quatro ações; usar, por exemplo, **Filtros** e **Estilo**. |
| Versões posteriores | `SectionedItemTemplate` só é aceito como conteúdo a partir da API 8. Não depender dele na base API 7. |

[Contrato de conteúdo e ações](https://developer.android.com/reference/androidx/car/app/navigation/model/MapWithContentTemplate.Builder).

Os controles de mapa ficam em `MapController`, conectado por `setMapController()`. Sua `setMapActionStrip()` admite no máximo **quatro ações com ícone**. Uma organização possível é `PAN`, ampliar, reduzir e restaurar o enquadramento inicial; restaurar significa voltar a Goiânia/ao enquadramento salvo, sem consultar GPS. `setPanModeListener()` informa entrada e saída do modo de movimentação. [MapController.Builder](https://developer.android.com/reference/androidx/car/app/navigation/model/MapController.Builder).

As seis categorias devem ficar em uma lista nativa de filtros com estado ligado/desligado, acessível por **Filtros**; não cabem como seis botões na faixa do mapa. A quantidade exibida e a paginação devem respeitar os limites informados pelo host. Essa é uma decisão de interface, sujeita à inspeção no DHU; não reproduz os controles HTML sobre a superfície.

O host entrega gestos por `SurfaceCallback.onScroll`, `onScale`, `onFling` e `onClick`. É necessário incluir `Action.PAN` na faixa do mapa para receber interatividade; em telas de toque o botão pode ser oculto pelo host. No modo pan, o host converte controles rotativos/touchpad em callbacks. [Interação com mapas](https://developer.android.com/training/cars/apps/library/interact-map).

Os deltas são em pixels da superfície. Em `onScale`, foco negativo indica foco indisponível; usar um centro válido da área visível em vez de repassar coordenadas negativas. `onScale` e `onFling` podem não existir em alguns carros, justificando os botões explícitos de zoom. Confirmar sentido do deslocamento, ancoragem e densidade no teste. `onSurfaceAvailable` pode repetir por mudança de tamanho/DPI sem destruição; o gerenciador não pode acumular displays. Cada instância de `Surface` recebida deve ser liberada conforme o contrato. [SurfaceCallback](https://developer.android.com/reference/androidx/car/app/SurfaceCallback).

## Prova de renderização antes da migração completa

A documentação Android mostra oficialmente a técnica `SurfaceCallback` → `VirtualDisplay` com largura/altura/DPI do `SurfaceContainer` → `Presentation` contendo uma `View`. Registrar o callback no `AppManager`. O host também informa áreas visível e estável, pois o conteúdo e as ações são sobrepostos ao mapa. A aplicação precisa respeitar essas áreas ao enquadrar lugares e atribuições. [Desenhar mapas, incluindo exemplo de display virtual](https://developer.android.com/training/cars/apps/library/draw-maps#virtual-display).

Para MapLibre Native, a hipótese inicial é colocar um `MapView` com `MapLibreMapOptions().textureMode(true)` na `Presentation`. A opção seleciona `TextureView`; a documentação alerta para seu custo de desempenho. A existência dessa opção não comprova a combinação com o DHU/dispositivo escolhido. [Opção textureMode](https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.maps/-map-libre-map-options/texture-mode.html).

Construir a view com `presentation.context`: a `Presentation` tem contexto e recursos adaptados à densidade do display de destino. [Contrato de Presentation](https://developer.android.com/reference/android/app/Presentation). Verificar que a janela realmente usa aceleração por hardware, requisito de `TextureView`. [TextureView](https://developer.android.com/reference/android/view/TextureView).

Encaminhar o ciclo de vida da sessão/apresentação para `MapView.onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy` e baixa memória, com liberação idempotente na desconexão. O exemplo oficial documenta esses métodos. A correspondência exata com o ciclo da sessão do carro é responsabilidade da integração. [Quickstart MapLibre](https://maplibre.org/maplibre-native/android/examples/getting-started/).

**Compatibilidade do renderer:** o artefato atual `org.maplibre.gl:android-sdk` usa Vulkan por padrão; existem artefatos explícitos OpenGL ES e duplo Vulkan/OpenGL. A primeira inicialização fixa o backend durante o processo. Portanto, fixar versão/artefato da prova e registrar o backend usado; mudar de backend exige reinício. Para uma prova conservadora de `MapView` em `TextureView`, testar explicitamente o artefato OpenGL e só comparar Vulkan em uma execução independente se necessário. Isso é escolha de experimento, não garantia de suporte. [Artefatos e backends oficiais](https://maplibre.org/maplibre-native/android/examples/artifacts/).

Há uma alternativa oficial mais recente: MapLibre Compose documenta `AndroidMapPresentation.attachSurface()` e um demo Android Auto. Ela suporta conexão direta com a superfície, sem exigir hierarquia de UI Compose, mas adiciona outro conjunto de APIs/dependências. Não misturar essa alternativa com a primeira prova de `MapView`; avaliar separadamente se o caminho inicial falhar. [Apresentação fora da UI Compose](https://maplibre.org/maplibre-compose/presentation/), [demo oficial Android Auto](https://github.com/maplibre/maplibre-compose/tree/main/demo-app/android-auto).

Portas de aceitação propostas, ainda não executadas:

1. Exibir mapa identificável e um POI fixo no **Surface do Android Auto**, acompanhado de conteúdo válido do template.
2. Demonstrar pan, zoom pelos botões e por gesto disponível, sem localização, rotas ou Activity de telefone aberta.
3. Redimensionar, trocar dia/noite, sair/voltar e desconectar/reconectar sem superfície preta, display acumulado ou exceção.
4. Salvar captura do DHU, versão do telefone/Android Auto, versão das bibliotecas e logs do backend/erros.
5. Somente após essa prova migrar integralmente estilos, seis filtros e tela complementar do telefone.

O mapa deve responder ao tema claro/escuro indicado pelo host, salvo escolha explícita permitida ao usuário. [Critério MR-1](https://developer.android.com/docs/quality-guidelines/car-app-quality#car-optimized).

## APK, DHU e veículo real

O DHU oficial funciona em Windows e usa um telefone Android com o APK compilado/instalado e Android Auto atualizado. O roteiro inclui o pacote **Android Auto Desktop Head Unit Emulator** do SDK Manager. No túnel ADB, habilitar o modo de desenvolvimento do Android Auto, iniciar **head unit server**, conectar o telefone e encaminhar `tcp:5277`; depois executar `desktop-head-unit.exe`. Isso é procedimento de teste futuro, não execução já realizada. [Teste no DHU](https://developer.android.com/training/cars/testing/dhu).

**Instalar o APK manualmente não é uma promessa de execução no carro físico.** A regra oficial exige instalação por origem confiável, como Google Play. **Internal App Sharing** ou **Internal Test Track** permitem distribuir a dispositivos de teste sem passar pela revisão normal. A opção **Unknown sources** do Android Auto **não se aplica** a aplicativos da Android for Cars App Library; ela contempla mídia, notificações de mensagens e aplicativos de uso estacionado. [Teste em veículos reais e origem da instalação](https://developer.android.com/training/cars/testing#test-in-real-vehicles).

Na consulta, a versão estável da Car App Library é **1.7.0**; **1.8.0-rc01** é candidata e **1.9.0-alpha03** é experimental. A API pública atual pode documentar recursos ainda ausentes da versão estável. Usar somente APIs realmente presentes na versão fixada pelo projeto; não presumir disponibilidade de chips/API 9. [Releases oficiais](https://developer.android.com/jetpack/androidx/releases/car-app).

## Estado de validação deste levantamento

- Confirmado por documentação: categoria POI, permissões, template API 7, limites de ações, caminho Surface/VirtualDisplay/Presentation e restrição de instalação no veículo real.
- Ainda pendente de execução: compilação APK, carregamento real do estilo/dados, renderização MapLibre no host, gestos, ciclo de vida, desempenho e teste físico.
- Não comprovado por este levantamento: aceitação no Google Play ou compatibilidade com uma central específica.
