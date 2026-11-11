package com.pfm.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.Transaction
import com.pfm.app.domain.FinanceCalc
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun ReportsScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    var mode by rememberSaveable { mutableStateOf("month") } // week | month | year
    var offset by rememberSaveable { mutableIntStateOf(0) }  // 0 = ปัจจุบัน, -1 = ก่อนหน้า
    val today = LocalDate.now()
    fun range(off: Int): Pair<LocalDate, LocalDate> = when (mode) {
        "week" -> FinanceCalc.weekRange(today.plusWeeks(off.toLong()))
        "year" -> LocalDate.of(today.year + off, 1, 1) to LocalDate.of(today.year + off, 12, 31)
        else -> FinanceCalc.monthRange(YearMonth.from(today).plusMonths(off.toLong()))
    }
    val (f, t) = range(offset)
    val (pf, pt) = range(offset - 1)
    val cur = FinanceCalc.summarize(s.txs, f, t)
    val prev = FinanceCalc.summarize(s.txs, pf, pt)
    val inPeriod = s.txs.filter { FinanceCalc.inRange(it, f, t) }
    val byCat = FinanceCalc.expenseByCategory(s.txs, f, t).entries.sortedByDescending { it.value }
    val fin = LocalFin.current

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
        item {
            Chips(listOf("week" to "รายสัปดาห์", "month" to "รายเดือน", "year" to "รายปี"), mode) { mode = it; offset = 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset-- }) { Icon(Icons.Filled.ChevronLeft, "prev") }
                Text("${f.fmt(s.settings)} – ${t.fmt(s.settings)}", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { offset++ }, enabled = offset < 0) { Icon(Icons.Filled.ChevronRight, "next") }
            }
        }
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("รายรับ"); Text(fmtMoney(s.settings, cur.income), color = fin.income, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("รายจ่าย"); Text(fmtMoney(s.settings, cur.expense), color = fin.expense, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("เงินคงเหลือสุทธิ"); BigAmount(fmtMoney(s.settings, cur.net), if (cur.net >= 0) fin.income else fin.expense, 28)
                Spacer(Modifier.height(6.dp))
                Delta("รายรับ", cur.income, prev.income, true, s); Delta("รายจ่าย", cur.expense, prev.expense, false, s)
                byCat.firstOrNull()?.let { Text("ใช้จ่ายมากที่สุด: ${s.cat(it.key)?.name ?: "ไม่ระบุ"} ${fmtMoney(s.settings, it.value)}", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        item { CardBox { Text("รายรับ-รายจ่าย", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp)); BarChart(inPeriod, f, t, mode, fin.income, fin.expense) } }
        item {
            CardBox {
                Text("สัดส่วนรายจ่ายตามหมวดหมู่", fontWeight = FontWeight.SemiBold)
                val total = byCat.sumOf { it.value }.coerceAtLeast(1)
                if (byCat.isEmpty()) Text("ไม่มีรายจ่ายในช่วงนี้", color = MaterialTheme.colorScheme.onSurfaceVariant)
                byCat.forEach { e -> ProgressRow("${s.cat(e.key)?.name ?: "ไม่ระบุ"} · ${e.value * 100 / total}%", e.value, total, s.settings) }
            }
        }
        if (mode == "month") item {
            val ym = YearMonth.from(f)
            val debtDue = s.debts.filter { com.pfm.app.domain.FinanceCalc.debtRemaining(it, s.txs) > 0 }.sumOf { it.monthlyPayment }
            val instDue = FinanceCalc.installmentDueInMonth(s.insts, s.txs, ym)
            CardBox { Text("ภาระชำระเดือนนี้", fontWeight = FontWeight.SemiBold)
                Text("ยอดหนี้ที่ต้องชำระ: ${fmtMoney(s.settings, debtDue)}"); Text("ยอดผ่อนชำระ: ${fmtMoney(s.settings, instDue)}") }
        }
        item { Text("รายการทั้งหมดในช่วงนี้ (${inPeriod.size})", fontWeight = FontWeight.SemiBold) }
        item { CardBox { if (inPeriod.isEmpty()) Text("ไม่มีรายการ"); inPeriod.forEach { TxRow(it, s) { nav.go("tx:${it.id}") } } } }
    }
}

@Composable
private fun Delta(label: String, cur: Long, prev: Long, upIsGood: Boolean, s: UiState) {
    val pct = FinanceCalc.pctChange(cur, prev)
    val good = if (pct == null) null else (pct >= 0) == upIsGood
    val c = when (good) { true -> LocalFin.current.income; false -> LocalFin.current.expense; null -> MaterialTheme.colorScheme.onSurfaceVariant }
    Text("$label เทียบช่วงก่อนหน้า: " + (pct?.let { (if (it >= 0) "▲ +" else "▼ ") + "%.1f%%".format(it) } ?: "—") + " (ก่อนหน้า ${fmtMoney(s.settings, prev)})",
        color = c, fontSize = 13.sp)
}

@Composable
private fun BarChart(txs: List<Transaction>, from: LocalDate, to: LocalDate, mode: String, incomeColor: androidx.compose.ui.graphics.Color, expenseColor: androidx.compose.ui.graphics.Color) {
    // รายวัน (สัปดาห์/เดือน) หรือรายเดือน (ปี)
    val buckets: List<Pair<String, Pair<Long, Long>>> =
        if (mode == "year") (1..12).map { m ->
            val l = txs.filter { FinanceCalc.dateOf(it.epochMillis).monthValue == m }
            "$m" to (l.sumOf { FinanceCalc.reportIncome(it) } to l.sumOf { FinanceCalc.reportExpense(it) })
        } else generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { d ->
            val l = txs.filter { FinanceCalc.dateOf(it.epochMillis) == d }
            (if (mode == "week") d.dayOfWeek.name.take(3) else "${d.dayOfMonth}") to (l.sumOf { FinanceCalc.reportIncome(it) } to l.sumOf { FinanceCalc.reportExpense(it) })
        }.toList()
    val maxV = buckets.maxOfOrNull { maxOf(it.second.first, it.second.second) }?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = buckets.size.coerceAtLeast(1)
        val slot = size.width / n
        val bw = (slot * 0.38f).coerceAtLeast(2f)
        buckets.forEachIndexed { i, (_, v) ->
            val x = i * slot + slot / 2
            val hi = size.height * v.first / maxV; val he = size.height * v.second / maxV
            drawRect(incomeColor, Offset(x - bw, size.height - hi), Size(bw, hi))
            drawRect(expenseColor, Offset(x, size.height - he), Size(bw, he))
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("■ รายรับ", color = incomeColor, fontSize = 12.sp); Text("■ รายจ่าย", color = expenseColor, fontSize = 12.sp)
        Text(if (mode == "year") "รายเดือน" else "รายวัน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
