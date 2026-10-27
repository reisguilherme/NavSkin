# Interface iOS para integração

Root implementa `@MainActor final class AppStore: ObservableObject` em `ios/GameMap/App/AppStore.swift` e renderer `GameMapView(store: AppStore)` em Map/. UI deve implementar `GameMapRootView` e folhas em `ios/GameMap/UI/` somente. Não duplicar estado nem CoreLocation em Views.

Imports: `SwiftUI`, `GameMapCore`. Disponível iOS17+, Swift5mode. `@Environment(\.scenePhase)` pode chamar `store.setForeground(_ foreground:Bool)` para pausar GPS sem navegação ao sair; App entry também pode fazer isso, root coordena.

Properties do store public internas app:
- `@Published private(set) var snapshot: NavigationSnapshot`
- `@Published private(set) var theme: MapTheme`
- `@Published private(set) var enabledCategories: Set<String>`
- `@Published private(set) var navigationPreferences: NavigationPreferences`
- `@Published private(set) var places: LocalPlacesState`
- `@Published private(set) var following: Bool`
- `@Published private(set) var mapStatus: String?`
- `@Published private(set) var resourceError: String?`
- `@Published private(set) var uiError: String?`
- `let resources: ResourceCatalog` com `saved: [CatalogIcon]`, `vehicles: [CatalogIcon]`, `imageURL(for icon: CatalogIcon, theme: MapTheme, preview: Bool = true) -> URL?`, `previewURL(theme:) -> URL`, `savedIcon(id:) -> CatalogIcon?`, `vehicleIcon(id:) -> CatalogIcon?`. CatalogIcon Identifiable(id:String,label:String,themeAssets:...), imagem PNG real.
- `@Published var mapSelection: Destination?` para seleção no mapa por long press, sheet(detectonChange), root limpia atribuindo nil.

`MapTheme: String, CaseIterable, Identifiable, Codable` casos `.gta`, `.rosa`, `.rdr2`; propriedades `id`, `title`, `subtitle`, `palette: ThemePalette`. `ThemePalette` tem `surface,text,muted,accent,soft,border: Color`. `PoiCategory: String,CaseIterable,Identifiable` casos `.parks,.fuel,.food,.parking,.health,.shops`, `id`,`title`,`symbol`SFsymbol.

Métodos síncronos chamados UI (iniciam Task internos quando rede):
`activateGPS()`, `search(_ query:String)`, `selectDestination(_ destination:Destination)`, `startNavigation()`, `stopNavigation()`, `retryRoute()`, `resumeDestination()`, `recenter()`, `showOverview()`, `zoomBy(_ delta:Double)`, `setTheme(_ theme:MapTheme)`, `setCategory(_ id:String,enabled:Bool)`, `setVehicle(_ id:String)`, `setSavedVisible(_ visible:Bool)`, `toggleOrientation()`, `setAPIKey(_ key:String) -> Bool`, `removeAPIKey()`, `clearUIError()`, `startSimulation()`, `stopSimulation()`, `savePlace(name:String,point:GeoPoint,address:String,categoryId:String?,iconId:String,id:String? = nil) -> Bool`, `deletePlace(_ id:String)`, `setHome(_ id:String?)`, `setWork(_ id:String?)`.

Sem chave, seleção preserva destino e mostra erro configuração. UI oferece configuração com SecureField sem reexibir chave; selecionar um destino pode abrir a folha de chave explicitamente. GPS pede WhenInUse por ação e, após autorização, o runtime inicia. Fonte ainda sem fix aguarda automaticamente para calcular prévia. `snapshot.providerConfigured` indica chave salva; não indica uma chamada autenticada já validada. `snapshot.resumeDestination` oferece recalcular após interrupção. UI formata números/ETA apenas se `guidanceAvailable` ou preview; sem GPS válido não apresenta estimativas antigas como atuais.

UI: mapa ocupa tela; carrossel inicial3cards com imagens do bundle, folhas retráteis para destinos/salvos/temas/6filtros/marcadores/chave. Navegação visual com próxima instrução/via+distância, ETA/distância/tempo/speed, encerrar. Controles follow/overview/orientação não cobrem marcador central. Previewrota pode mostrar e começar/cancelar/salvar. Favoritos CRUD inclcoordenadas/ícone/categoria; Casa/Trabalho e recentes; 12ícones+6veículos por tema. Ícones de catálogo semprePNGreal; controlesSF Symbols ok. Replays DEBUG apenascomSIMULAÇÃO bemvisível, semrotaHTTPfalsa. Sem voz/CarPlay. Aviso recortemapa eGeoapify/OSMcréditosvisíveis. Tratar orientaçãohorizontalcomcompactação, semforçarcarrossel sobreviagemativaretornada.
