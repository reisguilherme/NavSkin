package br.com.gamemap.navigation

import br.com.gamemap.navigation.model.Destination
import br.com.gamemap.navigation.model.LocationState
import br.com.gamemap.navigation.model.NavRoute
import br.com.gamemap.navigation.model.RouteProgress

enum class NavigationPhase {
    IDLE, CALCULATING, PREVIEW, NAVIGATING, RECALCULATING, REROUTE_FAILED,
    ARRIVED, ERROR, RESUME_AVAILABLE,
}

enum class LocationAccess {
    NOT_REQUESTED, DENIED, DISABLED, WAITING, AVAILABLE, WEAK, LOST, NEEDS_PHONE_START,
}

enum class MapOrientation { NORTH_UP, MOVEMENT_UP }

data class NavigationPreferences(
    val vehicleId: String = "arrow",
    val savedPlacesVisible: Boolean = true,
    val orientation: MapOrientation = MapOrientation.MOVEMENT_UP,
)

/** One session shared by phone, foreground location service and the car host. */
data class NavigationSnapshot(
    val phase: NavigationPhase = NavigationPhase.IDLE,
    val location: LocationState = LocationState(),
    val locationAccess: LocationAccess = LocationAccess.NOT_REQUESTED,
    val destination: Destination? = null,
    val route: NavRoute? = null,
    val progress: RouteProgress? = null,
    val message: String? = null,
    val searching: Boolean = false,
    val searchResults: List<Destination> = emptyList(),
    val searchError: String? = null,
    val providerConfigured: Boolean = false,
    val serviceRunning: Boolean = false,
    val simulation: Boolean = false,
    val sessionId: Long = 0L,
    val resumeDestination: Destination? = null,
) {
    val navigationActive: Boolean get() = phase in setOf(
        NavigationPhase.NAVIGATING, NavigationPhase.RECALCULATING, NavigationPhase.REROUTE_FAILED,
    )
    val guidanceAvailable: Boolean get() = phase == NavigationPhase.NAVIGATING && progress?.onRoute == true
}
