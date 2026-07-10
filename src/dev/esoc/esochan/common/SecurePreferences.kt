package dev.esoc.esochan.common

import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.io.IOException
import java.security.GeneralSecurityException

object SecurePreferences {

    private const val FILE_NAME = "secure_prefs"

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        val context = MainApplication.getInstance()
        return try {
            buildEncryptedPrefs(context)
        } catch (e: GeneralSecurityException) {
            recoverFromUnusableKey(context, e)
        } catch (e: IOException) {
            recoverFromUnusableKey(context, e)
        }
    }

    private fun recoverFromUnusableKey(
        context: android.content.Context,
        cause: Exception
    ): SharedPreferences {
        Logger.e("SecurePreferences", "Encrypted preferences could not be opened", cause)
        if (!context.deleteSharedPreferences(FILE_NAME)) {
            throw IllegalStateException("Could not reset unusable encrypted preferences", cause)
        }
        return buildEncryptedPrefs(context)
    }

    private fun buildEncryptedPrefs(context: android.content.Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun get(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun migrateFromPlain(plainPrefs: SharedPreferences, key: String) {
        if (plainPrefs.contains(key) && !prefs.contains(key)) {
            val value = plainPrefs.getString(key, "") ?: ""
            prefs.edit().putString(key, value).apply()
            plainPrefs.edit().remove(key).apply()
        }
    }
}
