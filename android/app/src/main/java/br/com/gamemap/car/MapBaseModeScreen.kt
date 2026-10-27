package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.PreferenceStore

/** Coverage is a host list choice, shared with the phone's appearance menu. */
class MapBaseModeScreen(context: CarContext, preferences: PreferenceStore,
    private val outsideOfflineRegion: () -> Boolean = { false },
    private val showOfflineRegion: (() -> Unit)? = null,
) :
    PreferenceScreen(context, preferences) {
    override fun onGetTemplate(): Template {
        val modes = MapBaseMode.entries
        val items = ItemList.Builder()
            .setSelectedIndex(modes.indexOf(preferences.snapshot().baseMapMode))
            .setOnSelectedListener { index -> preferences.setBaseMapMode(modes[index]) }
        modes.forEach { mode ->
            items.addItem(Row.Builder().setTitle(mode.label).addText(if (mode == MapBaseMode.WORLD)
                "Requer internet · OpenFreeMap e OpenStreetMap."
                else "Sem internet · somente o recorte incluído de Goiânia.").build())
        }
        val header = Header.Builder().setTitle("Cobertura do mapa").setStartHeaderAction(Action.BACK)
        if (preferences.snapshot().baseMapMode == MapBaseMode.GOIANIA_OFFLINE &&
            outsideOfflineRegion() && showOfflineRegion != null) {
            header.addEndHeaderAction(Action.Builder().setTitle("Ver região offline").setOnClickListener {
                showOfflineRegion.invoke(); screenManager.popToRoot()
            }.build())
        }
        return ListTemplate.Builder().setSingleList(items.build()).setHeader(header.build()).build()
    }
}
