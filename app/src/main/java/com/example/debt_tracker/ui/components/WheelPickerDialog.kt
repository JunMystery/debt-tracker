package com.example.debt_tracker.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.NumberPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.debt_tracker.databinding.DialogWheelPickerBinding
import java.time.YearMonth

object WheelPickerDialog {

    fun showMonthYearPicker(
        context: Context,
        title: String,
        initialMonth: Int = 1,
        initialYear: Int = 2026,
        onSelected: (month: Int, year: Int) -> Unit
    ) {
        val binding = DialogWheelPickerBinding.inflate(LayoutInflater.from(context))
        binding.textDialogTitle.text = title

        // Hide Day Picker
        binding.pickerDay.visibility = View.GONE

        // Set up Month Picker
        binding.pickerMonth.minValue = 1
        binding.pickerMonth.maxValue = 12
        binding.pickerMonth.value = initialMonth
        binding.pickerMonth.displayedValues = (1..12).map { String.format("%02d", it) }.toTypedArray()

        // Set up Year Picker
        binding.pickerYear.minValue = 2020
        binding.pickerYear.maxValue = 2050
        binding.pickerYear.value = initialYear

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            onSelected(binding.pickerMonth.value, binding.pickerYear.value)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showDatePicker(
        context: Context,
        title: String,
        initialDay: Int = 1,
        initialMonth: Int = 1,
        initialYear: Int = 2026,
        onSelected: (day: Int, month: Int, year: Int) -> Unit
    ) {
        val binding = DialogWheelPickerBinding.inflate(LayoutInflater.from(context))
        binding.textDialogTitle.text = title

        // Set up Month Picker
        binding.pickerMonth.minValue = 1
        binding.pickerMonth.maxValue = 12
        binding.pickerMonth.value = initialMonth
        binding.pickerMonth.displayedValues = (1..12).map { String.format("%02d", it) }.toTypedArray()

        // Set up Year Picker
        binding.pickerYear.minValue = 2020
        binding.pickerYear.maxValue = 2050
        binding.pickerYear.value = initialYear

        // Set up Day Picker
        fun updateMaxDay() {
            val year = binding.pickerYear.value
            val month = binding.pickerMonth.value
            val maxDay = YearMonth.of(year, month).lengthOfMonth()
            binding.pickerDay.maxValue = maxDay
        }

        binding.pickerDay.minValue = 1
        updateMaxDay()
        binding.pickerDay.value = initialDay.coerceIn(1, binding.pickerDay.maxValue)

        val listener = NumberPicker.OnValueChangeListener { _, _, _ -> updateMaxDay() }
        binding.pickerMonth.setOnValueChangedListener(listener)
        binding.pickerYear.setOnValueChangedListener(listener)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            onSelected(binding.pickerDay.value, binding.pickerMonth.value, binding.pickerYear.value)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showDayPicker(
        context: Context,
        title: String,
        initialDay: Int = 1,
        onSelected: (day: Int) -> Unit
    ) {
        val binding = DialogWheelPickerBinding.inflate(LayoutInflater.from(context))
        binding.textDialogTitle.text = title

        // Hide Month & Year Pickers
        binding.pickerMonth.visibility = View.GONE
        binding.pickerYear.visibility = View.GONE

        // Set up Day Picker
        binding.pickerDay.minValue = 1
        binding.pickerDay.maxValue = 31
        binding.pickerDay.value = initialDay

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            onSelected(binding.pickerDay.value)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showTimePicker(
        context: Context,
        title: String,
        minHour: Int,
        maxHour: Int,
        initialHour: Int,
        initialMinute: Int,
        onSelected: (hour: Int, minute: Int) -> Unit
    ) {
        val binding = DialogWheelPickerBinding.inflate(LayoutInflater.from(context))
        binding.textDialogTitle.text = title

        // Hide Year Picker, show Day Picker as Hour, and Month Picker as Minute
        binding.pickerYear.visibility = View.GONE
        binding.pickerDay.visibility = View.VISIBLE
        binding.pickerMonth.visibility = View.VISIBLE

        binding.pickerDay.minValue = minHour
        binding.pickerDay.maxValue = maxHour
        binding.pickerDay.value = initialHour.coerceIn(minHour, maxHour)
        binding.pickerDay.displayedValues = (minHour..maxHour).map { String.format("%02d", it) }.toTypedArray()

        binding.pickerMonth.minValue = 0
        binding.pickerMonth.maxValue = 59
        binding.pickerMonth.value = initialMinute.coerceIn(0, 59)
        binding.pickerMonth.displayedValues = (0..59).map { String.format("%02d", it) }.toTypedArray()

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        binding.btnCancel.setOnClickListener { dialog.dismiss() }
        binding.btnConfirm.setOnClickListener {
            onSelected(binding.pickerDay.value, binding.pickerMonth.value)
            dialog.dismiss()
        }

        dialog.show()
    }
}
