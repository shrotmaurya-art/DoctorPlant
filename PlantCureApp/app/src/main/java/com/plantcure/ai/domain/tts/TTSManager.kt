package com.plantcure.ai.domain.tts

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text-to-Speech manager for reading disease results aloud.
 *
 * Supports:
 *  - English and Hindi voice output
 *  - Configurable speech speed (0.5x – 2.0x)
 *  - Enable/disable via SharedPreferences
 *  - Pause, resume, and stop controls
 */
@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TTSManager"
        private const val PREFS_NAME = "plantcure_settings"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val DEFAULT_SPEED = 1.0f
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * Initialize the TTS engine. Call this early (e.g., in MainActivity.onCreate).
     */
    fun initialize() {
        if (tts != null) return

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val locale = getLocaleForLanguage(getLanguage())
                val result = tts?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "Language not supported: $locale, falling back to English")
                    tts?.setLanguage(Locale.US)
                }
                tts?.setSpeechRate(getSpeed())
                setupListener()
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                onDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onErrorCallback?.invoke("TTS error for utterance: $utteranceId")
            }
        })
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Speak the given text. Stops any currently playing speech first.
     */
    fun speak(text: String, utteranceId: String = "plantcure_result") {
        if (!isEnabled() || !isInitialized) return

        tts?.let { engine ->
            engine.stop()
            engine.setLanguage(getLocaleForLanguage(getLanguage()))
            engine.setSpeechRate(getSpeed())
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    /**
     * Stop any current speech.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Check if TTS is currently speaking.
     */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    /**
     * Release TTS resources. Call this in onDestroy of the host Activity.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    // ── Settings ────────────────────────────────────────────────────────

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_TTS_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        if (!enabled) stop()
    }

    fun getSpeed(): Float = prefs.getFloat(KEY_TTS_SPEED, DEFAULT_SPEED)

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        prefs.edit().putFloat(KEY_TTS_SPEED, clamped).apply()
        tts?.setSpeechRate(clamped)
    }

    fun getLanguage(): String = prefs.getString(KEY_APP_LANGUAGE, "en") ?: "en"

    fun setLanguage(langCode: String) {
        prefs.edit().putString(KEY_APP_LANGUAGE, langCode).apply()
        tts?.setLanguage(getLocaleForLanguage(langCode))
    }

    // ── Callbacks ───────────────────────────────────────────────────────

    fun setOnStartCallback(callback: () -> Unit) { onStartCallback = callback }
    fun setOnDoneCallback(callback: () -> Unit) { onDoneCallback = callback }
    fun setOnErrorCallback(callback: (String) -> Unit) { onErrorCallback = callback }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun getLocaleForLanguage(langCode: String): Locale {
        return when (langCode) {
            "hi" -> Locale("hi", "IN")
            "mr" -> Locale("mr", "IN")
            "te" -> Locale("te", "IN")
            "ta" -> Locale("ta", "IN")
            "pa" -> Locale("pa", "IN")
            "kn" -> Locale("kn", "IN")
            else -> Locale.US
        }
    }
}
