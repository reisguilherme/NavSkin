package br.com.gamemap.navigation.storage

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AtomicFile
import br.com.gamemap.navigation.model.Destination
import br.com.gamemap.navigation.model.GeoPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SavedPlace(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val address: String,
    val categoryId: String?,
    val iconId: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    fun asDestination() = Destination(id, name, point, address)
}

data class RecentDestination(val destination: Destination, val usedAtMs: Long)

data class LocalPlacesState(
    val savedPlaces: List<SavedPlace> = emptyList(),
    val recents: List<RecentDestination> = emptyList(),
    val homeId: String? = null,
    val workId: String? = null,
    val resumeDestination: Destination? = null,
    val loadError: String? = null,
)

class LocalPlacesStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** Atomic local favorites and explicit destination history; never records GPS fixes or routes. */
class LocalPlacesStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val file: File = File(context.applicationContext.filesDir, "navigation-places.json"),
) {
    private val atomic = AtomicFile(file)
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(LocalPlacesState) -> Unit>()
    @Volatile private var state = readState()

    fun snapshot(): LocalPlacesState = state

    fun savePlace(
        name: String,
        point: GeoPoint,
        address: String = "",
        categoryId: String? = null,
        iconId: String = "special",
        id: String? = null,
    ): SavedPlace {
        require(name.isNotBlank()) { "Informe um nome para o local" }
        validatePoint(point)
        require(iconId.isNotBlank()) { "Escolha um ícone para o local" }
        lateinit var saved: SavedPlace
        update { before ->
            val previous = id?.let { requested ->
                before.savedPlaces.firstOrNull { it.id == requested }
                    ?: throw IllegalArgumentException("O local a editar não existe")
            }
            val now = clock().coerceAtLeast(previous?.updatedAtMs ?: 0L)
            saved = SavedPlace(
                previous?.id ?: UUID.randomUUID().toString(), name.trim(), point, address.trim(),
                categoryId?.takeIf { it.isNotBlank() }, iconId,
                previous?.createdAtMs ?: now, now,
            )
            before.copy(savedPlaces = if (previous == null) before.savedPlaces + saved
                else before.savedPlaces.map { if (it.id == saved.id) saved else it })
        }
        return saved
    }

    fun deletePlace(id: String) = update { before ->
        before.copy(
            savedPlaces = before.savedPlaces.filterNot { it.id == id },
            homeId = before.homeId?.takeUnless { it == id },
            workId = before.workId?.takeUnless { it == id },
        )
    }

    fun setHome(id: String?) = update { before ->
        require(id == null || before.savedPlaces.any { it.id == id }) { "Escolha um local salvo para Casa" }
        before.copy(homeId = id)
    }

    fun setWork(id: String?) = update { before ->
        require(id == null || before.savedPlaces.any { it.id == id }) { "Escolha um local salvo para Trabalho" }
        before.copy(workId = id)
    }

    fun rememberRecent(destination: Destination) {
        validateDestination(destination)
        update { before ->
            val distinct = before.recents.filterNot { item ->
                item.destination.id == destination.id || (
                    item.destination.name.trim().lowercase(Locale.ROOT) == destination.name.trim().lowercase(Locale.ROOT)
                        && distanceMeters(item.destination.point, destination.point) <= 40.0
                    )
            }
            before.copy(recents = (listOf(RecentDestination(destination, clock().coerceAtLeast(0))) + distinct).take(MAX_RECENTS))
        }
    }

    fun setResumeDestination(destination: Destination?) {
        destination?.let(::validateDestination)
        update { it.copy(resumeDestination = destination) }
    }

    fun addListener(listener: (LocalPlacesState) -> Unit): () -> Unit {
        onMain { listeners.add(listener); listener(snapshot()) }
        return { onMain { listeners.remove(listener) } }
    }

    private fun update(transform: (LocalPlacesState) -> LocalPlacesState) {
        val next = synchronized(lock) {
            state.loadError?.let { throw LocalPlacesStorageException(it) }
            val candidate = transform(state)
            writeState(candidate)
            state = candidate
            candidate
        }
        onMain { listeners.forEach { it(next) } }
    }

    private fun readState(): LocalPlacesState {
        if (!file.exists() && !File(file.path + ".bak").exists()) return LocalPlacesState()
        return try {
            val source = atomic.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val version = source.optInt("schemaVersion", -1)
            require(version in 0..SCHEMA_VERSION) { "Unsupported saved-place schema" }
            val places = source.getJSONArray("savedPlaces").objects().map { item ->
                val point = if (version == 0 && !item.has("point")) GeoPoint(item.getDouble("latitude"), item.getDouble("longitude"))
                    else parsePoint(item.getJSONObject("point"))
                validatePoint(point)
                val created = if (version == 0) item.optLong("createdAtMs", 0) else item.getLong("createdAtMs")
                val updated = if (version == 0) item.optLong("updatedAtMs", created) else item.getLong("updatedAtMs")
                require(created >= 0 && updated >= created)
                SavedPlace(
                    item.getString("id").also { require(it.isNotBlank()) },
                    item.getString("name").also { require(it.isNotBlank()) }, point,
                    item.optString("address", ""), item.nullableString("categoryId"),
                    (if (version == 0) item.optString("iconId", "special") else item.getString("iconId"))
                        .also { require(it.isNotBlank()) }, created, updated,
                )
            }
            require(places.map { it.id }.distinct().size == places.size)
            val recentArray = if (!source.has("recents") || source.isNull("recents")) null else source.getJSONArray("recents")
            val recents = recentArray?.objects()?.map { item ->
                RecentDestination(parseDestination(item.getJSONObject("destination")), item.getLong("usedAtMs").also { require(it >= 0) })
            }.orEmpty().take(MAX_RECENTS)
            val homeId = source.nullableString("homeId")
            val workId = source.nullableString("workId")
            require(homeId == null || places.any { it.id == homeId })
            require(workId == null || places.any { it.id == workId })
            LocalPlacesState(
                places, recents, homeId, workId,
                if (!source.has("resumeDestination") || source.isNull("resumeDestination")) null
                    else parseDestination(source.getJSONObject("resumeDestination")),
            )
        } catch (_: Exception) {
            // The source is deliberately retained untouched. Every mutation is blocked,
            // so a fresh empty snapshot cannot silently replace recoverable favorites.
            LocalPlacesState(loadError = "Não foi possível ler os locais salvos. O arquivo original foi preservado; alterações estão bloqueadas até sua recuperação.")
        }
    }

    private fun writeState(value: LocalPlacesState) {
        val source = JSONObject().put("schemaVersion", SCHEMA_VERSION)
            .put("savedPlaces", JSONArray().also { array ->
                value.savedPlaces.forEach { item ->
                    array.put(JSONObject().put("id", item.id).put("name", item.name).put("point", pointJson(item.point))
                        .put("address", item.address).put("categoryId", item.categoryId ?: JSONObject.NULL)
                        .put("iconId", item.iconId).put("createdAtMs", item.createdAtMs).put("updatedAtMs", item.updatedAtMs))
                }
            }).put("recents", JSONArray().also { array ->
                value.recents.forEach { array.put(JSONObject().put("destination", destinationJson(it.destination)).put("usedAtMs", it.usedAtMs)) }
            }).put("homeId", value.homeId ?: JSONObject.NULL).put("workId", value.workId ?: JSONObject.NULL)
            .put("resumeDestination", value.resumeDestination?.let(::destinationJson) ?: JSONObject.NULL)
        var stream: java.io.FileOutputStream? = null
        val encoded = source.toString().toByteArray(Charsets.UTF_8)
        try {
            stream = atomic.startWrite()
            stream.write(encoded)
            atomic.finishWrite(stream)
            stream = null
            // AtomicFile logs some filesystem failures instead of throwing. Do not publish a
            // successful edit until the committed base file actually contains this transaction.
            check(file.readBytes().contentEquals(encoded)) { "Atomic commit did not replace the saved state" }
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw LocalPlacesStorageException("Não foi possível salvar os locais neste dispositivo", failure)
        }
    }

    private fun validateDestination(destination: Destination) {
        require(destination.id.isNotBlank() && destination.name.isNotBlank()) { "Destino inválido" }
        validatePoint(destination.point)
    }
    private fun validatePoint(point: GeoPoint) {
        require(point.latitude.isFinite() && point.longitude.isFinite()
            && point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0) { "Coordenadas inválidas" }
    }
    private fun parsePoint(json: JSONObject) = GeoPoint(json.getDouble("latitude"), json.getDouble("longitude")).also(::validatePoint)
    private fun pointJson(point: GeoPoint) = JSONObject().put("latitude", point.latitude).put("longitude", point.longitude)
    private fun parseDestination(json: JSONObject) = Destination(
        json.getString("id"), json.getString("name"), parsePoint(json.getJSONObject("point")), json.optString("address", ""),
    ).also(::validateDestination)
    private fun destinationJson(destination: Destination) = JSONObject().put("id", destination.id).put("name", destination.name)
        .put("point", pointJson(destination.point)).put("address", destination.address)
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val latitude = Math.toRadians(b.latitude - a.latitude)
        val longitude = Math.toRadians(b.longitude - a.longitude)
        val h = sin(latitude / 2) * sin(latitude / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(longitude / 2) * sin(longitude / 2)
        return 6_371_000.0 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_RECENTS = 20
    }
}
