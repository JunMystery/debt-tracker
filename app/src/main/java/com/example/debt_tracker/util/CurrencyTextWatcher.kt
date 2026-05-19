package com.example.debt_tracker.util

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.widget.EditText
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * A custom [TextWatcher] that formats numeric input into proper localized currency displays in real-time.
 * 
 * Requirements satisfied:
 * 1. Shows default numeric keyboard only (restricts input key listener to digits).
 * 2. Formats VND with dot separators (e.g. 1.000.000 VND).
 * 3. Formats USD from cents (e.g. types 100 -> 1.00 USD).
 * 4. Renders standard formatting rules for JPY, KRW, GBP, CNY, etc.
 * 5. Returns raw numeric values dynamically to the caller.
 */
class CurrencyTextWatcher(
    private val editText: EditText,
    private val currencyCode: String,
    private val onRawValueChanged: (Double) -> Unit
) : TextWatcher {

    private var currentText = ""
    private var isFormatting = false

    init {
        // Enforce numeric-only soft keyboard
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        // Restrict user typing to digits only
        editText.keyListener = DigitsKeyListener.getInstance("0123456789")
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (isFormatting) return
        
        val input = s.toString()
        if (input == currentText) return

        isFormatting = true

        // Extract raw digits
        val cleanString = input.replace(Regex("[^\\d]"), "")
        
        if (cleanString.isEmpty()) {
            editText.setText("")
            currentText = ""
            onRawValueChanged(0.0)
            isFormatting = false
            return
        }

        val rawValue: Double
        val formatted: String

        val activeCurrency = currencyCode.uppercase()
        when (activeCurrency) {
            "VND" -> {
                // VND: Thousand separator (dot), non-fractional, suffix " VND"
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                rawValue = parsed
                
                val formatter = NumberFormat.getNumberInstance(Locale("vi", "VN"))
                val formattedNumber = formatter.format(parsed)
                formatted = "$formattedNumber VND"
            }
            "USD" -> {
                // USD: Start from cents (e.g. user types 100 -> 1.00 USD)
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                rawValue = parsed / 100.0
                
                val decimalValue = BigDecimal(cleanString).divide(BigDecimal(100))
                val formatter = NumberFormat.getNumberInstance(Locale.US)
                formatter.minimumFractionDigits = 2
                formatter.maximumFractionDigits = 2
                formatted = "${formatter.format(decimalValue)} USD"
            }
            "JPY", "KRW" -> {
                // Non-fractional currencies (e.g., JPY, KRW)
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                rawValue = parsed
                
                val formatter = NumberFormat.getNumberInstance(Locale.US)
                formatted = "${formatter.format(parsed)} $activeCurrency"
            }
            else -> {
                // Standard fractional currencies (e.g., GBP, CNY, EUR, TWD): start from cents
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                rawValue = parsed / 100.0
                
                val decimalValue = BigDecimal(cleanString).divide(BigDecimal(100))
                val formatter = NumberFormat.getNumberInstance(Locale.US)
                formatter.minimumFractionDigits = 2
                formatter.maximumFractionDigits = 2
                formatted = "${formatter.format(decimalValue)} $activeCurrency"
            }
        }

        currentText = formatted
        editText.setText(formatted)
        
        // Adjust cursor selection before the currency code suffix
        val suffix = " $activeCurrency"
        val selectionIndex = if (formatted.endsWith(suffix)) {
            formatted.length - suffix.length
        } else {
            formatted.length
        }
        
        try {
            editText.setSelection(selectionIndex.coerceIn(0, formatted.length))
        } catch (e: Exception) {
            editText.setSelection(formatted.length)
        }

        // Fire callback with the raw parsed double value
        onRawValueChanged(rawValue)

        isFormatting = false
    }
}
