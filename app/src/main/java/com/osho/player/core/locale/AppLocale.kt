package com.osho.player.core.locale

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.osho.player.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The language the interface is drawn in.
 *
 * Deliberately separate from [com.osho.player.domain.model.ContentLanguage]:
 * which discourses a listener wants to hear says nothing about which language
 * they read menus in. Someone who only follows Hindi talks may still run an
 * English phone, and the reverse is at least as common.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    ENGLISH("en", R.string.settings_app_language_english),
    HINDI("hi", R.string.settings_app_language_hindi),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}

/**
 * Storage and application of the interface language.
 *
 * Backed by [android.content.SharedPreferences] rather than the DataStore that
 * holds every other preference, because this one has to be readable
 * synchronously: `attachBaseContext` runs before the process has a coroutine
 * scope, an injected dependency or a chance to await anything, and it is the
 * only place a `Context` can be given different resources.
 */
object AppLocale {

    private const val PREFS = "osho_app_locale"
    private const val KEY_LANGUAGE = "app_language"

    private val _language = MutableStateFlow(AppLanguage.ENGLISH)

    /** Observed by the UI so the Settings row reflects the current choice. */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private var loaded = false

    fun current(context: Context): AppLanguage {
        if (!loaded) {
            _language.value = AppLanguage.fromTag(prefs(context).getString(KEY_LANGUAGE, null))
            loaded = true
        }
        return _language.value
    }

    fun set(context: Context, language: AppLanguage) {
        prefs(context).edit().putString(KEY_LANGUAGE, language.tag).apply()
        loaded = true
        _language.value = language
    }

    /**
     * Returns [context] with its resources bound to the chosen language.
     *
     * Called from `attachBaseContext` of the application, the activity and both
     * services, so every `getString` in the process — including the ones behind
     * notification channels — resolves the same way.
     */
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(current(context).tag)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return context.createConfigurationContext(configuration)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** The hosting activity, so a language change can restart it with new resources. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
