package com.kstream.app.ui.year

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.kstream.app.databinding.FragmentYearBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class YearFragment : Fragment() {

    private var _binding: FragmentYearBinding? = null
    private val binding get() = _binding!!

    private val viewModel: YearViewModel by viewModels()
    private val args: YearFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentYearBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val movieAdapter = MovieAdapter { movieItem ->
            findNavController().navigate(YearFragmentDirections.actionYearFragmentToMovieDetailFragment(movieItem.path))
        }
        binding.movieRecycler.adapter = movieAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMovies(args.year).collectLatest { pagingData ->
                movieAdapter.submitData(pagingData)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
