package com.example.debt_tracker.controller

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.ui.model.DebtWithNextDue
import com.example.debt_tracker.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebtController(
    private val repository: DebtRepository
) : BaseController() {

    val activeDebts: LiveData<List<DebtWithNextDue>> =
        repository.observeActiveDebts().map { debts ->
            debts.map { DebtWithNextDue(it, DateUtils.computeNextDueDate(it)) }
                .sortedBy { it.nextDueDate }
        }

    val completedDebts: LiveData<List<DebtWithNextDue>> =
        repository.observeCompletedDebts().map { debts ->
            debts.map { DebtWithNextDue(it, DateUtils.computeNextDueDate(it)) }
        }

    fun createDebt(debt: Debt, onSuccess: (() -> Unit)? = null) {
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                repository.createDebt(debt)
            }
            onSuccess?.invoke()
        }
    }

    fun updateDebt(debt: Debt, onSuccess: (() -> Unit)? = null) {
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateDebt(debt)
            }
            onSuccess?.invoke()
        }
    }

    fun deleteDebt(debtId: Long, onSuccess: (() -> Unit)? = null) {
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteDebt(debtId)
            }
            onSuccess?.invoke()
        }
    }
}
