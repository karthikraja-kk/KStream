package com.kstream.tv.ui.welcome

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
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
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
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
import kotlin.math.min
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
        applyAdaptiveLogoSize()
        playEntryAnimations()

        nameField.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_NEXT ||
                actionId == EditorInfo.IME_ACTION_GO ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isDone) {
                // Keyboard "Next" / "Done" / Enter shouldn't bypass the
                // empty-name guard. If the field is empty, surface the
                // error and stay put; otherwise behave as a Continue tap.
                if (continueBtn.isEnabled) {
                    continueBtn.performClick()
                } else {
                    updateContinueEnabled()
                    nameField.requestFocus()
                }
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

    /**
     * Sizes the logo at runtime to `min(40% of screen width, 360dp)` so
     * larger panels get a more prominent mark and small panels stay
     * proportional. ImageView uses `adjustViewBounds=true` + `wrap_content`
     * height, so width alone is enough to drive the aspect ratio.
     */
    private fun applyAdaptiveLogoSize() {
        val logo = findViewById<ImageView>(R.id.welcome_logo) ?: return
        val metrics = resources.displayMetrics
        val maxPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, LOGO_MAX_DP, metrics
        ).toInt()
        val percentPx = (metrics.widthPixels * LOGO_WIDTH_FRACTION).toInt()
        logo.layoutParams = logo.layoutParams.apply {
            width = min(maxPx, percentPx)
        }
    }

    /**
     * One-shot entry sequence: logo slide-in → stripe scale → headline /
     * subtitle fade-up → action card fade-up. All cheap alpha + translation
     * tweens, hardware-friendly. Staggered so the eye lands on the brand
     * first and the card last.
     */
    private fun playEntryAnimations() {
        val logo = findViewById<View>(R.id.welcome_logo) ?: return
        val stripe = findViewById<View>(R.id.welcome_stripe) ?: return
        val headline = findViewById<View>(R.id.welcome_headline) ?: return
        val subtitle = findViewById<View>(R.id.welcome_subtitle) ?: return
        val card = findViewById<View>(R.id.welcome_right_col) ?: return

        // Initial states.
        logo.alpha = 0f; logo.translationX = -dp(16f)
        stripe.alpha = 0f; stripe.scaleX = 0f; stripe.pivotX = 0f
        headline.alpha = 0f; headline.translationY = dp(12f)
        subtitle.alpha = 0f; subtitle.translationY = dp(12f)
        card.alpha = 0f; card.translationY = dp(16f)

        fun fadeSlide(target: View, dxDp: Float, dyDp: Float, dur: Long, delay: Long) {
            val a = ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f)
            val anims = mutableListOf<android.animation.Animator>(a)
            if (dxDp != 0f) anims += ObjectAnimator.ofFloat(target, View.TRANSLATION_X, dp(dxDp), 0f)
            if (dyDp != 0f) anims += ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, dp(dyDp), 0f)
            AnimatorSet().apply {
                playTogether(anims)
                duration = dur
                startDelay = delay
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        fadeSlide(logo, -16f, 0f, 280L, 0L)
        // Stripe scales from start, fades in alongside.
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(stripe, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(stripe, View.SCALE_X, 0f, 1f)
            )
            duration = 220L
            startDelay = 120L
            interpolator = DecelerateInterpolator()
            start()
        }
        fadeSlide(headline, 0f, 12f, 280L, 200L)
        fadeSlide(subtitle, 0f, 12f, 280L, 320L)
        fadeSlide(card, 0f, 16f, 320L, 200L)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

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
        val raw = nameField.text?.toString().orEmpty()
        val trimmedLen = raw.trim().length
        val hasEdited = raw != getString(R.string.welcome_default_name)
        // Only show the "empty" error after the user has interacted with
        // the field (the default value is "Guest" so the field is never
        // empty on first paint).
        if (trimmedLen == 0 && hasEdited) {
            errorText.text = getString(R.string.welcome_error_empty)
            errorText.visibility = View.VISIBLE
        } else if (errorText.text == getString(R.string.welcome_error_empty)) {
            errorText.visibility = View.GONE
        }
        continueBtn.isEnabled = termsCheck.isChecked && trimmedLen >= 1
    }

    private fun onContinue() {
        if (!termsCheck.isChecked) return
        val raw = nameField.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) {
            // Empty input must NEVER silently substitute the default name.
            // Show the inline error and keep focus on the field.
            errorText.text = getString(R.string.welcome_error_empty)
            errorText.visibility = View.VISIBLE
            nameField.requestFocus()
            return
        }
        if (raw.length > 24) {
            errorText.text = getString(R.string.welcome_error_too_long)
            errorText.visibility = View.VISIBLE
            return
        }
        errorText.visibility = View.GONE
        finishWelcome(raw)
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
        private const val LOGO_MAX_DP = 360f
        private const val LOGO_WIDTH_FRACTION = 0.22f
    }
}
