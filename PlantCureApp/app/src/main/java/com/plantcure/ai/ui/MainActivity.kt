package com.plantcure.ai.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import android.view.Gravity
import android.transition.Slide
import com.plantcure.ai.R
import com.plantcure.ai.databinding.ActivityMainBinding
import com.plantcure.ai.domain.classifier.PlantDiseaseClassifier
import com.plantcure.ai.domain.tts.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity host for all primary navigation destinations.
 *
 * Uses Jetpack Navigation Component with bottom navigation:
 *   Home → History → Market → Community
 *
 * The TFLite classifier and TTS engine are initialized here (warm start).
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var classifier: PlantDiseaseClassifier

    @Inject
    lateinit var ttsManager: TTSManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.enterTransition = Slide(Gravity.BOTTOM).apply {
            duration = 300
        }

        // Warm up the TFLite model on a background thread
        Thread { classifier.initialize() }.start()

        // Initialize TTS engine
        ttsManager.initialize()

        setupNavigation()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't close classifier here — it's a singleton managed by Hilt
    }
}

