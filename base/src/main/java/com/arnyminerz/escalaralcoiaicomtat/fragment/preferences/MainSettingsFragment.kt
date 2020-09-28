package com.arnyminerz.escalaralcoiaicomtat.fragment.preferences

import android.os.Bundle
import androidx.core.graphics.drawable.toDrawable
import androidx.preference.Preference
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.activity.MainActivity
import com.arnyminerz.escalaralcoiaicomtat.data.user.UserData
import com.arnyminerz.escalaralcoiaicomtat.exception.NoInternetAccessException
import com.arnyminerz.escalaralcoiaicomtat.fragment.*
import com.arnyminerz.escalaralcoiaicomtat.fragment.model.NetworkChangeListenerPreferenceFragment
import com.arnyminerz.escalaralcoiaicomtat.generic.PreferenceData
import com.arnyminerz.escalaralcoiaicomtat.generic.runOnUiThread
import com.arnyminerz.escalaralcoiaicomtat.view.visibility
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

val SETTINGS_ALERT_PREF = PreferenceData("alert_pref", true)
val SETTINGS_GESTURE_SENSIBILITY_PREF = PreferenceData("gest_sens_pref", 3)
val SETTINGS_LANGUAGE_PREF = PreferenceData("language_pref", 0)
val SETTINGS_NEARBY_DISTANCE_PREF = PreferenceData("nearby_distance", 1000)
val SETTINGS_MARKER_SIZE_PREF = PreferenceData("marker_size", 3)
val SETTINGS_CENTER_MARKER_PREF = PreferenceData("center_marker", true)
val SETTINGS_SMALL_MAP_PREF = PreferenceData("small_map", true)
val SETTINGS_PREVIEW_SCALE_PREF = PreferenceData("preview_scale", .5f)
val SETTINGS_MOBILE_DOWNLOAD_PREF = PreferenceData("mobile_download", true)
val SETTINGS_ROAMING_DOWNLOAD_PREF = PreferenceData("roaming_download", false)
val AUTOMATIC_DOWNLOADS_UPDATE_PREF = PreferenceData("automatic_downloads_update", false)
val AUTOMATIC_DATA_UPDATE_PREF = PreferenceData("automatic_data_update", true)
val PREF_DISABLE_NEARBY = PreferenceData("NearbyZonesDisable", false)
val PREF_SHOWN_INTRO = PreferenceData("ShownIntro", false)

@ExperimentalUnsignedTypes
class MainSettingsFragment : NetworkChangeListenerPreferenceFragment() {
    companion object {
        enum class SettingsPage(val height: Int) {
            MAIN(SETTINGS_HEIGHT_MAIN),
            GENERAL(SETTINGS_HEIGHT_GENERAL),
            NOTIFICATIONS(SETTINGS_HEIGHT_NOTIFICATIONS),
            INFO(SETTINGS_HEIGHT_INFO),
            DOWNLOADS(SETTINGS_HEIGHT_DOWNLOADS),
            ACCOUNT(SETTINGS_HEIGHT_ACCOUNT)
        }
    }

    private var settingsListener: ((page: SettingsPage) -> Unit)? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_main, rootKey)

        val accountPreference: Preference? = findPreference("pref_account")
        val generalPreference: Preference? = findPreference("pref_general")
        val notificationsPreference: Preference? = findPreference("pref_notifications")
        val downloadsPreference: Preference? = findPreference("pref_downloads")
        val infoPreference: Preference? = findPreference("pref_info")

        val loggedIn = MainActivity.loggedIn()
        visibility(accountPreference, loggedIn)
        if (loggedIn)
            GlobalScope.launch {
                try {
                    val user = UserData.fromUID(networkState, MainActivity.user()!!.uid)
                    user.profileImage(requireContext(), networkState) { bitmap ->
                        runOnUiThread {
                            accountPreference?.icon = bitmap.toDrawable(resources)

                            accountPreference?.setOnPreferenceClickListener {
                                settingsListener?.invoke(SettingsPage.ACCOUNT)
                                true
                            }
                        }
                    }
                } catch (error: NoInternetAccessException) {
                    Timber.e("No Internet connection was found for loading profile data")
                } catch (error: Exception) {
                    Timber.e(error, "Could not load profile image")
                }
            }

        generalPreference?.setOnPreferenceClickListener {
            settingsListener?.invoke(SettingsPage.GENERAL)
            true
        }
        notificationsPreference?.setOnPreferenceClickListener {
            settingsListener?.invoke(SettingsPage.NOTIFICATIONS)
            true
        }
        downloadsPreference?.setOnPreferenceClickListener {
            settingsListener?.invoke(SettingsPage.DOWNLOADS)
            true
        }
        infoPreference?.setOnPreferenceClickListener {
            settingsListener?.invoke(SettingsPage.INFO)
            true
        }
    }

    fun listen(listener: (page: SettingsPage) -> Unit): MainSettingsFragment {
        this.settingsListener = listener
        return this
    }
}