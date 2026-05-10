package com.plantcure.ai.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.plantcure.ai.R
import com.plantcure.ai.databinding.ActivitySettingsBinding
import com.plantcure.ai.domain.tts.TTSManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Settings screen matching the Stitch design.
 *
 * Features:
 *  - 7-language selector (English, Hindi, Marathi, Telugu, Tamil, Punjabi, Kannada)
 *  - TTS enable/disable toggle + speech speed slider
 *  - Notification toggles (Weather, Treatment, Community, Market)
 *  - Farmer profile display
 */
@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    @Inject
    lateinit var ttsManager: TTSManager

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("plantcure_settings", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_NOTIF_WEATHER = "notif_weather"
        private const val KEY_NOTIF_TREATMENT = "notif_treatment"
        private const val KEY_NOTIF_COMMUNITY = "notif_community"
        private const val KEY_NOTIF_MARKET = "notif_market"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupLanguageSelector()
        setupVoiceOutput()
        setupNotifications()
        setupVersion()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ── Language ─────────────────────────────────────────────────────────

    private fun setupLanguageSelector() {
        val currentLang = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"

        val langToRadio = mapOf(
            "en" to binding.rbEnglish,
            "hi" to binding.rbHindi,
            "mr" to binding.rbMarathi,
            "te" to binding.rbTelugu,
            "ta" to binding.rbTamil,
            "pa" to binding.rbPunjabi,
            "kn" to binding.rbKannada
        )

        // Set current selection
        langToRadio[currentLang]?.isChecked = true

        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val langCode = when (checkedId) {
                R.id.rbHindi -> "hi"
                R.id.rbMarathi -> "mr"
                R.id.rbTelugu -> "te"
                R.id.rbTamil -> "ta"
                R.id.rbPunjabi -> "pa"
                R.id.rbKannada -> "kn"
                else -> "en"
            }

            prefs.edit().putString(KEY_APP_LANGUAGE, langCode).apply()
            ttsManager.setLanguage(langCode)
            updateLocale(langCode)
        }
    }

    private fun updateLocale(langCode: String) {
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        // Recreate to apply language change
        recreate()
    }

    // ── Voice Output ────────────────────────────────────────────────────

    private fun setupVoiceOutput() {
        binding.switchTTS.isChecked = ttsManager.isEnabled()
        binding.sliderSpeed.value = ttsManager.getSpeed()

        binding.switchTTS.setOnCheckedChangeListener { _, isChecked ->
            ttsManager.setEnabled(isChecked)
            binding.sliderSpeed.isEnabled = isChecked
        }

        binding.sliderSpeed.isEnabled = ttsManager.isEnabled()
        binding.sliderSpeed.addOnChangeListener(Slider.OnChangeListener { _, value, fromUser ->
            if (fromUser) {
                ttsManager.setSpeed(value)
            }
        })
    }

    // ── Notifications ───────────────────────────────────────────────────

    private fun setupNotifications() {
        binding.cbWeather.isChecked = prefs.getBoolean(KEY_NOTIF_WEATHER, true)
        binding.cbTreatment.isChecked = prefs.getBoolean(KEY_NOTIF_TREATMENT, true)
        binding.cbCommunity.isChecked = prefs.getBoolean(KEY_NOTIF_COMMUNITY, true)
        binding.cbMarket.isChecked = prefs.getBoolean(KEY_NOTIF_MARKET, false)

        binding.cbWeather.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIF_WEATHER, isChecked).apply()
        }
        binding.cbTreatment.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIF_TREATMENT, isChecked).apply()
        }
        binding.cbCommunity.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIF_COMMUNITY, isChecked).apply()
        }
        binding.cbMarket.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_NOTIF_MARKET, isChecked).apply()
        }
    }

    // ── Version ─────────────────────────────────────────────────────────

    private fun setupVersion() {
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersion.text = getString(R.string.app_version, versionName)
        } catch (_: Exception) {
            binding.tvVersion.text = getString(R.string.app_version, "1.0")
        }
    }
}
