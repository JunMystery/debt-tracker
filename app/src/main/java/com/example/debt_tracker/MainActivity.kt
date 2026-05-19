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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.example.debt_tracker.ui.components.ConfirmDialog
import android.widget.Toast

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
        controller = DashboardController(applicationContext, repository)

        // Set up recycler view
        binding.recyclerUpcoming.layoutManager = LinearLayoutManager(this)
        adapter = UpcomingAdapter(
            onItemClick = { debtWithNextDue ->
                val intent = Intent(this, DebtDetailActivity::class.java).apply {
                    putExtra("DEBT_ID", debtWithNextDue.debt.id)
                }
                startActivity(intent)
                overrideSlideTransition(true)
            },
            onItemLongClick = { debtWithNextDue ->
                ConfirmDialog.show(
                    this@MainActivity,
                    getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_message)
                ) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repository.deleteDebt(debtWithNextDue.debt.id)
                        }
                        Toast.makeText(this@MainActivity, R.string.debt_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.recyclerUpcoming.adapter = adapter

        // Observe dashboard data
        controller.dashboardData.observe(this) { data ->
            binding.textTotalDue.text = CurrencyUtils.formatPreferred(this@MainActivity, data.totalDueThisMonth)
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
        binding.textConvertedDue.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        controller.refresh()
    }

    private class UpcomingAdapter(
        private val onItemClick: (DebtWithNextDue) -> Unit,
        private val onItemLongClick: (DebtWithNextDue) -> Unit
    ) : RecyclerView.Adapter<UpcomingAdapter.ViewHolder>() {

        private var list: List<DebtWithNextDue> = emptyList()

        fun submitList(newList: List<DebtWithNextDue>) {
            list = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDebtCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onItemClick, onItemLongClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
        }

        override fun getItemCount(): Int = list.size

        class ViewHolder(
            private val binding: ItemDebtCardBinding,
            private val onItemClick: (DebtWithNextDue) -> Unit,
            private val onItemLongClick: (DebtWithNextDue) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: DebtWithNextDue) {
                val context = itemView.context
                val debt = item.debt

                binding.textCreditorName.text = debt.creditorName
                binding.textContractNumber.text = debt.contractNumber
                binding.textAmount.text = CurrencyUtils.getFormattedAmount(context, debt.monthlyAmount, debt.currencyCode)

                // Compute status & due date text
                val nextDue = item.nextDueDate
                if (nextDue != null) {
                    binding.textDueDate.text = context.getString(R.string.debt_card_due, DateUtils.formatDay(context, nextDue))
                    
                    // Check if overdue
                    val today = LocalDate.now()
                    if (nextDue.isBefore(today)) {
                        binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_error))
                        binding.textStatusLabel.text = context.getString(R.string.status_overdue)
                        binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_error))
                    } else {
                        binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_primary))
                        binding.textStatusLabel.text = context.getString(R.string.status_upcoming)
                        binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_primary))
                    }
                } else {
                    binding.textDueDate.text = context.getString(R.string.status_no_due)
                    binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_secondary))
                    binding.textStatusLabel.text = context.getString(R.string.status_expired)
                    binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_secondary))
                }

                if (debt.isCompleted) {
                    binding.viewStatusIndicator.setBackgroundColor(context.getColor(R.color.dt_success))
                    binding.textStatusLabel.text = context.getString(R.string.completed_debts)
                    binding.textStatusLabel.setTextColor(context.getColor(R.color.dt_success))
                }

                itemView.setOnClickListener { onItemClick(item) }
                itemView.setOnLongClickListener {
                    onItemLongClick(item)
                    true
                }
                binding.btnMore.setOnClickListener {
                    onItemLongClick(item)
                }
            }
        }
    }
}
