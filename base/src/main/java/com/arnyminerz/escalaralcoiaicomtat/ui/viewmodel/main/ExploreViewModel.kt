package com.arnyminerz.escalaralcoiaicomtat.ui.viewmodel.main

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.lifecycle.*
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.DataSingleton
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClass
import com.arnyminerz.escalaralcoiaicomtat.core.data.climb.dataclass.DataClassImpl
import com.arnyminerz.escalaralcoiaicomtat.core.shared.app
import com.arnyminerz.escalaralcoiaicomtat.core.shared.context
import kotlinx.coroutines.launch
import timber.log.Timber

class ExploreViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Timber.d("$this::init")
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("$this::onCleared")
    }

    /**
     * For accessing the DataSingleton at any moment.
     * @author Arnau Mora
     * @since 20220315
     */
    private val dataSingleton = DataSingleton.getInstance(application)

    val children by DataSingleton.getInstance(application).children

    val areas by DataSingleton.getInstance(application).areas

    /**
     * Loads all the available areas, and posts them using a [MutableLiveData].
     * @author Arnau Mora
     * @since 20220105
     */
    fun loadAreas() {
        viewModelScope.launch {
            val areasList = app.getAreas()
                .sortedBy { it.displayName }
            dataSingleton.areas.value = areasList
        }
    }

    /**
     * Loads the children of a set DataClass.
     * @author Arnau Mora
     * @since 20220102
     * @param dataClass The DataClass to load the children from.
     */
    fun <A : DataClassImpl, T : DataClass<A, *, *>, R : Comparable<R>> childrenLoader(
        dataClass: T,
        sortBy: (A) -> R?,
    ) {
        viewModelScope.launch {
            dataSingleton.children.value = dataClass.getChildren(context, sortBy)
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            if (modelClass.isAssignableFrom(ExploreViewModel::class.java))
                return ExploreViewModel(application) as T
            error("Unknown view model class: $modelClass")
        }
    }
}