package br.com.gamemap.navigation.storage

import android.app.Application
import android.content.Context
import br.com.gamemap.navigation.model.Destination
import br.com.gamemap.navigation.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], shadows = [PosixAtomicFileShadow::class])
class LocalPlacesStoreTest {
    private lateinit var context: Context
    private lateinit var file: File
    private var time = 1000L

    @Before fun prepare() {
        context = RuntimeEnvironment.getApplication()
        file = File(context.cacheDir, "places-${UUID.randomUUID()}.json")
        PosixAtomicFileShadow.failNextRename = false
    }

    private fun store() = LocalPlacesStore(context, clock = { time }, file = file)

    @Test fun resumeItineraryRoundTripsInOrderAndClearsTogetherWithTheFinalDestination() {
        val places = store()
        val final = Destination("final", "Final", GeoPoint(-16.7, -49.2))
        val stops = listOf(Destination("one", "One", GeoPoint(-16.68, -49.2)),
            Destination("two", "Two", GeoPoint(-16.69, -49.2)))
        places.setResumeDestination(final, stops)
        assertEquals(final, store().snapshot().resumeDestination)
        assertEquals(stops, store().snapshot().resumeIntermediateStops)
        places.setResumeDestination(null)
        assertNull(store().snapshot().resumeDestination)
        assertTrue(store().snapshot().resumeIntermediateStops.isEmpty())
    }

    @Test fun editsKeepStableIdentityAndCreationDateAndDeleteClearsShortcuts() {
        val places = store()
        val created = places.savePlace("Minha casa", GeoPoint(-16.67, -49.25), "Rua Um", "personal", "home")
        places.setHome(created.id)
        places.setWork(created.id)
        time = 9000L
        val edited = places.savePlace("Casa nova", GeoPoint(-16.68, -49.26), iconId = "future-icon", id = created.id)
        assertEquals(created.id, edited.id)
        assertEquals(1000L, edited.createdAtMs)
        assertEquals(9000L, edited.updatedAtMs)
        val restored = store().snapshot()
        assertEquals(listOf(edited), restored.savedPlaces)
        assertEquals(edited.id, restored.homeId)
        assertEquals(edited.id, restored.workId)
        assertEquals("future-icon", restored.savedPlaces.single().iconId)
        assertEquals(edited.point, edited.asDestination().point)
        val reopened = store()
        reopened.deletePlace(edited.id)
        assertTrue(store().snapshot().savedPlaces.isEmpty())
        assertNull(store().snapshot().homeId)
        assertNull(store().snapshot().workId)
    }

    @Test fun recentDestinationsAreDeduplicatedByIdOrNearbyNameAndCappedAtTwenty() {
        val places = store()
        val destination = Destination("one", "Café", GeoPoint(-16.67, -49.25), "Centro")
        places.rememberRecent(destination)
        time++
        places.rememberRecent(destination.copy(id = "other-provider-id", point = GeoPoint(-16.67001, -49.25001)))
        assertEquals(1, places.snapshot().recents.size)
        assertEquals(time, places.snapshot().recents.single().usedAtMs)
        repeat(25) { index ->
            time++
            places.rememberRecent(Destination("id-$index", "Destino $index", GeoPoint(-16.0 + index * .01, -49.0)))
        }
        val restored = store().snapshot()
        assertEquals(20, restored.recents.size)
        assertEquals("id-24", restored.recents.first().destination.id)
        assertEquals("id-5", restored.recents.last().destination.id)
    }

    @Test fun restorationPersistsOnlyTheDestinationAndNotGuidanceOrPositionHistory() {
        val places = store()
        val destination = Destination("destination", "Parque", GeoPoint(-16.7, -49.2), "Goiânia")
        places.setResumeDestination(destination)
        assertEquals(destination, store().snapshot().resumeDestination)
        val stored = JSONObject(file.readText())
        assertFalse(stored.has("route"))
        assertFalse(stored.has("progress"))
        assertFalse(stored.has("locationHistory"))
        places.setResumeDestination(null)
        assertNull(store().snapshot().resumeDestination)
    }

    @Test fun malformedFavoritesArePreservedAndBlockWritesInsteadOfBeingDiscarded() {
        val broken = "{\"schemaVersion\":1,\"savedPlaces\":[{\"id\":\"must-survive\"}]}"
        file.writeText(broken)
        val places = store()
        assertNotNull(places.snapshot().loadError)
        try {
            places.savePlace("Outro", GeoPoint(-16.7, -49.2))
            fail("A corrupt source must not be replaced")
        } catch (_: LocalPlacesStorageException) { }
        assertEquals(broken, file.readText())
    }

    @Test fun versionZeroMigrationPreservesIdentityCoordinatesAndUnknownIconPreference() {
        file.writeText(JSONObject().put("schemaVersion", 0).put("savedPlaces", JSONArray().put(
            JSONObject().put("id", "legacy-home").put("name", "Casa")
                .put("latitude", -16.67).put("longitude", -49.25)
                .put("iconId", "unavailable-old-theme-icon")
        )).put("homeId", "legacy-home").toString())
        val places = store()
        assertNull(places.snapshot().loadError)
        val restored = places.snapshot().savedPlaces.single()
        assertEquals("legacy-home", restored.id)
        assertEquals(GeoPoint(-16.67, -49.25), restored.point)
        assertEquals("unavailable-old-theme-icon", restored.iconId)
        places.setWork(restored.id)
        assertEquals(2, JSONObject(file.readText()).getInt("schemaVersion"))
        assertEquals("legacy-home", store().snapshot().homeId)
        assertEquals("legacy-home", store().snapshot().workId)
    }

    @Test fun listenerGetsCommittedStateAndCanBeRemoved() {
        val places = store()
        val sizes = mutableListOf<Int>()
        val remove = places.addListener { sizes.add(it.savedPlaces.size) }
        places.savePlace("Casa", GeoPoint(-16.7, -49.2))
        remove()
        places.savePlace("Trabalho", GeoPoint(-16.8, -49.3))
        assertEquals(listOf(0, 1), sizes)
    }

    @Test fun malformedOptionalStateIsNotSilentlyReplacedWithEmptyValues() {
        val malformed = JSONObject().put("schemaVersion", 1).put("savedPlaces", JSONArray())
            .put("recents", "corrupt-array").toString()
        file.writeText(malformed)
        val places = store()
        assertNotNull(places.snapshot().loadError)
        try {
            places.setResumeDestination(null)
            fail("Malformed recent destinations must remain recoverable")
        } catch (_: LocalPlacesStorageException) { }
        assertEquals(malformed, file.readText())
    }

    @Test fun failedAtomicReplacementDoesNotPublishAnUncommittedEdit() {
        val places = store()
        val original = places.savePlace("Casa", GeoPoint(-16.7, -49.2))
        val committed = file.readBytes()
        val notifications = mutableListOf<LocalPlacesState>()
        val remove = places.addListener { notifications += it }
        PosixAtomicFileShadow.failNextRename = true
        try {
            places.savePlace("Nome que não foi salvo", original.point, id = original.id)
            fail("AtomicFile's silent rename failure must be reported to the caller")
        } catch (_: LocalPlacesStorageException) { }
        assertEquals(listOf(original), places.snapshot().savedPlaces)
        assertEquals(1, notifications.size)
        assertArrayEquals(committed, file.readBytes())
        assertEquals(listOf(original), store().snapshot().savedPlaces)
        remove()
    }
}
