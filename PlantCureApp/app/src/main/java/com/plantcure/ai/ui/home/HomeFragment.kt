package com.plantcure.ai.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.plantcure.ai.R
import com.plantcure.ai.databinding.FragmentHomeBinding
import com.plantcure.ai.domain.model.RiskLevel
import com.plantcure.ai.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Dashboard Home Screen with weather, history, and actions.
 */
@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fetchLocationAndLoadWeather()
        } else {
            android.widget.Toast.makeText(requireContext(), "Location permission denied. Cannot fetch weather.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Gallery image picker for "Upload Photo" card
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    val stream = requireContext().contentResolver.openInputStream(imageUri)
                    BitmapFactory.decodeStream(stream)?.let { original ->
                        downscaleBitmap(original, 1024)
                    }
                }
                bitmap?.let { processAndNavigate(it) }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        // Navigation actions
        binding.fabScan.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToCamera())
        }

        binding.cardUpload.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.cardHistory.setOnClickListener {
            findNavController().navigate(com.plantcure.ai.R.id.historyFragment)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        binding.fabChat.setOnClickListener {
            startActivity(Intent(requireContext(), com.plantcure.ai.ui.chat.ChatActivity::class.java))
        }

        // Bouncy click animations
        addBouncyClick(binding.fabScan)
        addBouncyClick(binding.cardUpload)
        addBouncyClick(binding.cardHistory)
        addBouncyClick(binding.btnSettings)
        addBouncyClick(binding.fabChat)

        viewModel.isProcessing.observe(viewLifecycleOwner) { isLoading ->
            binding.fabScan.isEnabled = !isLoading
            binding.cardUpload.isEnabled = !isLoading
        }

        observeWeather()
        observeNextTreatment()
        requestLocationAndLoad()
        
        // Custom Animations
        setupAnimations()
    }

    private fun setupAnimations() {
        // 1. Camera scan button — pulsing glow ring
        val pulseAnimator = android.animation.ObjectAnimator.ofPropertyValuesHolder(
            binding.heroButtonContainer,
            android.animation.PropertyValuesHolder.ofFloat("scaleX", 1f, 1.08f, 1f),
            android.animation.PropertyValuesHolder.ofFloat("scaleY", 1f, 1.08f, 1f)
        )
        pulseAnimator.duration = 1500
        pulseAnimator.repeatCount = android.animation.ValueAnimator.INFINITE
        pulseAnimator.start()

        // 2. Staggered fade and slide up for cards
        animateViewIn(binding.weatherRiskCard, 100)
        animateViewIn(binding.cardUpload, 200)
        animateViewIn(binding.cardHistory, 250)
    }

    private fun animateViewIn(view: View, delay: Long) {
        view.alpha = 0f
        view.translationY = 100f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(delay)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun addBouncyClick(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNextTreatment()
    }

    private fun observeWeather() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.weatherData.collectLatest { weather ->
                    weather?.let { data ->
                        // City name
                        binding.weatherCityName.text = data.cityName
                        
                        // Temperature
                        binding.weatherTemp.text = "${data.temperature.toInt()}°C"
                        
                        // Humidity
                        binding.weatherHumidity.text = "💧 ${data.humidity}%"
                        
                        // Risk message
                        binding.tvWeatherRiskMsg.text = data.riskMessage

                        // Risk badge
                        binding.chipRisk.text = data.riskLevel.name
                        val (bgColor, textColor, icon) = when (data.riskLevel) {
                            RiskLevel.HIGH -> Triple(R.color.severity_high_bg, R.color.severity_high, R.drawable.ic_warning)
                            RiskLevel.MEDIUM -> Triple(R.color.severity_medium_bg, R.color.severity_medium, R.drawable.ic_warning)
                            RiskLevel.LOW -> Triple(R.color.severity_low_bg, R.color.severity_low, R.drawable.ic_check_circle)
                        }
                        
                        binding.chipRisk.backgroundTintList = ContextCompat.getColorStateList(requireContext(), bgColor)
                        binding.chipRisk.setTextColor(ContextCompat.getColor(requireContext(), textColor))
                        
                        val iconDrawable = ContextCompat.getDrawable(requireContext(), icon)
                        iconDrawable?.setTint(ContextCompat.getColor(requireContext(), textColor))
                        binding.chipRisk.setCompoundDrawablesWithIntrinsicBounds(iconDrawable, null, null, null)
                        binding.chipRisk.compoundDrawablePadding = (4 * resources.displayMetrics.density).toInt()
                        
                        // Weather icon based on condition
                        val weatherIconRes = when (data.condition) {
                            "Rain", "Drizzle" -> R.drawable.ic_water_drop
                            "Clouds" -> R.drawable.ic_water_drop
                            "Clear" -> R.drawable.ic_water_drop
                            "Thunderstorm" -> R.drawable.ic_warning
                            else -> R.drawable.ic_water_drop
                        }
                        binding.weatherIcon.setImageResource(weatherIconRes)
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
            fetchLocationAndLoadWeather()
        } else {
            locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @Suppress("MissingPermission")
    private fun fetchLocationAndLoadWeather() {
        fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    viewModel.loadWeather(location.latitude, location.longitude)
                } else {
                    android.widget.Toast.makeText(requireContext(), "Location not found. Enable GPS.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                android.widget.Toast.makeText(requireContext(), "Failed to get location. Enable GPS.", android.widget.Toast.LENGTH_LONG).show()
            }
    }


    private fun processAndNavigate(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                viewModel.setProcessing(true)
                val results = withContext(Dispatchers.IO) {
                    viewModel.classify(bitmap)
                }

                if (results.isNotEmpty()) {
                    val top = results.first()
                    
                    if (top.label == "not_a_plant") {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                            .setTitle("No Plant Detected")
                            .setMessage("Please point the camera directly at a plant leaf in good lighting.")
                            .setPositiveButton("OK", null)
                            .show()
                        return@launch
                    }

                    val imagePath = withContext(Dispatchers.IO) {
                        val file = File(requireContext().filesDir, "scans/${UUID.randomUUID()}.jpg")
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
                        }
                        file.absolutePath
                    }

                    val action = HomeFragmentDirections.actionHomeToResult(
                        imagePath = imagePath,
                        diseaseLabel = top.label,
                        confidence = top.confidence
                    )
                    findNavController().navigate(action)
                } else {
                    android.widget.Toast.makeText(requireContext(), "No results.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                viewModel.setProcessing(false)
            }
        }
    }

    private fun downscaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / maxOf(width, height)
        return Bitmap.createScaledBitmap(bitmap, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    private fun observeNextTreatment() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nextTreatment.collectLatest { treatment ->
                    if (treatment != null) {
                        // binding.tvNextTreatmentAction.text = "Next: ${treatment.actionDescription}"
                    } else {
                        // binding.tvNextTreatmentAction.text = "No upcoming treatments scheduled."
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
