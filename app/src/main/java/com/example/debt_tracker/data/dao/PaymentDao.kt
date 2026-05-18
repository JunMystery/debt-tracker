package com.example.debt_tracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.debt_tracker.data.model.Payment

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE debtId = :debtId ORDER BY paymentDate DESC, id DESC")
    fun observePaymentsByDebt(debtId: Long): LiveData<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, id DESC")
    fun observeAllPayments(): LiveData<List<Payment>>

    @Query("SELECT * FROM payments ORDER BY paymentDate DESC, id DESC")
    suspend fun getAllPaymentsOnce(): List<Payment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: Payment): Long

    @Query("DELETE FROM payments WHERE debtId = :debtId")
    suspend fun deleteByDebtId(debtId: Long)
}
