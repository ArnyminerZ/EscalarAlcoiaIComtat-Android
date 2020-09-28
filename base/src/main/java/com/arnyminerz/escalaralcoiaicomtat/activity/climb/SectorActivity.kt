package com.arnyminerz.escalaralcoiaicomtat.activity.climb

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.*
import com.arnyminerz.escalaralcoiaicomtat.activity.model.NetworkChangeListenerFragmentActivity
import com.arnyminerz.escalaralcoiaicomtat.data.climb.data.Sector
import com.arnyminerz.escalaralcoiaicomtat.fragment.climb.SectorFragment
import com.arnyminerz.escalaralcoiaicomtat.generic.getExtra
import com.arnyminerz.escalaralcoiaicomtat.list.adapter.SectorPagerAdapter
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import it.sephiroth.android.library.xtooltip.ClosePolicy
import it.sephiroth.android.library.xtooltip.Tooltip
import kotlinx.android.synthetic.main.activity_sector.*
import timber.log.Timber

@ExperimentalUnsignedTypes
class SectorActivity : NetworkChangeListenerFragmentActivity() {
    private var transitionName: String? = null

    private var areaIndex = -1
    private var zoneIndex = -1
    var sector: Int = 0
    lateinit var sectors: ArrayList<Sector>

    private fun updateTitle() {
        title_textView.text = sectors[sector].displayName
        title_textView.transitionName = transitionName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sector)

        val extras = intent.extras
        if (extras == null) {
            Timber.e("Extras is null")
            onBackPressed()
            return
        }

        areaIndex = intent.getExtra(EXTRA_AREA, -1)
        zoneIndex = intent.getExtra(EXTRA_ZONE, -1)
        sector = intent.getExtra(EXTRA_SECTOR, 0)
        if (areaIndex < 0 || zoneIndex < 0) {
            Timber.e("Area or Zone index wasn't specified")
            onBackPressed()
            return
        }
        sectors = AREAS[areaIndex][zoneIndex].children

        transitionName = intent.getExtra(EXTRA_SECTOR_TRANSITION_NAME)
        if (transitionName == null)
            Timber.e("Transition name is null")

        back_imageButton.bringToFront()
        updateTitle()

        no_internet_imageView.setOnClickListener {
            val tooltip = Tooltip.Builder(this)
                .text(R.string.tooltip_no_internet)
                .anchor(0, 0)
                .arrow(true)
                .closePolicy(ClosePolicy.TOUCH_ANYWHERE_CONSUME)
                .overlay(true)
                .create()
            tooltip.show(it, Tooltip.Gravity.LEFT, false)
        }

        back_imageButton.setOnClickListener { onBackPressed() }

        val fragments = arrayListOf<Fragment>()
        for (sector in sectors) {
            Timber.v("Got sector \"$sector\" with ${sector.count()} paths.")
            fragments.add(SectorFragment(sector, sector_viewPager))
        }

        sector_viewPager.adapter =
            SectorPagerAdapter(this, fragments)
        if (savedInstanceState == null)
            sector_viewPager.currentItem = sector
        sector_viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                sector = position

                for (fragment in fragments)
                    (fragment as SectorFragment).minimize()

                updateTitle()
            }
        })
    }

    override fun onStateChange(state: ConnectivityProvider.NetworkState) {
        val hasInternet = state.hasInternet
        visibility(no_internet_imageView, !hasInternet)
    }
}