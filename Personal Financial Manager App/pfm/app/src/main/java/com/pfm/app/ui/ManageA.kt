package com.pfm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.*
import com.pfm.app.domain.Money
import com.pfm.app.security.PinManager

@Composable
fun ManageScreens(route: String, vm: MainViewModel, s: UiState, nav: Nav, pins: PinManager, onRestart: () -> Unit) {
    when (route) {
        "accounts" -> AccountsScreen(vm, s, nav)
        "categories" -> CategoriesScreen(vm, s, nav)
        "goals" -> GoalsScreen(vm, s, nav)
        "cards" -> CardsScreen(vm, s, nav)
        "debts" -> DebtsScreen(vm, s, nav)
        "budgets" -> BudgetsScreen(vm, s, nav)
        "recurring" -> RecurringScreen(vm, s, nav)
        else -> SettingsPages(route, vm, s, nav, pins, onRestart)
    }
}

@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, onOk: () -> Unit, extra: @Composable (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) },
        confirmButton = { TextButton(onClick = onOk) { Text("บันทึก") } },
        dismissButton = { Row { extra?.invoke(); TextButton(onClick = onDismiss) { Text("ยกเลิก") } } })
}

@Composable
fun ListScreen(title: String, nav: Nav, onAdd: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenScaffold(title, nav, actions = { IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "add") } }) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun TextF(label: String, v: String, onChange: (String) -> Unit) =
    OutlinedTextField(v, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())

val palette = listOf(0xFF1E6BFF, 0xFF00A86B, 0xFFE53935, 0xFFF59E0B, 0xFF7B1FA2, 0xFF00897B, 0xFF5D4037, 0xFF455A64)

@Composable
fun ColorPick(sel: Long, onSel: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        palette.forEach { c ->
            Surface(Modifier.size(if (c == sel) 34.dp else 28.dp).clickable { onSel(c) }, shape = androidx.compose.foundation.shape.CircleShape, color = Color(c)) {}
        }
    }
}

// ---------------- Accounts ----------------
@Composable
fun AccountsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    ListScreen("บัญชีและกระเป๋าเงิน", nav, { creating = true }) {
        CardBox { Text("ยอดรวม (เฉพาะบัญชีที่นับรวม)"); BigAmount(fmtMoney(s.settings, s.totalAssets)) }
        s.accounts.forEach { a ->
            CardBox(onClick = { editing = a }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(iconFor(when (a.type) { AccountType.CASH -> "payments"; AccountType.JAR, AccountType.SAVINGS -> "savings"; AccountType.EWALLET -> "wallet"; else -> "account_balance" }), Color(a.color))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontWeight = FontWeight.SemiBold)
                        Text(listOf(accTypeLabel(a.type), a.bank, a.maskedRef).filter { it.isNotBlank() }.joinToString(" · ") + if (!a.includeInTotal) " · ไม่นับรวม" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(fmtMoney(s.settings, s.balance(a)), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (creating || editing != null) AccountDialog(vm, editing, onDismiss = { creating = false; editing = null }, s)
}

fun accTypeLabel(t: String) = when (t) {
    AccountType.BANK -> "บัญชีธนาคาร"; AccountType.CASH -> "เงินสด"; AccountType.SAVINGS -> "บัญชีเงินเก็บ"
    AccountType.JAR -> "กระปุกออมสิน"; AccountType.EWALLET -> "E-Wallet"; else -> "กำหนดเอง"
}

@Composable
private fun AccountDialog(vm: MainViewModel, a: Account?, onDismiss: () -> Unit, s: UiState) {
    var name by remember { mutableStateOf(a?.name ?: "") }
    var type by remember { mutableStateOf(a?.type ?: AccountType.BANK) }
    var bank by remember { mutableStateOf(a?.bank ?: "") }
    var ref by remember { mutableStateOf(a?.maskedRef ?: "") }
    var init by remember { mutableStateOf(a?.initialBalance?.let { Money.toInput(it) } ?: "") }
    var note by remember { mutableStateOf(a?.note ?: "") }
    var incl by remember { mutableStateOf(a?.includeInTotal ?: true) }
    var color by remember { mutableLongStateOf(a?.color ?: palette[0]) }
    var err by remember { mutableStateOf<String?>(null) }
    FormDialog(if (a == null) "เพิ่มบัญชี" else "แก้ไขบัญชี", onDismiss, onOk = {
        if (name.isBlank()) { err = "กรอกชื่อบัญชี"; return@FormDialog }
        val acc = (a ?: Account(name = name)).copy(name = name.trim(), type = type, bank = bank.trim(), maskedRef = ref.trim(),
            initialBalance = Money.parse(init) ?: 0, note = note.trim(), includeInTotal = incl, color = color)
        vm.run { if (a == null) db.accounts().insert(acc) else db.accounts().update(acc) }; onDismiss()
    }, extra = { if (a != null) TextButton(onClick = { vm.run { db.accounts().archive(a.id) }; onDismiss() }) { Text("ซ่อน") } }) {
        TextF("ชื่อบัญชี", name) { name = it }
        Dropdown("ประเภท", listOf(AccountType.BANK, AccountType.CASH, AccountType.SAVINGS, AccountType.JAR, AccountType.EWALLET, AccountType.CUSTOM).map { it as String? to accTypeLabel(it) }, type, { type = it ?: AccountType.BANK })
        TextF("ธนาคาร", bank) { bank = it }
        TextF("เลขบัญชี (แบบ Masked เช่น xxx-x-x1234-x)", ref) { ref = it }
        MoneyField("ยอดเงินเริ่มต้น", init) { init = it }
        TextF("หมายเหตุ", note) { note = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("นับรวมในยอดเงินรวม", Modifier.weight(1f)); Switch(incl, { incl = it }) }
        ColorPick(color) { color = it }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------- Categories ----------------
@Composable
fun CategoriesScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    ListScreen("หมวดหมู่", nav, { creating = true }) {
        listOf("EXPENSE" to "รายจ่าย", "INCOME" to "รายรับ").forEach { (k, label) ->
            Text(label, fontWeight = FontWeight.SemiBold)
            CardBox { s.categories.filter { it.kind == k }.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { editing = c }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(iconFor(c.icon)); Spacer(Modifier.width(12.dp)); Text(c.name)
                } } }
        }
    }
    if (creating || editing != null) {
        val c = editing
        var name by remember(c) { mutableStateOf(c?.name ?: "") }
        var kind by remember(c) { mutableStateOf(c?.kind ?: "EXPENSE") }
        var icon by remember(c) { mutableStateOf(c?.icon ?: "more_horiz") }
        val close = { creating = false; editing = null }
        FormDialog(if (c == null) "เพิ่มหมวดหมู่" else "แก้ไขหมวดหมู่", close, onOk = {
            if (name.isBlank()) return@FormDialog
            vm.run { val n = (c ?: Category(name = name, kind = kind, icon = icon)).copy(name = name.trim(), kind = kind, icon = icon)
                if (c == null) db.categories().insert(n) else db.categories().update(n) }; close()
        }, extra = { if (c != null) TextButton(onClick = { vm.run { db.categories().delete(c) }; close() }) { Text("ลบ") } }) {
            TextF("ชื่อหมวดหมู่", name) { name = it }
            if (c == null) Chips(listOf("EXPENSE" to "รายจ่าย", "INCOME" to "รายรับ"), kind) { kind = it }
            Text("ไอคอน")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(iconMap.keys.toList()) { k -> Box(Modifier.clickable { icon = k }) { IconBadge(iconFor(k), if (k == icon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }
}

// ---------------- Goals ----------------
@Composable
fun GoalsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var creating by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf<Pair<SavingsGoal, Int>?>(null) } // +1 เพิ่ม / -1 ถอน
    ListScreen("เป้าหมายเงินเก็บ", nav, { creating = true }) {
        if (s.goals.isEmpty()) Text("ยังไม่มีเป้าหมาย", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.goals.forEach { g ->
            val saved = s.goalSaved(g)
            CardBox {
                Text(g.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                ProgressRow("ความคืบหน้า", saved, g.targetAmount, s.settings)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { adjust = g to 1 }) { Text("เพิ่มเงิน") }
                    OutlinedButton(onClick = { adjust = g to -1 }) { Text("ถอนเงิน") }
                    TextButton(onClick = { vm.run { db.goals().deleteEntries(g.id); db.goals().delete(g) } }) { Text("ลบ") }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }; var target by remember { mutableStateOf("") }
        FormDialog("เป้าหมายใหม่", { creating = false }, onOk = {
            val t = Money.parse(target) ?: 0
            if (name.isNotBlank() && t > 0) { vm.run { db.goals().insert(SavingsGoal(name = name.trim(), targetAmount = t)) }; creating = false }
        }) { TextF("ชื่อเป้าหมาย", name) { name = it }; MoneyField("เป้าหมายทั้งหมด", target) { target = it } }
    }
    adjust?.let { (g, dir) ->
        var amt by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }
        FormDialog(if (dir > 0) "เพิ่มเงินเข้า ${g.name}" else "ถอนเงินจาก ${g.name}", { adjust = null }, onOk = {
            val v = Money.parse(amt) ?: 0
            if (v <= 0) { err = "จำนวนเงินไม่ถูกต้อง"; return@FormDialog }
            vm.run { goalAdjust(g.id, v * dir)?.let { e -> err = e } ?: run { adjust = null } }
        }) { MoneyField("จำนวนเงิน", amt) { amt = it }; err?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }
}
