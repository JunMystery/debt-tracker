package com.example.debt_tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ExchangeRateService {
    // Memory cache pre-filled with high-fidelity fallbacks to guarantee offline standard conversion
    private val cachedRates = mutableMapOf<String, Double>(
        "USD" to 1.0,
        "VND" to 25400.0,
        "GBP" to 0.78,
        "CNY" to 7.25,
        "JPY" to 156.0,
        "KRW" to 1350.0,
        "TWD" to 32.0
    )

    private val lastFetchTimes = mutableMapOf<String, Long>()
    private const val CACHE_DURATION = 15 * 60 * 1000 // 15 minutes cache
    private var isLoaded = false

    /**
     * Pre-fetches conversion rates relative to USD in the background for all supported currencies.
     */
    suspend fun loadRates() {
        if (isLoaded) return
        withContext(Dispatchers.IO) {
            val currencies = listOf("VND", "GBP", "CNY", "JPY", "KRW", "TWD")
            for (curr in currencies) {
                var connection: HttpURLConnection? = null
                try {
                    val url = URL("https://api.budjet.org/fiat/USD/$curr")
                    connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                        }
                        reader.close()

                        val json = JSONObject(response.toString())
                        if (json.getString("result") == "success") {
                            val rate = json.getDouble("conversion_rate")
                            cachedRates[curr] = rate
                            lastFetchTimes[curr] = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    connection?.disconnect()
                }
            }
            isLoaded = true
        }
    }

    /**
     * Returns the conversion rate relative to USD.
     */
    fun getRate(currency: String): Double {
        return cachedRates[currency.uppercase()] ?: 1.0
    }

    /**
     * Synchronously converts an amount from one currency to another using the memory cache.
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

    /**
     * Fetches or retrieves the cached rate. Retained for backward-compatibility.
     */
    suspend fun getUsdToTargetRate(targetCurrency: String): Double? {
        val tCurr = targetCurrency.uppercase()
        val now = System.currentTimeMillis()
        val cache = cachedRates[tCurr]
        val lastFetch = lastFetchTimes[tCurr] ?: 0
        if (cache != null && now - lastFetch < CACHE_DURATION && cache != 1.0) {
            return cache
        }

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.budjet.org/fiat/USD/$tCurr")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val json = JSONObject(response.toString())
                    if (json.getString("result") == "success") {
                        val rate = json.getDouble("conversion_rate")
                        cachedRates[tCurr] = rate
                        lastFetchTimes[tCurr] = System.currentTimeMillis()
                        rate
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                connection?.disconnect()
            }
        }
    }
}
