package br.com.gamemap.render

import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import br.com.gamemap.preferences.MapPreferences

internal const val MAP_3D_TILT = 48.0
internal const val MAP_3D_LAYER_PREFIX = "navskin-3d-"

/** Route fitting stays flat without overwriting either persisted dimension preference. */
internal fun mapUses3D(preferences: MapPreferences, navigation: NavigationSnapshot?, mode: NavigationCameraMode,
                       vibing: Boolean = false): Boolean =
    vibing || navigation?.phase != NavigationPhase.PREVIEW && mode != NavigationCameraMode.OVERVIEW &&
        preferences.is3D(navigation?.navigationActive == true)
