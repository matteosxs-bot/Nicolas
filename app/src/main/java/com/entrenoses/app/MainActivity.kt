package com.entrenoses.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF100D18)
private val Panel = Color(0xFF1D1728)
private val Purple = Color(0xFFB69CFF)
private val Muted = Color(0xFFB8AEC9)
private val White = Color(0xFFF8F5FF)
private val Green = Color(0xFF9EE6BD)
private val Red = Color(0xFFFFA8B5)

private enum class Page(val label: String) {
    Home("Início"), Transactions("Movimentos"), Shopping("Compras"), Goals("Metas"), More("Mais"), Trash("Lixeira")
}
private data class MoneyEntry(val title: String, val amount: Double, val income: Boolean, val person: String, val category: String, val date: String = currentDate(), val paid: Boolean = false)
private data class Goal(val title: String, val target: Double, val saved: Double)
private data class TrashEntry(val entry: MoneyEntry, val deletedAt: Long)
private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun money(value: Double) = brl.format(value)
private fun currentDate() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EntreNosesApp() }
    }
}

@Composable
private fun EntreNosesApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.Home) }
    val entries = remember { mutableStateListOf<MoneyEntry>().apply { addAll(loadEntries(context)) } }
    val trash = remember {
        val loaded = loadTrash(context)
        saveTrash(context, loaded)
        mutableStateListOf<TrashEntry>().apply { addAll(loaded) }
    }
    val shopping = remember { mutableStateListOf<String>().apply { addAll(loadShopping(context)) } }
    val goals = remember { mutableStateListOf<Goal>().apply { addAll(loadGoals(context)) } }
    var showEntry by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var showShopping by remember { mutableStateOf(false) }
    var showDeposit by remember { mutableStateOf(false) }
    var selectedGoal by remember { mutableStateOf<Goal?>(null) }
    val income = entries.filter { it.income && it.paid }.sumOf { it.amount }
    val expenses = entries.filter { !it.income && it.paid }.sumOf { it.amount }
    val pendingExpenses = entries.filter { !it.income && !it.paid }.sumOf { it.amount }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple, secondary = Color(0xFFDFCFFF), background = Night,
            surface = Panel, onPrimary = Night, onBackground = White, onSurface = White
        )
    ) {
        Scaffold(
            containerColor = Night,
            topBar = {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF302046), Night))).padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 16.dp)
                ) {
                    Text("ENTRE NOSES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Noses", color = White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Panel, contentColor = Muted) {
                    Page.entries.filter { it != Page.Trash }.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = {
                                Icon(when(item) {
                                    Page.Home -> Icons.Default.Home
                                    Page.Transactions -> Icons.Default.AccountBalanceWallet
                                    Page.Shopping -> Icons.Default.ListAlt
                                    Page.Goals -> Icons.Default.Savings
                                    Page.More -> Icons.Default.MoreHoriz
                                    Page.Trash -> Icons.Default.Delete
                                }, contentDescription = item.label)
                            },
                            label = { Text(item.label, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Night, selectedTextColor = Purple, indicatorColor = Purple, unselectedIconColor = Muted, unselectedTextColor = Muted)
                        )
                    }
                }
            },
            floatingActionButton = {
                if (page == Page.Home || page == Page.Transactions) {
                    FloatingActionButton(onClick = { showEntry = true }, containerColor = Purple, contentColor = Night) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar movimento")
                    }
                } else if (page == Page.Shopping) {
                    FloatingActionButton(onClick = { showShopping = true }, containerColor = Purple, contentColor = Night) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar item")
                    }
                } else if (page == Page.Goals) {
                    FloatingActionButton(onClick = { showGoal = true }, containerColor = Purple, contentColor = Night) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar meta")
                    }
                }
            }
        ) { padding ->
            when (page) {
                Page.Home -> HomePage(
                    Modifier.padding(padding), income, expenses, pendingExpenses, entries, goals,
                    onViewAll = { page = Page.Transactions },
                    onAddDeposit = { goal -> selectedGoal = goal; showDeposit = true },
                    onDelete = { entry ->
                        entries.remove(entry)
                        trash.add(0, TrashEntry(entry, System.currentTimeMillis()))
                        saveEntries(context, entries.toList())
                        saveTrash(context, trash.toList())
                    },
                    onTogglePaid = { entry ->
                        val index = entries.indexOf(entry)
                        if (index >= 0) {
                            entries[index] = entry.copy(paid = !entry.paid)
                            saveEntries(context, entries.toList())
                        }
                    }
                )
                Page.Transactions -> TransactionsPage(
                    Modifier.padding(padding), entries,
                    onDelete = { entry ->
                        entries.remove(entry)
                        trash.add(0, TrashEntry(entry, System.currentTimeMillis()))
                        saveEntries(context, entries.toList())
                        saveTrash(context, trash.toList())
                    },
                    onTogglePaid = { entry ->
                        val index = entries.indexOf(entry)
                        if (index >= 0) {
                            entries[index] = entry.copy(paid = !entry.paid)
                            saveEntries(context, entries.toList())
                        }
                    }
                )
                Page.Shopping -> ShoppingPage(Modifier.padding(padding), shopping) {
                    shopping.remove(it)
                    saveShopping(context, shopping.toList())
                }
                Page.Goals -> GoalsPage(Modifier.padding(padding), goals) { goal ->
                    selectedGoal = goal
                    showDeposit = true
                }
                Page.More -> MorePage(Modifier.padding(padding)) { page = Page.Trash }
                Page.Trash -> TrashPage(
                    Modifier.padding(padding), trash,
                    onRestore = { item ->
                        trash.remove(item)
                        entries.add(0, item.entry)
                        saveTrash(context, trash.toList())
                        saveEntries(context, entries.toList())
                        page = Page.Transactions
                    },
                    onDeletePermanently = { item ->
                        trash.remove(item)
                        saveTrash(context, trash.toList())
                    }
                )
            }
        }
    }

    if (showEntry) EntryDialog(
        onDismiss = { showEntry = false },
        onSave = { title, amount, isIncome, person, category, paid ->
            entries.add(0, MoneyEntry(title, amount, isIncome, person, category, currentDate(), paid))
            saveEntries(context, entries.toList())
            showEntry = false
        }
    )
    if (showGoal) GoalDialog(
        onDismiss = { showGoal = false },
        onSave = { title, target ->
            goals.add(Goal(title, target, 0.0))
            saveGoals(context, goals.toList())
            showGoal = false
        }
    )
    if (showShopping) ShoppingDialog(
        onDismiss = { showShopping = false },
        onSave = { item ->
            shopping.add(item)
            saveShopping(context, shopping.toList())
            showShopping = false
        }
    )
    if (showDeposit) DepositDialog(
        goalTitle = selectedGoal?.title.orEmpty(),
        onDismiss = { showDeposit = false; selectedGoal = null },
        onSave = { amount ->
            val goal = selectedGoal
            val index = if (goal == null) -1 else goals.indexOf(goal)
            if (index >= 0) {
                goals[index] = goals[index].copy(saved = goals[index].saved + amount)
                saveGoals(context, goals.toList())
            }
            showDeposit = false
            selectedGoal = null
        }
    )
}

@Composable
private fun HomePage(modifier: Modifier, income: Double, expenses: Double, pendingExpenses: Double, entries: List<MoneyEntry>, goals: List<Goal>, onViewAll: () -> Unit, onAddDeposit: (Goal) -> Unit, onDelete: (MoneyEntry) -> Unit, onTogglePaid: (MoneyEntry) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4B3372), Color(0xFF21182E))), RoundedCornerShape(24.dp)).padding(22.dp)) {
                Text("Visão geral do mês", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(money(income - expenses), color = White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("Saldo calculado pelos movimentos cadastrados", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Entradas", money(income), Green, Modifier.weight(1f))
                    Metric("Saídas pagas", money(expenses), Red, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Despesas pendentes: ${money(pendingExpenses)}", color = Muted, fontSize = 12.sp)
            }
        }
        item { SectionTitle("Movimentos recentes", "Ver todos", onViewAll) }
        if (entries.isEmpty()) item { EmptyCard("Nenhum movimento cadastrado ainda.") }
        items(entries.take(5)) { entry -> EntryRow(entry, onTogglePaid, onDelete) }
        item { SectionTitle("Metas de economia", null, null) }
        if (goals.isEmpty()) item { EmptyCard("Crie uma meta para acompanhar suas economias.") }
        items(goals.take(3)) { GoalCard(it, onAddDeposit) }
        item { Text("Os dados ficam salvos neste aparelho. A sincronização entre aparelhos ainda não está disponível.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun TransactionsPage(modifier: Modifier, entries: List<MoneyEntry>, onDelete: (MoneyEntry) -> Unit, onTogglePaid: (MoneyEntry) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Movimentos", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Entradas e saídas registradas neste aparelho.", color = Muted, fontSize = 13.sp) }
        if (entries.isEmpty()) item { EmptyCard("Ainda não há movimentos.") }
        items(entries) { EntryRow(it, onTogglePaid, onDelete) }
    }
}

@Composable
private fun ShoppingPage(modifier: Modifier, shopping: List<String>, onRemove: (String) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Lista de compras", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Marque os itens quando terminar de comprar.", color = Muted, fontSize = 13.sp) }
        if (shopping.isEmpty()) item { EmptyCard("Sua lista está vazia.") }
        items(shopping) { item ->
            var checked by remember(item) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { checked = it }, colors = CheckboxDefaults.colors(checkedColor = Purple))
                Text(item, Modifier.weight(1f), color = if (checked) Muted else White)
                TextButton(onClick = { onRemove(item) }) { Text("Remover", color = Red) }
            }
        }
    }
}

@Composable
private fun GoalsPage(modifier: Modifier, goals: List<Goal>, onAddDeposit: (Goal) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Metas", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Acompanhe o progresso das economias.", color = Muted, fontSize = 13.sp) }
        if (goals.isEmpty()) item { EmptyCard("Nenhuma meta criada.") }
        items(goals) { GoalCard(it, onAddDeposit) }
    }
}

@Composable
private fun MorePage(modifier: Modifier, onOpenTrash: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mais opções", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        MoreCard("Contas e cartões", "Bancos, dinheiro, cartões e limites")
        MoreCard("Despesas recorrentes", "Aluguel, contas e parcelas")
        MoreCard("Veículos", "Combustível, quilometragem e manutenção")
        MoreCard("Casa e pets", "Consumo, estoque e gastos dos animais")
        MoreCard("Relatórios", "Resumo mensal, categorias e exportação")
        MoreCard("Configurações", "Privacidade, categorias e limites")
        TextButton(onClick = onOpenTrash) { Text("Abrir lixeira de movimentos") }
        Text("As demais seções ainda estão em desenvolvimento.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Metric(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0x3320182C), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(title, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun SectionTitle(title: String, action: String?, onClick: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null && onClick != null) TextButton(onClick) { Text(action, color = Purple) }
    }
}

@Composable
private fun EntryRow(entry: MoneyEntry, onTogglePaid: (MoneyEntry) -> Unit, onDelete: (MoneyEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(if (entry.income) Color(0xFF244735) else Color(0xFF452631), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (entry.income) Icons.Default.CheckCircle else Icons.Default.AccountBalanceWallet, null, tint = if (entry.income) Green else Red)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title, color = White, fontWeight = FontWeight.SemiBold)
            Text("${entry.category} · ${entry.person} · ${entry.date}", color = Muted, fontSize = 10.sp)
            Text(if (entry.paid) (if (entry.income) "Recebido" else "Pago") else "Pendente", color = if (entry.paid) Green else Red, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onTogglePaid(entry) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text(if (entry.paid) "Marcar pendente" else if (entry.income) "Confirmar recebimento" else "Confirmar pagamento", fontSize = 10.sp)
                }
                TextButton(onClick = { onDelete(entry) }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text("Excluir", fontSize = 10.sp, color = Red)
                }
            }
        }
        Text((if (entry.income) "+" else "−") + money(entry.amount), color = if (entry.income) Green else Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun GoalCard(goal: Goal, onAddDeposit: (Goal) -> Unit) {
    val progress = if (goal.target <= 0) 0f else (goal.saved / goal.target).toFloat().coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(goal.title, color = White, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("${money(goal.saved)} de ${money(goal.target)}", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = Purple, trackColor = Color(0xFF3A3048))
        TextButton(onClick = { onAddDeposit(goal) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Adicionar depósito") }
    }
}

@Composable
private fun MoreCard(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Text("›", color = Purple, fontSize = 25.sp)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Text(message, Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(18.dp), color = Muted, fontSize = 13.sp)
}

@Composable
private fun EntryDialog(onDismiss: () -> Unit, onSave: (String, Double, Boolean, String, String, Boolean) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var person by remember { mutableStateOf("Matteo") }
    var category by remember { mutableStateOf("Outros") }
    var paid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo movimento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor em R$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !income, onClick = { income = false }, label = { Text("Saída") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = income, onClick = { income = true }, label = { Text("Entrada") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Responsável:", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    listOf("Matteo", "Nicolas", "Compartilhado").forEach { name ->
                        FilterChip(selected = person == name, onClick = { person = name }, label = { Text(name, fontSize = 10.sp) }, modifier = Modifier.padding(end = 3.dp))
                    }
                }
                OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, singleLine = true)
                Text("Situação do movimento", color = Muted, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = !paid, onClick = { paid = false }, label = { Text("Pendente") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = paid, onClick = { paid = true }, label = { Text(if (income) "Recebido" else "Pago") })
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsed = amount.replace(",", ".").toDoubleOrNull()
            if (title.isNotBlank() && parsed != null && parsed > 0) onSave(title.trim(), parsed, income, person, category.ifBlank { "Outros" }, paid)
        }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GoalDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova meta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nome da meta") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor desejado (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsed = amount.replace(",", ".").toDoubleOrNull()
            if (title.isNotBlank() && parsed != null && parsed > 0) onSave(title.trim(), parsed)
        }) { Text("Criar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ShoppingDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var item by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar à lista") },
        text = { OutlinedTextField(item, { item = it }, label = { Text("Produto") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (item.isNotBlank()) onSave(item.trim()) }) { Text("Adicionar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private const val PREFS_NAME = "entre_noses_local_data"

private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun loadEntries(context: Context): List<MoneyEntry> = runCatching {
    val array = JSONArray(prefs(context).getString("entries", "[]"))
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        MoneyEntry(item.optString("title"), item.optDouble("amount"), item.optBoolean("income"),
            item.optString("person", "Matteo"), item.optString("category", "Outros"),
            item.optString("date", currentDate()), item.optBoolean("paid", false))
    }
}.getOrDefault(emptyList())

private fun saveEntries(context: Context, entries: List<MoneyEntry>) {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(JSONObject().apply {
            put("title", entry.title)
            put("amount", entry.amount)
            put("income", entry.income)
            put("person", entry.person)
            put("category", entry.category)
            put("date", entry.date)
            put("paid", entry.paid)
        })
    }
    prefs(context).edit().putString("entries", array.toString()).apply()
}

private fun loadShopping(context: Context): List<String> = runCatching {
    val array = JSONArray(prefs(context).getString("shopping", "[]"))
    (0 until array.length()).map { array.getString(it) }
}.getOrDefault(emptyList())

private fun saveShopping(context: Context, items: List<String>) {
    val array = JSONArray()
    items.forEach { array.put(it) }
    prefs(context).edit().putString("shopping", array.toString()).apply()
}

private fun loadGoals(context: Context): List<Goal> = runCatching {
    val array = JSONArray(prefs(context).getString("goals", "[]"))
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        Goal(item.optString("title"), item.optDouble("target"), item.optDouble("saved"))
    }
}.getOrDefault(emptyList())

private fun saveGoals(context: Context, goals: List<Goal>) {
    val array = JSONArray()
    goals.forEach { goal ->
        array.put(JSONObject().apply {
            put("title", goal.title)
            put("target", goal.target)
            put("saved", goal.saved)
        })
    }
    prefs(context).edit().putString("goals", array.toString()).apply()
}


@Composable
private fun DepositDialog(goalTitle: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar depósito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(goalTitle, color = Muted, fontSize = 13.sp)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor depositado (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsed = amount.replace(",", ".").toDoubleOrNull()
            if (parsed != null && parsed > 0) onSave(parsed)
        }) { Text("Registrar depósito") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}


@Composable
private fun TrashPage(
    modifier: Modifier,
    trash: List<TrashEntry>,
    onRestore: (TrashEntry) -> Unit,
    onDeletePermanently: (TrashEntry) -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Lixeira", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Os movimentos ficam aqui por até 30 dias e depois são descartados.", color = Muted, fontSize = 13.sp) }
        if (trash.isEmpty()) item { EmptyCard("A lixeira está vazia.") }
        items(trash) { item ->
            val daysLeft = (30 - ((System.currentTimeMillis() - item.deletedAt) / 86_400_000L).toInt()).coerceAtLeast(0)
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text(item.entry.title, color = White, fontWeight = FontWeight.SemiBold)
                Text("${money(item.entry.amount)} · Excluído em ${SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(item.deletedAt))}", color = Muted, fontSize = 12.sp)
                Text("Restam até ${daysLeft} dias para restaurar", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onRestore(item) }) { Text("Restaurar") }
                    TextButton(onClick = { onDeletePermanently(item) }) { Text("Excluir definitivamente", color = Red) }
                }
            }
        }
    }
}

private fun loadTrash(context: Context): List<TrashEntry> = runCatching {
    val cutoff = System.currentTimeMillis() - 30L * 24L * 60L * 60L * 1000L
    val array = JSONArray(prefs(context).getString("trash", "[]"))
    (0 until array.length()).mapNotNull { i ->
        val item = array.getJSONObject(i)
        val deletedAt = item.optLong("deletedAt")
        if (deletedAt < cutoff) null else {
            val entry = item.getJSONObject("entry")
            TrashEntry(
                MoneyEntry(
                    entry.optString("title"), entry.optDouble("amount"), entry.optBoolean("income"),
                    entry.optString("person", "Matteo"), entry.optString("category", "Outros"),
                    entry.optString("date", currentDate()), entry.optBoolean("paid", false)
                ),
                deletedAt
            )
        }
    }
}.getOrDefault(emptyList())

private fun saveTrash(context: Context, trash: List<TrashEntry>) {
    val array = JSONArray()
    trash.filter { System.currentTimeMillis() - it.deletedAt < 30L * 24L * 60L * 60L * 1000L }.forEach { deleted ->
        val entry = deleted.entry
        array.put(JSONObject().apply {
            put("deletedAt", deleted.deletedAt)
            put("entry", JSONObject().apply {
                put("title", entry.title)
                put("amount", entry.amount)
                put("income", entry.income)
                put("person", entry.person)
                put("category", entry.category)
                put("date", entry.date)
                put("paid", entry.paid)
            })
        })
    }
    prefs(context).edit().putString("trash", array.toString()).apply()
}
