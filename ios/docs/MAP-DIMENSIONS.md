# Oito temas e mapa 2D/3D no iOS

O catálogo passa a incluir Pip-Boy (`pipboy`), Hollow Knight (`hollow`) e Crossy Road (`crossy`), além de GTA SA, Rosa Pop, RDR2, Super Mario World e Cyberpunk 2077. Os IDs existentes de favoritos e veículos continuam iguais ao trocar de tema.

O controle **Mapa 3D** fica no menu **⋯** e em **Configurações → Opções do mapa**. Os dois controles alteram a mesma preferência para o contexto atual:

- Ao explorar: o padrão é 2D. A escolha é salva em `browsing3D`.
- Durante uma viagem: o padrão é 3D. A escolha é salva em `navigation3D`.

Escolher 2D durante a navegação continua valendo nos próximos fixes de GPS e nas próximas viagens. Preferências antigas recebem os novos padrões sem perder veículo, visibilidade de salvos ou orientação. A prévia da rota permanece plana até **Iniciar**; a escolha da exploração continua guardada.

Em 3D, a câmera usa inclinação de 48° e os estilos mostram os edifícios `navskin-3d-*`. Em 2D, a inclinação é 0° e essas camadas ficam ocultas. A troca conserva centro, zoom e direção quando o mapa está sendo explorado manualmente. Acompanhamento, zoom adaptativo, suavização de direção, recentralização e proteção contra GPS impreciso continuam no mesmo fluxo.

Os edifícios usam a geometria disponível no estilo compartilhado. A disponibilidade e precisão das alturas dependem da fonte; o modo 3D não representa uma medição de cada edifício.

## Vibing

Abra **⋯ → Vibing** para dirigir com mapa e veículo em tela cheia. O modo acompanha o GPS com orientação de movimento, inclinação de 48° e zoom adaptado à velocidade, mesmo sem destino ou chave de rotas. Uma rota ativa continua; uma prévia válida é iniciada antes de ocultar os controles. Se faltar GPS preciso para iniciar essa rota, a interface mostra a explicação e permanece visível.

O botão **Sair do Vibing** aparece por quatro segundos ao entrar. Depois, um toque no mapa o revela novamente e retoma o acompanhamento do GPS após gestos. A ação também está disponível ao VoiceOver. Sair restaura os controles, a orientação e a preferência 2D/3D do contexto, sem encerrar a rota. O modo não é persistido ao fechar o aplicativo.

No modo sem rota, o GPS pausa quando o aplicativo vai para o fundo e volta ao retornar. Durante uma rota, permanece o ciclo de navegação existente. A tela fica acordada apenas enquanto Vibing está ativo e o aplicativo está em primeiro plano. Um GPS antigo não é extrapolado: a câmera espera um novo sinal e o veículo perde opacidade conforme a qualidade da posição.

No Pink World, uma instalação nova usa o carrinho temático quando nenhum veículo foi escolhido. A seleção explícita e os veículos já salvos em versões anteriores são preservados.

## Validação

O núcleo Swift tem testes de migração e persistência das duas preferências, escolha por contexto, inclinação na navegação, prévia plana, alteração sem novo fix e estabilidade com GPS fraco. O validador Foundation inspeciona os oito temas, 32 variantes de estilo, recursos dos catálogos e camadas de extrusão ocultas e controláveis.

O Windows/WSL permite executar o núcleo e analisar a sintaxe Swift. A compilação com UIKit, SwiftUI e MapLibre, assim como a renderização no iPhone, precisam de Xcode no Mac. Nenhum IPA é gerado por essa validação.

No simulador/aparelho, confira os oito temas dia/noite nas duas coberturas, alterne **Mapa 3D** durante exploração e navegação, arraste/rotacione o mapa e recentralize. Verifique também a prévia plana, reinício do aplicativo com navegação em 2D salva, retorno de GPS e troca de estilo durante uma viagem.
