# Validação NavSkin iOS 0.7.1 · 27/10/2026

A correção separa espera pelo primeiro GPS de falha de rota. O estado waitingForLocation só dispara HTTP quando a origem é atual e precisa. A interface mostra espera/progresso; ETA válido mantém apenas tempo, distância e chegada.

- **92 testes SwiftCore passaram**, incluindo origem fraca/antiga, aquisição inicial, cancelamento, retorno ao app, chave ausente/alterada, 401/429/503 e retry concorrente. São fixtures controladas, sem chave pessoal ou consulta real Geoapify.
- **62 testes Node passaram** para recursos e contratos de empacotamento compartilhados.
- Foundation ProjectProbe e parser Swift passaram com versão0.7.1, recursos, referências Xcode, aplicativo e testes Apple.
- A revisão independente conferiu os guards de requests, epoch e reativação da fonte no foreground.

Não houve build/typecheck com SDK Apple, simulador iOS, IPA ou iPhone. O projeto continua pronto para o [Mac do amigo](../README.md#para-quem-vai-compilar-no-mac). Os resultados de cartografia/UI0.7 e seus limites estão no [relatório anterior](VALIDATION-v0.7.md).

O pacote de fontes/ícones é o mesmo da0.7. A atualização não apaga preferências, salvos ou a chave guardada no Keychain. Hardware GPS e integração de serviços ainda precisam de teste em iPhone; não há voz, trânsito ao vivo ou CarPlay.
