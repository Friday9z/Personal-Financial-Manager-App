package com.pfm.app.data

import android.content.Context
import androidx.room.withTransaction
import com.pfm.app.domain.FinanceCalc
import com.pfm.app.domain.Money
import com.pfm.app.work.NotifKind
import com.pfm.app.work.Notifier
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FinanceRepository(private val ctx: Context, val db: AppDatabase) {

    val accounts: Flow<List<Account>> = db.accounts().observeAll()
    val categories: Flow<List<Category>> = db.categories().observeAll()
    val transactions: Flow<List<Transaction>> = db.transactions().observeAll()
    val cards: Flow<List<CreditCard>> = db.cards().observeAll()
    val installments: Flow<List<Installment>> = db.cards().observeInstallments()
    val debts: Flow<List<Debt>> = db.debts().observeAll()
    val budgets: Flow<List<Budget>> = db.budgets().observeAll()
    val recurring: Flow<List<Recurring>> = db.recurring().observeAll()
    val goals: Flow<List<SavingsGoal>> = db.goals().observeAll()
    val goalEntries: Flow<List<GoalEntry>> = db.goals().observeEntries()
    val settings: Flow<List<AppSetting>> = db.settings().observeAll()

    private val zone get() = ZoneId.systemDefault()
    fun newUid() = UUID.randomUUID().toString()
    fun millisOf(d: LocalDate, hour: Int = 12) = d.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    // ---------- ตั้งค่า ----------
    suspend fun setting(key: String, def: String = ""): String = db.settings().get(key) ?: def
    suspend fun putSetting(key: String, value: String) = db.settings().put(AppSetting(key, value))
    suspend fun notifEnabled(k: NotifKind) = setting("notif_${k.id}", "1") == "1"

    // ---------- Seed ครั้งแรก ----------
    suspend fun seedIfNeeded() {
        if (setting("seeded") == "1") return
        db.withTransaction {
            if (db.categories().count() == 0) db.categories().insertAll(defaultCategories())
            if (db.accounts().getAll().isEmpty())
                db.accounts().insert(Account(name = "เงินสด", type = AccountType.CASH, icon = "payments", color = 0xFF00A86B))
            putSetting("seeded", "1")
        }
    }

    private fun defaultCategories(): List<Category> {
        val e = listOf(
            "อาหาร" to "restaurant", "กาแฟ" to "local_cafe", "เดินทาง" to "directions_car",
            "ช้อปปิ้ง" to "shopping_bag", "บิล/สาธารณูปโภค" to "receipt_long", "ที่พัก" to "home",
            "สุขภาพ" to "favorite", "บันเทิง" to "movie", "การศึกษา" to "school", "อื่น ๆ" to "more_horiz"
        )
        val i = listOf("เงินเดือน" to "work", "โบนัส" to "card_giftcard", "ดอกเบี้ย" to "trending_up", "รายได้อื่น ๆ" to "add_circle")
        return e.mapIndexed { n, p -> Category(name = p.first, kind = "EXPENSE", icon = p.second, sortOrder = n) } +
            i.mapIndexed { n, p -> Category(name = p.first, kind = "INCOME", icon = p.second, sortOrder = n) }
    }

    // ---------- Transaction ----------
    /** บันทึก/แก้ไข รายการ; คืนข้อความ error หรือ null เมื่อสำเร็จ */
    suspend fun saveTransaction(t: Transaction): String? {
        FinanceCalc.validate(t)?.let { return it }
        val dao = db.transactions()
        if (t.id == 0L) {
            if (dao.insert(t) == -1L) return "รายการนี้ถูกบันทึกไปแล้ว"
        } else dao.update(t)
        afterChange(t)
        return null
    }

    suspend fun deleteTransaction(id: Long) {
        val dao = db.transactions()
        val t = dao.get(id) ?: return
        db.withTransaction {
            // กันรายการประจำสร้างซ้ำหลังผู้ใช้ลบเอง
            t.recurringKey?.let { putSetting("skip:$it", "1") }
            dao.delete(id)
        }
    }

    private suspend fun afterChange(t: Transaction) {
        if (FinanceCalc.reportExpense(t) > 0) checkBudgets()
    }

    // ---------- งบประมาณ: แจ้งเตือน (ไม่ซ้ำต่อรอบ/ระดับ) ----------
    suspend fun checkBudgets(today: LocalDate = LocalDate.now()) {
        if (!notifEnabled(NotifKind.BUDGET)) return
        val txs = db.transactions().getAll()
        val cats = db.categories().getAll()
        for (b in db.budgets().getAll()) {
            if (!FinanceCalc.budgetActive(b, today)) continue
            val key = FinanceCalc.budgetPeriodKey(b, today)
            val spent = FinanceCalc.budgetSpent(b, txs, today)
            val fired = db.budgets().firedPercents(b.id, key).toSet()
            val level = FinanceCalc.alertsToFire(b, spent, fired).firstOrNull() ?: continue
            db.budgets().logAlert(BudgetAlertLog(b.id, key, level, System.currentTimeMillis()))
            val name = cats.firstOrNull { it.id == b.categoryId }?.name ?: b.name
            val pct = (spent * 100.0 / b.limitAmount).toInt()
            val text = if (spent > b.limitAmount)
                "คุณใช้จ่าย$name เกินงบประมาณแล้ว\nงบประมาณ ${Money.format(b.limitAmount)}\nใช้ไป ${Money.format(spent)} ($pct%)\nเกินงบ ${Money.format(spent - b.limitAmount)}"
            else "คุณใช้จ่าย$name ไปแล้ว $pct% ของงบประมาณ\nงบประมาณ ${Money.format(b.limitAmount)} · ใช้ไป ${Money.format(spent)}"
            Notifier.post(ctx, NotifKind.BUDGET, 1000 + b.id.toInt(), "งบประมาณ: $name", text)
        }
    }

    // ---------- รายการประจำ ----------
    /** สร้างรายการอัตโนมัติ (autoCreate) และแจ้งเตือนรายการที่รอยืนยัน; ปลอดภัยต่อการเรียกซ้ำ */
    suspend fun processRecurring(today: LocalDate = LocalDate.now()) {
        for (r in db.recurring().getAll()) {
            val days = FinanceCalc.occurrences(r, r.startDay, today.toEpochDay())
            for (d in days) {
                val key = FinanceCalc.recurringKey(r, d)
                if (db.transactions().countRecurringKey(key) > 0 || setting("skip:$key") == "1") continue
                if (r.autoCreate) materialize(r, d)
                else if (notifEnabled(NotifKind.RECURRING) && setting("pendnotif:$key") != "1") {
                    putSetting("pendnotif:$key", "1")
                    Notifier.post(ctx, NotifKind.RECURRING, 2000 + (key.hashCode() and 0xFFFF),
                        "รอยืนยันรายการประจำ", "${r.title} ${Money.format(r.amount)} — เปิดแอปเพื่อยืนยัน")
                }
            }
        }
    }

    private suspend fun materialize(r: Recurring, day: Long): Boolean {
        val t = Transaction(
            uid = newUid(), epochMillis = millisOf(LocalDate.ofEpochDay(day)), title = r.title, type = r.type,
            categoryId = r.categoryId, amount = r.amount, accountId = r.accountId, cardId = r.cardId,
            scheduled = true, recurringId = r.id, recurringKey = FinanceCalc.recurringKey(r, day)
        )
        if (FinanceCalc.validate(t) != null) return false
        val ok = db.transactions().insert(t) != -1L
        if (ok) {
            if (notifEnabled(NotifKind.RECURRING)) {
                val title = if (r.type == TxType.INCOME) "วันนี้มีรายรับเข้าบัญชี" else "บันทึกรายการประจำแล้ว"
                Notifier.post(ctx, NotifKind.RECURRING, 3000 + (t.recurringKey.hashCode() and 0xFFFF), title,
                    "${r.title} ${Money.format(r.amount)}")
            }
            afterChange(t)
        }
        return ok
    }

    /** รายการประจำที่ถึงกำหนดแต่ยังไม่ยืนยัน (autoCreate=false) */
    suspend fun pendingRecurring(today: LocalDate = LocalDate.now()): List<Pair<Recurring, Long>> {
        val out = ArrayList<Pair<Recurring, Long>>()
        for (r in db.recurring().getAll().filter { !it.autoCreate && it.enabled }) {
            for (d in FinanceCalc.occurrences(r, r.startDay, today.toEpochDay())) {
                val key = FinanceCalc.recurringKey(r, d)
                if (db.transactions().countRecurringKey(key) == 0 && setting("skip:$key") != "1") out += r to d
            }
        }
        return out
    }

    suspend fun confirmRecurring(r: Recurring, day: Long) = materialize(r, day)
    suspend fun skipRecurring(r: Recurring, day: Long) = putSetting("skip:${FinanceCalc.recurringKey(r, day)}", "1")

    /** รายการที่กำลังจะเกิดขึ้นใน N วันข้างหน้า */
    suspend fun upcoming(days: Int = 30, today: LocalDate = LocalDate.now()): List<Triple<Recurring, Long, Boolean>> =
        db.recurring().getAll().flatMap { r ->
            FinanceCalc.occurrences(r, today.toEpochDay() + 1, today.toEpochDay() + days).map { Triple(r, it, r.autoCreate) }
        }.sortedBy { it.second }

    // ---------- เป้าหมายเงินเก็บ ----------
    suspend fun goalAdjust(goalId: Long, delta: Long, note: String = ""): String? {
        if (delta == 0L) return "จำนวนเงินต้องไม่เป็น 0"
        val cur = db.goals().sumFor(goalId)
        if (cur + delta < 0) return "ถอนเกินยอดที่สะสมไว้"
        db.goals().insertEntry(GoalEntry(goalId = goalId, delta = delta, epochMillis = System.currentTimeMillis(), note = note))
        return null
    }

    // ---------- ตรวจสอบยอด ----------
    suspend fun auditOk(): Boolean = FinanceCalc.auditTotals(db.accounts().getAll(), db.transactions().getAll())

}
