package com.plantcure.ai.ui.result

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.plantcure.ai.ui.result.tabs.AboutTabFragment
import com.plantcure.ai.ui.result.tabs.MarketImpactTabFragment
import com.plantcure.ai.ui.result.tabs.PreventionTabFragment
import com.plantcure.ai.ui.result.tabs.SymptomsTabFragment

class ResultPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AboutTabFragment()
            1 -> SymptomsTabFragment()
            2 -> PreventionTabFragment()
            3 -> MarketImpactTabFragment()
            else -> throw IllegalArgumentException("Invalid tab position")
        }
    }
}
