package com.plantcure.ai.ui.map

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.plantcure.ai.R
import com.plantcure.ai.data.repository.ScanHistoryRepository
import com.plantcure.ai.databinding.ActivityFieldMapBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Field Map Activity using OSMDroid (OpenStreetMap).
 * Shows all scan history entries as colored markers on the map.
 * No API key required — completely free.
 */
@AndroidEntryPoint
class FieldMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFieldMapBinding

    @Inject
    lateinit var historyRepository: ScanHistoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OSMDroid configuration
        Configuration.getInstance().userAgentValue = packageName

        binding = ActivityFieldMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupMap()
        loadScanMarkers()
    }

    private fun setupMap() {
        binding.osmMapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.osmMapView.setMultiTouchControls(true)
        binding.osmMapView.controller.setZoom(6.0)
        
        // Try to get actual location
        val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    binding.osmMapView.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                    binding.osmMapView.controller.setZoom(12.0)
                }
            }
        }
    }

    private fun loadScanMarkers() {
        lifecycleScope.launch {
            val scans = historyRepository.getAllScans().firstOrNull() ?: emptyList()
            val geotaggedScans = scans.filter { it.latitude != null && it.longitude != null }

            val listToPlot = geotaggedScans
            
            if (listToPlot.isEmpty() && scans.isNotEmpty()) {
                android.widget.Toast.makeText(this@FieldMapActivity, "No geotagged scans found.", android.widget.Toast.LENGTH_LONG).show()
            }

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            for (scan in listToPlot) {
                val lat = scan.latitude ?: continue
                val lon = scan.longitude ?: continue
                val point = GeoPoint(lat, lon)

                val markerColor = when (scan.severityLevel.lowercase()) {
                    "high" -> Color.parseColor("#B71C1C")
                    "medium" -> Color.parseColor("#F57F17")
                    else -> Color.parseColor("#2E7D32")
                }

                val marker = Marker(binding.osmMapView)
                marker.position = point
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = scan.diseaseName
                marker.snippet = "${dateFormat.format(Date(scan.timestamp))} — ${scan.severityLevel} Severity"
                marker.icon = createCircleMarkerDrawable(markerColor)

                binding.osmMapView.overlays.add(marker)
            }

            if (listToPlot.isNotEmpty()) {
                val first = listToPlot.first()
                val target = GeoPoint(first.latitude ?: 0.0, first.longitude ?: 0.0)
                binding.osmMapView.controller.animateTo(target)
                binding.osmMapView.controller.setZoom(12.0)
            }

            binding.osmMapView.invalidate()
        }
    }

    /**
     * Create a simple colored circle drawable for map markers.
     */
    private fun createCircleMarkerDrawable(color: Int): android.graphics.drawable.Drawable {
        val size = (24 * resources.displayMetrics.density).toInt()
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.OVAL
        drawable.setColor(color)
        drawable.setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
        drawable.setSize(size, size)
        return drawable
    }

    override fun onResume() {
        super.onResume()
        binding.osmMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.osmMapView.onPause()
    }
}
