package com.pfm.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pfm.app.security.PinManager
import com.pfm.app.security.PinResult
import kotlinx.coroutines.delay

/** ตั้ง PIN ครั้งแรก (บังคับก่อนเริ่มใช้งาน) */
@Composable
fun SetPinScreen(pins: PinManager, title: String = "ตั้ง PIN เพื่อปกป้องข้อมูล", onDone: () -> Unit, onCancel: (() -> Unit)? = null) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text("ตัวเลข 4-6 หลัก", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        PinField("PIN", p1) { p1 = it }
        Spacer(Modifier.height(8.dp))
        PinField("ยืนยัน PIN", p2) { p2 = it }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            when {
                p1.length !in 4..6 -> err = "PIN ต้องมี 4-6 หลัก"
                p1 != p2 -> err = "PIN ไม่ตรงกัน"
                else -> { pins.setPin(p1); onDone() }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("บันทึก PIN") }
        if (onCancel != null) TextButton(onClick = onCancel) { Text("ยกเลิก") }
    }
}

@Composable
private fun PinField(label: String, v: String, onChange: (String) -> Unit) =
    OutlinedTextField(
        value = v, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) onChange(it) }, label = { Text(label) },
        singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth()
    )

@Composable
fun LockScreen(pins: PinManager, bioAvailable: Boolean, onBiometric: () -> Unit, onUnlock: () -> Unit, onForgot: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockedUntil) { while (System.currentTimeMillis() < lockedUntil) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    LaunchedEffect(Unit) { if (pins.biometricEnabled && bioAvailable) onBiometric() }
    val locked = now < lockedUntil

    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("ใส่ PIN เพื่อเข้าใช้งาน", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        PinField("PIN", pin) { v ->
            pin = v
            if (v.length >= 4 && !locked) { /* ตรวจเมื่อกดปุ่ม เพื่อรองรับ PIN 4-6 หลัก */ }
        }
        val text = if (locked) "ล็อกชั่วคราว ลองใหม่ใน ${((lockedUntil - now) / 1000).coerceAtLeast(1)} วินาที" else msg
        text?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        Button(enabled = !locked && pin.length >= 4, onClick = {
            when (val r = pins.verify(pin)) {
                is PinResult.Ok -> onUnlock()
                is PinResult.Wrong -> { msg = "PIN ไม่ถูกต้อง (เหลือ ${r.attemptsLeft} ครั้งก่อนล็อกชั่วคราว)"; pin = "" }
                is PinResult.Locked -> { lockedUntil = r.untilMillis; now = System.currentTimeMillis(); pin = "" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("ปลดล็อก") }
        if (pins.biometricEnabled && bioAvailable)
            OutlinedButton(onClick = onBiometric, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.Fingerprint, null); Spacer(Modifier.width(8.dp)); Text("ใช้ลายนิ้วมือ / ใบหน้า")
            }
        TextButton(onClick = onForgot) { Text("ลืม PIN?") }
    }
}
