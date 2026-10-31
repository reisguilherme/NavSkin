package br.com.gamemap.render

import org.junit.Assert.*
import org.junit.Test

class NavigationAvatarFrameTest {
    private val pitches = listOf(0.0, 40.0, 55.0)

    @Test fun followsRelativeHeadingInsteadOfWorldHeading() {
        assertEquals(0, frame(125.0, 125.0).angleIndex)
        assertEquals(8, frame(90.0, 0.0).angleIndex)
        assertEquals(24, frame(0.0, 90.0).angleIndex)
        assertEquals(0, frame(359.0, 1.0).angleIndex)
        assertEquals(0, frame(1.0, 359.0).angleIndex)
    }

    @Test fun selectsNearestPitchWithoutStretchingTheBitmap() {
        assertEquals(0, frame(pitch = 0.0).pitchIndex)
        assertEquals(1, frame(pitch = 40.0).pitchIndex)
        assertEquals(2, frame(pitch = 48.0).pitchIndex)
        assertEquals(2, frame(pitch = 70.0).pitchIndex)
        assertEquals(0, frame(pitch = -10.0).pitchIndex)
    }

    @Test fun quantizesSmallCameraChangesAndWrapsMultipleTurns() {
        assertEquals(frame(45.0, 0.0), frame(47.0, 1.0))
        assertEquals(frame(45.0, 0.0), frame(765.0, -720.0))
        assertEquals(1, frame(6.0, 0.0).angleIndex)
        assertEquals(31, frame(-6.0, 0.0).angleIndex)
        assertEquals(0, frame(-359.0, 359.0).angleIndex)
    }

    @Test fun invalidSensorValuesNeverProduceAnInvalidFrame() {
        val frame = frame(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN)
        assertEquals(0, frame.angleIndex)
        assertEquals(0, frame.pitchIndex)
    }

    @Test fun onlyNavigationAndFreeDrivingUseThe3DAvatar() {
        assertFalse(NavigationAvatarFrameSelector.isEnabled(navigationActive = false, vibing = false))
        assertTrue(NavigationAvatarFrameSelector.isEnabled(navigationActive = true, vibing = false))
        assertTrue(NavigationAvatarFrameSelector.isEnabled(navigationActive = false, vibing = true))
        assertTrue(NavigationAvatarFrameSelector.isEnabled(navigationActive = true, vibing = true))
    }

    @Test fun artworkAnchorKeepsTheWheelsOnTheGpsCoordinate() {
        assertEquals(0f, NavigationAvatarFrameSelector.anchorOffset(0.5, 192), .0001f)
        assertEquals(-38.4f, NavigationAvatarFrameSelector.anchorOffset(0.7, 192), .0001f)
        // icon-offset is in source pixels and MapLibre scales it by icon-size.
        assertEquals(-14.4f, NavigationAvatarFrameSelector.anchorOffset(0.7, 192) * (72f / 192f), .0001f)
    }

    private fun frame(heading: Double = 0.0, bearing: Double = 0.0, pitch: Double = 48.0) =
        NavigationAvatarFrameSelector.select(heading, bearing, pitch, 32, pitches)
}
