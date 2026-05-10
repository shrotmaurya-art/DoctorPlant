package com.plantcure.ai.ui.result.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.R
import com.plantcure.ai.databinding.TabPreventionBinding
import com.plantcure.ai.ui.result.ResultViewModel

class PreventionTabFragment : Fragment() {
    private var _binding: TabPreventionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabPreventionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val organicAdapter = TreatmentAdapter(true)
        val chemicalAdapter = TreatmentAdapter(false)
        
        binding.rvOrganic.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrganic.adapter = organicAdapter
        
        binding.rvChemical.layoutManager = LinearLayoutManager(requireContext())
        binding.rvChemical.adapter = chemicalAdapter
        
        viewModel.disease.observe(viewLifecycleOwner) { disease ->
            disease?.let {
                organicAdapter.submitList(it.preventionOrganic)
                chemicalAdapter.submitList(it.preventionChemical)
                binding.tvDosageSafety.text = it.dosageSafety
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class TreatmentAdapter(private val isOrganic: Boolean) : RecyclerView.Adapter<TreatmentAdapter.ViewHolder>() {
        private var items = emptyList<String>()

        fun submitList(newItems: List<String>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_symptom, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position + 1)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvBullet: TextView = itemView.findViewById(R.id.tvBullet)
            private val tvText: TextView = itemView.findViewById(R.id.tvSymptomText)

            fun bind(treatment: String, index: Int) {
                tvBullet.text = index.toString()
                tvBullet.backgroundTintList = requireContext().getColorStateList(
                    if (isOrganic) R.color.severity_low else R.color.severity_medium
                )
                tvText.text = treatment
            }
        }
    }
}
