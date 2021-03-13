package com.arnyminerz.escalaralcoiaicomtat.fragment.model

import androidx.fragment.app.Fragment
import com.arnyminerz.escalaralcoiaicomtat.appNetworkProvider
import com.arnyminerz.escalaralcoiaicomtat.network.base.ConnectivityProvider

abstract class NetworkChangeListenerFragment : Fragment(),
    ConnectivityProvider.ConnectivityStateListener {

    override fun onResume() {
        super.onResume()
        appNetworkProvider.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        appNetworkProvider.removeListener(this)
    }
}
