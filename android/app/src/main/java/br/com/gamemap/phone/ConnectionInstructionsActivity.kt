package br.com.gamemap.phone

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import br.com.gamemap.GameMapApplication
import br.com.gamemap.R
import org.json.JSONObject

/** Local attribution and connection help, reachable from the floating info action. */
class ConnectionInstructionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val colors = PhonePalette.forTheme((application as GameMapApplication).preferences.snapshot().theme)
        window.statusBarColor = colors.surface
        window.navigationBarColor = colors.surface
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dip(22), dip(12), dip(22), dip(28))
            setBackgroundColor(colors.surface)
        }
        val back = PhoneAction(this, R.drawable.phone_close, "Voltar ao mapa", true).apply {
            bind(colors); setOnClickListener { finish() }
        }
        content.addView(back, LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.END })
        content.addView(phoneText("SEU CAMINHO, SEU ESTILO", 10f, true).apply { setTextColor(colors.accent) })
        content.addView(phoneText("Sobre o NavSkin", 28f, true).apply { setTextColor(colors.text) },
            LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(8); bottomMargin = dip(22) })

        fun paragraph(title: String, body: String) {
            content.addView(phoneText(title, 18f, true).apply { setTextColor(colors.text) },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(18); bottomMargin = dip(8) })
            content.addView(phoneText(body, 15f).apply {
                setTextColor(colors.muted); setLineSpacing(dip(4).toFloat(), 1f)
            }, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        paragraph("Um mapa para explorar", """
            Três estilos para explorar o mundo: GTA SA, Rosa Pop e RDR2. Mova e amplie o mapa com os dedos; use Temas e Filtros para escolher a aparência, a cobertura e os locais que quer ver.

            Mundo online carrega mapas OpenFreeMap / OpenStreetMap pela internet, sem uma chave de mapa. Goiânia offline conserva o recorte incluído no aparelho e funciona sem conexão dentro desse limite. Trocar a cobertura não encerra o GPS nem a viagem. Busca de endereços e cálculo de rotas usam Geoapify pela internet, com sua chave configurada no celular. O GPS preciso é ativado ao solicitar localização ou navegação. Não há orientação por voz.
        """.trimIndent())
        paragraph("Na tela do carro", "Conecte o telefone a uma central compatível com Android Auto e abra o NavSkin. A cobertura, os temas e as categorias de lugares podem ser escolhidos em Aparência na central. A central controla o movimento e o zoom do mapa e pode limitar as interações durante a condução.")
        val details = phoneText("""
            É necessário um host com Car App API 7 ou superior. O NavSkin usa a categoria Navegação e compartilha destino e viagem com o celular. Inicie o GPS no celular antes de conectar caso o Android impeça iniciar a localização apenas pela central.

            Para testar no DHU: habilite a depuração USB e o modo de desenvolvimento do Android Auto no telefone. Inicie o servidor da unidade principal no menu do Android Auto, conecte por USB e execute no computador:

            adb forward tcp:5277 tcp:5277
            desktop-head-unit.exe --adb=5277

            Em uma central real, apps da Car App Library precisam de origem confiável, como uma faixa de testes interna ou Compartilhamento Interno de Apps do Google Play. Instalar este APK por USB, sozinho, não garante que o app apareça na central; a opção de fontes desconhecidas do Android Auto não se aplica a esta categoria.

            A validação em emulador é distinta do teste em DHU ou veículo. O relatório que acompanha o APK registra os testes realizados.
        """.trimIndent(), 14f).apply {
            setTextColor(colors.text); setLineSpacing(dip(3).toFloat(), 1f)
            setPadding(dip(16), dip(16), dip(16), dip(16))
            background = rounded(colors.soft, 18)
            visibility = View.GONE
        }
        val connection = PhoneAction(this, R.drawable.phone_info, "Conexão e testes do Android Auto", true).apply {
            bind(colors); touchSurface(colors.soft, 16)
            setOnClickListener {
                details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                ViewCompat.setStateDescription(this, if (details.visibility == View.VISIBLE) "Expandido" else "Recolhido")
            }
        }
        content.addView(connection, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(14); bottomMargin = dip(10) })
        content.addView(details, LinearLayout.LayoutParams(MATCH, WRAP))
        paragraph("Sobre os dados", """
            No modo Mundo online, os mapas são fornecidos pelo OpenFreeMap a partir de dados OpenStreetMap. Em Goiânia, a camada de prédios Overture incluída no aplicativo complementa esse mapa. Fora do recorte, a densidade depende do provedor mundial. A cobertura e os pontos de interesse variam conforme os dados disponíveis em cada lugar.

            O modo Goiânia offline inclui vias, áreas e pontos de interesse OpenStreetMap, além de edificações Overture Maps Foundation com Google Open Buildings e Microsoft Global ML Building Footprints. Ele cobre somente o recorte incluído de Goiânia. Os locais podem estar incompletos ou desatualizados; contornos inferidos de imagens não equivalem a cadastro municipal.
        """.trimIndent())
        paragraph("GPS, rotas e privacidade", """
            A navegação usa um serviço com notificação para continuar com a tela bloqueada. O app salva favoritos, destinos recentes e o destino pendente, sem registrar histórico contínuo de posições. Depois de uma interrupção do processo, retome o destino e recalcule a rota com GPS atual.

            A chave Geoapify é criptografada com Android KeyStore neste aparelho. Busca e rotas enviam os dados necessários à Geoapify. As estimativas não incluem trânsito em tempo real. Créditos: Geoapify e © OpenStreetMap contributors. Consulte planos e termos em geoapify.com; configurar a chave não contrata um plano pago.

            O modo Mundo online envia pedidos de mapas à infraestrutura OpenFreeMap. Sem internet, os detalhes ainda não carregados podem ficar indisponíveis; selecione Goiânia offline para usar o recorte local. Indicadores SIMULAÇÃO identificam reprodução de desenvolvimento e não representam teste de direção real.
        """.trimIndent())
        paragraph("Créditos e licenças", "Mundo online: OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors. Rotas e busca: Geoapify. Goiânia offline: © OpenStreetMap contributors · Overture Maps Foundation · Google Open Buildings · Microsoft Global ML Building Footprints.")
        val creditHtml = runCatching {
            val data = JSONObject(assets.open("maps/credits/sources.json").bufferedReader().use { it.readText() })
            val sources = data.getJSONArray("sources")
            buildString {
                for (index in 0 until sources.length()) {
                    val source = sources.getJSONObject(index)
                    val name = Html.escapeHtml(source.getString("name"))
                    val license = Html.escapeHtml(source.getString("license"))
                    val url = source.optString("url")
                    append("<p>")
                    if (url.startsWith("https://")) append("<a href=\"" + Html.escapeHtml(url) + "\">" + name + "</a>") else append(name)
                    append("<br>" + license + "</p>")
                }
            }
        }.getOrElse {
            "<p>OpenStreetMap contributors e Overture Maps Foundation: ODbL 1.0.</p>" +
                "<p>Google Open Buildings: CC BY 4.0. Microsoft Global ML Building Footprints: ODbL 1.0.</p>" +
                "<p>Maki / Mapbox e somente adaptações originais NavSkin: CC0 1.0. Sprites e fontes dos jogos mantêm seus próprios copyrights e avisos.</p>" +
                "<p>Noto Sans Bold e Noto Serif Bold: SIL Open Font License 1.1.</p>"
        }
        content.addView(phoneText("", 14f).apply {
            text = Html.fromHtml("<p><a href=\"https://openfreemap.org/\">OpenFreeMap</a> · " +
                "<a href=\"https://www.openmaptiles.org/\">© OpenMapTiles</a> · " +
                "<a href=\"https://www.openstreetmap.org/copyright\">© OpenStreetMap contributors · ODbL</a></p>" +
                "<p><a href=\"https://www.geoapify.com/\">Geoapify · rotas e busca</a></p>" +
                "<p>Fontes do recorte offline de Goiânia e dos recursos visuais:</p>" + creditHtml.replace("GameMap", "NavSkin"),
                Html.FROM_HTML_MODE_LEGACY)
            setTextColor(colors.muted); setLinkTextColor(colors.accent)
            movementMethod = LinkMovementMethod.getInstance()
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(12) })
        content.addView(phoneText("Os temas combinam fontes e sprites dos jogos com adaptações visuais do NavSkin. GTA e Red Dead Redemption são marcas de seus respectivos titulares. O app não é afiliado à Rockstar Games. Links de fontes e licenças são referências.", 12f).apply {
            setTextColor(colors.muted); setLineSpacing(dip(3).toFloat(), 1f)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dip(18) })
        val scroll = ScrollView(this).apply { addView(content); setBackgroundColor(colors.surface) }
        setContentView(scroll)
        GameTypography.observe(scroll) { (application as GameMapApplication).preferences.snapshot().theme }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
