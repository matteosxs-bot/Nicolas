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
import java.net.URLEncoder

private const val DATA_PREFS_PREFIX = "entre_noses_secure_data_"
private const val AUTH_PREFS = "entre_noses_auth_secure"
private const val LEGACY_PREFS = "entre_noses_local_data"
private const val SESSION_KEY = "auth_session"

private fun encryptedPreferences(context: Context, name: String): SharedPreferences {
    val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    return EncryptedSharedPreferences.create(
        context.applicationContext, name, masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

private fun authPreferences(context: Context) = encryptedPreferences(context, AUTH_PREFS)

internal data class AuthSession(val accessToken: String, val refreshToken: String, val userId: String, val email: String)

internal fun securePreferences(context: Context): SharedPreferences {
    val userId = CloudAuth.savedSession(context)?.userId ?: "local"
    val safeId = userId.replace(Regex("[^A-Za-z0-9_-]"), "_")
    val secure = encryptedPreferences(context, DATA_PREFS_PREFIX + safeId)
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

internal object CloudAuth {
    fun isConfigured(): Boolean =
        BuildConfig.SUPABASE_URL.startsWith("https://") && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun savedSession(context: Context): AuthSession? = runCatching {
        val raw = authPreferences(context).getString(SESSION_KEY, null) ?: return null
        val json = JSONObject(raw)
        val user = json.getJSONObject("user")
        AuthSession(json.optString("access_token"), json.optString("refresh_token"), user.getString("id"), user.optString("email"))
    }.getOrNull()

    private fun saveSession(context: Context, session: AuthSession) {
        val json = JSONObject().put("access_token", session.accessToken).put("refresh_token", session.refreshToken)
            .put("user", JSONObject().put("id", session.userId).put("email", session.email))
        check(authPreferences(context).edit().putString(SESSION_KEY, json.toString()).commit()) {
            "Não foi possível salvar a sessão com segurança."
        }
    }

    fun signOut(context: Context) {
        authPreferences(context).edit().remove(SESSION_KEY).commit()
    }

    suspend fun signIn(context: Context, email: String, password: String): AuthSession = withContext(Dispatchers.IO) {
        requireConfigured()
        val json = request("/auth/v1/token?grant_type=password", "POST",
            JSONObject().put("email", email.trim()).put("password", password))
        parseAndSave(context, json)
    }

    suspend fun signUp(context: Context, email: String, password: String): AuthSession? = withContext(Dispatchers.IO) {
        requireConfigured()
        val json = request("/auth/v1/signup", "POST",
            JSONObject().put("email", email.trim()).put("password", password))
        val user = json.optJSONObject("user") ?: json
        if (json.optString("access_token").isBlank() || user.optString("id").isBlank()) null
        else parseAndSave(context, json)
    }

    suspend fun recoverPassword(email: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        request("/auth/v1/recover?email=" + URLEncoder.encode(email.trim(), "UTF-8"), "POST", JSONObject())
        Unit
    }

    private fun parseAndSave(context: Context, json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: throw IllegalStateException("O servidor não retornou os dados da conta.")
        val token = json.optString("access_token")
        if (token.isBlank() || user.optString("id").isBlank()) {
            throw IllegalStateException("Confirme seu e-mail e depois entre na conta.")
        }
        val session = AuthSession(token, json.optString("refresh_token"), user.getString("id"), user.optString("email"))
        saveSession(context, session)
        return session
    }

    private fun requireConfigured() {
        check(isConfigured()) { "O servidor seguro ainda não está configurado. Nenhuma senha foi enviada." }
    }

    private fun request(path: String, method: String, body: JSONObject): JSONObject {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val parsed = runCatching {
                    val error = JSONObject(response)
                    error.optString("msg").ifBlank { error.optString("message") }.ifBlank { error.optString("error_description") }
                }.getOrNull()
                throw IllegalStateException(parsed?.takeIf { it.isNotBlank() } ?: "Erro do servidor (HTTP $status).")
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun AuthGate(content: @Composable (onSignOut: () -> Unit) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var session by remember { mutableStateOf(CloudAuth.savedSession(context)) }
    var localMode by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("entrar") }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (session != null || localMode) {
        content {
            if (session != null) CloudAuth.signOut(context)
            session = null
            localMode = false
        }
        return
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB69CFF), secondary = Color(0xFFDFCFFF),
        background = Color(0xFF100D18), surface = Color(0xFF1D1728),
        onPrimary = Color(0xFF100D18), onBackground = Color(0xFFF8F5FF), onSurface = Color(0xFFF8F5FF)
    )) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF302046), Color(0xFF100D18)))).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(24.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                Text("ENTRE NOSES", color = Color(0xFFB8AEC9), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Noses", color = Color(0xFFF8F5FF), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(when (mode) { "criar" -> "Crie sua conta individual"; "recuperar" -> "Recupere o acesso por e-mail"; else -> "Entre na sua conta" },
                    color = Color(0xFFB69CFF), fontWeight = FontWeight.SemiBold)
                if (!CloudAuth.isConfigured()) {
                    Text("O servidor de autenticação ainda não foi conectado. Por segurança, o app não envia nem aceita senhas até a configuração real.",
                        color = Color(0xFFFFA8B5), fontSize = 13.sp)
                }
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, enabled = !busy)
                if (mode != "recuperar") {
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Senha (mínimo 8 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy)
                }
                if (mode == "criar") {
                    OutlinedTextField(confirmation, { confirmation = it }, Modifier.fillMaxWidth(), label = { Text("Repita a senha") },
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy)
                }
                if (message.isNotBlank()) Text(message, color = if (message.startsWith("Conta criada")) Color(0xFF9EE6BD) else Color(0xFFFFA8B5), fontSize = 13.sp)
                Button(
                    onClick = {
                        message = ""
                        when {
                            !CloudAuth.isConfigured() -> message = "Falta ligar o servidor seguro. Nenhuma senha foi enviada."
                            !email.contains("@") -> message = "Digite um e-mail válido."
                            mode != "recuperar" && password.length < 8 -> message = "Use uma senha com pelo menos 8 caracteres."
                            mode == "criar" && password != confirmation -> message = "As senhas não são iguais."
                            else -> {
                                busy = true
                                scope.launch {
                                    try {
                                        when (mode) {
                                            "criar" -> {
                                                val created = CloudAuth.signUp(context, email, password)
                                                session = created
                                                message = if (created == null) "Conta criada. Confirme o e-mail antes de entrar." else ""
                                            }
                                            "recuperar" -> {
                                                CloudAuth.recoverPassword(email)
                                                message = "Se esse e-mail estiver cadastrado, você receberá instruções."
                                            }
                                            else -> session = CloudAuth.signIn(context, email, password)
                                        }
                                    } catch (e: Exception) {
                                        message = e.message ?: "Não foi possível concluir. Tente novamente."
                                    } finally { busy = false }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !busy && CloudAuth.isConfigured()
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(when (mode) { "criar" -> "Criar conta"; "recuperar" -> "Enviar recuperação"; else -> "Entrar" })
                }
                if (!CloudAuth.isConfigured()) {
                    OutlinedButton(
                        onClick = { localMode = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Continuar sem conta neste aparelho") }
                    Text("O modo local funciona sem internet. A sincronização e o compartilhamento só funcionarão depois que o servidor for conectado.",
                        color = Color(0xFFB8AEC9), fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mode = if (mode == "entrar") "criar" else "entrar"; message = "" }, enabled = !busy) {
                        Text(if (mode == "criar") "Já tenho conta" else "Criar conta")
                    }
                    TextButton(onClick = { mode = if (mode == "recuperar") "entrar" else "recuperar"; message = "" }, enabled = !busy) {
                        Text(if (mode == "recuperar") "Voltar" else "Esqueci a senha")
                    }
                }
            }
        }
    }
}
