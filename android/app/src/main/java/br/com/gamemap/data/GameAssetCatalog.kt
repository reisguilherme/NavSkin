package br.com.gamemap.data

import android.content.Context
import br.com.gamemap.preferences.MapTheme
import org.json.JSONObject

/** References exact supplied font files; absent themes keep their existing system font. */
data class GameUiFont(val file: String, val postScriptName: String)
data class GameThemeAssets(val ui: GameUiFont?, val map: Map<String, String>)

class GameAssetCatalog private constructor(val themes: Map<String, GameThemeAssets>) {
    val fontStacks: Set<String> = themes.values.flatMap { it.map.values }.toSet()

    companion object {
        fun load(context: Context): GameAssetCatalog = runCatching {
            context.assets.open("game-assets/catalog.json").bufferedReader().use { fromJson(it.readText()) }
        }.getOrElse { GameAssetCatalog(emptyMap()) }

        fun fromJson(text: String): GameAssetCatalog {
            val json = JSONObject(text)
            require(json.getInt("schemaVersion") == 1)
            val themes = json.getJSONObject("themes")
            return GameAssetCatalog(buildMap {
                for (theme in MapTheme.entries.map { it.assetName }) {
                    val entry = themes.optJSONObject(theme) ?: continue
                    val ui = entry.optJSONObject("ui")?.let {
                        val file = it.getString("file")
                        require(safeFontPath(file, theme)) { "Unsafe font asset" }
                        GameUiFont(file, it.optString("postScriptName"))
                    }
                    val map = entry.optJSONObject("map")?.let { roles -> buildMap {
                        for (role in listOf("road", "poi", "place")) {
                            val name = roles.optString(role)
                            if (name.isNotBlank()) {
                                require(name.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Unsafe font stack" }
                                put(role, name)
                            }
                        }
                    } } ?: emptyMap()
                    put(theme, GameThemeAssets(ui, map))
                }
            })
        }

        private fun safeFontPath(path: String, theme: String) =
            path.matches(Regex("game-assets/$theme/fonts/[A-Za-z0-9_.-]+\\.(ttf|otf)")) &&
                !path.contains("..")
    }
}
