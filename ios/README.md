# NavSkin para iPhone · 0.5.0

Aplicativo nativo em SwiftUI e MapLibre Native/Metal 6.31.0 para iPhone e iPad com iOS 17 ou posterior. A versão 0.5.0 usa **mapa mundial online por padrão**, mantém **Goiânia offline** como opção e preserva GPS, busca, rotas, manobras visuais, ETA, velocidade, recálculo, favoritos, Casa/Trabalho e recentes.

O repositório é [reisguilherme/NavSkin](https://github.com/reisguilherme/NavSkin). Para gerar e instalar pelo Windows, siga [Sem Mac: GitHub Actions e AltServer](docs/SEM-MAC.md). O workflow está preparado para compilar em macOS; confira o resultado da execução e o [relatório de validação](docs/VALIDATION.md) antes de considerar o build ou os testes Apple aprovados.

## Mapa, temas e navegação

Em **Temas e mapa → Cobertura do mapa**, escolha:

| Modo | Cobertura | Conexão |
|---|---|---|
| Mapa mundial (online) | Cidades e estradas ao redor do mundo; padrão da instalação | Tiles e fontes do OpenFreeMap exigem internet |
| Goiânia offline | Recorte incluído: longitude −49,325 a −49,205; latitude −16,755 a −16,640 | Cartografia, fontes e ícones locais |

Os temas GTA SA, Rosa Pop e RDR2 têm variantes dia/noite nos dois modos. Alterar tema ou cobertura conserva a câmera e a sessão de navegação. Os filtros de lugares, favoritos e o marcador do veículo continuam disponíveis. O serviço público do [OpenFreeMap](https://openfreemap.org/) dispensa chave para exibir o mapa; os créditos OpenFreeMap, OpenMapTiles e OpenStreetMap acompanham a cobertura mundial. O recorte offline mantém os créditos OSM/Overture em `NavSkin/Resources/maps/credits`.

Se a câmera estiver fora de Goiânia ao escolher o modo offline, toque em **Ver região offline** no seletor ou no aviso do mapa para enquadrar o recorte. Essa ação muda apenas a câmera: o GPS e a viagem continuam ativos. **Centralizar no GPS** volta à sua posição atual; fora do recorte, escolha o mapa mundial para carregar detalhes da região.

Busca, cálculo e recálculo de rotas usam **Geoapify**, exigem internet e a chave do próprio usuário nos dois modos. Cadastre a chave em **Configurações → Busca e rotas online**; ela fica no Keychain do aparelho. Consulte os [planos](https://www.geoapify.com/pricing/) e [termos do provedor](https://www.geoapify.com/terms-and-conditions/). Nenhuma chave pessoal acompanha o aplicativo.

Para navegar:

1. Ative GPS e autorize **Durante o Uso** com **Localização Precisa**.
2. Busque um endereço ou cidade, escolha um salvo/recente ou mantenha pressionado um ponto no mapa.
3. Confira a prévia e toque em **Iniciar**. A rota usa perfil automóvel; os veículos do catálogo mudam a aparência do marcador.
4. Arraste para explorar. **Centralizar no GPS** retoma o acompanhamento; a visão geral mostra a rota inteira e o controle de orientação alterna norte e direção do movimento.

Durante uma viagem ativa, o aplicativo pode continuar recebendo localização com a tela bloqueada. Ao sair sem navegação ativa, o GPS de exploração é desligado; recentralize para ativá-lo novamente. Se o processo for encerrado, o aplicativo oferece o destino para calcular uma nova rota com a posição atual. A troca de mapa não encerra a sessão de GPS.

O modo mundial não baixa países para uso offline. O recorte de Goiânia não inclui busca nem cálculo de rota offline. O ETA não usa trânsito ao vivo; esta versão não inclui instruções por voz nem CarPlay. Não há conta NavSkin, sincronização de favoritos ou gravação contínua do trajeto. As consultas ao mapa mundial acessam OpenFreeMap; as consultas de busca e rota acessam Geoapify.

## Abrir no Mac

1. Obtenha o repositório completo ou o pacote-fonte `NavSkin-ios-0.5.0-source.zip`, quando disponibilizado.
2. Abra `ios/NavSkin.xcodeproj`, selecione o esquema **NavSkin** e um simulador com iOS 17 ou posterior. Use um Xcode com SDK compatível com o dispositivo; o projeto usa Swift 5 mode. Consulte os [requisitos do Xcode](https://developer.apple.com/xcode/system-requirements/).
3. Aguarde a resolução do pacote remoto MapLibre, fixado em 6.31.0. `ios/Packages/NavSkinCore` é o pacote local.
4. Execute **Run**. Os recursos preparados ficam em `ios/NavSkin/Resources`; o mapa mundial continuará dependendo de internet durante o uso.
5. Para iPhone físico, selecione sua conta e Team em Signing & Capabilities. Se preferir configuração por arquivo, copie `Config/Local.xcconfig.example` para `Config/Local.xcconfig` e informe Team ID e bundle próprio. Esse arquivo é ignorado pelo Git.

O identificador padrão é `br.com.navskin.ios`; a versão é 0.5.0, build 1. A [Personal Team da Apple](https://developer.apple.com/help/account/basics/about-your-developer-account) permite testes pessoais com provisionamento temporário. TestFlight e publicação exigem o fluxo de distribuição correspondente à conta. Senhas Apple, certificados privados e chaves Geoapify ficam fora dos arquivos de configuração do projeto.

## Build e testes

Na raiz do repositório, em um Mac:

```bash
bash scripts/build-ios.sh doctor
bash scripts/build-ios.sh simulator
swift test --package-path ios/Packages/NavSkinCore
xcrun simctl list devices available
bash scripts/build-ios.sh test SIMULATOR_UDID
bash scripts/build-ios.sh archive YOUR_APPLE_TEAM_ID
```

O esquema contém `NavSkinTests`, com testes Apple de recursos, estilos locais/mundiais e Keychain. O núcleo portátil tem sua própria suíte XCTest. O comando `archive` exige assinatura configurada; a exportação fica no Organizer do Xcode. O caminho sem Mac usa o [workflow dedicado](../.github/workflows/ios-cloud.yml) para gerar um IPA de dispositivo sem assinatura pessoal, seguido de assinatura no Windows.

Para regenerar recursos e projeto no workspace completo:

```bash
npm ci
node scripts/prepare-ios.mjs
```

Os assets Android são a fonte compartilhada dos mapas e catálogos. `node scripts/prepare-ios.mjs --project-only` atualiza apenas as referências do projeto após adicionar ou remover fontes. O pacote-fonte iOS preparado inclui os recursos necessários para abrir no Xcode.

## Verificação no aparelho

O debug oferece replay identificado como **SIMULAÇÃO**; ele não representa GPS real nem resposta do provedor. Localizações simuladas pelo sistema são aceitas e identificadas no debug. Release rejeita fixes marcados como simulados e não oferece replay pelo aplicativo.

Antes de usar em viagem, confira no iPhone os dois modos de mapa, temas dia/noite, posição real fora de Goiânia, busca autenticada, prévia, chegada, desvio/recálculo, perda e retorno de GPS, câmera manual/recentralização, rotação e tela bloqueada. Registre modelo, versão do iOS e resultados no [relatório](docs/VALIDATION.md). Compilar no CI ou passar testes do núcleo não comprova esses cenários de campo.

A arte dos catálogos é original e os temas são interpretações independentes, sem vínculo com a Rockstar. O manifesto de privacidade declara UserDefaults para preferências do aplicativo. Fontes e licenças acompanham os recursos.
