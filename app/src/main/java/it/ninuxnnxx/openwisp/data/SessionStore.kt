package it.ninuxnnxx.openwisp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Session(val server: HttpUrl, val username: String, val token: String)

/**
 * Server, utente e token. Il token e' cifrato con AES-GCM e una chiave del
 * Keystore di Android (non esportabile); la password non viene mai salvata.
 * Il file delle preferenze e' escluso dal backup (res/xml/backup_rules.xml).
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun load(): Session? {
        val server = prefs.getString(KEY_SERVER, null)?.toHttpUrlOrNull() ?: return null
        val username = prefs.getString(KEY_USER, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null)?.let(::decrypt) ?: return null
        return Session(server, username, token)
    }

    fun save(session: Session) = prefs.edit {
        putString(KEY_SERVER, session.server.toString())
        putString(KEY_USER, session.username)
        putString(KEY_TOKEN, encrypt(session.token))
    }

    /** Dimentica il token sul telefono; sul server resta valido (vedi README). */
    fun clear() = prefs.edit {
        remove(KEY_TOKEN)
        remove(KEY_LAST_NOTIFICATION)
    }

    /** Server e utente dell'ultimo accesso, per precompilare il login. */
    fun lastServer(): String? = prefs.getString(KEY_SERVER, null)
    fun lastUsername(): String? = prefs.getString(KEY_USER, null)

    var appLock: Boolean
        get() = prefs.getBoolean(KEY_LOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_LOCK, value) }

    var notifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFY, value) }

    /** Timestamp ISO dell'ultima notifica gia' mostrata. */
    var lastNotification: String?
        get() = prefs.getString(KEY_LAST_NOTIFICATION, null)
        set(value) = prefs.edit { putString(KEY_LAST_NOTIFICATION, value) }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** null se il dato e' rovinato o la chiave non c'e' piu' (es. app ripristinata). */
    private fun decrypt(encoded: String): String? = runCatching {
        val data = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        String(cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "openwisp_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12

        const val KEY_SERVER = "server"
        const val KEY_USER = "username"
        const val KEY_TOKEN = "token"
        const val KEY_LOCK = "app_lock"
        const val KEY_NOTIFY = "notifications"
        const val KEY_LAST_NOTIFICATION = "last_notification"
    }
}
