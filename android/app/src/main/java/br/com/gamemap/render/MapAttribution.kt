package br.com.gamemap.render

import android.text.Html
import org.json.JSONObject

/** The Surface attribution is passive text; links and license details live in phone help. */
internal fun mapAttributionText(styleJson: String, withNavigationProvider: Boolean): String {
    val raw = runCatching {
        val style = JSONObject(styleJson)
        val metadata = style.optJSONObject("metadata")
        metadata?.optJSONObject("navskin")?.optString("attribution")?.takeIf { it.isNotBlank() }
            ?: metadata?.optString("gamemap:attribution")?.takeIf { it.isNotBlank() }
            ?: metadata?.optJSONObject("gamemap")?.optString("attribution")?.takeIf { it.isNotBlank() }
            ?: style.optJSONObject("sources")?.let { sources ->
                sources.keys().asSequence().mapNotNull { key ->
                    sources.optJSONObject(key)?.optString("attribution")?.takeIf { it.isNotBlank() }
                }.distinct().joinToString(" · ").takeIf { it.isNotBlank() }
            }
    }.getOrNull() ?: "© OpenStreetMap contributors · ODbL"
    val credit = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
    val attributed = if (credit.contains("ODbL")) credit else "$credit · ODbL"
    return if (withNavigationProvider) "$attributed · Geoapify" else attributed
}
