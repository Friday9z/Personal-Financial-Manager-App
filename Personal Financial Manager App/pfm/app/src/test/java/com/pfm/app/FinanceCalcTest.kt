package com.pfm.app

import com.pfm.app.data.*
import com.pfm.app.domain.FinanceCalc
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class FinanceCalcTest {
    private val zone = ZoneId.systemDefault()
    private var seq = 0L
    private fun ms(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun baht(x: Long) = x * 100

    private fun tx(
        type: String, amount: Long, date: LocalDate = LocalDate.of(2026, 3, 10),
        acc: Long? = null, to: Long? = null, card: Long? = null, cat: Long? = null,
        debt: Long? = null, inst: Long? = null, p: Long = 0, i: Long = 0, f: Long = 0
    ) = Transaction(
        id = ++seq, uid = "u$seq", epochMillis = ms(date), title = "t", type = type,
        categoryId = cat, amount = amount, accountId = acc, toAccountId = to, cardId = card,
        debtId = debt, installmentId = inst, principal = p, interest = i, fee = f
    )

    private val bank = Account(id = 1, name = "KBank", initialBalance = baht(25000))
    private val save = Account(id = 2, name = "Save", initialBalance = 0)
    private val cash = Account(id = 3, name = "Cash", initialBalance = baht(1500))
    private val card = CreditCard(id = 1, name = "KTC", creditLimit = baht(100000), payAccountId = 1)

    @Test fun transferMovesMoneyButIsNotIncomeOrExpense() {
        val t = tx(TxType.TRANSFER, baht(5000), acc = 1, to = 2)
        val all = listOf(t)
        assertEquals(baht(20000), FinanceCalc.accountBalance(bank, all))
        assertEquals(baht(5000), FinanceCalc.accountBalance(save, all))
        val s = FinanceCalc.summarize(all, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(0, s.income); assertEquals(0, s.expense)
        assertTrue(FinanceCalc.auditTotals(listOf(bank, save), all))
    }

    @Test fun cardSpendRaisesDebtNotAccountThenPaymentIsNotDoubleExpense() {
        val spend = tx(TxType.EXPENSE, baht(35000), card = 1)
        val pay = tx(TxType.CARD_PAYMENT, baht(10000), acc = 1, card = 1)
        assertEquals(baht(25000), FinanceCalc.accountBalance(bank, listOf(spend)))
        assertEquals(baht(35000), FinanceCalc.cardOutstanding(card, listOf(spend), emptyList()))
        assertEquals(baht(65000), FinanceCalc.cardAvailable(card, listOf(spend), emptyList()))

        val all = listOf(spend, pay)
        assertEquals(baht(15000), FinanceCalc.accountBalance(bank, all))
        assertEquals(baht(25000), FinanceCalc.cardOutstanding(card, all, emptyList()))
        val s = FinanceCalc.summarize(all, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals(baht(35000), s.expense) // นับครั้งเดียว ไม่นับตอนชำระ
    }

    @Test fun debtPaymentSplitsPrincipalInterestFee() {
        val debt = Debt(id = 1, name = "Loan", initialAmount = baht(100000), interestRatePct = 12.0)
        val pay = tx(TxType.DEBT_PAYMENT, baht(5000), acc = 1, debt = 1, p = baht(4200), i = baht(750), f = baht(50))
        assertNull(FinanceCalc.validate(pay))
        assertEquals(baht(95800), FinanceCalc.debtRemaining(debt, listOf(pay)))
        assertEquals(baht(20000), FinanceCalc.accountBalance(bank, listOf(pay)))
        assertEquals(baht(800), FinanceCalc.reportExpense(pay)) // เฉพาะดอกเบี้ย+ค่าธรรมเนียม
    }

    @Test fun debtPaymentMustSumUp() {
        val bad = tx(TxType.DEBT_PAYMENT, baht(5000), acc = 1, debt = 1, p = baht(4000), i = baht(500))
        assertNotNull(FinanceCalc.validate(bad))
    }

    @Test fun installmentsMultipleOnOneCard() {
        val s25 = Installment(id = 1, name = "S25", cardId = 1, totalAmount = baht(56880), months = 24,
            monthlyAmount = baht(2370), startDay = LocalDate.of(2026, 1, 5).toEpochDay())
        val tv = Installment(id = 2, name = "TV", cardId = 1, totalAmount = baht(12000), months = 10,
            monthlyAmount = baht(1200), startDay = LocalDate.of(2026, 2, 5).toEpochDay())
        val paid = (1..10).map {
            tx(TxType.INSTALLMENT_PAYMENT, baht(2370), date = LocalDate.of(2026, 1, 5).plusMonths(it - 1L), acc = 1, inst = 1)
        }
        assertEquals(14, FinanceCalc.installmentRemainingMonths(s25, paid))
        assertEquals(baht(56880 - 23700), FinanceCalc.installmentRemainingAmount(s25, paid))
        assertEquals(10, FinanceCalc.installmentRemainingMonths(tv, paid))
        assertEquals(baht(33180 + 12000), FinanceCalc.cardOutstanding(card, paid, listOf(s25, tv)))
    }

    @Test fun editAndDeleteAdjustBalancesBecauseDerived() {
        val a = tx(TxType.EXPENSE, baht(100), acc = 3)
        assertEquals(baht(1400), FinanceCalc.accountBalance(cash, listOf(a)))
        val edited = a.copy(amount = baht(250))
        assertEquals(baht(1250), FinanceCalc.accountBalance(cash, listOf(edited)))
        assertEquals(baht(1500), FinanceCalc.accountBalance(cash, emptyList())) // ลบแล้วกลับ
    }

    @Test fun monthlyRecurringHandlesShortMonths() {
        val r = Recurring(id = 1, title = "rent", type = TxType.EXPENSE, amount = 1,
            startDay = LocalDate.of(2026, 1, 31).toEpochDay(), freq = Freq.MONTHLY)
        val occ = FinanceCalc.occurrences(r, LocalDate.of(2026, 1, 1).toEpochDay(), LocalDate.of(2026, 4, 30).toEpochDay())
            .map { LocalDate.ofEpochDay(it) }
        assertEquals(listOf(
            LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), occ)
    }

    @Test fun leapYearFebruary() {
        val r = Recurring(id = 1, title = "x", type = TxType.EXPENSE, amount = 1,
            startDay = LocalDate.of(2028, 1, 31).toEpochDay(), freq = Freq.MONTHLY)
        val feb = FinanceCalc.occurrences(r, LocalDate.of(2028, 2, 1).toEpochDay(), LocalDate.of(2028, 2, 29).toEpochDay())
        assertEquals(listOf(LocalDate.of(2028, 2, 29)), feb.map { LocalDate.ofEpochDay(it) })
    }

    @Test fun recurringRespectsEndDateAndDisabled() {
        val r = Recurring(id = 1, title = "x", type = TxType.INCOME, amount = 1,
            startDay = LocalDate.of(2026, 1, 25).toEpochDay(), endDay = LocalDate.of(2026, 3, 1).toEpochDay())
        val occ = FinanceCalc.occurrences(r, 0, LocalDate.of(2026, 12, 31).toEpochDay())
        assertEquals(2, occ.size)
        assertTrue(FinanceCalc.occurrences(r.copy(enabled = false), 0, Long.MAX_VALUE / 2).isEmpty())
    }

    @Test fun weekStartsMonday() {
        val (f, t) = FinanceCalc.weekRange(LocalDate.of(2026, 11, 11)) // Wednesday
        assertEquals(LocalDate.of(2026, 11, 9), f)
        assertEquals(LocalDate.of(2026, 11, 15), t)
    }

    @Test fun budgetSpentAndAlertDedupe() {
        val today = LocalDate.of(2026, 3, 20)
        val b = Budget(id = 1, name = "food", limitAmount = baht(5000), categoryId = 7,
            fromDay = LocalDate.of(2026, 1, 1).toEpochDay())
        val e1 = tx(TxType.EXPENSE, baht(2600), cat = 7, acc = 3)
        val other = tx(TxType.EXPENSE, baht(999), cat = 8, acc = 3)
        assertEquals(baht(2600), FinanceCalc.budgetSpent(b, listOf(e1, other), today))
        assertEquals(listOf(50), FinanceCalc.alertsToFire(b, baht(2600), emptySet()))
        assertTrue(FinanceCalc.alertsToFire(b, baht(2600), setOf(50)).isEmpty())   // ไม่ซ้ำ
        assertEquals(listOf(100), FinanceCalc.alertsToFire(b, baht(5450), setOf(50, 80)))
    }

    @Test fun validateRejectsBadInput() {
        assertNotNull(FinanceCalc.validate(tx(TxType.EXPENSE, 0, acc = 1)))
        assertNotNull(FinanceCalc.validate(tx(TxType.TRANSFER, 100, acc = 1, to = 1)))
        assertNotNull(FinanceCalc.validate(tx(TxType.EXPENSE, 100)))
        assertNull(FinanceCalc.validate(tx(TxType.EXPENSE, 100, card = 1)))
    }

    @Test fun installmentDueInMonthSkipsPaid() {
        val i = Installment(id = 1, name = "x", cardId = 1, totalAmount = 1200, months = 3, monthlyAmount = 400,
            startDay = LocalDate.of(2026, 3, 5).toEpochDay())
        val ym = YearMonth.of(2026, 3)
        assertEquals(400, FinanceCalc.installmentDueInMonth(listOf(i), emptyList(), ym))
        val paid = tx(TxType.INSTALLMENT_PAYMENT, 400, date = LocalDate.of(2026, 3, 6), acc = 1, inst = 1)
        assertEquals(0, FinanceCalc.installmentDueInMonth(listOf(i), listOf(paid), ym))
    }
}
