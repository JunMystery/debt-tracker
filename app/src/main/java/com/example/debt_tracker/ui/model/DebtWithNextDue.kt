package com.example.debt_tracker.ui.model

import com.example.debt_tracker.data.model.Debt
import java.time.LocalDate

data class DebtWithNextDue(
    val debt: Debt,
    val nextDueDate: LocalDate?
)
