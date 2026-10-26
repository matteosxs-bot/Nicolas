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
import androidx.compose.material.icons.filled.Remove
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
import java.util.UUID

private data class StockItem(
    val id: String,
    val name: String,
    val category: String,
    val quantity: Double,
    val unit: String,
    val minimum: Double,
    val lastPrice: Double,
    val lastPurchase: String,
    val note: String
)

private fun loadStock(context: Context): List<StockItem> = runCatching {
    val array = JSONArray(securePreferences(context).getString("stock_items", "[]"))
    (0 until array.length()).map { i ->
        val x = array.getJSONObject(i)
        StockItem(x.optString("id"), x.optString("name"), x.optString("category"),
            x.optDouble("quantity"), x.optString("unit"), x.optDouble("minimum"),
            x.optDouble("lastPrice"), x.optString("lastPurchase"), x.optString("note"))
    }
}.getOrDefault(emptyList())

private fun saveStock(context: Context, items: List<StockItem>) {
    val array = JSONArray()
    items.forEach { x ->
        array.put(JSONObject().put("id", x.id).put("name", x.name).put("category", x.category)
            .put("quantity", x.quantity).put("unit", x.unit).put("minimum", x.minimum)
            .put("lastPrice", x.lastPrice).put("lastPurchase", x.lastPurchase).put("note", x.note))
    }
    check(securePreferences(context).edit().putString("stock_items", array.toString()).commit()) {
        "Não foi possível salvar o estoque."
    }
}

@Composable
internal fun InventoryPage(modifier: Modifier) {
    val context = LocalContext.current
    val stock = remember { mutableStateListOf<StockItem>().apply { addAll(loadStock(context)) } }
    var adding by remember { mutableStateOf(false) }
    val lowStock = stock.count { it.quantity <= it.minimum }

    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Casa e pets", color = Color(0xFFF8F5FF), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text("Controle manual de ração, areia, gás, limpeza e itens da casa. Alterar estoque não cria uma despesa automaticamente.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Itens cadastrados", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                Text("${stock.size}", color = Color(0xFFF8F5FF), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Precisam repor", color = Color(0xFFB8AEC9), fontSize = 12.sp)
                Text("$lowStock", color = if (lowStock > 0) Color(0xFFFFA8B5) else Color(0xFF9EE6BD), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("Cadastrar item") }
        }
        if (stock.isEmpty()) {
            Text("Cadastre os itens que vocês querem acompanhar. O estoque é opcional e sempre pode ser ajustado manualmente.", color = Color(0xFFB8AEC9), fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(stock.sortedWith(compareBy<StockItem> { it.quantity > it.minimum }.thenBy { it.name.lowercase() }), key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().background(Color(0xFF1D1728), RoundedCornerShape(15.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color(0xFFF8F5FF), fontWeight = FontWeight.SemiBold)
                                Text(item.category + if (item.lastPurchase.isNotBlank()) " • última compra ${item.lastPurchase}" else "",
                                    color = Color(0xFFB8AEC9), fontSize = 12.sp)
                            }
                            if (item.quantity <= item.minimum) Text("Repor", color = Color(0xFFFFA8B5), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val index = stock.indexOfFirst { it.id == item.id }
                                if (index >= 0) { stock[index] = item.copy(quantity = (item.quantity - 1).coerceAtLeast(0.0)); saveStock(context, stock.toList()) }
                            }) { Icon(Icons.Default.Remove, "Diminuir estoque", tint = Color(0xFFB69CFF)) }
                            Text("${"%.2f".format(java.util.Locale("pt","BR"), item.quantity)} ${item.unit}", color = Color(0xFFF8F5FF), fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                val index = stock.indexOfFirst { it.id == item.id }
                                if (index >= 0) { stock[index] = item.copy(quantity = item.quantity + 1); saveStock(context, stock.toList()) }
                            }) { Icon(Icons.Default.Add, "Aumentar estoque", tint = Color(0xFF9EE6BD)) }
                            Spacer(Modifier.weight(1f))
                            if (item.lastPrice > 0) Text("Último preço R$ ${"%.2f".format(java.util.Locale("pt","BR"), item.lastPrice)}", color = Color(0xFFB8AEC9), fontSize = 11.sp)
                            IconButton(onClick = { stock.remove(item); saveStock(context, stock.toList()) }) {
                                Icon(Icons.Default.Delete, "Excluir item", tint = Color(0xFFFFA8B5))
                            }
                        }
                        if (item.note.isNotBlank()) Text(item.note, color = Color(0xFFB8AEC9), fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (adding) StockItemDialog(
        onDismiss = { adding = false },
        onSave = { item ->
            stock.add(item)
            saveStock(context, stock.toList())
            adding = false
        }
    )
}

@Composable
private fun StockItemDialog(onDismiss: () -> Unit, onSave: (StockItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Pets") }
    var quantity by remember { mutableStateOf("0") }
    var unit by remember { mutableStateOf("un.") }
    var minimum by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var purchaseDate by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cadastrar item de estoque") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome (ração, areia, gás...)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Pets", "Casa", "Limpeza", "Gás", "Alimentos").forEach { cat ->
                        FilterChip(selected = category == cat, onClick = { category = cat }, label = { Text(cat, fontSize = 10.sp) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(quantity, { quantity = it }, Modifier.weight(1f), label = { Text("Quantidade") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(unit, { unit = it }, Modifier.weight(1f), label = { Text("Unidade") }, singleLine = true)
                }
                OutlinedTextField(minimum, { minimum = it }, label = { Text("Avisar para repor em") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Último preço pago (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(purchaseDate, { purchaseDate = it }, label = { Text("Data da última compra (opcional)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Observação (opcional)") }, singleLine = true)
                if (error.isNotBlank()) Text(error, color = Color(0xFFFFA8B5), fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = quantity.replace(",", ".").toDoubleOrNull()
                val m = minimum.replace(",", ".").toDoubleOrNull()
                val p = price.replace(",", ".").toDoubleOrNull() ?: 0.0
                if (name.isBlank() || unit.isBlank() || q == null || q < 0 || m == null || m < 0 || p < 0) {
                    error = "Confira nome, quantidade, unidade e limite de reposição."
                } else {
                    onSave(StockItem(UUID.randomUUID().toString(), name.trim(), category, q, unit.trim(), m, p, purchaseDate.trim(), note.trim()))
                }
            }) { Text("Salvar item") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
