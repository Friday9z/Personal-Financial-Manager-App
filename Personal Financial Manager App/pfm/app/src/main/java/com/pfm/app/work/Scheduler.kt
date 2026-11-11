package com.pfm.app.work

import android.content.Context
import androidx.work.*
import com.pfm.app.PfmApp
import com.pfm.app.domain.FinanceCalc
import com.pfm.app.domain.Money
import java.time.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager (ไม่ต้องใช้ AlarmManager exact) — ทำงาน Offline จากฐานข้อมูลในเครื่อง
 *  - DailyWorker: ทุก ~1 ชม. ประมวลผลรายการประจำ, งบ, บิล/บัตร/หนี้/ผ่อนที่ใกล้ถึงกำหนด
 *  - ReportWorker: เช็กทุก 15 นาที ว่าถึงเวลาสรุปรายสัปดาห์ (จันทร์ 09:00) / รายเดือน (วันสุดท้าย 20:00) หรือยัง
 *    เวลาปรับได้ในหน้า Settings (key: weekly_time, monthly_time รูปแบบ "HH:mm")
 *  หมายเหตุ: WorkManager อาจเลื่อนเวลาได้เล็กน้อยตามระบบประหยัดแบตเตอรี่
 */
object Scheduler {
    fun scheduleAll(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        wm.enqueueUniquePeriodicWork(
            "pfm_daily", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.HOURS).build()
        )
        wm.enqueueUniquePeriodicWork(
            "pfm_reports", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReportWorker>(15, TimeUnit.MINUTES).build()
        )
    }
}

class DailyWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PfmApp
        val repo = app.repo
        val today = LocalDate.now()
        runCatching {
            repo.seedIfNeeded()
            repo.processRecurring(today)
            repo.checkBudgets(today)
            dueSoon(app, today)
        }
        return Result.success()
    }

    private suspend fun dueSoon(app: PfmApp, today: LocalDate) {
        val repo = app.repo
        val db = app.db
        val txs = db.transactions().getAll()
        val insts = db.cards().getInstallments()

        // บัตรเครดิต: เตือนล่วงหน้า 3 วันและวันครบกำหนด
        if (repo.notifEnabled(NotifKind.CARD)) {
            for (c in db.cards().getAll().filter { !it.archived }) {
                val due = dueDate(today, c.dueDay)
                val left = Duration.between(today.atStartOfDay(), due.atStartOfDay()).toDays()
                val owed = FinanceCalc.cardOutstanding(c, txs, insts)
                if (owed > 0 && left in 0..3) once(repo, "card:${c.id}:$due:$left") {
                    Notifier.post(app, NotifKind.CARD, 4000 + c.id.toInt(), "บัตรเครดิตใกล้ครบกำหนด",
                        "บัตรเครดิต ${c.name} ของคุณครบกำหนดชำระในอีก $left วัน (ยอดค้าง ${Money.format(owed)})")
                }
            }
        }
        // หนี้
        if (repo.notifEnabled(NotifKind.DEBT)) {
            for (d in db.debts().getAll().filter { !it.archived }) {
                val remain = FinanceCalc.debtRemaining(d, txs)
                val due = dueDate(today, d.dueDay)
                val left = Duration.between(today.atStartOfDay(), due.atStartOfDay()).toDays()
                if (remain > 0 && left in 0..3) once(repo, "debt:${d.id}:$due:$left") {
                    Notifier.post(app, NotifKind.DEBT, 5000 + d.id.toInt(), "หนี้ใกล้ครบกำหนด",
                        "${d.name} ครบกำหนดอีก $left วัน (ชำระ ${Money.format(d.monthlyPayment)})")
                }
            }
        }
        // งวดผ่อน
        if (repo.notifEnabled(NotifKind.INSTALLMENT)) {
            val ym = YearMonth.from(today)
            val due = FinanceCalc.installmentDueInMonth(insts, txs, ym)
            if (due > 0 && today.dayOfMonth in listOf(1, 25)) once(repo, "inst:$ym:${today.dayOfMonth}") {
                Notifier.post(app, NotifKind.INSTALLMENT, 6000, "งวดผ่อนที่ต้องจ่ายเดือนนี้", Money.format(due))
            }
        }
        // เป้าหมาย: ครบเป้า
        if (repo.notifEnabled(NotifKind.GOAL)) {
            db.goals().getAll().forEach { g ->
                val cur = db.goals().sumFor(g.id)
                if (g.targetAmount > 0 && cur >= g.targetAmount) once(repo, "goal:${g.id}") {
                    Notifier.post(app, NotifKind.GOAL, 7000 + g.id.toInt(), "บรรลุเป้าหมายแล้ว", g.name)
                }
            }
        }
    }

    private fun dueDate(today: LocalDate, day: Int): LocalDate {
        val thisM = YearMonth.from(today)
        val d = thisM.atDay(minOf(day.coerceAtLeast(1), thisM.lengthOfMonth()))
        if (!d.isBefore(today)) return d
        val n = thisM.plusMonths(1)
        return n.atDay(minOf(day.coerceAtLeast(1), n.lengthOfMonth()))
    }

    private suspend fun once(repo: com.pfm.app.data.FinanceRepository, key: String, block: () -> Unit) {
        if (repo.setting("sent:$key") == "1") return
        repo.putSetting("sent:$key", "1")
        block()
    }
}

class ReportWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PfmApp
        val repo = app.repo
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        runCatching {
            // รายสัปดาห์: จันทร์ ตั้งแต่เวลาที่ตั้งไว้ (ค่าเริ่มต้น 09:00)
            if (repo.notifEnabled(NotifKind.WEEKLY) && today.dayOfWeek == DayOfWeek.MONDAY &&
                !now.toLocalTime().isBefore(parse(repo.setting("weekly_time", "09:00")))
            ) {
                val k = "weekly:$today"
                if (repo.setting("sent:$k") != "1") {
                    repo.putSetting("sent:$k", "1")
                    val (f, t) = FinanceCalc.weekRange(today.minusWeeks(1))
                    post(app, NotifKind.WEEKLY, "สรุปรายสัปดาห์ ($f – $t)", f, t, 7100)
                }
            }
            // รายเดือน: วันสุดท้ายของเดือน เวลา 20:00 (ปรับได้)
            val last = YearMonth.from(today).atEndOfMonth()
            if (repo.notifEnabled(NotifKind.MONTHLY) && today == last &&
                !now.toLocalTime().isBefore(parse(repo.setting("monthly_time", "20:00")))
            ) {
                val k = "monthly:${YearMonth.from(today)}"
                if (repo.setting("sent:$k") != "1") {
                    repo.putSetting("sent:$k", "1")
                    post(app, NotifKind.MONTHLY, "สรุปรายเดือน ${YearMonth.from(today)}", today.withDayOfMonth(1), today, 7200)
                }
            }
        }
        return Result.success()
    }

    private suspend fun post(app: PfmApp, kind: NotifKind, title: String, f: LocalDate, t: LocalDate, id: Int) {
        val txs = app.db.transactions().getAll()
        val s = FinanceCalc.summarize(txs, f, t)
        Notifier.post(app, kind, id, title,
            "รายรับ ${Money.format(s.income)} · รายจ่าย ${Money.format(s.expense)}\nคงเหลือสุทธิ ${Money.format(s.net)}")
    }

    private fun parse(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(9, 0))
}

