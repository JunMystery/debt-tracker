package com.example.debt_tracker.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    private val formatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("vi", "VN")).apply {
        maximumFractionDigits = 0
    }

    fun format(value: Double): String = formatter.format(value)
}
