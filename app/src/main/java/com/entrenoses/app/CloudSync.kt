package com.entrenoses.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class SyncReport(val privateRecords: Int, val sharedShoppingItems: Int, val message: String)

internal object CloudSync {
    private const val SYNC_TIME = "cloud_sync_updated_at"

    suspend fun sync(context: Context): SyncReport = withContext(Dispatchers.IO) {
        check(CloudAuth.isConfigured()) { "O servidor ainda não está configurado." }
        val session = CloudAuth.savedSession(context) ?: error("Entre na sua conta primeiro.")
        val prefs = securePreferences(context)
        val householdId = prefs.getString("household_id", "").orEmpty()

        val privateKeys = prefs.all.keys.filter {
            it !in setOf("shopping", "household_id", "household_invite_code", SYNC_TIME)
        }
        val localPrivate = JSONObject()
        privateKeys.forEach { key -> encodePreference(localPrivate, key, prefs.all[key]) }

        val remotePrivate = getSnapshot(
            "/rest/v1/private_snapshots?select=payload&user_id=eq.${session.userId}",
            session.accessToken
        )?.optJSONObject("payload")

        val mergedPrivate = mergePayloads(remotePrivate, localPrivate, setOf("entries", "goals", "budgets", "bills", "trash", "transport_records", "stock_items", "commitments"))
        putSnapshot(
            "/rest/v1/private_snapshots?on_conflict=user_id",
            JSONObject().put("user_id", session.userId).put("payload", mergedPrivate),
            session.accessToken
        )
        applyPayload(prefs, mergedPrivate, mergedPrivate.keys().asSequence().toSet())

        var sharedCount = 0
        if (householdId.isNotBlank()) {
            val localShopping = runCatching { JSONArray(prefs.getString("shopping", "[]")) }.getOrElse { JSONArray() }
            val remoteShared = getSnapshot(
                "/rest/v1/shared_snapshots?select=payload&household_id=eq.$householdId",
                session.accessToken
            )?.optJSONObject("payload")
            val remoteShopping = remoteShared?.optJSONArray("shopping") ?: JSONArray()
            val mergedShopping = mergeArrays(remoteShopping, localShopping, "name")
            val sharedPayload = JSONObject().put("shopping", mergedShopping)
            putSnapshot(
                "/rest/v1/shared_snapshots?on_conflict=household_id",
                JSONObject().put("household_id", householdId).put("updated_by", session.userId).put("payload", sharedPayload),
                session.accessToken
            )
            prefs.edit().putString("shopping", mergedShopping.toString()).apply()
            sharedCount = mergedShopping.length()
        } else {
            // Shopping remains local until the user connects a couple space.
            sharedCount = runCatching { JSONArray(prefs.getString("shopping", "[]")).length() }.getOrDefault(0)
        }
        prefs.edit().putLong(SYNC_TIME, System.currentTimeMillis()).apply()
        SyncReport(mergedPrivate.length(), sharedCount,
            if (householdId.isBlank()) "Dados pessoais sincronizados. Para compartilhar a lista de compras, conecte um espaço do casal."
            else "Dados pessoais e lista de compras compartilhada sincronizados.")
    }

    private fun encodePreference(target: JSONObject, key: String, value: Any?) {
        when (value) {
            is String -> target.put(key, JSONObject().put("type", "string").put("value", value))
            is Boolean -> target.put(key, JSONObject().put("type", "boolean").put("value", value))
            is Int -> target.put(key, JSONObject().put("type", "int").put("value", value))
            is Long -> target.put(key, JSONObject().put("type", "long").put("value", value))
            is Float -> target.put(key, JSONObject().put("type", "float").put("value", value.toDouble()))
            is Set<*> -> target.put(key, JSONObject().put("type", "strings").put("value", JSONArray(value.filterIsInstance<String>())))
        }
    }

    private fun decodePreference(value: JSONObject): Any? = when (value.optString("type")) {
        "string" -> value.optString("value")
        "boolean" -> value.optBoolean("value")
        "int" -> value.optInt("value")
        "long" -> value.optLong("value")
        "float" -> value.optDouble("value").toFloat()
        "strings" -> value.optJSONArray("value")?.let { array -> (0 until array.length()).map { array.optString(it) }.toSet() }
        else -> null
    }

    private fun mergePayloads(remote: JSONObject?, local: JSONObject, mergeKeys: Set<String>): JSONObject {
        val result = JSONObject()
        if (remote != null) {
            val keys = remote.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result.put(key, remote.opt(key))
            }
        }
        val keys = local.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val localValue = local.opt(key)
            val remoteValue = result.opt(key)
            if (key in mergeKeys && localValue is JSONObject && remoteValue is JSONObject) {
                val localRaw = localValue.optString("value", "")
                val remoteRaw = remoteValue.optString("value", "")
                if (localValue.optString("type") == "string" && remoteValue.optString("type") == "string" &&
                    localRaw.trimStart().startsWith("[") && remoteRaw.trimStart().startsWith("[")) {
                    val merged = mergeArrays(JSONArray(remoteRaw), JSONArray(localRaw), key)
                    result.put(key, JSONObject().put("type", "string").put("value", merged.toString()))
                } else result.put(key, localValue)
            } else if (remoteValue == null) result.put(key, localValue)
            else result.put(key, localValue)
        }
        applyTrashTombstones(result)
        return result
    }

    private fun applyTrashTombstones(payload: JSONObject) {
        val now = System.currentTimeMillis()
        val retention = 30L * 24L * 60L * 60L * 1000L
        val trashEnvelope = payload.optJSONObject("trash") ?: return
        if (trashEnvelope.optString("type") != "string") return
        val rawTrash = trashEnvelope.optString("value", "[]")
        val trash = runCatching { JSONArray(rawTrash) }.getOrElse { JSONArray() }
        val validTrash = JSONArray()
        val deleted = mutableSetOf<String>()
        for (i in 0 until trash.length()) {
            val item = trash.optJSONObject(i) ?: continue
            val deletedAt = item.optLong("deletedAt")
            if (deletedAt <= 0L || now - deletedAt >= retention) continue
            validTrash.put(item)
            val entry = item.optJSONObject("entry") ?: continue
            deleted.add(listOf("title", "date", "person", "category", "income").joinToString("|") { entry.opt(it)?.toString().orEmpty() }.lowercase())
        }
        payload.put("trash", JSONObject().put("type", "string").put("value", validTrash.toString()))
        val entriesEnvelope = payload.optJSONObject("entries") ?: return
        if (entriesEnvelope.optString("type") != "string") return
        val entries = runCatching { JSONArray(entriesEnvelope.optString("value", "[]")) }.getOrElse { JSONArray() }
        val filtered = JSONArray()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val identity = listOf("title", "date", "person", "category", "income").joinToString("|") { entry.opt(it)?.toString().orEmpty() }.lowercase()
            if (identity !in deleted) filtered.put(entry)
        }
        payload.put("entries", JSONObject().put("type", "string").put("value", filtered.toString()))
    }

    private fun mergeArrays(remote: JSONArray, local: JSONArray, identityKey: String?): JSONArray {
        val records = linkedMapOf<String, Any>()
        fun identity(item: Any): String {
            if (item is JSONObject) {
                val fields = when (identityKey) {
                    "shopping", "name" -> listOf("name")
                    "transport_records", "stock_items", "commitments" -> listOf("id")
                    "entries" -> listOf("title", "date", "person", "category", "income")
                    "goals" -> listOf("title")
                    "budgets" -> listOf("category")
                    "bills" -> listOf("title", "dueDate")
                    "trash" -> listOf("deletedAt", "entry")
                    else -> emptyList()
                }
                if (fields.isNotEmpty()) {
                    return fields.joinToString("|") { field ->
                        val value = item.opt(field)
                        if (value is JSONObject) value.toString() else value?.toString().orEmpty()
                    }.lowercase()
                }
            }
            return item.toString()
        }
        for (i in 0 until remote.length()) remote.opt(i)?.let { records[identity(it)] = it }
        // Local edits take precedence for matching records; unrelated records from both devices remain.
        for (i in 0 until local.length()) local.opt(i)?.let { records[identity(it)] = it }
        return JSONArray().also { merged -> records.values.forEach { merged.put(it) } }
    }

    private fun applyPayload(prefs: SharedPreferences, payload: JSONObject, onlyKeys: Set<String>) {
        val editor = prefs.edit()
        val keys = payload.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in onlyKeys) continue
            val envelope = payload.optJSONObject(key) ?: continue
            val value = decodePreference(envelope) ?: continue
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Não foi possível salvar os dados sincronizados no aparelho." }
    }

    private fun getSnapshot(path: String, token: String): JSONObject? {
        val response = request(path, "GET", null, token)
        val array = JSONArray(response)
        return array.optJSONObject(0)
    }

    private fun putSnapshot(path: String, body: JSONObject, token: String) {
        request(path, "POST", body, token)
    }

    private fun request(path: String, method: String, body: JSONObject?, token: String): String {
        val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val details = runCatching {
                    val error = JSONObject(response)
                    error.optString("message").ifBlank { error.optString("details") }.ifBlank { error.optString("hint") }
                }.getOrNull()
                throw IllegalStateException(details?.takeIf { it.isNotBlank() } ?: "Falha na sincronização (HTTP $status).")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
