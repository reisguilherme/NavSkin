package br.com.gamemap.render

import br.com.gamemap.preferences.PoiCategory
import org.json.JSONObject

/** Returns only category changes that leave every base map source unchanged. */
internal fun poiVisibilityChanges(previous: String, requested: String): Map<String, String>? = runCatching {
    val before = JSONObject(previous)
    val after = JSONObject(requested)
    val expectedIds = PoiCategory.entries.map { "poi-${it.id}" }.toSet()
    fun poiLayers(style: JSONObject): Map<String, JSONObject> {
        val layers = style.getJSONArray("layers")
        return (0 until layers.length()).map { layers.getJSONObject(it) }
            .filter { it.optString("id") in expectedIds }.associateBy { it.getString("id") }
    }
    val oldLayers = poiLayers(before)
    val newLayers = poiLayers(after)
    if (oldLayers.keys != expectedIds || newLayers.keys != expectedIds) return@runCatching null
    val changes = linkedMapOf<String, String>()
    for (id in expectedIds) {
        val oldLayout = oldLayers.getValue(id).optJSONObject("layout") ?: return@runCatching null
        val newLayout = newLayers.getValue(id).optJSONObject("layout") ?: return@runCatching null
        val newVisibility = newLayout.optString("visibility", "visible")
        if (newVisibility !in setOf("visible", "none")) return@runCatching null
        if (oldLayout.optString("visibility", "visible") != newVisibility) changes[id] = newVisibility
        // Restore only the permitted property before comparing every other byte of JSON data.
        if (oldLayout.has("visibility")) newLayout.put("visibility", oldLayout.get("visibility"))
        else newLayout.remove("visibility")
    }
    if (before.toString() == after.toString()) changes else null
}.getOrNull()
