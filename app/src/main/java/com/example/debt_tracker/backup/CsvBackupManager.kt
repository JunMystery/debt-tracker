package com.example.debt_tracker.backup

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvBackupManager(
    private val context: Context
) {
    fun export(debts: List<Debt>, payments: List<Payment>) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val fileName = "debt_tracker_backup.csv"
        val relativePath = "Download/Debt-Tracker"

        val selection =
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val cursor = resolver.query(
            collection,
            arrayOf(MediaStore.Downloads._ID),
            selection,
            arrayOf(fileName, "$relativePath/"),
            null
        )
        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                resolver.delete(uri, null, null)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }
        val uri = resolver.insert(collection, values)
            ?: error("Cannot create backup file in Downloads")
        val output = resolver.openOutputStream(uri)
            ?: error("Cannot open backup output stream")

        OutputStreamWriter(output).use { writer ->
            writer.appendLine("ID,Creditor,Contract,MonthlyAmount,DueDay,Start,End,TotalPaid,Principal,Completed")
            debts.forEach { debt ->
                writer.appendLine(
                    listOf(
                        debt.id,
                        debt.creditorName.csvSafe(),
                        debt.contractNumber.csvSafe(),
                        debt.monthlyAmount,
                        debt.dueDayOfMonth,
                        debt.startYearMonth,
                        debt.endYearMonth,
                        debt.totalPaid,
                        debt.principal,
                        debt.isCompleted
                    ).joinToString(",")
                )
            }
            writer.appendLine()
            writer.appendLine("Payments:")
            writer.appendLine("PaymentID,DebtID,PaymentDate,Amount")
            payments.forEach { payment ->
                writer.appendLine(
                    listOf(
                        payment.id,
                        payment.debtId,
                        Instant.ofEpochMilli(payment.paymentDate)
                            .atZone(ZoneId.systemDefault())
                            .format(DATE_FORMATTER),
                        payment.amount
                    ).joinToString(",")
                )
            }
            writer.flush()
        }
    }

    private fun String.csvSafe(): String {
        return "\"${replace("\"", "\"\"")}\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        var currentToken = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    currentToken.append('"')
                    i++ // skip next quote
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(currentToken.toString())
                currentToken = StringBuilder()
            } else {
                currentToken.append(c)
            }
            i++
        }
        result.add(currentToken.toString())
        return result
    }

    suspend fun importCsv(uri: android.net.Uri): Boolean {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return false

        val debtsToInsert = mutableListOf<Debt>()
        val paymentsToInsert = mutableListOf<Payment>()

        try {
            java.io.BufferedReader(java.io.InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                var isReadingPayments = false

                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        line = reader.readLine()
                        continue
                    }

                    if (trimmed.equals("Payments:", ignoreCase = true)) {
                        isReadingPayments = true
                        line = reader.readLine()
                        continue
                    }

                    val parts = parseCsvLine(trimmed)
                    if (parts.isEmpty()) {
                        line = reader.readLine()
                        continue
                    }

                    if (!isReadingPayments) {
                        // Skip header row
                        if (parts[0] == "ID" || parts[0] == "Creditor") {
                            line = reader.readLine()
                            continue
                        }

                        // ID,Creditor,Contract,MonthlyAmount,DueDay,Start,End,TotalPaid,Principal,Completed
                        if (parts.size >= 10) {
                            val id = parts[0].toLongOrNull() ?: 0L
                            val creditor = parts[1]
                            val contract = parts[2]
                            val monthlyAmount = parts[3].toDoubleOrNull() ?: 0.0
                            val dueDay = parts[4].toIntOrNull() ?: 1
                            val start = parts[5]
                            val end = parts[6]
                            val totalPaid = parts[7].toDoubleOrNull() ?: 0.0
                            val principal = parts[8].toDoubleOrNull() ?: 0.0
                            val completed = parts[9].toBoolean()

                            debtsToInsert.add(
                                Debt(
                                    id = id,
                                    creditorName = creditor,
                                    contractNumber = contract,
                                    dueDayOfMonth = dueDay,
                                    monthlyAmount = monthlyAmount,
                                    startYearMonth = start,
                                    endYearMonth = end,
                                    totalPaid = totalPaid,
                                    principal = principal,
                                    isCompleted = completed,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        // Skip header row
                        if (parts[0] == "PaymentID" || parts[0] == "DebtID") {
                            line = reader.readLine()
                            continue
                        }

                        // PaymentID,DebtID,PaymentDate,Amount
                        if (parts.size >= 4) {
                            val paymentId = parts[0].toLongOrNull() ?: 0L
                            val debtId = parts[1].toLongOrNull() ?: 0L
                            val dateStr = parts[2]
                            val amount = parts[3].toDoubleOrNull() ?: 0.0

                            val dateMs = try {
                                java.time.LocalDate.parse(dateStr, DATE_FORMATTER)
                                    .atStartOfDay(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            } catch (e: Exception) {
                                System.currentTimeMillis()
                            }

                            paymentsToInsert.add(
                                Payment(
                                    id = paymentId,
                                    debtId = debtId,
                                    paymentDate = dateMs,
                                    amount = amount
                                )
                            )
                        }
                    }

                    line = reader.readLine()
                }
            }

            if (debtsToInsert.isEmpty() && paymentsToInsert.isEmpty()) {
                return false
            }

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val database = com.example.debt_tracker.data.local.AppDatabase.getInstance(context)
                val debtDao = database.debtDao()
                val paymentDao = database.paymentDao()

                // Clear existing
                paymentDao.deleteAllPayments()
                debtDao.deleteAllDebts()

                // Insert debts
                debtsToInsert.forEach { debtDao.insertDebt(it) }

                // Insert payments
                paymentsToInsert.forEach { paymentDao.insertPayment(it) }
            }

            return true

        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
