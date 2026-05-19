package com.example.debt_tracker.util

import android.content.Context
import java.text.NumberFormat
import java.util.Locale

object CurrencyUtils {

    /**
     * Retrieves the manually selected preferred currency from SharedPreferences.
     * If not set, defaults to the currency matching the active device locale.
     */
    fun getPreferredCurrency(context: Context): String {
        val prefs = context.getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        return prefs.getString("pref_preferred_currency", null) ?: getCurrencyCodeForLocale(Locale.getDefault())
    }

    /**
     * Saves the manually selected preferred currency to SharedPreferences.
     */
    fun setPreferredCurrency(context: Context, currencyCode: String) {
        val prefs = context.getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        prefs.edit().putString("pref_preferred_currency", currencyCode.uppercase()).apply()
    }

    /**
     * Formats a double value strictly in the given currency code's standard representation.
     */
    fun format(value: Double, currencyCode: String): String {
        val formatLocale = when (currencyCode.uppercase()) {
            "GBP" -> Locale("en", "GB")
            "VND" -> Locale("vi", "VN")
            "TWD" -> Locale("zh", "TW")
            "CNY" -> Locale("zh", "CN")
            "JPY" -> Locale("ja", "JP")
            "KRW" -> Locale("ko", "KR")
            else -> Locale("en", "US") // en-US default
        }

        val formatter = NumberFormat.getCurrencyInstance(formatLocale)
        
        // Hide decimal places for TWD, JPY, KRW, and VND since they are non-fractional currencies
        if (currencyCode == "JPY" || currencyCode == "KRW" || currencyCode == "VND" || currencyCode == "TWD") {
            formatter.maximumFractionDigits = 0
        }
        
        return formatter.format(value)
    }

    /**
     * Formats a double value in the user's manually selected preferred currency.
     */
    fun formatPreferred(context: Context, value: Double): String {
        val targetCurrency = getPreferredCurrency(context)
        return format(value, targetCurrency)
    }

    /**
     * Formats an amount in its native base currency.
     */
    fun getFormattedAmount(context: Context, amount: Double, fromCurrency: String): String {
        return format(amount, fromCurrency)
    }

    /**
     * Resolves the default currency code matching a locale. Retained for fallbacks.
     */
    fun getCurrencyCodeForLocale(locale: Locale): String {
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
