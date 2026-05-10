package com.plantcure.ai.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.plantcure.ai.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Extracted Camera screen for live viewfinder and scanning.
 */
@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    // We can reuse HomeViewModel since the logic is identical (processing images)
    private val viewModel: HomeViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // Camera permission request
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            android.widget.Toast.makeText(requireContext(), "Camera permission required.", android.widget.Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    // Gallery image picker
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
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnScan.setOnClickListener { capturePhoto() }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        viewModel.isProcessing.observe(viewLifecycleOwner) { isLoading ->
            binding.progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnScan.isEnabled = !isLoading
            binding.btnGallery.isEnabled = !isLoading
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.cameraPreview.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch (e2: Exception) {
                    android.widget.Toast.makeText(requireContext(), "Camera error.", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        viewModel.setProcessing(true)

        val photoFile = File(
            requireContext().filesDir,
            "scans/${UUID.randomUUID()}.jpg"
        ).also { it.parentFile?.mkdirs() }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    lifecycleScope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            BitmapFactory.decodeFile(photoFile.absolutePath)?.let {
                                downscaleBitmap(it, 1024)
                            }
                        }
                        bitmap?.let { processAndNavigate(it, photoFile.absolutePath) }
                            ?: viewModel.setProcessing(false)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    viewModel.setProcessing(false)
                    requireActivity().runOnUiThread {
                        android.widget.Toast.makeText(requireContext(), "Failed to save photo.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun processAndNavigate(bitmap: Bitmap, existingPath: String? = null) {
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    viewModel.classify(bitmap)
                }

                if (results.isNotEmpty()) {
                    val top = results.first()
                    val imagePath = existingPath ?: withContext(Dispatchers.IO) {
                        val file = File(requireContext().filesDir, "scans/${UUID.randomUUID()}.jpg")
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it)
                        }
                        file.absolutePath
                    }

                    // We use CameraFragmentDirections once it's generated by SafeArgs
                    val action = CameraFragmentDirections.actionCameraToResult(
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
        cameraExecutor.shutdown()
        _binding = null
    }
}
