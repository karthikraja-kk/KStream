package com.kstream.tv.ui.main

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kstream.core.common.AppState
import com.kstream.core.domain.GetMoviesUseCase
import com.kstream.core.domain.SyncMoviesUseCase
import com.kstream.core.domain.repository.UserDataRepository
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.model.Movie
import com.kstream.tv.R
import com.kstream.tv.ui.home.FocusedMovieRelay
import com.kstream.tv.ui.home.HomePreviewBinder
import com.kstream.tv.ui.home.HomeRowsFragment
import com.kstream.tv.ui.home.ShimmerOverlay
import com.kstream.tv.ui.search.SearchActivity
import com.kstream.tv.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * Post-welcome host activity (premium home redesign).
 *
 * Layout:
 *  - [R.id.home_preview]      : sticky preview pane (top, 280dp). Mirrors
 *                               the currently-focused rail tile via
 *                               [HomePreviewBinder].
 *  - [R.id.rows_container]    : Leanback [HomeRowsFragment], fills the
 *                               remainder of the screen.
 *  - Side nav                 : overlay on the start edge. Collapsed 56dp
 *                               by default, animates to 220dp when any
 *                               nav item gains focus.
 *
 * BACK behaviour:
 *  - If the side nav is expanded → collapse it.
 *  - Otherwise → show "Exit KStream?" dialog. On confirm, hand off to the
 *    system launcher and `finishAndRemoveTask` (Fire TV freezes if we
 *    just `finish()` from the root task).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var focusedRelay: FocusedMovieRelay
    @Inject lateinit var enrichmentRepository: EnrichmentRepository
    @Inject lateinit var userDataRepository: UserDataRepository
    @Inject lateinit var syncMoviesUseCase: SyncMoviesUseCase
    @Inject lateinit var getMoviesUseCase: GetMoviesUseCase

    private var rowsFragment: HomeRowsFragment? = null
    private lateinit var previewBinder: HomePreviewBinder

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

    /** Last focused view in the content area — restored when nav collapses. */
    private var lastContentFocus: View? = null

    private var exitDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        AppState.currentRoute = ROUTE

        collapsedWidthPx = dp(NAV_COLLAPSED_DP)
        expandedWidthPx = dp(NAV_EXPANDED_DP)

        if (savedInstanceState == null) {
            val rows = HomeRowsFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.rows_container, rows, TAG_ROWS)
                .commitNow()
            rowsFragment = rows
        } else {
            rowsFragment = supportFragmentManager.findFragmentByTag(TAG_ROWS) as? HomeRowsFragment
        }

        rowsFragment?.setOnMovieClicked { movie: Movie ->
            startActivity(com.kstream.tv.ui.details.DetailsActivity.newIntent(this, movie))
        }

        // Sticky section name placeholder. Updates with a quick fade
        // whenever D-pad focus moves into a different rail.
        val sectionLabel = findViewById<TextView>(R.id.section_title_label)
        rowsFragment?.onSectionChanged = { title ->
            if (sectionLabel.text?.toString().orEmpty() != title) {
                sectionLabel.animate().cancel()
                sectionLabel.animate().alpha(0f).setDuration(80L).withEndAction {
                    sectionLabel.text = title
                    sectionLabel.animate().alpha(1f).setDuration(120L).start()
                }.start()
            }
        }

        previewBinder = HomePreviewBinder(
            root = findViewById(R.id.home_preview),
            focusedRelay = focusedRelay,
            enrichmentRepository = enrichmentRepository,
            userDataRepository = userDataRepository
        )
        previewBinder.attach(this)

        wireSideNav()
        wireExitOnBack()
        // Show shimmer immediately on the first frame. HomeRowsFragment
        // hides it when its first non-empty rails emission arrives.
        ShimmerOverlay.show(this)

        findViewById<View>(R.id.rows_container)?.let { rows ->
            rows.post { rows.requestFocus() }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // LEFT from anywhere outside the nav → focus the nav (which
            // triggers expand via the global focus listener).
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                val before = currentFocus
                if (before != null && ::navHome.isInitialized && !isWithinNav(before)) {
                    val next = before.focusSearch(View.FOCUS_LEFT)
                    if (next == null || isWithinNav(next) || next === before) {
                        navHome.requestFocus()
                        return true
                    }
                }
            }
            // RIGHT while the side-nav is open AND currently has focus →
            // collapse the bar and hand focus back to the content area
            // (last remembered tile / rails fragment). Without this, RIGHT
            // would either do nothing or move focus between nav items.
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
                isExpanded && ::navHome.isInitialized
            ) {
                val before = currentFocus
                if (before != null && isWithinNav(before)) {
                    collapseAndRestoreFocus()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun wireSideNav() {
        navRoot = findViewById(R.id.side_nav_root)
        navHome = findViewById(R.id.nav_item_home)
        navSearch = findViewById(R.id.nav_item_search)
        navSettings = findViewById(R.id.nav_item_settings)
        navRefresh = findViewById(R.id.nav_item_refresh)
        navLabelHome = findViewById(R.id.nav_label_home)
        navLabelSearch = findViewById(R.id.nav_label_search)
        navLabelSettings = findViewById(R.id.nav_label_settings)
        navLabelRefresh = findViewById(R.id.nav_label_refresh)
        navLogoSquare = findViewById(R.id.nav_logo_square)
        navLogoWordmark = findViewById(R.id.nav_logo_wordmark)

        val focusListener = View.OnFocusChangeListener { _, _ ->
            navRoot.post {
                val anyNavFocused = navHome.isFocused || navSearch.isFocused ||
                    navSettings.isFocused || navRefresh.isFocused
                if (anyNavFocused && !isExpanded) {
                    lastContentFocus = currentFocus
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

        navHome.setOnClickListener { collapseAndRestoreFocus() }
        navSearch.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
            collapseAndRestoreFocus()
        }
        navSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            collapseAndRestoreFocus()
        }
        navRefresh.setOnClickListener { triggerDataRefresh() }

        // Mark Home as the active screen — gold left bar + soft glow.
        navHome.foreground = androidx.core.content.ContextCompat.getDrawable(
            this, R.drawable.nav_item_active
        )
    }

    @Volatile private var isRefreshInFlight = false

    private fun triggerDataRefresh() {
        if (isRefreshInFlight) return
        isRefreshInFlight = true
        ShimmerOverlay.show(this)
        Toast.makeText(this, "Refreshing data…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = runCatching {
                // 1) DB refresh — re-pull movie list from server into Room.
                //    HomeRowsFragment observes the Movies flow and re-renders
                //    automatically once Room is updated.
                withContext(Dispatchers.IO) { syncMoviesUseCase() }

                // 2) TMDb force-refresh — for the above-the-fold tiles, hit
                //    TMDb again (refresh() bypasses the "row exists" guard
                //    that ensureCached() uses). Bounded + parallel-limited so
                //    we don't spam the network.
                val movies = withContext(Dispatchers.IO) {
                    getMoviesUseCase().first()
                }.take(REFRESH_TMDB_CAP)
                if (movies.isNotEmpty()) {
                    coroutineScope {
                        val sem = Semaphore(REFRESH_TMDB_PARALLELISM)
                        movies.forEach { movie: Movie ->
                            launch(Dispatchers.IO) {
                                sem.withPermit {
                                    runCatching { enrichmentRepository.refresh(movie) }
                                }
                            }
                        }
                    }
                }
            }
            isRefreshInFlight = false
            ShimmerOverlay.hide(this@MainActivity)
            val message = if (result.isSuccess) "Data refreshed" else "Refresh failed — try again"
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun isWithinNav(view: View): Boolean {
        var p: View? = view
        while (p != null) {
            if (p.id == R.id.side_nav_root) return true
            p = p.parent as? View
        }
        return false
    }

    private fun wireExitOnBack() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isExpanded) {
                        collapseAndRestoreFocus()
                        return
                    }
                    showExitDialog()
                }
            }
        )
    }

    private fun showExitDialog() {
        exitDialog?.takeIf { it.isShowing }?.let { return }
        val view = layoutInflater.inflate(R.layout.dialog_exit, null, false)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setOnDismissListener { exitDialog = null }
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000))
        view.findViewById<View>(R.id.exit_btn_confirm).setOnClickListener {
            dialog.dismiss()
            exitToLauncher()
        }
        view.findViewById<View>(R.id.exit_btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        exitDialog = dialog
        dialog.show()
        view.findViewById<View>(R.id.exit_btn_cancel).requestFocus()
    }

    private fun exitToLauncher() {
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(home)
        }
        // finishAffinity() tears down every activity in the current task
        // stack (Main + any pushed Details/Player) in one shot, regardless
        // of which one initiated the exit. Combined with the launcher
        // intent above, this gives a clean hand-off on Fire TV without
        // the root-task freeze we used to hit with a plain finish().
        finishAffinity()
    }

    private fun collapseAndRestoreFocus() {
        animateExpand(false)
        lastContentFocus?.requestFocus()
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

    override fun onDestroy() {
        exitDialog?.takeIf { it.isShowing }?.dismiss()
        exitDialog = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    companion object {
        const val ROUTE = "tv/home"
        private const val TAG_ROWS = "home_rows"
        private const val NAV_COLLAPSED_DP = 56
        private const val NAV_EXPANDED_DP = 220
        private const val ANIM_MS = 180L
        // Match HomePrewarmTask: only force-refresh TMDb for the above-the-fold
        // tiles. Remaining cards re-fetch lazily via HomeRowsFragment.
        private const val REFRESH_TMDB_CAP = 12
        private const val REFRESH_TMDB_PARALLELISM = 4
    }
}
