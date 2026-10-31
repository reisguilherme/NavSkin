# NavSkin 0.12.0 — marcadores de navegação 3D

Os marcadores entram automaticamente ao iniciar uma viagem ou abrir **⋯ → Vibing**, com ou sem rota. Ao voltar à exploração, reaparece o ícone escolhido nas opções. Não é preciso mudar uma configuração para ativá-los.

| Mapa | Marcador |
|---|---|
| GTA SA | CJ na BMX vermelha, regata branca e jeans |
| Pink World | Carrinho Hello Kitty branco/rosa, capô com rosto, orelhas e laço |
| Crossy Road | Galinha voxel branca, crista vermelha e bico amarelo |
| RDR2 | Cavalo castanho com sela, manta, rédeas e alforjes |
| Super Mario World | Yoshi verde, sela vermelha e sapatos laranja |
| Cyberpunk | Moto vermelha inspirada na Kusanagi |
| Pip-Boy | Vault Boy com macacão azul/amarelo e Pip-Boy |
| Hollow Knight | Cavaleiro com máscara, chifres, capa e ferrão |

O ícone muda de perspectiva quando o veículo ou a câmera giram. Na câmera que acompanha o rumo, vemos suas costas; ao girar o mapa, aparecem as laterais e a frente. Se a navegação estiver configurada em 2D, usa-se a vista de cima. O ponto de contato fica na coordenada do GPS.

## Instalar e analisar

- APK: `android/artifacts/NavSkin-v0.12.0-debug.apk`. Mesmo identificador e assinatura de desenvolvimento usados na 0.11.0, permitindo atualização sem desinstalar.
- No computador, abra `Abrir-paineis-NavSkin.cmd` e acesse a [Garagem 3D](http://127.0.0.1:4176/markers-3d.html). Os HTMLs ficam em `prototypes/navskin-map-lab/`.
- O painel [Navegação 3D](http://127.0.0.1:4176/navegacao.html) usa as mesmas imagens do app sobre o recorte de Goiânia, com trajeto demonstrativo.
- Modelos editáveis OBJ/MTL/JSON: `scripts/navigation-3d/models/`. Código de construção e renderização: `scripts/navigation-3d/`.

## Implementação

São malhas próprias inspiradas nos personagens e veículos. Cada modelo foi renderizado com profundidade, iluminação e antialiasing em 32 direções e três inclinações: 768 imagens transparentes de 192×192 pixels, exibidas numa caixa de 72 pontos. A base fica em 50% da largura e 70% da altura. Os modelos não foram extraídos dos jogos.

O app seleciona a imagem correspondente ao rumo relativo à câmera; não executa um renderizador de malhas 3D em tempo real. Isso mantém volume e perspectiva com custo baixo. Android e iOS fazem a leitura/descompressão fora da UI, mantêm cache de até 24 imagens e reutilizam um único identificador no atlas do mapa. Um arquivo ausente usa o ícone existente como fallback.

Regenerar e copiar para iOS: `npm run assets:avatars`. Testar o pacote: `node --test tests/navigation-3d-assets.test.mjs tests/navigation-avatar-browser.test.mjs`.

Os modelos são apenas apresentação: não alteram o modo de rota automotiva da Geoapify. Selecionar BMX, Yoshi ou cavalo não muda o cálculo para bicicleta ou outro transporte. O projeto iOS está preparado, mas ainda requer compilação e teste no Mac. Não houve nova validação em Android Auto/CarPlay nesta entrega.
