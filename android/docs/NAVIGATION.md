# NavSkin — configurar e testar a navegação

Versão do código **0.5.0**, `versionCode 4`. Instale o APK por cima da v0.4, com a mesma assinatura, para manter favoritos, destinos, temas e filtros. O pacote de desenvolvimento continua intencionalmente `br.com.gamemap.goiania.dev`; o nome exibido é **NavSkin**. Os três estilos, os dados de Goiânia offline e os catálogos acompanham o APK. O padrão de cobertura é **Mundo online**; a escolha entre os dois modos fica salva no aparelho e é compartilhada com o Android Auto.

## Primeira utilização

1. Abra o aplicativo, escolha **Mundo · online** ou **Goiânia · offline**, escolha um dos temas GTA SA, Rosa Pop ou RDR2 e toque em **Explorar mapa**. Mundo é a opção inicial e requer internet, sem chave para carregar o mapa.
2. Toque em **Para onde? → Configurar chave Geoapify**. Insira a chave da sua conta e salve. A chave não acompanha o projeto.
3. Ative **GPS** e permita **localização precisa durante o uso**. Se o aparelho oferecer apenas localização aproximada, altere para precisa para navegar. A notificação permite acompanhar e encerrar o serviço; no Android 13+, sua permissão pode ser concedida pelo painel de destinos.
4. Pesquise um endereço ou estabelecimento e toque em um resultado. Outra opção é manter pressionado um ponto do mapa ou abrir **Seus lugares**. Aguarde uma posição atual e o cálculo da rota.
5. Confira distância e duração na prévia. Toque em **Iniciar navegação**. A viagem mostra próxima manobra, via, distância restante, tempo, chegada estimada e velocidade em km/h. O aplicativo não fala instruções.

Durante a viagem, **Opções da viagem** abre visão geral, recálculo, salvamento do destino e encerramento. Arrastar ou ampliar o mapa libera a câmera; **Recentralizar** volta a acompanhar. **N ↑ / Rumo ↑** alterna norte e direção do movimento. Cada tela mantém sua própria câmera, mesmo com uma sessão compartilhada.

Em **Temas** ou **Filtros**, **Cobertura do mapa** alterna os modos sem encerrar o GPS, cancelar a rota ou apagar favoritos. Também é acessível em **Para onde? → Cobertura do mapa**. Os três temas e suas variantes noturnas, as seis categorias e os marcadores funcionam nos dois modos. Os filtros mudam apenas os ícones dos pontos de interesse; os dados cartográficos e a rota continuam visíveis. As prévias do carrossel usam capturas reais do recorte offline para mostrar as paletas.

ETA usa os tempos dos segmentos da rota. Não inclui trânsito ao vivo nem limites de velocidade. Com sinal ruim, posição antiga ou rota abandonada, as instruções e o ETA deixam de ser apresentados como válidos. O marcador antigo fica atenuado. Um desvio precisa de evidência consistente de GPS antes do recálculo; falhas e limite de consultas têm mensagem e tentativa explícita.

## Chave e serviço externo

Crie sua conta/chave no [Geoapify](https://myprojects.geoapify.com/). A chave é criptografada com AES-GCM e Android KeyStore neste aparelho; não é mostrada novamente, incluída no código ou enviada a um backend NavSkin. **Remover chave deste aparelho** apaga a configuração. Desinstalar ou limpar dados exige reconfigurar.

Busca e cálculo/recálculo usam HTTPS e enviam à Geoapify o texto e as coordenadas necessários à consulta. A posição contínua fica no aparelho. Mundo online envia pedidos de tiles à infraestrutura [OpenFreeMap](https://openfreemap.org/), com dados [OpenStreetMap](https://www.openstreetmap.org/copyright). Nenhum dos dois mapas, a edição de favoritos ou os ícones exige chave Geoapify. Sem ela, solicitar busca ou rota mostra a configuração pendente.

Na consulta de 27/10/2026, o plano gratuito oferece **3.000 créditos/dia compartilhados entre APIs**, até 5 requisições/s, com atribuição obrigatória. Uma busca pode consultar mais de um endpoint. Verifique quota, preços e termos na sua conta; o aplicativo não cria assinatura paga. [Preços](https://www.geoapify.com/pricing/), [termos](https://www.geoapify.com/terms-and-conditions/), [decisão e contrato HTTP](../../docs/navigation-provider-decision.md).

Nenhuma chave pessoal foi disponibilizada ao agente. O adaptador foi validado com respostas controladas de testes; busca/rota autenticadas na conta do usuário permanecem por validar.

## Salvos, casa, trabalho e marcadores

- Um ponto mantido pressionado no mapa ou um destino selecionado oferece **Salvar local**.
- **Seus lugares** permite abrir, editar nome/endereço/categoria/coordenadas/ícone, excluir e definir/remover Casa e Trabalho. Alterações preservam o identificador e a data de criação.
- **Mostrar/Ocultar locais salvos** controla uma camada independente dos seis filtros de POI. Desligar farmácias ou parques não apaga favoritos.
- Há 12 ícones de local e seis marcadores de veículo por tema, com prévias dos arquivos efetivamente usados. O mesmo ID semântico muda de arte ao trocar GTA SA, Rosa Pop ou RDR2. Uma opção desconhecida recebe uma imagem padrão sem perder o ID salvo.
- **Marcador do veículo e orientação** escolhe o veículo. Moto e carroça são aparências; o cálculo continua no perfil automóvel.

São mantidos até 20 destinos recentes, favoritos, atalhos e um destino pendente. Não se grava trajetória contínua nem as instruções antigas da rota. Ao reabrir após encerramento do processo, **Retomar e recalcular** exige posição atual e novo cálculo. Encerrar/cancelar a viagem limpa a retomada.

## Android Auto

O serviço declara a categoria **NAVIGATION**. `NavigationTemplate`, `NavigationManager` e `Trip` fornecem ao host manobras e estimativas estruturadas. O mapa próprio usa a Surface; só um indicador passivo de velocidade/simulação é desenhado além da cartografia. Destinos, cobertura, temas, filtros, marcador e ações usam templates e controles do host. Em **Aparência → Cobertura do mapa**, uma lista de seleção do host alterna Mundo online e Goiânia offline. **Aparência** também contém os três temas e os seis filtros.

Configure a chave no telefone. Se o Android bloquear a ativação de localização a partir da central, abra o telefone quando estacionado e ative o GPS; o app mostra essa necessidade. Não solicita localização permanente em segundo plano. Uma navegação já iniciada usa um serviço de localização em primeiro plano e não depende da Activity permanecer visível. Desconectar o carro libera a Surface e os metadados daquele host, sem encerrar a sessão no telefone. **Encerrar** em qualquer tela encerra a mesma viagem.

O AVD usado nesta entrega não contém Android Auto; Surface nativa e testes de modelos/lifecycle não representam teste no host. Para DHU/central, siga [TESTING.md](TESTING.md) e [a documentação de plataforma](../../docs/android-navigation-platform.md). Distribuição para central real depende de origem confiável e validação da categoria navegação.

## Cobertura e falhas

**Mundo online**, padrão, carrega tiles vetoriais mundiais OpenFreeMap / OpenStreetMap por HTTPS. A qualidade, os detalhes e os pontos de interesse dependem dos dados de cada local. Sem rede, detalhes ainda não carregados podem ficar indisponíveis. O telefone mostra uma mensagem tocável para abrir a cobertura e escolher Goiânia offline; no carro, **Mapa sem rede → Cobertura do mapa** oferece a mesma escolha. A perda de rede não encerra GPS ou viagem e não troca automaticamente o mapa.

**Goiânia offline** cobre somente longitude −49,325 a −49,205 e latitude −16,755 a −16,640. Busca e rotas podem retornar locais fora do recorte; a prévia informa essa limitação apenas quando esse modo está selecionado. Nesses trechos não existem detalhes cartográficos locais. Esse limite não se aplica ao mapa mundial. O modo offline conserva os dados OpenStreetMap e as edificações Overture/Google/Microsoft incluídos no APK.

Busca, cálculo e recálculo de rotas precisam de internet e chave Geoapify nos dois modos. Esta versão não oferece cálculo offline de rotas nem download de regiões mundiais para uso offline.

Chave ausente/inválida, sem conexão, quota esgotada, nenhum resultado/rota, localização desativada e sinal perdido têm estados explícitos. Cancelamento ou troca de destino invalida requisições antigas; uma resposta atrasada não reativa viagem encerrada. Se o arquivo de favoritos estiver corrompido, o original é preservado e alterações ficam bloqueadas com mensagem, evitando substituir os dados por uma lista vazia.

## Reprodução de desenvolvimento

No APK debug, **Para onde? → Desenvolvimento · simulação GPS** reproduz um trajeto original fixo, sem consulta HTTP. **SIMULAÇÃO** aparece no telefone, no indicador do carro e nos metadados. Parar a reprodução limpa suas posições antes de voltar a usar GPS real. Ela não exige permissão de localização e não substitui teste de rua.

Os cenários instrumentados históricos de cartografia, câmera e navegação selecionam explicitamente Goiânia offline e restauram a preferência ao terminar; assim, a prova de GPS e renderização local independe de conexão ao provedor mundial. Os relatórios v0.4 e anteriores são históricos e não comprovam a execução dos cenários da v0.5.

O modo de teste `AUTO_DRIVE` do host, disponível no debug, reproduz uma rota já selecionada. O release não habilita essas reproduções pelo app. A validação de distribuição e requisitos de simulação do host deve ser feita antes de publicar.
