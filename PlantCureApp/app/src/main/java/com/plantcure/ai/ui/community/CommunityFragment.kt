package com.plantcure.ai.ui.community

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentCommunityBinding
import com.plantcure.ai.databinding.ItemCommunityFeedBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Community Disease Feed — anonymous disease reports + heatmap.
 */
@AndroidEntryPoint
class CommunityFragment : Fragment() {
    private var _binding: FragmentCommunityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentCommunityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCommunityFeed.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCommunityFeed.adapter = CommunityAdapter(getDummyData())
        
        binding.fabShareDetection.setOnClickListener {
            // Placeholder
            android.widget.Toast.makeText(requireContext(), "Share dialog...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDummyData(): List<CommunityPost> {
        return listOf(
            CommunityPost("Tomato Late Blight", "Tomato", "High", "Nashik District, Maharashtra", "12 km away", "2 hours ago"),
            CommunityPost("Wheat Rust", "Wheat", "Medium", "Nashik District, Maharashtra", "8 km away", "5 hours ago"),
            CommunityPost("Aphid Infestation", "Cotton", "Low", "Nashik District, Maharashtra", "15 km away", "1 day ago"),
            CommunityPost("Downy Mildew", "Grape", "High", "Nashik District, Maharashtra", "3 km away", "3 hours ago"),
            CommunityPost("Bacterial Wilt", "Brinjal", "Medium", "Nashik District, Maharashtra", "22 km away", "6 hours ago")
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

data class CommunityPost(
    val disease: String, val crop: String, val severity: String,
    val location: String, val distance: String, val time: String
)

class CommunityAdapter(private val items: List<CommunityPost>) :
    RecyclerView.Adapter<CommunityAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemCommunityFeedBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemCommunityFeedBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvDiseaseName.text = item.disease
        holder.binding.tvCropName.text = "Crop: ${item.crop}"
        holder.binding.chipSeverity.text = item.severity
        holder.binding.tvLocation.text = item.location
        holder.binding.tvDistance.text = item.distance
        holder.binding.tvTime.text = item.time

        when (item.severity.lowercase()) {
            "high" -> {
                holder.binding.chipSeverity.setTextColor(Color.parseColor("#93000A"))
                // Background color change not supported directly via textview without drawable mutation,
                // but we can set background tint on newer APIs.
                holder.binding.chipSeverity.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFDAD6"))
            }
            "medium" -> {
                holder.binding.chipSeverity.setTextColor(Color.parseColor("#723600"))
                holder.binding.chipSeverity.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFDCC7"))
            }
            "low" -> {
                holder.binding.chipSeverity.setTextColor(Color.parseColor("#00511D"))
                holder.binding.chipSeverity.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#D1E9D2"))
            }
        }
    }

    override fun getItemCount() = items.size
}
