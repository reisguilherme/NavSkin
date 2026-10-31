# NavSkin Android 0.9.0 — validação em 29/10/2026

Entrega local: **0.9.0-debug / versionCode 9**, pacote `br.com.gamemap.goiania.dev`, minSdk 26/targetSdk 36.

## Resultado

- **187 testes JVM em 28 suítes**, zero falhas/erros. Incluem itinerários, resposta Geoapify multitrecho, falhas e cancelamentos de edição, consumo de paradas, retomada e controles com fonte ampliada.
- `lintDebug`, APK e AAB: **BUILD SUCCESSFUL**. Lint sem erros; 46 avisos não bloqueantes, incluindo APIs obsoletas e versões de dependências. [Log de build](logs/v0.9/final-build.txt).
- **14 testes instrumentados distintos passaram**, em Android API 36, AVD dedicado com GPU nativa. Cobrem UI, câmera adaptativa, troca de estilo, mapa mundial real em São Paulo, persistência/KeyStore, rota em simulação e camada de tráfego com fixtures.
- A primeira execução completa passou 13/14: o teste de corrida de câmera ainda esperava o antigo carrossel automático. Atualizamos somente a preparação do teste, preservando todas as verificações de câmera. Os dois testes com preparação alterada passaram na repetição. [Execução completa](logs/v0.9/native-full.txt) · [Repetição após correção](logs/v0.9/native-updated-startup.txt). Os XML das duas execuções estão nas pastas adjacentes.
- Capturas nativas inspecionadas: exploração, menu de quatro entradas, navegação, adicionar parada e paisagem. Círculos de velocidade/recentralização iguais; ETA sem velocidade/precisão; menu contextual e Parar/Adicionar parada legíveis. [Capturas](../screenshots/v0.9/README.md).
- Revisão independente dos fluxos de rota concluída sem bloqueador Android. Fontes/ícones autênticos existentes e recursos cartográficos mantidos.

## Artefatos

| Arquivo | Bytes | SHA-256 |
|---|---:|---|
| `NavSkin-v0.9.0-debug.apk` | 122869609 | `bbc9b239b02edbb7de5b5c3bdbbc092d5b2b01758c9c6e0c271a0bce2b5cc72b` |
| `NavSkin-v0.9.0-debug.aab` | 90712767 | `16c7a83a8e72b82a9f54382989ccfe389fdf92bcf5cdadf125a128f5caa0e5da` |

Assinatura APK verificada pelo `apksigner`, mesmo certificado debug das entregas anteriores: SHA-256 `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`. `aapt2` confirmou ID e versão. O APK pode atualizar a instalação anterior assinada com esse certificado; o AAB não se instala diretamente.

## Limites da validação

Os testes de itinerário usam respostas de API controladas; não foi feita consulta Geoapify/TomTom com chave pessoal nem direção em via real. A UI de navegação foi testada com replay identificado como **SIMULAÇÃO GPS**. As estimativas demonstradas não são uma viagem real. O mapa mundial foi consultado/renderizado de fato.

Android Auto conserva a integração existente; esta etapa não testou uma central/DHU nem alterou a restrição de descoberta por origem de instalação. iOS teve 120 testes Core e validação de fontes/recursos, mas ainda depende de Mac/Xcode. CarPlay não foi implementado.

[Uso da versão](../../docs/navigation-v0.9.md) · [Versão anterior](VALIDATION-v0.8.md).
