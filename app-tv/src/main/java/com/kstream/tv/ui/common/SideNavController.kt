package com.kstream.tv.ui.common

import android.animation.ValueAnimator
import android.content.Intent
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.kstream.tv.R
import com.kstream.tv.ui.search.SearchActivity
import com.kstream.tv.ui.settings.SettingsActivity

/**
 * Shared side-nav controller used by [com.kstream.tv.ui.main.MainActivity]
 * and [com.kstream.tv.ui.search.SearchActivity]. Wires the standard
 * collapse/expand-on-focus behaviour, LEFT/RIGHT key handoff, and
 * Home/Search/Settings click targets.
 *
 * Per-activity overrides:
 *  - [onHomeClick]/[onSettingsClick]/[onSearchClick] can be set to
 *    re-route a tap (default behaviours start the relevant activity, or
 *    just collapse the nav for the activity that already represents that
 *    destination).
 */
class SideNavController {

    private lateinit var activity: FragmentActivity
    private lateinit var navRoot: ViewGroup
    private lateinit var navHome: View
    private lateinit var navSearch: View
    private lateinit var navSettings: View
    private lateinit var navRefresh: View
    private lateinit var navLabelHome: TextView
    private lateinit var navLabelSearch: TextView
    private lateinit var navLabelSettings: TextView
    private lateinit var navLabelRefresh: TextView
    private lateinit var navLogoSquare: ImageView
    private lateinit var navLogoWordmark: ImageView

    private var collapsedWidthPx: Int = 0
    private var expandedWidthPx: Int = 0
    private var widthAnimator: ValueAnimator? = null
    private var isExpanded = false

    private var lastContentFocus: View? = null

    var onHomeClick: (() -> Unit)? = null
    var onSearchClick: (() -> Unit)? = null
    var onSettingsClick: (() -> Unit)? = null
    var onRefreshClick: (() -> Unit)? = null

    fun attach(activity: FragmentActivity) {
        this.activity = activity
        navRoot = activity.findViewById(R.id.side_nav_root)
        navHome = activity.findViewById(R.id.nav_item_home)
        navSearch = activity.findViewById(R.id.nav_item_search)
        navSettings = activity.findViewById(R.id.nav_item_settings)
        navRefresh = activity.findViewById(R.id.nav_item_refresh)
        navLabelHome = activity.findViewById(R.id.nav_label_home)
        navLabelSearch = activity.findViewById(R.id.nav_label_search)
        navLabelSettings = activity.findViewById(R.id.nav_label_settings)
        navLabelRefresh = activity.findViewById(R.id.nav_label_refresh)
        navLogoSquare = activity.findViewById(R.id.nav_logo_square)
        navLogoWordmark = activity.findViewById(R.id.nav_logo_wordmark)

        collapsedWidthPx = dp(NAV_COLLAPSED_DP)
        expandedWidthPx = dp(NAV_EXPANDED_DP)

        val focusListener = View.OnFocusChangeListener { _, _ ->
            navRoot.post {
                val anyNavFocused = navHome.isFocused || navSearch.isFocused ||
                    navSettings.isFocused || navRefresh.isFocused
                if (anyNavFocused && !isExpanded) {
                    lastContentFocus = activity.currentFocus
                        ?.takeIf { !isWithinNav(it) }
                        ?: lastContentFocus
                    animateExpand(true)
                } else if (!anyNavFocused && isExpanded) {
                    animateExpand(false)
                }
            }
        }
        navHome.onFocusChangeListener = focusListener
        navSearch.onFocusChangeListener = focusListener
        navSettings.onFocusChangeListener = focusListener
        navRefresh.onFocusChangeListener = focusListener

        navHome.setOnClickListener {
            onHomeClick?.invoke() ?: run {
                val intent = Intent().apply {
                    setClassName(activity, "com.kstream.tv.ui.main.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                activity.startActivity(intent)
                collapseAndRestoreFocus()
            }
        }
        navSearch.setOnClickListener {
            onSearchClick?.invoke() ?: run {
                activity.startActivity(Intent(activity, SearchActivity::class.java))
                collapseAndRestoreFocus()
            }
        }
        navSettings.setOnClickListener {
            onSettingsClick?.invoke() ?: run {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                collapseAndRestoreFocus()
            }
        }
        navRefresh.setOnClickListener {
            onRefreshClick?.invoke()
            // Leave focus / nav state alone — host activity decides whether to
            // navigate, show a toast, or just stay put.
        }
    }

    /**
     * Standard dispatch behaviour:
     *  - LEFT from outside the nav → focus the nav (auto-expands).
     *  - RIGHT from inside the (expanded) nav → collapse and restore focus.
     *
     * Returns true if the event was consumed.
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val before = activity.currentFocus
                if (before != null && ::navHome.isInitialized && !isWithinNav(before)) {
                    val next = before.focusSearch(View.FOCUS_LEFT)
                    if (next == null || isWithinNav(next) || next === before) {
                        navHome.requestFocus()
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isExpanded && ::navHome.isInitialized) {
                    val before = activity.currentFocus
                    if (before != null && isWithinNav(before)) {
                        collapseAndRestoreFocus()
                        return true
                    }
                }
            }
        }
        return false
    }

    fun isExpanded(): Boolean = isExpanded

    /**
     * Marks one of the nav items as representing the currently active
     * screen. The matching item gets a left-edge gold accent bar + soft
     * horizontal glow drawn as a foreground (so the focus selector still
     * paints over it). Pass `null` (or an unknown id) to clear.
     */
    fun setActive(navItemId: Int?) {
        if (!::navHome.isInitialized) return
        listOf(navHome, navSearch, navSettings, navRefresh).forEach { item ->
            item.foreground = if (item.id == navItemId) {
                androidx.core.content.ContextCompat.getDrawable(
                    activity, R.drawable.nav_item_active
                )
            } else {
                null
            }
        }
    }

    fun collapseAndRestoreFocus() {
        animateExpand(false)
        lastContentFocus?.requestFocus()
    }

    fun isWithinNav(view: View): Boolean {
        var p: View? = view
        while (p != null) {
            if (p.id == R.id.side_nav_root) return true
            p = p.parent as? View
        }
        return false
    }

    private fun animateExpand(expand: Boolean) {
        if (expand == isExpanded) return
        isExpanded = expand
        widthAnimator?.cancel()
        val target = if (expand) expandedWidthPx else collapsedWidthPx
        val start = navRoot.layoutParams.width.takeIf { it > 0 } ?: collapsedWidthPx
        widthAnimator = ValueAnimator.ofInt(start, target).apply {
            duration = ANIM_MS
            addUpdateListener { anim ->
                val v = anim.animatedValue as Int
                navRoot.layoutParams.width = v
                navRoot.requestLayout()
            }
            start()
        }
        val labelAlpha = if (expand) 1f else 0f
        val squareAlpha = if (expand) 0f else 1f
        val wordmarkAlpha = if (expand) 1f else 0f
        listOf(navLabelHome, navLabelSearch, navLabelSettings, navLabelRefresh).forEach {
            it.animate().alpha(labelAlpha).setDuration(ANIM_MS).start()
        }
        navLogoSquare.animate().alpha(squareAlpha).setDuration(ANIM_MS).start()
        navLogoWordmark.animate().alpha(wordmarkAlpha).setDuration(ANIM_MS).start()
        navRoot.setBackgroundResource(
            if (expand) R.drawable.nav_scrim else R.drawable.nav_scrim_collapsed
        )
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        activity.resources.displayMetrics
    ).toInt()

    companion object {
        private const val NAV_COLLAPSED_DP = 56
        private const val NAV_EXPANDED_DP = 220
        private const val ANIM_MS = 180L
    }
}
