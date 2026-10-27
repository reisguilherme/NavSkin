package br.com.gamemap.navigation.runtime

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import br.com.gamemap.navigation.location.AndroidLocationSource
import br.com.gamemap.navigation.model.LocationFix
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class AndroidLocationSourceTest {
    private lateinit var manager: LocationManager
    private lateinit var source: AndroidLocationSource
    private val fixes = mutableListOf<LocationFix>()
    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        manager = app.getSystemService(LocationManager::class.java)
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        source = AndroidLocationSource(app, { fixes += it }, {}, { fail(it) })
    }
    @After fun cleanup() { if (::source.isInitialized) source.stop() }

    private fun location(provider: String, accuracyMeters: Float = 5f) = Location(provider).apply {
        latitude = -16.68
        longitude = -49.25
        accuracy = accuracyMeters
        speed = 10f
        speedAccuracyMetersPerSecond = 0.5f
        bearing = 90f
        bearingAccuracyDegrees = 60f
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        time = 1_000_000L
    }
    private fun deliver(value: Location) {
        shadowOf(manager).simulateLocation(value)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun lowConfidenceBearingIsDroppedAndMonotonicTimestampIsPreserved() {
        assertTrue(source.start())
        val value = location(LocationManager.GPS_PROVIDER)
        deliver(value)
        assertEquals(1, fixes.size)
        assertNull(fixes.single().bearingDegrees)
        assertEquals(0.5, fixes.single().speedAccuracyMps!!, 0.01)
        assertEquals(value.elapsedRealtimeNanos / 1_000_000, fixes.single().elapsedRealtimeMs)
    }

    @Test fun coarserNetworkPositionDoesNotReplaceFreshAccurateGps() {
        assertTrue(source.start())
        deliver(location(LocationManager.GPS_PROVIDER))
        deliver(location(LocationManager.NETWORK_PROVIDER, 80f).apply { latitude += 0.001 })
        assertEquals(1, fixes.size)
        assertEquals(5.0, fixes.single().accuracyMeters, 0.01)
    }

    @Test fun repeatedStartCreatesOneGpsSubscriptionAndStopRemovesIt() {
        assertTrue(source.start())
        assertTrue(source.start())
        assertEquals(1, shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).size)
        source.stop()
        assertTrue(shadowOf(manager).getLocationRequests(LocationManager.GPS_PROVIDER).isEmpty())
        assertTrue(shadowOf(manager).getLocationRequests(LocationManager.NETWORK_PROVIDER).isEmpty())
    }
}
