package az.payreport.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Həssas məlumatlar (mail şifrəsi, API token, baza açarı, PIN heşi) üçün saxlama.
 * Dəyərlər Android Keystore-da saxlanılan, cihazdan çıxarıla bilməyən AES-256-GCM açarı
 * ilə şifrələnir və yalnız şifrələnmiş şəkildə SharedPreferences-ə yazılır.
 */
class SecureStore(context: Context) {

    private val prefs = context.getSharedPreferences("secure_store", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(payload: String): ByteArray {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }

    @Synchronized
    fun putString(name: String, value: String?) {
        if (value.isNullOrEmpty()) prefs.edit().remove(name).apply()
        else prefs.edit().putString(name, encrypt(value.toByteArray(Charsets.UTF_8))).apply()
    }

    @Synchronized
    fun getString(name: String): String? {
        val v = prefs.getString(name, null) ?: return null
        return try {
            String(decrypt(v), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun has(name: String) = prefs.contains(name)

    /** SQLCipher üçün 32 baytlıq təsadüfi açar; ilk dəfə yaradılır. */
    @Synchronized
    fun databasePassphrase(): ByteArray {
        prefs.getString(DB_KEY, null)?.let { return decrypt(it) }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(DB_KEY, encrypt(bytes)).commit()
        return bytes
    }

    // ------------------------------------------------------------------ PIN
    fun setPin(pin: String?) {
        if (pin.isNullOrEmpty()) {
            putString(PIN_HASH, null)
            return
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        putString(PIN_HASH, Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(pbkdf2(pin, salt), Base64.NO_WRAP))
    }

    fun hasPin() = has(PIN_HASH)

    fun verifyPin(pin: String): Boolean {
        val stored = getString(PIN_HASH) ?: return true
        val (salt, hash) = stored.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        return MessageDigest.isEqual(hash, pbkdf2(pin, salt))
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "payreport_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DB_KEY = "db_passphrase"
        private const val PIN_HASH = "admin_pin"
        const val MAIL_PASSWORD = "mail_password"
        const val API_TOKEN = "api_token"
    }
}
