package br.com.gamemap.render

import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.preferences.MapPreferences
import br.com.gamemap.preferences.MapTheme
import org.junit.Assert.*
import org.junit.Test

class MapDimensionTest {
    private val active = NavigationSnapshot(phase = NavigationPhase.NAVIGATING, sessionId = 42L)

    @Test fun vibingForces3DWithoutChangingEitherSavedPreference() {
        val flat = MapPreferences(browse3D = false, navigation3D = false)
        for (state in listOf(NavigationSnapshot(), active, active.copy(phase = NavigationPhase.PREVIEW))) {
            assertTrue(mapUses3D(flat, state, NavigationCameraMode.FOLLOW, vibing = true))
            assertTrue(mapUses3D(flat, state, NavigationCameraMode.MANUAL, vibing = true))
            assertFalse(mapUses3D(flat, state, NavigationCameraMode.FOLLOW, vibing = false))
        }
        assertFalse(flat.browse3D)
        assertFalse(flat.navigation3D)
    }

    @Test fun allEightThemesBrowseFlatAndNavigateIn3DByDefault() {
        assertEquals(listOf("gta", "rosa", "rdr2", "smw", "cyberpunk", "pipboy", "hollow", "crossy"),
            MapTheme.entries.map { it.assetName })
        for (theme in MapTheme.entries) {
            val preferences = MapPreferences(theme = theme)
            assertFalse(mapUses3D(preferences, null, NavigationCameraMode.FOLLOW))
            assertTrue(mapUses3D(preferences, active, NavigationCameraMode.FOLLOW))
            assertTrue(mapUses3D(preferences, active, NavigationCameraMode.MANUAL))
        }
    }

    @Test fun previewAndOverviewStayFlatThenStartOrRecenterRestoresSavedNavigationChoice() {
        val preferences = MapPreferences(browse3D = true)
        val camera = NavigationCameraState()
        val preview = active.copy(phase = NavigationPhase.PREVIEW)
        assertFalse(mapUses3D(preferences, preview, camera.mode))
        camera.overview()
        camera.observe(active)
        assertTrue(mapUses3D(preferences, active, camera.mode))
        camera.overview()
        assertFalse(mapUses3D(preferences, active, camera.mode))
        camera.follow()
        assertTrue(mapUses3D(preferences, active, camera.mode))
        assertFalse(mapUses3D(preferences.copy(navigation3D = false), active, camera.mode))
    }

    @Test fun manualCameraAndRecalculationKeepTheExplicitDimensionChoice() {
        val camera = NavigationCameraState(NavigationCameraMode.MANUAL, 42L)
        val flat = MapPreferences(navigation3D = false, browse3D = true)
        repeat(5) {
            val recalculating = active.copy(phase = NavigationPhase.RECALCULATING)
            camera.observe(recalculating)
            assertEquals(NavigationCameraMode.MANUAL, camera.mode)
            assertFalse(mapUses3D(flat, recalculating, camera.mode))
            assertTrue(mapUses3D(flat.copy(navigation3D = true), recalculating, camera.mode))
        }
        assertTrue(mapUses3D(flat, NavigationSnapshot(), camera.mode))
    }

    @Test fun panningAnOverviewKeepsItFlatUntilExplicitRecenterOrDimensionChange() {
        val camera = NavigationCameraState()
        camera.observe(active)
        camera.overview()
        camera.manual()
        camera.observe(active.copy(phase = NavigationPhase.RECALCULATING))
        assertEquals(NavigationCameraMode.MANUAL, camera.mode)
        assertFalse(mapUses3D(MapPreferences(), active, camera.dimensionMode))
        camera.usePreferredDimension()
        assertTrue(mapUses3D(MapPreferences(), active, camera.dimensionMode))
        camera.overview()
        camera.manual()
        camera.follow()
        assertTrue(mapUses3D(MapPreferences(), active, camera.dimensionMode))
    }
}
