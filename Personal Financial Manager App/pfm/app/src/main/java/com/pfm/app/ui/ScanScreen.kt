package com.pfm.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pfm.app.data.Receipts
import com.pfm.app.data.TxType
import com.pfm.app.domain.Money
import com.pfm.app.domain.ReceiptParser
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** ส่งผลสแกนไปหน้า Add (ใช้ครั้งเดียว) */
object ScanBridge {
    private var pending: Prefill? = null
    fun put(p: Prefill) { pending = p }
    fun take(): Prefill? = pending.also { pending = null }
}

/**
 * สแกนใบเสร็จ/สลิป: CameraX -> ML Kit OCR (on-device, ไม่ส่งข้อมูลออกนอกเครื่อง)
 * ผลลัพธ์เป็น "ค่าที่เสนอ" เท่านั้น — ผู้ใช้ต้องตรวจ/แก้ไขที่หน้า Add แล้วกด Save เอง (ไม่บันทึกอัตโนมัติ)
 * ข้อจำกัด: ML Kit ไม่มีโมเดลภาษาไทย จึงอ่านตัวเลข/วันที่/ภาษาอังกฤษได้ดี แต่ข้อความไทยอาจอ่านไม่ได้ -> แก้ไขเองได้
 */
@Composable
fun ScanScreen(vm: MainViewModel, s: UiState, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    fun finish(file: File?, text: String) {
        val p = ReceiptParser.parse(text)
        ScanBridge.put(Prefill(
            type = if (p.looksLikeTransfer) TxType.TRANSFER else TxType.EXPENSE,
            amount = p.amountMinor?.let { Money.toInput(it) } ?: "", title = p.merchant ?: "",
            date = p.date ?: java.time.LocalDate.now(), refNo = p.refNo ?: "", attachment = file?.name
        ))
        nav.back(); if (nav.current == "add") nav.back(); nav.go("add")
    }

    fun process(uriFile: File) {
        busy = true
        // ย่อรูปและเก็บในพื้นที่ส่วนตัวของแอป
        val dest = File(Receipts.dir(ctx), "rcpt_${UUID.randomUUID()}.jpg")
        runCatching {
            val bmp = BitmapFactory.decodeFile(uriFile.path)
            val scale = 1600f / maxOf(bmp.width, bmp.height).coerceAtLeast(1)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            dest.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        runCatching { InputImage.fromFilePath(ctx, Uri.fromFile(uriFile)) }.onSuccess { img ->
            recognizer.process(img)
                .addOnSuccessListener { r -> busy = false; uriFile.delete(); finish(dest.takeIf { it.exists() }, r.text) }
                .addOnFailureListener { busy = false; uriFile.delete(); msg = "อ่านข้อมูลอัตโนมัติไม่ได้ กรุณากรอกเอง"; finish(dest.takeIf { it.exists() }, "") }
        }.onFailure { busy = false; msg = "เปิดรูปไม่ได้" }
    }

    ScreenScaffold("สแกนใบเสร็จ / สลิป", nav) {
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ต้องอนุญาตการใช้กล้องเพื่อสแกนเอกสาร")
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("อนุญาตกล้อง") }
                TextButton(onClick = { finish(null, "") }) { Text("กรอกเอง (ไม่สแกน)") }
            }
        } else {
            val owner = LocalLifecycleOwner.current
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(factory = { c ->
                    val pv = PreviewView(c)
                    val fut = ProcessCameraProvider.getInstance(c)
                    fut.addListener({
                        val provider = fut.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                        }
                    }, ContextCompat.getMainExecutor(c))
                    pv
                }, modifier = Modifier.fillMaxSize())
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            msg?.let { Text(it, Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { finish(null, "") }, modifier = Modifier.weight(1f)) { Text("กรอกเอง") }
                Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                    val tmp = File(ctx.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(tmp).build(), ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(r: ImageCapture.OutputFileResults) { scope.launch { process(tmp) } }
                            override fun onError(e: ImageCaptureException) { msg = "ถ่ายภาพไม่สำเร็จ: ${e.message}" }
                        })
                }) { Text("ถ่ายภาพ") }
            }
        }
    }
}
