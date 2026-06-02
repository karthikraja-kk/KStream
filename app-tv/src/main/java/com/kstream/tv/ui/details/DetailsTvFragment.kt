package com.kstream.tv.ui.details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ItemBridgeAdapter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.kstream.core.enrichment.EnrichmentRepository
import com.kstream.core.enrichment.model.MovieEnrichment
import com.kstream.core.model.Movie
import com.kstream.core.model.MovieWithMedia
import com.kstream.feature.details.DetailsUiState
import com.kstream.feature.details.DetailsViewModel
import com.kstream.tv.R
import com.kstream.tv.ui.details.presenter.CastCardPresenter
import com.kstream.tv.ui.player.PlayerActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DetailsTvFragment : Fragment() {

    @Inject lateinit var enrichmentRepository: EnrichmentRepository

    private val viewModel: DetailsViewModel by viewModels()

    private lateinit var backdrop: ImageView
    private lateinit var logoImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var taglineText: TextView
    private lateinit var metaText: TextView
    private lateinit var synopsisText: TextView
    private lateinit var playButton: Button
    private lateinit var resumeButton: Button
    private lateinit var downloadButton: Button
    private lateinit var likeButton: Button
    private lateinit var qualityRow: ViewGroup
    private lateinit var castHeader: TextView
    private lateinit var castGrid: HorizontalGridView
    private lateinit var errorOverlay: View
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button
    private lateinit var loadingOverlay: View

    private val castAdapter = ArrayObjectAdapter(CastCardPresenter())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_details_tv, container, false)

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        backdrop = view.findViewById(R.id.details_backdrop)
        logoImage = view.findViewById(R.id.details_logo)
        titleText = view.findViewById(R.id.details_title)
        taglineText = view.findViewById(R.id.details_tagline)
        metaText = view.findViewById(R.id.details_meta)
        synopsisText = view.findViewById(R.id.details_synopsis)
        playButton = view.findViewById(R.id.btn_play)
        resumeButton = view.findViewById(R.id.btn_resume)
        downloadButton = view.findViewById(R.id.btn_download)
        likeButton = view.findViewById(R.id.btn_like)
        qualityRow = view.findViewById(R.id.quality_row)
        castHeader = view.findViewById(R.id.cast_header)
        castGrid = view.findViewById(R.id.cast_grid)
        errorOverlay = view.findViewById(R.id.error_overlay)
        errorText = view.findViewById(R.id.error_text)
        retryButton = view.findViewById(R.id.btn_retry)
        loadingOverlay = view.findViewById(R.id.loading_overlay)

        castGrid.adapter = ItemBridgeAdapter(castAdapter)

        playButton.setOnClickListener { startPlayback(resume = false) }
        resumeButton.setOnClickListener { startPlayback(resume = true) }
        downloadButton.setOnClickListener { viewModel.downloadMovie() }
        likeButton.setOnClickListener { viewModel.toggleLike() }
        retryButton.setOnClickListener { viewModel.refreshMovieDetails() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { render(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { it.movieWithMedia?.movie }
                    .distinctUntilChanged()
                    .flatMapLatest { movie ->
                        if (movie == null) {
                            flowOf<MovieEnrichment?>(null)
                        } else {
                            runCatching { enrichmentRepository.ensureCached(movie) }
                            enrichmentRepository.observe(movie)
                        }
                    }
                    .collectLatest { enrichment -> renderEnrichment(enrichment) }
            }
        }

        playButton.requestFocus()
    }

    private fun render(state: DetailsUiState) {
        loadingOverlay.isVisible = state.isLoading
        val mwm = state.movieWithMedia
        if (state.error != null && mwm == null) {
            errorOverlay.isVisible = true
            errorText.text = state.error
            return
        }
        errorOverlay.isVisible = false
        if (mwm == null) return
        bindMovie(mwm)
        bindQualities(mwm, state.selectedQuality)
        resumeButton.isVisible = state.hasWatchProgress
        likeButton.text = if (state.isLiked) getString(R.string.details_liked) else getString(R.string.details_like)
    }

    private fun bindMovie(mwm: MovieWithMedia) {
        val movie: Movie = mwm.movie
        titleText.text = movie.movieName
        val parts = buildList {
            if (movie.year > 0) add(movie.year.toString())
            if (movie.duration.isNotBlank()) add(movie.duration)
            if (movie.rating.isNotBlank()) add(movie.rating)
            if (movie.language.isNotBlank()) add(movie.language)
            val g = movie.genres.take(3).joinToString(" / ")
            if (g.isNotBlank()) add(g)
        }
        metaText.text = parts.joinToString("  •  ")
        synopsisText.text = movie.synopsis.ifBlank { "" }
        if (backdrop.tag == null && movie.posterUrl.isNotBlank()) {
            Glide.with(this).load(movie.posterUrl)
                .placeholder(R.drawable.backdrop_placeholder)
                .error(R.drawable.backdrop_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(backdrop)
        }
    }

    private fun renderEnrichment(enrichment: MovieEnrichment?) {
        if (enrichment == null) {
            castHeader.isGone = true
            castGrid.isGone = true
            taglineText.isGone = true
            logoImage.isGone = true
            return
        }
        val firstBackdrop = enrichment.backdrops.firstOrNull()
        if (!firstBackdrop.isNullOrBlank()) {
            backdrop.tag = "enriched"
            Glide.with(this).load(firstBackdrop)
                .placeholder(R.drawable.backdrop_placeholder)
                .error(R.drawable.backdrop_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .centerCrop()
                .into(backdrop)
        }
        if (!enrichment.logoUrl.isNullOrBlank()) {
            logoImage.isVisible = true
            titleText.isGone = true
            Glide.with(this).load(enrichment.logoUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .fitCenter()
                .into(logoImage)
        } else {
            logoImage.isGone = true
            titleText.isVisible = true
        }
        if (!enrichment.tagline.isNullOrBlank()) {
            taglineText.isVisible = true
            taglineText.text = enrichment.tagline
        } else {
            taglineText.isGone = true
        }
        if (synopsisText.text.isNullOrBlank() && !enrichment.overview.isNullOrBlank()) {
            synopsisText.text = enrichment.overview
        }
        if (enrichment.cast.isNotEmpty()) {
            castHeader.isVisible = true
            castGrid.isVisible = true
            castAdapter.clear()
            castAdapter.addAll(0, enrichment.cast)
        } else {
            castHeader.isGone = true
            castGrid.isGone = true
        }
    }

    private fun bindQualities(mwm: MovieWithMedia, selected: String?) {
        qualityRow.removeAllViews()
        val ctx = requireContext()
        mwm.media.forEach { media ->
            val btn = Button(ctx).apply {
                text = media.quality
                isAllCaps = false
                setBackgroundResource(
                    if (media.quality == selected) R.drawable.quality_chip_selected
                    else R.drawable.quality_chip_bg
                )
                setTextColor(resources.getColor(android.R.color.white, null))
                setPadding(28, 12, 28, 12)
                isFocusable = true
                isFocusableInTouchMode = true
                setOnClickListener { viewModel.onQualitySelected(media.quality) }
            }
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = 16
            qualityRow.addView(btn, lp)
        }
    }

    private fun startPlayback(resume: Boolean) {
        val mwm = viewModel.uiState.value.movieWithMedia ?: return
        val quality = viewModel.uiState.value.selectedQuality ?: mwm.media.firstOrNull()?.quality ?: return
        val media = mwm.media.find { it.quality == quality } ?: return
        val url = media.watchUrl1 ?: media.watchUrl2 ?: return
        if (!resume) viewModel.onStartOver()
        startActivity(
            PlayerActivity.newIntent(
                requireContext(),
                movieId = mwm.movie.id,
                title = mwm.movie.movieName,
                streamUrl = url,
                quality = quality
            )
        )
    }
}
