package com.entrenoses.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class HouseholdInfo(val id: String, val inviteCode: String)

private object HouseholdCloud {
    private fun local(context: Context) = securePreferences(context)

    fun saved(context: Context) = HouseholdInfo(
        local(context).getString("household_id", "").orEmpty(),
        local(context).getString("household_invite_code", "").orEmpty()
    )

    suspend fun create(context: Context, name: String): HouseholdInfo = withContext(Dispatchers.IO) {
        val session = CloudAuth.savedSession(context) ?: error("Entre na sua conta primeiro.")
        val response = rpc(session.accessToken, "/rest/v1/rpc/create_household", JSONObject().put("household_name", name.trim()))
        val row = JSONArray(response).optJSONObject(0) ?: error("O servidor não retornou o código do espaço.")
        val info = HouseholdInfo(row.getString("household_id"), row.getString("invite_code"))
        local(context).edit().putString("household_id", info.id).putString("household_invite_code", info.inviteCode).apply()
        info
    }

    suspend fun join(context: Context, code: String): HouseholdInfo = withContext(Dispatchers.IO) {
        val session = CloudAuth.savedSession(context) ?: error("Entre na sua conta primeiro.")
        val response = rpc(session.accessToken, "/rest/v1/rpc/join_household", JSONObject().put("code", code.trim().uppercase()))
        val id = runCatching { JSONObject(response).optString("household_id") }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: response.trim().trim('"')
        if (id.isBlank()) error("O servidor não retornou o espaço compartilhado.")
        val info = HouseholdInfo(id, "")
        local(context).edit().putString("household_id", info.id).putString("household_invite_code", "").apply()
        info
    }

    private fun rpc(token: String, path: String, body: JSONObject): String {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message").ifBlank { JSONObject(response).optString("details") } }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "Não foi possível acessar o espaço (HTTP $status).")
            }
            return response
        } finally { connection.disconnect() }
    }
}

@Composable
internal fun HouseholdPage(modifier: Modifier) {
    val context = LocalContext.current
    val initial = remember { HouseholdCloud.saved(context) }
    var householdId by remember { mutableStateOf(initial.id) }
    var inviteCode by remember { mutableStateOf(initial.inviteCode) }
    var householdName by remember { mutableStateOf("Nosso lar") }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Espaço do casal", color = Color(0xFFF8F5FF), fontSize = 25.sp)
        Text("O espaço usa um código de convite para limitar o acesso aos dois membros.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        if (householdId.isNotBlank()) {
            Column(Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(16.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Espaço conectado", color = Color(0xFF9EE6BD))
                Text("ID: $householdId", color = Color(0xFFF8F5FF), fontSize = 12.sp)
                if (inviteCode.isNotBlank()) {
                    Text("Código para convidar o Nicolas", color = Color(0xFFB8AEC9))
                    Text(inviteCode, color = Color(0xFFB69CFF), fontSize = 24.sp)
                    Button(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Convite Entre Noses", inviteCode))
                        message = "Código copiado."
                    }) { Text("Copiar código") }
                } else {
                    Text("Este aparelho entrou em um espaço existente. O código de convite só é mostrado ao criador.", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                }
            }
        } else {
            Text("Você ainda não está conectado a um espaço do casal.", color = Color(0xFFB8AEC9))
            OutlinedTextField(householdName, { householdName = it }, Modifier.fillMaxWidth(), label = { Text("Nome do espaço") }, singleLine = true, enabled = !busy)
            Button(
                onClick = {
                    busy = true; message = ""
                    scope.launch {
                        try {
                            val info = HouseholdCloud.create(context, householdName)
                            householdId = info.id; inviteCode = info.inviteCode
                            message = "Espaço criado. Compartilhe o código com o Nicolas."
                        } catch (e: Exception) { message = e.message ?: "Não foi possível criar o espaço." }
                        finally { busy = false }
                    }
                }, enabled = !busy && CloudAuth.isConfigured() && householdName.isNotBlank()
            ) { Text("Criar espaço e gerar convite") }
            Divider(color = Color(0xFF3A3048))
            OutlinedTextField(joinCode, { joinCode = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("Código recebido") }, singleLine = true, enabled = !busy)
            Button(
                onClick = {
                    busy = true; message = ""
                    scope.launch {
                        try {
                            val info = HouseholdCloud.join(context, joinCode)
                            householdId = info.id; inviteCode = info.inviteCode
                            message = "Você entrou no espaço do casal."
                        } catch (e: Exception) { message = e.message ?: "Não foi possível entrar no espaço." }
                        finally { busy = false }
                    }
                }, enabled = !busy && CloudAuth.isConfigured() && joinCode.isNotBlank()
            ) { Text("Entrar com convite") }
        }
        if (message.isNotBlank()) Text(message, color = if (message.contains("criado") || message.contains("entrou") || message.contains("copiado")) Color(0xFF9EE6BD) else Color(0xFFFFA8B5), fontSize = 13.sp)
        Text("A associação de contas está implementada. A sincronização dos movimentos só será ativada depois de separar explicitamente dados pessoais e compartilhados, para não expor suas informações privadas.", color = Color(0xFFB8AEC9), fontSize = 12.sp)
    }
}
