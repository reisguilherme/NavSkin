# NavSkin Android 0.12.0 — validação

Executada em 29/10/2026 no Windows, com SDK Android 36, JDK 17 e emulador AOSP API 36/OpenGL host.

## Resultado

- **212 testes JVM**, zero falhas/erros: seleção de ângulo/inclinação, âncora e contexto de ativação, além das regressões existentes.
- **93 testes Node**, zero falhas: inclui geometria, depth buffer, enquadramento, transparência, 768 vistas, orientação e recursos compartilhados.
- **10 cenários Android instrumentados**, zero falhas: `NativeNavigationAvatarTest` (2), `NativeNavigationTest` (5), `NativeVibingTest` (3).
- A suíte de avatares foi repetida com verificações visuais reforçadas: carregamento da geração exata do estilo, ruas, edifícios e rótulos renderizados, imagem sobre o GPS e capturas com mapa visível. **2/2 passaram nos oito temas**. As primeiras capturas isoladas não comprovavam a composição com o mapa; foram substituídas por essas capturas completas.
- **17 verificações de navegador**: galeria, giro manual/automático, vista de cima, celular sem overflow, oito temas no mapa, rotação, percurso e ausência de erros de página/recursos.
- `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug` concluídos. Lint: **0 erros, 48 avisos**.
- Revisão independente não encontrou bloqueadores no carregamento assíncrono, cache, troca de estilo/contexto e alinhamento.

## Artefato

`android/artifacts/NavSkin-v0.12.0-debug.apk`

- Aplicativo: `br.com.gamemap.goiania.dev`, código 12, versão `0.12.0-debug`.
- Tamanho: **137.199.930 bytes**.
- SHA-256: `2eb117c25e76462ab5ab2d7762dc0146f28fd7cf32da1ccddd39816af4cef5bc`.
- Certificado SHA-256: `958178e38a83735afeece617c3557a7a4dbd20b9a31d88e7d216b5255302c42e`, igual à 0.11.0.
- Todos os **2.119 assets** no APK são idênticos aos fontes preparados; 32 estilos e 768 imagens dos avatares. Nenhum arquivo ausente, extra ou divergente.

## Evidências e limites

Logs em [logs/v0.12](logs/v0.12/). Capturas nativas em [screenshots/v0.12/v012-navigation-avatars](../screenshots/v0.12/v012-navigation-avatars/) a partir do diretório Android. Galeria e capturas de navegador em `prototypes/navskin-map-lab/`.

Os testes usaram posições e rotas controladas para verificar apresentação e comportamento; não representam uma viagem em rua nem um ensaio novo em central Android Auto. O avatar é uma imagem multivista de malha 3D autorada, selecionada segundo a câmera, sem renderização de malha em tempo real no celular.
