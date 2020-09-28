package com.arnyminerz.escalaralcoiaicomtat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.fragment.model.NetworkChangeListenerFragment
import com.arnyminerz.escalaralcoiaicomtat.fragment.preferences.*
import com.arnyminerz.escalaralcoiaicomtat.fragment.preferences.MainSettingsFragment.Companion.SettingsPage
import timber.log.Timber

const val SETTINGS_HEIGHT_MAIN = 0
const val SETTINGS_HEIGHT_GENERAL = 1
const val SETTINGS_HEIGHT_NOTIFICATIONS = 1
const val SETTINGS_HEIGHT_DOWNLOADS = 1
const val SETTINGS_HEIGHT_ACCOUNT = 1
const val SETTINGS_HEIGHT_INFO = 1

@ExperimentalUnsignedTypes
class SettingsFragmentManager : NetworkChangeListenerFragment() {
    var height = 0
        private set

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_settings_manager, container, false)

    fun loadPage(page: SettingsPage, backPressed: Boolean) {
        if (view == null) {
            Timber.e("View not showing, should't load any pages.")
            return
        }

        activity?.supportFragmentManager?.beginTransaction()?.apply {
            if (backPressed)
                if (height > page.height) // Going Back
                    setCustomAnimations(R.anim.enter_left, R.anim.exit_right)
                else
                    setCustomAnimations(R.anim.enter_right, R.anim.exit_left)
            replace(
                R.id.settings_manager_frameLayout,
                when (page) {
                    SettingsPage.MAIN -> MainSettingsFragment().listen {
                        loadPage(it, false)
                    }
                    SettingsPage.GENERAL -> GeneralSettingsFragment(requireActivity())
                    SettingsPage.NOTIFICATIONS -> NotificationsSettingsFragment()
                    SettingsPage.DOWNLOADS -> DownloadsSettingsFragment()
                    SettingsPage.INFO -> InfoSettingsFragment()

                    SettingsPage.ACCOUNT -> AccountSettingsFragment()
                }
            )
            commit()
        } ?: Timber.e("No Activity Found!")

        height = page.height
    }

    override fun onResume() {
        super.onResume()

        if (isResumed)
            loadPage(SettingsPage.MAIN, false)
    }
}