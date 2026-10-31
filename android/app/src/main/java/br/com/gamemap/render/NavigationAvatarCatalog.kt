package br.com.gamemap.render

import android.content.res.AssetManager
import br.com.gamemap.preferences.MapTheme
import org.json.JSONObject

internal data class NavigationAvatarAsset(
    val path: String,
    val imageSize: Int,
    val displaySize: Int,
    val anchorX: Double,
    val anchorY: Double,
)

/** Optional catalog: an incomplete package falls back to the existing navigation icon. */
internal class NavigationAvatarCatalog(assets: AssetManager) {
    private val data = runCatching {
        JSONObject(assets.open("navigation-3d/catalog.json").bufferedReader().use { it.readText() })
            .also { source ->
                require(source.getInt("schemaVersion") == 1)
                require(source.getInt("frameCount") in 1..128)
                require(source.getInt("imageSize") in 32..512)
                require(source.getInt("displaySize") in 32..128)
                require(source.getJSONArray("pitches").length() in 1..8)
                require(source.getJSONArray("anchor").length() == 2)
                (0..1).forEach { require(source.getJSONArray("anchor").getDouble(it) in 0.0..1.0) }
            }
    }.getOrNull()
    private val pitches = runCatching { data?.getJSONArray("pitches")?.let { array ->
        (0 until array.length()).map { array.getDouble(it) }
    }?.takeIf { values -> values.all { it.isFinite() && it in 0.0..85.0 } } }.getOrNull()

    fun frame(theme: MapTheme, heading: Double, bearing: Double, pitch: Double): NavigationAvatarAsset? =
        runCatching {
            val source = data ?: return null
            val pitchValues = pitches ?: return null
            val selection = NavigationAvatarFrameSelector.select(heading, bearing, pitch, source.getInt("frameCount"), pitchValues)
            val frames = source.getJSONObject("themes").getJSONObject(theme.assetName).getJSONArray("frames")
            require(frames.length() == pitchValues.size)
            val angles = frames.getJSONArray(selection.pitchIndex)
            require(angles.length() == source.getInt("frameCount"))
            val path = angles.getString(selection.angleIndex)
            require(path.isNotBlank() && !path.startsWith('/') && !path.contains("..") && !path.contains(':') && !path.contains('\\'))
            NavigationAvatarAsset("navigation-3d/$path", source.getInt("imageSize"), source.getInt("displaySize"),
                source.getJSONArray("anchor").getDouble(0), source.getJSONArray("anchor").getDouble(1))
        }.getOrNull()
}
