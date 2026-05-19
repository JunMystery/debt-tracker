package com.example.debt_tracker.util

object ExchangeRateService {
    // Offline-only high-fidelity static conversion rates to USD
    // The application operates strictly offline and uses no network-based conversion APIs.
    private val staticRates = mapOf<String, Double>(
        "USD" to 1.0,
        "VND" to 25400.0,
        "GBP" to 0.78,
        "CNY" to 7.25,
        "JPY" to 156.0,
        "KRW" to 1350.0,
        "TWD" to 32.0
    )

    /**
     * Returns the static rate relative to USD.
     */
    fun getRate(currency: String): Double {
        return staticRates[currency.uppercase()] ?: 1.0
    }

    /**
     * Synchronously converts an amount from one currency to another using the static memory map.
     */
    fun convert(amount: Double, fromCurrency: String, toCurrency: String): Double {
        val fCurr = fromCurrency.uppercase()
        val tCurr = toCurrency.uppercase()
        if (fCurr == tCurr) return amount
        
        val rateFrom = getRate(fCurr)
        val rateTo = getRate(tCurr)
        
        if (rateFrom <= 0.0 || rateTo <= 0.0) return amount
        
        val usdAmount = amount / rateFrom
        return usdAmount * rateTo
    }
}
