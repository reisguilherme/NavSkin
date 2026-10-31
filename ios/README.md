# NavSkin para iPhone · 0.12.0

A **0.12.0** prepara os mesmos oito marcadores 3D do Android, ativados durante navegação e Vibing, com perspectiva relativa à câmera, carga assíncrona e cache limitado. Os recursos já estão em `NavSkin/Resources/navigation-3d/`. [Uso e modelos](../docs/navigation-avatars-v0.12.md) · [Validação atual](docs/VALIDATION-v0.12.md). A compilação UIKit/MapLibre e o teste no iPhone ainda precisam de um Mac.

O catálogo atual reúne **GTA SA, Rosa Pop, RDR2, Super Mario World, Cyberpunk 2077, Pip-Boy, Hollow Knight e Crossy Road**, com dia/noite e catálogos de locais/veículos. Todos oferecem **Mapa 3D** no menu **⋯**: exploração começa em 2D e navegação em 3D, com escolhas salvas separadamente. A prévia da rota permanece plana até iniciar a viagem. [Uso e validação de 2D/3D](docs/MAP-DIMENSIONS.md).

A **0.10.0** adicionou Super Mario World e Cyberpunk 2077. Minecraft e GTA V foram retirados; uma preferência antiga por esses temas usa o fallback GTA SA. GTA SA usa Bank Gothic para ruas e POIs mais legíveis, preservando Pricedown nos nomes maiores. [Temas e procedência](../docs/maps-v0.10.md).

A **0.9.0** aplica o visual Vidro com material nativo, menu **⋯**, controles circulares iguais, ETA de três valores e **Parar / Adicionar parada**. Paradas reais mantêm o destino final, entram na rota em ordem e são preservadas para nova tentativa quando o cálculo falha. A interface usa tipografia do sistema; as fontes e os ícones temáticos continuam nos mapas. [Uso e limites](../docs/navigation-v0.9.md).

A 0.8.0 acrescentou câmera adaptativa, rotação, novo painel de manobras e ETA, e trânsito TomTom opcional com chave separada no Keychain. O projeto ainda precisa ser compilado e testado no Mac. [Configuração e limites](../docs/navigation-v0.8.md).

Aplicativo nativo em SwiftUI e MapLibre Native/Metal 6.31.0 para iPhone e iPad com iOS 17 ou posterior. A versão 0.12.0 usa **mapa mundial online por padrão**, mantém **Goiânia offline** como opção e preserva GPS, busca, rotas, manobras visuais, ETA, velocidade, recálculo, favoritos, Casa/Trabalho e recentes. Inclui nove filtros, fontes temáticas locais e fallback de sistema/Noto.

O repositório é [reisguilherme/NavSkin](https://github.com/reisguilherme/NavSkin). O projeto está preparado para um amigo compilar no Mac e enviar o IPA ao Guilherme, que fará a assinatura pessoal no Windows. A compilação Apple e os testes no iPhone ainda precisam ser executados; consulte o [relatório de validação](docs/VALIDATION.md).

Na 0.7, Temas/Filtros ficam no topo ao lado de NavSkin, e salvos/opções/créditos ficam no menu retrátil. A busca passa para baixo, com velocidade e recentralização em círculos nos cantos. O mapa ocupa toda a tela, com resumo da rota somente durante uma viagem. O GTA usa radar_centre original no marcador padrão; o modo mundial também mostra os footprints Overture locais em Goiânia.

## Para quem vai compilar no Mac

Você precisa de um **Mac com Xcode completo**, SDK compatível e um simulador de iPhone com iOS 17 ou posterior instalado. O projeto usa Swift 5 mode. O Swift Package Manager baixa o **MapLibre 6.31.0** pela internet; `NavSkinCore` é local. O empacotador usa `python3` no Terminal. Confira os [requisitos do Xcode](https://developer.apple.com/xcode/system-requirements/).

1. Clone a branch `main` de `https://github.com/reisguilherme/NavSkin.git`. O projeto 0.12.0 inclui os recursos preparados dos oito temas e avatares 3D. Preserve as pastas `ios`, `scripts` e `tests` juntas.
2. Abra **`ios/NavSkin.xcodeproj`** no Xcode e aguarde a resolução dos pacotes.
3. Selecione o esquema **NavSkin** e um **simulador de iPhone com iOS 17+** como destino.
4. Clique em **Run**. Os recursos em `ios/NavSkin/Resources` já estão preparados; o mapa mundial precisa de internet durante o uso.

No Terminal, entre na pasta que contém `ios` e `scripts`. Estes comandos verificam o ambiente, compilam para simulador e executam a suíte no dispositivo simulado escolhido:

```bash
bash scripts/build-ios.sh doctor
bash scripts/build-ios.sh simulator
xcrun simctl list devices available
bash scripts/build-ios.sh test SIMULATOR_UDID
```

Substitua `SIMULATOR_UDID` pelo identificador listado no comando anterior. O último comando executa os testes do núcleo e `NavSkinTests`, com verificações de recursos, estilos e Keychain. Confira a saída para confirmar o resultado.

### Gerar o IPA para enviar ao Guilherme

Ainda nessa pasta, execute:

```bash
RUN_APPLE_TESTS=true bash scripts/build-ios-cloud.sh
```

Apesar do nome, esse script **também roda localmente no Mac**. Ele executa testes, compila o aplicativo para iPhone físico arm64 e empacota o resultado **sem assinatura pessoal**. Não usa GitHub e não exige login Apple, Team ou certificado do amigo.

Se todas as etapas terminarem com sucesso, os arquivos serão:

```text
ios/artifacts/cloud/NavSkin-unsigned.ipa
ios/artifacts/cloud/NavSkin-unsigned.json
```

Envie esses dois arquivos ao Guilherme. O IPA será assinado e instalado no Windows com a conta Apple do próprio Guilherme, seguindo [AltServer no Windows](docs/SEM-MAC.md#2-preparar-o-windows). Não inclua credenciais, certificados ou perfis pessoais do amigo no pacote. Se a compilação falhar, os logs ficam em `ios/artifacts/cloud`; corrija a falha antes de enviar o IPA como pronto.

### Instalar diretamente pelo Xcode, se preferir

Para rodar em um iPhone físico com iOS 17+, o responsável pela instalação precisa configurar sua própria Apple Account, Team e perfis de provisionamento em **Signing & Capabilities**. A [Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account) tem validade e limites de teste. Isso é opcional para o amigo: gerar o IPA unsigned pelo comando acima não exige essa assinatura.

O identificador padrão é `br.com.navskin.ios`, versão 0.12.0, build 12. Para assinatura própria, copie `Config/Local.xcconfig.example` para `Config/Local.xcconfig` e configure Team ID e bundle próprio; esse arquivo é ignorado pelo Git. Para criar um archive assinado no Mac:

```bash
bash scripts/build-ios.sh archive YOUR_APPLE_TEAM_ID
```

A exportação do archive fica no Organizer do Xcode e depende dos perfis da conta. TestFlight e publicação usam o fluxo de distribuição correspondente à conta.

O [workflow GitHub Actions](../.github/workflows/ios-cloud.yml) permanece como alternativa manual. A execução nesta conta foi bloqueada pelo GitHub; ela não é necessária para entregar o projeto ao amigo nem comprova compilação Apple aprovada. O [guia do Windows](docs/SEM-MAC.md) explica como receber o IPA e fazer a assinatura pessoal.

## Mapa, temas e navegação

Em **⋯ → Configurações → Opções do mapa → Cobertura do mapa**, escolha:

| Modo | Cobertura | Conexão |
|---|---|---|
| Mapa mundial (online) | Cidades e estradas ao redor do mundo; padrão da instalação | Tiles e blocos adicionais de caracteres do OpenFreeMap exigem internet |
| Goiânia offline | Recorte incluído: longitude −49,325 a −49,205; latitude −16,755 a −16,640 | Cartografia, fontes e ícones locais |

Os oito temas têm variantes dia/noite nos dois modos: **32 estilos** ao todo e **oito prévias**. Alterar tema ou cobertura conserva a câmera, a dimensão escolhida e a sessão de navegação. Os filtros de lugares, favoritos e o marcador do veículo continuam disponíveis. O serviço público do [OpenFreeMap](https://openfreemap.org/) dispensa chave para exibir o mapa; os créditos OpenFreeMap, OpenMapTiles e OpenStreetMap acompanham a cobertura mundial. O recorte offline mantém os créditos OSM/Overture em `NavSkin/Resources/maps/credits`.

Os nove filtros abrangem parques; combustível; restaurantes, cafés e bares; estacionamento; saúde; mercados e conveniências; escolas e universidades; museus, teatros e cultura; locais de culto. As fontes incluem Pricedown e Bank Gothic para GTA SA, Hapna Slab Serif DemiBold e RDR Lino para RDR2, SMW Whole-Pixel Spacing e Blender Pro Bold. Pip-Boy reutiliza Blender; Hollow Knight reutiliza Hapna; Crossy Road usa Noto no mapa. Essas adaptações não são fontes extraídas desses três jogos. SMW é uma reconstrução de Eric Kaiser/wye; Blender veio do site oficial Cyberpunk, convertido de WOFF para TTF. O pack RDR2 descreve extração/conversão do jogo, sem verificação binária contra os arquivos distribuídos. Essas escolhas não comprovam equivalência às fontes cartográficas originais. Noto supre caracteres ausentes. Sprites originais GTA SA/RDR2 aparecem nos subtipos equivalentes; os demais símbolos, incluindo os conjuntos SMW/Cyberpunk, são adaptações próprias NavSkin. Consulte [origem, hashes e avisos](../docs/game-assets-provenance.md).

O catálogo de salvos e veículos mantém 18 IDs semânticos em cada tema: 12 locais e seis veículos. São **144 combinações tema/escolha**, com **288 PNGs finais** nas resoluções de mapa e seletor. Trocar de tema mantém o ID escolhido. As prévias ajudam a comparar os estilos compartilhados; a renderização iOS ainda precisa ser conferida no Mac e no iPhone.

Se a câmera estiver fora de Goiânia ao escolher o modo offline, toque em **Ver região offline** no seletor ou no aviso do mapa para enquadrar o recorte. Essa ação muda apenas a câmera: o GPS e a viagem continuam ativos. **Centralizar no GPS** volta à sua posição atual; fora do recorte, escolha o mapa mundial para carregar detalhes da região.

Busca, cálculo e recálculo de rotas usam **Geoapify**, exigem internet e a chave do próprio usuário nos dois modos. Cadastre a chave em **Configurações → Busca e rotas online**; ela fica no Keychain do aparelho. Consulte os [planos](https://www.geoapify.com/pricing/) e [termos do provedor](https://www.geoapify.com/terms-and-conditions/). Nenhuma chave pessoal acompanha o aplicativo.

Para navegar:

1. Ative GPS e autorize **Durante o Uso** com **Localização Precisa**.
2. Busque um endereço ou cidade, escolha um salvo/recente ou mantenha pressionado um ponto no mapa.
3. Confira a prévia e toque em **Iniciar**. A rota usa perfil automóvel; os veículos do catálogo mudam a aparência do marcador.
4. Arraste para explorar. **Centralizar no GPS** retoma o acompanhamento; a visão geral mostra a rota inteira e o controle de orientação alterna norte e direção do movimento.

Durante uma viagem ativa, o aplicativo pode continuar recebendo localização com a tela bloqueada. Ao sair sem navegação ativa, o GPS de exploração é desligado; recentralize para ativá-lo novamente. Se o processo for encerrado, o aplicativo oferece o destino para calcular uma nova rota com a posição atual. A troca de mapa não encerra a sessão de GPS.

O modo mundial não baixa países para uso offline. O recorte de Goiânia não inclui busca nem cálculo de rota offline. O ETA não usa trânsito ao vivo; esta versão não inclui instruções por voz nem CarPlay. Não há conta NavSkin, sincronização de favoritos ou gravação contínua do trajeto. As consultas ao mapa mundial acessam OpenFreeMap; as consultas de busca e rota acessam Geoapify.

## Regenerar recursos e projeto

Esta etapa serve para atualizar os recursos no workspace completo. Ela não é necessária para abrir o pacote-fonte já preparado no Xcode:

```bash
npm ci
node scripts/prepare-ios.mjs
```

Os assets Android são a fonte compartilhada dos mapas, fontes e catálogos. Para reconstruir a importação original a partir dos arquivos locais e gerar os PBFs, siga o [pipeline dos assets dos jogos](../docs/game-assets-provenance.md#reproduzir-a-importação-local) antes de preparar o iOS. `node scripts/prepare-ios.mjs --project-only` atualiza apenas as referências do projeto após adicionar ou remover fontes. O pacote-fonte iOS preparado inclui os recursos necessários para abrir no Xcode; os arquivos originais versionados mantêm seus próprios avisos e são separados da licença do código.

## Verificação no aparelho

O debug oferece replay identificado como **SIMULAÇÃO**; ele não representa GPS real nem resposta do provedor. Localizações simuladas pelo sistema são aceitas e identificadas no debug. Release rejeita fixes marcados como simulados e não oferece replay pelo aplicativo.

Antes de usar em viagem, confira no iPhone os dois modos de mapa, temas dia/noite, posição real fora de Goiânia, busca autenticada, prévia, chegada, desvio/recálculo, perda e retorno de GPS, câmera manual/recentralização, rotação e tela bloqueada. Registre modelo, versão do iOS e resultados no [relatório](docs/VALIDATION.md). Compilar no CI ou passar testes do núcleo não comprova esses cenários de campo.

A arte própria NavSkin e os sprites dos jogos têm origens distintas, registradas na [procedência dos assets](../docs/game-assets-provenance.md). Os copyrights de Rockstar e dos autores das fontes foram preservados. O projeto é independente, sem vínculo com a Rockstar. O manifesto de privacidade declara UserDefaults para preferências do aplicativo. Fontes, avisos e registros de origem acompanham os recursos preparados.

O clone atual já contém os recursos dos oito temas e os avatares 3D. Pacotes-fonte antigos podem conter somente os cinco temas da 0.10.0. Os arquivos de assinatura pessoal continuam excluídos.
