package com.pfm.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.TxType
import com.pfm.app.domain.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val iconMap: Map<String, ImageVector> = mapOf(
    "restaurant" to Icons.Filled.Restaurant, "local_cafe" to Icons.Filled.LocalCafe,
    "directions_car" to Icons.Filled.DirectionsCar, "shopping_bag" to Icons.Filled.ShoppingBag,
    "receipt_long" to Icons.Filled.ReceiptLong, "home" to Icons.Filled.Home, "favorite" to Icons.Filled.Favorite,
    "movie" to Icons.Filled.Movie, "school" to Icons.Filled.School, "more_horiz" to Icons.Filled.MoreHoriz,
    "work" to Icons.Filled.Work, "card_giftcard" to Icons.Filled.CardGiftcard, "trending_up" to Icons.Filled.TrendingUp,
    "add_circle" to Icons.Filled.AddCircle, "account_balance" to Icons.Filled.AccountBalance,
    "payments" to Icons.Filled.Payments, "savings" to Icons.Filled.Savings, "credit_card" to Icons.Filled.CreditCard,
    "wallet" to Icons.Filled.AccountBalanceWallet, "swap" to Icons.Filled.SwapHoriz
)

fun iconFor(name: String): ImageVector = iconMap[name] ?: Icons.Filled.Category

@Composable
fun CardBox(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
    Surface(
        modifier = if (onClick != null) m.clickable { onClick() } else m,
        color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, shape = RoundedCornerShape(20.dp)
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun BigAmount(text: String, color: Color = MaterialTheme.colorScheme.onSurface, size: Int = 32) =
    Text(text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color = MaterialTheme.colorScheme.primary, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun amountColor(type: String): Color {
    val f = LocalFin.current
    return when (type) {
        TxType.INCOME -> f.income
        TxType.TRANSFER -> MaterialTheme.colorScheme.primary
        else -> f.expense
    }
}

fun signedAmount(type: String, amount: Long) = when (type) {
    TxType.INCOME -> "+" + Money.format(amount)
    TxType.TRANSFER -> Money.format(amount)
    else -> "-" + Money.format(amount)
}

fun typeLabel(type: String) = when (type) {
    TxType.INCOME -> "รายรับ"; TxType.EXPENSE -> "รายจ่าย"; TxType.TRANSFER -> "โอนเงิน"
    TxType.CARD_PAYMENT -> "ชำระบัตรเครดิต"; TxType.DEBT_PAYMENT -> "ชำระหนี้"
    TxType.INSTALLMENT_PAYMENT -> "ชำระงวดผ่อน"; else -> type
}

fun dateFmt(settings: Map<String, String>): DateTimeFormatter =
    DateTimeFormatter.ofPattern(settings["date_format"] ?: "dd/MM/yyyy")

fun LocalDate.fmt(s: Map<String, String>): String = format(dateFmt(s))

/** ใช้สัญลักษณ์สกุลเงินตามตั้งค่า (ค่าเริ่มต้น THB = ฿) */
fun currencySymbol(s: Map<String, String>) = when (s["currency"] ?: "THB") {
    "USD" -> "$"; "EUR" -> "€"; "JPY" -> "¥"; "GBP" -> "£"; else -> "฿"
}

fun fmtMoney(s: Map<String, String>, minor: Long) = Money.format(minor, currencySymbol(s))

@Composable
fun ProgressRow(label: String, used: Long, limit: Long, settings: Map<String, String>, onClick: (() -> Unit)? = null) {
    val f = LocalFin.current
    val pct = if (limit > 0) used * 100f / limit else 0f
    val color = when { pct >= 100 -> f.expense; pct >= 80 -> Color(0xFFF59E0B); else -> MaterialTheme.colorScheme.primary }
    Column(Modifier.fillMaxWidth().let { if (onClick != null) it.clickable { onClick() } else it }.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Medium)
            Text("${pct.toInt()}%", color = color, fontWeight = FontWeight.SemiBold)
        }
        Text("ใช้ไป ${fmtMoney(settings, used)} / ${fmtMoney(settings, limit)}", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { (pct / 100f).coerceIn(0f, 1f) }, color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
    }
}

@Composable
fun <T> Chips(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label) })
        }
    }
}

/** ตัวเลือกแบบเมนูเลื่อนลง */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, options: List<Pair<T?, String>>, selected: T?, onSelect: (T?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val text = options.firstOrNull { it.first == selected }?.second ?: ""
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onSelect(v); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate, settings: Map<String, String>, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date.fmt(settings), onValueChange = {}, readOnly = true, label = { Text(label) },
        trailingIcon = { IconButton(onClick = { show = true }) { Icon(Icons.Outlined.CalendarMonth, null) } },
        modifier = modifier.fillMaxWidth().clickable { show = true }
    )
    if (show) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { show = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { onChange(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()) }
                show = false
            }) { Text("ตกลง") }
        }, dismissButton = { TextButton(onClick = { show = false }) { Text("ยกเลิก") } }) { DatePicker(state = st) }
    }
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.all { it.isDigit() || it == '.' || it == ',' }) onChange(v) },
        label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
    )
}

@Composable
fun TxRow(t: com.pfm.app.data.Transaction, s: UiState, onClick: () -> Unit) {
    val cat = s.cat(t.categoryId)
    val icon = when (t.type) {
        TxType.TRANSFER -> Icons.Filled.SwapHoriz
        TxType.CARD_PAYMENT -> Icons.Filled.CreditScore
        TxType.DEBT_PAYMENT -> Icons.Filled.RequestQuote
        TxType.INSTALLMENT_PAYMENT -> Icons.Filled.Event
        else -> iconFor(cat?.icon ?: "more_horiz")
    }
    val src = t.cardId?.let { s.card(it)?.name } ?: s.acc(t.accountId)?.name ?: ""
    val where = if (t.type == TxType.TRANSFER) "$src → ${s.acc(t.toAccountId)?.name ?: ""}" else src
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, amountColor(t.type))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title.ifBlank { cat?.name ?: typeLabel(t.type) }, fontWeight = FontWeight.Medium, maxLines = 1)
                if (t.scheduled) { Spacer(Modifier.width(6.dp)); AssistChip(onClick = onClick, label = { Text("Scheduled", fontSize = 10.sp) }, modifier = Modifier.height(22.dp)) }
            }
            Text("${cat?.name ?: typeLabel(t.type)} · ${com.pfm.app.domain.FinanceCalc.dateOf(t.epochMillis).fmt(s.settings)} · $where",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(signedAmount(t.type, t.amount).let { if (currencySymbol(s.settings) != "฿") it.replace("฿", currencySymbol(s.settings)) else it },
            color = amountColor(t.type), fontWeight = FontWeight.SemiBold)
    }
}
