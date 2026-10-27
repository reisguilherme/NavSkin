package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import br.com.gamemap.R
import br.com.gamemap.render.CarSurfaceRenderer

/** First integration gate: useful real geography rendered by Native on the host Surface. */
class SurfaceProbeScreen(context: CarContext) : Screen(context) {
    var renderer: CarSurfaceRenderer? = null
    private var status = "Aguardando superfície do Android Auto"
    private var panMode = false

    fun showStatus(message: String) {
        status = message
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val content = Pane.Builder()
            .addRow(Row.Builder().setTitle("Centro de Goiânia")
                .addText("Praça Cívica · dados reais e locais do OSM").build())
            .addRow(Row.Builder().setTitle("Superfície MapLibre Native")
                .addText(status).build())
            .addRow(Row.Builder().setTitle(if (panMode) "Modo de deslocamento ativo" else "Controles do host")
                .addText("Use mover, ampliar, reduzir ou voltar ao enquadramento inicial.").build())
            .build()
        val pane = PaneTemplate.Builder(content)
            .setHeader(Header.Builder().setTitle("NavSkin · prova nativa")
                .setStartHeaderAction(Action.APP_ICON).build())
            .build()
        val strip = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(mapAction(R.drawable.ic_zoom_in) { renderer?.zoomBy(1.0) })
            .addAction(mapAction(R.drawable.ic_zoom_out) { renderer?.zoomBy(-1.0) })
            .addAction(mapAction(R.drawable.ic_reset) { renderer?.reset() })
            .build()
        val controller = MapController.Builder().setMapActionStrip(strip)
            .setPanModeListener { active -> panMode = active; invalidate() }.build()
        return MapWithContentTemplate.Builder().setContentTemplate(pane)
            .setMapController(controller).build()
    }

    private fun mapAction(icon: Int, click: () -> Unit): Action = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
        .setOnClickListener(click).build()
}
