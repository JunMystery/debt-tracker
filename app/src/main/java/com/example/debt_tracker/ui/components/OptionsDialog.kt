package com.example.debt_tracker.ui.components

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.debt_tracker.R

object OptionsDialog {
    fun show(
        context: Context,
        title: String,
        onEdit: () -> Unit,
        onDelete: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_options, null)
        val textTitle = view.findViewById<TextView>(R.id.textDialogTitle)
        val btnEdit = view.findViewById<MaterialButton>(R.id.btnEdit)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btnDelete)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)

        textTitle.text = title

        val dialog = MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DebtTracker_MaterialAlertDialog)
            .setView(view)
            .create()

        dialog.window?.setWindowAnimations(0)

        btnEdit.setOnClickListener {
            onEdit()
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            onDelete()
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
