package com.plantcure.ai.ui.shops

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.plantcure.ai.domain.model.ShopResult
import com.plantcure.ai.databinding.FragmentShopsBinding
import com.plantcure.ai.databinding.ItemShopBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@AndroidEntryPoint
class ShopsFragment : Fragment() {
    private var _binding: FragmentShopsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShopsViewModel by viewModels()
    private lateinit var adapter: ShopAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationOverlay: MyLocationNewOverlay? = null

    // Store user location for re-centering
    private var userLat: Double = 0.0
    private var userLon: Double = 0.0

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocationAndLoadShops()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentShopsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        setupMap()
        setupRecyclerView()
        observeData()
        requestLocationAndLoad()
    }

    private fun setupMap() {
        org.osmdroid.config.Configuration.getInstance().load(
            requireContext(),
            android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(14.0)

        // Active location tracking overlay
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), binding.mapView)
        locationOverlay?.enableMyLocation()
        binding.mapView.overlays.add(locationOverlay)

        binding.btnLocateMe.setOnClickListener {
            if (userLat != 0.0 && userLon != 0.0) {
                binding.mapView.controller.animateTo(GeoPoint(userLat, userLon))
            }
            locationOverlay?.enableFollowLocation()
        }
    }

    private fun setupRecyclerView() {
        adapter = ShopAdapter(
            onDirectionsClick = { shop -> openDirections(shop) },
            onCallClick = { shop -> openDialer(shop) }
        )
        binding.rvShops.layoutManager = LinearLayoutManager(requireContext())
        binding.rvShops.adapter = adapter
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.shops.collect { shops ->
                        adapter.submitList(shops)
                        updateMapMarkers(shops)
                    }
                }
            }
        }
    }

    private fun requestLocationAndLoad() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationAndLoadShops()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @Suppress("MissingPermission")
    private fun fetchLocationAndLoadShops() {
        // Use getCurrentLocation for fresh GPS fix instead of lastLocation
        val cancellationToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                userLat = location.latitude
                userLon = location.longitude
                viewModel.loadNearby(userLat, userLon)
                binding.mapView.controller.animateTo(GeoPoint(userLat, userLon))
            } else {
                // Fallback: try lastLocation
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                    if (lastLoc != null) {
                        userLat = lastLoc.latitude
                        userLon = lastLoc.longitude
                        viewModel.loadNearby(userLat, userLon)
                        binding.mapView.controller.animateTo(GeoPoint(userLat, userLon))
                    } else {
                        android.widget.Toast.makeText(requireContext(), "Location not found. Please enable GPS.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.addOnFailureListener {
            android.widget.Toast.makeText(requireContext(), "Failed to get location. Please enable GPS.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun updateMapMarkers(shops: List<ShopResult>) {
        // Keep only the user location overlay, clear all shop markers
        val overlaysToKeep = binding.mapView.overlays.filterIsInstance<MyLocationNewOverlay>()
        binding.mapView.overlays.clear()
        binding.mapView.overlays.addAll(overlaysToKeep)

        for (shop in shops) {
            val shopMarker = Marker(binding.mapView)
            shopMarker.position = GeoPoint(shop.latitude, shop.longitude)
            shopMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            shopMarker.title = shop.name
            shopMarker.snippet = "${shop.address}\n⭐ ${shop.rating}"
            shopMarker.icon = ContextCompat.getDrawable(requireContext(), com.plantcure.ai.R.drawable.ic_shop_marker)

            // Custom info window with Directions and Call buttons
            shopMarker.infoWindow = ShopInfoWindow(binding.mapView, shop)

            binding.mapView.overlays.add(shopMarker)
        }
        binding.mapView.invalidate()
    }

    private fun openDirections(shop: ShopResult) {
        val uri = Uri.parse("google.navigation:q=${shop.latitude},${shop.longitude}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        intent.setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            // Fallback to browser
            val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${shop.latitude},${shop.longitude}")
            startActivity(Intent(Intent.ACTION_VIEW, browserUri))
        }
    }

    private fun openDialer(shop: ShopResult) {
        val phoneNumber = shop.phoneNumber
        if (!phoneNumber.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(intent)
        } else {
            // Search for the shop on Google to find contact info
            val searchUri = Uri.parse("https://www.google.com/search?q=${Uri.encode(shop.name + " " + shop.address)}")
            startActivity(Intent(Intent.ACTION_VIEW, searchUri))
        }
    }

    /**
     * Custom InfoWindow for shop markers showing name, address, rating,
     * and action buttons for Directions and Call.
     */
    inner class ShopInfoWindow(
        mapView: org.osmdroid.views.MapView,
        private val shop: ShopResult
    ) : InfoWindow(com.plantcure.ai.R.layout.popup_shop_marker, mapView) {

        override fun onOpen(item: Any?) {
            val view = mView ?: return

            view.findViewById<TextView>(com.plantcure.ai.R.id.tvPopupName)?.text = shop.name
            view.findViewById<TextView>(com.plantcure.ai.R.id.tvPopupAddress)?.text = shop.address
            view.findViewById<TextView>(com.plantcure.ai.R.id.tvPopupRating)?.text = "⭐ ${shop.rating}"

            view.findViewById<Button>(com.plantcure.ai.R.id.btnPopupDirections)?.setOnClickListener {
                openDirections(shop)
                close()
            }

            view.findViewById<Button>(com.plantcure.ai.R.id.btnPopupCall)?.setOnClickListener {
                openDialer(shop)
                close()
            }

            // Close on tap outside
            view.setOnClickListener { close() }
        }

        override fun onClose() {
            // No-op
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * RecyclerView adapter for shop list items.
     */
    class ShopAdapter(
        private val onDirectionsClick: (ShopResult) -> Unit,
        private val onCallClick: (ShopResult) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<ShopResult, ShopAdapter.ShopViewHolder>(ShopDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
            val binding = ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ShopViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        inner class ShopViewHolder(private val binding: ItemShopBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(shop: ShopResult) {
                binding.tvShopName.text = shop.name
                binding.tvShopAddress.text = shop.address
                binding.tvDistance.text = String.format("%.1f km", shop.distanceKm)
                binding.tvRating.text = shop.rating.toString()

                // Open/Closed badge
                when (shop.isOpen) {
                    true -> {
                        binding.tvOpenStatus.text = "Open"
                        binding.tvOpenStatus.setTextColor(
                            ContextCompat.getColor(binding.root.context, android.R.color.holo_green_dark)
                        )
                        binding.tvOpenStatus.visibility = View.VISIBLE
                    }
                    false -> {
                        binding.tvOpenStatus.text = "Closed"
                        binding.tvOpenStatus.setTextColor(
                            ContextCompat.getColor(binding.root.context, android.R.color.holo_red_dark)
                        )
                        binding.tvOpenStatus.visibility = View.VISIBLE
                    }
                    null -> {
                        binding.tvOpenStatus.visibility = View.GONE
                    }
                }

                binding.btnCall.setOnClickListener { onCallClick(shop) }
                binding.btnDirections.setOnClickListener { onDirectionsClick(shop) }

                // Cascade slide up
                itemView.translationY = 150f
                itemView.alpha = 0f
                itemView.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setStartDelay((bindingAdapterPosition * 80).toLong())
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
        }

        class ShopDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<ShopResult>() {
            override fun areItemsTheSame(oldItem: ShopResult, newItem: ShopResult) = oldItem.placeId == newItem.placeId
            override fun areContentsTheSame(oldItem: ShopResult, newItem: ShopResult) = oldItem == newItem
        }
    }
}
