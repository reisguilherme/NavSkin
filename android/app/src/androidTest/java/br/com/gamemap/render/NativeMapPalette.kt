package br.com.gamemap.render

import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import br.com.gamemap.preferences.MapTheme
import org.json.JSONObject

/** Expected authored colors, checked against actual GPU pixels by native tests. */
internal data class NativeMapPalette(
    val name: String, val park: Int, val road: Int, val water: Int, val building: Int,
) {
    companion object {
        private val palettes by lazy {
            val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
            JSONObject(assets.open("maps/styles/theme-tokens.json").bufferedReader().use { it.readText() })
                .getJSONObject("palettes")
        }

        fun forTheme(theme: MapTheme, night: Boolean = false): NativeMapPalette {
            val variant = theme.assetName + if (night) "-night" else ""
            val colors = palettes.getJSONObject(variant)
            fun color(key: String) = Color.parseColor(colors.getString(key))
            val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
            val style = JSONObject(assets.open("maps/styles/$variant.json").bufferedReader().use { it.readText() })
            val layers = style.getJSONArray("layers")
            fun authored(layerId: String, property: String, fallback: Int): Int {
                val layer = (0 until layers.length()).map(layers::getJSONObject).firstOrNull { it.getString("id") == layerId }
                val properties = layer?.optJSONObject("paint")
                val paint = properties?.opt(property)
                val authored = (paint as? String)?.takeIf { it.startsWith("#") }?.let(Color::parseColor) ?: return fallback
                val opacity = properties?.optDouble(property.replace("-color", "-opacity"), 1.0) ?: 1.0
                if (opacity >= 1.0) return authored
                val background = (0 until layers.length()).map(layers::getJSONObject)
                    .first { it.getString("id") == "background" }.getJSONObject("paint").getString("background-color")
                    .let(Color::parseColor)
                fun channel(a: Int, b: Int) = (a * opacity + b * (1.0 - opacity)).toInt()
                return Color.rgb(channel(Color.red(authored), Color.red(background)),
                    channel(Color.green(authored), Color.green(background)), channel(Color.blue(authored), Color.blue(background)))
            }
            // Wide primary fills are stable samples; thin casings are antialiased.
            // Approved authored passes can override the seed palette; verify their final colors.
            return NativeMapPalette(variant, authored("vegetation-parks", "fill-color", color("park")),
                authored("road-0-primary-fill", "line-color", color(if (colors.has("primary")) "primary" else "road")),
                authored("water-fill", "fill-color", color("water")),
                authored("building-footprints", "fill-color", color("building")))
        }
    }
}
