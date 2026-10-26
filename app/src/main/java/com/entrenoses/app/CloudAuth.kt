package com.entrenoses.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SECURE_PREFS = "entre_noses_secure_data_"\nprivate const val AUTH_PREFS = "entre_noses_auth_secure"
private const val LEGACY_PREFS = "entre_noses_local_data"
private const val SESSION_KEY = "auth_session"

private fun encryptedPreferences(context: Context, name: String): SharedPreferences {
    val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context.applicationContext, name, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun authPreferences(context: Context) = encryptedPreferences(context, AUTH_PREFS)

internal fun securePreferences(context: Context): SharedPreferences {
    val userId = CloudAuth.savedSession(context)?.userId ?: "local"
    val safeId = userId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val secure = encryptedPreferences(context, SECURE_PREFS + safeId)
    val authPrefs = authPreferences(context)
    val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    if (!authPrefs.getBoolean("migration_v1_complete", false)) {
        if (legacy.all.isNotEmpty()) {
            val editor = secure.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Não foi possível proteger os dados locais." }
            check(legacy.edit().clear().commit()) { "Não foi possível concluir a migração segura." }
        }
        check(authPrefs.edit().putBoolean("migration_v1_complete", true).commit())
    }
    return secure
}
