package com.example.debt_tracker.util

import com.example.debt_tracker.data.model.Debt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

object DateUtils {
    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/yyyy")

    fun parseYearMonth(value: String): YearMonth = YearMonth.parse(value)

    fun formatDay(date: LocalDate): String = date.format(dayFormatter)

    fun formatDay(timestampMillis: Long): String {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return formatDay(date)
    }

    fun formatMonthYear(yearMonth: YearMonth): String = yearMonth.format(monthFormatter)

    fun nowYearMonth(): YearMonth = YearMonth.now()

    fun monthsBetweenInclusive(startYearMonth: String, endYearMonth: String): Int {
        val start = parseYearMonth(startYearMonth)
        val end = parseYearMonth(endYearMonth)
        return (end.year - start.year) * 12 + (end.monthValue - start.monthValue) + 1
    }

    fun remainingMonths(endYearMonth: String, from: YearMonth = nowYearMonth()): Int {
        val end = parseYearMonth(endYearMonth)
        val value = (end.year - from.year) * 12 + (end.monthValue - from.monthValue) + 1
        return value.coerceAtLeast(0)
    }

    fun isActiveForMonth(debt: Debt, month: YearMonth): Boolean {
        val start = parseYearMonth(debt.startYearMonth)
        val end = parseYearMonth(debt.endYearMonth)
        return month >= start && month <= end
    }

    fun computeNextDueDate(
        debt: Debt,
        fromDate: LocalDate = LocalDate.now()
    ): LocalDate? {
        val startMonth = parseYearMonth(debt.startYearMonth)
        val endMonth = parseYearMonth(debt.endYearMonth)
        var cursor = YearMonth.of(fromDate.year, fromDate.month)
        if (cursor < startMonth) {
            cursor = startMonth
        }

        while (cursor <= endMonth) {
            val day = min(debt.dueDayOfMonth, cursor.lengthOfMonth())
            val candidate = LocalDate.of(cursor.year, cursor.month, day)
            if (!candidate.isBefore(fromDate)) {
                return candidate
            }
            cursor = cursor.plusMonths(1)
        }
        return null
    }
}
