package com.pfm.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.domain.FinanceCalc
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun HomeScreen(vm: MainViewModel, s: UiState, nav: Nav, snack: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var quick by rememberSaveable { mutableStateOf(false) }
    val ym = YearMonth.now()
    val (f, t) = FinanceCalc.monthRange(ym)
    val (pf, pt) = FinanceCalc.monthRange(ym.minusMonths(1))
    val cur = FinanceCalc.summarize(s.txs, f, t)
    val prev = FinanceCalc.summarize(s.txs, pf, pt)
    val fin = LocalFin.current
    val today = LocalDate.now()
    val activeBudgets = s.budgets.filter { FinanceCalc.budgetActive(it, today) }.take(4)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)) {

        // 1) Total Balance
        item {
            CardBox(onClick = { nav.go("accounts") }) {
                Text("ยอดเงินรวม", color = MaterialTheme.colorScheme.onSurfaceVariant)
                BigAmount(fmtMoney(s.settings, s.totalAssets), size = 36)
                Text("ไม่รวมวงเงินบัตรที่ยังไม่ใช้และหนี้สิน · แตะเพื่อดูรายบัญชี", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 2) Monthly Summary
        item {
            CardBox {
                Text("สรุปเดือนนี้ (${ym})", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCell("รายรับ", cur.income, fin.income, FinanceCalc.pctChange(cur.income, prev.income), s, Modifier.weight(1f)) { nav.go("drill:income:$ym") }
                    SummaryCell("รายจ่าย", cur.expense, fin.expense, FinanceCalc.pctChange(cur.expense, prev.expense), s, Modifier.weight(1f)) { nav.go("drill:expense:$ym") }
                    SummaryCell("คงเหลือสุทธิ", cur.net, if (cur.net >= 0) fin.income else fin.expense, FinanceCalc.pctChange(cur.net, prev.net), s, Modifier.weight(1f)) { nav.go("drill:all:$ym") }
                }
                Text("เทียบกับเดือนก่อน", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }

        // 3) Quick Add (Expandable Card)
        item {
            CardBox {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick Add", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { nav.go("scan") }) { Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(4.dp)); Text("สแกน") }
                    TextButton(onClick = { nav.go("add") }) { Text("Full Mode") }
                    IconButton(onClick = { quick = !quick }) { Icon(if (quick) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null) }
                }
                AnimatedVisibility(quick) {
                    TxForm(vm, s, edit = null, full = false, keyPrefix = "quick", scrollable = false,
                        onSaved = { msg -> scope.launch { snack.showSnackbar(msg) } })
                }
                if (!quick) Text("แตะเพื่อบันทึกรายการได้ภายในไม่กี่วินาที", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickableNoRipple { quick = true })
            }
        }

        // 4) Recent Transactions
        item { SectionTitle("รายการล่าสุด", "ดูทั้งหมด") { nav.tab("records") } }
        val recent = s.txs.take(8)
        if (recent.isEmpty()) item { Text("ยังไม่มีรายการ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else item { CardBox { recent.forEach { TxRow(it, s) { nav.go("tx:${it.id}") } } } }

        // 5) Budget Overview
        item { SectionTitle("งบประมาณ", "จัดการ") { nav.go("budgets") } }
        if (activeBudgets.isEmpty()) item { Text("ยังไม่ได้ตั้งงบประมาณ", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else item {
            CardBox {
                activeBudgets.forEach { b ->
                    ProgressRow(s.cat(b.categoryId)?.name ?: b.name, FinanceCalc.budgetSpent(b, s.txs, today), b.limitAmount, s.settings) { nav.go("budgets") }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryCell(label: String, v: Long, color: androidx.compose.ui.graphics.Color, pct: Double?, s: UiState, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier, onClick = onClick, shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmtMoney(s.settings, v).removeSuffix(".00"), color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
            Text(pct?.let { (if (it >= 0) "▲ " else "▼ ") + "%.0f%%".format(abs(it)) } ?: "—", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick
    )
