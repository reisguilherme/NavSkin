# NavSkin 0.8.0 — validação em 29/10/2026

APK `0.8.0-debug`, código 8, pacote `br.com.gamemap.goiania.dev`. Mantém a identidade e o certificado debug anterior, permitindo atualização sobre o APK local sem apagar configurações e favoritos.

## Resultado

- **167 testes JVM**, 27 suítes: zero falhas, erros ou ignorados.
- **11 testes instrumentados**, Android 16/API 36 x86_64, GPU do host: zero falhas, erros ou ignorados.
- **62 testes Node** aprovados.
- APK e AAB compilados; lint: **zero erros e 43 avisos**.
- **107 testes Swift Core** aprovados; fontes/recursos/projeto iOS validados separadamente. [Limites Apple](../../ios/docs/VALIDATION.md).

[Log final do build e instrumentação](logs/v0.8/final-validation.txt). Os testes nativos cobrem animação/rumo cruzando o norte, zoom de retorno, ticks sem movimento, exploração manual, recálculo, padding durante animação, troca de estilo, Surface do carro, filtros, persistência, Keystore e layout horizontal sem sobrepor manobra/ETA.

O teste TomTom instala fontes e símbolos em MapLibre real, verifica pixels/features renderizados, desligamento e respostas atrasadas após troca de chave/estilo. Usa incidentes sintéticos e PNG local, sem HTTP externo ou chave real. Testes de parser, limite de área/tamanho/frequência, armazenamento e autenticação complementam essa verificação. Não houve teste autenticado da conta TomTom, de sua quota ou cobertura ao vivo.

## Fluidez observada

Na transição de uma posição recebida para outra, a cena de prova offline registrou **52 quadros**, intervalo mediano **17,43 ms** e percentil 95 **21,47 ms**. [Medição bruta](logs/v0.8/native-camera-frame-metrics.json). Trata-se de um trecho curto no emulador com GPU NVIDIA, não de benchmark comparativo nem garantia de FPS em celular, mapa urbano completo ou rede móvel.

A implementação anima câmera e marcador sem extrapolar posições, usa o menor arco para o rumo, limita atualização do marcador, mantém a cauda da rota e altera só a conexão móvel entre vértices. Não reinicia animações por ticks sem novo fix, nem reatribui layout do telefone quando as medidas não mudam.

## Iterações

A primeira execução ampliada de nove testes encontrou uma comparação de câmera antes de o cache do MapLibre refletir o pan; o teste foi corrigido para aguardar o movimento efetivo, mantendo as comparações exatas entre estilos. Houve também uma seleção de filtro não registrada por um toque do UI Automator; não voltou a ocorrer na execução final completa. Foi adicionado diagnóstico de tela/hierarquia em caso de recorrência, sem alterar o comportamento dos filtros para contornar o teste. [Registro inicial](logs/v0.8/native-validation.txt).

A revisão independente encontrou a animação interrompida por mudança de padding; o renderer agora retoma o destino aceito somente no modo de acompanhamento. A regressão nativa passou na execução final.

## Artefatos e limites

Hashes e tamanhos dos arquivos: [artifacts.json](logs/v0.8/artifacts.json). Certificado SHA256: `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`.

[Capturas](../screenshots/v0.8/README.md) usam SIMULAÇÃO identificada. Não houve trajeto físico, Android Auto/DHU real nem build Apple. TomTom é camada opcional; o ETA/recálculo continua Geoapify sem trânsito ao vivo. Radares e semáforos não foram adicionados. [Configuração e comportamento](../../docs/navigation-v0.8.md).
