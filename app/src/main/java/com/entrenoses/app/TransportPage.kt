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
import java.util.Date
import java.util.Locale

private data class TransportRecord(
    val id: String,
    val vehicle: String,
    val type: String,
    val date: String,
    val odometer: Double,
    val liters: Double,
    val pricePerLiter: Double,
    val amount: Double,
    val note: String
)

private fun loadTransport(context: Context): List<TransportRecord> = runCatching {
    val array = JSONArray(securePreferences(context).getString("transport_records", "[]"))
    (0 until array.length()).map { i ->
        val x = array.getJSONObject(i)
        TransportRecord(
            x.optString("id"), x.optString("vehicle"), x.optString("type"),
            x.optString("date"), x.optDouble("odometer"), x.optDouble("liters"),
            x.optDouble("pricePerLiter"), x.optDouble("amount"), x.optString("note")
        )
    }
}.getOrDefault(emptyList())

private fun saveTransport(context: Context, records: List<TransportRecord>) {
    val array = JSONArray()
    records.forEach { r ->
        array.put(JSONObject().put("id", r.id).put("vehicle", r.vehicle).put("type", r.type)
            .put("date", r.date).put("odometer", r.odometer).put("liters", r.liters)
            .put("pricePerLiter", r.pricePerLiter).put("amount", r.amount).put("note", r.note))
    }
    check(securePreferences(context).edit().putString("transport_records", array.toString()).commit()) {
        "Não foi possível salvar o registro."
    }
}

@Composable
internal fun TransportPage(modifier: Modifier) {
    val context = LocalContext.current
    val records = remember { mutableStateListOf<TransportRecord>().apply { addAll(loadTransport(context)) } }
    var showAdd by remember { mutableStateOf(false) }
    val totalFuel = records.filter { it.type == "Abastecimento" }.sumOf { it.amount }
    val totalMaintenance = records.filter { it.type == "Manutenção" }.sumOf { it.amount }

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Transporte", color = Color(0xFFF8F5FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Abastecimentos, quilometragem e manutenção da moto ou de outros veículos.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Combustível", totalFuel, Modifier.weight(1f))
            SummaryCard("Manutenção", totalMaintenance, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Histórico", color = Color(0xFFF8F5FF), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Registrar") }
        }
        if (records.isEmpty()) {
            Text("Ainda não há abastecimentos ou manutenções registrados.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(records.sortedByDescending { it.id }, key = { it.id }) { r ->
                    Column(Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.vehicle, color = Color(0xFFF8F5FF), fontWeight = FontWeight.SemiBold)
                                Text("${r.type} • ${r.date}", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                            }
                            Text("R$ ${"%.2f".format(Locale("pt","BR"), r.amount)}", color = Color(0xFFB69CFF), fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                records.remove(r); saveTransport(context, records.toList())
                            }) { Icon(Icons.Default.Delete, "Excluir registro", tint = Color(0xFFFFA8B5)) }
                        }
                        if (r.type == "Abastecimento") {
                            Text("${r.liters} L × R$ ${"%.3f".format(Locale("pt","BR"), r.pricePerLiter)}/L • odômetro ${r.odometer.toLong()} km",
                                color = Color(0xFFB8AEC9), fontSize = 12.sp)
                        } else {
                            Text("Odômetro: ${r.odometer.toLong()} km", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                        }
                        if (r.note.isNotBlank()) Text(r.note, color = Color(0xFFF8F5FF), fontSize = 13.sp)
                    }
                }
            }
        }
    }
    if (showAdd) TransportEntryDialog(
        onDismiss = { showAdd = false },
        onSave = { record ->
            records.add(record)
            saveTransport(context, records.toList())
            showAdd = false
        }
    )
}

@Composable
private fun SummaryCard(title: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF1D1728), RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(title, color = Color(0xFFB8AEC9), fontSize = 12.sp)
        Text("R$ ${"%.2f".format(Locale("pt","BR"), amount)}", color = Color(0xFFF8F5FF), fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun TransportEntryDialog(onDismiss: () -> Unit, onSave: (TransportRecord) -> Unit) {
    var vehicle by remember { mutableStateOf("Moto") }
    var type by remember { mutableStateOf("Abastecimento") }
    var date by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale("pt","BR")).format(Date())) }
    var odometer by remember { mutableStateOf("") }
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo registro de transporte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Abastecimento", "Manutenção").forEach { option ->
                        FilterChip(selected = type == option, onClick = { type = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(vehicle, { vehicle = it }, label = { Text("Veículo (ex.: Moto)") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Data (DD/MM/AAAA)") }, singleLine = true)
                OutlinedTextField(odometer, { odometer = it }, label = { Text("Odômetro em km") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                if (type == "Abastecimento") {
                    OutlinedTextField(liters, { liters = it }, label = { Text("Litros abastecidos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("Preço por litro (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Text("Total calculado: R$ ${"%.2f".format(Locale("pt","BR"), (liters.replace(",", ".").toDoubleOrNull() ?: 0.0) * (price.replace(",", ".").toDoubleOrNull() ?: 0.0))}", color = Color(0xFFB69CFF), fontWeight = FontWeight.SemiBold)
                } else {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Valor da manutenção (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                OutlinedTextField(note, { note = it }, label = { Text("Observação (óleo, pneu, posto...)") }, minLines = 2)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFA8B5), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val km = odometer.replace(",", ".").toDoubleOrNull()
                val l = liters.replace(",", ".").toDoubleOrNull() ?: 0.0
                val p = price.replace(",", ".").toDoubleOrNull() ?: 0.0
                val total = if (type == "Abastecimento") l * p else amount.replace(",", ".").toDoubleOrNull() ?: 0.0
                val validDate = runCatching {
                    val f = SimpleDateFormat("dd/MM/yyyy", Locale("pt","BR")).apply { isLenient = false }
                    f.parse(date)?.let { f.format(it) == date } == true
                }.getOrDefault(false)
                if (vehicle.isBlank() || km == null || km < 0 || !validDate || total <= 0 ||
                    (type == "Abastecimento" && (l <= 0 || p <= 0))) {
                    error = "Confira veículo, data, quilometragem e valores."
                } else {
                    onSave(TransportRecord(
                        java.util.UUID.randomUUID().toString(), vehicle.trim(), type, date, km,
                        if (type == "Abastecimento") l else 0.0,
                        if (type == "Abastecimento") p else 0.0, total, note.trim()
                    ))
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
