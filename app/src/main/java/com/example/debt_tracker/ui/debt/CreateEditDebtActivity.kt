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
import com.example.debt_tracker.ui.components.NoFilterAdapter
import com.example.debt_tracker.ui.components.WheelPickerDialog
import com.example.debt_tracker.util.overrideSlideTransition
import java.time.LocalDate
import java.time.YearMonth
import com.example.debt_tracker.util.CurrencyTextWatcher

class CreateEditDebtActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEditDebtBinding
    private lateinit var controller: DebtController

    private var debtId: Long = -1L
    private var isEditMode = false
    private var originalDebt: Debt? = null

    private var selectedInterestType = com.example.debt_tracker.data.model.InterestType.FIXED
    private var selectedPaymentType = com.example.debt_tracker.data.model.PaymentType.INSTALLMENT

    private var rawAmount: Double = 0.0
    private var rawCreditLimit: Double = 0.0
    private var rawPrincipal: Double = 0.0

    private fun setupCurrencyWatchers(currency: String) {
        binding.editAmount.addTextChangedListener(CurrencyTextWatcher(binding.editAmount, currency) { value ->
            rawAmount = value
            calculateAndSetPrincipal()
        })
        binding.editCreditLimit.addTextChangedListener(CurrencyTextWatcher(binding.editCreditLimit, currency) { value ->
            rawCreditLimit = value
        })
        binding.editPrincipal.addTextChangedListener(CurrencyTextWatcher(binding.editPrincipal, currency) { value ->
            rawPrincipal = value
        })
    }

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

        setupLoanTypeDropdowns()

        if (isEditMode) {
            binding.toolbar.title = getString(R.string.edit_debt)
            loadDebtDetails(repository)
        } else {
            binding.toolbar.title = getString(R.string.create_debt)
            updateDatePickersUI()
            setupCurrencyWatchers(com.example.debt_tracker.util.CurrencyUtils.getPreferredCurrency(this))
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
                calculateAndSetPrincipal()
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
                calculateAndSetPrincipal()
            }
        }

        // Empty block - replaced by setupCurrencyWatchers

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
                val activeCurrency = debt.currencyCode
                setupCurrencyWatchers(activeCurrency)

                // USD/GBP/CNY/EUR/TWD use cents, so scale raw digits correctly for watcher initialization
                val rawAmountVal = debt.monthlyAmount
                val isCentsCurrency = activeCurrency.uppercase() in listOf("USD", "GBP", "CNY", "EUR", "TWD")
                val amountText = if (isCentsCurrency) {
                    String.format(java.util.Locale.US, "%.0f", rawAmountVal * 100)
                } else {
                    String.format(java.util.Locale.US, "%.0f", rawAmountVal)
                }
                binding.editAmount.setText(amountText)

                if (debt.principal > 0) {
                    val rawPrincipalVal = debt.principal
                    val principalText = if (isCentsCurrency) {
                        String.format(java.util.Locale.US, "%.0f", rawPrincipalVal * 100)
                    } else {
                        String.format(java.util.Locale.US, "%.0f", rawPrincipalVal)
                    }
                    binding.editPrincipal.setText(principalText)
                }

                selectedDueDay = debt.dueDayOfMonth

                val startYM = try { YearMonth.parse(debt.startYearMonth) } catch (e: Exception) { YearMonth.now() }
                selectedStartMonth = startYM.monthValue
                selectedStartYear = startYM.year

                val endYM = try { YearMonth.parse(debt.endYearMonth) } catch (e: Exception) { YearMonth.now() }
                selectedEndMonth = endYM.monthValue
                selectedEndYear = endYM.year

                selectedInterestType = debt.interestType
                selectedPaymentType = debt.paymentType

                binding.editInterestRate.setText(String.format(java.util.Locale.US, "%.2f", debt.interestRate))
                val rawLimitVal = debt.creditLimit
                val limitText = if (isCentsCurrency) {
                    String.format(java.util.Locale.US, "%.0f", rawLimitVal * 100)
                } else {
                    String.format(java.util.Locale.US, "%.0f", rawLimitVal)
                }
                binding.editCreditLimit.setText(limitText)
                binding.editMinPaymentPercent.setText(String.format(java.util.Locale.US, "%.2f", debt.minimumPaymentPercent))

                binding.interestTypeDropdown.setText(getInterestTypeLabel(debt.interestType), false)
                binding.paymentTypeDropdown.setText(getPaymentTypeLabel(debt.paymentType), false)
                updateInterestTypeUI(debt.interestType)

                updateDatePickersUI()
                calculateAndSetPrincipal()
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

        // Validation using raw amounts from watches
        if (creditor.isEmpty() || contract.isEmpty() || rawAmount <= 0) {
            Toast.makeText(this, R.string.validation_required, Toast.LENGTH_SHORT).show()
            return
        }

        val amount = rawAmount
        val principal = rawPrincipal

        val startYM = YearMonth.of(selectedStartYear, selectedStartMonth)
        val endYM = YearMonth.of(selectedEndYear, selectedEndMonth)
        if (endYM.isBefore(startYM)) {
            Toast.makeText(this, R.string.validation_date_range, Toast.LENGTH_SHORT).show()
            return
        }

        val startYearMonthStr = String.format(java.util.Locale.US, "%04d-%02d", selectedStartYear, selectedStartMonth)
        val endYearMonthStr = String.format(java.util.Locale.US, "%04d-%02d", selectedEndYear, selectedEndMonth)

        val interestRate = binding.editInterestRate.text.toString().trim().toDoubleOrNull() ?: 0.0
        val creditLimit = rawCreditLimit
        val minPaymentPercent = binding.editMinPaymentPercent.text.toString().trim().toDoubleOrNull() ?: 0.0

        val remainingBalance = if (isEditMode) {
            originalDebt?.remainingBalance ?: principal
        } else {
            if (selectedInterestType == com.example.debt_tracker.data.model.InterestType.REDUCING) {
                if (principal > 0) principal else creditLimit
            } else {
                principal
            }
        }

        val debt = originalDebt?.copy(
            creditorName = creditor,
            contractNumber = contract,
            dueDayOfMonth = selectedDueDay,
            monthlyAmount = amount,
            startYearMonth = startYearMonthStr,
            endYearMonth = endYearMonthStr,
            principal = principal,
            interestRate = interestRate,
            interestType = selectedInterestType,
            paymentType = selectedPaymentType,
            creditLimit = creditLimit,
            remainingBalance = remainingBalance,
            minimumPaymentPercent = minPaymentPercent
        ) ?: Debt(
            creditorName = creditor,
            contractNumber = contract,
            dueDayOfMonth = selectedDueDay,
            monthlyAmount = amount,
            startYearMonth = startYearMonthStr,
            endYearMonth = endYearMonthStr,
            principal = principal,
            interestRate = interestRate,
            interestType = selectedInterestType,
            paymentType = selectedPaymentType,
            creditLimit = creditLimit,
            remainingBalance = remainingBalance,
            minimumPaymentPercent = minPaymentPercent,
            currencyCode = com.example.debt_tracker.util.CurrencyUtils.getPreferredCurrency(this)
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
        val interestRateStr = binding.editInterestRate.text.toString().trim()
        val creditLimitStr = binding.editCreditLimit.text.toString().trim()
        val minPaymentPercentStr = binding.editMinPaymentPercent.text.toString().trim()

        if (isEditMode) {
            val orig = originalDebt ?: return false
            val startYM = String.format(java.util.Locale.US, "%04d-%02d", selectedStartYear, selectedStartMonth)
            val endYM = String.format(java.util.Locale.US, "%04d-%02d", selectedEndYear, selectedEndMonth)
            val amount = rawAmount
            val principal = rawPrincipal
            val interestRate = interestRateStr.toDoubleOrNull() ?: 0.0
            val creditLimit = rawCreditLimit
            val minPaymentPercent = minPaymentPercentStr.toDoubleOrNull() ?: 0.0

            return orig.creditorName != creditor ||
                    orig.contractNumber != contract ||
                    orig.dueDayOfMonth != selectedDueDay ||
                    orig.monthlyAmount != amount ||
                    orig.startYearMonth != startYM ||
                    orig.endYearMonth != endYM ||
                    orig.principal != principal ||
                    orig.interestRate != interestRate ||
                    orig.interestType != selectedInterestType ||
                    orig.paymentType != selectedPaymentType ||
                    orig.creditLimit != creditLimit ||
                    orig.minimumPaymentPercent != minPaymentPercent
        } else {
            return creditor.isNotEmpty() ||
                    contract.isNotEmpty() ||
                    amountStr.isNotEmpty() ||
                    principalStr.isNotEmpty() ||
                    interestRateStr.isNotEmpty() ||
                    creditLimitStr.isNotEmpty() ||
                    minPaymentPercentStr.isNotEmpty() ||
                    selectedDueDay != 15 ||
                    selectedStartMonth != LocalDate.now().monthValue ||
                    selectedStartYear != LocalDate.now().year ||
                    selectedInterestType != com.example.debt_tracker.data.model.InterestType.FIXED ||
                    selectedPaymentType != com.example.debt_tracker.data.model.PaymentType.INSTALLMENT
        }
    }

    private fun calculateAndSetPrincipal() {
        val amount = rawAmount

        val startYM = YearMonth.of(selectedStartYear, selectedStartMonth)
        val endYM = YearMonth.of(selectedEndYear, selectedEndMonth)

        if (amount > 0 && !endYM.isBefore(startYM)) {
            val months = com.example.debt_tracker.util.DateUtils.monthsBetweenInclusive(
                String.format(java.util.Locale.US, "%04d-%02d", selectedStartYear, selectedStartMonth),
                String.format(java.util.Locale.US, "%04d-%02d", selectedEndYear, selectedEndMonth)
            )
            val calculatedPrincipal = amount * months
            val activeCurrency = com.example.debt_tracker.util.CurrencyUtils.getPreferredCurrency(this)
            val isCentsCurrency = activeCurrency.uppercase() in listOf("USD", "GBP", "CNY", "EUR", "TWD")
            val principalText = if (isCentsCurrency) {
                String.format(java.util.Locale.US, "%.0f", calculatedPrincipal * 100)
            } else {
                String.format(java.util.Locale.US, "%.0f", calculatedPrincipal)
            }
            binding.editPrincipal.setText(principalText)
        } else {
            binding.editPrincipal.setText("")
        }
    }

    private fun setupLoanTypeDropdowns() {
        val interestTypes = arrayOf(
            getString(R.string.fixed_rate),
            getString(R.string.reducing_balance),
            getString(R.string.islamic_finance)
        )
        val paymentTypes = arrayOf(
            getString(R.string.installment),
            getString(R.string.interest_only),
            getString(R.string.custom_payment)
        )

        val interestAdapter = NoFilterAdapter(this, R.layout.item_dropdown, interestTypes.toList())
        binding.interestTypeDropdown.setAdapter(interestAdapter)
        binding.interestTypeDropdown.setText(interestTypes[0], false)
        updateInterestTypeUI(selectedInterestType)

        binding.interestTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedInterestType = com.example.debt_tracker.data.model.InterestType.values()[position]
            updateInterestTypeUI(selectedInterestType)
        }

        val paymentAdapter = NoFilterAdapter(this, R.layout.item_dropdown, paymentTypes.toList())
        binding.paymentTypeDropdown.setAdapter(paymentAdapter)
        binding.paymentTypeDropdown.setText(paymentTypes[0], false)

        binding.paymentTypeDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedPaymentType = com.example.debt_tracker.data.model.PaymentType.values()[position]
        }
    }

    private fun updateInterestTypeUI(interestType: com.example.debt_tracker.data.model.InterestType) {
        when (interestType) {
            com.example.debt_tracker.data.model.InterestType.REDUCING -> {
                binding.creditLimitLayout.visibility = android.view.View.VISIBLE
                binding.minPaymentPercentLayout.visibility = android.view.View.VISIBLE
                binding.interestRateLayout.hint = getString(R.string.interest_rate)
            }
            com.example.debt_tracker.data.model.InterestType.ISLAMIC -> {
                binding.creditLimitLayout.visibility = android.view.View.GONE
                binding.minPaymentPercentLayout.visibility = android.view.View.GONE
                binding.interestRateLayout.hint = getString(R.string.profit_rate)
            }
            com.example.debt_tracker.data.model.InterestType.FIXED -> {
                binding.creditLimitLayout.visibility = android.view.View.GONE
                binding.minPaymentPercentLayout.visibility = android.view.View.GONE
                binding.interestRateLayout.hint = getString(R.string.interest_rate)
            }
        }
    }

    private fun getInterestTypeLabel(interestType: com.example.debt_tracker.data.model.InterestType): String {
        return when (interestType) {
            com.example.debt_tracker.data.model.InterestType.FIXED -> getString(R.string.fixed_rate)
            com.example.debt_tracker.data.model.InterestType.REDUCING -> getString(R.string.reducing_balance)
            com.example.debt_tracker.data.model.InterestType.ISLAMIC -> getString(R.string.islamic_finance)
        }
    }

    private fun getPaymentTypeLabel(paymentType: com.example.debt_tracker.data.model.PaymentType): String {
        return when (paymentType) {
            com.example.debt_tracker.data.model.PaymentType.INSTALLMENT -> getString(R.string.installment)
            com.example.debt_tracker.data.model.PaymentType.INTEREST_ONLY -> getString(R.string.interest_only)
            com.example.debt_tracker.data.model.PaymentType.CUSTOM -> getString(R.string.custom_payment)
        }
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }
}
