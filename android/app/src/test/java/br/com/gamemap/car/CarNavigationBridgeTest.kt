package br.com.gamemap.car

import android.app.Application
import android.os.Looper
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import br.com.gamemap.navigation.NavigationPhase
import br.com.gamemap.navigation.NavigationSnapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class CarNavigationBridgeTest {
    private fun manager(): TestNavigationManager {
        val car = TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        return car.getCarService(NavigationManager::class.java) as TestNavigationManager
    }
    private val active = NavigationSnapshot(phase = NavigationPhase.NAVIGATING, serviceRunning = true)

    @Test fun repeatedUpdatesStartOnceAndDisconnectDoesNotStopSharedRuntime() {
        val manager = manager()
        var stops = 0
        val bridge = CarNavigationBridge(manager, { stops++ }, {})
        bridge.update(active)
        bridge.update(active)
        assertNotNull(manager.navigationManagerCallback)
        assertEquals(1, manager.navigationStartedCount)
        assertEquals(1, manager.tripsSent.size)
        bridge.close()
        bridge.close()
        assertEquals(1, manager.navigationEndedCount)
        assertEquals(0, stops)
    }

    @Test fun failedForegroundServiceNeverClaimsHostNavigation() {
        val manager = manager()
        val bridge = CarNavigationBridge(manager, {}, {})
        bridge.update(active.copy(serviceRunning = false))
        assertEquals(0, manager.navigationStartedCount)
        assertTrue(manager.tripsSent.isEmpty())
        bridge.close()
    }

    @Test fun hostStopReachesSharedRuntimeWithoutAnExtraHostEnd() {
        val manager = manager()
        var stops = 0
        val bridge = CarNavigationBridge(manager, { stops++ }, {})
        bridge.update(active)
        manager.onStopNavigation()
        shadowOf(Looper.getMainLooper()).idle()
        bridge.update(NavigationSnapshot())
        assertEquals(1, stops)
        assertEquals(0, manager.navigationEndedCount)
        bridge.close()
    }
}
