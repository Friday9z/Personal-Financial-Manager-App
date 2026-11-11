package com.pfm.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.Receipts
import com.pfm.app.data.Transaction
import com.pfm.app.data.TxType
import com.pfm.app.domain.FinanceCalc
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** หน้า Add (Full Mode) และหน้าแก้ไข */
@Composable
fun AddScreen(vm: MainViewModel, s: UiState, nav: Nav, snack: SnackbarHostState, editId: Long?) {
    val scope = rememberCoroutineScope()
    val edit = editId?.let { id -> s.txs.firstOrNull { it.id == id } }
    // ค่าที่ส่งกลับมาจากหน้าสแกน
    val pre = remember { ScanBridge.take() }
    ScreenScaffold(if (editId == null) "เพิ่มรายการ" else "แก้ไขรายการ", nav) {
        Box(Modifier.padding(horizontal = 16.dp)) {
            TxForm(vm, s, edit, full = true, prefill = pre ?: Prefill(), keyPrefix = "add",
                onScan = { nav.go("scan") },
                onSaved = { msg -> scope.launch { snack.showSnackbar(msg) }; if (editId != null) nav.back() })
        }
    }
}

private enum class SortBy(val label: String) { DATE_DESC("วันที่ ใหม่→เก่า"), DATE_ASC("วันที่ เก่า→ใหม่"), AMT_DESC("จำนวนเงิน มาก→น้อย"), AMT_ASC("จำนวนเงิน น้อย→มาก") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var q by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var catId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var fromStr by rememberSaveable { mutableStateOf("") }
    var toStr by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortBy.DATE_DESC) }
    var showFilters by rememberSaveable { mutableStateOf(false) }

    val from = runCatching { LocalDate.parse(fromStr) }.getOrNull()
    val to = runCatching { LocalDate.parse(toStr) }.getOrNull()
    val list = s.txs.filter { t ->
        (type == null || t.type == type) && (catId == null || t.categoryId == catId) &&
            (accId == null || t.accountId == accId || t.toAccountId == accId) && (cardId == null || t.cardId == cardId) &&
            (from == null || !FinanceCalc.dateOf(t.epochMillis).isBefore(from)) &&
            (to == null || !FinanceCalc.dateOf(t.epochMillis).isAfter(to)) &&
            (q.isBlank() || listOf(t.title, t.note, t.refNo, s.cat(t.categoryId)?.name ?: "", t.id.toString()).any { it.contains(q, ignoreCase = true) })
    }.let {
        when (sort) {
            SortBy.DATE_DESC -> it.sortedWith(compareByDescending<Transaction> { t -> t.epochMillis }.thenByDescending { t -> t.id })
            SortBy.DATE_ASC -> it.sortedWith(compareBy<Transaction> { t -> t.epochMillis }.thenBy { t -> t.id })
            SortBy.AMT_DESC -> it.sortedByDescending { t -> t.amount }
            SortBy.AMT_ASC -> it.sortedBy { t -> t.amount }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(q, { q = it }, placeholder = { Text("ค้นหารายการ / หมายเหตุ / เลขอ้างอิง") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Filled.FilterList, "filter") } },
            modifier = Modifier.fillMaxWidth())
        if (showFilters) {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("ประเภท", listOf<Pair<String?, String>>(null to "ทั้งหมด") + listOf(TxType.INCOME, TxType.EXPENSE, TxType.TRANSFER,
                    TxType.CARD_PAYMENT, TxType.DEBT_PAYMENT, TxType.INSTALLMENT_PAYMENT).map { it to typeLabel(it) }, type, { type = it })
                Dropdown("หมวดหมู่", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.categories.map { it.id to it.name }, catId, { catId = it })
                Dropdown("บัญชี", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.accounts.map { it.id to it.name }, accId, { accId = it })
                Dropdown("บัตรเครดิต", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.cards.map { it.id to it.name }, cardId, { cardId = it })
                Dropdown("เรียงตาม", SortBy.values().map { it to it.label }, sort, { sort = it ?: SortBy.DATE_DESC })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fromStr, { fromStr = it }, label = { Text("ตั้งแต่ (yyyy-MM-dd)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(toStr, { toStr = it }, label = { Text("ถึง") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { type = null; catId = null; accId = null; cardId = null; fromStr = ""; toStr = ""; q = "" }) { Text("ล้างตัวกรอง") }
            }
        }
        Text("${list.size} รายการ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
        LazyColumn(Modifier.fillMaxSize()) { items(list, key = { it.id }) { TxRow(it, s) { nav.go("tx:${it.id}") }; HorizontalDivider() } }
    }
}

@Composable
fun TransactionDetail(vm: MainViewModel, s: UiState, nav: Nav, id: Long) {
    val t = s.txs.firstOrNull { it.id == id }
    var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    ScreenScaffold("รายละเอียดรายการ", nav, actions = {
        if (t != null) {
            IconButton(onClick = { nav.go("edit:${t.id}") }) { Icon(Icons.Filled.Edit, "edit") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "delete") }
        }
    }) {
        if (t == null) { Text("ไม่พบรายการ (อาจถูกลบแล้ว)", Modifier.padding(16.dp)); return@ScreenScaffold }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardBox {
                Text(typeLabel(t.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigAmount(signedAmount(t.type, t.amount), amountColor(t.type))
                Text(t.title.ifBlank { "-" }, fontSize = 18.sp)
            }
            CardBox {
                @Composable fun row(k: String, v: String?) { if (!v.isNullOrBlank()) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v) } }
                val dt = java.time.Instant.ofEpochMilli(t.epochMillis).atZone(java.time.ZoneId.systemDefault())
                row("Transaction ID", "#${t.id}")
                row("วันที่", dt.toLocalDate().fmt(s.settings)); row("เวลา", dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                row("หมวดหมู่", s.cat(t.categoryId)?.name)
                row(if (t.type == TxType.TRANSFER) "บัญชีต้นทาง" else "บัญชี", s.acc(t.accountId)?.name)
                row("บัญชีปลายทาง", s.acc(t.toAccountId)?.name)
                row("บัตรเครดิต", s.card(t.cardId)?.name)
                row("หนี้", s.debts.firstOrNull { it.id == t.debtId }?.name)
                row("รายการผ่อน", s.insts.firstOrNull { it.id == t.installmentId }?.name)
                if (t.type == TxType.DEBT_PAYMENT) { row("เงินต้น", fmtMoney(s.settings, t.principal)); row("ดอกเบี้ย", fmtMoney(s.settings, t.interest)); row("ค่าธรรมเนียม", fmtMoney(s.settings, t.fee)) }
                row("หมายเหตุ", t.note); row("เลขที่อ้างอิง", t.refNo)
                if (t.scheduled) row("สถานะ", "Scheduled Transaction (สร้างจากรายการประจำ)")
            }
            Receipts.file(ctx, t.attachmentPath)?.let { f ->
                val bmp = remember(f) { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }
                if (bmp != null) CardBox { Text("ใบเสร็จ / สลิป"); Image(bmp, null, Modifier.fillMaxWidth()) }
            }
        }
    }
    if (confirm && t != null) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("ลบรายการนี้?") },
        text = { Text("ยอดบัญชีที่เกี่ยวข้องจะถูกปรับอัตโนมัติ") },
        confirmButton = { TextButton(onClick = { vm.delete(t.id); confirm = false; nav.back() }) { Text("ลบ") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("ยกเลิก") } })
}

/** รายการต้นทางของตัวเลขบน Home: key = "income:2026-11" | "expense:..." | "all:..." */
@Composable
fun DrillScreen(vm: MainViewModel, s: UiState, nav: Nav, key: String) {
    val (kind, ymS) = key.split(":").let { it[0] to it.getOrElse(1) { YearMonth.now().toString() } }
    val ym = YearMonth.parse(ymS)
    val (f, t) = FinanceCalc.monthRange(ym)
    val list = s.txs.filter { FinanceCalc.inRange(it, f, t) }.filter {
        when (kind) { "income" -> FinanceCalc.reportIncome(it) > 0; "expense" -> FinanceCalc.reportExpense(it) > 0
            else -> FinanceCalc.reportIncome(it) > 0 || FinanceCalc.reportExpense(it) > 0 }
    }
    ScreenScaffold(when (kind) { "income" -> "รายรับ $ym"; "expense" -> "รายจ่าย $ym"; else -> "รายการ $ym" }, nav) {
        LazyColumn(Modifier.padding(horizontal = 16.dp)) { items(list, key = { it.id }) { TxRow(it, s) { nav.go("tx:${it.id}") }; HorizontalDivider() } }
    }
}
