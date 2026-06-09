package com.kstream.tv.ui.common

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * TV IME behavior contract shared by every EditText in the app:
 *
 *  1. The soft keyboard MUST NOT pop open just because the EditText
 *     received focus via D-pad navigation. It opens only on an explicit
 *     click (touch / mouse) or a DPAD_CENTER / ENTER key press while the
 *     field is focused.
 *  2. Tapping the IME confirm key (tick / Done / Enter) hides the
 *     keyboard. Caller can pass [onConfirm] to also advance focus.
 *  3. BACK while the keyboard is showing hides the keyboard instead of
 *     navigating away. We can't intercept BACK on the EditText itself
 *     (the IME consumes it), but the IME's built-in BACK behavior already
 *     hides the keyboard on TV — what was breaking that was our prior
 *     showSoftInputOnFocus=true causing it to re-open as soon as BACK
 *     restored focus. With showSoftInputOnFocus=false the IME's BACK
 *     correctly closes the keyboard and the EditText simply keeps focus.
 *
 * Usage:
 * ```
 * TvEditTextIme.apply(myEditText) {
 *     // optional: do something on tick (e.g., advance focus / submit)
 * }
 * ```
 */
object TvEditTextIme {

    fun apply(editText: EditText, onConfirm: (() -> Unit)? = null) {
        // (1) Don't open IME on focus — only on explicit interaction.
        editText.showSoftInputOnFocus = false

        editText.setOnClickListener { showKeyboard(editText) }

        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && (
                    keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    )
            ) {
                val imm = editText.context.getSystemService(Activity.INPUT_METHOD_SERVICE)
                    as? InputMethodManager
                val imeShown = imm?.isAcceptingText == true
                if (!imeShown) {
                    showKeyboard(editText)
                    return@setOnKeyListener true
                }
                // IME is already up — let the IME's own confirm handler
                // (onEditorActionListener) run so the tick dismisses it.
            }
            false
        }

        // (2) Tick / Done / Enter from the IME → hide keyboard + onConfirm.
        editText.setOnEditorActionListener { _, actionId, event ->
            val isConfirm = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_SEND ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isConfirm) {
                hideKeyboard(editText)
                onConfirm?.invoke()
                true
            } else false
        }
    }

    fun showKeyboard(view: EditText) {
        view.requestFocus()
        if (view is EditText) {
            view.setSelection(view.text?.length ?: 0)
        }
        val imm = view.context.getSystemService(Activity.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard(view: View) {
        val imm = view.context.getSystemService(Activity.INPUT_METHOD_SERVICE)
            as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
