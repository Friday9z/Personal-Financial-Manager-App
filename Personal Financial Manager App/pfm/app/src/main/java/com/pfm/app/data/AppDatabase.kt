package com.pfm.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Account::class, Category::class, CreditCard::class, Installment::class, Debt::class,
        Transaction::class, Recurring::class, Budget::class, BudgetAlertLog::class,
        SavingsGoal::class, GoalEntry::class, AppSetting::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun cards(): CardDao
    abstract fun debts(): DebtDao
    abstract fun budgets(): BudgetDao
    abstract fun recurring(): RecurringDao
    abstract fun goals(): GoalDao
    abstract fun settings(): SettingDao

    companion object {
        const val FILE_NAME = "pfm.db"
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, FILE_NAME)
                .build().also { inst = it }
        }

        /** ปิดและล้าง singleton (ใช้ตอน Restore ไฟล์ฐานข้อมูล) */
        fun closeAndReset() = synchronized(this) { inst?.close(); inst = null }
    }
}
