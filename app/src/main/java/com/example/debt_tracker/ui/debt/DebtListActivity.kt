package com.example.debt_tracker.ui.debt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.debt_tracker.R
import com.example.debt_tracker.controller.DebtController
import com.example.debt_tracker.data.repository.DebtRepository
import com.example.debt_tracker.databinding.ActivityDebtListBinding
import com.example.debt_tracker.databinding.ItemDebtCardBinding
import com.example.debt_tracker.ui.components.ConfirmDialog
import com.example.debt_tracker.ui.model.DebtWithNextDue
import com.example.debt_tracker.util.CurrencyUtils
import com.example.debt_tracker.util.DateUtils
import com.example.debt_tracker.util.overrideSlideTransition
import com.google.android.material.tabs.TabLayout
import java.time.LocalDate

class DebtListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDebtListBinding
    private lateinit var controller: DebtController
    private lateinit var adapter: DebtListAdapter

    private var activeList: List<DebtWithNextDue> = emptyList()
    private var completedList: List<DebtWithNextDue> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebtListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Set up controller
        val repository = DebtRepository.getInstance(applicationContext)
        controller = DebtController(repository)

        // Set up RecyclerView
        binding.recyclerDebts.layoutManager = LinearLayoutManager(this)
        adapter = DebtListAdapter(
            onItemClick = { debtWithNextDue ->
                val intent = Intent(this, DebtDetailActivity::class.java).apply {
                    putExtra("DEBT_ID", debtWithNextDue.debt.id)
                }
                startActivity(intent)
                overrideSlideTransition(true)
            },
            onItemLongClick = { debtWithNextDue ->
                ConfirmDialog.show(
                    this@DebtListActivity,
                    getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_message)
                ) {
                    controller.deleteDebt(debtWithNextDue.debt.id) {
                        Toast.makeText(this@DebtListActivity, R.string.debt_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        binding.recyclerDebts.adapter = adapter

        // Set up Swipe-to-Delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Disable swipe to delete on completed debts tab
                if (binding.tabLayout.selectedTabPosition == 1) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.getItemAt(position)

                ConfirmDialog.show(
                    this@DebtListActivity,
                    getString(R.string.confirm_delete_title),
                    getString(R.string.confirm_delete_message)
                ) {
                    controller.deleteDebt(item.debt.id) {
                        Toast.makeText(this@DebtListActivity, R.string.debt_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
                // Refresh list item to reset visual swipe state
                adapter.notifyItemChanged(position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerDebts)

        // Observe data
        controller.activeDebts.observe(this) { list ->
            activeList = list ?: emptyList()
            updateUI()
        }
        controller.completedDebts.observe(this) { list ->
            completedList = list ?: emptyList()
            updateUI()
        }

        // Tab selection listener
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateUI()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateUI() {
        val currentTab = binding.tabLayout.selectedTabPosition
        val currentList = if (currentTab == 0) activeList else completedList

        if (currentList.isEmpty()) {
            binding.textEmpty.visibility = View.VISIBLE
            binding.recyclerDebts.visibility = View.GONE
        } else {
            binding.textEmpty.visibility = View.GONE
            binding.recyclerDebts.visibility = View.VISIBLE
            adapter.submitList(currentList)
        }
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }

    private class DebtListAdapter(
        private val onItemClick: (DebtWithNextDue) -> Unit,
        private val onItemLongClick: (DebtWithNextDue) -> Unit
    ) : RecyclerView.Adapter<DebtListAdapter.ViewHolder>() {

        private var list: List<DebtWithNextDue> = emptyList()

        fun submitList(newList: List<DebtWithNextDue>) {
            list = newList
            notifyDataSetChanged()
        }

        fun getItemAt(position: Int): DebtWithNextDue = list[position]

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
