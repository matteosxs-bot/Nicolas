package com.entrenoses.app

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
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
import java.util.Calendar
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
    Home("Início"), Transactions("Movimentos"), Shopping("Compras"), Goals("Metas"), More("Mais"), Trash("Lixeira"), Budgets("Limites"), Reports("Relatórios"), Bills("Contas")
}
private data class MoneyEntry(val title: String, val amount: Double, val income: Boolean, val person: String, val category: String, val date: String = currentDate(), val paid: Boolean = false)
private data class Goal(val title: String, val target: Double, val saved: Double)
private data class TrashEntry(val entry: MoneyEntry, val deletedAt: Long)
private data class ShoppingItem(val name: String, val checked: Boolean = false)
private data class BudgetLimit(val category: String, val limit: Double)
private data class Bill(val title: String, val amount: Double, val dueDate: String, val paid: Boolean = false, val recurring: Boolean = false)
private val brl = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private fun money(value: Double) = brl.format(value)
private fun currentDate() = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date())
private fun dateMillis(value: String): Long? {
    val format = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply { isLenient = false }
    val parsed = runCatching { format.parse(value) }.getOrNull() ?: return null
    return if (format.format(parsed) == value) parsed.time else null
}
private fun isValidDate(value: String): Boolean = dateMillis(value) != null

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
    val shopping = remember { mutableStateListOf<ShoppingItem>().apply { addAll(loadShopping(context)) } }
    val goals = remember { mutableStateListOf<Goal>().apply { addAll(loadGoals(context)) } }
    val budgets = remember { mutableStateListOf<BudgetLimit>().apply { addAll(loadBudgets(context)) } }
    val bills = remember { mutableStateListOf<Bill>().apply { addAll(loadBills(context)) } }
    var showEntry by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<MoneyEntry?>(null) }
    var showGoal by remember { mutableStateOf(false) }
    var showShopping by remember { mutableStateOf(false) }
    var showDeposit by remember { mutableStateOf(false) }
    var showBudget by remember { mutableStateOf(false) }
    var showBill by remember { mutableStateOf(false) }
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
                    Page.entries.filter { it != Page.Trash && it != Page.Budgets && it != Page.Reports && it != Page.Bills }.forEach { item ->
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
                                    Page.Budgets -> Icons.Default.PieChart
                                    Page.Reports -> Icons.Default.BarChart
                                    Page.Bills -> Icons.Default.ReceiptLong
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
                    FloatingActionButton(onClick = { editingEntry = null; showEntry = true }, containerColor = Purple, contentColor = Night) {
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
                } else if (page == Page.Budgets) {
                    FloatingActionButton(onClick = { showBudget = true }, containerColor = Purple, contentColor = Night) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar limite")
                    }
                } else if (page == Page.Bills) {
                    FloatingActionButton(onClick = { showBill = true }, containerColor = Purple, contentColor = Night) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar conta")
                    }
                }
            }
        ) { padding ->
            when (page) {
                Page.Home -> HomePage(
                    Modifier.padding(padding), income, expenses, pendingExpenses, entries, goals,
                    onViewAll = { page = Page.Transactions },
                    onEdit = { entry -> editingEntry = entry; showEntry = true },
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
                    onEdit = { entry -> editingEntry = entry; showEntry = true },
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
                Page.Shopping -> ShoppingPage(
                    Modifier.padding(padding), shopping,
                    onToggle = { item ->
                        val index = shopping.indexOf(item)
                        if (index >= 0) {
                            shopping[index] = item.copy(checked = !item.checked)
                            saveShopping(context, shopping.toList())
                        }
                    },
                    onRemove = { item ->
                        shopping.remove(item)
                        saveShopping(context, shopping.toList())
                    }
                )
                Page.Goals -> GoalsPage(Modifier.padding(padding), goals) { goal ->
                    selectedGoal = goal
                    showDeposit = true
                }
                Page.More -> MorePage(Modifier.padding(padding), onOpenTrash = { page = Page.Trash }, onOpenBudgets = { page = Page.Budgets }, onOpenReports = { page = Page.Reports }, onOpenBills = { page = Page.Bills })
                Page.Reports -> ReportsPage(Modifier.padding(padding), entries)
                Page.Bills -> BillsPage(
                    Modifier.padding(padding), bills,
                    onTogglePaid = { bill ->
                        val index = bills.indexOf(bill)
                        if (index >= 0) {
                            val updated = bill.copy(paid = !bill.paid)
                            bills[index] = updated
                            if (!bill.paid && bill.recurring) {
                                val next = nextMonthBill(updated)
                                if (next != null && bills.none { it.title == next.title && it.dueDate == next.dueDate }) {
                                    bills.add(next)
                                }
                            }
                            saveBills(context, bills.toList())
                        }
                    },
                    onDelete = { bill ->
                        bills.remove(bill)
                        saveBills(context, bills.toList())
                    }
                )
                Page.Budgets -> BudgetsPage(
                    Modifier.padding(padding), budgets, entries,
                    onDelete = { budget ->
                        budgets.remove(budget)
                        saveBudgets(context, budgets.toList())
                    }
                )
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
        initialEntry = editingEntry,
        onDismiss = { showEntry = false; editingEntry = null },
        onSave = { updated ->
            val existingIndex = if (editingEntry == null) -1 else entries.indexOf(editingEntry)
            if (existingIndex >= 0) entries[existingIndex] = updated else entries.add(0, updated)
            saveEntries(context, entries.toList())
            showEntry = false
            editingEntry = null
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
            shopping.add(ShoppingItem(item))
            saveShopping(context, shopping.toList())
            showShopping = false
        }
    )
    if (showBill) BillDialog(
        onDismiss = { showBill = false },
        onSave = { bill ->
            bills.add(0, bill)
            saveBills(context, bills.toList())
            showBill = false
        }
    )
    if (showBudget) BudgetDialog(
        onDismiss = { showBudget = false },
        onSave = { category, limit ->
            budgets.removeAll { it.category.equals(category, ignoreCase = true) }
            budgets.add(BudgetLimit(category, limit))
            saveBudgets(context, budgets.toList())
            showBudget = false
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
private fun HomePage(modifier: Modifier, income: Double, expenses: Double, pendingExpenses: Double, entries: List<MoneyEntry>, goals: List<Goal>, onViewAll: () -> Unit, onEdit: (MoneyEntry) -> Unit, onAddDeposit: (Goal) -> Unit, onDelete: (MoneyEntry) -> Unit, onTogglePaid: (MoneyEntry) -> Unit) {
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
        items(entries.take(5)) { entry -> EntryRow(entry, onTogglePaid, onDelete, onEdit) }
        item { SectionTitle("Metas de economia", null, null) }
        if (goals.isEmpty()) item { EmptyCard("Crie uma meta para acompanhar suas economias.") }
        items(goals.take(3)) { GoalCard(it, onAddDeposit) }
        item { Text("Os dados ficam salvos neste aparelho. A sincronização entre aparelhos ainda não está disponível.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun TransactionsPage(modifier: Modifier, entries: List<MoneyEntry>, onEdit: (MoneyEntry) -> Unit, onDelete: (MoneyEntry) -> Unit, onTogglePaid: (MoneyEntry) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Movimentos", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Entradas e saídas registradas neste aparelho.", color = Muted, fontSize = 13.sp) }
        if (entries.isEmpty()) item { EmptyCard("Ainda não há movimentos.") }
        items(entries) { EntryRow(it, onTogglePaid, onDelete, onEdit) }
    }
}

@Composable
private fun ShoppingPage(modifier: Modifier, shopping: List<ShoppingItem>, onToggle: (ShoppingItem) -> Unit, onRemove: (ShoppingItem) -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Lista de compras", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Marque os itens quando terminar de comprar.", color = Muted, fontSize = 13.sp) }
        if (shopping.isEmpty()) item { EmptyCard("Sua lista está vazia.") }
        items(shopping) { item ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(item.checked, { onToggle(item) }, colors = CheckboxDefaults.colors(checkedColor = Purple))
                Text(item.name, Modifier.weight(1f), color = if (item.checked) Muted else White)
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
private fun MorePage(modifier: Modifier, onOpenTrash: () -> Unit, onOpenBudgets: () -> Unit, onOpenReports: () -> Unit, onOpenBills: () -> Unit) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mais opções", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        MoreCard("Contas e cartões", "Bancos, dinheiro, cartões e limites")
        MoreCard("Despesas recorrentes", "Aluguel, contas e parcelas")
        MoreCard("Veículos", "Combustível, quilometragem e manutenção")
        MoreCard("Casa e pets", "Consumo, estoque e gastos dos animais")
        MoreCard("Relatórios", "Resumo mensal, categorias e exportação")
        MoreCard("Configurações", "Privacidade, categorias e limites")
        TextButton(onClick = onOpenBills) { Text("Contas a pagar") }
        TextButton(onClick = onOpenReports) { Text("Relatórios mensais e anuais") }
        TextButton(onClick = onOpenBudgets) { Text("Orçamentos e limites por categoria") }
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
private fun EntryRow(entry: MoneyEntry, onTogglePaid: (MoneyEntry) -> Unit, onDelete: (MoneyEntry) -> Unit, onEdit: (MoneyEntry) -> Unit) {
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
                TextButton(onClick = { onEdit(entry) }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                    Text("Editar", fontSize = 10.sp)
                }
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
private fun EntryDialog(initialEntry: MoneyEntry?, onDismiss: () -> Unit, onSave: (MoneyEntry) -> Unit) {
    var title by remember(initialEntry) { mutableStateOf(initialEntry?.title.orEmpty()) }
    var amount by remember(initialEntry) { mutableStateOf(initialEntry?.amount?.toString().orEmpty()) }
    var date by remember(initialEntry) { mutableStateOf(initialEntry?.date ?: currentDate()) }
    var income by remember(initialEntry) { mutableStateOf(initialEntry?.income ?: false) }
    var person by remember(initialEntry) { mutableStateOf(initialEntry?.person ?: "Matteo") }
    var category by remember(initialEntry) { mutableStateOf(initialEntry?.category ?: "Outros") }
    var paid by remember(initialEntry) { mutableStateOf(initialEntry?.paid ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEntry == null) "Novo movimento" else "Editar movimento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Descrição") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor em R$") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(date, { date = it }, label = { Text("Data (DD/MM/AAAA)") }, singleLine = true)
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
            if (title.isNotBlank() && parsed != null && parsed > 0 && isValidDate(date.trim())) {
                onSave(MoneyEntry(title.trim(), parsed, income, person, category.ifBlank { "Outros" }, date.trim(), paid))
            }
        }) { Text(if (initialEntry == null) "Salvar" else "Salvar alterações") } },
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

private fun loadShopping(context: Context): List<ShoppingItem> = runCatching {
    val array = JSONArray(prefs(context).getString("shopping", "[]"))
    (0 until array.length()).map { i ->
        val raw = array.get(i)
        if (raw is JSONObject) ShoppingItem(raw.optString("name"), raw.optBoolean("checked", false))
        else ShoppingItem(raw.toString(), false)
    }
}.getOrDefault(emptyList())

private fun saveShopping(context: Context, items: List<ShoppingItem>) {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject().apply {
            put("name", item.name)
            put("checked", item.checked)
        })
    }
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


@Composable
private fun BudgetsPage(
    modifier: Modifier,
    budgets: List<BudgetLimit>,
    entries: List<MoneyEntry>,
    onDelete: (BudgetLimit) -> Unit
) {
    val monthKey = currentDate().let { it.substring(3) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Orçamentos e limites", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Você recebe um aviso visual quando os gastos pagos passam do limite. O registro não é bloqueado.", color = Muted, fontSize = 13.sp) }
        if (budgets.isEmpty()) item { EmptyCard("Nenhum limite definido. Toque em + para criar o primeiro.") }
        items(budgets) { budget ->
            val spent = entries.filter {
                !it.income && it.paid && it.category.equals(budget.category, ignoreCase = true) && it.date.length >= 10 && it.date.substring(3) == monthKey
            }.sumOf { it.amount }
            val progress = if (budget.limit <= 0) 0f else (spent / budget.limit).toFloat().coerceIn(0f, 1f)
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(budget.category, Modifier.weight(1f), color = White, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { onDelete(budget) }) { Text("Remover", color = Red) }
                }
                Text("${money(spent)} de ${money(budget.limit)}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp), color = if (spent > budget.limit) Red else Purple, trackColor = Color(0xFF3A3048))
                if (spent > budget.limit) Text("Limite ultrapassado em ${money(spent - budget.limit)}", color = Red, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BudgetDialog(onDismiss: () -> Unit, onSave: (String, Double) -> Unit) {
    var category by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo limite mensal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Categoria") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Limite em R$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsed = amount.replace(",", ".").toDoubleOrNull()
            if (category.isNotBlank() && parsed != null && parsed > 0) onSave(category.trim(), parsed)
        }) { Text("Salvar limite") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun loadBudgets(context: Context): List<BudgetLimit> = runCatching {
    val array = JSONArray(prefs(context).getString("budgets", "[]"))
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        BudgetLimit(item.optString("category"), item.optDouble("limit"))
    }
}.getOrDefault(emptyList())

private fun saveBudgets(context: Context, budgets: List<BudgetLimit>) {
    val array = JSONArray()
    budgets.forEach { budget ->
        array.put(JSONObject().apply {
            put("category", budget.category)
            put("limit", budget.limit)
        })
    }
    prefs(context).edit().putString("budgets", array.toString()).apply()
}


@Composable
private fun ReportsPage(modifier: Modifier, entries: List<MoneyEntry>) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val csv = buildString {
                appendLine("Data;Descrição;Tipo;Categoria;Responsável;Valor;Situação")
                entries.forEach { entry ->
                    val fields = listOf(
                        entry.date, entry.title, if (entry.income) "Entrada" else "Saída",
                        entry.category, entry.person, String.format(Locale("pt", "BR"), "%.2f", entry.amount),
                        if (entry.paid) if (entry.income) "Recebido" else "Pago" else "Pendente"
                    )
                    appendLine(fields.joinToString(";") { csvField(it) })
                }
            }
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) } }
        }
    }
    val today = currentDate()
    val monthKey = today.substring(3)
    val yearKey = today.takeLast(4)
    val monthEntries = entries.filter { it.paid && it.date.length >= 10 && it.date.substring(3) == monthKey }
    val yearEntries = entries.filter { it.paid && it.date.endsWith(yearKey) }
    val monthIncome = monthEntries.filter { it.income }.sumOf { it.amount }
    val monthExpenses = monthEntries.filter { !it.income }.sumOf { it.amount }
    val yearIncome = yearEntries.filter { it.income }.sumOf { it.amount }
    val yearExpenses = yearEntries.filter { !it.income }.sumOf { it.amount }
    val categories = monthEntries.filter { !it.income }.groupBy { it.category }
        .map { (category, items) -> category to items.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val maxCategory = categories.maxOfOrNull { it.second } ?: 1.0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Relatórios", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Somente movimentos pagos ou recebidos entram nos totais.", color = Muted, fontSize = 13.sp) }
        item {
            Button(onClick = { exportLauncher.launch("entre-noses-movimentos.csv") }) {
                Text("Exportar movimentos em CSV")
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text("Este mês", color = Purple, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Entradas: ${money(monthIncome)}", color = Green)
                Text("Saídas: ${money(monthExpenses)}", color = Red)
                Text("Saldo: ${money(monthIncome - monthExpenses)}", color = White, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text("Este ano", color = Purple, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Entradas: ${money(yearIncome)}", color = Green)
                Text("Saídas: ${money(yearExpenses)}", color = Red)
                Text("Saldo: ${money(yearIncome - yearExpenses)}", color = White, fontWeight = FontWeight.Bold)
            }
        }
        item { Text("Gastos por categoria neste mês", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        if (categories.isEmpty()) item { EmptyCard("Ainda não há gastos pagos neste mês.") }
        items(categories) { (category, amount) ->
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(category, Modifier.weight(1f), color = White)
                    Text(money(amount), color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { (amount / maxCategory).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Purple, trackColor = Color(0xFF3A3048))
            }
        }
    }
}


private fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""


@Composable
private fun BillsPage(
    modifier: Modifier,
    bills: List<Bill>,
    onTogglePaid: (Bill) -> Unit,
    onDelete: (Bill) -> Unit
) {
    val today = currentDate()
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Contas a pagar", color = White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { Text("Registre vencimentos e confirme manualmente quando cada conta for paga.", color = Muted, fontSize = 13.sp) }
        if (bills.isEmpty()) item { EmptyCard("Nenhuma conta cadastrada. Toque em + para adicionar.") }
        items(bills.sortedBy { dateMillis(it.dueDate) ?: Long.MAX_VALUE }) { bill ->
            val dueMillis = dateMillis(bill.dueDate)
            val overdue = !bill.paid && dueMillis != null && dueMillis < (dateMillis(today) ?: 0L)
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bill.title, Modifier.weight(1f), color = White, fontWeight = FontWeight.SemiBold)
                    Text(money(bill.amount), color = if (bill.paid) Green else Red, fontWeight = FontWeight.Bold)
                }
                Text("Vencimento: ${bill.dueDate}", color = Muted, fontSize = 12.sp)
                Text(if (bill.paid) "Paga" else if (overdue) "Atrasada" else "Pendente", color = if (bill.paid) Green else if (overdue) Red else Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onTogglePaid(bill) }) { Text(if (bill.paid) "Marcar pendente" else "Confirmar pagamento") }
                    TextButton(onClick = { onDelete(bill) }) { Text("Excluir", color = Red) }
                }
            }
        }
    }
}

@Composable
private fun BillDialog(onDismiss: () -> Unit, onSave: (Bill) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(currentDate()) }
    var recurring by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova conta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Nome da conta") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor em R$") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(dueDate, { dueDate = it }, label = { Text("Vencimento (DD/MM/AAAA)") }, singleLine = true)
                FilterChip(selected = recurring, onClick = { recurring = !recurring }, label = { Text("Repetir todo mês") })
            }
        },
        confirmButton = { TextButton(onClick = {
            val parsed = amount.replace(",", ".").toDoubleOrNull()
            if (title.isNotBlank() && parsed != null && parsed > 0 && isValidDate(dueDate.trim())) {
                onSave(Bill(title.trim(), parsed, dueDate.trim(), false, recurring))
            }
        }) { Text("Salvar conta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

private fun loadBills(context: Context): List<Bill> = runCatching {
    val array = JSONArray(prefs(context).getString("bills", "[]"))
    (0 until array.length()).map { i ->
        val item = array.getJSONObject(i)
        Bill(item.optString("title"), item.optDouble("amount"), item.optString("dueDate"), item.optBoolean("paid"), item.optBoolean("recurring"))
    }
}.getOrDefault(emptyList())

private fun saveBills(context: Context, bills: List<Bill>) {
    val array = JSONArray()
    bills.forEach { bill ->
        array.put(JSONObject().apply {
            put("title", bill.title)
            put("amount", bill.amount)
            put("dueDate", bill.dueDate)
            put("paid", bill.paid)
            put("recurring", bill.recurring)
        })
    }
    prefs(context).edit().putString("bills", array.toString()).apply()
}


private fun nextMonthBill(bill: Bill): Bill? {
    val millis = dateMillis(bill.dueDate) ?: return null
    val calendar = Calendar.getInstance().apply { time = Date(millis) }
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    calendar.add(Calendar.MONTH, 1)
    calendar.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
    val nextDate = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(calendar.time)
    return bill.copy(dueDate = nextDate, paid = false)
}
