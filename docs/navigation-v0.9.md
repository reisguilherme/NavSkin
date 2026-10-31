# NavSkin 0.9 — interface Vidro e paradas

Esta versão aplica a interface aprovada no [protótipo interativo](../prototypes/navskin-glass/README.md) aos aplicativos nativos. O mapa continua com os recursos reais dos três temas; os valores de demonstração do protótipo não são usados na navegação real.

## Controles do mapa

- O botão **⋯** reúne **Temas**, **Filtros**, **Seus lugares** e **Configurações**. Durante a navegação, ele fica dentro do card da próxima manobra; ao explorar, flutua no topo.
- Velocidade e recentralização têm o mesmo diâmetro. A velocidade só é exibida quando existe posição atual com qualidade suficiente.
- O resumo da viagem mantém somente tempo restante, distância restante e chegada.
- Durante a viagem, **Parar** encerra a navegação. **Adicionar parada** abre a busca de uma parada intermediária. Ao encerrar, a busca **Para onde?** volta a aparecer.
- Configurações conserva acesso ao GPS, chave Geoapify, trânsito TomTom, favoritos, veículo, cobertura do mapa e créditos. As fontes e ícones da cartografia continuam temáticos; os controles usam tipografia legível conforme o protótipo aprovado.

## Paradas reais

As paradas passam a fazer parte da solicitação Geoapify entre sua posição e o destino final. São mantidas na ordem escolhida, sem substituir o destino nem otimizar silenciosamente a sequência. Adicionar ou remover uma parada exige internet, chave configurada e GPS atual/preciso.

A mudança de itinerário só é confirmada após o provedor devolver a nova rota. Em falha de conexão ou indisponibilidade, o app mantém a rota e as paradas confirmadas e informa que a alteração precisa ser tentada novamente. Encerrar ou trocar de destino invalida respostas atrasadas. Paradas já percorridas são removidas da lista pendente por progresso válido na rota, para não voltarem ao recalcular.

A retomada de viagem recalcula o destino com as paradas restantes; não reutiliza uma posição GPS antiga como origem atual. API de referência: [Geoapify Routing](https://apidocs.geoapify.com/docs/routing/), com waypoints ordenados e modo stopover.

## Aparência e desempenho

O Android usa superfícies nativas claras/translúcidas, bordas e sombras para o acabamento de vidro. Não captura o mapa a cada atualização GPS para simular desfoque. O iOS usa os materiais nativos do SwiftUI. As melhorias anteriores de câmera adaptativa, rotação e geometria de rota são preservadas.

## Limites

- Geoapify continua responsável por busca, rota e ETA. TomTom permanece uma camada opcional de fluxo/incidentes e não muda o ETA.
- Não foram adicionados radares, semáforos ao vivo, instruções por voz ou CarPlay nesta etapa.
- O modo Goiânia offline fornece cartografia local; busca/rotas/paradas continuam online.
- O projeto iOS precisa de build e teste no Mac com Xcode. O pacote de fontes não é um IPA assinado.
- O Android Auto usa o serviço já existente no APK; validação no DHU/central e instalação por origem confiável continuam etapas separadas.

Consulte os relatórios de validação da entrega para distinguir testes emulados, fixtures e verificações ainda pendentes em dispositivos reais.
