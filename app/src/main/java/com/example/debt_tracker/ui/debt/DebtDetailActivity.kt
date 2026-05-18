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
                adapter.submitList(payments)
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
        binding.textMonthlyAmount.text = CurrencyUtils.format(debt.monthlyAmount)

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
            binding.textNextDue.text = DateUtils.formatDay(nextDue)
        } else {
            binding.textNextDue.text = "Không có kỳ hạn"
        }

        // Remaining months
        val remaining = DateUtils.remainingMonths(debt.endYearMonth)
        binding.textRemainingMonths.text = getString(R.string.remaining_months, remaining).replace("Số tháng còn lại: ", "")

        // Total paid
        binding.textTotalPaid.text = CurrencyUtils.format(debt.totalPaid)

        // Principal display if set
        if (debt.principal > 0) {
            binding.labelPrincipal.visibility = View.VISIBLE
            binding.textPrincipal.visibility = View.VISIBLE
            binding.textPrincipal.text = CurrencyUtils.format(debt.principal)
        } else {
            binding.labelPrincipal.visibility = View.GONE
            binding.textPrincipal.visibility = View.GONE
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
        bsBinding.editAmount.setText(String.format(java.util.Locale.US, "%.0f", debt.monthlyAmount))
        var selectedDate = LocalDate.now()
        bsBinding.textSelectedDate.text = DateUtils.formatDay(selectedDate)

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
                bsBinding.textSelectedDate.text = DateUtils.formatDay(selectedDate)
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

    private class PaymentAdapter : RecyclerView.Adapter<PaymentAdapter.ViewHolder>() {

        private var list: List<Payment> = emptyList()

        fun submitList(newList: List<Payment>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPaymentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(private val binding: ItemPaymentBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(payment: Payment) {
                binding.textPaymentDate.text = DateUtils.formatDay(payment.paymentDate)
                binding.textPaymentAmount.text = "+${CurrencyUtils.format(payment.amount)}"
            }
        }
    }
}
