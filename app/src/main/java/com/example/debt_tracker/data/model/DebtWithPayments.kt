package com.example.debt_tracker.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class DebtWithPayments(
    @Embedded val debt: Debt,
    @Relation(parentColumn = "id", entityColumn = "debtId")
    val payments: List<Payment>
)
