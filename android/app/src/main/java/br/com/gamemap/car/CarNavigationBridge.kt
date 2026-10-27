package br.com.gamemap.car

import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.navigation.model.Trip
import br.com.gamemap.navigation.NavigationSnapshot

/** A host attachment owns metadata only; closing it never stops the shared phone trip. */
internal class CarNavigationBridge(
    private val manager: NavigationManager,
    onStop: () -> Unit,
    onAutoDrive: () -> Unit,
) : AutoCloseable {
    private var started = false
    private var lastTrip: Trip? = null
    private var closed = false

    init {
        manager.setNavigationManagerCallback(object : NavigationManagerCallback {
            override fun onStopNavigation() {
                // The manager has already relinquished host ownership before this callback.
                started = false
                lastTrip = null
                onStop()
            }
            override fun onAutoDriveEnabled() = onAutoDrive()
        })
    }

    fun update(state: NavigationSnapshot) {
        if (closed) return
        val active = state.navigationActive && (state.serviceRunning || state.simulation)
        if (!active) {
            if (started) manager.navigationEnded()
            started = false
            lastTrip = null
            return
        }
        if (!started) {
            manager.navigationStarted()
            started = true
        }
        val trip = CarNavigationModels.trip(state)
        if (trip != lastTrip) {
            manager.updateTrip(trip)
            lastTrip = trip
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        if (started) manager.navigationEnded()
        started = false
        lastTrip = null
        manager.clearNavigationManagerCallback()
    }
}
