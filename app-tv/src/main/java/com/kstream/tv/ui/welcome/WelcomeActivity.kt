package com.kstream.tv.ui.welcome

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kstream.core.common.AppState
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.tv.R
import com.kstream.tv.ui.main.MainActivity
import com.kstream.tv.ui.terms.TermsActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * One-time welcome screen shown on first launch.
 *
 *  - Asks for a display name (pre-filled with "Guest"; falls back to "Guest"
 *    if the user clears the field).
 *  - Requires the user to accept Terms & Conditions before Continue is enabled.
 *  - The "Terms & Conditions" text in the checkbox row opens [TermsActivity].
 *  - Persists name via [UserDataRepository.setUsername] and marks
 *    `isFirstLaunchCompleted = true`.
 */
@AndroidEntryPoint
class WelcomeActivity : FragmentActivity() {

    @Inject
    lateinit var userDataRepository: UserDataRepository

    private lateinit var nameField: EditText
    private lateinit var continueBtn: Button
    private lateinit var termsCheck: CheckBox
    private lateinit var termsText: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        AppState.currentRoute = ROUTE

        nameField = findViewById(R.id.welcome_name_field)
        continueBtn = findViewById(R.id.welcome_continue_btn)
        termsCheck = findViewById(R.id.welcome_terms_check)
        termsText = findViewById(R.id.welcome_terms_text)
        errorText = findViewById(R.id.welcome_error_text)

        bindTermsText()

        nameField.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isDone) {
                continueBtn.performClick()
                true
            } else false
        }

        nameField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateContinueEnabled()
        })

        termsCheck.setOnCheckedChangeListener { _, _ -> updateContinueEnabled() }
        termsText.setOnClickListener { openTerms() }

        continueBtn.setOnClickListener { onContinue() }

        nameField.setSelection(nameField.text?.length ?: 0)
        nameField.requestFocus()
        updateContinueEnabled()
    }

    private fun bindTermsText() {
        val prefix = getString(R.string.welcome_accept_prefix)
        val link = getString(R.string.welcome_accept_link)
        val full = prefix + link
        val span = SpannableString(full)
        val start = prefix.length
        val end = full.length
        val gold = ContextCompat.getColor(this, R.color.accent_gold)
        span.setSpan(ForegroundColorSpan(gold), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        span.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) = openTerms()
        }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        termsText.text = span
        termsText.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun openTerms() {
        startActivity(Intent(this, TermsActivity::class.java))
    }

    private fun updateContinueEnabled() {
        val nameLen = (nameField.text?.toString()?.trim()?.length ?: 0)
        continueBtn.isEnabled = termsCheck.isChecked && nameLen >= 1
    }

    private fun onContinue() {
        if (!termsCheck.isChecked) return
        val raw = nameField.text?.toString()?.trim().orEmpty()
        val name = when {
            raw.isEmpty() -> getString(R.string.welcome_default_name)
            raw.length > 24 -> {
                errorText.text = getString(R.string.welcome_error_too_long)
                errorText.visibility = View.VISIBLE
                return
            }
            else -> raw
        }
        errorText.visibility = View.GONE
        finishWelcome(name)
    }

    private fun finishWelcome(name: String) {
        continueBtn.isEnabled = false
        lifecycleScope.launch {
            val ok = runCatching {
                userDataRepository.setUsername(name)
                userDataRepository.setFirstLaunchCompleted(true)
            }.isSuccess
            if (!ok) {
                Toast.makeText(
                    this@WelcomeActivity,
                    R.string.welcome_save_failed,
                    Toast.LENGTH_LONG
                ).show()
                updateContinueEnabled()
                return@launch
            }
            val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    companion object {
        const val ROUTE = "tv/welcome"
    }
}
