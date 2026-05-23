package com.plantcure.ai.ui.market

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.content.res.ColorStateList
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.plantcure.ai.data.local.entity.MarketPrice
import com.plantcure.ai.databinding.FragmentMarketBinding
import com.plantcure.ai.databinding.ItemMarketPriceBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.plantcure.ai.R

/**
 * Market Prices Tracker — mandi prices with state & district selection.
 */
@AndroidEntryPoint
class MarketFragment : Fragment() {
    private var _binding: FragmentMarketBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MarketViewModel by viewModels()
    private lateinit var adapter: MarketAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentMarketBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val locationPermissionRequest = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            autoLocateAndFetch()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupChips()
        setupLocationDropdowns()
        observeData()

        binding.btnAutoLocate.setOnClickListener {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                autoLocateAndFetch()
            } else {
                locationPermissionRequest.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun autoLocateAndFetch() {
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(requireActivity())
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                try {
                    val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val state = addresses[0].adminArea
                        val subAdminArea = addresses[0].subAdminArea // usually the district
                        val locality = addresses[0].locality
                        val rawDistrict = subAdminArea ?: locality ?: ""
                        
                        // Normalize GPS district name → Agmarknet district name
                        val district = com.plantcure.ai.data.repository.DistrictNameMapper.normalize(rawDistrict)
                        android.util.Log.d("PlantCure_Market", "GPS location: state=$state, rawDistrict=$rawDistrict, normalized=$district")
                        
                        if (state != null) {
                            binding.actvState.setText(state, false)
                            viewModel.selectState(state)
                            updateDistrictDropdown(state)
                            
                            val districts = viewModel.getDistrictsForState(state)
                            // Find best matching district using normalized name
                            val matchedDistrict = districts.find { d ->
                                district.contains(d, ignoreCase = true) || d.contains(district, ignoreCase = true)
                            }
                            
                            if (matchedDistrict != null) {
                                binding.actvDistrict.setText(matchedDistrict, false)
                                viewModel.selectDistrict(matchedDistrict)
                            } else {
                                binding.actvDistrict.setText("", false)
                                viewModel.selectDistrict(null)
                                android.widget.Toast.makeText(requireContext(), "Showing state-level prices — district data unavailable.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = MarketAdapter()
        binding.rvMarketPrices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMarketPrices.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshPrices()
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_price_high -> {
                    viewModel.setSortOption(MarketViewModel.SortOption.PRICE_HIGH)
                    true
                }
                R.id.sort_price_low -> {
                    viewModel.setSortOption(MarketViewModel.SortOption.PRICE_LOW)
                    true
                }
                R.id.sort_name -> {
                    viewModel.setSortOption(MarketViewModel.SortOption.NAME)
                    true
                }
                R.id.sort_none -> {
                    viewModel.setSortOption(MarketViewModel.SortOption.NONE)
                    true
                }
                else -> false
            }
        }
    }



    private fun setupChips() {
        binding.chipGroupCrops.translationX = -200f
        binding.chipGroupCrops.alpha = 0f
        binding.chipGroupCrops.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        binding.chipGroupCrops.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedIds.first())
                val commodity = chip.text.toString()
                viewModel.selectCommodity(commodity)
            }
        }
    }

    private fun setupLocationDropdowns() {
        // State dropdown
        val stateAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            viewModel.statesList
        )
        binding.actvState.setAdapter(stateAdapter)
        binding.actvState.setText("Maharashtra", false)

        binding.actvState.setOnItemClickListener { _, _, position, _ ->
            val selectedState = viewModel.statesList[position]
            viewModel.selectState(selectedState)

            // Update district dropdown for new state
            updateDistrictDropdown(selectedState)
        }

        // Initialize district dropdown for default state
        updateDistrictDropdown("Maharashtra")

        binding.actvDistrict.setOnItemClickListener { _, _, position, _ ->
            val districts = viewModel.getDistrictsForState(viewModel.selectedState.value)
            val selectedDistrict = districts[position]
            if (selectedDistrict == "All Districts") {
                viewModel.selectDistrict(null)
            } else {
                viewModel.selectDistrict(selectedDistrict)
            }
        }
    }

    private fun updateDistrictDropdown(state: String) {
        val districts = viewModel.getDistrictsForState(state)
        val districtAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            districts
        )
        binding.actvDistrict.setAdapter(districtAdapter)
        binding.actvDistrict.setText("", false) // Reset district selection
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.prices.collect { prices ->
                        adapter.submitList(prices)
                        
                        // Handle Empty State
                        if (prices.isEmpty() && !viewModel.isRefreshing.value) {
                            binding.emptyStateLayout.visibility = View.VISIBLE
                            binding.rvMarketPrices.visibility = View.GONE
                        } else {
                            binding.emptyStateLayout.visibility = View.GONE
                            binding.rvMarketPrices.visibility = View.VISIBLE
                            // Trigger layout animation for sorting/updating
                            binding.rvMarketPrices.scheduleLayoutAnimation()
                        }
                    }
                }

                launch {
                    viewModel.isRefreshing.collect { isRefreshing ->
                        binding.swipeRefresh.isRefreshing = isRefreshing
                        if (isRefreshing && adapter.itemCount == 0) {
                            binding.shimmerLayout.visibility = View.VISIBLE
                            binding.shimmerLayout.startShimmer()
                            binding.emptyStateLayout.visibility = View.GONE
                        } else {
                            binding.shimmerLayout.stopShimmer()
                            binding.shimmerLayout.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.refreshFailed.collect { failed ->
                        if (failed && adapter.itemCount == 0) {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Unable to load prices. Check API key or internet.",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
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

class MarketAdapter : RecyclerView.Adapter<MarketAdapter.ViewHolder>() {

    private var items: List<MarketPrice> = emptyList()

    class ViewHolder(val binding: ItemMarketPriceBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitList(newItems: List<MarketPrice>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemMarketPriceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvMarketName.text = item.market
        holder.binding.tvLocation.text = "${item.district}, ${item.state}"
        holder.binding.tvMinPrice.text = "₹${item.minPrice.toInt()}"
        holder.binding.tvModalPrice.text = "₹${item.modalPrice.toInt()}"
        holder.binding.tvMaxPrice.text = "₹${item.maxPrice.toInt()}"

        val trendText: String
        val textColor: Int
        val bgColor: Int

        when (item.trend) {
            "up" -> {
                trendText = "↑ Rising"
                textColor = Color.parseColor("#1B5E20")
                bgColor = Color.parseColor("#C8E6C9")
            }
            "down" -> {
                trendText = "↓ Falling"
                textColor = Color.parseColor("#B71C1C")
                bgColor = Color.parseColor("#FFCDD2")
            }
            else -> {
                trendText = "— Stable"
                textColor = Color.parseColor("#40493F")
                bgColor = Color.parseColor("#E0E4DB")
            }
        }

        holder.binding.chipTrend.text = trendText
        holder.binding.chipTrend.setTextColor(textColor)
        holder.binding.chipTrend.backgroundTintList = ColorStateList.valueOf(bgColor)

        // Staggered Slide up
        holder.itemView.translationY = 150f
        holder.itemView.alpha = 0f
        holder.itemView.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(400)
            .setStartDelay((position * 80).toLong())
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Count up prices
        animatePriceUp(holder.binding.tvMinPrice, item.minPrice.toInt())
        animatePriceUp(holder.binding.tvModalPrice, item.modalPrice.toInt())
        animatePriceUp(holder.binding.tvMaxPrice, item.maxPrice.toInt())

        // Trend badge pulse
        holder.binding.chipTrend.scaleX = 0f
        holder.binding.chipTrend.scaleY = 0f
        holder.binding.chipTrend.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setStartDelay((position * 80 + 200).toLong())
            .setInterpolator(android.view.animation.OvershootInterpolator(2f))
            .start()
    }

    private fun animatePriceUp(textView: android.widget.TextView, finalValue: Int) {
        val animator = android.animation.ValueAnimator.ofInt(0, finalValue)
        animator.duration = 1000
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener {
            textView.text = "₹${it.animatedValue}"
        }
        animator.start()
    }

    override fun getItemCount() = items.size
}
