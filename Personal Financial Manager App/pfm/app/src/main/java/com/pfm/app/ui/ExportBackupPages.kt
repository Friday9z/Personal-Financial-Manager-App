package com.pfm.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.data.Backup
import com.pfm.app.data.TxType
import com.pfm.app.export.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExportPage(vm: MainViewModel, s: UiState, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(ExportKind.LINE_ITEMS) }
    var fromS by rememberSaveable { mutableStateOf(YearMonth.now().atDay(1).toString()) }
    var toS by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var catId by rememberSaveable { mutableStateOf<Long?>(null) }
    var accId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cardId by rememberSaveable { mutableStateOf<Long?>(null) }
    var picked by remember(kind) { mutableStateOf(kind.sheets) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lastFile by remember { mutableStateOf<File?>(null) }

    fun options() = ExportOptions(
        kind, runCatching { LocalDate.parse(fromS) }.getOrDefault(LocalDate.now().withDayOfMonth(1)),
        runCatching { LocalDate.parse(toS) }.getOrDefault(LocalDate.now()), picked,
        types = setOfNotNull(type), categoryIds = setOfNotNull(catId), accountIds = setOfNotNull(accId), cardIds = setOfNotNull(cardId)
    )
    suspend fun build(): File = withContext(Dispatchers.IO) { Exporter.build(ctx, vm.db(), options()) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { val f = lastFile ?: build(); withContext(Dispatchers.IO) { Exporter.copyTo(ctx, f, uri) } }
                .onSuccess { msg = "บันทึกไฟล์แล้ว" }.onFailure { msg = "บันทึกไม่สำเร็จ: ${it.message}" }
            busy = false
        }
    }

    ScreenScaffold("Export Excel", nav) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Dropdown("ชนิดรายงาน", ExportKind.values().map { it as ExportKind? to it.label }, kind, { kind = it ?: ExportKind.LINE_ITEMS })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromS, { fromS = it }, label = { Text("ตั้งแต่ yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(toS, { toS = it }, label = { Text("ถึง") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Dropdown("ประเภท Transaction", listOf<Pair<String?, String>>(null to "ทั้งหมด") + listOf(TxType.INCOME, TxType.EXPENSE, TxType.TRANSFER, TxType.CARD_PAYMENT, TxType.DEBT_PAYMENT, TxType.INSTALLMENT_PAYMENT).map { it to typeLabel(it) }, type, { type = it })
            Dropdown("หมวดหมู่", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.categories.map { it.id to it.name }, catId, { catId = it })
            Dropdown("บัญชี", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.accounts.map { it.id to it.name }, accId, { accId = it })
            Dropdown("บัตรเครดิต", listOf<Pair<Long?, String>>(null to "ทั้งหมด") + s.cards.map { it.id to it.name }, cardId, { cardId = it })
            Text("เลือกชีตที่ต้องการ")
            kind.sheets.forEach { sh ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(sh in picked, { c -> picked = if (c) picked + sh else picked - sh }); Text(sh.title)
                }
            }
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    busy = true
                    runCatching { build() }.onSuccess { lastFile = it; ctx.startActivity(Exporter.shareIntent(ctx, it)); msg = "สร้างไฟล์แล้ว: ${it.name}" }
                        .onFailure { msg = "สร้างไฟล์ไม่สำเร็จ: ${it.message}" }
                    busy = false
                }
            }) { Text("สร้างไฟล์และแชร์ (Share Sheet)") }
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                lastFile = null; saveLauncher.launch("pfm_${kind.name.lowercase()}_${LocalDate.now()}.xlsx")
            }) { Text("บันทึกไปยังตำแหน่งที่เลือก…") }
            msg?.let { Text(it, fontSize = 13.sp) }
        }
    }
}

@Composable
fun BackupPage(vm: MainViewModel, nav: Nav, onRestart: () -> Unit) {
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val out = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) msg = runCatching { Backup.export(ctx, vm.db(), uri); "สำรองข้อมูลสำเร็จ" }.getOrElse { "สำรองไม่สำเร็จ: ${it.message}" }
    }
    val inp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }
    ScreenScaffold("Backup & Restore", nav) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ไฟล์สำรองประกอบด้วยฐานข้อมูลและรูปใบเสร็จ (ไม่รวม PIN) เก็บไฟล์ไว้ที่ปลอดภัย เพราะไม่ได้เข้ารหัส")
            Button(onClick = { out.launch("pfm_backup_${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) { Text("สำรองข้อมูล") }
            OutlinedButton(onClick = { inp.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("กู้คืนจากไฟล์สำรอง") }
            msg?.let { Text(it) }
        }
    }
    pendingRestore?.let { uri ->
        AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("กู้คืนข้อมูล?") },
            text = { Text("ข้อมูลปัจจุบันทั้งหมดจะถูกแทนที่ด้วยข้อมูลในไฟล์สำรอง และแอปจะเริ่มใหม่") },
            confirmButton = { TextButton(onClick = {
                pendingRestore = null
                val err = Backup.restore(ctx, uri)
                if (err == null) onRestart() else msg = err
            }) { Text("กู้คืน") } },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("ยกเลิก") } })
    }
}
