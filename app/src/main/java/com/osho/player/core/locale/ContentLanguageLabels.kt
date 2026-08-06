package com.osho.player.core.locale

import androidx.annotation.StringRes
import com.osho.player.R
import com.osho.player.domain.model.ContentLanguage

/**
 * On-screen wording for the discourse-language filter.
 *
 * Kept out of the domain enum so the model stays free of resource ids, and so
 * the labels follow the *app* language: "Hindi only" reads in English when the
 * interface is English, whatever the filter is set to.
 */
@get:StringRes
val ContentLanguage.labelRes: Int
    get() = when (this) {
        ContentLanguage.ALL -> R.string.content_language_all
        ContentLanguage.ENGLISH -> R.string.content_language_english
        ContentLanguage.HINDI -> R.string.content_language_hindi
    }
