package br.com.gamemap.navigation.storage

import android.app.Application
import android.content.Context
import br.com.gamemap.navigation.MapOrientation
import br.com.gamemap.preferences.MapTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class NavigationSettingsTest {
    private lateinit var context: Context
    @Before fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(NavigationSettings.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Real AES-GCM, with an in-memory key because Robolectric has no AndroidKeyStore provider. */
    private class MemoryVault : KeyVault {
        var key: SecretKey? = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, checkNotNull(key))
            return Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
        }
        override fun decrypt(ciphertext: String): String {
            val pieces = ciphertext.split(':')
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, checkNotNull(key), GCMParameterSpec(128, Base64.getDecoder().decode(pieces[0])))
            return String(cipher.doFinal(Base64.getDecoder().decode(pieces[1])), Charsets.UTF_8)
        }
    }

    @Test fun preferencesAndUnknownVehicleChoiceSurviveRecreation() {
        val vault = MemoryVault()
        NavigationSettings(context, vault).use { settings ->
            settings.setVehicleId("unavailable-future-vehicle")
            settings.setSavedPlacesVisible(false)
            settings.setOrientation(MapOrientation.NORTH_UP)
            val catalog = IconCatalogRepository(context)
            assertEquals(catalog.vehicleIcon("arrow", MapTheme.ROSA_POP),
                catalog.vehicleIcon(settings.snapshot().vehicleId, MapTheme.ROSA_POP))
        }
        NavigationSettings(context, vault).use { restored ->
            assertEquals("unavailable-future-vehicle", restored.snapshot().vehicleId)
            assertFalse(restored.snapshot().savedPlacesVisible)
            assertEquals(MapOrientation.NORTH_UP, restored.snapshot().orientation)
        }
    }

    @Test fun apiKeyIsStoredAsAuthenticatedCiphertextAndBecomesUnconfiguredWhenVaultIsLost() {
        val vault = MemoryVault()
        val value = "sample-test-token-not-a-real-credential"
        NavigationSettings(context, vault).use { settings ->
            settings.setApiKey(value)
            assertEquals(value, settings.apiKey())
            val storage = context.getSharedPreferences(NavigationSettings.FILE_NAME, Context.MODE_PRIVATE)
            assertFalse(storage.all.values.any { it.toString().contains(value) })
        }
        NavigationSettings(context, vault).use { restored -> assertEquals(value, restored.apiKey()) }
        vault.key = null
        NavigationSettings(context, vault).use { restored ->
            assertEquals("", restored.apiKey())
            restored.setApiKey("")
            assertEquals("", restored.apiKey())
        }
    }
}
