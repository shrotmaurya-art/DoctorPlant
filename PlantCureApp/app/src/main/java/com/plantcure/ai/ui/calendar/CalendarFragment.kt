package com.plantcure.ai.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentCalendarBinding
import com.plantcure.ai.databinding.ItemTreatmentStepBinding
import dagger.hilt.android.AndroidEntryPoint

import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.plantcure.ai.data.local.entity.TreatmentSchedule
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        binding.rvTreatments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTreatments.adapter = TreatmentAdapter(emptyList())
    }

    private fun observeViewModel() {
        lifecycleScope.launchWhenStarted {
            viewModel.treatments.collect { items ->
                (binding.rvTreatments.adapter as TreatmentAdapter).updateItems(items)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class TreatmentAdapter(private var items: List<TreatmentSchedule>) : RecyclerView.Adapter<TreatmentAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemTreatmentStepBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateItems(newItems: List<TreatmentSchedule>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemTreatmentStepBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val date = Date(item.scheduledTime)
        
        holder.binding.tvMonth.text = SimpleDateFormat("MMM", Locale.getDefault()).format(date).uppercase()
        holder.binding.tvDay.text = SimpleDateFormat("dd", Locale.getDefault()).format(date)
        holder.binding.tvActionTitle.text = item.actionTitle
        holder.binding.tvStatus.text = if (item.isCompleted) "Done" else "Scheduled"

        val color = if (item.isCompleted) {
            Color.parseColor("#1B6B2F") // Primary Green
        } else if (item.scheduledTime < System.currentTimeMillis()) {
            Color.parseColor("#B00020") // Overdue Red
        } else {
            Color.parseColor("#707A6E") // Neutral Gray
        }

        holder.binding.tvMonth.setTextColor(color)
        holder.binding.tvDay.setTextColor(color)
        holder.binding.tvStatus.setTextColor(color)
        holder.binding.ivStatusIcon.setColorFilter(color)
        
        holder.binding.ivStatusIcon.setImageResource(
            if (item.isCompleted) com.plantcure.ai.R.drawable.ic_check_circle else com.plantcure.ai.R.drawable.ic_calendar
        )
    }

    override fun getItemCount() = items.size
}
