package com.example.debt_tracker.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.debt_tracker.data.model.Debt

@Dao
interface DebtDao {
    @Query("SELECT * FROM debts ORDER BY updatedAt DESC")
    fun observeAllDebts(): LiveData<List<Debt>>

    @Query("SELECT * FROM debts WHERE isCompleted = 0 ORDER BY updatedAt DESC")
    fun observeActiveDebts(): LiveData<List<Debt>>

    @Query("SELECT * FROM debts WHERE isCompleted = 1 ORDER BY completionTimestamp DESC, updatedAt DESC")
    fun observeCompletedDebts(): LiveData<List<Debt>>

    @Query("SELECT * FROM debts WHERE id = :debtId LIMIT 1")
    fun observeDebtById(debtId: Long): LiveData<Debt?>

    @Query("SELECT * FROM debts ORDER BY updatedAt DESC")
    suspend fun getAllDebtsOnce(): List<Debt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: Debt): Long

    @Update
    suspend fun updateDebt(debt: Debt)

    @Query("DELETE FROM debts WHERE id = :debtId")
    suspend fun deleteDebtById(debtId: Long)
}
