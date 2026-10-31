package br.com.gamemap.render

import kotlin.math.abs
import kotlin.math.floor

internal data class NavigationAvatarFrame(val angleIndex: Int, val pitchIndex: Int)

/** Select a baked mesh view in camera space; the billboard itself must never rotate. */
internal object NavigationAvatarFrameSelector {
    fun isEnabled(navigationActive: Boolean, vibing: Boolean) = navigationActive || vibing

    fun select(heading: Double, cameraBearing: Double, cameraPitch: Double, frameCount: Int,
               pitches: List<Double>): NavigationAvatarFrame {
        require(frameCount > 0 && pitches.isNotEmpty())
        val safeHeading = heading.takeIf { it.isFinite() } ?: 0.0
        val safeBearing = cameraBearing.takeIf { it.isFinite() } ?: 0.0
        val safePitch = cameraPitch.takeIf { it.isFinite() } ?: 0.0
        val relative = ((safeHeading % 360.0 - safeBearing % 360.0) % 360.0 + 360.0) % 360.0
        val index = floor(relative / 360.0 * frameCount + .5).toInt() % frameCount
        val pitchIndex = pitches.indices.minBy { abs(pitches[it] - safePitch) }
        return NavigationAvatarFrame(index, pitchIndex)
    }

    /** MapLibre multiplies this source-pixel offset by icon-size. */
    fun anchorOffset(anchor: Double, imageSize: Int) = ((0.5 - anchor) * imageSize).toFloat()
}
