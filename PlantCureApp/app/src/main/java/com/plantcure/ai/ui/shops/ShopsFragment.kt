package com.plantcure.ai.ui.shops

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.databinding.FragmentShopsBinding
import com.plantcure.ai.databinding.ItemShopBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShopsFragment : Fragment() {
    private var _binding: FragmentShopsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentShopsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.rvShops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvShops.adapter = ShopAdapter(getDummyData())
    }

    private fun getDummyData(): List<Shop> {
        return listOf(
            Shop("Shree Krishi Kendra", "Pesticide & Fertilizer", "1.2 km away", "4.3"),
            Shop("Bharat Seed Store", "Quality Seeds & Tools", "0.8 km away", "4.7"),
            Shop("Kisan Seva Center", "Irrigation & Fertilizers", "2.4 km away", "4.0"),
            Shop("Green Earth Agri", "Organic Pesticides", "3.1 km away", "4.5")
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

data class Shop(
    val name: String, val type: String, val distance: String, val rating: String
)

class ShopAdapter(private val items: List<Shop>) : RecyclerView.Adapter<ShopAdapter.ViewHolder>() {
    class ViewHolder(val binding: ItemShopBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvShopName.text = item.name
        holder.binding.tvShopType.text = item.type
        holder.binding.tvDistance.text = item.distance
        holder.binding.tvRating.text = item.rating
    }

    override fun getItemCount() = items.size
}
