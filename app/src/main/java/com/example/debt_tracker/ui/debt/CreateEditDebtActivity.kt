package com.example.debt_tracker.ui.debt

import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.debt_tracker.R
import com.example.debt_tracker.controller.DebtController
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.databinding.ActivityCreateEditDebtBinding
import com.example.debt_tracker.ui.components.ConfirmDialog
import com.example.debt_tracker.ui.components.WheelPickerDialog
import com.example.debt_tracker.util.overrideSlideTransition
import java.time.LocalDate
import java.time.YearMonth

class CreateEditDebtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEditDebtBinding
    private lateinit var controller: DebtController

    private var debtId: Long = -1L
    private var isEditMode = false
    private var originalDebt: Debt? = null

    // Date picker variables
    private var selectedDueDay = 15
    private var selectedStartMonth = LocalDate.now().monthValue
    private var selectedStartYear = LocalDate.now().year
    private var selectedEndMonth = LocalDate.now().plusYears(1).monthValue
    private var selectedEndYear = LocalDate.now().plusYears(1).year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEditDebtBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup controller
        val repository = DebtRepository.getInstance(applicationContext)
        controller = DebtController(repository)

        // Check if edit mode
        debtId = intent.getLongExtra("DEBT_ID", -1L)
        isEditMode = debtId != -1L

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (isEditMode) {
            binding.toolbar.title = getString(R.string.edit_debt)
            loadDebtDetails(repository)
        } else {
            binding.toolbar.title = getString(R.string.create_debt)
            updateDatePickersUI()
        }

        // Click pickers
        binding.cardDueDay.setOnClickListener {
            WheelPickerDialog.showDayPicker(this, getString(R.string.pick_due_day), selectedDueDay) { day ->
                selectedDueDay = day
                binding.textDueDay.text = getString(R.string.due_day_label, day)
            }
        }

        binding.cardStartDate.setOnClickListener {
            WheelPickerDialog.showMonthYearPicker(
                this,
                getString(R.string.start_month_year),
                selectedStartMonth,
                selectedStartYear
            ) { month, year ->
                selectedStartMonth = month
                selectedStartYear = year
                binding.textStartDate.text = com.example.debt_tracker.util.DateUtils.formatMonthYear(YearMonth.of(year, month))
            }
        }

        binding.cardEndDate.setOnClickListener {
            WheelPickerDialog.showMonthYearPicker(
                this,
                getString(R.string.end_month_year),
                selectedEndMonth,
                selectedEndYear
            ) { month, year ->
                selectedEndMonth = month
                selectedEndYear = year
                binding.textEndDate.text = com.example.debt_tracker.util.DateUtils.formatMonthYear(YearMonth.of(year, month))
            }
        }

        // Action listeners
        binding.btnCancel.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnSave.setOnClickListener { saveDebt() }

        // Unsaved changes back press callback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) {
                    ConfirmDialog.show(
                        this@CreateEditDebtActivity,
                        getString(R.string.confirm_exit_title),
                        getString(R.string.confirm_exit_message)
                    ) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadDebtDetails(repository: DebtRepository) {
        repository.observeDebtById(debtId).observe(this) { debt ->
            if (debt == null) return@observe
            if (originalDebt == null) {
                originalDebt = debt
                binding.editCreditor.setText(debt.creditorName)
                binding.editContract.setText(debt.contractNumber)
                binding.editAmount.setText(String.format(java.util.Locale.US, "%.0f", debt.monthlyAmount))
                if (debt.principal > 0) {
                    binding.editPrincipal.setText(String.format(java.util.Locale.US, "%.0f", debt.principal))
                }

                selectedDueDay = debt.dueDayOfMonth

                val startYM = YearMonth.parse(debt.startYearMonth)
                selectedStartMonth = startYM.monthValue
                selectedStartYear = startYM.year

                val endYM = YearMonth.parse(debt.endYearMonth)
                selectedEndMonth = endYM.monthValue
                selectedEndYear = endYM.year

                updateDatePickersUI()
            }
        }
    }

    private fun updateDatePickersUI() {
        binding.textDueDay.text = getString(R.string.due_day_label, selectedDueDay)
        binding.textStartDate.text = com.example.debt_tracker.util.DateUtils.formatMonthYear(YearMonth.of(selectedStartYear, selectedStartMonth))
        binding.textEndDate.text = com.example.debt_tracker.util.DateUtils.formatMonthYear(YearMonth.of(selectedEndYear, selectedEndMonth))
    }

    private fun saveDebt() {
        val creditor = binding.editCreditor.text.toString().trim()
        val contract = binding.editContract.text.toString().trim()
        val amountStr = binding.editAmount.text.toString().trim()
        val principalStr = binding.editPrincipal.text.toString().trim()

        // Validation
        if (creditor.isEmpty() || contract.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, R.string.validation_required, Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            Toast.makeText(this, R.string.validation_positive_amount, Toast.LENGTH_SHORT).show()
            return
        }

        val principal = principalStr.toDoubleOrNull() ?: 0.0

        val startYM = YearMonth.of(selectedStartYear, selectedStartMonth)
        val endYM = YearMonth.of(selectedEndYear, selectedEndMonth)
        if (endYM.isBefore(startYM)) {
            Toast.makeText(this, R.string.validation_date_range, Toast.LENGTH_SHORT).show()
            return
        }

        val startYearMonthStr = String.format("%04d-%02d", selectedStartYear, selectedStartMonth)
        val endYearMonthStr = String.format("%04d-%02d", selectedEndYear, selectedEndMonth)

        val debt = originalDebt?.copy(
            creditorName = creditor,
            contractNumber = contract,
            dueDayOfMonth = selectedDueDay,
            monthlyAmount = amount,
            startYearMonth = startYearMonthStr,
            endYearMonth = endYearMonthStr,
            principal = principal
        ) ?: Debt(
            creditorName = creditor,
            contractNumber = contract,
            dueDayOfMonth = selectedDueDay,
            monthlyAmount = amount,
            startYearMonth = startYearMonthStr,
            endYearMonth = endYearMonthStr,
            principal = principal
        )

        if (isEditMode) {
            controller.updateDebt(debt) {
                Toast.makeText(this, R.string.debt_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            controller.createDebt(debt) {
                Toast.makeText(this, R.string.debt_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val creditor = binding.editCreditor.text.toString().trim()
        val contract = binding.editContract.text.toString().trim()
        val amountStr = binding.editAmount.text.toString().trim()
        val principalStr = binding.editPrincipal.text.toString().trim()

        if (isEditMode) {
            val orig = originalDebt ?: return false
            val startYM = String.format("%04d-%02d", selectedStartYear, selectedStartMonth)
            val endYM = String.format("%04d-%02d", selectedEndYear, selectedEndMonth)
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val principal = principalStr.toDoubleOrNull() ?: 0.0

            return orig.creditorName != creditor ||
                    orig.contractNumber != contract ||
                    orig.dueDayOfMonth != selectedDueDay ||
                    orig.monthlyAmount != amount ||
                    orig.startYearMonth != startYM ||
                    orig.endYearMonth != endYM ||
                    orig.principal != principal
        } else {
            return creditor.isNotEmpty() ||
                    contract.isNotEmpty() ||
                    amountStr.isNotEmpty() ||
                    principalStr.isNotEmpty() ||
                    selectedDueDay != 15 ||
                    selectedStartMonth != LocalDate.now().monthValue ||
                    selectedStartYear != LocalDate.now().year
        }
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }
}
