package com.plantcure.ai.ui.calendar

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.R
import com.plantcure.ai.databinding.FragmentCalendarBinding
import com.plantcure.ai.databinding.ItemTreatmentStepBinding
import com.plantcure.ai.data.local.entity.TreatmentSchedule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: TreatmentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        setupAnimations()
    }

    private fun setupAnimations() {
        binding.nextTreatmentCard.translationY = -150f
        binding.nextTreatmentCard.alpha = 0f
        binding.nextTreatmentCard.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = TreatmentAdapter(emptyList()) { treatment, action ->
            when (action) {
                "complete" -> viewModel.markCompleted(treatment.id)
                "skip" -> viewModel.markSkipped(treatment.id)
            }
        }
        binding.rvTreatments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTreatments.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.treatments.collect { items ->
                        adapter.updateItems(items)
                        // Show empty state if no treatments
                        if (items.isEmpty()) {
                            binding.rvTreatments.visibility = View.GONE
                        } else {
                            binding.rvTreatments.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class TreatmentAdapter(
    private var items: List<TreatmentSchedule>,
    private val onAction: (TreatmentSchedule, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<TreatmentAdapter.ViewHolder>() {

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
        val date = Date(item.treatmentDate)

        holder.binding.tvMonth.text = SimpleDateFormat("MMM", Locale.getDefault()).format(date).uppercase()
        holder.binding.tvDay.text = SimpleDateFormat("dd", Locale.getDefault()).format(date)
        holder.binding.tvActionTitle.text = item.actionDescription

        val statusText: String
        val color: Int

        when {
            item.isCompleted -> {
                statusText = "Done"
                color = Color.parseColor("#1B6B2F")
            }
            item.isSkipped -> {
                statusText = "Skipped"
                color = Color.parseColor("#707A6E")
            }
            item.treatmentDate < System.currentTimeMillis() -> {
                statusText = "Overdue"
                color = Color.parseColor("#B00020")
            }
            else -> {
                statusText = "Scheduled"
                color = Color.parseColor("#707A6E")
            }
        }

        holder.binding.tvStatus.text = statusText
        holder.binding.tvMonth.setTextColor(color)
        holder.binding.tvDay.setTextColor(color)
        holder.binding.tvStatus.setTextColor(color)
        holder.binding.ivStatusIcon.setColorFilter(color)

        holder.binding.ivStatusIcon.setImageResource(
            if (item.isCompleted) R.drawable.ic_check_circle else R.drawable.ic_calendar
        )

        // Long-press to mark complete/skip
        holder.itemView.setOnLongClickListener {
            if (!item.isCompleted && !item.isSkipped) {
                // Scale animation
                holder.itemView.animate()
                    .scaleX(0.95f).scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        holder.itemView.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(100)
                            .withEndAction {
                                onAction(item, "complete")
                            }.start()
                    }.start()
            }
            true
        }

        // Cascade fade in
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .alpha(1f)
            .setDuration(400)
            .setStartDelay((position * 100).toLong())
            .start()
    }

    override fun getItemCount() = items.size
}
