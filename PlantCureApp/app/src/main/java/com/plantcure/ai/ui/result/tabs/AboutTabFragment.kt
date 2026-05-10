package com.plantcure.ai.ui.result.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.plantcure.ai.databinding.TabAboutBinding
import com.plantcure.ai.ui.result.ResultViewModel

class AboutTabFragment : Fragment() {
    private var _binding: TabAboutBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.disease.observe(viewLifecycleOwner) { disease ->
            disease?.let {
                binding.tvAbout.text = it.about
                binding.tvCauseType.text = it.causeType
                binding.tvWeatherTrigger.text = it.weatherTrigger
                binding.tvRecoveryTime.text = "${it.recoveryWeeks} weeks"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
