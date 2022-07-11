package com.arnyminerz.escalaralcoiaicomtat.core.utils.context

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.arnyminerz.escalaralcoiaicomtat.core.preferences.PreferencesModule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * Manages setting of the app's locale.
 */
@Deprecated("Use Android 13's Per-App Language Preferences: https://developer.android.com/about/versions/13/features/app-languages")
object LocaleHelper {
    fun onAttach(context: Context): Context {
        val locale = getPersistedLocale()
        return setLocale(context, locale)
    }

    fun getPersistedLocale(): String =
        runBlocking { PreferencesModule.getLanguage.invoke().first() }

    /**
     * Set the app's locale to the one specified by the given String.
     *
     * @param context
     * @param localeSpec a locale specification as used for Android resources (NOTE: does not
     * support country and variant codes so far); the special string "system" sets
     * the locale to the locale specified in system settings
     * @return
     */
    fun setLocale(context: Context, localeSpec: String): Context {
        val locale: Locale = if (localeSpec == "system")
            Resources.getSystem().configuration.locales[0]
        else
            Locale(localeSpec)
        Locale.setDefault(locale)
        return updateResources(context, locale)
    }

    private fun updateResources(context: Context, locale: Locale): Context {
        val configuration: Configuration = context.resources.configuration
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}