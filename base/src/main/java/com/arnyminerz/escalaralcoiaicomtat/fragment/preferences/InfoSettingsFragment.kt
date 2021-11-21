package com.arnyminerz.escalaralcoiaicomtat.fragment.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.arnyminerz.escalaralcoiaicomtat.BuildConfig
import com.arnyminerz.escalaralcoiaicomtat.R
import com.arnyminerz.escalaralcoiaicomtat.core.shared.PREF_DATA_VERSION
import com.arnyminerz.escalaralcoiaicomtat.core.utils.doAsync
import com.arnyminerz.escalaralcoiaicomtat.core.utils.uiContext
import com.arnyminerz.escalaralcoiaicomtat.core.worker.BlockStatusWorker
import timber.log.Timber

class InfoSettingsFragment : PreferenceFragmentCompat() {
    private var blockStatusServicePreference: Preference? = null
    private var versionPreference: Preference? = null
    private var buildPreference: Preference? = null
    private var dataVersionPreference: Preference? = null
    private var githubPreference: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_info, rootKey)

        blockStatusServicePreference = findPreference("pref_service_block_status")
        versionPreference = findPreference("pref_version")
        buildPreference = findPreference("pref_build")
        dataVersionPreference = findPreference("pref_info_data_version_title")
        githubPreference = findPreference("pref_github")

        Timber.v("Refreshing preferences values...")
        updateStatus()
    }

    override fun onResume() {
        super.onResume()

        Timber.v("Refreshing preferences values...")
        updateStatus()
    }

    /**
     * Updates the values of all the preferences.
     * @author Arnau Mora
     * @since 20210916
     */
    private fun updateStatus() {
        val versionCode = BuildConfig.VERSION_CODE
        val versionName = BuildConfig.VERSION_NAME
        val dataVersion = PREF_DATA_VERSION.get()

        blockStatusServicePreference?.summary =
            getString(
                R.string.pref_info_service_block_status_summary,
                getString(R.string.status_loading)
            )

        versionPreference?.summary = versionName
        buildPreference?.summary = versionCode.toString()
        dataVersionPreference?.summary = dataVersion

        githubPreference?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://github.com/ArnyminerZ/EscalarAlcoiaIComtat-Android")
            })
            true
        }

        // Fetch the BlockStatusWorker status
        doAsync {
            val workerInfo = BlockStatusWorker.info(requireContext())
            uiContext {
                val status = workerInfo?.let {
                    if (it.state.isFinished)
                        R.string.status_finished
                    else
                        R.string.status_running
                } ?: R.string.status_not_running

                blockStatusServicePreference?.summary =
                    getString(R.string.pref_info_service_block_status_summary, getString(status))
            }
        }
    }
}