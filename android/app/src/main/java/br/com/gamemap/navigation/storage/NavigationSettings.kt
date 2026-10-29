package br.com.gamemap.navigation.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.navigation.NavigationPreferences
import java.util.concurrent.CopyOnWriteArraySet

class NavigationSettings(
    context: Context,
    private val keyVault: KeyVault = AndroidKeyStoreKeyVault(),
) : AutoCloseable {
    private val storage = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(NavigationPreferences) -> Unit>()
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        onMain { val current = snapshot(); listeners.forEach { it(current) } }
    }
    init { storage.registerOnSharedPreferenceChangeListener(changed) }

    fun snapshot() = NavigationPreferences(
        vehicleId = storage.getString(VEHICLE_ID, "arrow").orEmpty().ifBlank { "arrow" },
        savedPlacesVisible = storage.getBoolean(SAVED_VISIBLE, true),
        orientation = MapOrientation.entries.firstOrNull { it.name == storage.getString(ORIENTATION, null) }
            ?: MapOrientation.MOVEMENT_UP,
    )

    /** A missing/reset keystore or failed authentication is a configuration loss, never a usable key. */
    fun apiKey(): String {
        val ciphertext = storage.getString(API_CIPHERTEXT, null) ?: return ""
        return try { keyVault.decrypt(ciphertext) } catch (_: Exception) { "" }
    }

    fun setApiKey(value: String) {
        val plaintext = value.trim()
        val editor = storage.edit()
        if (plaintext.isEmpty()) editor.remove(API_CIPHERTEXT)
        else editor.putString(API_CIPHERTEXT, keyVault.encrypt(plaintext))
        check(editor.commit()) { "Não foi possível salvar a configuração do serviço" }
    }

    /** Traffic has its own credential and is opt-in, independently of route search. */
    fun trafficApiKey(): String {
        val ciphertext = storage.getString(TRAFFIC_CIPHERTEXT, null) ?: return ""
        return try { keyVault.decrypt(ciphertext) } catch (_: Exception) { "" }
    }

    fun trafficEnabled(): Boolean = storage.getBoolean(TRAFFIC_ENABLED, false) && trafficApiKey().isNotBlank()

    fun setTrafficApiKey(value: String) {
        val key = value.trim()
        require(key.length <= 512 && key.all { it.code in 33..126 }) { "Chave de trânsito inválida" }
        val editor = storage.edit()
        if (key.isEmpty()) editor.remove(TRAFFIC_CIPHERTEXT).putBoolean(TRAFFIC_ENABLED, false)
        else editor.putString(TRAFFIC_CIPHERTEXT, keyVault.encrypt(key))
        commit(editor)
    }

    fun setTrafficEnabled(enabled: Boolean) = commit(storage.edit()
        .putBoolean(TRAFFIC_ENABLED, enabled && trafficApiKey().isNotBlank()))

    fun setVehicleId(id: String) {
        require(id.isNotBlank()) { "Escolha um marcador de veículo" }
        commit(storage.edit().putString(VEHICLE_ID, id))
    }
    fun setSavedPlacesVisible(visible: Boolean) = commit(storage.edit().putBoolean(SAVED_VISIBLE, visible))
    fun setOrientation(orientation: MapOrientation) = commit(storage.edit().putString(ORIENTATION, orientation.name))

    fun addListener(listener: (NavigationPreferences) -> Unit): () -> Unit {
        onMain { listeners.add(listener); listener(snapshot()) }
        return { onMain { listeners.remove(listener) } }
    }

    override fun close() {
        storage.unregisterOnSharedPreferenceChangeListener(changed)
        listeners.clear()
    }

    private fun commit(editor: SharedPreferences.Editor) {
        check(editor.commit()) { "Não foi possível salvar as preferências de navegação" }
    }
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    companion object {
        const val FILE_NAME = "gamemap_navigation_preferences_v1"
        private const val VEHICLE_ID = "vehicle_id"
        private const val SAVED_VISIBLE = "saved_places_visible"
        private const val ORIENTATION = "orientation"
        private const val API_CIPHERTEXT = "provider_api_ciphertext"
        private const val TRAFFIC_CIPHERTEXT = "traffic_api_ciphertext"
        private const val TRAFFIC_ENABLED = "traffic_enabled"
    }
}
