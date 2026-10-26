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

private const val SECURE_PREFS = "entre_noses_secure_data"
private const val LEGACY_PREFS = "entre_noses_local_data"
private const val SESSION_KEY = "auth_session"

internal fun securePreferences(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    val secure = EncryptedSharedPreferences.create(
        context.applicationContext,
        SECURE_PREFS,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    if (legacy.all.isNotEmpty() && secure.getBoolean("migration_v1_complete", false).not()) {
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
        editor.putBoolean("migration_v1_complete", true)
        check(editor.commit()) { "Não foi possível proteger os dados locais." }
        legacy.edit().clear().commit()
    } else if (!secure.contains("migration_v1_complete")) {
        secure.edit().putBoolean("migration_v1_complete", true).apply()
    }
    return secure
}

internal data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String
)

internal object CloudAuth {
    private fun configured() = BuildConfig.SUPABASE_URL.startsWith("https://") &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    fun isConfigured() = configured()

    fun savedSession(context: Context): AuthSession? = runCatching {
        val raw = securePreferences(context).getString(SESSION_KEY, null) ?: return null
        val json = JSONObject(raw)
        AuthSession(
            json.getString("access_token"),
            json.optString("refresh_token"),
            json.getJSONObject("user").getString("id"),
            json.getJSONObject("user").optString("email")
        )
    }.getOrNull()

    private fun saveSession(context: Context, session: AuthSession) {
        val json = JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("user", JSONObject().put("id", session.userId).put("email", session.email))
        check(securePreferences(context).edit().putString(SESSION_KEY, json.toString()).commit()) {
            "Não foi possível salvar a sessão com segurança."
        }
    }

    fun signOut(context: Context) {
        securePreferences(context).edit().remove(SESSION_KEY).commit()
    }

    suspend fun signIn(context: Context, email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val json = request(
                "/auth/v1/token?grant_type=password",
                "POST",
                JSONObject().put("email", email.trim()).put("password", password)
            )
            val session = parseSession(json)
            saveSession(context, session)
            session
        }

    suspend fun signUp(context: Context, email: String, password: String): AuthSession? =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val json = request(
                "/auth/v1/signup",
                "POST",
                JSONObject().put("email", email.trim()).put("password", password)
            )
            val token = json.optString("access_token")
            val user = json.optJSONObject("user") ?: json
            if (token.isBlank() || user.optString("id").isBlank()) null else {
                val session = parseSession(json)
                saveSession(context, session)
                session
            }
        }

    suspend fun recoverPassword(email: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        request(
            "/auth/v1/recover?email=" + java.net.URLEncoder.encode(email.trim(), "UTF-8"),
            "POST",
            JSONObject()
        )
        Unit
    }

    private fun requireConfigured() {
        check(configured()) {
            "A conexão segura ainda não foi configurada. O login e a sincronização estão desativados até o servidor ser ligado."
        }
    }

    private fun parseSession(json: JSONObject): AuthSession {
        val user = json.optJSONObject("user") ?: throw IllegalStateException("O servidor não retornou os dados da conta.")
        val access = json.optString("access_token")
        if (access.isBlank() || user.optString("id").isBlank()) {
            throw IllegalStateException("A conta foi criada, mas falta confirmar o e-mail. Confira sua caixa de entrada.")
        }
        return AuthSession(access, json.optString("refresh_token"), user.getString("id"), user.optString("email"))
    }

    private fun request(path: String, method: String, body: JSONObject): JSONObject {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
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
                val detail = runCatching { JSONObject(response).optString("msg").ifBlank { JSONObject(response).optString("message") } }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() } ?: "Falha no servidor (HTTP $status).")
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun AuthGate(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var session by remember { mutableStateOf(CloudAuth.savedSession(context)) }
    var mode by remember { mutableStateOf("entrar") }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (session != null) {
        content()
        return
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFB69CFF), secondary = Color(0xFFDFCFFF),
            background = Color(0xFF100D18), surface = Color(0xFF1D1728),
            onPrimary = Color(0xFF100D18), onBackground = Color(0xFFF8F5FF),
            onSurface = Color(0xFFF8F5FF)
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF302046), Color(0xFF100D18)))
            ).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(24.dp)).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("ENTRE NOSES", color = Color(0xFFB8AEC9), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Noses", color = Color(0xFFF8F5FF), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    when (mode) {
                        "criar" -> "Crie sua conta individual"
                        "recuperar" -> "Recupere o acesso por e-mail"
                        else -> "Entre na sua conta"
                    },
                    color = Color(0xFFB69CFF), fontWeight = FontWeight.SemiBold
                )
                if (!CloudAuth.isConfigured()) {
                    Text(
                        "A autenticação ainda não está ligada a um servidor. Por segurança, o aplicativo não aceita senhas até que a configuração real seja adicionada.",
                        color = Color(0xFFFFA8B5), fontSize = 13.sp
                    )
                }
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, enabled = !busy
                )
                if (mode != "recuperar") {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Senha (mínimo 8 caracteres)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy
                    )
                }
                if (mode == "criar") {
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Repita a senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, enabled = !busy
                    )
                }
                if (message.isNotBlank()) Text(message, color = if (message.startsWith("Conta criada")) Color(0xFF9EE6BD) else Color(0xFFFFA8B5), fontSize = 13.sp)
                Button(
                    onClick = {
                        message = ""
                        if (!CloudAuth.isConfigured()) {
                            message = "Falta configurar o servidor seguro. Nenhuma senha foi enviada."
                        } else if (!email.contains("@")) {
                            message = "Digite um e-mail válido."
                        } else if (mode != "recuperar" && password.length < 8) {
                            message = "Use uma senha com pelo menos 8 caracteres."
                        } else if (mode == "criar" && password != confirmPassword) {
                            message = "As senhas não são iguais."
                        } else {
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
                                            message = "Se esse e-mail estiver cadastrado, você receberá instruções de recuperação."
                                        }
                                        else -> {
                                            session = CloudAuth.signIn(context, email, password)
                                        }
                                    }
                                } catch (e: Exception) {
                                    message = e.message ?: "Não foi possível concluir. Tente novamente."
                                } finally {
                                    busy = false
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mode = if (mode == "entrar") "criar" else "entrar"; message = "" }, enabled = !busy) {
                        Text(if (mode == "criar") "Já tenho conta" else "Criar conta")
                    }
                    if (mode != "recuperar") {
                        TextButton(onClick = { mode = "recuperar"; message = "" }, enabled = !busy) { Text("Esqueci a senha") }
                    } else {
                        TextButton(onClick = { mode = "entrar"; message = "" }, enabled = !busy) { Text("Voltar") }
                    }
                }
            }
        }
    }
}
