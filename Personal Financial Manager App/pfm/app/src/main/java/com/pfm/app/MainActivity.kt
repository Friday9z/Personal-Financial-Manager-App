package com.pfm.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.pfm.app.data.AppDatabase
import com.pfm.app.security.BiometricHelper
import com.pfm.app.security.PinManager
import com.pfm.app.ui.*

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private lateinit var pins: PinManager
    private var locked by mutableStateOf(true)
    private var stoppedAt = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if (pins.lockOnScreenOff && pins.hasPin()) locked = true }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ซ่อนเนื้อหาในหน้า Recent apps และกันแคปหน้าจอ
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        pins = PinManager(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val s by vm.state.collectAsState()
            PfmTheme(s.themeMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var hasPin by remember { mutableStateOf(pins.hasPin()) }
                    var showReset by remember { mutableStateOf(false) }
                    when {
                        !hasPin -> SetPinScreen(pins, onDone = { hasPin = true; locked = false })
                        locked -> {
                            LockScreen(pins, BiometricHelper.available(this@MainActivity),
                                onBiometric = {
                                    BiometricHelper.prompt(this@MainActivity, "ปลดล็อก Personal Finance",
                                        onSuccess = { locked = false }, onFallback = {})
                                },
                                onUnlock = { locked = false }, onForgot = { showReset = true })
                            if (showReset) AlertDialog(
                                onDismissRequest = { showReset = false },
                                title = { Text("ลืม PIN") },
                                text = { Text("แอปทำงาน Offline และไม่มีเซิร์ฟเวอร์กู้ PIN การรีเซ็ตจะ ลบข้อมูลทั้งหมดในแอป แล้วให้ตั้ง PIN ใหม่ " +
                                    "(ถ้ามีไฟล์ Backup สามารถกู้คืนได้ภายหลัง) ต้องการดำเนินการต่อหรือไม่?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        AppDatabase.closeAndReset()
                                        deleteDatabase(AppDatabase.FILE_NAME)
                                        com.pfm.app.data.Receipts.dir(this@MainActivity).listFiles()?.forEach { it.delete() }
                                        pins.clearPin()
                                        restartApp()
                                    }) { Text("ลบข้อมูลและรีเซ็ต") }
                                },
                                dismissButton = { TextButton(onClick = { showReset = false }) { Text("ยกเลิก") } }
                            )
                        }
                        else -> AppRoot(vm, pins, s, onLockNow = { locked = true }, onRestart = { restartApp() })
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0 && pins.hasPin()) {
            val away = (System.currentTimeMillis() - stoppedAt) / 1000
            if (away >= pins.autoLockSeconds) locked = true
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }

    private fun restartApp() {
        val i = packageManager.getLaunchIntentForPackage(packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(i)
        Runtime.getRuntime().exit(0)
    }
}
