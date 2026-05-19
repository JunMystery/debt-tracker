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
import com.example.debt_tracker.ui.components.OptionsDialog
import com.example.debt_tracker.util.SwipeRevealTouchListener
import android.graphics.Rect
import android.view.MotionEvent

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
                OptionsDialog.show(
                    this@DebtListActivity,
                    debtWithNextDue.debt.creditorName,
                    onEdit = {
                        val intent = Intent(this@DebtListActivity, CreateEditDebtActivity::class.java).apply {
                            putExtra("DEBT_ID", debtWithNextDue.debt.id)
                        }
                        startActivity(intent)
                        overrideSlideTransition(true)
                    },
                    onDelete = {
                        showDeleteConfirmation(debtWithNextDue)
                    }
                )
            },
            onDeleteClick = { debtWithNextDue ->
                showDeleteConfirmation(debtWithNextDue)
            },
            isSwipeEnabled = {
                binding.tabLayout.selectedTabPosition == 0
            }
        )
        binding.recyclerDebts.adapter = adapter

        // Close swiped card on scroll or touch outside
        binding.recyclerDebts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.closeOpenedSwipe()
                }
            }
        })
        binding.recyclerDebts.setOnTouchListener { _, _ ->
            adapter.closeOpenedSwipe()
            false
        }

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
                adapter.closeOpenedSwipe()
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

    private fun showDeleteConfirmation(item: DebtWithNextDue) {
        ConfirmDialog.show(
            this,
            getString(R.string.confirm_delete_title),
            getString(R.string.confirm_delete_message)
        ) {
            controller.deleteDebt(item.debt.id) {
                Toast.makeText(this@DebtListActivity, R.string.debt_deleted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun finish() {
        super.finish()
        overrideSlideTransition(false)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val opened = adapter.openedSwipeListener
            if (opened != null && opened.isOpened()) {
                val view = opened.itemView
                val rect = Rect()
                view.getGlobalVisibleRect(rect)
                if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    adapter.closeOpenedSwipe()
                    return true // Consume to block action outside
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private class DebtListAdapter(
        private val onItemClick: (DebtWithNextDue) -> Unit,
        private val onItemLongClick: (DebtWithNextDue) -> Unit,
        private val onDeleteClick: (DebtWithNextDue) -> Unit,
        private val isSwipeEnabled: () -> Boolean
    ) : RecyclerView.Adapter<DebtListAdapter.ViewHolder>() {

        private var list: List<DebtWithNextDue> = emptyList()
        var openedSwipeListener: SwipeRevealTouchListener? = null

        fun submitList(newList: List<DebtWithNextDue>) {
            list = newList
            notifyDataSetChanged()
        }

        fun closeOpenedSwipe() {
            openedSwipeListener?.animateSwipe(0f)
            openedSwipeListener = null
        }

        fun getItemAt(position: Int): DebtWithNextDue = list[position]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDebtCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onItemClick, onItemLongClick, onDeleteClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position], isSwipeEnabled())
        }

        override fun getItemCount(): Int = list.size

        inner class ViewHolder(
            private val binding: ItemDebtCardBinding,
            private val onItemClick: (DebtWithNextDue) -> Unit,
            private val onItemLongClick: (DebtWithNextDue) -> Unit,
            private val onDeleteClick: (DebtWithNextDue) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {

            private val swipeTouchListener = SwipeRevealTouchListener(
                binding.cardForeground,
                onSwipeStateChanged = { listener ->
                    val otherOpened = this@DebtListAdapter.openedSwipeListener != null &&
                            this@DebtListAdapter.openedSwipeListener != listener &&
                            this@DebtListAdapter.openedSwipeListener!!.isOpened()
                    if (otherOpened) {
                        this@DebtListAdapter.openedSwipeListener?.animateSwipe(0f)
                        this@DebtListAdapter.openedSwipeListener = null
                    } else {
                        this@DebtListAdapter.openedSwipeListener = listener
                    }
                    otherOpened
                }
            )

            init {
                binding.cardForeground.setOnTouchListener(swipeTouchListener)
            }

            fun bind(item: DebtWithNextDue, isSwipeEnabled: Boolean) {
                swipeTouchListener.reset()
                if (isSwipeEnabled) {
                    binding.cardForeground.setOnTouchListener(swipeTouchListener)
                } else {
                    binding.cardForeground.setOnTouchListener(null)
                }

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

                binding.cardForeground.setOnClickListener {
                    if (swipeTouchListener.isOpened()) {
                        swipeTouchListener.animateSwipe(0f)
                    } else {
                        onItemClick(item)
                    }
                }
                binding.cardForeground.setOnLongClickListener {
                    if (!swipeTouchListener.isOpened()) {
                        onItemLongClick(item)
                        true
                    } else {
                        false
                    }
                }
                binding.btnDeleteSwipe.setOnClickListener {
                    swipeTouchListener.animateSwipe(0f)
                    onDeleteClick(item)
                }
            }
        }
    }
}
