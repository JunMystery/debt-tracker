package com.example.debt_tracker.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {
    fun format(value: Double): String {
        val currentLocale = Locale.getDefault()
        val tag = currentLocale.toLanguageTag()

        val formatLocale = when {
            tag.startsWith("en-GB", ignoreCase = true) -> Locale("en", "GB")
            tag.startsWith("vi", ignoreCase = true) -> Locale("vi", "VN")
            tag.startsWith("zh-TW", ignoreCase = true) || tag.startsWith("zh-HK", ignoreCase = true) || tag.contains("Hant", ignoreCase = true) -> Locale("zh", "TW")
            tag.startsWith("zh", ignoreCase = true) -> Locale("zh", "CN")
            tag.startsWith("ja", ignoreCase = true) -> Locale("ja", "JP")
            tag.startsWith("ko", ignoreCase = true) -> Locale("ko", "KR")
            else -> Locale("en", "US") // en-US default
        }

        val formatter = NumberFormat.getCurrencyInstance(formatLocale)
        
        // Hide decimal places for TWD, JPY, KRW, and VND since they are non-fractional currencies
        val currencyCode = getCurrencyCodeForLocale(formatLocale)
        if (currencyCode == "JPY" || currencyCode == "KRW" || currencyCode == "VND" || currencyCode == "TWD") {
            formatter.maximumFractionDigits = 0
        }
        
        return formatter.format(value)
    }

    fun getFormattedAmount(amount: Double, fromCurrency: String): String {
        val targetCurrency = getCurrencyCode()
        val converted = ExchangeRateService.convert(amount, fromCurrency, targetCurrency)
        return format(converted)
    }

    fun getCurrencyCode(): String {
        return getCurrencyCodeForLocale(Locale.getDefault())
    }

    private fun getCurrencyCodeForLocale(locale: Locale): String {
        val tag = locale.toLanguageTag()
        return when {
            tag.startsWith("en-GB", ignoreCase = true) -> "GBP"
            tag.startsWith("vi", ignoreCase = true) -> "VND"
            tag.startsWith("zh-TW", ignoreCase = true) || tag.startsWith("zh-HK", ignoreCase = true) || tag.contains("Hant", ignoreCase = true) -> "TWD"
            tag.startsWith("zh", ignoreCase = true) -> "CNY"
            tag.startsWith("ja", ignoreCase = true) -> "JPY"
            tag.startsWith("ko", ignoreCase = true) -> "KRW"
            else -> "USD"
        }
    }
}
