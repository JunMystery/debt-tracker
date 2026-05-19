package com.example.debt_tracker.ui.settings

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.example.debt_tracker.R
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.databinding.ActivitySettingsBinding
import com.example.debt_tracker.notification.DebtReminderScheduler
import com.example.debt_tracker.ui.components.NoFilterAdapter
import com.example.debt_tracker.ui.components.WheelPickerDialog
import com.example.debt_tracker.util.overrideSlideTransition
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val importCsvLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            confirmImport(uri)
        }
    }

    private class LanguageOption(
        val displayName: String,
        val tag: String
    ) {
        override fun toString(): String = displayName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Set up options
        setupLanguageSection()
        setupCurrencySection()
        setupDateFormatSection()
        setupNotificationSection()
        setupBackupSection()
        setupImportSection()
        setupCollapsibleSections()
    }

    private fun setupLanguageSection() {
        val languages = listOf(
            LanguageOption("🇺🇸 English (US)", "en-US"),
            LanguageOption("🇬🇧 English (UK)", "en-GB"),
            LanguageOption("🇻🇳 Tiếng Việt", "vi-VN"),
            LanguageOption("🇨🇳 简体中文", "zh-CN"),
            LanguageOption("🇨🇳 繁體中文", "zh-TW"),
            LanguageOption("🇯🇵 日本語", "ja-JP"),
            LanguageOption("🇰🇷 한국어", "ko-KR")
        )

        val adapter = NoFilterAdapter(this, R.layout.item_dropdown, languages)
        binding.autoCompleteLanguage.setAdapter(adapter)

        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) {
            currentLocales.get(0)?.toLanguageTag() ?: "en-US"
        } else {
            java.util.Locale.getDefault().toLanguageTag()
        }

        val activeIndex = when {
            currentTag.startsWith("en-GB", ignoreCase = true) -> 1
            currentTag.startsWith("vi", ignoreCase = true) -> 2
            currentTag.startsWith("zh-TW", ignoreCase = true) || currentTag.startsWith("zh-HK", ignoreCase = true) || currentTag.contains("Hant", ignoreCase = true) -> 4
            currentTag.startsWith("zh", ignoreCase = true) -> 3
            currentTag.startsWith("ja", ignoreCase = true) -> 5
            currentTag.startsWith("ko", ignoreCase = true) -> 6
            currentTag.startsWith("en", ignoreCase = true) -> 0
            else -> 0
        }

        binding.autoCompleteLanguage.setText(languages[activeIndex].displayName, false)

        binding.autoCompleteLanguage.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = languages[position]
            val currentLang = if (!currentLocales.isEmpty) {
                currentLocales.get(0)?.toLanguageTag()
            } else {
                java.util.Locale.getDefault().toLanguageTag()
            }

            if (selectedOption.tag != currentLang) {
                val localeList = LocaleListCompat.forLanguageTags(selectedOption.tag)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        }
    }

    private class CurrencyOption(
        val displayName: String,
        val code: String
    ) {
        override fun toString(): String = displayName
    }

    private fun setupCurrencySection() {
        val currencies = listOf(
            CurrencyOption("🇺🇸 USD - US Dollar", "USD"),
            CurrencyOption("🇻🇳 VND - Vietnamese Dong", "VND"),
            CurrencyOption("🇬🇧 GBP - British Pound", "GBP"),
            CurrencyOption("🇨🇳 CNY - Chinese Yuan", "CNY"),
            CurrencyOption("🇯🇵 JPY - Japanese Yen", "JPY"),
            CurrencyOption("🇰🇷 KRW - Korean Won", "KRW"),
            CurrencyOption("🇨🇳 TWD - New Taiwan Dollar", "TWD")
        )

        val adapter = NoFilterAdapter(this, R.layout.item_dropdown, currencies)
        binding.autoCompleteCurrency.setAdapter(adapter)

        val currentPreferred = com.example.debt_tracker.util.CurrencyUtils.getPreferredCurrency(this)
        val activeIndex = currencies.indexOfFirst { it.code.uppercase() == currentPreferred.uppercase() }.coerceAtLeast(0)

        binding.autoCompleteCurrency.setText(currencies[activeIndex].displayName, false)

        binding.autoCompleteCurrency.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = currencies[position]
            com.example.debt_tracker.util.CurrencyUtils.setPreferredCurrency(this, selectedOption.code)
        }
    }

    private class DateFormatOption(
        val displayName: String,
        val pattern: String
    ) {
        override fun toString(): String = displayName
    }

    private fun setupDateFormatSection() {
        val formats = listOf(
            DateFormatOption("DD/MM/YYYY (e.g. 19/05/2026)", "dd/MM/yyyy"),
            DateFormatOption("MM/DD/YYYY (e.g. 05/19/2026)", "MM/dd/yyyy"),
            DateFormatOption("YYYY-MM-DD (e.g. 2026-05-19)", "yyyy-MM-dd")
        )

        val adapter = NoFilterAdapter(this, R.layout.item_dropdown, formats)
        binding.autoCompleteDateFormat.setAdapter(adapter)

        val currentPreferred = com.example.debt_tracker.util.DateUtils.getPreferredDateFormat(this)
        val activeIndex = formats.indexOfFirst { it.pattern == currentPreferred }.coerceAtLeast(0)

        binding.autoCompleteDateFormat.setText(formats[activeIndex].displayName, false)

        binding.autoCompleteDateFormat.setOnItemClickListener { _, _, position, _ ->
            val selectedOption = formats[position]
            com.example.debt_tracker.util.DateUtils.setPreferredDateFormat(this, selectedOption.pattern)
            android.widget.Toast.makeText(this, getString(R.string.debt_saved), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupNotificationSection() {
        val prefs = getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_notifications_enabled", false)
        val daysBefore = prefs.getInt("pref_remind_days", 3)
        val frequency = prefs.getInt("pref_remind_frequency", 1)
        val startHour = prefs.getInt("pref_remind_start_hour", 9)
        val startMinute = prefs.getInt("pref_remind_start_minute", 0)
        val endHour = prefs.getInt("pref_remind_end_hour", 21)
        val endMinute = prefs.getInt("pref_remind_end_minute", 0)

        // 1. Switch setting
        binding.switchNotifications.isChecked = isEnabled
        binding.layoutNotificationParams.visibility = if (isEnabled) View.VISIBLE else View.GONE

        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            binding.layoutNotificationParams.visibility = if (checked) View.VISIBLE else View.GONE
            prefs.edit().putBoolean("pref_notifications_enabled", checked).apply()
            DebtReminderScheduler.rescheduleAll(this)
        }

        // 2. Days setting
        binding.editRemindDays.setText(daysBefore.toString())
        binding.editRemindDays.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val days = s?.toString()?.toIntOrNull() ?: 3
                prefs.edit().putInt("pref_remind_days", days).apply()
                DebtReminderScheduler.rescheduleAll(this@SettingsActivity)
            }
        })

        // 3. Frequency dropdown
        val freqList = listOf(1, 2, 3, 4)
        val freqLabels = freqList.map {
            if (it == 1) getString(R.string.frequency_format_single, 1)
            else getString(R.string.frequency_format_plural, it)
        }
        val freqAdapter = NoFilterAdapter(this, R.layout.item_dropdown, freqLabels)
        binding.autoCompleteFrequency.setAdapter(freqAdapter)
        val activeFreqIndex = freqList.indexOf(frequency).coerceAtLeast(0)
        binding.autoCompleteFrequency.setText(freqLabels[activeFreqIndex], false)

        binding.autoCompleteFrequency.setOnItemClickListener { _, _, position, _ ->
            val freq = freqList[position]
            prefs.edit().putInt("pref_remind_frequency", freq).apply()
            updatePreviewAndAlarms()
        }

        // 4. Start Hour/Minute Selector
        var currentStartHour = startHour
        var currentStartMinute = startMinute
        binding.textStartHour.text = String.format("%02d:%02d", currentStartHour, currentStartMinute)
        binding.cardStartHour.setOnClickListener {
            WheelPickerDialog.showTimePicker(
                this,
                getString(R.string.remind_start_hour),
                0,
                23,
                currentStartHour,
                currentStartMinute
            ) { hour, minute ->
                currentStartHour = hour
                currentStartMinute = minute
                binding.textStartHour.text = String.format("%02d:%02d", hour, minute)
                prefs.edit()
                    .putInt("pref_remind_start_hour", hour)
                    .putInt("pref_remind_start_minute", minute)
                    .apply()
                updatePreviewAndAlarms()
            }
        }

        // 6. End Hour/Minute Selector
        var currentEndHour = endHour
        var currentEndMinute = endMinute
        binding.textEndHour.text = String.format("%02d:%02d", currentEndHour, currentEndMinute)
        binding.cardEndHour.setOnClickListener {
            WheelPickerDialog.showTimePicker(
                this,
                getString(R.string.remind_end_hour),
                0,
                23,
                currentEndHour,
                currentEndMinute
            ) { hour, minute ->
                currentEndHour = hour
                currentEndMinute = minute
                binding.textEndHour.text = String.format("%02d:%02d", hour, minute)
                prefs.edit()
                    .putInt("pref_remind_end_hour", hour)
                    .putInt("pref_remind_end_minute", minute)
                    .apply()
                updatePreviewAndAlarms()
            }
        }

        // 7. Preview info click
        binding.ibPreviewInfo.setOnClickListener {
            showTimetableDialog()
        }

        // Run initial preview setup
        updatePreviewText()
    }

    private fun updatePreviewAndAlarms() {
        updatePreviewText()
        DebtReminderScheduler.rescheduleAll(this)
    }

    private fun getSelectedParams(): ReminderParams {
        val prefs = getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val freq = prefs.getInt("pref_remind_frequency", 1)
        val startH = prefs.getInt("pref_remind_start_hour", 9)
        val startM = prefs.getInt("pref_remind_start_minute", 0)
        val endH = prefs.getInt("pref_remind_end_hour", 21)
        val endM = prefs.getInt("pref_remind_end_minute", 0)
        return ReminderParams(freq, startH, startM, endH, endM)
    }

    private fun calculateReminderTimes(params: ReminderParams): List<Pair<Int, Int>> {
        val startInMinutes = params.startHour * 60 + params.startMinute
        val endInMinutes = params.endHour * 60 + params.endMinute
        if (endInMinutes < startInMinutes) return emptyList()
        val freq = params.frequency.coerceAtLeast(1)
        if (freq == 1) {
            return listOf(Pair(params.startHour, params.startMinute))
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

    private fun updatePreviewText() {
        val params = getSelectedParams()
        val startInMinutes = params.startHour * 60 + params.startMinute
        val endInMinutes = params.endHour * 60 + params.endMinute
        if (endInMinutes < startInMinutes) {
            binding.textPreviewHours.text = getString(R.string.validation_start_end_hour)
            binding.textPreviewHours.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
            return
        }

        val times = calculateReminderTimes(params)
        val formattedTimes = times.joinToString(", ") { String.format("%02d:%02d", it.first, it.second) }
        binding.textPreviewHours.text = getString(R.string.notification_preview_hours, formattedTimes)
        binding.textPreviewHours.setTextColor(resources.getColor(R.color.dt_secondary, theme))
    }

    private fun showTimetableDialog() {
        val params = getSelectedParams()
        val titleStr = getString(R.string.notification_preview_dialog_title)
        
        val startInMinutes = params.startHour * 60 + params.startMinute
        val endInMinutes = params.endHour * 60 + params.endMinute
        val messageStr = if (endInMinutes < startInMinutes) {
            getString(R.string.validation_start_end_hour)
        } else {
            val times = calculateReminderTimes(params)
            if (times.isEmpty()) {
                "No valid reminder times scheduled."
            } else {
                val messageText = getString(R.string.notification_preview_dialog_message)
                val bulletList = times.joinToString("\n") { String.format("  • %02d:%02d", it.first, it.second) }
                "$messageText\n\n$bulletList"
            }
        }

        val dialogBinding = com.example.debt_tracker.databinding.DialogPreviewInfoBinding.inflate(layoutInflater)
        dialogBinding.textDialogTitle.text = titleStr
        dialogBinding.textDialogMessage.text = messageStr

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnOk.setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private data class ReminderParams(
        val frequency: Int,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int
    )

    private fun setupBackupSection() {
        val prefs = getSharedPreferences("debt_tracker_settings", Context.MODE_PRIVATE)
        val isBackupEnabled = prefs.getBoolean("pref_auto_backup_enabled", false)

        binding.switchAutoBackup.isChecked = isBackupEnabled
        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("pref_auto_backup_enabled", checked).apply()
            if (checked) {
                val repo = DebtRepository.getInstance(this)
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.forceBackupNow()
                }
            }
        }
    }



    private fun setupImportSection() {
        binding.btnImportCsv.setOnClickListener {
            importCsvLauncher.launch("*/*")
        }
    }

    private fun confirmImport(uri: android.net.Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_confirm_title)
            .setMessage(R.string.import_confirm_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.import_confirm_button) { _, _ ->
                executeImport(uri)
            }
            .show()
    }

    private fun executeImport(uri: android.net.Uri) {
        lifecycleScope.launch {
            val repository = DebtRepository.getInstance(applicationContext)
            val success = repository.importBackup(uri)
            if (success) {
                android.widget.Toast.makeText(
                    this@SettingsActivity,
                    R.string.import_success,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                updatePreviewAndAlarms()
            } else {
                android.widget.Toast.makeText(
                    this@SettingsActivity,
                    R.string.import_failure,
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }

    private fun setupCollapsibleSections() {
        setupCollapsibleSection(binding.headerGeneral, binding.containerGeneral, binding.ivChevronGeneral)
        setupCollapsibleSection(binding.headerNotifications, binding.containerNotifications, binding.ivChevronNotifications)
        setupCollapsibleSection(binding.headerBackup, binding.containerBackup, binding.ivChevronBackup)
        setupCollapsibleSection(binding.headerImport, binding.containerImport, binding.ivChevronImport)
    }

    private fun setupCollapsibleSection(
        headerView: View,
        containerView: View,
        chevronView: android.widget.ImageView
    ) {
        chevronView.rotation = 0f
        headerView.setOnClickListener {
            if (containerView.visibility == View.VISIBLE) {
                containerView.visibility = View.GONE
                chevronView.animate().rotation(0f).setDuration(200).start()
            } else {
                containerView.visibility = View.VISIBLE
                chevronView.animate().rotation(180f).setDuration(200).start()
            }
        }
    }
}
