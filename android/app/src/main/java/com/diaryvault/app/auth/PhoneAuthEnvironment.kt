package com.diaryvault.app.auth

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Emulator / browser / Play Services preflight for Firebase Phone Auth.
 *
 * Prefer **Play Integrity** (then SafetyNet inside the Auth SDK) so we never open the
 * storage-partitioned `*.firebaseapp.com` reCAPTCHA page. Custom Tabs are only a fallback
 * when Integrity cannot attest (sideloaded APK without SHA, no Play Services, etc.).
 *
 * Forcing reCAPTCHA when Chrome/Custom Tabs cannot keep `sessionStorage` strands users on
 * “Unable to process request due to missing initial state”.
 */
object PhoneAuthEnvironment {
    private const val TAG = "PhoneAuthEnv"

    /** Prefer Chrome family so Firebase RecaptchaActivity returns via Custom Tabs, not Firefox. */
    private val PREFERRED_CUSTOM_TABS = listOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
    )

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT
        val model = Build.MODEL
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val manufacturer = Build.MANUFACTURER
        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            fp.contains("emulator", ignoreCase = true) ||
            fp.contains("vbox", ignoreCase = true) ||
            model.contains("google_sdk", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true) ||
            brand.startsWith("generic") && device.startsWith("generic") ||
            product.contains("sdk", ignoreCase = true) ||
            product.contains("emulator", ignoreCase = true) ||
            product.contains("google_sdk", ignoreCase = true) ||
            manufacturer.contains("Genymotion", ignoreCase = true) ||
            Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true)
    }

    /** True when Google Play services can power Play Integrity / SafetyNet. */
    fun hasPlayServices(context: Context): Boolean {
        val code = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context.applicationContext)
        return code == ConnectionResult.SUCCESS
    }

    /** True when any package can handle https VIEW (Chrome / browser / Custom Tabs host). */
    fun hasHttpsBrowser(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val matches = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return matches.isNotEmpty()
    }

    /**
     * Preferred Custom Tabs package — Chrome first so the Auth redirect returns to the app
     * instead of a full browser (Firefox often blocks native-app link opens → missing state).
     */
    fun customTabsPackage(context: Context): String? =
        runCatching {
            CustomTabsClient.getPackageName(context, PREFERRED_CUSTOM_TABS, true)
                ?: CustomTabsClient.getPackageName(context, null)
        }.getOrNull()

    fun hasCustomTabs(context: Context): Boolean = !customTabsPackage(context).isNullOrBlank()

    /**
     * Warm the Custom Tabs service so Firebase's RecaptchaActivity can open quickly
     * **and return** to the app when Integrity falls back to reCAPTCHA.
     * Best-effort — never throws.
     */
    fun warmCustomTabs(context: Context) {
        val pkg = customTabsPackage(context) ?: return
        try {
            val ok = CustomTabsClient.bindCustomTabsService(
                context.applicationContext,
                pkg,
                object : CustomTabsServiceConnection() {
                    override fun onCustomTabsServiceConnected(
                        name: ComponentName,
                        client: CustomTabsClient,
                    ) {
                        client.warmup(0L)
                        Log.i(TAG, "Custom Tabs warmed pkg=$pkg")
                        runCatching {
                            context.applicationContext.unbindService(this)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit
                },
            )
            Log.i(TAG, "Custom Tabs bind pkg=$pkg ok=$ok")
        } catch (e: Exception) {
            Log.w(TAG, "Custom Tabs warm failed", e)
        }
    }

    fun describe(context: Context): String =
        "emulator=${isEmulator()} playServices=${hasPlayServices(context)} " +
            "httpsBrowser=${hasHttpsBrowser(context)} customTabs=${customTabsPackage(context)}"
}
