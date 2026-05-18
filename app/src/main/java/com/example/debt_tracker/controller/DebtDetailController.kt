package com.example.debt_tracker.controller

import androidx.lifecycle.LiveData
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment
import com.example.debt_tracker.data.repository.DebtRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DebtDetailController(
    private val repository: DebtRepository
) : BaseController() {

    fun observeDebt(debtId: Long): LiveData<Debt?> = repository.observeDebtById(debtId)

    fun observePayments(debtId: Long): LiveData<List<Payment>> = repository.observePaymentsByDebt(debtId)

    fun addPayment(
        debt: Debt,
        paymentDateMillis: Long,
        amount: Double,
        onSuccess: (() -> Unit)? = null
    ) {
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                repository.addPayment(debt, paymentDateMillis, amount)
            }
            onSuccess?.invoke()
        }
    }

    fun markCompleted(debt: Debt, onSuccess: (() -> Unit)? = null) {
        controllerScope.launch {
            withContext(Dispatchers.IO) {
                repository.markCompleted(debt)
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
