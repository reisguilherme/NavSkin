package br.com.gamemap.car

import androidx.car.app.CarContext
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PreferenceStore

class ThemeScreen(context: CarContext, preferences: PreferenceStore) :
    PreferenceScreen(context, preferences) {
    private var page = 0
    private var displayedTheme: MapTheme? = null

    override fun onGetTemplate(): Template {
        val current = preferences.snapshot().theme
        val themes = MapTheme.entries
        val limit = hostListLimit(carContext)
        val pages = (themes.size + limit - 1) / limit
        // Follow a new phone selection, while keeping manual page browsing intact.
        if (displayedTheme != current) page = themes.indexOf(current) / limit
        displayedTheme = current
        page = page.coerceIn(0, pages - 1)
        val choices = themes.drop(page * limit).take(limit)
        val selected = choices.indexOf(current)
        val items = ItemList.Builder()
        // Radio lists require an actual selected row; another page stays unselected.
        if (selected >= 0) items.setSelectedIndex(selected)
            .setOnSelectedListener { index -> preferences.setTheme(choices[index]) }
        choices.forEach { theme ->
            val row = Row.Builder().setTitle(theme.label)
            if (selected < 0) row.setOnClickListener { preferences.setTheme(theme) }
            items.addItem(row.build())
        }
        return ListTemplate.Builder().setSingleList(items.build())
            .setHeader(pagedHeader("Tema do mapa", page, pages) { page = it; invalidate() })
            .build()
    }
}
