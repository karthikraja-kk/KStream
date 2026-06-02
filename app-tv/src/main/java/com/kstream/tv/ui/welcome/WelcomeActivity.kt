package com.kstream.tv.ui.welcome

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kstream.core.common.AppState
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.tv.R
import com.kstream.tv.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * One-time welcome screen shown on first launch.
 *
 *  - Asks for a display name (min 2 chars, max 24).
 *  - Saves it via [UserDataRepository.setUsername] and marks
 *    `isFirstLaunchCompleted = true` so subsequent launches skip straight to
 *    [MainActivity].
 *  - "Skip" stores an empty name and still marks first launch done so the
 *    user is never trapped on this screen.
 */
@AndroidEntryPoint
class WelcomeActivity : AppCompatActivity() {

    @Inject
    lateinit var userDataRepository: UserDataRepository

    private lateinit var nameField: EditText
    private lateinit var continueBtn: Button
    private lateinit var skipBtn: Button
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)
        AppState.currentRoute = ROUTE

        nameField = findViewById(R.id.welcome_name_field)
        continueBtn = findViewById(R.id.welcome_continue_btn)
        skipBtn = findViewById(R.id.welcome_skip_btn)
        errorText = findViewById(R.id.welcome_error_text)

        nameField.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isDone) {
                continueBtn.performClick()
                true
            } else false
        }
        continueBtn.setOnClickListener { onContinue() }
        skipBtn.setOnClickListener { finishWelcome(name = "") }
        continueBtn.requestFocus()
    }

    private fun onContinue() {
        val raw = nameField.text?.toString()?.trim().orEmpty()
        if (raw.length < 2) {
            errorText.text = getString(R.string.welcome_error_too_short)
            errorText.visibility = View.VISIBLE
            return
        }
        if (raw.length > 24) {
            errorText.text = getString(R.string.welcome_error_too_long)
            errorText.visibility = View.VISIBLE
            return
        }
        errorText.visibility = View.GONE
        finishWelcome(name = raw)
    }

    private fun finishWelcome(name: String) {
        continueBtn.isEnabled = false
        skipBtn.isEnabled = false
        lifecycleScope.launch {
            val ok = runCatching {
                if (name.isNotEmpty()) userDataRepository.setUsername(name)
                userDataRepository.setFirstLaunchCompleted(true)
            }.isSuccess
            if (!ok) {
                Toast.makeText(
                    this@WelcomeActivity,
                    R.string.welcome_save_failed,
                    Toast.LENGTH_LONG
                ).show()
                continueBtn.isEnabled = true
                skipBtn.isEnabled = true
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
