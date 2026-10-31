# NavSkin — Android

A **0.12.0** inclui oito marcadores 3D que entram automaticamente ao navegar ou usar Vibing. O ângulo acompanha o movimento e a câmera, com carga de imagens fora da UI. O ícone escolhido nas opções retorna ao explorar. [Avatares e APK](../docs/navigation-avatars-v0.12.md) · [Validação](docs/VALIDATION-v0.12.md).

A **0.11.0** reúne **oito temas**: GTA SA, Rosa Pop, RDR2, Super Mario World, Cyberpunk 2077, Pip-Boy, Hollow Knight e Crossy Road. São 32 estilos entre dia/noite e as duas coberturas, oito prévias e catálogos de locais/veículos. Inclui os redesenhos aprovados nos painéis, ícones Pink World e seu carrinho temático. [Recursos e procedência](../docs/eight-theme-assets.md).

**Vibing** fica no menu ⋯ do telefone: mapa e veículo em 3D, GPS acompanhando o movimento, sem cards. Pode iniciar sem destino ou continuar uma rota. Um toque revela Sair e Recentralizar por cinco segundos; Voltar restaura os controles sem encerrar a viagem. [Uso e limites](../docs/vibing-v0.11.md).

**Mapa 3D** fica no menu ⋯ do telefone e em Aparência no Android Auto. A escolha é persistida separadamente: exploração começa em 2D, navegação em 3D. A prévia/visão geral da rota permanece plana; recentralizar retoma a perspectiva escolhida. Todos os temas usam contornos reais dos prédios, com alturas ilustrativas quando faltam medições. Vibing força 3D temporariamente e preserva essas preferências.

Minecraft e GTA V foram retirados desta entrega a pedido do usuário. Os SVGs e as paletas ficam como rascunhos, fora dos seletores e catálogos ativos.

A **0.9.0** aplica a interface Vidro: menu **⋯** dentro do card de direção durante a viagem, controles circulares iguais e **Parar / Adicionar parada** no rodapé. Paradas reais são calculadas em ordem, mantendo o destino final e a rota confirmada em caso de falha. Temas, filtros, salvos e configurações ficam no menu. [Uso e limites](../docs/navigation-v0.9.md).

A **0.8.0** liberou girar o mapa, acompanha o rumo com transições e aproxima curvas/retornos e baixas velocidades. A instrução de manobra é maior e o ETA tem três campos. A camada opcional TomTom mostra fluxo e incidentes mediante chave própria. [Configuração, comportamento e limites](../docs/navigation-v0.8.md).

A 0.7.1 separa espera pelo GPS de erro de rota: o primeiro fix é aguardado com progresso e a prévia é calculada automaticamente. Internet/chave/quota continuam sendo falhas explicadas, com tentativa explícita. [Regressões e validação](docs/VALIDATION-v0.7.1.md).

Aplicativo nativo em Kotlin com mapas mundiais, GPS do Android e navegação visual para automóvel no telefone e no Android Auto. A integração usa a categoria **Navegação** da Car App Library. MapLibre Native renderiza **Mundo online**, padrão, com mapas OpenFreeMap / OpenStreetMap por HTTPS, ou **Goiânia offline**, com o recorte cartográfico incluído no APK. Geoapify fornece busca e rotas pela internet com a chave do usuário.

Versão do código: **0.12.0**, `versionCode 12`. Consulte [avatares 3D da versão atual](../docs/navigation-avatars-v0.12.md) e [validação da v0.12.0](docs/VALIDATION-v0.12.md). O identificador de instalação, a assinatura debug e os arquivos de preferências/dados mantêm os nomes anteriores para permitir atualização e conservar favoritos, destinos e configurações. Os registros da [v0.6](docs/VALIDATION-v0.6.md), [v0.5](docs/VALIDATION-v0.5.md), [v0.4.0](docs/VALIDATION-v0.4.md), [v0.3.0](docs/VALIDATION-v0.3.md), suas [capturas](screenshots/v0.3/README.md) e a [primeira entrega](docs/VALIDATION.md) permanecem como histórico.

Na 0.7, Temas/Filtros sobem para o cabeçalho. O botão de menu abre salvos, GPS, configurações, orientação, zoom e créditos. Busca inferior, velocidade e recentralização circulares substituem os painéis ociosos e a faixa de atribuição. O card de ETA mostra somente tempo, distância e chegada, centralizados; rótulos GTA estão maiores. O marcador padrão GTA usa radar_centre original, com escala lógica corrigida em telas densas. Overture local também é renderizado no modo Mundo em Goiânia, sem duplicar os prédios mundiais; fora da cobertura continua OpenFreeMap.

## Recursos visuais da versão 0.6

Pricedown histórica é usada na UI e nos rótulos GTA; Hapna e RDR Lino no tema RDR2. Os sprites originais de POIs mantêm sua procedência, com adaptações próprias para categorias sem equivalente no jogo. Cores, contornos, nomes de ruas e sentidos foram calibrados pelos exemplos. São nove filtros, incluindo ensino, cultura e culto; os três novos grupos não estão no snapshot offline atual, mas aparecem no mundial. [Fontes, origem e importação](../docs/game-assets-provenance.md).

Os recursos preparados e seus arquivos de origem estão versionados com os avisos dos titulares. O build verifica sua integridade; um clone atual dispensa pacote visual separado. A licença do código não se estende às fontes e à arte de terceiros.

## Funcionalidades implementadas

- GPS do Android com precisão, direção de deslocamento, velocidade em km/h e estados de sinal indisponível, fraco e perdido. Leituras antigas não continuam sendo apresentadas como atuais.
- Pesquisa de endereços e estabelecimentos; seleção por salvos, recentes ou ponto no mapa do telefone; prévia com destino, distância, duração e chegada antes de iniciar.
- Rota de automóvel destacada, próximas manobras visuais, via seguinte quando disponível, distância/tempo restantes e horário estimado de chegada. ETA sem trânsito ao vivo; sem voz. Fluxo/incidentes TomTom opcionais aparecem em camada independente.
- Progresso calculado ao longo da rota, confirmação de desvio por leituras sucessivas, recálculo com proteção contra respostas antigas e estado explícito quando falha.
- Acompanhamento do veículo, exploração manual, recentralização, rota completa e orientação norte para cima ou direção do movimento.
- Sessão única entre telefone e central, mantida por foreground service com notificação. Após encerramento do processo, o destino pode ser retomado com uma nova rota.
- Mapa ocupando a tela, controles flutuantes e menus retráteis para cobertura, temas e filtros. **Mundo online** usa rede; **Goiânia offline** usa somente o recorte incluído. A escolha é compartilhada e persistida entre telefone e central.
- Carrossel em **⋯ → Temas** com prévias reais e locais dos oito temas. Cada tema tem variante noturna selecionada pelo modo do sistema no telefone e pelo host no carro.
- Rosa Pop com interface clara, rosa vivo, menta e turquesa; RDR2 com papel sépia e tinta. GTA SA usa Bank Gothic nas ruas/POIs e Pricedown nos nomes maiores; RDR2 usa Hapna e RDR Lino nos dois modos de mapa. Noto complementa caracteres ausentes. Sprites originais e adaptações NavSkin têm sua procedência registrada separadamente.
- Nove filtros independentes: parques; combustível; alimentação; estacionamento; saúde; mercados e conveniências; ensino; cultura; locais de culto.
- Locais salvos editáveis no telefone, atalhos Casa/Trabalho e destinos recentes, persistidos localmente. A visibilidade dos salvos é independente dos filtros de POI.
- Catálogo próprio com 12 ícones de locais e seis marcadores de veículo por tema, prévias reais e IDs semânticos preservados ao trocar tema. O desenho de moto ou veículo temático é uma preferência visual; a rota continua sendo de automóvel.
- No carro, `NavigationTemplate`, manobras/estimativas oficiais e controles do host; destinos salvos/recentes, pesquisa permitida, iniciar/encerrar, câmera, temas, filtros e seleção simplificada de marcador.
- Preferências de cobertura, tema, filtros, veículo e orientação compartilhadas; a troca visual preserva GPS, rota e viagem, e cada tela mantém sua própria câmera. Reprodução de desenvolvimento é separada e identificada como **SIMULAÇÃO**.

O mapa mundial depende de internet e não exige chave cartográfica. Quando falta rede, o telefone e o carro indicam o problema e oferecem acesso à escolha de Goiânia offline. Esse modo cobre apenas o recorte incluído. Busca, cálculo e recálculo dependem de rede e chave Geoapify; não há navegação offline completa. Desligar um filtro oculta apenas seus ícones de POI. Não há contas do aplicativo, sincronização em nuvem nem gravação contínua do trajeto.

## Começar a usar

No telefone, escolha a cobertura em **Temas** ou **Filtros**; ela também está em **⋯ → Configurações → Cobertura do mapa**. No Android Auto, use **Aparência → Cobertura do mapa**. Os oito temas e suas versões noturnas estão disponíveis nos dois mapas. Os nove filtros aparecem no painel; ensino, cultura e culto dependem dos dados mundiais e ainda não estão no snapshot offline. As cinco prévias anteriores são capturas nativas; as três novas mostram os recortes aprovados no painel, com procedência registrada.

Para navegar, abra **⋯ → Configurações → Configurar chave Geoapify**, salve uma chave da sua conta e ative **GPS**. Autorize localização precisa durante o uso; a notificação mantém a sessão com a tela bloqueada. A chave é criptografada com Android KeyStore e não precisa ser inserida no código ou no build. Sem chave, o mapa, GPS e dados locais continuam disponíveis; o aplicativo informa a configuração pendente ao solicitar busca ou rota.

Pesquise um destino, escolha um local salvo/recente ou mantenha pressionado um ponto do mapa. Confira a prévia e toque em **Iniciar**. Arrastar o mapa suspende o acompanhamento; **Recentralizar** retoma. Edição detalhada de favoritos e catálogos ficam no celular. No Android Auto, o teclado depende de estacionamento e a entrada/seleção segue os templates do host. Se o Android impedir ativar o GPS apenas pela central, faça a ativação no telefone quando estacionado e depois use os destinos pela multimídia.

Consulte [NAVIGATION.md](docs/NAVIGATION.md) para permissões, recuperação, configuração e roteiro atual. [Fornecedor e custos](../docs/navigation-provider-decision.md) registra a oferta consultada e alternativas; configurar uma chave não contrata automaticamente um plano pago.

## Implementação e compatibilidade

| Componente | Versão/configuração do projeto |
| --- | --- |
| Kotlin | 2.2.21, alvo JVM 17 |
| Android Gradle Plugin | 8.13.2 |
| Gradle | 8.13 |
| Java | JDK 17; runtime portátil Temurin 17.0.20.1+1 |
| Android | `minSdk 26`, `compileSdk/targetSdk 36`; OpenGL ES 3.0 |
| MapLibre Native | `org.maplibre.gl:android-sdk-opengl:13.7.1` |
| Car App Library | `app` e `app-projected` 1.7.0 |
| Interface do telefone | Views Kotlin e Material Components 1.13.0 |
| Host do carro | Car App API 7 ou superior |

O manifesto declara `androidx.car.app.category.NAVIGATION`, `NAVIGATION_TEMPLATES`, `MAP_TEMPLATES` e `ACCESS_SURFACE`, além das permissões de localização precisa/aproximada, foreground service de localização, notificações e estado da rede. Não solicita localização permanente. `LocationManager` obtém GPS sem depender do provedor Google Play Services. Estilos, sprites e fontes do modo offline acompanham o APK. Mundo usa tiles vetoriais e fontes OpenFreeMap por HTTPS; Goiânia offline usa os dados locais. Busca e rotas usam HTTPS.

No telefone, há uma view nativa de mapa. No carro, `CarSurfaceRenderer` recebe a Surface do host e cria um `VirtualDisplay` com uma `Presentation` contendo outro `MapView` em modo `TextureView`. A interface do telefone não é espelhada. Consulte o [contrato e a prova mínima de Surface](docs/surface-probe.md).

`MapWithContentTemplate` apresenta destinos e prévia; `NavigationTemplate` apresenta navegação ativa. A Surface contém mapa, rota, marcadores e velocidade passiva. As manobras, estimativas e ações usam componentes oficiais. O contrato de plataforma e as APIs CAL 1.7.0 estão em [android-navigation-platform.md](../docs/android-navigation-platform.md). O uso do artefato OpenGL explícito mantém o backend já adotado; consulte [artefatos MapLibre](https://maplibre.org/maplibre-native/android/examples/artifacts/).

## Compilar no Windows

Os comandos abaixo partem da **raiz do repositório**, acima de `android/`, em PowerShell. O ambiente entregue usa `.runtime/` para JDK, Gradle, SDK e caches. Em uma máquina nova, o download inicial de ferramentas e dependências requer rede.

```powershell
# Somente se as ferramentas portáteis ainda não estiverem preparadas:
.\scripts\android-toolchain.ps1 -Action Bootstrap
.\scripts\android-toolchain.ps1 -Action InstallSdk

# Inspecionar versões e pacotes já instalados:
.\scripts\android-toolchain.ps1 -Action Doctor

# Compilar o APK de desenvolvimento:
.\scripts\android-toolchain.ps1 -Action Gradle -ToolArguments @(':app:assembleDebug')
```

`InstallSdk` mantém a revisão dos contratos interativa quando necessária. A licença dos pacotes usados já foi aceita neste workspace. O script também configura um diretório temporário curto para os sockets Java no Windows.

Saída padrão: `android/app/build/outputs/apk/debug/app-debug.apk`. O pacote debug é intencionalmente `br.com.gamemap.goiania.dev`; o identificador base continua `br.com.gamemap.goiania`. Esses identificadores técnicos anteriores não limitam a cobertura mundial. Instale com `adb install -r`, usando a mesma assinatura, para preservar os dados da v0.4. O APK empacota os assets existentes: não é necessário baixá-los novamente para compilar.

Os artefatos históricos [APK 0.4.0](artifacts/gamemap-goiania-v0.4.0-debug.apk) e [bundle debug 0.4.0](artifacts/gamemap-goiania-v0.4.0-debug.aab) ficam em [artifacts/](artifacts/). O [relatório v0.4](docs/VALIDATION-v0.4.md) registra aquela versão. Para gerar o AAB atual, execute o script com `-Action Gradle -ToolArguments @(':app:bundleDebug')`. O bundle não se instala diretamente. Permanecem o [APK 0.3.0](artifacts/gamemap-goiania-v0.3.0-debug.apk), o [AAB 0.3.0](artifacts/gamemap-goiania-v0.3.0-debug.aab) e os artefatos `gamemap-goiania-mvp-debug.*` da 0.2.0.

Para regenerar os assets após editar os dados ou o estilo:

```powershell
npm ci
node scripts/prepare-android-probe.mjs
node scripts/prepare-android-maps.mjs
node scripts/prepare-world-maps.mjs
node scripts/prepare-navigation-icons.mjs
node --test tests/android-pois.test.mjs tests/android-native-assets.test.mjs tests/navigation-icons.test.mjs
```

Os cards usam cinco capturas reais do renderizador nativo. Após mudar o estilo, atualize `android/screenshots/v0.10/world-sao-paulo-{gta,rosa,rdr2,smw,cyberpunk}.png` pelo cenário instrumentado `NativeWorldMapTest`, mantendo a câmera documentada no [índice de capturas](screenshots/v0.10/README.md), e rode `node scripts/prepare-android-previews.mjs` antes de recompilar. O preparador gera `maps/previews/*.webp` e registra os hashes dos estilos e das imagens de origem.

Esses preparadores usam os arquivos salvos no projeto. Uma atualização voluntária da base POI é separada: `node scripts/android-pois.mjs --fetch` consulta Overpass; em seguida, rode novamente a preparação dos mapas. Sem `--fetch`, o adaptador POI reconstrói a partir do cache.

`prepare-navigation-icons.mjs` rasteriza os SVGs editáveis de `android/maps/catalogs/` e publica catálogo, SVGs e PNGs em `app/src/main/assets/navigation-icons/`, preservando os desenhos de origem. Veja [catálogos e licença](../docs/navigation-icon-catalogs.md). Os assets preparados já acompanham o projeto; não é necessário usar uma chave de API para gerá-los ou compilar.

## Instalar para desenvolvimento

Com o AVD ou um telefone de teste autorizado conectado:

```powershell
.\scripts\android-toolchain.ps1 -Action Devices
$adb = Join-Path (Get-Location) '.runtime\android-sdk\platform-tools\adb.exe'
$deviceSerial = 'emulator-5554' # Substitua pelo serial listado, se necessário.
& $adb -s $deviceSerial install -r '.\android\app\build\outputs\apk\debug\app-debug.apk'
& $adb -s $deviceSerial shell am start -n 'br.com.gamemap.goiania.dev/br.com.gamemap.phone.MainActivity'
```

Isso instala a experiência do telefone para desenvolvimento. Para configuração e testes atuais, siga [docs/NAVIGATION.md](docs/NAVIGATION.md); [docs/TESTING.md](docs/TESTING.md) preserva o roteiro anterior de conexão. Um APK instalado por ADB, sozinho, não garante presença no Android Auto do carro. Aplicativos da Car App Library precisam de origem confiável no veículo; a opção “fontes desconhecidas” do Android Auto não se aplica a eles. [Regra oficial de testes em veículos](https://developer.android.com/training/cars/testing#test-in-real-vehicles).

O debug utiliza `ALLOW_ALL_HOSTS_VALIDATOR` para desenvolvimento. O release usa `HostValidator.Builder` com `androidx.car.app.R.array.hosts_allowlist_sample`, além do reconhecimento de hosts privilegiados previsto pela biblioteca; não permite hosts arbitrários. Uma distribuição release requer conferir a lista de hosts e configurar a assinatura de produção. [HostValidator](https://developer.android.com/reference/androidx/car/app/validation/HostValidator).

## Dados e edição

Mundo online usa a cobertura OpenFreeMap / OpenStreetMap. A disponibilidade e a classificação dos locais variam conforme os dados de cada região; a contagem local não representa o catálogo mundial.

O recorte de **Goiânia offline** é `[-49.325, -16.755, -49.205, -16.640]`, em longitude/latitude. Há **1.571 POIs**: 571 parques, 201 postos, 308 de alimentação, 152 estacionamentos, 245 de saúde e 94 mercados/conveniências. A base POI informada por Overpass é de **27/10/2026, 00:49:32 UTC**; download e geração têm campos próprios. Não houve verificação do funcionamento atual dos estabelecimentos.

| Onde editar | Finalidade |
| --- | --- |
| `android/maps/theme-tokens.json` | Paletas, limites de zoom e parâmetros cartográficos |
| `android/maps/icons/{gta,rosa,rdr2,smw,cyberpunk}/` | Ícones SVG editáveis dos nove grupos e seus subtipos |
| `android/maps/catalogs/{gta,rosa,rdr2,smw,cyberpunk}/{saved,vehicles}/` | SVGs editáveis de locais salvos e veículos dos oito temas ativos |
| `android/maps/catalogs/catalog.json` | IDs semânticos, correspondências por tema e fallbacks |
| `android/maps/previews/` | Prévias reais, convertidas para WebP e com procedência |
| `android/maps/pois.geojson` e `pois-metadata.json` | Pacote POI Android e procedência |
| `scripts/prepare-android-maps.mjs` | Conversão dos dados, estilos e sprites para assets nativos |
| `app/src/main/assets/maps/` | Dados gerados e incluídos no APK |
| `app/src/main/assets/navigation-icons/` | Catálogos e imagens gerados, incluídos no APK |

No modo mundial, o fornecedor de tiles é OpenFreeMap, no esquema OpenMapTiles, e os dados são OpenStreetMap. No recorte offline, vias, áreas e POIs vêm de OpenStreetMap; edificações incluem Overture e suas fontes Google/Microsoft. Contornos inferidos de imagens não equivalem a cadastro municipal. Fontes, ícones e dados locais têm créditos e licenças em `app/src/main/assets/maps/credits/`, incluindo `sources.json`, `attribution.html` e `asset-manifest.json` com hashes. O app apresenta a atribuição correspondente ao mapa selecionado e os créditos Geoapify de busca/rotas.

Em **Goiânia offline**, rotas podem ultrapassar o recorte local; nesses trechos faltam detalhes dos tiles empacotados e a prévia informa essa limitação. Em **Mundo online**, a cobertura usa os tiles mundiais, sem essa restrição geográfica local. Nenhum modo oferece cálculo offline de rotas.

Consulte também [procedência e categorias POI](../docs/android-poi-categories.md), [decisão de navegação e custos](../docs/navigation-provider-decision.md), [requisitos de plataforma](../docs/android-navigation-platform.md), [viabilidade Android Auto anterior](../docs/android-auto-feasibility.md) e [ambiente emulado](../docs/emulator-status.md).

O clone atual já contém os recursos preparados. O ZIP de assets 0.7.0 é histórico e não é necessário. Os arquivos de assinatura pessoal continuam excluídos.
