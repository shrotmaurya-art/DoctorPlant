package com.plantcure.ai.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.plantcure.ai.databinding.FragmentHomeBinding
import com.plantcure.ai.ui.settings.SettingsActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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

        // Navigation actions
        binding.btnScan.setOnClickListener {
            findNavController().navigate(HomeFragmentDirections.actionHomeToCamera())
        }

        binding.cardUpload.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.cardHistory.setOnClickListener {
            // Navigate to History (assuming bottom nav handles it, or explicit navigation)
            findNavController().navigate(com.plantcure.ai.R.id.historyFragment)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        viewModel.isProcessing.observe(viewLifecycleOwner) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnScan.isEnabled = !isLoading
            binding.cardUpload.isEnabled = !isLoading
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
