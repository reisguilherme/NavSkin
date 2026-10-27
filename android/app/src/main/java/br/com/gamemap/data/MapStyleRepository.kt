package br.com.gamemap.data

import android.content.Context
import br.com.gamemap.preferences.MapPreferences
import br.com.gamemap.preferences.MapBaseMode
import br.com.gamemap.preferences.MapTheme
import br.com.gamemap.preferences.PoiCategory
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import kotlin.math.cos
import kotlin.math.pow

data class PoiPlace(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val latitude: Double,
    val longitude: Double,
) {
    val displayName: String get() = name.ifBlank { "${category.label} · sem nome no OSM" }
    val sourceUrl: String get() = "https://www.openstreetmap.org/$id"
}

class MapStyleRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val rawStyles = mutableMapOf<String, String>()
    private val catalog: List<PoiPlace> by lazy {
        val json = JSONObject(read("maps/data/pois.geojson"))
        val features = json.getJSONArray("features")
        buildList {
            for (index in 0 until features.length()) {
                val feature = features.getJSONObject(index)
                val properties = feature.getJSONObject("properties")
                val category = PoiCategory.fromId(properties.getString("kind")) ?: continue
                val id = properties.getString("osm_id")
                require(id.matches(Regex("(?:node|way|relation)/[0-9]+"))) { "Origem OSM inválida" }
                val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                add(PoiPlace(id, properties.optString("name"), category, coordinates.getDouble(1), coordinates.getDouble(0)))
            }
        }
    }

    fun styleFor(preferences: MapPreferences, isDarkMode: Boolean = false): String {
        val variant = (if (preferences.baseMapMode == MapBaseMode.WORLD) "world-" else "") +
            preferences.theme.assetName + if (isDarkMode) "-night" else ""
        val raw = rawStyles.getOrPut(variant) { read("maps/styles/$variant.json") }
        val style = JSONObject(raw)
        val layers = style.getJSONArray("layers")
        val seen = mutableSetOf<PoiCategory>()
        for (index in 0 until layers.length()) {
            val layer = layers.getJSONObject(index)
            val id = layer.getString("id")
            val category = PoiCategory.entries.firstOrNull { id == "poi-${it.id}" } ?: continue
            val layout = layer.optJSONObject("layout") ?: JSONObject().also { layer.put("layout", it) }
            layout.put("visibility", if (category in preferences.enabledCategories) "visible" else "none")
            seen.add(category)
        }
        check(seen == PoiCategory.entries.toSet()) { "Estilo sem todas as categorias de POIs" }
        return style.toString()
    }

    fun categoryCounts(): Map<PoiCategory, Int> = PoiCategory.entries.associateWith { category ->
        catalog.count { it.category == category }
    }

    fun offlineMissingCategories(): Set<PoiCategory> {
        val values = JSONObject(read("maps/data/pois-metadata.json"))
            .optJSONObject("dataCollection")?.optJSONArray("notCollectedCategories") ?: return emptySet()
        return (0 until values.length()).mapNotNull { PoiCategory.fromId(values.optString(it)) }.toSet()
    }

    /** Manual map center only. This never reads the device's location or computes a route. */
    fun nearestTo(camera: CameraPosition, categories: Set<PoiCategory>, limit: Int): List<PoiPlace> {
        val center = camera.target ?: return emptyList()
        val correction = cos(Math.toRadians(center.latitude))
        return catalog.asSequence().filter { it.category in categories && it.name.isNotBlank() }
            .sortedBy { (it.latitude - center.latitude).pow(2) + ((it.longitude - center.longitude) * correction).pow(2) }
            .take(limit.coerceAtLeast(0)).toList()
    }

    private fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}
