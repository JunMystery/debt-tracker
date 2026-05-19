package com.example.debt_tracker.util

import com.example.debt_tracker.data.model.Debt
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

object DateUtils {
    fun parseYearMonth(value: String): YearMonth = YearMonth.parse(value)

    fun getPreferredDateFormat(context: android.content.Context): String {
        val prefs = context.getSharedPreferences("debt_tracker_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getString("pref_date_format", "dd/MM/yyyy") ?: "dd/MM/yyyy"
    }

    fun setPreferredDateFormat(context: android.content.Context, format: String) {
        val prefs = context.getSharedPreferences("debt_tracker_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("pref_date_format", format).apply()
    }

    fun formatDay(context: android.content.Context, date: LocalDate): String {
        val locale = java.util.Locale.getDefault()
        return try {
            val format = getPreferredDateFormat(context)
            val formatter = DateTimeFormatter.ofPattern(format, locale)
            date.format(formatter)
        } catch (e: Exception) {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
            date.format(formatter)
        }
    }

    fun formatDay(context: android.content.Context, timestampMillis: Long): String {
        val date = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        return formatDay(context, date)
    }

    fun formatMonthYear(yearMonth: YearMonth): String {
        val locale = java.util.Locale.getDefault()
        return try {
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "MMyyyy")
            val formatter = DateTimeFormatter.ofPattern(pattern, locale)
            yearMonth.format(formatter)
        } catch (e: Exception) {
            val formatter = DateTimeFormatter.ofPattern("MM/yyyy", locale)
            yearMonth.format(formatter)
        }
    }

    fun nowYearMonth(): YearMonth = YearMonth.now()

    fun monthsBetweenInclusive(startYearMonth: String, endYearMonth: String): Int {
        return try {
            val start = parseYearMonth(startYearMonth)
            val end = parseYearMonth(endYearMonth)
            (end.year - start.year) * 12 + (end.monthValue - start.monthValue) + 1
        } catch (e: Exception) {
            1
        }
    }

    fun remainingMonths(startYearMonth: String, endYearMonth: String, from: YearMonth = nowYearMonth()): Int {
        return try {
            val start = parseYearMonth(startYearMonth)
            val end = parseYearMonth(endYearMonth)
            val startPoint = if (from < start) start else from
            val value = (end.year - startPoint.year) * 12 + (end.monthValue - startPoint.monthValue) + 1
            value.coerceAtLeast(0)
        } catch (e: Exception) {
            0
        }
    }

    fun isActiveForMonth(debt: Debt, month: YearMonth): Boolean {
        return try {
            val start = parseYearMonth(debt.startYearMonth)
            val end = parseYearMonth(debt.endYearMonth)
            month >= start && month <= end
        } catch (e: Exception) {
            false
        }
    }

    fun computeNextDueDate(
        debt: Debt,
        fromDate: LocalDate = LocalDate.now()
    ): LocalDate? {
        return try {
            val startMonth = parseYearMonth(debt.startYearMonth)
            val endMonth = parseYearMonth(debt.endYearMonth)
            
            // Calculate covered months from totalPaid
            val monthsPaid = if (debt.monthlyAmount > 0) (debt.totalPaid / debt.monthlyAmount).toInt() else 0
            val firstUnpaidMonth = startMonth.plusMonths(monthsPaid.toLong())
            
            var cursor = firstUnpaidMonth
            if (cursor < startMonth) {
                cursor = startMonth
            }

            while (cursor <= endMonth) {
                val day = min(debt.dueDayOfMonth, cursor.lengthOfMonth())
                val candidate = LocalDate.of(cursor.year, cursor.month, day)
                return candidate
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
