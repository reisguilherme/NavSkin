package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.Toggle
import br.com.gamemap.data.MapStyleRepository
import br.com.gamemap.preferences.PoiCategory
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.PreferenceStore

class CategoryScreen(
    context: CarContext,
    preferences: PreferenceStore,
    private val maps: MapStyleRepository,
) : PreferenceScreen(context, preferences) {
    private var page = 0

    override fun onGetTemplate(): Template {
        val state = preferences.snapshot()
        val counts = maps.categoryCounts()
        val pageSize = hostListLimit(carContext)
        val pageCount = (PoiCategory.entries.size + pageSize - 1) / pageSize
        page = page.coerceIn(0, pageCount - 1)
        val items = ItemList.Builder()
        PoiCategory.entries.drop(page * pageSize).take(pageSize).forEach { category ->
            items.addItem(Row.Builder().setTitle(category.label)
                .addText(if (state.baseMapMode == MapBaseMode.WORLD) "Locais no mapa mundial · dados OpenStreetMap"
                    else "${counts[category] ?: 0} pontos no recorte offline")
                .setToggle(Toggle.Builder { enabled ->
                    preferences.setCategoryEnabled(category, enabled)
                }.setChecked(category in state.enabledCategories).build())
                .build())
        }
        val header = Header.Builder()
            .setTitle(if (pageCount == 1) "Categorias" else "Categorias · ${page + 1}/$pageCount")
            .setStartHeaderAction(Action.BACK)
        if (pageCount > 1) {
            if (page > 0) header.addEndHeaderAction(Action.Builder().setTitle("Anteriores")
                .setOnClickListener { page--; invalidate() }.build())
            if (page + 1 < pageCount) header.addEndHeaderAction(Action.Builder().setTitle("Próximas")
                .setOnClickListener { page++; invalidate() }.build())
        }
        return ListTemplate.Builder().setSingleList(items.build()).setHeader(header.build()).build()
    }
}
