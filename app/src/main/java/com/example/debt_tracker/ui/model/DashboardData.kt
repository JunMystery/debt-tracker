package com.example.debt_tracker.ui.model

data class DashboardData(
    val totalDueThisMonth: Double = 0.0,
    val paidCount: Int = 0,
    val totalActiveCount: Int = 0,
    val upcomingDebts: List<DebtWithNextDue> = emptyList()
)
