# NavSkin 0.11.0 — Vibing

Abra **⋯ → Vibing**. O mapa assume a perspectiva 3D, acompanha o GPS e gira conforme a direção do deslocamento. A velocidade adapta a aproximação; com rota ativa, curvas e retornos também aproximam a câmera. O modo mantém a tela ligada enquanto está visível.

- **Sem destino:** dirija livremente; nenhum cálculo de rota ou chave Geoapify é necessário. O mapa mundial continua precisando de conexão para novos tiles.
- **Com rota em prévia:** o modo inicia a navegação se o GPS está pronto. Com navegação já ativa, a sessão continua.
- **Calculando rota:** aguarde a conclusão ou limpe o destino para sair sem rota.
- **Sair:** toque no mapa para revelar os controles temporários. No Android, Sair e Recentralizar aparecem por cinco segundos; Voltar também sai. A rota ativa continua após sair do Vibing.
- **Gestos:** arrastar ou girar permite explorar o mapa. Toque e Recentralizar retomam o GPS. No iOS, o toque revela a saída e retoma o acompanhamento.

Cards, busca, velocidade e barras do sistema ficam ocultos durante o Vibing. A posição só é mostrada após uma leitura de GPS; a perda de sinal reduz a opacidade do veículo. Sem rota, o GPS para quando o aplicativo deixa de estar visível, conforme o ciclo de vida de localização existente. Uma navegação ativa mantém seu serviço de localização.

Os oito temas têm 3D opcional fora do Vibing. Exploração começa em 2D e navegação em 3D; as escolhas são salvas separadamente. Sair do Vibing recupera essas preferências. Prédios usam contornos reais; alturas ausentes são ilustrativas.

O pacote inclui Pip-Boy, Hollow Knight e Crossy Road, os redesenhos aprovados dos demais mapas e os ícones próprios do Pink World. O carrinho Hello Kitty aparece em **Configurações → Marcador do veículo → Temático** e é o padrão Pink quando ainda não há escolha de veículo gravada. Escolhas anteriores são preservadas.

APK: `android/artifacts/NavSkin-v0.11.0-debug.apk`, versão 0.11.0 / código 11. Instale sobre a versão debug anterior para preservar dados. As condições de distribuição/descoberta do Android Auto não mudaram; este modo é da interface do telefone. O projeto iOS foi atualizado, mas precisa de Mac/Xcode para compilar. CarPlay continua fora desta entrega.
