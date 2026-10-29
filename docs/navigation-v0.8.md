# Navegação NavSkin 0.8

## Câmera e interface

Gire o mapa com dois dedos. Arrastar, girar ou alterar o zoom deixa a câmera em exploração manual; o círculo de recentralização retoma o acompanhamento. Uma troca de tema ou recálculo não deve desfazer a exploração. A orientação padrão segue o movimento; a preferência de norte fixo continua no menu do marcador do veículo.

Durante navegação, velocidades baixas aproximam a câmera e velocidades maiores mostram mais área à frente. A aproximação também considera distância e velocidade antes de curvas, rotatórias, saídas e retornos. Não depende de presumir que toda rua lenta é urbana. Rumo e zoom têm suavização; quando parado, a direção fica estável. A câmera só reage a novas observações ou comandos, sem reiniciar a animação nos ticks de idade do GPS.

A instrução superior destaca ícone, distância e rua. O painel inferior separa tempo, distância e chegada. Pesquisa, temas/filtros/menu, círculo de velocidade e recentralização permanecem nas posições definidas. A implementação evita reconstruir a hierarquia da interface, estilos e toda a geometria restante da rota em cada atualização.

## Ativar a TomTom

1. Crie ou obtenha uma API key no [portal TomTom](https://developer.tomtom.com/), habilitada para Traffic Orbis v2. Confirme as cotas e serviços da sua conta.
2. No NavSkin, abra **Menu → Trânsito ao vivo → TomTom**.
3. Informe a chave e toque em **Salvar chave e ativar trânsito**. A chave é separada da Geoapify e fica no armazenamento seguro do aparelho.
4. Aproxime o mapa para consultar a área. Abra o indicador TomTom para conferir o estado da consulta, a última atualização e a legenda. Desative a camada ou remova a chave para interromper seu uso.

Fluxo usa tiles transparentes de trânsito. Incidentes incluem acidentes, congestionamentos, interdições e obras recebidos do provedor. As consultas são limitadas em frequência e área; a camada é suspensa quando a tela deixa de estar ativa. Falhas de chave, quota, rede ou resposta não impedem o mapa e a rota Geoapify de funcionar.

**A camada de trânsito não altera o ETA nem escolhe automaticamente um desvio de congestionamento.** Rotas, manobras e recálculo continuam na Geoapify. O atraso de incidentes próximos não é somado ao tempo da viagem, pois eles podem afetar outra via ou sentido. Uma resposta sem incidentes não significa trânsito livre.

O provedor pode ter atraso, falhas ou cobertura parcial. A consulta revela a área visível do mapa à TomTom. Nenhuma conta, assinatura paga ou chave compartilhada foi criada pelo projeto. [Pesquisa, contratos das APIs e limites consultados](traffic-provider-research.md).

## Radares e semáforos

Esta versão não inclui radares ou posições de semáforos. OpenStreetMap tem cadastros desses elementos, mas é preciso preparar uma fonte com cobertura e atualização adequadas antes de apresentá-los no app. Não foi identificada uma API geral de fases ao vivo de semáforos no Brasil; um cadastro de localização não informa a cor atual do sinal.

## Limites da entrega

A validação local com fixtures e emulador não substitui um trajeto físico, nem valida a chave/cobertura TomTom do usuário. O projeto iOS é preparado no Windows para build no Mac; testes do núcleo Swift e parsing dos fontes não são compilação de SwiftUI/MapLibre com o SDK Apple. O APK local também continua sujeito ao requisito de instalação confiável para aparecer em uma central Android Auto real.
