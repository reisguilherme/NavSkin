# NavSkin iOS 0.12.0 — preparação e validação

Projeto Xcode configurado para **0.12.0 / build 12**, com os oito temas e os avatares de navegação/Vibing compartilhados. O manifesto indexa **2.127 recursos**, incluindo 770 arquivos do catálogo 3D, idênticos aos do Android.

- **148 testes Swift Core**, zero falhas, em Swift 6.4.1/Linux/WSL. Seis testes novos cobrem ângulo relativo, passagem pelo norte, inclinação, tamanho/âncora, contexto e catálogo inválido.
- Probe Foundation do projeto/recursos passou: 32 estilos, oito temas persistidos, referências de fontes, previews e projeto.
- Parser de todos os fontes Swift do app/testes Apple passou após a implementação da carga assíncrona.
- Recursos Android/iOS conferidos byte a byte; empacotamento também coberto pela suíte Node.

Leitura e descompressão dos PNGs 3D usam fila serial fora da UI. Há uma tarefa ativa e um pedido mais recente, cache de até 24 imagens/12 MiB, revalidação da geração do estilo e um slot de imagem no atlas. Trocas de tema, estilo e saída do modo não aplicam resultados obsoletos.

Logs em [logs/v0.12](logs/v0.12/). **Não houve compilação de UIKit/MapLibre no Xcode, teste em iPhone ou geração de IPA.** O parser e os testes Linux não substituem essa etapa. Siga o [README para o Mac](../README.md#para-quem-vai-compilar-no-mac).
