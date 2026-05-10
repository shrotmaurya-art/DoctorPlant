package com.plantcure.ai.ui.result.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.plantcure.ai.databinding.TabMarketImpactBinding
import com.plantcure.ai.ui.result.ResultViewModel

class MarketImpactTabFragment : Fragment() {
    private var _binding: TabMarketImpactBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabMarketImpactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.disease.observe(viewLifecycleOwner) { disease ->
            disease?.let {
                binding.tvYieldLoss.text = it.yieldLoss
                binding.tvMarketImpact.text = it.marketImpact
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
