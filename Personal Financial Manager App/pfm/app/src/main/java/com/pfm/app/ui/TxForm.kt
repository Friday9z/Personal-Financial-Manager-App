package com.pfm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.Transaction
import com.pfm.app.data.TxType
import com.pfm.app.domain.FinanceCalc
import com.pfm.app.domain.Money
import java.time.LocalDate

data class Prefill(
    val type: String = TxType.EXPENSE, val amount: String = "", val title: String = "",
    val date: LocalDate = LocalDate.now(), val refNo: String = "", val attachment: String? = null
)

/**
 * ฟอร์มบันทึกรายการ ใช้ทั้ง Quick Add (full=false) และหน้า Add/Edit (full=true)
 * ช่องจำนวนเงินเป็นช่องแรก/เด่นที่สุด; ค่าเก็บใน rememberSaveable จึงไม่หายเมื่อสลับหน้า
 */
@Composable
fun TxForm(
    vm: MainViewModel, s: UiState, edit: Transaction?, full: Boolean, prefill: Prefill = Prefill(),
    onSaved: (String) -> Unit, onScan: (() -> Unit)? = null, keyPrefix: String = "f", scrollable: Boolean = true
) {
    val init = remember(edit?.id) {
        edit?.let {
            Prefill(it.type, Money.toInput(it.amount), it.title, FinanceCalc.dateOf(it.epochMillis), it.refNo, it.attachmentPath)
        } ?: prefill
    }
    var type by rememberSaveable(keyPrefix, edit?.id, init.type) { mutableStateOf(init.type) }
    var amount by rememberSaveable(keyPrefix, edit?.id, init.amount) { mutableStateOf(init.amount) }
    var title by rememberSaveable(keyPrefix, edit?.id, init.title) { mutableStateOf(init.title) }
    var dateStr by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(init.date.toString()) }
    var note by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.note ?: "") }
    var ref by rememberSaveable(keyPrefix, edit?.id, init.refNo) { mutableStateOf(init.refNo) }
    var catId by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.categoryId) }
    var src by rememberSaveable(keyPrefix, edit?.id) {
        mutableStateOf(edit?.let { t -> t.cardId?.let { "c:$it" } ?: t.accountId?.let { "a:$it" } } ?: "")
    }
    var toAcc by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.toAccountId) }
    var debtId by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.debtId) }
    var instId by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.installmentId) }
    var payCardId by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.cardId.takeIf { edit?.type == TxType.CARD_PAYMENT }) }
    var principal by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.principal?.let { Money.toInput(it) } ?: "") }
    var interest by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.interest?.let { Money.toInput(it) } ?: "") }
    var fee by rememberSaveable(keyPrefix, edit?.id) { mutableStateOf(edit?.fee?.let { Money.toInput(it) } ?: "") }
    var attach by rememberSaveable(keyPrefix, edit?.id, init.attachment) { mutableStateOf(init.attachment) }
    var error by remember { mutableStateOf<String?>(null) }
    val date = runCatching { LocalDate.parse(dateStr) }.getOrDefault(LocalDate.now())

    // บัญชีเริ่มต้น = บัญชีแรก
    LaunchedEffect(s.accounts.size) {
        if (src.isEmpty() && edit == null) s.accounts.firstOrNull()?.let { src = "a:${it.id}" }
    }

    val types = if (full) listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER, TxType.CARD_PAYMENT, TxType.DEBT_PAYMENT, TxType.INSTALLMENT_PAYMENT)
    else listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)

    Column(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(types) { t -> FilterChip(selected = type == t, onClick = { type = t; catId = null }, label = { Text(typeLabel(t)) }) }
        }
        if (type != TxType.DEBT_PAYMENT) {
            OutlinedTextField(
                value = amount, onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) amount = v },
                label = { Text("จำนวนเงิน (${currencySymbol(s.settings)})") }, singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (type == TxType.EXPENSE || type == TxType.INCOME) {
            val kind = if (type == TxType.INCOME) "INCOME" else "EXPENSE"
            val cats = s.categories.filter { it.kind == kind }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cats) { c ->
                    val sel = catId == c.id
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { catId = c.id }.padding(4.dp)) {
                        IconBadge(iconFor(c.icon), if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 46)
                        Text(c.name, fontSize = 11.sp, color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // แหล่งเงิน: บัญชี (+ บัตรเครดิตเมื่อเป็นรายจ่าย)
        val srcOptions: List<Pair<String?, String>> = buildList {
            s.accounts.forEach { add("a:${it.id}" to it.name) }
            if (type == TxType.EXPENSE) s.cards.forEach { add("c:${it.id}" to "💳 ${it.name}") }
        }
        val srcLabel = when (type) { TxType.INCOME -> "รับเข้าบัญชี"; TxType.TRANSFER -> "จากบัญชี"; TxType.EXPENSE -> "จ่ายด้วย"; else -> "ชำระจากบัญชี" }
        Dropdown(srcLabel, srcOptions, src.ifEmpty { null }, { src = it ?: "" })

        if (type == TxType.TRANSFER) Dropdown("ไปยังบัญชี", s.accounts.filter { "a:${it.id}" != src }.map { it.id as Long? to it.name }, toAcc, { toAcc = it })
        if (type == TxType.CARD_PAYMENT) Dropdown("บัตรเครดิตที่ชำระ", s.cards.map { it.id as Long? to it.name }, payCardId, { payCardId = it })
        if (type == TxType.INSTALLMENT_PAYMENT) Dropdown("รายการผ่อน", s.insts.map { it.id as Long? to it.name }, instId, {
            instId = it; s.insts.firstOrNull { i -> i.id == it }?.let { i -> if (amount.isBlank()) amount = Money.toInput(i.monthlyAmount) }
        })
        if (type == TxType.DEBT_PAYMENT) {
            Dropdown("หนี้ที่ชำระ", s.debts.map { it.id as Long? to it.name }, debtId, { debtId = it })
            MoneyField("เงินต้น", principal) { principal = it }
            MoneyField("ดอกเบี้ย", interest) { interest = it }
            MoneyField("ค่าธรรมเนียม", fee) { fee = it }
            val tot = (Money.parse(principal) ?: 0) + (Money.parse(interest) ?: 0) + (Money.parse(fee) ?: 0)
            Text("ยอดชำระรวม ${fmtMoney(s.settings, tot)}", fontWeight = FontWeight.SemiBold)
            s.debts.firstOrNull { it.id == debtId }?.let { d ->
                val rem = FinanceCalc.debtRemaining(d, s.txs.filter { it.id != edit?.id })
                Text("หนี้คงเหลือหลังชำระ ${fmtMoney(s.settings, (rem - (Money.parse(principal) ?: 0)).coerceAtLeast(0))}", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        DateField("วันที่", date, s.settings, { dateStr = it.toString() })
        OutlinedTextField(title, { title = it }, label = { Text("รายละเอียด / ชื่อรายการ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (full) {
            OutlinedTextField(note, { note = it }, label = { Text("หมายเหตุ") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(ref, { ref = it }, label = { Text("เลขที่อ้างอิง") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onScan != null) OutlinedButton(onClick = onScan) { Text("สแกนใบเสร็จ/สลิป") }
                if (attach != null) { Spacer(Modifier.width(8.dp)); AssistChip(onClick = { attach = null }, label = { Text("แนบรูปแล้ว (แตะเพื่อเอาออก)") }) }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(modifier = Modifier.fillMaxWidth().height(52.dp), onClick = {
            val srcAcc = src.takeIf { it.startsWith("a:") }?.drop(2)?.toLongOrNull()
            val srcCard = src.takeIf { it.startsWith("c:") }?.drop(2)?.toLongOrNull()
            val pr = Money.parse(principal) ?: 0; val ir = Money.parse(interest) ?: 0; val fr = Money.parse(fee) ?: 0
            val amt = if (type == TxType.DEBT_PAYMENT) pr + ir + fr else (Money.parse(amount) ?: 0)
            val base = edit ?: vm.newTx(type, amt, title, date)
            val t: Transaction = base.copy(
                type = type, amount = amt, title = title.trim(),
                epochMillis = if (edit != null && FinanceCalc.dateOf(edit.epochMillis) == date) edit.epochMillis else vm.repo.millisOf(date, java.time.LocalTime.now().hour),
                categoryId = if (type == TxType.EXPENSE || type == TxType.INCOME) catId else null,
                accountId = if (type == TxType.EXPENSE && srcCard != null) null else srcAcc,
                toAccountId = if (type == TxType.TRANSFER) toAcc else null,
                cardId = when (type) { TxType.EXPENSE -> srcCard; TxType.CARD_PAYMENT -> payCardId; else -> null },
                debtId = if (type == TxType.DEBT_PAYMENT) debtId else null,
                installmentId = if (type == TxType.INSTALLMENT_PAYMENT) instId else null,
                principal = if (type == TxType.DEBT_PAYMENT) pr else 0, interest = if (type == TxType.DEBT_PAYMENT) ir else 0,
                fee = if (type == TxType.DEBT_PAYMENT) fr else 0,
                note = note.trim(), refNo = ref.trim(), attachmentPath = attach
            )
            vm.save(t) { err ->
                if (err != null) error = err else {
                    error = null
                    val msg = if (edit != null) "แก้ไขรายการแล้ว" else "บันทึกสำเร็จ ${typeLabel(type)} ${fmtMoney(s.settings, amt)}"
                    if (edit == null) { amount = ""; title = ""; note = ""; ref = ""; attach = null; principal = ""; interest = ""; fee = "" }
                    onSaved(msg)
                }
            }
        }) { Text("Save", fontSize = 18.sp) }
    }
}
