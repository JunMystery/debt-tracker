package com.example.debt_tracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val creditorName: String,
    val contractNumber: String,
    val dueDayOfMonth: Int,
    val monthlyAmount: Double,
    val startYearMonth: String,
    val endYearMonth: String,
    val totalPaid: Double = 0.0,
    val principal: Double = 0.0,
    val isCompleted: Boolean = false,
    val completionTimestamp: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Multi-type loan fields
    val interestRate: Double = 0.0,
    val interestType: InterestType = InterestType.FIXED,
    val paymentType: PaymentType = PaymentType.INSTALLMENT,
    val creditLimit: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val lastInterestCalculationDate: Long? = null,
    val minimumPaymentPercent: Double = 0.0
)
