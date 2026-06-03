package com.plantcure.ai.ui.chat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

import com.plantcure.ai.R

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        
        val diseaseName = intent.getStringExtra("disease_name") ?: "Unknown Disease"
        val cropName = intent.getStringExtra("crop_name") ?: "Unknown Crop"
        val severity = intent.getStringExtra("severity") ?: "Unknown"

        android.util.Log.d("Chat", "Disease: $diseaseName")
        android.util.Log.d("Chat", "Crop: $cropName")
        android.util.Log.d("Chat", "Severity: $severity")
        
        window.enterTransition = android.transition.Slide(android.view.Gravity.BOTTOM).apply {
            duration = 300
        }
    }
}
