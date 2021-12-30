package com.arnyminerz.escalaralcoiaicomtat.core.preferences.usecase.user

import com.arnyminerz.escalaralcoiaicomtat.core.preferences.repo.UserPreferencesRepository

class SetDataCollection(
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) =
        userPreferencesRepository.setDataCollectionEnabled(enabled)
}