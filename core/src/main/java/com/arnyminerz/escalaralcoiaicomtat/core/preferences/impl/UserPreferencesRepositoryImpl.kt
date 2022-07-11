package com.arnyminerz.escalaralcoiaicomtat.core.preferences.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.arnyminerz.escalaralcoiaicomtat.core.preferences.model.UserPreferences
import com.arnyminerz.escalaralcoiaicomtat.core.preferences.repo.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * The default value of the distance to consider as nearby for nearby zones.
 * @author Arnau Mora
 * @since 20211229
 */
const val NEARBY_DISTANCE_DEFAULT = 1000

/**
 * The implementation of the user preferences repository so it can be used.
 * @author Arnau Mora
 * @since 20211229
 * @param dataStore The data store to use for applying preferences.
 */
class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    // TODO: Add javadocs
    private object Keys {
        val nearbyZonesEnabled = booleanPreferencesKey("nearby_enabled")
        val nearbyZonesDistance = intPreferencesKey("nearby_distance")
        val dataCollection = booleanPreferencesKey("data_collection")
        val errorCollection = booleanPreferencesKey("error_collection")
        val showAlerts = booleanPreferencesKey("alerts_enabled")
        val centerMarkerOnClick = booleanPreferencesKey("center_marker")
        val mobileDownload = booleanPreferencesKey("mobile_download")
        val meteredDownload = booleanPreferencesKey("metered_download")
        val roamingDownload = booleanPreferencesKey("roaming_download")
        val downloadQuality = intPreferencesKey("download_quality")
        val enableDeveloperTab = booleanPreferencesKey("developer_tab")
    }

    private inline val Preferences.nearbyZonesEnabled
        get() = this[Keys.nearbyZonesEnabled] ?: true

    private inline val Preferences.nearbyZonesDistance
        get() = this[Keys.nearbyZonesDistance] ?: NEARBY_DISTANCE_DEFAULT

    private inline val Preferences.dataCollection
        get() = this[Keys.dataCollection] ?: true

    private inline val Preferences.errorCollection
        get() = this[Keys.errorCollection] ?: true

    private inline val Preferences.showAlerts
        get() = this[Keys.showAlerts] ?: true

    private inline val Preferences.centerMarkerOnClick
        get() = this[Keys.centerMarkerOnClick] ?: true

    private inline val Preferences.mobileDownload
        get() = this[Keys.mobileDownload] ?: false

    private inline val Preferences.meteredDownload
        get() = this[Keys.meteredDownload] ?: false

    private inline val Preferences.roamingDownload
        get() = this[Keys.roamingDownload] ?: false

    private inline val Preferences.downloadQuality
        get() = this[Keys.downloadQuality] ?: 85

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch {
            if (it is IOException)
                emit(emptyPreferences())
            else throw it
        }
        .map { preferences ->
            UserPreferences(
                preferences.nearbyZonesEnabled,
                preferences.nearbyZonesDistance,
                preferences.dataCollection,
                preferences.errorCollection,
                preferences.showAlerts,
                preferences.centerMarkerOnClick,
                preferences.mobileDownload,
                preferences.meteredDownload,
                preferences.roamingDownload,
                preferences.downloadQuality
            )
        }
        .distinctUntilChanged()

    override suspend fun setNearbyZonesEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.nearbyZonesEnabled] = enabled
        }
    }

    override suspend fun setNearbyZonesDistance(distance: Int) {
        dataStore.edit {
            it[Keys.nearbyZonesDistance] = distance
        }
    }

    override suspend fun setDataCollectionEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.dataCollection] = enabled
        }
    }

    override suspend fun setErrorCollectionEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.errorCollection] = enabled
        }
    }

    override suspend fun setDisplayAlertsEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.showAlerts] = enabled
        }
    }

    override suspend fun setMarkerClickCenteringEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.centerMarkerOnClick] = enabled
        }
    }

    override suspend fun setDownloadMobileEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.mobileDownload] = enabled
        }
    }

    override suspend fun setDownloadMeteredEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.meteredDownload] = enabled
        }
    }

    override suspend fun setDownloadRoamingEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.roamingDownload] = enabled
        }
    }

    override suspend fun setDownloadQuality(quality: Int) {
        dataStore.edit {
            it[Keys.downloadQuality] = quality
        }
    }

    override suspend fun setDeveloperTabEnabled(enabled: Boolean) {
        dataStore.edit {
            it[Keys.enableDeveloperTab] = enabled
        }
    }

    /**
     * Returns whether or not the nearby zones module is enabled.
     * @author Arnau Mora
     * @since 20211229
     */
    override val nearbyZonesEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.nearbyZonesEnabled] ?: true
        }
        .distinctUntilChanged()

    /**
     * Returns the distance until which a zone is considered as nearby.
     * @author Arnau Mora
     * @since 20211229
     */
    override val nearbyZonesDistance: Flow<Int> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.nearbyZonesDistance] ?: NEARBY_DISTANCE_DEFAULT
        }
        .distinctUntilChanged()

    /**
     * Returns whether or not the map should be centered when clicking a marker.
     * @author Arnau Mora
     * @since 20211229
     */
    override val markerClickCenteringEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.centerMarkerOnClick] ?: true
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on error collection.
     * @author Arnau Mora
     * @since 20211229
     */
    override val errorCollectionEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.errorCollection] ?: true
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on data collection.
     * @author Arnau Mora
     * @since 20211229
     */
    override val dataCollectionEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.dataCollection] ?: true
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on alert notifications.
     * @author Arnau Mora
     * @since 20211229
     */
    override val alertNotificationsEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.showAlerts] ?: true
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on downloads on mobile data networks.
     * @author Arnau Mora
     * @since 20211229
     */
    override val mobileDownloadsEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.mobileDownload] ?: false
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on downloads on metered networks.
     * @author Arnau Mora
     * @since 20211229
     */
    override val meteredDownloadsEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.meteredDownload] ?: false
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on downloads on roaming networks.
     * @author Arnau Mora
     * @since 20211229
     */
    override val roamingDownloadsEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.roamingDownload] ?: false
        }
        .distinctUntilChanged()

    /**
     * Returns the preference of the user on showing the developer tab on the bottom navigation bar.
     * Only enabled to debug builds.
     * @author Arnau Mora
     * @since 20220227
     */
    override val developerTabEnabled: Flow<Boolean> = dataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map {
            it[Keys.enableDeveloperTab] ?: true
        }
        .distinctUntilChanged()
}