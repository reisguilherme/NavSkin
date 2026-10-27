# NavSkin Android 0.5.0 · 27/10/2026

Nome do aplicativo e projeto: **NavSkin**. APK confirmado por `aapt2`: versão `0.5.0-debug`, código 4, Android 8+ (API26), target36. O identificador `br.com.gamemap.goiania.dev` e o certificado debug anterior foram mantidos para permitir atualização com dados existentes. Certificado SHA256: `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`.

## Verificado

- Build de APK, AAB e APK de instrumentação aprovado. **119 testes JVM**, zero falhas, em 18 suítes. Lint: **zero erros e 32 avisos**. [Log de build/lint](logs/v0.5/build-final.txt).
- **9 testes instrumentados nativos aprovados**, zero falhas/skip, no AVD AOSP API36, aceleração WHPX e GPU host. [Log final](logs/v0.5/native-instrumentation-final.txt).
- Os oito cenários anteriores verificam renderização/Surface/câmera, permissões opcionais, temas dia/noite, persistência, KeyStore e reprodução de navegação identificada como SIMULAÇÃO.
- O novo cenário consulta OpenFreeMap por HTTPS e renderiza **São Paulo nos três temas**, com prédios, rótulos, sprites e POIs reais do serviço. Confere os seis filtros e troca mundial → Goiânia offline → mundial, conservando câmera e salvo independente. O teste exige rede e falha sem resposta real; não usa tiles de fixture. [Capturas nativas](../screenshots/v0.5/README.md).
- **48 testes Node aprovados**, incluindo estilos mundiais/offline, fontes, sprites, dados e recursos compartilhados. Build web aprovado; permanece o aviso de tamanho do bundle MapLibre.
- APK instalado com sucesso no emulador. Nome, versão e certificado conferidos no arquivo final.

A primeira execução nativa encontrou duas expectativas antigas de acessibilidade e uma consulta ao frame anterior imediatamente após carregar o estilo. As expectativas agora incluem NavSkin/cobertura; o teste mundial aguarda frame completo e idle do estilo correto, mantendo as verificações finais de geometria, pixels e câmera. A execução final acima é a evidência de aprovação. O log da primeira tentativa permanece identificado como histórico em `logs/v0.5/native-instrumentation.txt`.

## Entregas locais

| Arquivo | Bytes | SHA256 |
|---|---:|---|
| `NavSkin-v0.5.0-debug.apk` | 121277894 | `2E49C527B9C750896D23D29878E38C53DA5EB42A4B4E583D5AAD2E936D3F53CD` |
| `NavSkin-v0.5.0-debug.aab` | 89242927 | `8D2D6C662CEFACECBADF8B377CA20624C2155CEF17AD887654EBF548F08BF820` |

Os binários ficam em `android/artifacts` na entrega local e são ignorados pelo Git. [Manifesto calculado dos artefatos](logs/v0.5/artifacts.json). AAB debug não é publicação no Google Play.

## Cobertura e limites

Mundo online é o padrão, com tiles/fontes OpenFreeMap e dados OpenMapTiles/OSM. Goiânia offline preserva o recorte incluído; trocar cobertura não encerra GPS/rota. Fora do recorte, a ação explícita **Ver região offline** enquadra Goiânia. A opção mundial não baixa países para uso offline. [Fontes, licenças e amostras São Paulo/Tóquio](../../docs/world-maps.md).

Busca, cálculo e recálculo Geoapify exigem a chave do usuário e conexão. Nenhuma consulta autenticada com chave pessoal foi validada. O ETA não usa trânsito ao vivo. A suíte não comprova condução real, DHU/central Android Auto ou funcionamento físico do GPS na v0.5; esses testes permanecem pendentes. Renderização Android não valida a interface iOS.

Para reproduzir, siga [README Android](../README.md) e [navegação](NAVIGATION.md). O teste `NativeWorldMapTest` exige internet no emulador; os demais cenários selecionam explicitamente o mapa offline para serem determinísticos.
