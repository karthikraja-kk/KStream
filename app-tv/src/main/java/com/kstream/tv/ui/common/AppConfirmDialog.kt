package com.kstream.tv.ui.common

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.kstream.tv.R

/**
 * Reusable themed confirm dialog matching dialog_exit (custom view + framework AlertDialog
 * with a transparent window). Works on activities that don't use Theme.AppCompat — which is
 * why neither MaterialAlertDialogBuilder nor androidx.appcompat.app.AlertDialog can be used
 * from the personal screens.
 */
object AppConfirmDialog {

    fun show(
        context: Context,
        title: String,
        message: String,
        positiveLabel: String = "Confirm",
        negativeLabel: String = "Cancel",
        onConfirm: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null, false)
        view.findViewById<TextView>(R.id.confirm_title).text = title
        view.findViewById<TextView>(R.id.confirm_message).text = message

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(0x00000000))

        val cancelBtn = view.findViewById<Button>(R.id.confirm_btn_cancel).apply { text = negativeLabel }
        val confirmBtn = view.findViewById<Button>(R.id.confirm_btn_confirm).apply { text = positiveLabel }
        cancelBtn.setOnClickListener { dialog.dismiss() }
        confirmBtn.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }
        dialog.show()
        cancelBtn.requestFocus()
    }

    /** Single-button informational variant — hides the cancel button. */
    fun showInfo(
        context: Context,
        title: String,
        message: String,
        dismissLabel: String = "Close"
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm, null, false)
        view.findViewById<TextView>(R.id.confirm_title).text = title
        view.findViewById<TextView>(R.id.confirm_message).text = message

        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(0x00000000))

        view.findViewById<Button>(R.id.confirm_btn_cancel).visibility = View.GONE
        val ok = view.findViewById<Button>(R.id.confirm_btn_confirm).apply { text = dismissLabel }
        ok.setOnClickListener { dialog.dismiss() }
        dialog.show()
        ok.requestFocus()
    }
}
