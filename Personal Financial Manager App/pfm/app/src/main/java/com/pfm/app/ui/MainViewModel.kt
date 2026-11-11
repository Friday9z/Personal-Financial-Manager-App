package com.pfm.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pfm.app.PfmApp
import com.pfm.app.data.*
import com.pfm.app.domain.FinanceCalc
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val txs: List<Transaction> = emptyList(),
    val cards: List<CreditCard> = emptyList(),
    val insts: List<Installment> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val goalEntries: List<GoalEntry> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val loaded: Boolean = false
) {
    fun cat(id: Long?) = categories.firstOrNull { it.id == id }
    fun acc(id: Long?) = accounts.firstOrNull { it.id == id }
    fun card(id: Long?) = cards.firstOrNull { it.id == id }
    fun balance(a: Account) = FinanceCalc.accountBalance(a, txs)
    val totalAssets get() = FinanceCalc.totalAssets(accounts, txs)
    fun goalSaved(g: SavingsGoal) = goalEntries.filter { it.goalId == g.id }.sumOf { it.delta }
    val themeMode get() = settings["theme"] ?: "system"
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val repo = (app as PfmApp).repo

    private val part1 = combine(repo.accounts, repo.categories, repo.transactions, repo.cards, repo.installments) { a, c, t, k, i ->
        UiState(accounts = a, categories = c, txs = t, cards = k, insts = i)
    }
    private val part2 = combine(repo.debts, repo.budgets, repo.recurring, repo.goals, repo.goalEntries) { d, b, r, g, e ->
        listOf(d, b, r, g, e)
    }

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<UiState> = combine(part1, part2, repo.settings) { p1, p2, s ->
        p1.copy(
            debts = p2[0] as List<Debt>, budgets = p2[1] as List<Budget>, recurring = p2[2] as List<Recurring>,
            goals = p2[3] as List<SavingsGoal>, goalEntries = p2[4] as List<GoalEntry>,
            settings = s.associate { it.key to it.value }, loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            repo.seedIfNeeded()
            repo.processRecurring()
        }
    }

    /** onResult(error?) — null = สำเร็จ */
    fun save(t: Transaction, onResult: (String?) -> Unit = {}) = viewModelScope.launch { onResult(repo.saveTransaction(t)) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteTransaction(id) }
    fun put(key: String, value: String) = viewModelScope.launch { repo.putSetting(key, value) }

    fun run(block: suspend FinanceRepository.() -> Unit) = viewModelScope.launch { repo.block() }
    fun db() = repo.db

    fun newTx(
        type: String, amount: Long, title: String, date: LocalDate = LocalDate.now(),
        categoryId: Long? = null, accountId: Long? = null, toAccountId: Long? = null, cardId: Long? = null,
        note: String = "", refNo: String = "", attachment: String? = null
    ) = Transaction(
        uid = repo.newUid(), epochMillis = repo.millisOf(date, java.time.LocalTime.now().hour), title = title, type = type,
        categoryId = categoryId, amount = amount, accountId = accountId, toAccountId = toAccountId, cardId = cardId,
        note = note, refNo = refNo, attachmentPath = attachment
    )
}
