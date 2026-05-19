package com.example.debt_tracker.controller

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.ui.model.DashboardData
import com.example.debt_tracker.ui.model.DebtWithNextDue
import com.example.debt_tracker.util.DateUtils
import java.time.YearMonth

class DashboardController(
    private val context: Context,
    private val repository: DebtRepository
) : BaseController() {
    private val debtsSource = repository.observeAllDebts()
    private val paymentsSource = repository.observeAllPayments()
    private val refreshTrigger = MutableLiveData<Unit>()

    val dashboardData: LiveData<DashboardData> = MediatorLiveData<DashboardData>().apply {
        var debts = emptyList<Debt>()
        var payments = emptyList<Payment>()

        fun rebuild() {
            value = buildDashboardData(debts, payments)
        }

        addSource(debtsSource) {
            debts = it ?: emptyList()
            rebuild()
        }
        addSource(paymentsSource) {
            payments = it ?: emptyList()
            rebuild()
        }
        addSource(refreshTrigger) {
            rebuild()
        }
    }

    fun refresh() {
        refreshTrigger.value = Unit
    }

    private fun buildDashboardData(debts: List<Debt>, payments: List<Payment>): DashboardData {
        val currentMonth = DateUtils.nowYearMonth()
        val activeDebts = debts.filter { !it.isCompleted && DateUtils.isActiveForMonth(it, currentMonth) }
        val totalDue = activeDebts.sumOf { it.monthlyAmount }

        val paidDebtIdsThisMonth = payments
            .filter { payment ->
                val paymentMonth = YearMonth.from(
                    java.time.Instant.ofEpochMilli(payment.paymentDate)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                )
                paymentMonth == currentMonth
            }
            .map { it.debtId }
            .toSet()

        val paidCount = activeDebts.count { paidDebtIdsThisMonth.contains(it.id) }
        val upcoming = debts
            .filter { !it.isCompleted }
            .map { DebtWithNextDue(it, DateUtils.computeNextDueDate(it)) }
            .sortedBy { it.nextDueDate }
            .take(5)

        return DashboardData(
            totalDueThisMonth = totalDue,
            paidCount = paidCount,
            totalActiveCount = activeDebts.size,
            upcomingDebts = upcoming
        )
    }
}
