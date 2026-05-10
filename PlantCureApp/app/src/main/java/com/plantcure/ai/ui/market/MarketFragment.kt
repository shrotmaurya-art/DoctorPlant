package com.plantcure.ai.ui.market

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentMarketBinding
import com.plantcure.ai.databinding.ItemMarketPriceBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * Market Prices Tracker — mandi prices.
 */
@AndroidEntryPoint
class MarketFragment : Fragment() {
    private var _binding: FragmentMarketBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentMarketBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvMarket.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMarket.adapter = MarketAdapter(getDummyData())
        
        binding.fabNotifications.setOnClickListener {
            // Placeholder
            android.widget.Toast.makeText(requireContext(), "Price Alerts Setup...", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getDummyData(): List<MarketPrice> {
        return listOf(
            MarketPrice("Vashi APMC, Navi Mumbai", "Thane, Maharashtra", "↑ +8%", "₹1,200", "₹2,100", "₹2,800", "up"),
            MarketPrice("Pune APMC", "Pune, Maharashtra", "↓ -3%", "₹1,100", "₹1,950", "₹2,600", "down"),
            MarketPrice("Nashik APMC", "Nashik, Maharashtra", "↑ +5%", "₹1,300", "₹2,200", "₹2,900", "up"),
            MarketPrice("Nagpur APMC", "Nagpur, Maharashtra", "No change", "₹1,150", "₹2,050", "₹2,700", "flat")
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

data class MarketPrice(
    val name: String, val location: String, val trendText: String,
    val minPrice: String, val modalPrice: String, val maxPrice: String, val trendType: String
)

class MarketAdapter(private val items: List<MarketPrice>) :
    RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemMarketPriceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemMarketPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvMarketName.text = item.name
        holder.binding.tvLocation.text = item.location
        holder.binding.chipTrend.text = item.trendText
        holder.binding.tvMinPrice.text = item.minPrice
        holder.binding.tvModalPrice.text = item.modalPrice
        holder.binding.tvMaxPrice.text = item.maxPrice

        when (item.trendType) {
            "up" -> {
                holder.binding.chipTrend.setTextColor(Color.parseColor("#1B5E20"))
                holder.binding.chipTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C8E6C9"))
            }
            "down" -> {
                holder.binding.chipTrend.setTextColor(Color.parseColor("#B71C1C"))
                holder.binding.chipTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFCDD2"))
            }
            "flat" -> {
                holder.binding.chipTrend.setTextColor(Color.parseColor("#40493F"))
                holder.binding.chipTrend.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E0E4DB"))
            }
        }
    }

    override fun getItemCount() = items.size
}
