# Capturas nativas da versão 0.4

Imagens capturadas no Android Emulator API 36 com MapLibre Native OpenGL e GPU do host. As telas de viagem mostram **SIMULAÇÃO**, com fixture de desenvolvimento, e não uma rota autenticada nem direção real.

- [Rosa Pop, orientação vertical](phone-rosa-simulation.png).
- [Rosa Pop, orientação horizontal](phone-landscape-simulation.png).
- [Posição pelo LocationManager no AVD](phone-emulator-location.png): coordenadas fornecidas por `adb emu geo fix`, sem rota autenticada ou antena física.

As capturas são produzidas pelo teste instrumentado `NativeNavigationTest`. O teste também verifica fontes/camadas nativas, persistência do marcador e da sessão após recriação, e independência dos favoritos em relação aos filtros de POI. O [relatório de validação](../../docs/VALIDATION-v0.4.md) identifica exatamente os testes executados e limitações.
