package com.pfm.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pfm.app.MainActivity

/** หมวดการแจ้งเตือน — ตรงกับสวิตช์เปิด/ปิดในหน้า Settings (key = "notif_<id>") */
enum class NotifKind(val id: String, val label: String) {
    WEEKLY("weekly", "สรุปรายสัปดาห์"),
    MONTHLY("monthly", "สรุปรายเดือน"),
    BUDGET("budget", "งบประมาณ"),
    BILLS("bills", "บิลที่ใกล้ถึงกำหนด"),
    CARD("card", "บัตรเครดิตครบกำหนด"),
    DEBT("debt", "หนี้ครบกำหนด"),
    RECURRING("recurring", "รายการประจำ"),
    GOAL("goal", "เป้าหมายเงินเก็บ"),
    INSTALLMENT("installment", "งวดผ่อนชำระ")
}

object Notifier {
    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        NotifKind.values().forEach {
            nm.createNotificationChannel(
                NotificationChannel("pfm_${it.id}", it.label, NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun post(ctx: Context, kind: NotifKind, notifId: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "pfm_${kind.id}")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(notifId, n)
    }
}
