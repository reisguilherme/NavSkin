package br.com.gamemap.navigation.storage

import android.content.Context
import br.com.gamemap.preferences.MapTheme
import org.json.JSONArray
import org.json.JSONObject

data class CatalogAsset(val image: String, val preview: String, val svg: String)
data class CatalogIcon(val id: String, val label: String, val themeAssets: Map<String, CatalogAsset>)

/** Semantic choices resolve into the current theme without rewriting persisted preferences. */
class IconCatalogRepository(context: Context) {
    private val source = JSONObject(context.applicationContext.assets.open("navigation-icons/catalog.json")
        .bufferedReader(Charsets.UTF_8).use { it.readText() })
    private val saved = parseIcons(source.getJSONArray("saved"))
    private val vehicles = parseIcons(source.getJSONArray("vehicles"))
    private val fallbackSaved = source.getString("fallbackSaved")
    private val fallbackVehicle = source.getString("fallbackVehicle")

    init {
        require(source.getInt("schemaVersion") == 1) { "Catálogo de ícones incompatível" }
        require(saved.any { it.id == fallbackSaved } && vehicles.any { it.id == fallbackVehicle }) {
            "Catálogo de ícones sem marcador padrão"
        }
    }

    fun savedIcons(): List<CatalogIcon> = saved.toList()
    fun vehicleIcons(): List<CatalogIcon> = vehicles.toList()
    fun savedIcon(id: String, theme: MapTheme): CatalogAsset = resolve(saved, id, fallbackSaved, theme)
    fun vehicleIcon(id: String, theme: MapTheme): CatalogAsset = resolve(vehicles, id, fallbackVehicle, theme)

    private fun resolve(icons: List<CatalogIcon>, id: String, fallback: String, theme: MapTheme): CatalogAsset =
        icons.firstOrNull { it.id == id }?.themeAssets?.get(theme.assetName)
            ?: icons.first { it.id == fallback }.themeAssets[theme.assetName]
            ?: error("Catálogo sem representação para ${theme.label}")

    private fun parseIcons(array: JSONArray): List<CatalogIcon> = (0 until array.length()).map { index ->
        val entry = array.getJSONObject(index)
        val assets = entry.getJSONObject("themeAssets")
        val themes = linkedMapOf<String, CatalogAsset>()
        assets.keys().forEach { theme ->
            val paths = assets.getJSONObject(theme)
            fun assetPath(key: String): String {
                val path = paths.getString(key)
                require(path.isNotBlank() && !path.startsWith('/') && !path.contains("..") && !path.contains(':'))
                return "navigation-icons/$path"
            }
            themes[theme] = CatalogAsset(assetPath("image"), assetPath("preview"), assetPath("svg"))
        }
        CatalogIcon(entry.getString("id"), entry.getString("label"), themes.toMap())
    }
}
