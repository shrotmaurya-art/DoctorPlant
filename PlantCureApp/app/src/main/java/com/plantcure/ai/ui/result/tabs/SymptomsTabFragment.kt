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
import com.plantcure.ai.databinding.TabSymptomsBinding
import com.plantcure.ai.ui.result.ResultViewModel

class SymptomsTabFragment : Fragment() {
    private var _binding: TabSymptomsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ResultViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TabSymptomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val adapter = SymptomAdapter()
        binding.rvSymptoms.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSymptoms.adapter = adapter
        
        viewModel.disease.observe(viewLifecycleOwner) { disease ->
            disease?.let { adapter.submitList(it.symptoms) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class SymptomAdapter : RecyclerView.Adapter<SymptomAdapter.ViewHolder>() {
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

            fun bind(symptom: String, index: Int) {
                tvBullet.text = index.toString()
                tvText.text = symptom
            }
        }
    }
}
