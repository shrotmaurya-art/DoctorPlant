package com.plantcure.ai.ui.calendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.plantcure.ai.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CalendarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CalendarFragment())
                .commit()
        }
    }
}

