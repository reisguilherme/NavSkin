package br.com.gamemap.navigation.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Injectable cryptographic boundary; implementations never persist plaintext. */
interface KeyVault {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}

/** AES key material remains non-exportable in AndroidKeyStore. */
class AndroidKeyStoreKeyVault(private val alias: String = "gamemap_geoapify_aes_v1") : KeyVault {
    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized private fun encryptionKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encoder = Base64.getEncoder()
        return "v1:${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))}"
    }

    override fun decrypt(ciphertext: String): String {
        val parts = ciphertext.split(':')
        require(parts.size == 3 && parts[0] == "v1") { "Formato de credencial inválido" }
        // Decryption must never create a replacement key for an existing ciphertext.
        val key = keyStore().getKey(alias, null) as? SecretKey
            ?: throw UnrecoverableKeyException("Credencial indisponível neste dispositivo")
        val decoder = Base64.getDecoder()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, decoder.decode(parts[1])))
        return String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
    }
}
