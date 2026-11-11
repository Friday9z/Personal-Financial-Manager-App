package com.pfm.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.pfm.app.data.*
import com.pfm.app.domain.FinanceCalc
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportSheet(val title: String) {
    SUMMARY("Summary"), INCOME("Income"), EXPENSE("Expense"), CATEGORY("Category Breakdown"),
    ACCOUNT("Account Breakdown"), CREDIT_CARD("Credit Card"), DEBT("Debt"), TRANSACTIONS("Transactions"),
    INSTALLMENT("Installments"), BUDGET("Budgets")
}

/** ชนิดรายงานตามสเปก (ข้อ 14) -> ชุดชีตที่เกี่ยวข้อง */
enum class ExportKind(val label: String, val sheets: Set<ExportSheet>) {
    LINE_ITEMS("รายการทั้งหมด (Line Items)", setOf(ExportSheet.TRANSACTIONS)),
    WEEKLY("รายงานรายสัปดาห์", ExportSheet.values().toSet() - setOf(ExportSheet.BUDGET, ExportSheet.INSTALLMENT)),
    MONTHLY("รายงานรายเดือน", ExportSheet.values().toSet()),
    YEARLY("รายงานรายปี", ExportSheet.values().toSet()),
    BY_CATEGORY("รายงานตามหมวดหมู่", setOf(ExportSheet.SUMMARY, ExportSheet.CATEGORY)),
    BY_ACCOUNT("รายงานตามบัญชี", setOf(ExportSheet.SUMMARY, ExportSheet.ACCOUNT)),
    CREDIT_CARD("รายงานบัตรเครดิต", setOf(ExportSheet.CREDIT_CARD, ExportSheet.INSTALLMENT)),
    DEBT("รายงานหนี้สิน", setOf(ExportSheet.DEBT)),
    INSTALLMENT("รายงานการผ่อนชำระ", setOf(ExportSheet.INSTALLMENT)),
    BUDGET("รายงานงบประมาณ", setOf(ExportSheet.BUDGET))
}

data class ExportOptions(
    val kind: ExportKind,
    val from: LocalDate,
    val to: LocalDate,
    val sheets: Set<ExportSheet> = kind.sheets,      // เลือก export เฉพาะบางชีตได้
    val types: Set<String> = emptySet(),             // ว่าง = ทุกประเภท
    val categoryIds: Set<Long> = emptySet(),
    val accountIds: Set<Long> = emptySet(),
    val cardIds: Set<Long> = emptySet()
)

object Exporter {
    private val tf = DateTimeFormatter.ofPattern("HH:mm")

    suspend fun build(ctx: Context, db: AppDatabase, o: ExportOptions): File {
        val allTx = db.transactions().getAll()
        val accounts = db.accounts().getAll()
        val cats = db.categories().getAll().associateBy { it.id }
        val cards = db.cards().getAll()
        val insts = db.cards().getInstallments()
        val debts = db.debts().getAll()
        val budgets = db.budgets().getAll()
        val accName = accounts.associate { it.id to it.name }
        val cardName = cards.associate { it.id to it.name }
        val zone = ZoneId.systemDefault()

        val tx = allTx.filter { FinanceCalc.inRange(it, o.from, o.to, zone) }.filter {
            (o.types.isEmpty() || it.type in o.types) &&
                (o.categoryIds.isEmpty() || it.categoryId in o.categoryIds) &&
                (o.accountIds.isEmpty() || it.accountId in o.accountIds || it.toAccountId in o.accountIds) &&
                (o.cardIds.isEmpty() || it.cardId in o.cardIds)
        }.sortedWith(compareBy({ it.epochMillis }, { it.id }))

        fun cat(id: Long?) = id?.let { cats[it]?.name } ?: ""
        val sheets = ArrayList<XSheet>()
        val want = o.sheets

        if (ExportSheet.SUMMARY in want) {
            val s = FinanceCalc.summarize(tx, o.from, o.to)
            sheets += XSheet("Summary", listOf("Item", "Value"), listOf(
                listOf("Period", "${o.from} – ${o.to}"),
                listOf("Total Income", Cur(s.income)),
                listOf("Total Expense", Cur(s.expense)),
                listOf("Net", Cur(s.net)),
                listOf("Transactions", tx.size),
                listOf("Total Assets (now)", Cur(FinanceCalc.totalAssets(accounts, allTx)))
            ), listOf(24, 28))
        }
        if (ExportSheet.INCOME in want) sheets += XSheet("Income",
            listOf("Date", "Description", "Category", "Account", "Amount"),
            tx.filter { it.type == TxType.INCOME }.map {
                listOf(FinanceCalc.dateOf(it.epochMillis, zone), it.title, cat(it.categoryId), accName[it.accountId] ?: "", Cur(it.amount))
            }, listOf(12, 30, 18, 20, 14))
        if (ExportSheet.EXPENSE in want) sheets += XSheet("Expense",
            listOf("Date", "Description", "Category", "Account/Card", "Amount"),
            tx.filter { FinanceCalc.reportExpense(it) > 0 }.map {
                listOf(FinanceCalc.dateOf(it.epochMillis, zone), it.title, cat(it.categoryId),
                    it.cardId?.let { c -> cardName[c] } ?: accName[it.accountId] ?: "", Cur(FinanceCalc.reportExpense(it)))
            }, listOf(12, 30, 18, 20, 14))
        if (ExportSheet.CATEGORY in want) {
            val byCat = FinanceCalc.expenseByCategory(tx, o.from, o.to)
            val total = byCat.values.sum().coerceAtLeast(1)
            sheets += XSheet("Category Breakdown", listOf("Category", "Expense", "Share %"),
                byCat.entries.sortedByDescending { it.value }.map {
                    listOf(cat(it.key).ifBlank { "(ไม่ระบุ)" }, Cur(it.value), Math.round(it.value * 1000.0 / total) / 10.0)
                }, listOf(24, 16, 10))
        }
        if (ExportSheet.ACCOUNT in want) sheets += XSheet("Account Breakdown",
            listOf("Account", "Type", "Initial", "In (period)", "Out (period)", "Balance (now)"),
            accounts.map { a ->
                val inn = tx.sumOf { t -> FinanceCalc.accountDelta(a.id, t).coerceAtLeast(0) }
                val out = tx.sumOf { t -> -FinanceCalc.accountDelta(a.id, t).coerceAtMost(0) }
                listOf(a.name, a.type, Cur(a.initialBalance), Cur(inn), Cur(out), Cur(FinanceCalc.accountBalance(a, allTx)))
            }, listOf(24, 12, 14, 14, 14, 16))
        if (ExportSheet.CREDIT_CARD in want) sheets += XSheet("Credit Card",
            listOf("Card", "Bank", "Limit", "Outstanding", "Available", "Statement Day", "Due Day"),
            cards.map { c ->
                listOf(c.name, c.bank, Cur(c.creditLimit), Cur(FinanceCalc.cardOutstanding(c, allTx, insts)),
                    Cur(FinanceCalc.cardAvailable(c, allTx, insts)), c.statementDay, c.dueDay)
            }, listOf(22, 16, 14, 14, 14, 14, 10))
        if (ExportSheet.INSTALLMENT in want) sheets += XSheet("Installments",
            listOf("Item", "Card", "Total", "Months", "Paid", "Remaining Months", "Monthly", "Remaining Amount", "Start"),
            insts.map { i ->
                listOf(i.name, cardName[i.cardId] ?: "", Cur(i.totalAmount), i.months,
                    FinanceCalc.installmentPaidCount(i, allTx), FinanceCalc.installmentRemainingMonths(i, allTx),
                    Cur(i.monthlyAmount), Cur(FinanceCalc.installmentRemainingAmount(i, allTx)), LocalDate.ofEpochDay(i.startDay))
            }, listOf(24, 16, 14, 8, 8, 16, 14, 16, 12))
        if (ExportSheet.DEBT in want) sheets += XSheet("Debt",
            listOf("Debt", "Kind", "Initial", "Remaining", "Rate %", "Monthly", "Due Day", "Paid Principal", "Paid Interest", "Paid Fee"),
            debts.map { d ->
                val pays = allTx.filter { it.type == TxType.DEBT_PAYMENT && it.debtId == d.id }
                listOf(d.name, d.kind, Cur(d.initialAmount), Cur(FinanceCalc.debtRemaining(d, allTx)), d.interestRatePct,
                    Cur(d.monthlyPayment), d.dueDay, Cur(pays.sumOf { it.principal }), Cur(pays.sumOf { it.interest }), Cur(pays.sumOf { it.fee }))
            }, listOf(22, 16, 14, 14, 8, 14, 8, 14, 14, 12))
        if (ExportSheet.BUDGET in want) sheets += XSheet("Budgets",
            listOf("Budget", "Period", "Category", "Limit", "Spent (current)", "Used %"),
            budgets.map { b ->
                val spent = FinanceCalc.budgetSpent(b, allTx, LocalDate.now())
                listOf(b.name, b.period, cat(b.categoryId), Cur(b.limitAmount), Cur(spent),
                    if (b.limitAmount > 0) Math.round(spent * 1000.0 / b.limitAmount) / 10.0 else 0.0)
            }, listOf(22, 12, 18, 14, 16, 10))
        if (ExportSheet.TRANSACTIONS in want) sheets += XSheet("Transactions",
            listOf("Transaction ID", "Date", "Time", "Type", "Category", "Description", "Amount", "Account", "Credit Card", "Note", "Reference Number"),
            tx.map {
                val dt = java.time.Instant.ofEpochMilli(it.epochMillis).atZone(zone)
                listOf(it.id, dt.toLocalDate(), dt.format(tf), it.type, cat(it.categoryId), it.title, Cur(it.amount),
                    accName[it.accountId] ?: "", it.cardId?.let { c -> cardName[c] } ?: "", it.note, it.refNo)
            }, listOf(14, 12, 8, 18, 18, 30, 14, 20, 18, 24, 18))

        if (sheets.isEmpty()) sheets += XSheet("Empty", listOf("Info"), listOf(listOf("ไม่ได้เลือกชีต")))

        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "pfm_${o.kind.name.lowercase()}_${o.from}_${o.to}.xlsx")
        f.outputStream().use { XlsxWriter.write(it, sheets) }
        return f
    }

    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    /** Android Share Sheet */
    fun shareIntent(ctx: Context, f: File): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "แชร์ไฟล์ Excel"
    )

    /** คัดลอกไปยังตำแหน่งที่ผู้ใช้เลือก (Storage Access Framework: CreateDocument) */
    fun copyTo(ctx: Context, f: File, dest: Uri) {
        ctx.contentResolver.openOutputStream(dest)?.use { out -> f.inputStream().use { it.copyTo(out) } }
    }
}
