package com.diaryvault.app.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Safe Firebase init — only when `google-services.json` was processed into the app resources.
 * No-op (and no crash) when the JSON is missing so stub SMS still works.
 *
 * Debug builds should always ship with `android/app/google-services.json` for project
 * **diaryappsilas** (Gradle validates this unless `-Pdiary.allowStubSms=true`).
 */
object FirebaseBootstrap {
    private const val TAG = "DiaryVaultFirebase"
    const val EXPECTED_PROJECT_ID = "diaryappsilas"

    @Volatile private var didAttempt = false

    /** True when the google-services Gradle plugin generated Firebase options resources. */
    @Volatile
    var hasGoogleServicesJson: Boolean = false
        private set

    /** Cached project id from [FirebaseApp] options and/or generated `project_id` resource. */
    @Volatile
    private var cachedProjectId: String? = null

    fun configureIfNeeded(context: Context) {
        if (didAttempt) return
        synchronized(this) {
            if (didAttempt) return
            didAttempt = true

            val appContext = context.applicationContext
            val resourceProjectId = readResourceString(appContext, "project_id")
            hasGoogleServicesJson = detectGoogleServices(appContext)
            when (AuthConfig.smsProviderResolution) {
                is AuthConfig.SMSProviderResolution.Explicit ->
                    Log.d(TAG, "SMS provider=${AuthConfig.smsProvider.raw} (explicit DIARY_SMS_PROVIDER)")
                AuthConfig.SMSProviderResolution.AutoFirebaseJsonPresent ->
                    Log.d(TAG, "SMS provider=firebase (auto: google-services.json present)")
                AuthConfig.SMSProviderResolution.AutoStubNoJson ->
                    Log.d(TAG, "SMS provider=stub (auto: google-services.json missing)")
            }

            if (!hasGoogleServicesJson) {
                cachedProjectId = null
                Log.w(
                    TAG,
                    "google-services.json not baked into this APK — stub SMS only. " +
                        "Silas: place file at android/app/google-services.json " +
                        "(Windows: C:\\dev\\diaryapp\\android\\app\\google-services.json) " +
                        "with project_id=$EXPECTED_PROJECT_ID, then rebuild Debug.",
                )
                return
            }
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    val options = buildOptionsFromResources(appContext)
                    if (options != null) {
                        FirebaseApp.initializeApp(appContext, options)
                    } else {
                        FirebaseApp.initializeApp(appContext)
                    }
                }
                cachedProjectId = resolveProjectId(appContext, resourceProjectId)
                Log.i(
                    TAG,
                    "Firebase configured project=${cachedProjectId ?: "?"} " +
                        "resourceProjectId=${resourceProjectId ?: "?"} " +
                        "expected=$EXPECTED_PROJECT_ID",
                )
                // Trailing if must include else: try is an expression in Kotlin.
                if (cachedProjectId.isNullOrBlank()) {
                    Log.e(
                        TAG,
                        "FirebaseApp has blank projectId — google-services plugin resources " +
                            "are incomplete. Re-download google-services.json for " +
                            "$EXPECTED_PROJECT_ID / com.diaryvault.app and rebuild.",
                    )
                } else if (cachedProjectId != EXPECTED_PROJECT_ID) {
                    Log.e(
                        TAG,
                        "FIREBASE PROJECT MISMATCH runtime=$cachedProjectId " +
                            "expected=$EXPECTED_PROJECT_ID — Console test codes on " +
                            "diaryappsilas will never verify",
                    )
                } else {
                    // projectId matches EXPECTED_PROJECT_ID
                }
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp.initializeApp failed — staying on stub SMS", e)
                hasGoogleServicesJson = false
                cachedProjectId = null
            }
        }
    }

    val isConfigured: Boolean
        get() = currentApp() != null

    /**
     * Runtime Firebase project id. Prefer live [FirebaseOptions.projectId], then the
     * google-services-generated `project_id` string resource, then the value cached at init.
     * Blank/`null` means the JSON was missing, not applied, or incomplete — UI shows `?`.
     */
    val projectId: String?
        get() {
            cachedProjectId?.takeIf { it.isNotBlank() }?.let { return it }
            val fromOptions = currentApp()?.options?.projectId?.trim()?.takeIf { it.isNotEmpty() }
            if (fromOptions != null) {
                cachedProjectId = fromOptions
                return fromOptions
            }
            return cachedProjectId?.takeIf { it.isNotBlank() }
        }

    /** True when runtime project matches diaryappsilas (Console test list project). */
    val isExpectedProject: Boolean
        get() = projectId == EXPECTED_PROJECT_ID

    private fun currentApp(): FirebaseApp? =
        try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            null
        }

    private fun resolveProjectId(context: Context, resourceProjectId: String?): String? {
        val fromOptions = currentApp()?.options?.projectId?.trim()?.takeIf { it.isNotEmpty() }
        if (fromOptions != null) return fromOptions
        val fromResource = resourceProjectId?.trim()?.takeIf { it.isNotEmpty() }
            ?: readResourceString(context, "project_id")?.trim()?.takeIf { it.isNotEmpty() }
        return fromResource
    }

    private fun detectGoogleServices(context: Context): Boolean {
        // google-services plugin generates values.xml with google_app_id when json is present.
        val appId = readResourceString(context, "google_app_id").orEmpty()
        if (appId.isBlank() || appId.contains("REPLACE_ME", ignoreCase = true)) return false
        return true
    }

    /**
     * Build [FirebaseOptions] from google-services-generated string resources so
     * `project_id` is never dropped when [FirebaseApp.initializeApp] falls back to
     * incomplete default resource parsing.
     */
    private fun buildOptionsFromResources(context: Context): FirebaseOptions? {
        val appId = readResourceString(context, "google_app_id")?.trim().orEmpty()
        if (appId.isBlank() || appId.contains("REPLACE_ME", ignoreCase = true)) return null
        val apiKey = readResourceString(context, "google_api_key")?.trim().orEmpty()
        if (apiKey.isBlank() || apiKey.contains("REPLACE_ME", ignoreCase = true)) return null
        val projectId = readResourceString(context, "project_id")?.trim().orEmpty()
        val builder = FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
        if (projectId.isNotBlank() && !projectId.contains("REPLACE_ME", ignoreCase = true)) {
            builder.setProjectId(projectId)
        }
        readResourceString(context, "gcm_defaultSenderId")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.setGcmSenderId(it)
        }
        readResourceString(context, "google_storage_bucket")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.setStorageBucket(it)
        }
        readResourceString(context, "firebase_database_url")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            builder.setDatabaseUrl(it)
        }
        return builder.build()
    }

    private fun readResourceString(context: Context, name: String): String? {
        val resId = context.resources.getIdentifier(name, "string", context.packageName)
        if (resId == 0) return null
        return runCatching { context.getString(resId) }.getOrNull()
    }
}
