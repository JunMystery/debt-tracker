package com.example.debt_tracker.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.room.withTransaction
import com.example.debt_tracker.backup.CsvBackupManager
import com.example.debt_tracker.data.local.AppDatabase
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment
import com.example.debt_tracker.notification.DebtReminderScheduler

class DebtRepository private constructor(
    context: Context
) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val debtDao = database.debtDao()
    private val paymentDao = database.paymentDao()
    private val backupManager = CsvBackupManager(appContext)

    fun observeAllDebts(): LiveData<List<Debt>> = debtDao.observeAllDebts()

    fun observeActiveDebts(): LiveData<List<Debt>> = debtDao.observeActiveDebts()

    fun observeCompletedDebts(): LiveData<List<Debt>> = debtDao.observeCompletedDebts()

    fun observeDebtById(debtId: Long): LiveData<Debt?> = debtDao.observeDebtById(debtId)

    fun observePaymentsByDebt(debtId: Long): LiveData<List<Payment>> = paymentDao.observePaymentsByDebt(debtId)

    fun observeAllPayments(): LiveData<List<Payment>> = paymentDao.observeAllPayments()

    suspend fun createDebt(debt: Debt): Long {
        val id = debtDao.insertDebt(debt.copy(updatedAt = System.currentTimeMillis()))
        exportBackup()
        DebtReminderScheduler.rescheduleAll(appContext)
        return id
    }

    suspend fun updateDebt(debt: Debt) {
        debtDao.updateDebt(debt.copy(updatedAt = System.currentTimeMillis()))
        exportBackup()
        DebtReminderScheduler.rescheduleAll(appContext)
    }

    suspend fun deleteDebt(debtId: Long) {
        database.withTransaction {
            paymentDao.deleteByDebtId(debtId)
            debtDao.deleteDebtById(debtId)
        }
        exportBackup()
        DebtReminderScheduler.rescheduleAll(appContext)
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
        DebtReminderScheduler.rescheduleAll(appContext)
    }

    private suspend fun exportBackup() {
        val prefs = appContext.getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val isAutoBackupEnabled = prefs.getBoolean("pref_auto_backup_enabled", false)
        if (isAutoBackupEnabled) {
            try {
                backupManager.export(
                    debts = debtDao.getAllDebtsOnce(),
                    payments = paymentDao.getAllPaymentsOnce()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun forceBackupNow() {
        try {
            backupManager.export(
                debts = debtDao.getAllDebtsOnce(),
                payments = paymentDao.getAllPaymentsOnce()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importBackup(uri: android.net.Uri): Boolean {
        val success = backupManager.importCsv(uri)
        if (success) {
            com.example.debt_tracker.notification.DebtReminderScheduler.scheduleNext(appContext)
        }
        return success
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
