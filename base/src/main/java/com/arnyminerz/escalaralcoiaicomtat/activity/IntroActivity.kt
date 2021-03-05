package com.arnyminerz.escalaralcoiaicomtat.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.arnyminerz.escalaralcoiaicomtat.BuildConfig
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.model.NetworkChangeListenerActivity
import com.arnyminerz.escalaralcoiaicomtat.data.IntroShowReason
import com.arnyminerz.escalaralcoiaicomtat.data.preference.sharedPreferences
import com.arnyminerz.escalaralcoiaicomtat.databinding.ActivityIntroBinding
import com.arnyminerz.escalaralcoiaicomtat.fragment.intro.BetaIntroFragment
import com.arnyminerz.escalaralcoiaicomtat.fragment.intro.DownloadAreasIntroFragment
import com.arnyminerz.escalaralcoiaicomtat.fragment.intro.MainIntroFragment
import com.arnyminerz.escalaralcoiaicomtat.fragment.intro.StorageIntroFragment
import com.arnyminerz.escalaralcoiaicomtat.fragment.intro.StorageIntroFragment.Companion.STORAGE_PERMISSION_REQUEST
import com.arnyminerz.escalaralcoiaicomtat.fragment.preferences.PREF_SHOWN_INTRO
import com.arnyminerz.escalaralcoiaicomtat.generic.isPermissionGranted
import com.arnyminerz.escalaralcoiaicomtat.generic.runAsync
import com.arnyminerz.escalaralcoiaicomtat.generic.toast
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider
import com.google.android.material.button.MaterialButton
import timber.log.Timber
import java.io.File


@ExperimentalUnsignedTypes
class IntroActivity : NetworkChangeListenerActivity() {
    companion object {
        var shouldChange = false

        fun cacheFile(context: Context) = File(context.filesDir, "cache.json")

        fun hasDownloaded(context: Context): Boolean = cacheFile(context).exists()

        fun shouldShow(context: Context): IntroShowReason {
            if (!context.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE))
                return IntroShowReason.STORAGE_PERMISSION
            if (!hasDownloaded(context))
                return IntroShowReason.DOWNLOAD
            if (!PREF_SHOWN_INTRO.get(context.sharedPreferences))
                return IntroShowReason.PREF_FALSE
            return IntroShowReason.OK
        }
    }

    var adapterViewPager: IntroPagerAdapter? = null
        private set

    private lateinit var binding: ActivityIntroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        adapterViewPager = IntroPagerAdapter(supportFragmentManager, this)
        binding.viewPager.adapter = adapterViewPager
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                val storageIntroFragmentIndex =
                    adapterViewPager!!.fragments.indexOf(adapterViewPager!!.storageIntroFragment)
                if (position - 1 == storageIntroFragmentIndex)
                    if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        binding.viewPager.currentItem = storageIntroFragmentIndex
                        binding.introNextFAB.setImageResource(R.drawable.round_chevron_right_24)
                        shouldChange = false
                        ActivityCompat.requestPermissions(
                            this@IntroActivity,
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ),
                            STORAGE_PERMISSION_REQUEST
                        )
                        return
                    }
            }

            override fun onPageSelected(position: Int) {
            }
        })

        binding.introNextFAB.setOnClickListener {
            next()
        }
    }

    fun fabStatus(enabled: Boolean) {
        binding.introNextFAB.isEnabled = enabled
    }

    fun next() {
        val position = binding.viewPager.currentItem

        val storageIntroFragmentIndex =
            adapterViewPager!!.fragments.indexOf(adapterViewPager!!.storageIntroFragment)
        if (position == storageIntroFragmentIndex)
            if (!isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                binding.viewPager.currentItem = storageIntroFragmentIndex
                binding.introNextFAB.setImageResource(R.drawable.round_chevron_right_24)
                shouldChange = false
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_REQUEST
                )
                return
            }

        if (position + 1 >= adapterViewPager!!.fragments.size) {
            Timber.v("Finished showing intro pages. Loading MainActivity")
            PREF_SHOWN_INTRO.put(sharedPreferences, true)
            startActivity(Intent(this, MainActivity()::class.java))
        } else {
            if (binding.viewPager.currentItem == adapterViewPager!!.fragments.size - 2)
                binding.introNextFAB.setImageResource(R.drawable.round_check_24)
            binding.viewPager.currentItem++
            Timber.v("Showing intro page ${binding.viewPager.currentItem}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    findViewById<MaterialButton?>(R.id.grant_storage_permission_button)
                        ?.apply {
                            setText(R.string.status_permission_granted)
                            isEnabled = false
                        }
                } else {
                    toast(R.string.toast_permission_required)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onStateChange(state: ConnectivityProvider.NetworkState) {
        super.onStateChange(state)

        if (state.hasInternet && binding.viewPager.currentItem ==
            adapterViewPager!!.fragments.indexOf(adapterViewPager!!.downloadIntroFragment)
        )
            if (!DownloadAreasIntroFragment.loading)
                runAsync {
                    DownloadAreasIntroFragment.downloadAreasCache(
                        this,
                        findViewById(R.id.intro_download_spinner),
                        findViewById(R.id.internetWaiting_layout)
                    )
                }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class IntroPagerAdapter(fragmentManager: FragmentManager, context: Context) :
        FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        val fragments = arrayListOf<Fragment>()

        val mainIntroFragment = MainIntroFragment()
        val betaIntroFragment = BetaIntroFragment()
        val storageIntroFragment = StorageIntroFragment()
        val downloadIntroFragment = DownloadAreasIntroFragment()

        init {
            fragments.add(mainIntroFragment)
            if (BuildConfig.DEBUG)
                fragments.add(betaIntroFragment)
            if (!context.isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                fragments.add(storageIntroFragment)
            if (!hasDownloaded(context))
                fragments.add(downloadIntroFragment)
        }

        override fun getCount() = fragments.size

        override fun getItem(position: Int): Fragment = fragments[position]
    }
}