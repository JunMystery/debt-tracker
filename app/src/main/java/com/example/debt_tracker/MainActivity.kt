package com.example.debt_tracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.debt_tracker.controller.DashboardController
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.databinding.ActivityMainBinding
import com.example.debt_tracker.databinding.ItemDebtCardBinding
import com.example.debt_tracker.ui.debt.CreateEditDebtActivity
import com.example.debt_tracker.ui.debt.DebtDetailActivity
import com.example.debt_tracker.ui.debt.DebtListActivity
import com.example.debt_tracker.ui.model.DebtWithNextDue
import com.example.debt_tracker.ui.settings.SettingsActivity
import com.example.debt_tracker.util.CurrencyUtils
import com.example.debt_tracker.util.DateUtils
import com.example.debt_tracker.util.overrideSlideTransition
import com.example.debt_tracker.util.ExchangeRateService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: DashboardController
    private lateinit var adapter: UpcomingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up controller
        val repository = DebtRepository.getInstance(applicationContext)
        controller = DashboardController(repository)

        // Set up recycler view
        binding.recyclerUpcoming.layoutManager = LinearLayoutManager(this)
        adapter = UpcomingAdapter { debtWithNextDue ->
            val intent = Intent(this, DebtDetailActivity::class.java).apply {
                putExtra("DEBT_ID", debtWithNextDue.debt.id)
            }
            startActivity(intent)
            overrideSlideTransition(true)
        }
        binding.recyclerUpcoming.adapter = adapter

        // Pre-fetch latest exchange rates in the background for smooth auto-conversion
        lifecycleScope.launch {
            try {
                ExchangeRateService.loadRates()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Observe dashboard data
        controller.dashboardData.observe(this) { data ->
            binding.textTotalDue.text = CurrencyUtils.format(data.totalDueThisMonth)
            binding.textProgress.text = getString(R.string.paid_count, data.paidCount, data.totalActiveCount)
            updateExchangeRate(data.totalDueThisMonth)

            if (data.upcomingDebts.isEmpty()) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.recyclerUpcoming.visibility = View.GONE
            } else {
                binding.textEmpty.visibility = View.GONE
                binding.recyclerUpcoming.visibility = View.VISIBLE
                adapter.submitList(data.upcomingDebts)
            }
        }

        // Navigation listeners
        binding.btnViewList.setOnClickListener {
            val intent = Intent(this, DebtListActivity::class.java)
            startActivity(intent)
            overrideSlideTransition(true)
        }

        binding.fabAddDebt.setOnClickListener {
            val intent = Intent(this, CreateEditDebtActivity::class.java)
            startActivity(intent)
            overrideSlideTransition(true)
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overrideSlideTransition(true)
        }
    }

    private fun updateExchangeRate(totalDue: Double) {
        if (totalDue <= 0) {
            binding.textConvertedDue.visibility = View.GONE
            return
        }

        binding.textConvertedDue.visibility = View.VISIBLE
        binding.textConvertedDue.text = getString(R.string.updating_exchange_rate)

        val activeCurrency = com.example.debt_tracker.util.CurrencyUtils.getCurrencyCode()
        val targetCurrency = if (activeCurrency == "USD") "VND" else "USD"

        lifecycleScope.launch {
            try {
                val fetchCurrency = if (activeCurrency == "USD") targetCurrency else activeCurrency
                val rate = ExchangeRateService.getUsdToTargetRate(fetchCurrency)
                
                if (rate != null && rate > 0) {
                    val convertedAmount: Double
                    val formattedConverted: String
                    val rateString: String

                    if (activeCurrency == "USD") {
                        // Native is USD, target is VND
                        convertedAmount = totalDue * rate
                        
                        val vndFormatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("vi", "VN"))
                        vndFormatter.maximumFractionDigits = 0
                        formattedConverted = vndFormatter.format(convertedAmount)
                        
                        val usdFormatter = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
                        rateString = "${usdFormatter.format(rate)} VND/USD"
                    } else {
                        // Native is other currency, target is USD
                        convertedAmount = totalDue / rate
                        
                        val usdFormatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US)
                        formattedConverted = usdFormatter.format(convertedAmount)
                        
                        val activeLocale = when (activeCurrency) {
                            "GBP" -> java.util.Locale.UK
                            "VND" -> java.util.Locale("vi", "VN")
                            "CNY" -> java.util.Locale.CHINA
                            "JPY" -> java.util.Locale.JAPAN
                            "KRW" -> java.util.Locale.KOREA
                            else -> java.util.Locale.getDefault()
                        }
                        val rateFormatter = java.text.NumberFormat.getNumberInstance(activeLocale)
                        val symbol = when (activeCurrency) {
                            "GBP" -> "£"
                            "VND" -> "₫"
                            "CNY" -> "¥"
                            "JPY" -> "¥"
                            "KRW" -> "₩"
                            else -> activeCurrency
                        }
                        rateString = "${rateFormatter.format(rate)} $symbol/USD"
                    }

                    binding.textConvertedDue.text = getString(
                        R.string.exchange_rate_format,
                        formattedConverted,
                        rateString
                    )
                } else {
                    binding.textConvertedDue.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.textConvertedDue.visibility = View.GONE
            }
        }
    }

    private class UpcomingAdapter(
        private val onItemClick: (DebtWithNextDue) -> Unit
    ) : RecyclerView.Adapter<UpcomingAdapter.ViewHolder>() {

        private var list: List<DebtWithNextDue> = emptyList()

        fun submitList(newList: List<DebtWithNextDue>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDebtCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onItemClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(
            private val binding: ItemDebtCardBinding,
            private val onItemClick: (DebtWithNextDue) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: DebtWithNextDue) {
                val context = itemView.context
                val debt = item.debt

                binding.textCreditorName.text = debt.creditorName
                binding.textContractNumber.text = debt.contractNumber
                binding.textAmount.text = CurrencyUtils.getFormattedAmount(debt.monthlyAmount, debt.currencyCode)

                // Compute status & due date text
                val nextDue = item.nextDueDate
                if (nextDue != null) {
                    binding.textDueDate.text = context.getString(R.string.debt_card_due, DateUtils.formatDay(nextDue))
                    
                    // Check if overdue
                    val today = LocalDate.now()
                    if (nextDue.isBefore(today)) {
                        binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_error))
                        binding.textStatusLabel.text = context.getString(R.string.validation_due_day) // placeholder or just overdue style
                        binding.textStatusLabel.text = "Trễ hạn"
                        binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_error))
                    } else {
                        binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_primary))
                        binding.textStatusLabel.text = "Sắp đến"
                        binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_primary))
                    }
                } else {
                    binding.textDueDate.text = "Không có kỳ hạn"
                    binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_secondary))
                    binding.textStatusLabel.text = "Hết hạn"
                    binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_secondary))
                }

                if (debt.isCompleted) {
                    binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_success))
                    binding.textStatusLabel.text = context.getString(R.string.completed_debts)
                    binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_success))
                }

                itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }
}
