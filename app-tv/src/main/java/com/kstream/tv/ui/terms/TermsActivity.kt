package com.kstream.tv.ui.terms

import android.os.Bundle
import android.view.KeyEvent
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.FragmentActivity
import com.kstream.tv.R

/**
 * Read-only Terms &amp; Conditions screen.
 *
 *  - Full-screen scroll view; D-pad UP/DOWN scroll by a fixed chunk.
 *  - BACK closes (no explicit "I Understand" button by design).
 */
class TermsActivity : FragmentActivity() {

    private lateinit var scroll: NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms)
        scroll = findViewById(R.id.terms_scroll)
        scroll.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                scroll.smoothScrollBy(0, SCROLL_CHUNK_PX)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                scroll.smoothScrollBy(0, -SCROLL_CHUNK_PX)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        private const val SCROLL_CHUNK_PX = 220
    }
}
