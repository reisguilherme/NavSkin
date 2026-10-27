package br.com.gamemap.render

import java.net.URI

/** Pure URL routing; only declared custom glyph stacks can resolve to bundled files. */
class GameGlyphRouter(
    private val stacks: Set<String>,
    private val assetExists: (String) -> Boolean,
) {
    fun transform(kind: Int, url: String): String {
        if (kind != 4) return url // MapLibre Resource.GLYPHS
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        if (uri.scheme != "https" || !uri.host.equals("tiles.openfreemap.org", ignoreCase = true) ||
            uri.port != -1 || uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return url
        val parts = uri.path?.split('/') ?: return url
        if (parts.size != 4 || parts[1] != "fonts" || parts[2] !in stacks) return url
        val block = Regex("([0-9]+)-([0-9]+)\\.pbf").matchEntire(parts[3]) ?: return url
        val start = block.groupValues[1].toIntOrNull() ?: return url
        val end = block.groupValues[2].toIntOrNull() ?: return url
        if (start !in 0..0x10ff00 || start % 256 != 0 || end != start + 255) return url
        val asset = "maps/glyphs/${parts[2]}/${parts[3]}"
        return if (assetExists(asset)) "asset://$asset"
        else "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/${parts[3]}"
    }
}
