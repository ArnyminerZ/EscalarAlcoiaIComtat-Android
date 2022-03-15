package com.arnyminerz.escalaralcoiaicomtat.core.shared

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.annotation.WorkerThread
import androidx.appsearch.app.AppSearchSession
import androidx.appsearch.app.SetSchemaRequest
import androidx.appsearch.exceptions.AppSearchException
import androidx.lifecycle.AndroidViewModel
import androidx.work.await
import com.arnyminerz.escalaralcoiaicomtat.core.annotations.ObjectId
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.SearchSingleton
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.area.Area
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.downloads.DownloadedData
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.SectorData
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.zone.Zone
import com.arnyminerz.escalaralcoiaicomtat.core.network.base.ConnectivityProvider
import com.arnyminerz.escalaralcoiaicomtat.core.preferences.PreferencesModule
import com.arnyminerz.escalaralcoiaicomtat.core.utils.doAsync
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getArea
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getAreas
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getDownloads
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getPath
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getPaths
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getSector
import com.arnyminerz.escalaralcoiaicomtat.core.utils.getZone
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

class App : Application(), ConnectivityProvider.ConnectivityStateListener {
    private val provider: ConnectivityProvider
        get() = appNetworkProvider

    /**
     * The [FirebaseAnalytics] instance reference for analyzing the user actions.
     * @author Arnau Mora
     * @since 20210826
     */
    private lateinit var analytics: FirebaseAnalytics

    private lateinit var searchSingleton: SearchSingleton

    override fun onCreate() {
        super.onCreate()

        searchSingleton = SearchSingleton.getInstance(this)

        doAsync {
            Timber.v("Search > Adding document classes...")
            try {
                val setSchemaRequest = SetSchemaRequest.Builder()
                    .addDocumentClasses(SEARCH_SCHEMAS)
                    .setVersion(SEARCH_SCHEMA_VERSION)
                    .setMigrator("SectorData", SectorData.Companion.Migrator)
                    .build()
                searchSingleton
                    .searchSession
                    .setSchema(setSchemaRequest)
                    .await()
            } catch (e: AppSearchException) {
                Timber.e(e, "Search > Could not add search schemas.")
            }
        }

        PreferencesModule.initWith(this)

        // TODO: Shared preferences will be removed
        @Suppress("DEPRECATION")
        sharedPreferences =
            applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

        Timber.v("Getting Analytics instance...")
        analytics = Firebase.analytics

        Timber.v("Initializing network provider...")
        appNetworkProvider = ConnectivityProvider.createProvider(this)
        Timber.v("Adding network listener...")
        provider.addListener(this)
    }

    override fun onTerminate() {
        Timber.i("Terminating app...")
        Timber.v("Removing network listener...")
        provider.removeListener(this)
        Timber.v("Closing AppSearch...")
        searchSingleton
            .searchSession
            .close()
        super.onTerminate()
    }

    override fun onStateChange(state: ConnectivityProvider.NetworkState) {
        Timber.v("Network state updated: $state")

        appNetworkState = state
    }

    override suspend fun onStateChangeAsync(state: ConnectivityProvider.NetworkState) {}

    /**
     * Gets all the [Area]s available.
     * @author Arnau Mora
     * @since 20210818
     */
    @WorkerThread
    suspend fun getAreas(): List<Area> = searchSingleton.searchSession.getAreas()

    /**
     * Searches for the specified [Zone].
     * Serves for a shortcut to [AppSearchSession.getZone].
     * @author Arnau Mora
     * @since 20210820
     * @see AppSearchSession.getZone
     */
    @WorkerThread
    suspend fun getArea(@ObjectId areaId: String): Area? =
        searchSingleton.searchSession.getArea(areaId)

    /**
     * Searches for the specified [Zone].
     * Serves for a shortcut to [AppSearchSession.getZone].
     * @author Arnau Mora
     * @since 20210820
     * @see AppSearchSession.getZone
     */
    @WorkerThread
    suspend fun getZone(@ObjectId zoneId: String): Zone? =
        searchSingleton.searchSession.getZone(zoneId)

    /**
     * Searches for the specified [Sector].
     * Serves for a shortcut to [AppSearchSession.getSector].
     * @author Arnau Mora
     * @since 20210820
     * @see AppSearchSession.getSector
     */
    @WorkerThread
    suspend fun getSector(@ObjectId sectorId: String): Sector? =
        searchSingleton.searchSession.getSector(sectorId)

    /**
     * Searches for the specified [Path].
     * Serves for a shortcut to [AppSearchSession.getPath].
     * @author Arnau Mora
     * @since 20210820
     * @see AppSearchSession.getSector
     */
    @WorkerThread
    suspend fun getPath(@ObjectId pathId: String): Path? =
        searchSingleton.searchSession.getPath(pathId)

    /**
     * Searches for the specified [Path]s.
     * Serves for a shortcut to [AppSearchSession.getPaths].
     * @author Arnau Mora
     * @since 20210820
     * @see AppSearchSession.getSector
     */
    @WorkerThread
    suspend fun getPaths(@ObjectId zoneId: String): List<Path> =
        searchSingleton.searchSession.getPaths(zoneId)

    /**
     * Fetches all the downloaded items.
     * @author Arnau Mora
     * @since 20211231
     * @return A [Flow] that emits the downloaded items.
     */
    @WorkerThread
    suspend fun getDownloads(): Flow<DownloadedData> = searchSingleton.searchSession.getDownloads()
}

/**
 * Returns the [Activity.getApplication] casted as [App].
 * @author Arnau Mora
 * @since 20210818
 */
val Activity.app: App
    get() = application as App

/**
 * Returns the [AndroidViewModel.getApplication] casted as [App].
 * @author Arnau Mora
 * @since 20210818
 */
val AndroidViewModel.app: App
    get() = getApplication<App>()

/**
 * Returns the application's context attached to the view model.
 * @author Arnau Mora
 * @since 20211229
 */
val AndroidViewModel.context: Context
    get() = getApplication()
