package com.example.debt_tracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.debt_tracker.R
import com.example.debt_tracker.data.local.AppDatabase
import com.example.debt_tracker.util.CurrencyUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DebtReminderReceiver : BroadcastReceiver() {
    private val TAG = "DebtReminderReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.debt_tracker.ACTION_DEBT_REMINDER") return

        Log.d(TAG, "Debt reminder alarm triggered!")
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                processReminders(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing reminders", e)
            } finally {
                // Schedule the next upcoming alarm
                DebtReminderScheduler.scheduleNext(context)
                pendingResult.finish()
            }
        }
    }

    private suspend fun processReminders(context: Context) {
        val prefs = context.getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_notifications_enabled", false)
        if (!isEnabled) return

        val daysBefore = prefs.getInt("pref_remind_days", 3)
        val frequency = prefs.getInt("pref_remind_frequency", 1)
        val startHour = prefs.getInt("pref_remind_start_hour", 9)
        val startMinute = prefs.getInt("pref_remind_start_minute", 0)
        val endHour = prefs.getInt("pref_remind_end_hour", 21)
        val endMinute = prefs.getInt("pref_remind_end_minute", 0)

        val reminderTimes = calculateReminderTimes(frequency, startHour, startMinute, endHour, endMinute)
        if (reminderTimes.isEmpty()) return

        val nowCal = Calendar.getInstance()
        val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)
        val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = nowCal.get(Calendar.MINUTE)

        // Only send push notifications if the current time matches one of our scheduled times
        val hasMatchingTime = reminderTimes.any { it.first == currentHour && it.second == currentMinute }
        if (!hasMatchingTime) {
            Log.d(TAG, "Current time ($currentHour:$currentMinute) is not in scheduled reminder times. Skipping notifications.")
            return
        }

        // Calculate start and end of current month in milliseconds
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonthMs = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val startOfNextMonthMs = calendar.timeInMillis

        val database = AppDatabase.getInstance(context)
        val activeDebts = database.debtDao().getAllDebtsOnce().filter { !it.isCompleted }
        val allPayments = database.paymentDao().getAllPaymentsOnce()

        val maxDaysInMonth = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        for (debt in activeDebts) {
            val targetDueDay = debt.dueDayOfMonth.coerceAtMost(maxDaysInMonth)
            val startReminderDay = (targetDueDay - daysBefore).coerceAtLeast(1)

            // Check if current day of month is in active reminder window for this debt
            if (currentDay in startReminderDay..targetDueDay) {
                // Verify if a payment has already been logged for this debt in the current month
                val paymentsThisMonth = allPayments.filter {
                    it.debtId == debt.id && it.paymentDate >= startOfMonthMs && it.paymentDate < startOfNextMonthMs
                }

                if (paymentsThisMonth.isEmpty()) {
                    // Send notification!
                    val title = context.getString(R.string.notification_title)
                    val formattedAmount = CurrencyUtils.format(debt.monthlyAmount)
                    val message = context.getString(
                        R.string.notification_message,
                        debt.creditorName,
                        formattedAmount,
                        debt.dueDayOfMonth.toString()
                    )
                    
                    Log.d(TAG, "Firing notification for debt ${debt.id}: $message")
                    NotificationHelper.showNotification(context, debt.id, title, message)
                } else {
                    Log.d(TAG, "Debt ${debt.id} already paid this month. Skipping notification.")
                }
            }
        }
    }

    private fun calculateReminderTimes(
        frequency: Int,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ): List<Pair<Int, Int>> {
        val startInMinutes = startHour * 60 + startMinute
        val endInMinutes = endHour * 60 + endMinute
        if (endInMinutes < startInMinutes) return emptyList()
        val freq = frequency.coerceAtLeast(1)
        if (freq == 1) {
            return listOf(Pair(startHour, startMinute))
        }
        
        val times = mutableListOf<Pair<Int, Int>>()
        val totalSpan = endInMinutes - startInMinutes
        
        for (i in 0 until freq) {
            val nextInMinutes = startInMinutes + (i * totalSpan) / (freq - 1)
            val h = (nextInMinutes / 60) % 24
            val m = nextInMinutes % 60
            times.add(Pair(h, m))
        }
        
        return times.distinct().sortedWith(compareBy({ it.first }, { it.second }))
    }
}
