# NavSkin 0.7.1 · espera por GPS na seleção de destino

Validado em 27/10/2026. APK `0.7.1-debug`, código 7, mesma identidade de instalação e certificado anterior.

## Problema e correção

Selecionar um destino antes da primeira posição GPS publicava ERROR e o telefone mostrava **Tentar novamente**. A prévia continuava pendente internamente e era calculada automaticamente quando chegava um fix atual/preciso. Esse fluxo foi reproduzido em regressão antes da correção.

Agora a espera inicial tem estado próprio **WAITING_FOR_LOCATION**. O telefone mostra **Buscando sua localização…**, depois **Calculando rota…** durante HTTP e, com a prévia pronta, **Iniciar navegação**. Se falta permissão ou o GPS está desativado, oferece a ação específica. Falhas reais mostram a mensagem e a tentativa explícita; não se confunde espera normal com falha de chave, rede ou quota.

- A origem continua exigindo posição precisa e atual, com até 5 s. Não se usa posição antiga para acelerar o cálculo.
- A primeira posição válida inicia uma única consulta. Retry durante HTTP não rearma outra tentativa automática após erro.
- Cancelamento e troca de destino invalidam respostas antigas. Retornar ao celular após parada do GPS retoma a aquisição pendente; anexar o cliente do carro não inicia serviço em segundo plano.
- O menu continua permitindo cancelar durante a preparação. O ETA válido mantém só tempo, distância e chegada, sem precisão/velocidade duplicada.

## Verificação

- **141 testes JVM**, 22 suítes, zero falhas/erros. Incluem espera, GPS fraco/antigo, serviço parado, retorno ao app, permissão aproximada, HTTP 401/429/503, retry em voo e cinco contratos de apresentação da preparação/erro.
- **4 testes nativos** no emulador API36, zero falhas/skip: navegação simulada, troca de tema/veículo, câmera, rotação, Keystore/persistência e layout/menu do telefone.
- **62 testes Node** passaram. Nenhum dado ou estilo cartográfico mudou neste hotfix.
- APK/AAB gerados; lint com **zero erros e 35 avisos**. [Log final](logs/v0.7.1/release-validation.txt).
- **92 testes SwiftCore** e parser/resolução do projeto iOS passaram; [limites Apple](../../ios/docs/VALIDATION.md).

Provedor e localizações dos testes são fixtures/replay identificados. Não foi usada a chave do usuário, feita consulta Geoapify autenticada ou teste físico de GPS nesta validação. O iOS continua pendente de build/typecheck com Xcode e teste em iPhone.

## Artefatos

| Arquivo | Bytes | SHA256 |
|---|---:|---|
| `NavSkin-v0.7.1-debug.apk` | 122682644 | `B6C884BED46998AA608F5A0A0AE74E23944A117975D528885C505E8B30E2A1A9` |
| `NavSkin-v0.7.1-debug.aab` | 90650709 | `792270101F414B75615A41833D618764585022AAE5D1FA169D313A53297366A4` |

Certificado SHA256 `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. A atualização mantém favoritos, configurações e chave. Os recursos originais continuam no mesmo pacote de assets 0.7; o ZIP iOS 0.7.1 já inclui todos os recursos preparados.
