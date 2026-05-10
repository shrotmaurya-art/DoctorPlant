package com.plantcure.ai.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayoutMediator
import com.plantcure.ai.R
import com.plantcure.ai.databinding.FragmentResultBinding
import com.plantcure.ai.domain.tts.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

/**
 * Disease Result screen — displays detection results with 4-tab detail view.
 *
 * Phase 4 additions:
 *  - TTS listen FAB reads the disease summary aloud
 *  - Share FAB sends disease info via Android share sheet
 */
@AndroidEntryPoint
class ResultFragment : Fragment() {
    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResultViewModel by viewModels()
    private val args: ResultFragmentArgs by navArgs()

    @Inject
    lateinit var ttsManager: TTSManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupTTS()
        setupShare()
        observeData()

        // Load data and auto-save if new scan
        viewModel.loadResult(args.imagePath, args.diseaseLabel, args.confidence, args.scanId)
    }

    private fun setupUI() {
        // Load scanned image
        Glide.with(this)
            .load(File(args.imagePath))
            .centerCrop()
            .into(binding.ivScannedImage)

        // Setup ViewPager2 and TabLayout
        val pagerAdapter = ResultPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_about)
                1 -> getString(R.string.tab_symptoms)
                2 -> getString(R.string.tab_prevention)
                3 -> getString(R.string.tab_market)
                else -> ""
            }
        }.attach()
    }

    private fun setupTTS() {
        binding.fabListen.setOnClickListener {
            if (ttsManager.isSpeaking()) {
                ttsManager.stop()
                binding.fabListen.setImageResource(R.drawable.ic_volume_up)
            } else {
                val disease = viewModel.disease.value
                if (disease != null) {
                    val text = buildString {
                        append("${disease.name}. ")
                        append(disease.about)
                        append(". Symptoms: ")
                        disease.symptoms.forEachIndexed { i, s ->
                            append("${i + 1}. $s. ")
                        }
                        append("Organic treatment: ")
                        disease.preventionOrganic.forEach { append("$it. ") }
                    }
                    ttsManager.speak(text)
                    // We can't easily change icon to "stop" with vector, but
                    // the FAB feedback is still intuitive (tap again to stop)
                }
            }
        }
    }

    private fun setupShare() {
        binding.fabShare.setOnClickListener {
            val disease = viewModel.disease.value
            val result = viewModel.detectionResult.value
            if (disease != null && result != null) {
                val shareText = buildString {
                    appendLine("🌿 PlantCure AI — Disease Detection Report")
                    appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    appendLine("🔬 Disease: ${disease.name}")
                    appendLine("🌾 Crop: ${disease.affectedCrop}")
                    appendLine("📊 Confidence: ${(result.confidence * 100).toInt()}%")
                    appendLine("⚠ Severity: ${result.severity.uppercase()}")
                    appendLine()
                    appendLine("📋 Symptoms:")
                    disease.symptoms.forEach { appendLine("  • $it") }
                    appendLine()
                    appendLine("🧪 Organic Treatment:")
                    disease.preventionOrganic.forEach { appendLine("  • $it") }
                    appendLine()
                    appendLine("Detected by PlantCure AI 🌿")
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "PlantCure AI: ${disease.name} Detection")
                }
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_result)))
            }
        }
    }

    private fun observeData() {
        viewModel.detectionResult.observe(viewLifecycleOwner) { result ->
            // Confidence Bar
            binding.tvConfidenceValue.text = "${(result.confidence * 100).toInt()}%"
            binding.progressConfidence.progress = (result.confidence * 100).toInt()

            // Severity Chip
            binding.chipSeverity.text = "${result.severity.uppercase()} SEVERITY"
            val colorRes = when (result.severity.lowercase()) {
                "high" -> R.color.severity_high
                "medium" -> R.color.severity_medium
                else -> R.color.severity_low
            }
            binding.chipSeverity.setChipBackgroundColorResource(colorRes)
        }

        viewModel.disease.observe(viewLifecycleOwner) { disease ->
            binding.tvDiseaseName.text = disease?.name ?: args.diseaseLabel
            binding.tvCropName.text = disease?.affectedCrop ?: ""
        }
    }

    override fun onDestroyView() {
        ttsManager.stop()
        super.onDestroyView()
        _binding = null
    }
}
