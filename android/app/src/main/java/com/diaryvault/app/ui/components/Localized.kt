package com.diaryvault.app.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.diaryvault.app.model.AppLanguage
import com.diaryvault.app.ui.theme.LocalAppLanguage
import com.diaryvault.app.ui.theme.localeFor

@Composable
fun localizedString(resId: Int, vararg formatArgs: Any): String {
    val language = LocalAppLanguage.current
    val context = LocalContext.current
    return remember(language, resId, formatArgs.contentDeepHashCode()) {
        val config = Configuration(context.resources.configuration).apply {
            setLocale(localeFor(language))
        }
        val localized = context.createConfigurationContext(config)
        if (formatArgs.isEmpty()) localized.getString(resId)
        else localized.getString(resId, *formatArgs)
    }
}

@Composable
fun rememberLocalizedContext(language: AppLanguage = LocalAppLanguage.current) =
    remember(language) {
        // placeholder for callers that need Context; prefer localizedString
        language
    }
