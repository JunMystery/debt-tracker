package com.example.debt_tracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.debt_tracker.data.local.AppDatabase
import com.example.debt_tracker.data.model.Debt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

object DebtReminderScheduler {
    private const val TAG = "DebtReminderScheduler"
    private const val REQUEST_CODE = 9999

    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            scheduleNext(context)
        }
    }

    suspend fun scheduleNext(context: Context) {
        val prefs = context.getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_notifications_enabled", false)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Intent for the receiver
        val intent = Intent(context, DebtReminderReceiver::class.java).apply {
            action = "com.example.debt_tracker.ACTION_DEBT_REMINDER"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Always cancel existing alarm first
        alarmManager.cancel(pendingIntent)

        if (!isEnabled) {
            Log.d(TAG, "Notifications are disabled. Canceled existing alarms.")
            return
        }

        val database = AppDatabase.getInstance(context)
        val activeDebts = database.debtDao().getAllDebtsOnce().filter { !it.isCompleted }
        if (activeDebts.isEmpty()) {
            Log.d(TAG, "No active debts found. Alarm canceled.")
            return
        }

        val daysBefore = prefs.getInt("pref_remind_days", 3)
        val frequency = prefs.getInt("pref_remind_frequency", 1)
        val startHour = prefs.getInt("pref_remind_start_hour", 9)
        val startMinute = prefs.getInt("pref_remind_start_minute", 0)
        val endHour = prefs.getInt("pref_remind_end_hour", 21)
        val endMinute = prefs.getInt("pref_remind_end_minute", 0)

        val reminderTimes = calculateReminderTimes(frequency, startHour, startMinute, endHour, endMinute)
        if (reminderTimes.isEmpty()) {
            Log.d(TAG, "No valid reminder hours configured.")
            return
        }

        val now = System.currentTimeMillis()
        var earliestTriggerTime = Long.MAX_VALUE

        // For each active debt, find its next upcoming reminder timestamp after 'now'
        for (debt in activeDebts) {
            val nextReminder = getNextReminderForDebt(debt, daysBefore, reminderTimes, now)
            if (nextReminder != null && nextReminder < earliestTriggerTime) {
                earliestTriggerTime = nextReminder
            }
        }

        if (earliestTriggerTime != Long.MAX_VALUE) {
            Log.d(TAG, "Scheduling next alarm at: ${Calendar.getInstance().apply { timeInMillis = earliestTriggerTime }.time}")
            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canScheduleExact) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        earliestTriggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        earliestTriggerTime,
                        pendingIntent
                    )
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        earliestTriggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        earliestTriggerTime,
                        pendingIntent
                    )
                }
            }
        } else {
            Log.d(TAG, "No upcoming reminders found to schedule.")
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

    private fun getNextReminderForDebt(
        debt: Debt,
        daysBefore: Int,
        reminderTimes: List<Pair<Int, Int>>,
        nowMs: Long
    ): Long? {
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val currentYear = nowCal.get(Calendar.YEAR)
        val currentMonth = nowCal.get(Calendar.MONTH) // 0-indexed

        // Try evaluating reminder times in: current month, next month
        for (monthOffset in 0..1) {
            val evalCal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.MONTH, monthOffset)
            }
            val year = evalCal.get(Calendar.YEAR)
            val month = evalCal.get(Calendar.MONTH)

            // Get max days in this evaluation month to prevent overflow
            val maxDaysInMonth = evalCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val targetDueDay = debt.dueDayOfMonth.coerceAtMost(maxDaysInMonth)

            // The reminder window is [targetDueDay - daysBefore, targetDueDay]
            val startReminderDay = (targetDueDay - daysBefore).coerceAtLeast(1)

            // Check every day in the reminder window
            for (day in startReminderDay..targetDueDay) {
                for (time in reminderTimes) {
                    val reminderCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, day)
                        set(Calendar.HOUR_OF_DAY, time.first)
                        set(Calendar.MINUTE, time.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    if (reminderCal.timeInMillis > nowMs) {
                        return reminderCal.timeInMillis
                    }
                }
            }
        }
        return null
    }
}
