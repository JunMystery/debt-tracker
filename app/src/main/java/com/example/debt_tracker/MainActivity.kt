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
import com.example.debt_tracker.ui.components.OptionsDialog
import com.example.debt_tracker.util.SwipeRevealTouchListener
import android.widget.Toast
import android.graphics.Rect
import android.view.MotionEvent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: DashboardController
    private lateinit var adapter: UpcomingAdapter
    private lateinit var repository: DebtRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up controller
        repository = DebtRepository.getInstance(applicationContext)
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
                OptionsDialog.show(
                    this@MainActivity,
                    debtWithNextDue.debt.creditorName,
                    onEdit = {
                        val intent = Intent(this@MainActivity, CreateEditDebtActivity::class.java).apply {
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
            }
        )
        binding.recyclerUpcoming.adapter = adapter

        // Close swiped card on scroll or touch outside
        binding.recyclerUpcoming.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    adapter.closeOpenedSwipe()
                }
            }
        })
        binding.recyclerUpcoming.setOnTouchListener { _, _ ->
            adapter.closeOpenedSwipe()
            false
        }

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

    private fun showDeleteConfirmation(item: DebtWithNextDue) {
        ConfirmDialog.show(
            this,
            getString(R.string.confirm_delete_title),
            getString(R.string.confirm_delete_message)
        ) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    repository.deleteDebt(item.debt.id)
                }
                Toast.makeText(this@MainActivity, R.string.debt_deleted, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private class UpcomingAdapter(
        private val onItemClick: (DebtWithNextDue) -> Unit,
        private val onItemLongClick: (DebtWithNextDue) -> Unit,
        private val onDeleteClick: (DebtWithNextDue) -> Unit
    ) : RecyclerView.Adapter<UpcomingAdapter.ViewHolder>() {

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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDebtCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding, onItemClick, onItemLongClick, onDeleteClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(list[position])
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
                    val otherOpened = this@UpcomingAdapter.openedSwipeListener != null &&
                            this@UpcomingAdapter.openedSwipeListener != listener &&
                            this@UpcomingAdapter.openedSwipeListener!!.isOpened()
                    if (otherOpened) {
                        this@UpcomingAdapter.openedSwipeListener?.animateSwipe(0f)
                        this@UpcomingAdapter.openedSwipeListener = null
                    } else {
                        this@UpcomingAdapter.openedSwipeListener = listener
                    }
                    otherOpened
                }
            )

            init {
                binding.cardForeground.setOnTouchListener(swipeTouchListener)
            }

            fun bind(item: DebtWithNextDue) {
                swipeTouchListener.reset()
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
