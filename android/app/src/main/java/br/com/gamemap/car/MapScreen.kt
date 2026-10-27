package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.graphics.drawable.IconCompat
import br.com.gamemap.R
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.preferences.PreferenceStore
import br.com.gamemap.preferences.PoiCategory
import br.com.gamemap.render.CarSurfaceRenderer
import br.com.gamemap.render.NativeMapRenderer

/** The host owns every control; the Surface contains the native map and attribution. */
class MapScreen(
    context: CarContext,
    private val preferences: PreferenceStore,
    private val maps: MapStyleRepository,
) : Screen(context) {
    var renderer: CarSurfaceRenderer? = null
    private var mapError: String? = null

    fun showStatus(message: String) {
        if (message.contains("falha", ignoreCase = true)) {
            mapError = "Não foi possível carregar o mapa local."
        } else if (message.contains("estilo local carregado")) {
            mapError = null
        }
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val state = preferences.snapshot()
        val limit = hostListLimit(carContext)
        val items = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Tema do mapa").addText(state.theme.label)
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(ThemeScreen(carContext, preferences)) }
                .build())
            .addItem(Row.Builder().setTitle("Categorias")
                .addText("${state.enabledCategories.size} de ${PoiCategory.entries.size} ativas")
                .setBrowsable(true)
                .setOnClickListener { screenManager.push(CategoryScreen(carContext, preferences, maps)) }
                .build())

        if (mapError != null) {
            items.addItem(Row.Builder().setTitle("Mapa indisponível").addText(mapError!!).build())
        } else if (state.enabledCategories.isEmpty()) {
            items.addItem(Row.Builder().setTitle("Nenhuma categoria ativa")
                .addText("Ative uma categoria para mostrar seus pontos de interesse.").build())
        } else {
            val camera = renderer?.cameraState ?: NativeMapRenderer.defaultCamera()
            val places = maps.nearestTo(camera, state.enabledCategories, (limit - 2).coerceAtLeast(0))
            places.forEach { place ->
                items.addItem(Row.Builder().setTitle(place.displayName).addText(place.category.label)
                    .setOnClickListener {
                        renderer?.focusOn(place.latitude, place.longitude)
                        invalidate()
                    }.build())
            }
            if (places.isEmpty()) items.addItem(Row.Builder().setTitle("Nenhum ponto disponível")
                .addText("Escolha outras categorias para explorar Goiânia.").build())
        }

        val content = ListTemplate.Builder().setSingleList(items.build())
            .setHeader(Header.Builder().setTitle("Goiânia · ${state.theme.label}")
                .setStartHeaderAction(Action.APP_ICON).build())
            .build()
        val mapActions = ActionStrip.Builder()
            .addAction(Action.PAN)
            .addAction(mapAction(R.drawable.ic_zoom_in) { renderer?.zoomBy(1.0) })
            .addAction(mapAction(R.drawable.ic_zoom_out) { renderer?.zoomBy(-1.0) })
            .addAction(mapAction(R.drawable.ic_reset) { renderer?.reset() })
            .build()
        val controller = MapController.Builder().setMapActionStrip(mapActions)
            .setPanModeListener { _ -> invalidate() }.build()
        return MapWithContentTemplate.Builder().setContentTemplate(content)
            .setMapController(controller).build()
    }

    private fun mapAction(icon: Int, click: () -> Unit): Action = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
        .setOnClickListener { click(); invalidate() }.build()
}
