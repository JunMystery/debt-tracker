package com.example.debt_tracker.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.example.debt_tracker.R

object ConfirmDialog {
    fun show(
        context: Context,
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }
}
