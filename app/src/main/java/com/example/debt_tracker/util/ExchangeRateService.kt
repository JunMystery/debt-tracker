package com.example.debt_tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object ExchangeRateService {
    private val cachedRates = mutableMapOf<String, Double>()
    private val lastFetchTimes = mutableMapOf<String, Long>()
    private const val CACHE_DURATION = 15 * 60 * 1000 // 15 minutes cache

    suspend fun getUsdToTargetRate(targetCurrency: String): Double? {
        val now = System.currentTimeMillis()
        val cache = cachedRates[targetCurrency]
        val lastFetch = lastFetchTimes[targetCurrency] ?: 0
        if (cache != null && now - lastFetch < CACHE_DURATION) {
            return cache
        }

        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("https://api.budjet.org/fiat/USD/$targetCurrency")
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
                        cachedRates[targetCurrency] = rate
                        lastFetchTimes[targetCurrency] = System.currentTimeMillis()
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
