# NavSkin 0.11 — Vibing e catálogo completo

Pedido autorizado: implementar o catálogo aprovado no aplicativo e entregar novo APK, com modo Vibing em 3D para dirigir com ou sem rota.

## Comportamento

- Entrada no menu de três pontos. Vibing mostra apenas mapa e marcador do veículo.
- Uma rota válida em prévia inicia sua navegação; uma viagem ativa continua. Sem rota, o modo acompanha GPS e direção sem criar destino ou instruções fictícios.
- Perspectiva 3D durante o modo, preservando preferências de exploração e navegação para a saída.
- Toque no mapa revela temporariamente a saída; Voltar no Android também encerra apenas Vibing. O usuário continua podendo explorar o mapa por gestos.
- Os oito temas aprovados, redesenhos e ícones Pink World entram no pacote; Minecraft e GTA V continuam fora do catálogo.

## Execução e verificação

1. Implementar estado, câmera, interface e ciclo de vida no Android; manter paridade dos fontes iOS.
2. Promover arte aprovada dos painéis ao pipeline reproduzível de recursos nativos.
3. Validar estado/câmera, recursos compartilhados, fontes iOS e renderização nativa no emulador disponível.
4. Compilar APK 0.11.0 / versionCode 11, verificar assinatura, conteúdo e metadados, registrar limites dos testes.

APK usa o mesmo applicationId e certificado de desenvolvimento para atualizar instalações anteriores sem apagar configurações.

## Concluído

Android e fontes iOS implementados. APK 0.11.0 gerado e assinatura verificada; 1.349 recursos conferidos contra o pacote. Validação: 206 testes JVM, 86 Node, 142 Swift Core e 16 cenários nativos distintos aprovados após as correções documentadas em [VALIDATION-v0.11](../../../android/docs/VALIDATION-v0.11.md). Testes de rua, Android Auto/DHU e compilação Apple não foram executados nesta etapa.
