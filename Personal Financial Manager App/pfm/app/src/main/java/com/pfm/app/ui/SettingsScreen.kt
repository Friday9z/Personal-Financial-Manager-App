package com.pfm.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfm.app.security.BiometricHelper
import com.pfm.app.security.PinManager

@Composable
fun SettingsScreen(vm: MainViewModel, s: UiState, nav: Nav, pins: PinManager, onLockNow: () -> Unit) {
    @Composable fun item(icon: ImageVector, title: String, sub: String? = null, route: String) {
        Row(Modifier.fillMaxWidth().clickable { nav.go(route) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium); if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        CardBox {
            item(Icons.Filled.Palette, "ธีม / สกุลเงิน / รูปแบบวันที่", null, "appearance")
            item(Icons.Filled.Notifications, "การแจ้งเตือน", null, "notifications")
            item(Icons.Filled.Lock, "PIN / Biometrics / ล็อกอัตโนมัติ", null, "security")
        }
        CardBox {
            item(Icons.Filled.AccountBalance, "จัดการบัญชี", null, "accounts")
            item(Icons.Filled.CreditCard, "จัดการบัตรเครดิต และรายการผ่อน", null, "cards")
            item(Icons.Filled.RequestQuote, "หนี้สิน", null, "debts")
            item(Icons.Filled.PieChart, "งบประมาณ", null, "budgets")
            item(Icons.Filled.Repeat, "รายการประจำ", null, "recurring")
            item(Icons.Filled.Savings, "เป้าหมายเงินเก็บ", null, "goals")
            item(Icons.Filled.Category, "จัดการหมวดหมู่", null, "categories")
        }
        CardBox {
            item(Icons.Filled.TableChart, "Export Excel (.xlsx)", null, "export")
            item(Icons.Filled.Backup, "Backup & Restore", null, "backup")
            item(Icons.Filled.Info, "About App", null, "about")
        }
        Button(onClick = onLockNow, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("ล็อกแอปตอนนี้") }
    }
}

@Composable
fun SettingsPages(route: String, vm: MainViewModel, s: UiState, nav: Nav, pins: PinManager, onRestart: () -> Unit) {
    when (route) {
        "appearance" -> ScreenScaffold("ธีม / สกุลเงิน / วันที่", nav) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ธีม"); Chips(listOf("system" to "ตามระบบ", "light" to "สว่าง", "dark" to "มืด"), s.themeMode) { vm.put("theme", it) }
                Dropdown("สกุลเงิน", listOf("THB", "USD", "EUR", "JPY", "GBP").map { it as String? to it }, s.settings["currency"] ?: "THB", { vm.put("currency", it ?: "THB") })
                Dropdown("รูปแบบวันที่", listOf("dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "d MMM yyyy").map { it as String? to it }, s.settings["date_format"] ?: "dd/MM/yyyy", { vm.put("date_format", it ?: "dd/MM/yyyy") })
                Text("ค่าเริ่มต้นคือ THB (฿) — การเปลี่ยนสกุลเงินเปลี่ยนเฉพาะสัญลักษณ์ที่แสดง ไม่แปลงอัตราแลกเปลี่ยน", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "notifications" -> ScreenScaffold("การแจ้งเตือน", nav) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.pfm.app.work.NotifKind.values().forEach { k ->
                    val on = (s.settings["notif_${k.id}"] ?: "1") == "1"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label, Modifier.weight(1f)); Switch(on, { vm.put("notif_${k.id}", if (it) "1" else "0") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(s.settings["weekly_time"] ?: "09:00", { if (it.length <= 5) vm.put("weekly_time", it) }, label = { Text("เวลาสรุปรายสัปดาห์ทุกวันจันทร์ (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(s.settings["monthly_time"] ?: "20:00", { if (it.length <= 5) vm.put("monthly_time", it) }, label = { Text("เวลาสรุปรายเดือนวันสุดท้ายของเดือน (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("ระบบใช้ WorkManager ตรวจทุก 15 นาที อาจเลื่อนเล็กน้อยตามโหมดประหยัดแบตเตอรี่ของเครื่อง", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "security" -> SecurityPage(s, nav, pins)
        "export" -> ExportPage(vm, s, nav)
        "backup" -> BackupPage(vm, nav, onRestart)
        "about" -> ScreenScaffold("About App", nav) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Personal Finance Manager v1.0.0", style = MaterialTheme.typography.titleMedium)
                Text("ทำงาน Offline ข้อมูลทั้งหมดอยู่ในเครื่องของคุณ (Room/SQLite) ไม่มีการส่งข้อมูลออกเซิร์ฟเวอร์")
                Text("OCR ใช้ ML Kit บนเครื่อง (ไม่รองรับข้อความไทย — แก้ไขข้อมูลเองได้เสมอ)")
            }
        }
    }
}

@Composable
private fun SecurityPage(s: UiState, nav: Nav, pins: PinManager) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var bio by remember { mutableStateOf(pins.biometricEnabled) }
    var lockOff by remember { mutableStateOf(pins.lockOnScreenOff) }
    var auto by remember { mutableIntStateOf(pins.autoLockSeconds) }
    var changing by remember { mutableStateOf(false) }
    if (changing) { SetPinScreen(pins, "เปลี่ยน PIN", onDone = { changing = false }, onCancel = { changing = false }); return }
    val bioOk = BiometricHelper.available(ctx)
    ScreenScaffold("ความปลอดภัย", nav) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { changing = true }, modifier = Modifier.fillMaxWidth()) { Text("เปลี่ยน PIN") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("ลายนิ้วมือ / ใบหน้า"); if (!bioOk) Text("เครื่องนี้ยังไม่ได้ตั้งค่าหรือไม่รองรับ Biometrics", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                Switch(bio && bioOk, { bio = it; pins.biometricEnabled = it }, enabled = bioOk)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("ล็อกเมื่อหน้าจอดับ", Modifier.weight(1f)); Switch(lockOff, { lockOff = it; pins.lockOnScreenOff = it }) }
            Text("ล็อกอัตโนมัติเมื่อออกจากแอปนาน")
            Chips(listOf(0 to "ทันที", 30 to "30 วิ", 60 to "1 นาที", 300 to "5 นาที"), auto) { auto = it; pins.autoLockSeconds = it }
            Text("PIN เก็บแบบแฮช PBKDF2 ใน EncryptedSharedPreferences (Android Keystore) ไม่เก็บเป็น Plain Text; กรอกผิดครบ ${PinManager.MAX_TRIES} ครั้งจะถูกล็อกชั่วคราว", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
