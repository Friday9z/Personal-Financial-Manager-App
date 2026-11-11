package com.pfm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.*
import com.pfm.app.domain.FinanceCalc
import com.pfm.app.domain.Money
import java.time.LocalDate
import java.time.YearMonth

private fun intOr(s: String, d: Int) = s.trim().toIntOrNull() ?: d
private fun dblOr(s: String) = s.trim().toDoubleOrNull() ?: 0.0

// ---------------- Credit cards + installments ----------------
@Composable
fun CardsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<CreditCard?>(null) }
    var creating by remember { mutableStateOf(false) }
    var instFor by remember { mutableStateOf<CreditCard?>(null) }
    var instEdit by remember { mutableStateOf<Installment?>(null) }
    ListScreen("บัตรเครดิต", nav, { creating = true }) {
        val ym = YearMonth.now()
        val monthlyInst = FinanceCalc.installmentDueInMonth(s.insts, s.txs, ym)
        if (s.insts.isNotEmpty()) CardBox { Text("ยอดผ่อนที่ต้องจ่ายเดือนนี้"); BigAmount(fmtMoney(s.settings, monthlyInst), size = 24)
            Text("ยอดผ่อนรวมต่อเดือน ${fmtMoney(s.settings, s.insts.filter { FinanceCalc.installmentRemainingMonths(it, s.txs) > 0 }.sumOf { it.monthlyAmount })}", fontSize = 12.sp) }
        if (s.cards.isEmpty()) Text("ยังไม่มีบัตรเครดิต", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.cards.forEach { c ->
            val owed = FinanceCalc.cardOutstanding(c, s.txs, s.insts)
            CardBox(onClick = { editing = c }) {
                Row(verticalAlignment = Alignment.CenterVertically) { IconBadge(iconFor("credit_card"), Color(c.color)); Spacer(Modifier.width(10.dp))
                    Column { Text(c.name, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${c.bank} ${c.cardType} · ตัดรอบวันที่ ${c.statementDay} · ครบกำหนดวันที่ ${c.dueDay}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Spacer(Modifier.height(6.dp))
                Text("วงเงิน ${fmtMoney(s.settings, c.creditLimit)}"); Text("ยอดใช้จ่าย ${fmtMoney(s.settings, owed)}")
                Text("วงเงินคงเหลือ ${fmtMoney(s.settings, c.creditLimit - owed)}", fontWeight = FontWeight.SemiBold)
                ProgressRow("วงเงินที่ใช้ไป", owed.coerceAtLeast(0), c.creditLimit, s.settings)
                val mine = s.insts.filter { it.cardId == c.id }
                if (mine.isNotEmpty()) { Text("รายการผ่อน", fontWeight = FontWeight.SemiBold)
                    mine.forEach { i -> val paid = FinanceCalc.installmentPaidCount(i, s.txs)
                        Text("• ${i.name}: ผ่อนแล้ว $paid/${i.months} เหลือ ${FinanceCalc.installmentRemainingMonths(i, s.txs)} เดือน · งวดละ ${fmtMoney(s.settings, i.monthlyAmount)} · คงเหลือ ${fmtMoney(s.settings, FinanceCalc.installmentRemainingAmount(i, s.txs))}",
                            fontSize = 12.sp, modifier = Modifier.clickableNoRipple { instEdit = i; instFor = c }) } }
                TextButton(onClick = { instEdit = null; instFor = c }) { Text("+ เพิ่มรายการผ่อน") }
            }
        }
    }
    if (creating || editing != null) CardDialog(vm, s, editing) { creating = false; editing = null }
    instFor?.let { c -> InstallmentDialog(vm, c, instEdit) { instFor = null; instEdit = null } }
}

@Composable
private fun CardDialog(vm: MainViewModel, s: UiState, c: CreditCard?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }; var bank by remember { mutableStateOf(c?.bank ?: "") }
    var type by remember { mutableStateOf(c?.cardType ?: "") }; var limit by remember { mutableStateOf(c?.creditLimit?.let { Money.toInput(it) } ?: "") }
    var st by remember { mutableStateOf((c?.statementDay ?: 1).toString()) }; var due by remember { mutableStateOf((c?.dueDay ?: 20).toString()) }
    var pay by remember { mutableStateOf(c?.payAccountId) }; var color by remember { mutableLongStateOf(c?.color ?: palette[4]) }
    var err by remember { mutableStateOf<String?>(null) }
    FormDialog(if (c == null) "เพิ่มบัตรเครดิต" else "แก้ไขบัตร", onDismiss, onOk = {
        val l = Money.parse(limit) ?: 0
        if (name.isBlank() || l <= 0) { err = "กรอกชื่อและวงเงิน"; return@FormDialog }
        val n = (c ?: CreditCard(name = name, creditLimit = l)).copy(name = name.trim(), bank = bank.trim(), cardType = type.trim(), creditLimit = l,
            statementDay = intOr(st, 1).coerceIn(1, 31), dueDay = intOr(due, 20).coerceIn(1, 31), payAccountId = pay, color = color)
        vm.run { if (c == null) db.cards().insert(n) else db.cards().update(n) }; onDismiss()
    }, extra = { if (c != null) TextButton(onClick = { vm.run { db.cards().archive(c.id) }; onDismiss() }) { Text("ซ่อน") } }) {
        TextF("ชื่อบัตร", name) { name = it }; TextF("ธนาคาร", bank) { bank = it }; TextF("ประเภทบัตร (Visa/Master...)", type) { type = it }
        MoneyField("วงเงิน", limit) { limit = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextF("วันตัดรอบ", st) { st = it }; TextF("วันครบกำหนด", due) { due = it } }
        Dropdown("บัญชีที่ใช้ชำระ", listOf<Pair<Long?, String>>(null to "-") + s.accounts.map { it.id to it.name }, pay, { pay = it })
        ColorPick(color) { color = it }; err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun InstallmentDialog(vm: MainViewModel, c: CreditCard, i: Installment?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(i?.name ?: "") }; var total by remember { mutableStateOf(i?.totalAmount?.let { Money.toInput(it) } ?: "") }
    var months by remember { mutableStateOf((i?.months ?: 10).toString()) }; var rate by remember { mutableStateOf((i?.interestRatePct ?: 0.0).toString()) }
    var start by remember { mutableStateOf(LocalDate.ofEpochDay(i?.startDay ?: LocalDate.now().toEpochDay())) }
    var err by remember { mutableStateOf<String?>(null) }
    val m = intOr(months, 0); val t = Money.parse(total) ?: 0
    // ยอดผ่อนต่อเดือนปัดขึ้น แล้วงวดสุดท้ายไม่ต่างเกินสตางค์ — เก็บ monthly ตามที่คำนวณ (total/months)
    val monthly = if (m > 0) (t + m - 1) / m else 0
    FormDialog(if (i == null) "เพิ่มรายการผ่อน (${c.name})" else "แก้ไขรายการผ่อน", onDismiss, onOk = {
        if (name.isBlank() || t <= 0 || m <= 0) { err = "กรอกชื่อ ยอดผ่อนทั้งหมด และจำนวนงวด"; return@FormDialog }
        val n = (i ?: Installment(name = name, cardId = c.id, totalAmount = t, months = m, monthlyAmount = monthly, startDay = start.toEpochDay()))
            .copy(name = name.trim(), totalAmount = t, months = m, monthlyAmount = monthly, interestRatePct = dblOr(rate), startDay = start.toEpochDay())
        vm.run { if (i == null) db.cards().insertInstallment(n) else db.cards().updateInstallment(n) }; onDismiss()
    }, extra = { if (i != null) TextButton(onClick = { vm.run { db.cards().deleteInstallment(i) }; onDismiss() }) { Text("ลบ") } }) {
        TextF("ชื่อสินค้า", name) { name = it }; MoneyField("ยอดผ่อนทั้งหมด (รวมดอกเบี้ย)", total) { total = it }
        TextF("จำนวนงวด", months) { months = it }; TextF("อัตราดอกเบี้ย % ต่อปี", rate) { rate = it }
        Text("ผ่อนต่อเดือน ${Money.format(monthly)} · สิ้นสุด ${start.plusMonths((m - 1).coerceAtLeast(0).toLong())}", fontWeight = FontWeight.SemiBold)
        DateField("วันที่เริ่มผ่อน", start, emptyMap(), { start = it })
        Text("บันทึกการจ่ายแต่ละงวดผ่านปุ่ม + เลือก \"ชำระงวดผ่อน\"", fontSize = 12.sp)
        err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ---------------- Debts ----------------
fun debtKindLabel(k: String) = when (k) { DebtKind.CREDIT_CARD -> "บัตรเครดิต"; DebtKind.PERSONAL_LOAN -> "สินเชื่อส่วนบุคคล"; DebtKind.INSTALLMENT -> "ผ่อนสินค้า"
    DebtKind.BANK -> "ธนาคาร"; DebtKind.PERSON -> "บุคคล"; else -> "อื่น ๆ" }

@Composable
fun DebtsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<Debt?>(null) }; var creating by remember { mutableStateOf(false) }
    val ym = YearMonth.now()
    ListScreen("หนี้สิน", nav, { creating = true }) {
        val remain = s.debts.sumOf { FinanceCalc.debtRemaining(it, s.txs) }
        CardBox { Text("หนี้คงเหลือทั้งหมด"); BigAmount(fmtMoney(s.settings, remain), LocalFin.current.expense)
            Text("หนี้เริ่มต้นรวม ${fmtMoney(s.settings, s.debts.sumOf { it.initialAmount })} · ต้องชำระเดือนนี้ ${fmtMoney(s.settings, s.debts.filter { FinanceCalc.debtRemaining(it, s.txs) > 0 }.sumOf { it.monthlyPayment })}", fontSize = 12.sp) }
        s.debts.sortedBy { it.dueDay }.forEach { d ->
            val rem = FinanceCalc.debtRemaining(d, s.txs)
            CardBox(onClick = { editing = d }) {
                Text(d.name, fontWeight = FontWeight.SemiBold); Text("${debtKindLabel(d.kind)} · ดอกเบี้ย ${d.interestRatePct}% · ครบกำหนดวันที่ ${d.dueDay}", fontSize = 12.sp)
                ProgressRow("ชำระแล้ว", (d.initialAmount - rem).coerceAtLeast(0), d.initialAmount, s.settings)
                Text("คงเหลือ ${fmtMoney(s.settings, rem)} · ชำระต่อเดือน ${fmtMoney(s.settings, d.monthlyPayment)}")
                val hist = s.txs.filter { it.debtId == d.id && it.type == TxType.DEBT_PAYMENT }
                if (hist.isNotEmpty()) { Text("ประวัติการชำระ", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    hist.take(5).forEach { TxRow(it, s) { nav.go("tx:${it.id}") } } }
            }
        }
        Text("ชำระหนี้: กดปุ่ม + เลือกประเภท \"ชำระหนี้\" เพื่อแยกเงินต้น/ดอกเบี้ย/ค่าธรรมเนียม", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (creating || editing != null) {
        val d = editing
        var name by remember(d) { mutableStateOf(d?.name ?: "") }; var kind by remember(d) { mutableStateOf(d?.kind ?: DebtKind.OTHER) }
        var amt by remember(d) { mutableStateOf(d?.initialAmount?.let { Money.toInput(it) } ?: "") }; var rate by remember(d) { mutableStateOf((d?.interestRatePct ?: 0.0).toString()) }
        var monthly by remember(d) { mutableStateOf(d?.monthlyPayment?.let { Money.toInput(it) } ?: "") }; var due by remember(d) { mutableStateOf((d?.dueDay ?: 1).toString()) }
        var term by remember(d) { mutableStateOf((d?.termMonths ?: 0).toString()) }; var pay by remember(d) { mutableStateOf(d?.payAccountId) }
        val close = { creating = false; editing = null }
        FormDialog(if (d == null) "เพิ่มหนี้" else "แก้ไขหนี้", close, onOk = {
            val a = Money.parse(amt) ?: 0; if (name.isBlank() || a <= 0) return@FormDialog
            val n = (d ?: Debt(name = name, initialAmount = a)).copy(name = name.trim(), kind = kind, initialAmount = a, interestRatePct = dblOr(rate),
                monthlyPayment = Money.parse(monthly) ?: 0, dueDay = intOr(due, 1).coerceIn(1, 31), termMonths = intOr(term, 0), payAccountId = pay)
            vm.run { if (d == null) db.debts().insert(n) else db.debts().update(n) }; close()
        }, extra = { if (d != null) TextButton(onClick = { vm.run { db.debts().archive(d.id) }; close() }) { Text("ซ่อน") } }) {
            TextF("ชื่อหนี้", name) { name = it }
            Dropdown("ประเภทหนี้", listOf(DebtKind.CREDIT_CARD, DebtKind.PERSONAL_LOAN, DebtKind.INSTALLMENT, DebtKind.BANK, DebtKind.PERSON, DebtKind.OTHER).map { it as String? to debtKindLabel(it) }, kind, { kind = it ?: DebtKind.OTHER })
            MoneyField("ยอดหนี้เริ่มต้น", amt) { amt = it }; TextF("อัตราดอกเบี้ย % ต่อปี", rate) { rate = it }
            MoneyField("ยอดชำระต่อเดือน", monthly) { monthly = it }; TextF("วันครบกำหนดชำระ (1-31)", due) { due = it }
            TextF("ระยะเวลาผ่อน (เดือน)", term) { term = it }
            Dropdown("บัญชีที่ใช้ชำระ", listOf<Pair<Long?, String>>(null to "-") + s.accounts.map { it.id to it.name }, pay, { pay = it })
        }
    }
}

// ---------------- Budgets ----------------
@Composable
fun BudgetsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<Budget?>(null) }; var creating by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    ListScreen("งบประมาณ", nav, { creating = true }, ) {
        TextButton(onClick = {
            // คัดลอกงบที่ใช้งานอยู่ไปเริ่มเดือนหน้า
            val next = YearMonth.now().plusMonths(1).atDay(1).toEpochDay()
            vm.run { db.budgets().getAll().filter { FinanceCalc.budgetActive(it, today) && it.period == BudgetPeriod.MONTHLY }
                .forEach { db.budgets().insert(it.copy(id = 0, fromDay = next, toDay = null, name = it.name)) } }
        }) { Text("คัดลอกงบเดือนนี้ไปเดือนหน้า") }
        if (s.budgets.isEmpty()) Text("ยังไม่ได้ตั้งงบประมาณ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        s.budgets.forEach { b ->
            val active = FinanceCalc.budgetActive(b, today)
            CardBox(onClick = { editing = b }) {
                ProgressRow((s.cat(b.categoryId)?.name ?: b.name) + " · " + periodLabel(b.period) + if (!active) " (ล่วงหน้า/หมดอายุ)" else "",
                    if (active) FinanceCalc.budgetSpent(b, s.txs, today) else 0, b.limitAmount, s.settings)
                Text("แจ้งเตือนที่ ${b.alertPercents}% และเมื่อเกินงบ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (creating || editing != null) {
        val b = editing
        var name by remember(b) { mutableStateOf(b?.name ?: "") }; var period by remember(b) { mutableStateOf(b?.period ?: BudgetPeriod.MONTHLY) }
        var limit by remember(b) { mutableStateOf(b?.limitAmount?.let { Money.toInput(it) } ?: "") }; var cat by remember(b) { mutableStateOf(b?.categoryId) }
        var acc by remember(b) { mutableStateOf(b?.accountId) }; var alerts by remember(b) { mutableStateOf(b?.alertPercents ?: "50,80,100") }
        var from by remember(b) { mutableStateOf(LocalDate.ofEpochDay(b?.fromDay ?: today.toEpochDay())) }
        val close = { creating = false; editing = null }
        FormDialog(if (b == null) "ตั้งงบประมาณ" else "แก้ไขงบประมาณ", close, onOk = {
            val l = Money.parse(limit) ?: 0; if (l <= 0) return@FormDialog
            val n = (b ?: Budget(name = name, limitAmount = l, fromDay = from.toEpochDay())).copy(name = name.ifBlank { "งบรวม" }.trim(), period = period, limitAmount = l,
                categoryId = cat, accountId = acc, fromDay = from.toEpochDay(), alertPercents = alerts.ifBlank { "100" })
            vm.run { if (b == null) db.budgets().insert(n) else db.budgets().update(n) }; close()
        }, extra = { if (b != null) TextButton(onClick = { vm.run { db.budgets().delete(b) }; close() }) { Text("ลบ") } }) {
            TextF("ชื่องบ (ว่าง = ใช้ชื่อหมวด/งบรวม)", name) { name = it }
            Dropdown("รอบงบ", listOf(BudgetPeriod.DAILY, BudgetPeriod.WEEKLY, BudgetPeriod.MONTHLY, BudgetPeriod.YEARLY).map { it as String? to periodLabel(it) }, period, { period = it ?: BudgetPeriod.MONTHLY })
            MoneyField("วงเงินงบประมาณ", limit) { limit = it }
            Dropdown("หมวดหมู่ (ว่าง = ทุกหมวด)", listOf<Pair<Long?, String>>(null to "งบรวมทุกหมวด") + s.categories.filter { it.kind == "EXPENSE" }.map { it.id to it.name }, cat, { cat = it })
            Dropdown("บัญชี (ว่าง = ทุกบัญชี)", listOf<Pair<Long?, String>>(null to "ทุกบัญชี") + s.accounts.map { it.id to it.name }, acc, { acc = it })
            TextF("เปอร์เซ็นต์แจ้งเตือน คั่นด้วย , เช่น 50,80,100", alerts) { alerts = it }
            DateField("เริ่มมีผลวันที่ (วางแผนล่วงหน้าได้)", from, s.settings, { from = it })
        }
    }
}

fun periodLabel(p: String) = when (p) { BudgetPeriod.DAILY -> "รายวัน"; BudgetPeriod.WEEKLY -> "รายสัปดาห์"; BudgetPeriod.YEARLY -> "รายปี"; else -> "รายเดือน" }

// ---------------- Recurring ----------------
fun freqLabel(f: String) = when (f) { Freq.DAILY -> "ทุกวัน"; Freq.WEEKLY -> "ทุกสัปดาห์"; Freq.MONTHLY -> "ทุกเดือน"; Freq.YEARLY -> "ทุกปี"; else -> "กำหนดเอง (ทุก N วัน)" }

@Composable
fun RecurringScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var editing by remember { mutableStateOf<Recurring?>(null) }; var creating by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<Pair<Recurring, Long>>>(emptyList()) }
    var upcoming by remember { mutableStateOf<List<Triple<Recurring, Long, Boolean>>>(emptyList()) }
    LaunchedEffect(s.recurring, s.txs.size) { pending = vm.repo.pendingRecurring(); upcoming = vm.repo.upcoming(30) }
    ListScreen("รายการประจำ", nav, { creating = true }) {
        if (pending.isNotEmpty()) CardBox { Text("รอยืนยัน", fontWeight = FontWeight.SemiBold)
            pending.forEach { (r, d) -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.title} ${fmtMoney(s.settings, r.amount)} · ${LocalDate.ofEpochDay(d).fmt(s.settings)}", Modifier.weight(1f), fontSize = 13.sp)
                TextButton(onClick = { vm.run { confirmRecurring(r, d) } }) { Text("ยืนยัน") }
                TextButton(onClick = { vm.run { skipRecurring(r, d) }; pending = pending - (r to d) }) { Text("ข้าม") } } } }
        if (upcoming.isNotEmpty()) CardBox { Text("กำลังจะเกิดขึ้น (30 วัน)", fontWeight = FontWeight.SemiBold)
            upcoming.take(8).forEach { (r, d, auto) -> Text("${LocalDate.ofEpochDay(d).fmt(s.settings)} · ${r.title} ${fmtMoney(s.settings, r.amount)}${if (auto) " (อัตโนมัติ)" else " (รอยืนยัน)"}", fontSize = 13.sp) } }
        s.recurring.forEach { r ->
            CardBox(onClick = { editing = r }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text("${if (r.type == TxType.INCOME) "รายรับ" else "รายจ่าย"} ${fmtMoney(s.settings, r.amount)} · ${freqLabel(r.freq)} · เริ่ม ${LocalDate.ofEpochDay(r.startDay).fmt(s.settings)}" +
                        (r.endDay?.let { " · สิ้นสุด ${LocalDate.ofEpochDay(it).fmt(s.settings)}" } ?: "") + if (r.autoCreate) " · สร้างอัตโนมัติ" else " · ถามก่อนบันทึก", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(r.enabled, { on -> vm.run { db.recurring().update(r.copy(enabled = on)) } }) }
            }
        }
    }
    if (creating || editing != null) {
        val r = editing
        var title by remember(r) { mutableStateOf(r?.title ?: "") }; var type by remember(r) { mutableStateOf(r?.type ?: TxType.EXPENSE) }
        var amt by remember(r) { mutableStateOf(r?.amount?.let { Money.toInput(it) } ?: "") }; var cat by remember(r) { mutableStateOf(r?.categoryId) }
        var acc by remember(r) { mutableStateOf(r?.accountId ?: s.accounts.firstOrNull()?.id) }
        var start by remember(r) { mutableStateOf(LocalDate.ofEpochDay(r?.startDay ?: LocalDate.now().toEpochDay())) }
        var hasEnd by remember(r) { mutableStateOf(r?.endDay != null) }; var end by remember(r) { mutableStateOf(LocalDate.ofEpochDay(r?.endDay ?: LocalDate.now().plusYears(1).toEpochDay())) }
        var freq by remember(r) { mutableStateOf(r?.freq ?: Freq.MONTHLY) }; var every by remember(r) { mutableStateOf((r?.interval ?: 1).toString()) }
        var auto by remember(r) { mutableStateOf(r?.autoCreate ?: true) }
        val close = { creating = false; editing = null }
        FormDialog(if (r == null) "เพิ่มรายการประจำ" else "แก้ไขรายการประจำ", close, onOk = {
            val a = Money.parse(amt) ?: 0; if (title.isBlank() || a <= 0 || acc == null) return@FormDialog
            val n = (r ?: Recurring(title = title, type = type, amount = a, startDay = start.toEpochDay())).copy(title = title.trim(), type = type, amount = a, categoryId = cat, accountId = acc,
                startDay = start.toEpochDay(), endDay = if (hasEnd) end.toEpochDay() else null, freq = freq, interval = intOr(every, 1).coerceAtLeast(1), autoCreate = auto)
            vm.run { if (r == null) db.recurring().insert(n) else db.recurring().update(n); processRecurring() }; close()
        }, extra = { if (r != null) TextButton(onClick = { vm.run { db.recurring().delete(r) }; close() }) { Text("ยกเลิกรายการ") } }) {
            TextF("ชื่อรายการ (เช่น เงินเดือน, ค่าเช่า)", title) { title = it }
            Chips(listOf(TxType.EXPENSE to "รายจ่าย", TxType.INCOME to "รายรับ"), type) { type = it; cat = null }
            MoneyField("จำนวนเงิน", amt) { amt = it }
            Dropdown("หมวดหมู่", listOf<Pair<Long?, String>>(null to "-") + s.categories.filter { it.kind == if (type == TxType.INCOME) "INCOME" else "EXPENSE" }.map { it.id to it.name }, cat, { cat = it })
            Dropdown("บัญชี", s.accounts.map { it.id as Long? to it.name }, acc, { acc = it })
            Dropdown("ความถี่", listOf(Freq.DAILY, Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY, Freq.CUSTOM).map { it as String? to freqLabel(it) }, freq, { freq = it ?: Freq.MONTHLY })
            TextF(if (freq == Freq.CUSTOM) "ทุกกี่วัน" else "ทุกกี่รอบ (1 = ทุกรอบ)", every) { every = it }
            DateField("วันที่เริ่มต้น (กำหนดวันที่ของเดือนด้วยวันนี้)", start, s.settings, { start = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Text("กำหนดวันสิ้นสุด", Modifier.weight(1f)); Switch(hasEnd, { hasEnd = it }) }
            if (hasEnd) DateField("วันที่สิ้นสุด", end, s.settings, { end = it })
            Row(verticalAlignment = Alignment.CenterVertically) { Text(if (auto) "สร้างรายการอัตโนมัติ" else "แจ้งเตือนให้ยืนยันก่อนบันทึก", Modifier.weight(1f)); Switch(auto, { auto = it }) }
        }
    }
}
