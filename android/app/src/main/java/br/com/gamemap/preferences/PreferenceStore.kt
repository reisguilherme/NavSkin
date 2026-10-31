package br.com.gamemap.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper

/** Shared by the Activity and CarAppService in the same process. No account or server. */
class PreferenceStore(context: Context) : AutoCloseable {
    private val storage = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = linkedSetOf<(MapPreferences) -> Unit>()
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == THEME || key == CATEGORIES || key == BASE_MAP_MODE || key == BROWSE_3D || key == NAVIGATION_3D) onMain {
            val state = snapshot()
            listeners.toList().forEach { it(state) }
        }
    }
    init {
        if (storage.getInt(CATEGORY_SCHEMA, 0) < 2) {
            val previous = storage.getStringSet(CATEGORIES, null)
            val edit = storage.edit().putInt(CATEGORY_SCHEMA, 2)
            // Preserve an explicit "all off"; enable newly introduced groups once
            // for an existing nonempty selection without changing earlier toggles.
            if (!previous.isNullOrEmpty()) edit.putStringSet(CATEGORIES,
                previous + setOf(PoiCategory.EDUCATION.id, PoiCategory.CULTURE.id, PoiCategory.RELIGION.id))
            check(edit.commit()) { "Não foi possível atualizar as categorias" }
        }
        storage.registerOnSharedPreferenceChangeListener(changed)
    }

    fun snapshot(): MapPreferences {
        val categories = storage.getStringSet(CATEGORIES, null)
        return MapPreferences(
            MapTheme.fromId(storage.getString(THEME, null)),
            categories?.mapNotNull { PoiCategory.fromId(it) }?.toSet() ?: PoiCategory.entries.toSet(),
            MapBaseMode.fromId(storage.getString(BASE_MAP_MODE, null)),
            browse3D = storage.getBoolean(BROWSE_3D, false),
            navigation3D = storage.getBoolean(NAVIGATION_3D, true),
        )
    }

    fun setTheme(theme: MapTheme) {
        check(storage.edit().putString(THEME, theme.assetName).commit()) { "Não foi possível salvar o tema" }
    }

    fun setBaseMapMode(mode: MapBaseMode) {
        check(storage.edit().putString(BASE_MAP_MODE, mode.id).commit()) { "Não foi possível salvar a cobertura do mapa" }
    }

    fun setMap3D(navigationActive: Boolean, enabled: Boolean) {
        val key = if (navigationActive) NAVIGATION_3D else BROWSE_3D
        check(storage.edit().putBoolean(key, enabled).commit()) { "Não foi possível salvar a dimensão do mapa" }
    }

    fun setCategoryEnabled(category: PoiCategory, enabled: Boolean) {
        val selected = snapshot().enabledCategories.toMutableSet()
        if (enabled) selected.add(category) else selected.remove(category)
        check(storage.edit().putStringSet(CATEGORIES, selected.map { it.id }.toSet()).commit()) {
            "Não foi possível salvar as categorias"
        }
    }

    fun addListener(listener: (MapPreferences) -> Unit): () -> Unit {
        onMain { listeners.add(listener); listener(snapshot()) }
        return { onMain { listeners.remove(listener) } }
    }

    override fun close() {
        storage.unregisterOnSharedPreferenceChangeListener(changed)
        onMain { listeners.clear() }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val FILE_NAME = "gamemap_preferences_v1"
        private const val THEME = "map_theme"
        private const val CATEGORIES = "poi_categories"
        private const val CATEGORY_SCHEMA = "poi_categories_schema"
        private const val BASE_MAP_MODE = "base_map_mode"
        private const val BROWSE_3D = "browse_3d"
        private const val NAVIGATION_3D = "navigation_3d"
    }
}
