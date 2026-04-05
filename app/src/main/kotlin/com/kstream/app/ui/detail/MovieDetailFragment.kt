package com.kstream.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.kstream.app.databinding.FragmentMovieDetailBinding
import com.kstream.core.domain.models.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MovieDetailFragment : Fragment() {

    private var _binding: FragmentMovieDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MovieDetailViewModel by viewModels()
    private val args: MovieDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.getMovieDetail(args.path).observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Success -> {
                    val movie = resource.data
                    binding.title.text = movie.title
                    binding.info.text = "${movie.year} | ${movie.genre} | ${movie.duration}"
                    binding.synopsis.text = movie.synopsis
                    
                    // We need the poster URL. Let's observe it separately.
                    viewModel.getPoster(args.path).observe(viewLifecycleOwner) { posterUrl ->
                        binding.poster.load(posterUrl)
                        binding.backdrop.load(posterUrl)
                    }

                    binding.watch_button.setOnClickListener {
                        // For simplicity, just pick first quality
                        val url = movie.qualities.firstOrNull()?.server1Url ?: ""
                        findNavController().navigate(MovieDetailFragmentDirections.actionMovieDetailFragmentToPlayerFragment(url, movie.title))
                    }
                }
                else -> {} // Handle loading/error
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
