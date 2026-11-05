package com.diaryvault.app.config

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Safe Firebase init — only when `google-services.json` was processed into the app resources.
 * No-op (and no crash) when the JSON is missing so stub SMS still works.
 */
object FirebaseBootstrap {
    private const val TAG = "DiaryVaultFirebase"
    @Volatile private var didAttempt = false

    /** True when the google-services Gradle plugin generated Firebase options resources. */
    @Volatile
    var hasGoogleServicesJson: Boolean = false
        private set

    fun configureIfNeeded(context: Context) {
        if (didAttempt) return
        synchronized(this) {
            if (didAttempt) return
            didAttempt = true

            hasGoogleServicesJson = detectGoogleServices(context.applicationContext)
            when (AuthConfig.smsProviderResolution) {
                is AuthConfig.SMSProviderResolution.Explicit ->
                    Log.d(TAG, "SMS provider=${AuthConfig.smsProvider.raw} (explicit DIARY_SMS_PROVIDER)")
                AuthConfig.SMSProviderResolution.AutoFirebaseJsonPresent ->
                    Log.d(TAG, "SMS provider=firebase (auto: google-services.json present)")
                AuthConfig.SMSProviderResolution.AutoStubNoJson ->
                    Log.d(TAG, "SMS provider=stub (auto: google-services.json missing)")
            }

            if (!hasGoogleServicesJson) return
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseApp.initializeApp failed — staying on stub SMS", e)
                hasGoogleServicesJson = false
            }
        }
    }

    val isConfigured: Boolean
        get() = currentApp() != null

    val projectId: String?
        get() = currentApp()?.options?.projectId

    private fun currentApp(): FirebaseApp? =
        try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            null
        }

    private fun detectGoogleServices(context: Context): Boolean {
        // google-services plugin generates values.xml with google_app_id when json is present.
        val resId = context.resources.getIdentifier(
            "google_app_id",
            "string",
            context.packageName,
        )
        if (resId == 0) return false
        val value = runCatching { context.getString(resId) }.getOrNull().orEmpty()
        return value.isNotBlank() && !value.contains("REPLACE_ME", ignoreCase = true)
    }
}
