package com.example.debt_tracker.util

import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.InterestType
import com.example.debt_tracker.data.model.PaymentType
import java.time.YearMonth

object LoanCalculator {

    /**
     * Calculate the next payment amount based on loan type
     */
    fun calculateNextPayment(debt: Debt): Double {
        return when {
            // Fixed Installment: return the fixed monthly amount
            debt.paymentType == PaymentType.INSTALLMENT && debt.interestType == InterestType.FIXED -> {
                debt.monthlyAmount
            }
            
            // Reducing Balance / Credit Line: calculate minimum or suggested payment
            debt.interestType == InterestType.REDUCING -> {
                calculateReducingBalancePayment(debt)
            }
            
            // Interest-Only (Bullet): calculate interest portion only
            debt.paymentType == PaymentType.INTEREST_ONLY -> {
                calculateInterestOnlyPayment(debt)
            }
            
            // Islamic Finance: similar to fixed but uses profit rate
            debt.interestType == InterestType.ISLAMIC -> {
                calculateIslamicPayment(debt)
            }
            
            else -> debt.monthlyAmount // fallback
        }
    }

    /**
     * Calculate monthly payment for reducing balance loan
     * Formula: Interest = (Remaining Balance * Annual Rate / 365) * Days in Month
     * Minimum payment = greater of (Interest + 1% principal) or (MinPaymentPercent * Balance)
     */
    fun calculateReducingBalancePayment(debt: Debt): Double {
        val dailyRate = debt.interestRate / 100.0 / 365.0
        val daysInMonth = YearMonth.now().lengthOfMonth()
        val interestCharge = debt.remainingBalance * dailyRate * daysInMonth
        val principalPayment = maxOf(debt.remainingBalance * 0.01, 0.0)
        val minimumPayment = maxOf(
            interestCharge + principalPayment,
            debt.remainingBalance * debt.minimumPaymentPercent / 100.0
        )
        return minimumPayment
    }

    /**
     * Calculate interest-only payment for bullet loans
     * Formula: Monthly Interest = (Principal * Annual Rate) / 12
     */
    fun calculateInterestOnlyPayment(debt: Debt): Double {
        return (debt.principal * debt.interestRate / 100.0) / 12.0
    }

    /**
     * Calculate payment for Islamic financing (Murabaha/Ijarah)
     * Uses profit rate instead of interest rate
     * Total financed amount = Principal + (Principal * ProfitRate * Tenor)
     * Monthly = Total / Number of Months
     */
    fun calculateIslamicPayment(debt: Debt): Double {
        val totalMonths = calculateTotalMonths(debt)
        if (totalMonths <= 0) return debt.monthlyAmount
        val totalProfit = debt.principal * (debt.interestRate / 100.0) * (totalMonths / 12.0)
        val totalFinanced = debt.principal + totalProfit
        return totalFinanced / totalMonths
    }

    /**
     * Calculate remaining balance after payment
     */
    fun calculateNewBalance(debt: Debt, paymentAmount: Double): Double {
        return when (debt.interestType) {
            InterestType.FIXED, InterestType.ISLAMIC -> {
                // Fixed loans: reduce total expected
                val expectedTotal = debt.monthlyAmount * calculateTotalMonths(debt)
                val newTotalPaid = debt.totalPaid + paymentAmount
                maxOf(expectedTotal - newTotalPaid, 0.0)
            }
            InterestType.REDUCING -> {
                // Reducing balance: direct subtraction
                maxOf(debt.remainingBalance - paymentAmount, 0.0)
            }
        }
    }

    /**
     * Check if debt is fully paid
     */
    fun isDebtCompleted(debt: Debt): Boolean {
        return when (debt.interestType) {
            InterestType.FIXED, InterestType.ISLAMIC -> {
                val expectedTotal = debt.monthlyAmount * calculateTotalMonths(debt)
                debt.totalPaid >= expectedTotal
            }
            InterestType.REDUCING -> {
                debt.remainingBalance <= 0.0
            }
        }
    }

    private fun calculateTotalMonths(debt: Debt): Int {
        return try {
            val start = YearMonth.parse(debt.startYearMonth)
            val end = YearMonth.parse(debt.endYearMonth)
            ((end.year - start.year) * 12) + (end.monthValue - start.monthValue) + 1
        } catch (e: Exception) {
            1
        }
    }
}
