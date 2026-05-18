package com.example.debt_tracker.ui.components

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class NoFilterAdapter<T>(
    context: Context,
    layout: Int,
    var items: List<T>
) : ArrayAdapter<T>(context, layout, items) {

    private val filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            results.values = items
            results.count = items.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter(): Filter = filter
}
