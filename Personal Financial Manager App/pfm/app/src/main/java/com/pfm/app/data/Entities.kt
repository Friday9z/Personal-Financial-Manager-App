package com.pfm.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ทุกจำนวนเงินเก็บเป็น Long หน่วย "สตางค์" (฿1.00 = 100) เพื่อตัดปัญหาทศนิยมลอยตัว
 * ยอดคงเหลือของบัญชี/บัตร/หนี้ "คำนวณจาก Transaction" เสมอ (ไม่เก็บซ้ำ) จึงตรงกับรายการย้อนหลังเสมอ
 */

object TxType {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
    const val TRANSFER = "TRANSFER"
    const val CARD_PAYMENT = "CARD_PAYMENT"          // ชำระบัตรเครดิตจากบัญชี (ไม่ใช่รายจ่าย)
    const val DEBT_PAYMENT = "DEBT_PAYMENT"          // ชำระหนี้ (เงินต้น/ดอกเบี้ย/ค่าธรรมเนียม)
    const val INSTALLMENT_PAYMENT = "INSTALLMENT_PAYMENT" // จ่ายงวดผ่อนบัตร
}

object AccountType {
    const val BANK = "BANK"
    const val CASH = "CASH"
    const val SAVINGS = "SAVINGS"
    const val JAR = "JAR"
    const val EWALLET = "EWALLET"
    const val CUSTOM = "CUSTOM"
}

object DebtKind {
    const val CREDIT_CARD = "CREDIT_CARD"
    const val PERSONAL_LOAN = "PERSONAL_LOAN"
    const val INSTALLMENT = "INSTALLMENT"
    const val BANK = "BANK"
    const val PERSON = "PERSON"
    const val OTHER = "OTHER"
}

object Freq {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
    const val CUSTOM = "CUSTOM" // ทุก N วัน
}

object BudgetPeriod {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"
    const val YEARLY = "YEARLY"
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = AccountType.BANK,
    val bank: String = "",
    val maskedRef: String = "",
    val initialBalance: Long = 0,
    val color: Long = 0xFF1E6BFF,
    val icon: String = "account_balance",
    val note: String = "",
    val includeInTotal: Boolean = true,
    val archived: Boolean = false
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String, // INCOME | EXPENSE
    val icon: String,
    val color: Long = 0xFF607D8B,
    val sortOrder: Int = 0
)

@Entity(tableName = "credit_cards")
data class CreditCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String = "",
    val cardType: String = "",
    val creditLimit: Long,
    val statementDay: Int = 1,
    val dueDay: Int = 20,
    val payAccountId: Long? = null,
    val color: Long = 0xFF7B1FA2,
    val icon: String = "credit_card",
    val archived: Boolean = false
)

@Entity(
    tableName = "installments",
    foreignKeys = [ForeignKey(CreditCard::class, ["id"], ["cardId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("cardId")]
)
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cardId: Long,
    val totalAmount: Long,       // ยอดผ่อนทั้งหมด (รวมดอกเบี้ย)
    val months: Int,
    val monthlyAmount: Long,
    val interestRatePct: Double = 0.0,
    val startDay: Long,          // epochDay งวดแรก
    val categoryId: Long? = null
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = DebtKind.OTHER,
    val initialAmount: Long,
    val interestRatePct: Double = 0.0,
    val monthlyPayment: Long = 0,
    val dueDay: Int = 1,
    val termMonths: Int = 0,
    val payAccountId: Long? = null,
    val note: String = "",
    val archived: Boolean = false
)

@Entity(
    tableName = "transactions",
    indices = [
        Index("epochMillis"), Index("accountId"), Index("toAccountId"),
        Index("cardId"), Index("categoryId"), Index("debtId"), Index("installmentId"),
        Index(value = ["uid"], unique = true),
        Index(value = ["recurringKey"], unique = true)
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,                         // กันบันทึกซ้ำ (UUID/idempotency key)
    val epochMillis: Long,
    val title: String,
    val type: String,
    val categoryId: Long? = null,
    val amount: Long,                        // ยอดรวมของรายการ (>0)
    val accountId: Long? = null,             // บัญชีต้นทาง
    val toAccountId: Long? = null,           // บัญชีปลายทาง (Transfer)
    val cardId: Long? = null,                // บัตรที่ใช้ / บัตรที่ชำระ
    val debtId: Long? = null,
    val installmentId: Long? = null,
    val principal: Long = 0,                 // เฉพาะ DEBT_PAYMENT
    val interest: Long = 0,
    val fee: Long = 0,
    val note: String = "",
    val refNo: String = "",
    val attachmentPath: String? = null,
    val scheduled: Boolean = false,          // สร้างจากรายการประจำ
    val recurringId: Long? = null,
    val recurringKey: String? = null         // "recId:epochDay" ห้ามซ้ำ
)

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String,                        // INCOME | EXPENSE
    val amount: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val cardId: Long? = null,
    val startDay: Long,                      // epochDay
    val endDay: Long? = null,
    val freq: String = Freq.MONTHLY,
    val interval: Int = 1,                   // ทุก N หน่วย (CUSTOM = ทุก N วัน)
    val autoCreate: Boolean = true,          // false = แจ้งเตือนให้ยืนยัน
    val enabled: Boolean = true
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val period: String = BudgetPeriod.MONTHLY,
    val limitAmount: Long,
    val categoryId: Long? = null,            // null = ทุกหมวด
    val accountId: Long? = null,             // งบเฉพาะบัญชี
    val fromDay: Long,                       // epochDay เริ่มมีผล (ใช้วางแผนล่วงหน้า)
    val toDay: Long? = null,
    val alertPercents: String = "50,80,100"  // ตั้งเองได้ คั่นด้วย ,
)

/** กันแจ้งเตือนงบซ้ำ: 1 แถวต่อ (budget, ช่วงเวลา, เปอร์เซ็นต์) */
@Entity(tableName = "budget_alerts", primaryKeys = ["budgetId", "periodKey", "percent"])
data class BudgetAlertLog(val budgetId: Long, val periodKey: String, val percent: Int, val firedAt: Long)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val color: Long = 0xFF00A86B,
    val icon: String = "savings",
    val deadlineDay: Long? = null
)

/** เพิ่ม(+)/ถอน(−) เงินจากเป้าหมาย — เป็นการ "กันเงิน" ไม่กระทบยอดบัญชีและไม่ใช่รายรับ/รายจ่าย */
@Entity(tableName = "goal_entries", indices = [Index("goalId")])
data class GoalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val delta: Long,
    val epochMillis: Long,
    val note: String = ""
)

@Entity(tableName = "app_settings")
data class AppSetting(@PrimaryKey val key: String, val value: String)
