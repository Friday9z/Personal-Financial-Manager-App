package com.pfm.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY id")
    fun observeAll(): Flow<List<Account>>
    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<Account>
    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): Account?
    @Insert suspend fun insert(a: Account): Long
    @Update suspend fun update(a: Account)
    @Query("UPDATE accounts SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    fun observeAll(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, id")
    suspend fun getAll(): List<Category>
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
    @Insert suspend fun insert(c: Category): Long
    @Insert suspend fun insertAll(c: List<Category>)
    @Update suspend fun update(c: Category)
    @Delete suspend fun delete(c: Category)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY epochMillis DESC, id DESC")
    fun observeAll(): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions ORDER BY epochMillis DESC, id DESC")
    suspend fun getAll(): List<Transaction>
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Transaction?
    @Query("SELECT COUNT(*) FROM transactions WHERE uid = :uid")
    suspend fun countUid(uid: String): Int
    @Query("SELECT COUNT(*) FROM transactions WHERE recurringKey = :key")
    suspend fun countRecurringKey(key: String): Int
    // IGNORE: uid / recurringKey ซ้ำจะไม่ถูกบันทึก (คืน -1)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Transaction): Long
    @Update suspend fun update(t: Transaction)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CardDao {
    @Query("SELECT * FROM credit_cards WHERE archived = 0 ORDER BY id")
    fun observeAll(): Flow<List<CreditCard>>
    @Query("SELECT * FROM credit_cards ORDER BY id")
    suspend fun getAll(): List<CreditCard>
    @Insert suspend fun insert(c: CreditCard): Long
    @Update suspend fun update(c: CreditCard)
    @Query("UPDATE credit_cards SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT * FROM installments ORDER BY id")
    fun observeInstallments(): Flow<List<Installment>>
    @Query("SELECT * FROM installments ORDER BY id")
    suspend fun getInstallments(): List<Installment>
    @Insert suspend fun insertInstallment(i: Installment): Long
    @Update suspend fun updateInstallment(i: Installment)
    @Delete suspend fun deleteInstallment(i: Installment)
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts WHERE archived = 0 ORDER BY id")
    fun observeAll(): Flow<List<Debt>>
    @Query("SELECT * FROM debts ORDER BY id")
    suspend fun getAll(): List<Debt>
    @Insert suspend fun insert(d: Debt): Long
    @Update suspend fun update(d: Debt)
    @Query("UPDATE debts SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<Budget>>
    @Query("SELECT * FROM budgets ORDER BY id")
    suspend fun getAll(): List<Budget>
    @Insert suspend fun insert(b: Budget): Long
    @Update suspend fun update(b: Budget)
    @Delete suspend fun delete(b: Budget)

    @Query("SELECT percent FROM budget_alerts WHERE budgetId = :id AND periodKey = :key")
    suspend fun firedPercents(id: Long, key: String): List<Int>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun logAlert(a: BudgetAlertLog)
    @Query("DELETE FROM budget_alerts WHERE periodKey NOT LIKE :keepPrefix")
    suspend fun pruneAlerts(keepPrefix: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY id")
    fun observeAll(): Flow<List<Recurring>>
    @Query("SELECT * FROM recurring ORDER BY id")
    suspend fun getAll(): List<Recurring>
    @Insert suspend fun insert(r: Recurring): Long
    @Update suspend fun update(r: Recurring)
    @Delete suspend fun delete(r: Recurring)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM savings_goals ORDER BY id")
    fun observeAll(): Flow<List<SavingsGoal>>
    @Query("SELECT * FROM savings_goals ORDER BY id")
    suspend fun getAll(): List<SavingsGoal>
    @Query("SELECT * FROM goal_entries ORDER BY epochMillis DESC")
    fun observeEntries(): Flow<List<GoalEntry>>
    @Insert suspend fun insert(g: SavingsGoal): Long
    @Update suspend fun update(g: SavingsGoal)
    @Delete suspend fun delete(g: SavingsGoal)
    @Insert suspend fun insertEntry(e: GoalEntry): Long
    @Query("DELETE FROM goal_entries WHERE goalId = :goalId")
    suspend fun deleteEntries(goalId: Long)
    @Query("SELECT COALESCE(SUM(delta),0) FROM goal_entries WHERE goalId = :goalId")
    suspend fun sumFor(goalId: Long): Long
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSetting>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(s: AppSetting)
}
