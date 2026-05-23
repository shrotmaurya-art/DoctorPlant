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
        window.enterTransition = android.transition.Slide(android.view.Gravity.BOTTOM).apply {
            duration = 300
        }
    }
}
