package com.example.debt_tracker.ui.debt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.debt_tracker.R
import com.example.debt_tracker.controller.DebtDetailController
import com.example.debt_tracker.data.model.Debt
import com.example.debt_tracker.data.model.Payment
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.databinding.ActivityDebtDetailBinding
import com.example.debt_tracker.databinding.DialogPaymentBottomSheetBinding
import com.example.debt_tracker.databinding.ItemPaymentBinding
import com.example.debt_tracker.ui.components.ConfirmDialog
import com.example.debt_tracker.ui.components.WheelPickerDialog
import com.example.debt_tracker.util.CurrencyUtils
import com.example.debt_tracker.util.DateUtils
import com.example.debt_tracker.util.LoanCalculator
import com.example.debt_tracker.util.overrideSlideTransition
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.time.LocalDate
import java.time.ZoneId

class DebtDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebtDetailBinding
    private lateinit var controller: DebtDetailController
    private lateinit var adapter: PaymentAdapter
    private var currentDebt: Debt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val debtId = intent.getLongExtra("DEBT_ID", -1L)
        if (debtId == -1L) {
            Toast.makeText(this, R.string.error_loading_data, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Set up controller
        val repository = DebtRepository.getInstance(applicationContext)
        controller = DebtDetailController(repository)

        // Set up recycler view
        binding.recyclerPayments.layoutManager = LinearLayoutManager(this)
        adapter = PaymentAdapter()
        binding.recyclerPayments.adapter = adapter

        // Observe Debt
        controller.observeDebt(debtId).observe(this) { debt ->
            if (debt == null) {
                // If debt was deleted, just finish
                finish()
                return@observe
            }
            currentDebt = debt
            bindDebtDetails(debt)
        }

        // Observe Payments
        controller.observePayments(debtId).observe(this) { payments ->
            if (payments.isNullOrEmpty()) {
                binding.textNoPayments.visibility = View.VISIBLE
                binding.recyclerPayments.visibility = View.GONE
            } else {
                binding.textNoPayments.visibility = View.GONE
                binding.recyclerPayments.visibility = View.VISIBLE
                adapter.submitList(payments, currentDebt?.currencyCode ?: "USD")
            }
        }

        // Button Click Listeners
        binding.btnMarkComplete.setOnClickListener {
            val debt = currentDebt ?: return@setOnClickListener
            ConfirmDialog.show(
                this,
                getString(R.string.confirm_complete_title),
                getString(R.string.confirm_complete_message)
            ) {
                controller.markCompleted(debt) {
                    Toast.makeText(this, R.string.debt_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnUpdatePayment.setOnClickListener {
            val debt = currentDebt ?: return@setOnClickListener
            showPaymentBottomSheet(debt)
        }
    }

    private fun bindDebtDetails(debt: Debt) {
        binding.textCreditor.text = debt.creditorName
        binding.textContract.text = "${getString(R.string.contract_number)}: ${debt.contractNumber}"

        val nextPayment = LoanCalculator.calculateNextPayment(debt)
        binding.textMonthlyAmount.text = CurrencyUtils.getFormattedAmount(this, nextPayment, debt.currencyCode)
        if (debt.interestType == com.example.debt_tracker.data.model.InterestType.REDUCING) {
            binding.labelMonthlyAmount.text = getString(R.string.suggested_payment)
        } else {
            binding.labelMonthlyAmount.text = getString(R.string.monthly_amount)
        }

        // Clipboard Copy contract action
        binding.imageCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Contract Number", debt.contractNumber)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, R.string.copied_contract, Toast.LENGTH_SHORT).show()
        }

        // Next due date calculations
        val nextDue = DateUtils.computeNextDueDate(debt)
        if (nextDue != null) {
            binding.textNextDue.text = DateUtils.formatDay(this, nextDue)
        } else {
            binding.textNextDue.text = getString(R.string.status_no_due)
        }

        // Remaining months
        val remaining = DateUtils.remainingMonths(debt.endYearMonth)
        binding.textRemainingMonths.text = getString(R.string.months_format, remaining)

        // Total paid
        binding.textTotalPaid.text = CurrencyUtils.getFormattedAmount(this, debt.totalPaid, debt.currencyCode)

        // Principal display if set
        if (debt.principal > 0) {
            binding.labelPrincipal.visibility = View.VISIBLE
            binding.textPrincipal.visibility = View.VISIBLE
            binding.textPrincipal.text = CurrencyUtils.getFormattedAmount(this, debt.principal, debt.currencyCode)
        } else {
            binding.labelPrincipal.visibility = View.GONE
            binding.textPrincipal.visibility = View.GONE
        }

        // Loan Type Display
        binding.labelLoanType.visibility = View.VISIBLE
        binding.textLoanType.visibility = View.VISIBLE
        val interestLabel = when (debt.interestType) {
            com.example.debt_tracker.data.model.InterestType.FIXED -> getString(R.string.fixed_rate)
            com.example.debt_tracker.data.model.InterestType.REDUCING -> getString(R.string.reducing_balance)
            com.example.debt_tracker.data.model.InterestType.ISLAMIC -> getString(R.string.islamic_finance)
        }
        val paymentLabel = when (debt.paymentType) {
            com.example.debt_tracker.data.model.PaymentType.INSTALLMENT -> getString(R.string.installment)
            com.example.debt_tracker.data.model.PaymentType.INTEREST_ONLY -> getString(R.string.interest_only)
            com.example.debt_tracker.data.model.PaymentType.CUSTOM -> getString(R.string.custom_payment)
        }
        binding.textLoanType.text = "$interestLabel - $paymentLabel"

        // Interest Rate Display
        if (debt.interestRate > 0) {
            binding.labelInterestRate.visibility = View.VISIBLE
            binding.textInterestRate.visibility = View.VISIBLE
            if (debt.interestType == com.example.debt_tracker.data.model.InterestType.ISLAMIC) {
                binding.labelInterestRate.text = getString(R.string.profit_rate)
            } else {
                binding.labelInterestRate.text = getString(R.string.interest_rate)
            }
            binding.textInterestRate.text = String.format(java.util.Locale.US, "%.2f %%", debt.interestRate)
        } else {
            binding.labelInterestRate.visibility = View.GONE
            binding.textInterestRate.visibility = View.GONE
        }

        // Credit Limit Display
        if (debt.interestType == com.example.debt_tracker.data.model.InterestType.REDUCING && debt.creditLimit > 0) {
            binding.labelCreditLimit.visibility = View.VISIBLE
            binding.textCreditLimit.visibility = View.VISIBLE
            binding.textCreditLimit.text = CurrencyUtils.getFormattedAmount(this, debt.creditLimit, debt.currencyCode)
        } else {
            binding.labelCreditLimit.visibility = View.GONE
            binding.textCreditLimit.visibility = View.GONE
        }

        // Remaining Balance Display
        if (debt.interestType == com.example.debt_tracker.data.model.InterestType.REDUCING || debt.remainingBalance > 0) {
            binding.labelRemainingBalance.visibility = View.VISIBLE
            binding.textRemainingBalance.visibility = View.VISIBLE
            binding.textRemainingBalance.text = CurrencyUtils.getFormattedAmount(this, debt.remainingBalance, debt.currencyCode)
        } else {
            binding.labelRemainingBalance.visibility = View.GONE
            binding.textRemainingBalance.visibility = View.GONE
        }

        // If completed, hide action bar
        if (debt.isCompleted) {
            binding.layoutBottomActions.visibility = View.GONE
        } else {
            binding.layoutBottomActions.visibility = View.VISIBLE
        }
    }

    private fun showPaymentBottomSheet(debt: Debt) {
        val dialog = BottomSheetDialog(this)
        val bsBinding = DialogPaymentBottomSheetBinding.inflate(layoutInflater)
        dialog.setContentView(bsBinding.root)

        // Prefill values
        val nextPayment = LoanCalculator.calculateNextPayment(debt)
        bsBinding.editAmount.setText(String.format(java.util.Locale.US, "%.0f", nextPayment))
        var selectedDate = LocalDate.now()
        bsBinding.textSelectedDate.text = DateUtils.formatDay(this, selectedDate)

        // Date Picker Action
        bsBinding.cardDatePicker.setOnClickListener {
            WheelPickerDialog.showDatePicker(
                this,
                getString(R.string.choose_date),
                selectedDate.dayOfMonth,
                selectedDate.monthValue,
                selectedDate.year
            ) { d, m, y ->
                selectedDate = LocalDate.of(y, m, d)
                bsBinding.textSelectedDate.text = DateUtils.formatDay(this, selectedDate)
            }
        }

        bsBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        bsBinding.btnSave.setOnClickListener {
            val amountStr = bsBinding.editAmount.text.toString().trim()
            if (amountStr.isEmpty()) {
                Toast.makeText(this, R.string.validation_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            if (amount <= 0) {
                Toast.makeText(this, R.string.validation_positive_amount, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val timestamp = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            controller.addPayment(debt, timestamp, amount) {
                Toast.makeText(this, R.string.payment_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_debt_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                val debt = currentDebt
                if (debt != null) {
                    val intent = android.content.Intent(this, CreateEditDebtActivity::class.java).apply {
                        putExtra("DEBT_ID", debt.id)
                    }
                    startActivity(intent)
                    overrideSlideTransition(true)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private class PaymentAdapter : RecyclerView.Adapter<PaymentAdapter.ViewHolder>() {

        private var list: List<Payment> = emptyList()
        private var currencyCode: String = "USD"

        fun submitList(newList: List<Payment>, currency: String) {
            list = newList
            currencyCode = currency
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position], currencyCode)
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(private val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(payment: Payment, currency: String) {
                binding.textPaymentDate.text = DateUtils.formatDay(itemView.context, payment.paymentDate)
                binding.textPaymentAmount.text = "+${CurrencyUtils.getFormattedAmount(itemView.context, payment.amount, currency)}"
            }
        }
    }
}
