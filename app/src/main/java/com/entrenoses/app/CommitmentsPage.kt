package com.entrenoses.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private data class Commitment(
    val id: String, val type: String, val name: String, val amount: Double,
    val dueDate: String, val paid: Boolean, val recurring: Boolean,
    val installment: Int, val totalInstallments: Int, val note: String
)

private fun loadCommitments(context: Context): List<Commitment> = runCatching {
    val array = JSONArray(securePreferences(context).getString("commitments", "[]"))
    (0 until array.length()).map { i ->
        val x = array.getJSONObject(i)
        Commitment(x.optString("id"), x.optString("type"), x.optString("name"), x.optDouble("amount"),
            x.optString("dueDate"), x.optBoolean("paid"), x.optBoolean("recurring"),
            x.optInt("installment"), x.optInt("totalInstallments"), x.optString("note"))
    }
}.getOrDefault(emptyList())

private fun saveCommitments(context: Context, records: List<Commitment>) {
    val array = JSONArray()
    records.forEach { x ->
        array.put(JSONObject().put("id", x.id).put("type", x.type).put("name", x.name)
            .put("amount", x.amount).put("dueDate", x.dueDate).put("paid", x.paid)
            .put("recurring", x.recurring).put("installment", x.installment)
            .put("totalInstallments", x.totalInstallments).put("note", x.note))
    }
    check(securePreferences(context).edit().putString("commitments", array.toString()).commit()) {
        "Não foi possível salvar o compromisso."
    }
}

private fun nextDueDate(value: String): String? = runCatching {
    val format = SimpleDateFormat("dd/MM/yyyy", Locale("pt","BR")).apply { isLenient = false }
    val date = format.parse(value) ?: return null
    val calendar = Calendar.getInstance().apply { time = date }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.add(Calendar.MONTH, 1)
    val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, minOf(day, lastDay))
    format.format(calendar.time)
}.getOrNull()

@Composable
internal fun CommitmentsPage(modifier: Modifier) {
    val context = LocalContext.current
    val records = remember { mutableStateListOf<Commitment>().apply { addAll(loadCommitments(context)) } }
    var showAdd by remember { mutableStateOf(false) }
    val pending = records.filter { !it.paid }.sumOf { it.amount }
    val paid = records.filter { it.paid }.sumOf { it.amount }

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dívidas e assinaturas", color = Color(0xFFF8F5FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Acompanhe parcelas, dívidas, assinaturas e serviços recorrentes. A quitação é sempre confirmada manualmente.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CommitmentSummary("Em aberto", pending, Modifier.weight(1f), Color(0xFFFFA8B5))
            CommitmentSummary("Marcado pago", paid, Modifier.weight(1f), Color(0xFF9EE6BD))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Adicionar") }
        }
        if (records.isEmpty()) Text("Nenhuma dívida ou assinatura cadastrada.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(records.sortedWith(compareBy<Commitment> { it.paid }.thenBy { it.dueDate }), key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(15.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Color(0xFFF8F5FF), fontWeight = FontWeight.SemiBold)
                            Text("${item.type} • vencimento ${item.dueDate}", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                        }
                        Text("R$ ${"%.2f".format(Locale("pt","BR"), item.amount)}", color = Color(0xFFB69CFF), fontWeight = FontWeight.Bold)
                    }
                    if (item.type == "Parcela" && item.totalInstallments > 0) {
                        Text("Parcela ${item.installment}/${item.totalInstallments}", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                    }
                    if (item.recurring) Text("Recorrente: gera o próximo vencimento ao marcar como pago", color = Color(0xFFB8AEC9), fontSize = 11.sp)
                    if (item.note.isNotBlank()) Text(item.note, color = Color(0xFFB8AEC9), fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val index = records.indexOfFirst { it.id == item.id }
                            if (index >= 0) {
                                if (!item.paid) {
                                    records[index] = item.copy(paid = true)
                                    val shouldRepeat = item.recurring || (item.type == "Parcela" && item.installment < item.totalInstallments)
                                    if (shouldRepeat) {
                                        val nextDate = nextDueDate(item.dueDate)
                                        if (nextDate != null) records.add(item.copy(
                                            id = UUID.randomUUID().toString(), dueDate = nextDate, paid = false,
                                            installment = if (item.type == "Parcela") item.installment + 1 else item.installment
                                        ))
                                    }
                                } else records[index] = item.copy(paid = false)
                                saveCommitments(context, records.toList())
                            }
                        }) { Text(if (item.paid) "Marcar pendente" else "Confirmar pagamento", color = if (item.paid) Color(0xFFFFA8B5) else Color(0xFF9EE6BD)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { records.remove(item); saveCommitments(context, records.toList()) }) {
                            Icon(Icons.Default.Delete, "Excluir compromisso", tint = Color(0xFFFFA8B5))
                        }
                    }
                }
            }
        }
    }
    if (showAdd) CommitmentDialog(onDismiss = { showAdd = false }, onSave = {
        records.add(it); saveCommitments(context, records.toList()); showAdd = false
    })
}

@Composable
private fun CommitmentSummary(title: String, amount: Double, modifier: Modifier, tint: Color) {
    Column(modifier.background(Color(0xFF1D1728), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(title, color = Color(0xFFB8AEC9), fontSize = 12.sp)
        Text("R$ ${"%.2f".format(Locale("pt","BR"), amount)}", color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun CommitmentDialog(onDismiss: () -> Unit, onSave: (Commitment) -> Unit) {
    var type by remember { mutableStateOf("Dívida") }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale("pt","BR").apply { },).format(Date())) }
    var recurring by remember { mutableStateOf(false) }
    var installment by remember { mutableStateOf("1") }
    var totalInstallments by remember { mutableStateOf("1") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo compromisso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Dívida", "Parcela", "Assinatura").forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Nome do compromisso") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Vencimento (DD/MM/AAAA)") }, singleLine = true)
                if (type == "Parcela") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(installment, { installment = it }, Modifier.weight(1f), label = { Text("Parcela nº") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(totalInstallments, { totalInstallments = it }, Modifier.weight(1f), label = { Text("Total parcelas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    }
                }
                if (type == "Assinatura" || type == "Dívida") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(recurring, { recurring = it })
                        Text(if (type == "Assinatura") "Renova mensalmente" else "Repetir mensalmente", color = Color(0xFFF8F5FF), fontSize = 12.sp)
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFA8B5), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.replace(",", ".").toDoubleOrNull()
                val inst = installment.toIntOrNull() ?: 1
                val total = totalInstallments.toIntOrNull() ?: 1
                val validDate = runCatching {
                    val f = SimpleDateFormat("dd/MM/yyyy", Locale("pt","BR")).apply { isLenient = false }
                    f.parse(dueDate)?.let { f.format(it) == dueDate } == true
                }.getOrDefault(false)
                if (name.isBlank() || value == null || value <= 0 || !validDate || inst < 1 || total < 1 || inst > total) {
                    error = "Confira nome, valor, vencimento e parcelas."
                } else onSave(Commitment(UUID.randomUUID().toString(), type, name.trim(), value, dueDate, false,
                    recurring && type != "Parcela", inst, total, note.trim()))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
