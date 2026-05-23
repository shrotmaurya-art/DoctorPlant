package com.plantcure.ai.ui.shop

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.plantcure.ai.R
import com.plantcure.ai.ui.shops.ShopsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ShopLocatorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_placeholder)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ShopsFragment())
                .commit()
        }
    }
}
