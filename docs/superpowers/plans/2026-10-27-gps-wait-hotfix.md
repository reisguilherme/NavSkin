# NavSkin 0.7.1 · espera pelo GPS

Relato: chave encontra o destino, seleção mostra Tentar novamente, depois calcula sozinha. A regressão reproduziu ERROR quando a prévia ainda aguardava o primeiro fix. O destino continuava pendente e era calculado quando o GPS chegava.

1. Separar WAITING_FOR_LOCATION/waitingForLocation de CALCULATING e de erro de provedor.
2. Manter origem precisa e atual (até 5 s), sem HTTP antecipado ou retry em voo. GPS pronto retoma uma única prévia; erro real não dispara tentativas automáticas por novos fixes.
3. Retomar a aquisição pendente ao voltar ao telefone após parada por inatividade; anexar cliente do carro não inicia serviço em segundo plano.
4. Telefone mostra progresso e ação específica se falta permissão/GPS. Tentar novamente fica para falhas reais, cuja mensagem aparece no card. Preservar ETA válido com só tempo, distância e chegada.
5. Cobrir origem fraca/antiga, serviço parado, retorno ao app, cancelamento, chave/quota/HTTP e ações de UI com regressões; executar a navegação e disposição de controles no emulador. Entregar APK e fonte iOS 0.7.1 com a mesma identidade de instalação.
