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

    companion object {
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
