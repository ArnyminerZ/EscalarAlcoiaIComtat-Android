package com.arnyminerz.escalaralcoiaicomtat.core.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClass
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.db.database.BlockingDatabase
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.db.database.DataClassDatabase
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.BlockingData
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.path.Path
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.sector.Sector
import com.arnyminerz.escalaralcoiaicomtat.core.shared.context
import com.arnyminerz.escalaralcoiaicomtat.core.ui.theme.*
import com.arnyminerz.escalaralcoiaicomtat.core.worker.BlockStatusWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bytebeats.views.charts.bar.BarChartData
import timber.log.Timber

class SectorPageViewModel(application: Application) : AndroidViewModel(application) {

    val dataClass: MutableLiveData<DataClass<*, *>?> = MutableLiveData(null)

    /**
     * A Mutable State delegation of a list of paths with the paths to display.
     * @author Arnau Mora
     * @since 20220106
     */
    val paths: MutableLiveData<List<Path>> = MutableLiveData(emptyList())

    /**
     * A mutable state delegation of a list of [BlockingData] of the paths.
     * @author Arnau Mora
     * @since 20220330
     */
    val blockStatusList: MutableLiveData<List<BlockingData>> = MutableLiveData(emptyList())

    /**
     * A Mutable state delegation of [BarChartData] with the data to display.
     * @author Arnau Mora
     * @since 20220106
     */
    val barChartData: MutableLiveData<BarChartData> = MutableLiveData(
        BarChartData(
            bars = listOf(
                BarChartData.Bar(
                    label = "3º-5+",
                    value = 0f,
                    color = grade_green,
                ),
                BarChartData.Bar(
                    label = "6a-6c+",
                    value = 0f,
                    color = grade_blue,
                ),
                BarChartData.Bar(
                    label = "7a-7c+",
                    value = 0f,
                    color = grade_red,
                ),
                BarChartData.Bar(
                    label = "8a-8c+",
                    value = 0f,
                    color = grade_black,
                ),
                BarChartData.Bar(
                    label = "¿?",
                    value = 0f,
                    color = grade_purple,
                ),
            )
        )
    )

    /**
     * Loads the [BarChartData] of [sector] into [loadBarChartData].
     * @author Arnau Mora
     * @since 20220106
     */
    fun loadBarChartData(sector: Sector) {
        viewModelScope.launch {
            val bars = withContext(Dispatchers.IO) {
                Timber.d("Loading path grades...")
                val paths = sector.getChildren(context) { it.objectId }
                var grades1Count = 0 // 3º-5+
                var grades2Count = 0 // 6a-6c+
                var grades3Count = 0 // 7a-7c+
                var grades4Count = 0 // 8a-8c+
                var grades5Count = 0 // ¿?
                Timber.d("Got ${paths.size} paths. Getting grades...")
                for (path in paths)
                    (path.pitches
                        .takeIf { it.isNotEmpty() }
                        ?.mapNotNull { it.grade?.replace("L\\d ".toRegex(), "") }
                        ?: listOf(path.generalGrade))
                        .forEach {
                            Timber.v("- Grade: $it")
                            when {
                                it.matches("^[3-5].*".toRegex()) -> grades1Count++
                                it.matches("^6.*".toRegex()) -> grades2Count++
                                it.matches("^7.*".toRegex()) -> grades3Count++
                                it.matches("^8.*".toRegex()) -> grades4Count++
                                else -> grades5Count++
                            }
                        }

                Timber.d("Grades processed: $grades1Count, $grades2Count, $grades3Count, $grades4Count, $grades5Count.")
                arrayListOf<BarChartData.Bar>().apply {
                    if (grades1Count > 0)
                        add(
                            BarChartData.Bar(
                                label = "3º-5+",
                                value = grades1Count.toFloat(),
                                color = grade_green,
                            )
                        )
                    if (grades2Count > 0)
                        add(
                            BarChartData.Bar(
                                label = "6a-6c+",
                                value = grades2Count.toFloat(),
                                color = grade_blue,
                            )
                        )
                    if (grades3Count > 0)
                        add(
                            BarChartData.Bar(
                                label = "7a-7c+",
                                value = grades3Count.toFloat(),
                                color = grade_red,
                            )
                        )
                    if (grades4Count > 0)
                        add(
                            BarChartData.Bar(
                                label = "8a-8c+",
                                value = grades4Count.toFloat(),
                                color = grade_black,
                            )
                        )
                    if (grades5Count > 0)
                        add(
                            BarChartData.Bar(
                                label = "¿?",
                                value = grades5Count.toFloat(),
                                color = grade_purple,
                            )
                        )
                }
            }

            Timber.d("BarChartData built. Updating value...")
            barChartData.postValue(BarChartData(bars))
        }
    }

    /**
     * Loads the list of [Path] contained in [sector].
     * @author Arnau Mora
     * @since 20220106
     * @param sector The [Sector] to load the [Path] from.
     */
    fun loadPaths(sector: Sector) {
        viewModelScope.launch {
            val pathsList = withContext(Dispatchers.IO) {
                Timber.d("Loading paths from $sector...")
                sector.getChildren(context) { it.sketchId }
            }
            val blockingStatus = withContext(Dispatchers.IO) {
                val database = BlockingDatabase.getInstance(context)
                val dao = database.blockingDao()
                dao.getAllOnce().takeIf { it.isNotEmpty() } ?: run {
                    Timber.v("No block status have been loaded. Fetching all now.")
                    BlockStatusWorker.blockStatusFetchRoutine(context)

                    dao.getAllOnce()
                }
            }

            Timber.v("Blocking status: $blockingStatus")

            blockStatusList.postValue(blockingStatus)
            paths.postValue(pathsList)
        }
    }

    /**
     * Loads the zone at [objectId].
     * @author Arnau Mora
     * @since 20220330
     * @param objectId The id of the zone to load.
     */
    fun loadZone(objectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                Timber.i("Loading Zone $objectId...")
                val db = DataClassDatabase.getInstance(context)
                val dao = db.zonesDao()
                dao.get(objectId)
            }?.let { dataClass.postValue(it) } ?: run {
                Timber.w("Could not find $objectId!")
            }
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            if (modelClass.isAssignableFrom(SectorPageViewModel::class.java))
                return SectorPageViewModel(application) as T
            error("Unknown view model class: $modelClass")
        }
    }
}