package com.kstream.app.ui.welcome

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.kstream.app.R
import com.kstream.app.databinding.FragmentWelcomeBinding
import com.kstream.core.domain.models.AppSettings
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WelcomeFragment : Fragment() {

    private var _binding: FragmentWelcomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WelcomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startButton.setOnClickListener {
            val name = binding.nameInput.text.toString()
            val baseUrl = binding.baseUrlInput.text.toString()
            val workerUrl = binding.workerUrlInput.text.toString()

            if (name.isNotEmpty() && baseUrl.isNotEmpty() && workerUrl.isNotEmpty()) {
                viewModel.saveSettings(AppSettings(name, baseUrl, workerUrl, "", "Auto", 100))
                findNavController().popBackStack() // Go back to Home
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
