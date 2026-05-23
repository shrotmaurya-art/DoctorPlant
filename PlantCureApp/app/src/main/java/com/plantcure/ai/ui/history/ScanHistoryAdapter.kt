package com.plantcure.ai.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.plantcure.ai.R
import com.plantcure.ai.data.local.entity.ScanHistory
import com.plantcure.ai.databinding.ItemScanHistoryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanHistoryAdapter(
    private val onItemClick: (ScanHistory) -> Unit
) : ListAdapter<ScanHistory, ScanHistoryAdapter.ScanViewHolder>(ScanDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        val binding = ItemScanHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ScanViewHolder(private val binding: ItemScanHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(scan: ScanHistory) {
            binding.tvDiseaseName.text = scan.diseaseName
            binding.tvCropName.text = scan.cropName
            binding.chipSeverity.text = scan.severityLevel.uppercase()

            val colorRes = when (scan.severityLevel.lowercase()) {
                "high" -> R.color.severity_high
                "medium" -> R.color.severity_medium
                else -> R.color.severity_low
            }
            binding.chipSeverity.setChipBackgroundColorResource(colorRes)

            val formatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
            binding.tvDate.text = formatter.format(Date(scan.timestamp))

            Glide.with(itemView.context)
                .load(File(scan.imagePath))
                .centerCrop()
                .into(binding.ivThumbnail)

            itemView.setOnClickListener { onItemClick(scan) }

            // Cascade slide in
            itemView.translationX = 300f
            itemView.alpha = 0f
            itemView.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(400)
                .setStartDelay((bindingAdapterPosition * 50).toLong())
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    class ScanDiffCallback : DiffUtil.ItemCallback<ScanHistory>() {
        override fun areItemsTheSame(oldItem: ScanHistory, newItem: ScanHistory) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ScanHistory, newItem: ScanHistory) = oldItem == newItem
    }
}
