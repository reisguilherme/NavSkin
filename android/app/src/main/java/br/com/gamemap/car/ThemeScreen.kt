package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PreferenceStore

class ThemeScreen(context: CarContext, preferences: PreferenceStore) :
    PreferenceScreen(context, preferences) {
    override fun onGetTemplate(): Template {
        val current = preferences.snapshot().theme
        val themes = MapTheme.entries
        val items = ItemList.Builder()
            .setSelectedIndex(themes.indexOf(current))
            .setOnSelectedListener { index -> preferences.setTheme(themes[index]) }
        themes.forEach { theme -> items.addItem(Row.Builder().setTitle(theme.label).build()) }
        return ListTemplate.Builder().setSingleList(items.build())
            .setHeader(Header.Builder().setTitle("Tema do mapa")
                .setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
