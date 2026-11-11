package com.pfm.app.domain

import com.pfm.app.data.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * ตรรกะการเงินแบบ Pure (ไม่พึ่ง Android/Room) เพื่อทดสอบได้ด้วย JUnit
 *
 * กฎหลัก:
 *  1. ยอดบัญชี/บัตร/หนี้ = คำนวณจากรายการเสมอ  -> แก้/ลบรายการแล้วยอดตามอัตโนมัติ
 *  2. TRANSFER / CARD_PAYMENT ไม่เป็นรายรับ-รายจ่าย
 *  3. DEBT_PAYMENT: เงินต้นลดหนี้, ดอกเบี้ย+ค่าธรรมเนียมนับเป็นรายจ่ายจริง (เงินต้นไม่นับซ้ำ)
 *  4. INSTALLMENT_PAYMENT นับเป็นรายจ่ายตอนจ่ายงวด; ตอนตั้งผ่อนไม่สร้างรายจ่าย
 *  5. EXPENSE ที่ใช้บัตร: เพิ่มหนี้บัตร ไม่ลดเงินในบัญชี
 */
object FinanceCalc {

    // ---------- ยอดคงเหลือ ----------

    fun accountBalance(account: Account, txs: List<Transaction>): Long {
        var bal = account.initialBalance
        for (t in txs) bal += accountDelta(account.id, t)
        return bal
    }

    /** ผลของรายการหนึ่งต่อบัญชีหนึ่ง (บวก = เงินเข้า) */
    fun accountDelta(accountId: Long, t: Transaction): Long = when (t.type) {
        TxType.INCOME -> if (t.accountId == accountId) t.amount else 0
        TxType.EXPENSE ->
            // ใช้บัตร => ไม่ตัดเงินจากบัญชีทันที
            if (t.cardId == null && t.accountId == accountId) -t.amount else 0
        TxType.TRANSFER ->
            (if (t.accountId == accountId) -t.amount else 0L) +
                (if (t.toAccountId == accountId) t.amount else 0L)
        TxType.CARD_PAYMENT, TxType.DEBT_PAYMENT, TxType.INSTALLMENT_PAYMENT ->
            if (t.accountId == accountId) -t.amount else 0
        else -> 0
    }

    fun totalAssets(accounts: List<Account>, txs: List<Transaction>): Long =
        accounts.filter { it.includeInTotal && !it.archived }.sumOf { accountBalance(it, txs) }

    // ---------- บัตรเครดิต / ผ่อน ----------

    fun installmentPaidCount(inst: Installment, txs: List<Transaction>): Int =
        txs.count { it.type == TxType.INSTALLMENT_PAYMENT && it.installmentId == inst.id }

    fun installmentRemainingMonths(inst: Installment, txs: List<Transaction>): Int =
        (inst.months - installmentPaidCount(inst, txs)).coerceAtLeast(0)

    fun installmentRemainingAmount(inst: Installment, txs: List<Transaction>): Long =
        (inst.totalAmount - txs.filter {
            it.type == TxType.INSTALLMENT_PAYMENT && it.installmentId == inst.id
        }.sumOf { it.amount }).coerceAtLeast(0)

    /** ยอดค้างบัตร = ใช้จ่ายผ่านบัตร − ชำระบัตร + ยอดผ่อนคงเหลือ */
    fun cardOutstanding(card: CreditCard, txs: List<Transaction>, insts: List<Installment>): Long {
        val spent = txs.filter { it.type == TxType.EXPENSE && it.cardId == card.id }.sumOf { it.amount }
        val paid = txs.filter { it.type == TxType.CARD_PAYMENT && it.cardId == card.id }.sumOf { it.amount }
        val instRemain = insts.filter { it.cardId == card.id }.sumOf { installmentRemainingAmount(it, txs) }
        return spent - paid + instRemain
    }

    fun cardAvailable(card: CreditCard, txs: List<Transaction>, insts: List<Installment>): Long =
        card.creditLimit - cardOutstanding(card, txs, insts)

    /** ยอดผ่อนที่ต้องจ่ายในเดือนที่กำหนด (งวดที่ยังไม่จ่ายและถึงรอบเดือนนั้น) */
    fun installmentDueInMonth(insts: List<Installment>, txs: List<Transaction>, ym: YearMonth): Long =
        insts.sumOf { i ->
            val first = LocalDate.ofEpochDay(i.startDay)
            val idx = ChronoUnit.MONTHS.between(YearMonth.from(first), ym).toInt()
            val inPlan = idx in 0 until i.months
            val paidThisMonth = txs.any {
                it.type == TxType.INSTALLMENT_PAYMENT && it.installmentId == i.id &&
                    YearMonth.from(dateOf(it.epochMillis)) == ym
            }
            if (inPlan && !paidThisMonth && installmentRemainingMonths(i, txs) > 0) i.monthlyAmount else 0L
        }

    // ---------- หนี้ ----------

    fun debtRemaining(debt: Debt, txs: List<Transaction>): Long =
        (debt.initialAmount - txs.filter { it.type == TxType.DEBT_PAYMENT && it.debtId == debt.id }
            .sumOf { it.principal }).coerceAtLeast(0)

    // ---------- รายงาน ----------

    /** รายรับสำหรับรายงาน */
    fun reportIncome(t: Transaction): Long = if (t.type == TxType.INCOME) t.amount else 0

    /** รายจ่ายสำหรับรายงาน (Transfer / ชำระบัตร / เงินต้นหนี้ ไม่นับ) */
    fun reportExpense(t: Transaction): Long = when (t.type) {
        TxType.EXPENSE -> t.amount
        TxType.INSTALLMENT_PAYMENT -> t.amount
        TxType.DEBT_PAYMENT -> t.interest + t.fee
        else -> 0
    }

    data class Summary(val income: Long, val expense: Long) { val net get() = income - expense }

    fun inRange(t: Transaction, from: LocalDate, toInclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val d = Instant.ofEpochMilli(t.epochMillis).atZone(zone).toLocalDate()
        return !d.isBefore(from) && !d.isAfter(toInclusive)
    }

    fun summarize(txs: List<Transaction>, from: LocalDate, to: LocalDate): Summary {
        val l = txs.filter { inRange(it, from, to) }
        return Summary(l.sumOf { reportIncome(it) }, l.sumOf { reportExpense(it) })
    }

    fun expenseByCategory(txs: List<Transaction>, from: LocalDate, to: LocalDate): Map<Long?, Long> =
        txs.filter { inRange(it, from, to) && reportExpense(it) > 0 }
            .groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { reportExpense(it) } }

    fun pctChange(current: Long, previous: Long): Double? =
        if (previous == 0L) null else (current - previous) * 100.0 / previous

    fun weekRange(date: LocalDate): Pair<LocalDate, LocalDate> {
        val mon = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return mon to mon.plusDays(6)
    }

    fun monthRange(ym: YearMonth) = ym.atDay(1) to ym.atEndOfMonth()

    // ---------- งบประมาณ ----------

    fun budgetRange(b: Budget, today: LocalDate): Pair<LocalDate, LocalDate> = when (b.period) {
        BudgetPeriod.DAILY -> today to today
        BudgetPeriod.WEEKLY -> weekRange(today)
        BudgetPeriod.YEARLY -> LocalDate.of(today.year, 1, 1) to LocalDate.of(today.year, 12, 31)
        else -> monthRange(YearMonth.from(today))
    }

    fun budgetPeriodKey(b: Budget, today: LocalDate): String {
        val (f, _) = budgetRange(b, today)
        return "${b.period}:$f"
    }

    fun budgetActive(b: Budget, today: LocalDate): Boolean {
        val d = today.toEpochDay()
        return d >= b.fromDay && (b.toDay == null || d <= b.toDay)
    }

    fun budgetSpent(b: Budget, txs: List<Transaction>, today: LocalDate): Long {
        val (f, t) = budgetRange(b, today)
        return txs.filter { inRange(it, f, t) }.filter { tx ->
            (b.categoryId == null || tx.categoryId == b.categoryId) &&
                (b.accountId == null || tx.accountId == b.accountId)
        }.sumOf { reportExpense(it) }
    }

    fun parsePercents(s: String): List<Int> =
        s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.distinct().sorted()

    /** เปอร์เซ็นต์ที่ "ถึงแล้ว" และยังไม่เคยแจ้งในรอบนี้ (>100 = เกินงบแจ้งทันที) */
    fun alertsToFire(b: Budget, spent: Long, alreadyFired: Set<Int>): List<Int> {
        if (b.limitAmount <= 0) return emptyList()
        val pct = spent * 100.0 / b.limitAmount
        val levels = parsePercents(b.alertPercents).toMutableList()
        if (100 !in levels) levels += 100
        val reached = levels.filter { pct >= it }
        // แจ้งเฉพาะระดับสูงสุดที่ถึง เพื่อกันสแปม
        val top = reached.maxOrNull() ?: return emptyList()
        return if (top in alreadyFired) emptyList() else listOf(top)
    }

    // ---------- รายการประจำ ----------

    /** วันที่เกิดรายการทั้งหมดของรายการประจำในช่วง [fromDay, toDay] (epochDay) */
    fun occurrences(r: Recurring, fromDay: Long, toDay: Long): List<Long> {
        if (!r.enabled) return emptyList()
        val start = LocalDate.ofEpochDay(r.startDay)
        val end = minOf(toDay, r.endDay ?: Long.MAX_VALUE)
        val out = ArrayList<Long>()
        val n = r.interval.coerceAtLeast(1)
        when (r.freq) {
            Freq.DAILY, Freq.CUSTOM -> {
                var d = r.startDay
                while (d <= end) { if (d >= fromDay) out += d; d += n }
            }
            Freq.WEEKLY -> {
                var d = r.startDay
                while (d <= end) { if (d >= fromDay) out += d; d += 7L * n }
            }
            Freq.MONTHLY -> {
                var k = 0
                while (true) {
                    val ym = YearMonth.from(start).plusMonths((k * n).toLong())
                    // รองรับเดือนที่วันไม่ครบ: เช่น 31 -> 28/29/30
                    val day = minOf(start.dayOfMonth, ym.lengthOfMonth())
                    val ed = ym.atDay(day).toEpochDay()
                    if (ed > end) break
                    if (ed >= fromDay) out += ed
                    k++
                }
            }
            Freq.YEARLY -> {
                var k = 0
                while (true) {
                    val y = start.year + k * n
                    val ym = YearMonth.of(y, start.month)
                    val day = minOf(start.dayOfMonth, ym.lengthOfMonth()) // 29 ก.พ. -> 28
                    val ed = ym.atDay(day).toEpochDay()
                    if (ed > end) break
                    if (ed >= fromDay) out += ed
                    k++
                }
            }
        }
        return out
    }

    fun recurringKey(r: Recurring, day: Long) = "${r.id}:$day"

    // ---------- ตรวจสอบความถูกต้องก่อนบันทึก ----------

    fun validate(t: Transaction): String? {
        if (t.amount <= 0) return "จำนวนเงินต้องมากกว่า 0"
        return when (t.type) {
            TxType.INCOME -> if (t.accountId == null) "เลือกบัญชีที่รับเงิน" else null
            TxType.EXPENSE -> if (t.accountId == null && t.cardId == null) "เลือกบัญชีหรือบัตรเครดิต" else null
            TxType.TRANSFER -> when {
                t.accountId == null || t.toAccountId == null -> "เลือกบัญชีต้นทางและปลายทาง"
                t.accountId == t.toAccountId -> "บัญชีต้นทางและปลายทางต้องไม่เหมือนกัน"
                else -> null
            }
            TxType.CARD_PAYMENT -> if (t.accountId == null || t.cardId == null) "เลือกบัญชีและบัตร" else null
            TxType.DEBT_PAYMENT -> when {
                t.accountId == null || t.debtId == null -> "เลือกบัญชีและหนี้"
                t.principal < 0 || t.interest < 0 || t.fee < 0 -> "ค่าติดลบไม่ได้"
                t.principal + t.interest + t.fee != t.amount -> "เงินต้น+ดอกเบี้ย+ค่าธรรมเนียม ต้องเท่ากับยอดชำระ"
                else -> null
            }
            TxType.INSTALLMENT_PAYMENT -> if (t.accountId == null || t.installmentId == null) "เลือกบัญชีและรายการผ่อน" else null
            else -> "ประเภทรายการไม่ถูกต้อง"
        }
    }

    fun dateOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /**
     * ตรวจสอบความสอดคล้อง: ผลรวมยอดทุกบัญชี ต้องเท่ากับ เงินเริ่มต้น + รายรับ − จ่ายจากบัญชี
     * (Transfer รวมแล้วเป็น 0) — ถ้าไม่เท่ากันแปลว่ามีรายการอ้างบัญชีที่ไม่มีอยู่/ข้อมูลเสีย
     */
    fun auditTotals(accounts: List<Account>, txs: List<Transaction>): Boolean {
        val ids = accounts.map { it.id }.toSet()
        val actual = accounts.sumOf { accountBalance(it, txs) }
        val expected = accounts.sumOf { it.initialBalance } + txs.sumOf { t ->
            when (t.type) {
                TxType.INCOME -> if (t.accountId in ids) t.amount else 0
                TxType.EXPENSE -> if (t.cardId == null && t.accountId in ids) -t.amount else 0
                TxType.TRANSFER ->
                    (if (t.toAccountId in ids) t.amount else 0L) - (if (t.accountId in ids) t.amount else 0L)
                else -> if (t.accountId in ids) -t.amount else 0
            }
        }
        return actual == expected
    }
}
