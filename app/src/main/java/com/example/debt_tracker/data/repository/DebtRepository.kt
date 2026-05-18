package com.example.debt_tracker.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.example.debt_tracker.backup.CsvBackupManager
import com.example.debt_tracker.data.local.AppDatabase
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment

class DebtRepository private constructor(
    context: Context
) {
    private val database = AppDatabase.getInstance(context)
    private val debtDao = database.debtDao()
    private val paymentDao = database.paymentDao()
    private val backupManager = CsvBackupManager(context.applicationContext)

    fun observeAllDebts(): LiveData<List<Debt>> = debtDao.observeAllDebts()

    fun observeActiveDebts(): LiveData<List<Debt>> = debtDao.observeActiveDebts()

    fun observeCompletedDebts(): LiveData<List<Debt>> = debtDao.observeCompletedDebts()

    fun observeDebtById(debtId: Long): LiveData<Debt?> = debtDao.observeDebtById(debtId)

    fun observePaymentsByDebt(debtId: Long): LiveData<List<Payment>> = paymentDao.observePaymentsByDebt(debtId)

    fun observeAllPayments(): LiveData<List<Payment>> = paymentDao.observeAllPayments()

    suspend fun createDebt(debt: Debt): Long {
        val id = debtDao.insertDebt(debt.copy(updatedAt = System.currentTimeMillis()))
        exportBackup()
        return id
    }

    suspend fun updateDebt(debt: Debt) {
        debtDao.updateDebt(debt.copy(updatedAt = System.currentTimeMillis()))
        exportBackup()
    }

    suspend fun deleteDebt(debtId: Long) {
        database.withTransaction {
            paymentDao.deleteByDebtId(debtId)
            debtDao.deleteDebtById(debtId)
        }
        exportBackup()
    }

    suspend fun markCompleted(debt: Debt) {
        updateDebt(
            debt.copy(
                isCompleted = true,
                completionTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun addPayment(debt: Debt, paymentDateMillis: Long, amount: Double) {
        database.withTransaction {
            paymentDao.insertPayment(
                Payment(
                    debtId = debt.id,
                    paymentDate = paymentDateMillis,
                    amount = amount
                )
            )
            debtDao.updateDebt(
                debt.copy(
                    totalPaid = debt.totalPaid + amount,
                    isCompleted = debt.isCompleted || (debt.totalPaid + amount >= debt.expectedTotal()),
                    completionTimestamp = if (debt.totalPaid + amount >= debt.expectedTotal()) {
                        System.currentTimeMillis()
                    } else {
                        debt.completionTimestamp
                    },
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        exportBackup()
    }

    private suspend fun exportBackup() {
        backupManager.export(
            debts = debtDao.getAllDebtsOnce(),
            payments = paymentDao.getAllPaymentsOnce()
        )
    }

    private fun Debt.expectedTotal(): Double {
        val months = com.example.debt_tracker.util.DateUtils.monthsBetweenInclusive(startYearMonth, endYearMonth)
        return months * monthlyAmount
    }

    companion object {
        @Volatile
        private var INSTANCE: DebtRepository? = null

        fun getInstance(context: Context): DebtRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DebtRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
