package br.com.gamemap.navigation.runtime

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import android.os.Looper
import br.com.gamemap.navigation.*
import br.com.gamemap.navigation.model.*
import br.com.gamemap.navigation.provider.*
import br.com.gamemap.navigation.storage.*
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import java.util.UUID
import java.util.concurrent.*
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
@Config(application = Application::class, sdk = [35], manifest = Config.NONE, shadows = [PosixAtomicFileShadow::class])
class NavigationRuntimeTest {
    private lateinit var context: Context
    private lateinit var settings: NavigationSettings
    private lateinit var places: LocalPlacesStore
    private lateinit var runtime: NavigationRuntime
    private lateinit var executor: UninterruptibleHttpExecutor
    private var elapsed = 100_000L
    private var serviceStarts = 0
    private val requests = mutableListOf<String>()
    private var response: (String) -> HttpResponse = ::normalResponse
    private fun destination(id: String) = Destination(id, "Destination $id", GeoPoint(0.0, if (id == "A") 0.001 else 0.002))

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(NavigationSettings.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        settings = NavigationSettings(context, object : KeyVault {
            override fun encrypt(plaintext: String) = "test-only:$plaintext"
            override fun decrypt(ciphertext: String) = ciphertext.removePrefix("test-only:")
        })
        settings.setApiKey("synthetic-test-key")
        places = LocalPlacesStore(context, file = File(context.filesDir, "runtime-test-${UUID.randomUUID()}.json"))
        executor = UninterruptibleHttpExecutor()
        runtime = NavigationRuntime(context, GeoapifyProvider(settings::apiKey, HttpTransport {
            requests += it
            response(it)
        }), settings, places, executor, { elapsed }, { 1_000_000L + elapsed },
            { serviceStarts++ }, {})
    }

    @After fun cleanup() {
        if (::runtime.isInitialized) runtime.close()
        if (::settings.isInitialized) settings.close()
    }

    private fun fix(x: Double = 0.0, y: Double = 0.0, accuracy: Double = 5.0) {
        runtime.acceptFix(LocationFix(GeoPoint(y / 111195.0, x / 111195.0), accuracy, 90.0, 10.0, 0.5,
            elapsed, 1_000_000L + elapsed))
    }

    private fun stop(id: String, longitude: Double) = Destination(id, "Stop $id", GeoPoint(0.0, longitude))

    private fun beginNavigation() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("B"))
        executor.runAt(0)
        runtime.startNavigation()
    }

    private fun waypoints(url: String = requests.last()): List<String> = URI(url).rawQuery.split("&")
        .first { it.startsWith("waypoints=") }.substringAfter("=")
        .let { URLDecoder.decode(it, "UTF-8").split("|") }

    @Test fun orderedStopsCommitOnlyWithReplacementRouteAndKeepFinalDestination() {
        beginNavigation()
        val original = runtime.snapshot().route
        val one = stop("one", 0.0005)
        val two = stop("two", 0.001)
        runtime.addStop(one)
        assertEquals(original, runtime.snapshot().route)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf(one), runtime.snapshot().pendingIntermediateStops)
        executor.runAt(0)
        runtime.addStop(two)
        executor.runAt(0)
        assertEquals(listOf("0.0,0.0", "0.0,5.0E-4", "0.0,0.001", "0.0,0.002"), waypoints())
        assertEquals(listOf(one, two), runtime.snapshot().intermediateStops)
        assertEquals(destination("B"), runtime.snapshot().destination)
        assertEquals(destination("B"), runtime.snapshot().route!!.destination)
        assertNull(runtime.snapshot().pendingIntermediateStops)
        assertEquals(listOf(one, two), places.snapshot().resumeIntermediateStops)
    }

    @Test fun failedRemovalKeepsCommittedRouteAndStopsAndRetryCommitsRequestedItinerary() {
        beginNavigation()
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        executor.runAt(0)
        val oldRoute = runtime.snapshot().route
        response = { HttpResponse(503, "") }
        runtime.removeStop(one.id)
        executor.runAt(0)
        assertEquals(oldRoute, runtime.snapshot().route)
        assertEquals(listOf(one), runtime.snapshot().intermediateStops)
        assertEquals(emptyList<Destination>(), runtime.snapshot().pendingIntermediateStops)
        assertEquals(listOf(one), places.snapshot().resumeIntermediateStops)
        response = ::normalResponse
        runtime.retryRoute()
        executor.runAt(0)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf("0.0,0.0", "0.0,0.002"), waypoints())
        assertTrue(places.snapshot().resumeIntermediateStops.isEmpty())
    }

    @Test fun latestStopEditWinsEvenWhenEarlierHttpWorkIgnoresCancellation() {
        beginNavigation()
        val one = stop("one", 0.0005)
        val two = stop("two", 0.001)
        runtime.addStop(one)
        runtime.addStop(two)
        executor.runAt(1)
        val latest = runtime.snapshot().route
        executor.runAt(0)
        assertEquals(latest, runtime.snapshot().route)
        assertEquals(listOf(one, two), runtime.snapshot().intermediateStops)
        runtime.removeStop(one.id)
        runtime.stopNavigation()
        executor.runAt(0)
        assertEquals(NavigationPhase.IDLE, runtime.snapshot().phase)
        assertNull(runtime.snapshot().destination)
        assertNull(runtime.snapshot().route)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertNull(runtime.snapshot().pendingIntermediateStops)
        assertNull(places.snapshot().resumeDestination)
    }

    @Test fun weakGpsAndMissingKeyRetainOldItineraryUntilExplicitRetrySucceeds() {
        beginNavigation()
        val original = runtime.snapshot().route
        val one = stop("one", 0.0005)
        elapsed += 1_000
        fix(accuracy = 100.0)
        runtime.addStop(one)
        assertTrue(executor.tasks.isEmpty())
        assertEquals(original, runtime.snapshot().route)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf(one), runtime.snapshot().pendingIntermediateStops)
        elapsed += 1_000
        fix()
        settings.setApiKey("")
        runtime.retryRoute()
        assertTrue(executor.tasks.isEmpty())
        assertEquals(original, runtime.snapshot().route)
        settings.setApiKey("synthetic-test-key")
        runtime.retryRoute()
        executor.runAt(0)
        assertEquals(listOf(one), runtime.snapshot().intermediateStops)
    }

    @Test fun invalidDuplicateAndFinalDestinationStopsDoNotDispatchNewRoutes() {
        beginNavigation()
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        executor.runAt(0)
        runtime.addStop(one)
        runtime.addStop(one.copy(id = "alias"))
        runtime.addStop(destination("B"))
        runtime.addStop(stop("bad", Double.NaN))
        runtime.addStop(stop("origin", 0.0))
        assertTrue(executor.tasks.isEmpty())
        assertEquals(listOf(one), runtime.snapshot().intermediateStops)
        assertNull(runtime.snapshot().pendingIntermediateStops)
    }

    @Test fun passedStopIsRemovedFromResumeAndNeverReturnedToOnDeviationReroute() {
        beginNavigation()
        val one = stop("one", 0.0005)
        val two = stop("two", 0.0015)
        runtime.addStop(one)
        executor.runAt(0)
        runtime.addStop(two)
        executor.runAt(0)
        elapsed += 1_000; fix(10.0)
        elapsed += 4_000; fix(60.0)
        assertEquals(listOf(two), runtime.snapshot().intermediateStops)
        assertEquals(listOf(two), places.snapshot().resumeIntermediateStops)
        repeat(3) { elapsed += 1_000; fix(70.0, 80.0) }
        assertEquals(NavigationPhase.RECALCULATING, runtime.snapshot().phase)
        executor.runAt(0)
        assertEquals(3, waypoints().size)
        assertEquals("0.0,0.0015", waypoints()[1])
        assertEquals("0.0,0.002", waypoints().last())
    }

    @Test fun resumeAfterServiceDeathRecalculatesRemainingStopsBeforeTheFinalDestination() {
        beginNavigation()
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        executor.runAt(0)
        runtime.onServiceStopped()
        assertEquals(listOf(one), runtime.snapshot().resumeIntermediateStops)
        runtime.resumeDestination()
        executor.runAt(0)
        assertEquals(listOf(one), runtime.snapshot().intermediateStops)
        assertEquals(destination("B"), runtime.snapshot().destination)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
        assertEquals(listOf("0.0,0.0", "0.0,5.0E-4", "0.0,0.002"), waypoints())
    }

    @Test fun passingACommittedStopDuringAnEditInvalidatesItsInflightResponse() {
        beginNavigation()
        val one = stop("one", 0.0005)
        val two = stop("two", 0.0015)
        runtime.addStop(one)
        executor.runAt(0)
        elapsed += 1_000; fix(10.0)
        runtime.addStop(two)
        elapsed += 4_000; fix(60.0)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf(two), runtime.snapshot().pendingIntermediateStops)
        assertEquals(2, executor.tasks.size)
        executor.runAt(1)
        val replacement = runtime.snapshot().route
        assertEquals(3, waypoints().size)
        assertEquals("0.0,0.0015", waypoints()[1])
        executor.runAt(0)
        assertEquals(replacement, runtime.snapshot().route)
        assertEquals(listOf(two), runtime.snapshot().intermediateStops)
    }

    @Test fun oldStopResponseCannotReplaceANewFinalDestination() {
        beginNavigation()
        runtime.addStop(stop("one", 0.0005))
        runtime.selectDestination(destination("A"))
        executor.runAt(1)
        val latest = runtime.snapshot().route
        executor.runAt(0)
        assertEquals(latest, runtime.snapshot().route)
        assertEquals(destination("A"), runtime.snapshot().destination)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
    }

    @Test fun failedAddNeverClaimsTheStopOrOverwritesPersistedResume() {
        beginNavigation()
        val original = runtime.snapshot().route
        val one = stop("one", 0.0005)
        response = { HttpResponse(503, "") }
        runtime.addStop(one)
        executor.runAt(0)
        assertEquals(original, runtime.snapshot().route)
        assertEquals(destination("B"), runtime.snapshot().destination)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf(one), runtime.snapshot().pendingIntermediateStops)
        assertTrue(places.snapshot().resumeIntermediateStops.isEmpty())
        assertEquals(destination("B"), places.snapshot().resumeDestination)
    }

    @Test fun intermediateStopDoesNotTriggerArrivalAndReplayCannotRequestRealStops() {
        beginNavigation()
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        executor.runAt(0)
        elapsed += 1_000; fix(10.0)
        elapsed += 4_000; fix(56.0)
        elapsed += 1_000; fix(57.0)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertEquals(destination("B"), places.snapshot().resumeDestination)
        val detach = runtime.attachClient("test-phone")
        runtime.startReplay(replayRoute(), listOf(replayFix()))
        runtime.addStop(one)
        assertTrue(runtime.snapshot().simulation)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertTrue(executor.tasks.isEmpty())
        detach()
    }

    @Test fun reachingOldRouteEndWhileStopEditIsPendingCannotDiscardThePendingStop() {
        beginNavigation()
        elapsed += 1_000; fix(10.0)
        val detour = stop("detour", 0.003)
        runtime.addStop(detour)
        elapsed += 5_000; fix(80.0)
        elapsed += 5_000; fix(150.0)
        elapsed += 5_000; fix(215.0)
        elapsed += 1_000; fix(221.0)
        assertEquals(NavigationPhase.RECALCULATING, runtime.snapshot().phase)
        assertEquals(listOf(detour), runtime.snapshot().pendingIntermediateStops)
        assertEquals(destination("B"), places.snapshot().resumeDestination)
        executor.runAt(0)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertEquals(listOf(detour), runtime.snapshot().intermediateStops)
        assertEquals(destination("B"), runtime.snapshot().destination)
    }

    @Test fun passingAStopAfterFailedEditRemovesItFromBothCommittedAndRetryItineraries() {
        beginNavigation()
        val one = stop("one", 0.0005)
        val two = stop("two", 0.0015)
        runtime.addStop(one)
        executor.runAt(0)
        elapsed += 1_000; fix(10.0)
        response = { HttpResponse(503, "") }
        runtime.addStop(two)
        executor.runAt(0)
        elapsed += 4_000; fix(60.0)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertEquals(listOf(two), runtime.snapshot().pendingIntermediateStops)
        assertTrue(places.snapshot().resumeIntermediateStops.isEmpty())
        response = ::normalResponse
        runtime.retryRoute()
        executor.runAt(0)
        assertEquals(listOf(two), runtime.snapshot().intermediateStops)
        assertEquals("0.0,0.0015", waypoints()[1])
        assertEquals(3, waypoints().size)
    }

    @Test fun failedPreviewEditCannotStartNavigationOnTheUneditedRoute() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("B"))
        executor.runAt(0)
        val oldRoute = runtime.snapshot().route
        response = { HttpResponse(503, "") }
        runtime.addStop(stop("one", 0.0005))
        executor.runAt(0)
        runtime.startNavigation()
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
        assertEquals(oldRoute, runtime.snapshot().route)
        assertFalse(runtime.snapshot().navigationActive)
        assertNull(places.snapshot().resumeDestination)
    }

    @Test fun failedStopEditKeepsSafeGuidanceOnTheUnchangedRouteWhileShowingPendingEdit() {
        beginNavigation()
        elapsed += 1_000; fix(10.0)
        val original = runtime.snapshot().route
        assertTrue(runtime.snapshot().guidanceAvailable)
        response = { HttpResponse(503, "") }
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        executor.runAt(0)
        assertEquals(original, runtime.snapshot().route)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertTrue(runtime.snapshot().guidanceAvailable)
        assertEquals(listOf(one), runtime.snapshot().pendingIntermediateStops)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
        assertNotNull(runtime.snapshot().message)
    }

    @Test fun failedStopEditStillSuspendsGuidanceIfTheCarHasLeftTheCommittedRoute() {
        beginNavigation()
        elapsed += 1_000; fix(10.0)
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        repeat(3) { elapsed += 1_000; fix(20.0, 80.0) }
        response = { HttpResponse(503, "") }
        executor.runAt(0)
        assertEquals(NavigationPhase.REROUTE_FAILED, runtime.snapshot().phase)
        assertFalse(runtime.snapshot().guidanceAvailable)
        assertEquals(listOf(one), runtime.snapshot().pendingIntermediateStops)
        assertTrue(runtime.snapshot().intermediateStops.isEmpty())
    }

    @Test fun firstCurrentGpsFixClearsOnlyTheLocationStartupNotice() {
        assertTrue(runtime.activateLocation())
        assertNotNull(runtime.snapshot().message)
        runtime.onServiceStarted()
        fix()
        assertEquals(LocationAccess.AVAILABLE, runtime.snapshot().locationAccess)
        assertNull(runtime.snapshot().message)
    }

    @Test fun providerRecoveryClearsTheDisabledLocationNotice() {
        runtime.onServiceStarted()
        fix()
        runtime.onLocationProvidersChanged(false)
        assertNotNull(runtime.snapshot().message)
        elapsed += 17_000
        runtime.onLocationProvidersChanged(true)
        fix()
        assertEquals(LocationAccess.AVAILABLE, runtime.snapshot().locationAccess)
        assertNull(runtime.snapshot().message)
    }

    @Test fun currentGpsDoesNotClearANewerRoutingProviderError() {
        runtime.onServiceStarted()
        fix()
        runtime.onLocationFailure("Aviso temporário de localização")
        response = { HttpResponse(503, "") }
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        val providerMessage = runtime.snapshot().message
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        assertNotNull(providerMessage)
        elapsed += 1_000
        fix()
        assertEquals(providerMessage, runtime.snapshot().message)
    }

    @Test fun selectedDestinationWaitsForGpsThenAutomaticallyCalculatesPreview() {
        val phases = mutableListOf<NavigationPhase>()
        runtime.addListener { phases += it.phase }
        runtime.selectDestination(destination("A"))
        assertEquals(destination("A"), runtime.snapshot().destination)
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertFalse(phases.contains(NavigationPhase.ERROR))
        assertNull(runtime.snapshot().location.speedKmh)
        assertEquals(0, executor.tasks.size)
        assertTrue(requests.isEmpty())
        assertEquals(1, serviceStarts)
        runtime.onServiceStarted()
        fix()
        assertEquals(NavigationPhase.CALCULATING, runtime.snapshot().phase)
        assertEquals(1, executor.tasks.size)
        elapsed += 1_000
        fix()
        assertEquals(1, executor.tasks.size)
        executor.runAt(0)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
        assertNotNull(runtime.snapshot().route)
        assertFalse(runtime.snapshot().navigationActive)
        elapsed += 1_000
        fix()
        assertEquals(0, executor.tasks.size)
        assertEquals(1, requests.size)
    }

    @Test fun weakGpsWaitsWithoutSendingARouteUntilAPreciseFixArrives() {
        runtime.onServiceStarted()
        fix(accuracy = 100.0)
        runtime.selectDestination(destination("A"))
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertEquals(LocationAccess.WEAK, runtime.snapshot().locationAccess)
        assertNull(runtime.snapshot().location.speedKmh)
        assertEquals(0, executor.tasks.size)
        assertTrue(requests.isEmpty())
        elapsed += 1_000
        fix()
        assertEquals(NavigationPhase.CALCULATING, runtime.snapshot().phase)
        assertEquals(1, executor.tasks.size)
    }

    @Test fun staleSnapshotWaitsForAFixNoMoreThanFiveSecondsOld() {
        runtime.onServiceStarted()
        fix()
        elapsed += 5_001
        runtime.selectDestination(destination("A"))
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertEquals(0, executor.tasks.size)
        assertTrue(requests.isEmpty())
        fix()
        assertEquals(NavigationPhase.CALCULATING, runtime.snapshot().phase)
        assertEquals(1, executor.tasks.size)
    }

    @Test fun disabledLocationWaitsUntilProvidersRecoverBeforeRequestingARoute() {
        runtime.onServiceStarted()
        fix()
        runtime.onLocationProvidersChanged(false)
        runtime.selectDestination(destination("A"))
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertEquals(0, executor.tasks.size)
        runtime.onLocationProvidersChanged(true)
        assertEquals(NavigationPhase.CALCULATING, runtime.snapshot().phase)
        assertEquals(1, executor.tasks.size)
    }

    @Test fun returningToThePhoneRestartsGpsForThePendingDestinationAfterAnIdleStop() {
        val detachPhone = runtime.attachClient("phone")
        runtime.selectDestination(destination("A"))
        runtime.onServiceStarted()
        detachPhone()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        runtime.onServiceStopped()
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertEquals(LocationAccess.NEEDS_PHONE_START, runtime.snapshot().locationAccess)
        val detachCar = runtime.attachClient("android-auto-main")
        assertEquals(1, serviceStarts)
        val detachReturningPhone = runtime.attachClient("phone")
        assertEquals(2, serviceStarts)
        assertEquals(LocationAccess.WAITING, runtime.snapshot().locationAccess)
        runtime.onServiceStarted()
        fix()
        assertEquals(1, executor.tasks.size)
        executor.runAt(0)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
        detachReturningPhone()
        detachCar()
    }

    @Test fun grantingPrecisePermissionOnThePhoneRestartsThePendingGpsWait() {
        runtime.setLocationPermission(fine = false, coarse = false)
        val detachPhone = runtime.attachClient("phone")
        runtime.selectDestination(destination("A"))
        assertEquals("WAITING_FOR_LOCATION", runtime.snapshot().phase.name)
        assertEquals(0, serviceStarts)
        runtime.setLocationPermission(fine = true, coarse = true)
        assertEquals(1, serviceStarts)
        runtime.onServiceStarted()
        fix()
        assertEquals(NavigationPhase.CALCULATING, runtime.snapshot().phase)
        detachPhone()
    }

    @Test fun coarseOnlyPermissionKeepsThePrecisePermissionActionWhileWaitingWithoutAService() {
        runtime.setLocationPermission(fine = false, coarse = true)
        runtime.selectDestination(destination("A"))
        runtime.onServiceStopped()
        runtime.setLocationPermission(fine = false, coarse = true)
        assertEquals(NavigationPhase.WAITING_FOR_LOCATION, runtime.snapshot().phase)
        assertEquals(LocationAccess.WEAK, runtime.snapshot().locationAccess)
        fix()
        assertEquals(NavigationPhase.WAITING_FOR_LOCATION, runtime.snapshot().phase)
        assertEquals(0, executor.tasks.size)
        assertTrue(requests.isEmpty())
        assertNull(runtime.snapshot().location.speedKmh)
    }

    @Test fun invalidProviderKeyRemainsAnErrorAfterFurtherGpsFixes() {
        runtime.onServiceStarted()
        fix()
        response = { HttpResponse(401, "") }
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        val errorMessage = runtime.snapshot().message
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        repeat(3) { elapsed += 1_000; fix() }
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        assertEquals(errorMessage, runtime.snapshot().message)
        assertEquals(0, executor.tasks.size)
        assertEquals(1, requests.size)
    }

    @Test fun routeFailureAfterRetryDuringTheRequestDoesNotAutomaticallyRetryOnGpsFixes() {
        runtime.onServiceStarted()
        fix()
        response = { HttpResponse(503, "") }
        runtime.selectDestination(destination("A"))
        runtime.retryRoute()
        assertEquals(1, executor.tasks.size)
        executor.runAt(0)
        val errorMessage = runtime.snapshot().message
        repeat(3) { elapsed += 1_000; fix() }
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        assertEquals(errorMessage, runtime.snapshot().message)
        assertEquals(0, executor.tasks.size)
        assertEquals(1, requests.size)
    }

    @Test fun supersededDestinationResponseCannotReplaceTheNewPreview() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        runtime.selectDestination(destination("B"))
        executor.runAt(1)
        assertEquals("B", runtime.snapshot().route!!.destination.id)
        executor.runAt(0)
        assertEquals("B", runtime.snapshot().route!!.destination.id)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
    }

    @Test fun stopInvalidatesAnUninterruptibleOutstandingRouteResponse() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        runtime.stopNavigation()
        executor.runAt(0)
        assertEquals(NavigationPhase.IDLE, runtime.snapshot().phase)
        assertNull(runtime.snapshot().destination)
        assertNull(runtime.snapshot().route)
    }

    @Test fun cannotStartNavigationUntilForegroundServiceActuallyStarts() {
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
        runtime.onServiceStarted()
        runtime.startNavigation()
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertEquals("A", places.snapshot().resumeDestination!!.id)
    }

    @Test fun unavailableRouteIsAnExplicitErrorAndRetryCanRecover() {
        runtime.onServiceStarted()
        fix()
        response = { HttpResponse(503, "") }
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        assertNull(runtime.snapshot().progress)
        response = ::normalResponse
        runtime.retryRoute()
        executor.runAt(0)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
    }

    @Test fun rateLimitPreventsImmediateRepeatedRequests() {
        runtime.onServiceStarted()
        fix()
        response = { HttpResponse(429, "", mapOf("Retry-After" to listOf("30"))) }
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.retryRoute()
        assertEquals(0, executor.tasks.size)
        elapsed += 31_000L
        fix()
        assertEquals(NavigationPhase.ERROR, runtime.snapshot().phase)
        assertEquals(0, executor.tasks.size)
        assertEquals(1, requests.size)
        response = ::normalResponse
        runtime.retryRoute()
        executor.runAt(0)
        assertEquals(NavigationPhase.PREVIEW, runtime.snapshot().phase)
    }

    @Test fun staleSearchResultsCannotResurrectAfterStop() {
        runtime.search("synthetic")
        runtime.stopNavigation()
        executor.runAt(0)
        assertFalse(runtime.snapshot().searching)
        assertTrue(runtime.snapshot().searchResults.isEmpty())
    }

    @Test fun cancellingPreviewAlsoClearsCompletedSearchResultsAndSearchError() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("B"))
        executor.runAt(0)
        runtime.search("synthetic")
        executor.runAt(0)
        assertFalse(runtime.snapshot().searchResults.isEmpty())
        settings.setApiKey("")
        runtime.search("blocked search")
        assertNotNull(runtime.snapshot().searchError)

        runtime.clearDestination()

        assertEquals(NavigationPhase.IDLE, runtime.snapshot().phase)
        assertNull(runtime.snapshot().destination)
        assertNull(runtime.snapshot().route)
        assertTrue(runtime.snapshot().searchResults.isEmpty())
        assertNull(runtime.snapshot().searchError)
        assertFalse(runtime.snapshot().searching)
    }

    @Test fun clearingPreviewDropsStopsAndIgnoresPendingRouteAndSearchResponses() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("B"))
        executor.runAt(0)
        runtime.addStop(stop("one", 0.0005))
        executor.runAt(0)
        runtime.addStop(stop("two", 0.0015))
        runtime.search("synthetic")
        assertEquals(1, runtime.snapshot().intermediateStops.size)
        assertEquals(2, runtime.snapshot().pendingIntermediateStops!!.size)

        runtime.clearDestination()
        executor.runAt(0)
        executor.runAt(0)

        val cleared = runtime.snapshot()
        assertEquals(NavigationPhase.IDLE, cleared.phase)
        assertNull(cleared.destination)
        assertNull(cleared.route)
        assertNull(cleared.progress)
        assertTrue(cleared.intermediateStops.isEmpty())
        assertNull(cleared.pendingIntermediateStops)
        assertNull(cleared.resumeDestination)
        assertTrue(cleared.resumeIntermediateStops.isEmpty())
        assertTrue(cleared.searchResults.isEmpty())
        assertNull(cleared.searchError)
        assertNull(cleared.message)
        assertFalse(cleared.searching)
        assertEquals(LocationQuality.GOOD, cleared.location.quality)
    }

    @Test fun clearingDestinationAlsoIgnoresLateProviderFailures() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("B"))
        runtime.search("synthetic")
        runtime.clearDestination()
        response = { HttpResponse(503, "") }
        executor.runAt(0)
        executor.runAt(0)
        assertEquals(NavigationPhase.IDLE, runtime.snapshot().phase)
        assertNull(runtime.snapshot().message)
        assertNull(runtime.snapshot().searchError)
        assertFalse(runtime.snapshot().searching)
    }

    @Test fun clearingDestinationWhileWaitingForGpsDoesNotCalculateOnTheNextFix() {
        runtime.selectDestination(destination("B"))
        assertEquals(NavigationPhase.WAITING_FOR_LOCATION, runtime.snapshot().phase)
        runtime.clearDestination()
        runtime.onServiceStarted()
        fix()
        assertEquals(NavigationPhase.IDLE, runtime.snapshot().phase)
        assertNull(runtime.snapshot().destination)
        assertTrue(executor.tasks.isEmpty())
    }

    @Test fun clearDestinationCannotInterruptActiveNavigationOrItsStopEdit() {
        beginNavigation()
        val one = stop("one", 0.0005)
        runtime.addStop(one)
        val beforeClear = runtime.snapshot()
        runtime.clearDestination()
        assertEquals(beforeClear, runtime.snapshot())
        executor.runAt(0)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertEquals(listOf(one), runtime.snapshot().intermediateStops)
        assertEquals(destination("B"), places.snapshot().resumeDestination)
    }

    @Test fun disablingLocationImmediatelyHidesGuidanceAndSpeed() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        elapsed += 1_000
        fix(10.0)
        assertTrue(runtime.snapshot().guidanceAvailable)
        runtime.onLocationProvidersChanged(false)
        assertEquals(LocationAccess.DISABLED, runtime.snapshot().locationAccess)
        assertFalse(runtime.snapshot().guidanceAvailable)
        assertNull(runtime.snapshot().location.speedKmh)
    }

    @Test fun serviceDeathOffersResumeWithNoOldRouteInstructions() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        runtime.onServiceStopped()
        assertEquals(NavigationPhase.RESUME_AVAILABLE, runtime.snapshot().phase)
        assertFalse(runtime.snapshot().navigationActive)
        assertNull(runtime.snapshot().route)
        assertNull(runtime.snapshot().progress)
        assertEquals("A", runtime.snapshot().resumeDestination!!.id)
    }

    @Test fun stoppingReplayCannotLeaveASimulatedPositionAvailableForRealRouting() {
        val detach = runtime.attachClient("test-phone")
        fix()
        runtime.startReplay(replayRoute(), listOf(replayFix()))
        assertTrue(runtime.snapshot().simulation)
        assertTrue(runtime.snapshot().location.fix!!.isMock)
        runtime.stopReplay()
        assertNull(runtime.snapshot().location.fix)
        assertFalse(runtime.snapshot().simulation)
        runtime.selectDestination(destination("A"))
        assertEquals(0, executor.tasks.size)
        detach()
    }

    @Test fun debugReplayStopsWhenTheLastVisibleClientLeaves() {
        val detach = runtime.attachClient("test-phone")
        runtime.startReplay(replayRoute(), listOf(replayFix()))
        detach()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6))
        assertFalse(runtime.snapshot().simulation)
        assertFalse(runtime.snapshot().navigationActive)
    }

    @Test fun permissionRefreshAfterRotationDoesNotInterruptIsolatedReplay() {
        val detach = runtime.attachClient("test-phone")
        runtime.startReplay(replayRoute(), listOf(replayFix(), replayFix().copy(point = GeoPoint(0.0, 0.0005))))
        assertTrue(runtime.snapshot().guidanceAvailable)
        runtime.setLocationPermission(fine = false, coarse = false)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        elapsed += 1_000
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue(runtime.snapshot().simulation)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
        assertTrue(runtime.snapshot().guidanceAvailable)
        assertTrue(runtime.snapshot().message!!.startsWith("SIMULAÇÃO"))
        detach()
    }

    @Test fun arrivalClearsBothPersistedAndVisibleResumeDestination() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        elapsed += 1_000; fix(10.0)
        elapsed += 5_000; fix(60.0)
        elapsed += 4_000; fix(102.0)
        elapsed += 1_000; fix(109.0)
        assertEquals(NavigationPhase.ARRIVED, runtime.snapshot().phase)
        assertNull(places.snapshot().resumeDestination)
        assertNull(runtime.snapshot().resumeDestination)
    }

    @Test fun reducedPermissionImmediatelySuspendsPreciseGuidance() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        elapsed += 1_000; fix(10.0)
        assertTrue(runtime.snapshot().guidanceAvailable)
        runtime.setLocationPermission(fine = false, coarse = true)
        assertEquals(LocationQuality.WEAK, runtime.snapshot().location.quality)
        assertNull(runtime.snapshot().location.speedKmh)
        assertFalse(runtime.snapshot().guidanceAvailable)
    }

    @Test fun failedRecalculationHidesOldGuidanceUntilSuccessfulReplacement() {
        runtime.onServiceStarted()
        fix()
        runtime.selectDestination(destination("A"))
        executor.runAt(0)
        runtime.startNavigation()
        elapsed += 1_000; fix(10.0)
        repeat(3) { elapsed += 1_000; fix(20.0, 80.0) }
        assertEquals(NavigationPhase.RECALCULATING, runtime.snapshot().phase)
        assertNull(runtime.snapshot().progress)
        response = { HttpResponse(503, "") }
        executor.runAt(0)
        assertEquals(NavigationPhase.REROUTE_FAILED, runtime.snapshot().phase)
        elapsed += 1_000; fix(30.0, 80.0)
        assertFalse(runtime.snapshot().guidanceAvailable)
        response = ::normalResponse
        runtime.retryRoute()
        executor.runAt(0)
        assertEquals(NavigationPhase.NAVIGATING, runtime.snapshot().phase)
    }

    private fun replayRoute() = NavRoute("explicit-test-replay", listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)),
        emptyList(), 111.2, 60.0, destination("A"), 1_000_000L)
    private fun replayFix() = LocationFix(GeoPoint(0.0, 0.0004), 5.0, 90.0, 10.0, 0.5, 0L, 0L, true)

    private fun normalResponse(url: String): HttpResponse {
        if (!url.contains("/routing")) return HttpResponse(200, """{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[0.001,0]},"properties":{"name":"Synthetic result","formatted":"Test address","place_id":"test-place"}}]}""")
        val parameters = URI(url).rawQuery.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to URLDecoder.decode(parts[1], "UTF-8")
        }
        val points = parameters.getValue("waypoints").split("|").map { it.split(",") }
        val coordinates = points.zipWithNext().joinToString(",") { (a, b) -> "[[${a[1]},${a[0]}],[${b[1]},${b[0]}]]" }
        val legs = points.zipWithNext().joinToString(",") {
            """{"distance":222,"time":60,"steps":[{"from_index":0,"to_index":1,"distance":222,"time":60,"instruction":{"type":"StartAt","text":"Depart"}}]}"""
        }
        return HttpResponse(200, """{"type":"FeatureCollection","features":[{"geometry":{"type":"MultiLineString","coordinates":[$coordinates]},"properties":{"distance":${222 * (points.size - 1)},"time":${60 * (points.size - 1)},"legs":[$legs]}}]}""")
    }
}

/** Deliberately ignores cancellation like an HTTP response already being delivered by the OS. */
private class UninterruptibleHttpExecutor : AbstractExecutorService() {
    val tasks = mutableListOf<Runnable>()
    private var stopped = false
    fun runAt(index: Int) { tasks.removeAt(index).run() }
    override fun execute(command: Runnable) { tasks += command }
    override fun <T : Any?> newTaskFor(callable: Callable<T>): RunnableFuture<T> =
        object : FutureTask<T>(callable) { override fun cancel(mayInterruptIfRunning: Boolean) = false }
    override fun <T : Any?> newTaskFor(runnable: Runnable, value: T): RunnableFuture<T> =
        object : FutureTask<T>(runnable, value) { override fun cancel(mayInterruptIfRunning: Boolean) = false }
    override fun shutdown() { stopped = true }
    override fun shutdownNow(): MutableList<Runnable> { stopped = true; return tasks.toMutableList().also { tasks.clear() } }
    override fun isShutdown() = stopped
    override fun isTerminated() = stopped
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = stopped
}
