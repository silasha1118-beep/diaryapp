package com.diaryvault.app.auth

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.diaryvault.app.BuildConfig
import com.diaryvault.app.config.AuthConfig
import com.diaryvault.app.config.FirebaseBootstrap
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Which SMS backend produced this session (drives verify + UI hints). */
enum class SmsAuthPath {
    FIREBASE,
    ALIYUN,
    STUB,
}

/** Result of requesting an SMS OTP. */
data class SMSCodeSession(
    val verificationID: String,
    val phoneE164: String,
    /** Stub-only hint for DEBUG UI; always null for Firebase. */
    val stubDebugHint: String? = null,
    /**
     * True when Firebase completed verification without an SMS (Play instant / auto-retrieval).
     * UI must sign in immediately — do **not** wait for an SMS that will never arrive.
     */
    val autoVerified: Boolean = false,
    val path: SmsAuthPath = SmsAuthPath.FIREBASE,
)

/** Signed-in identity after a successful OTP verify. */
data class SMSAuthUser(
    val userID: String,
    val phoneE164: String,
)

sealed class SMSAuthError(message: String) : Exception(message) {
    data object InvalidPhone : SMSAuthError("Invalid phone number")
    /**
     * @param firebaseErrorCode raw Firebase Auth error code when known
     *   (e.g. `ERROR_INVALID_VERIFICATION_CODE`) — shown in DEBUG UI / Logcat.
     */
    data class InvalidCode(val firebaseErrorCode: String = "") : SMSAuthError(
        "Invalid verification code",
    )
    /** Firebase verificationId expired / consumed (often Instant Verification raced onCodeSent). */
    data class SessionExpired(val firebaseErrorCode: String = "") : SMSAuthError(
        "Verification session expired. Request a new code.",
    )
    data class NotConfigured(val detail: String) : SMSAuthError(detail)
    data object RateLimited : SMSAuthError("Too many attempts; try again later")
    /**
     * Firebase Phone Auth live SMS requires Blaze (pay-as-you-go). Spark projects
     * may still return `onCodeSent` for Console **test** numbers, but carrier SMS fails.
     */
    data object BillingRequired : SMSAuthError(
        "Firebase project must be on the Blaze plan to send live SMS.",
    )
    data object RegionNotAllowed : SMSAuthError("SMS is not allowed for this phone region")
    data object AppVerificationFailed : SMSAuthError(
        "App verification failed (Play Integrity / reCAPTCHA).",
    )
    /** Emulator / device has no browser so reCAPTCHA Custom Tabs cannot open. */
    data object RecaptchaUnavailable : SMSAuthError(
        "Security check needs a browser. Use a Play Store emulator with Chrome, or a real device.",
    )
    /**
     * reCAPTCHA Custom Tab failed (sessionStorage / missing initial state / Chrome ANR).
     * User must leave the dead browser page and retry in-app — prefer a real device.
     */
    data object RecaptchaBrowserFailed : SMSAuthError(
        "Browser security check failed. Close that page and try again, or use a real phone.",
    )
    data object TimedOut : SMSAuthError("SMS request timed out. Check network, then try again.")
    data object EmptyVerificationId : SMSAuthError(
        "Verification did not complete — no verification id from Firebase.",
    )
    data class Underlying(val detail: String) : SMSAuthError(detail)
}

interface SMSAuthProviding {
    /**
     * Request a live SMS OTP. [activity] must be a foreground Activity so Firebase can
     * complete Play Integrity / reCAPTCHA (do **not** call from a background-only context).
     *
     * @param resend when true, uses the stored [ForceResendingToken] for the same E.164 only.
     */
    suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean = false,
    ): SMSCodeSession

    suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser

    /** Drop force-resend token / pending auto credential (e.g. user changed number). */
    fun clearSendState() {}
}

object SMSAuthProviderFactory {
    fun make(context: Context): SMSAuthProviding {
        FirebaseBootstrap.configureIfNeeded(context)
        val app = context.applicationContext
        val resolution = AuthConfig.smsProviderResolution
        val kind = resolution.kind
        // Never silently stay on stub when google-services was baked into this APK.
        if (kind == AuthConfig.SMSProviderKind.STUB && FirebaseBootstrap.hasGoogleServicesJson) {
            val explicitStub = resolution is AuthConfig.SMSProviderResolution.Explicit &&
                resolution.provider == AuthConfig.SMSProviderKind.STUB
            if (!explicitStub) {
                Log.e(
                    "SMSAuthFactory",
                    "google-services.json present but resolution chose stub — forcing Firebase",
                )
                return routedFirebase(app)
            }
            Log.w(
                "SMSAuthFactory",
                "DIARY_SMS_PROVIDER=stub with google-services.json present — local OTP only, no live SMS",
            )
        }
        return when (kind) {
            AuthConfig.SMSProviderKind.FIREBASE -> routedFirebase(app)
            AuthConfig.SMSProviderKind.TWILIO -> TwilioReadySMSAuthProvider()
            AuthConfig.SMSProviderKind.STUB -> StubSMSAuthProvider()
        }
    }

    /**
     * Dual SMS:
     * - **+86:** Aliyun Cloud Functions when secrets are set; if keys/Functions missing
     *   (or CF stub mode) → Firebase Phone Auth fallback so Console / DEBUG test codes work
     * - other dials → Firebase Phone Auth (Play Integrity preferred — see #77)
     * Aliyun AccessKeys stay on the server — never in the APK.
     */
    private fun routedFirebase(app: Context): SMSAuthProviding {
        val firebase = FirebasePhoneSMSAuthProvider(app)
        return RoutingSMSAuthProvider(
            chinaMainland = AliyunSmsAuthProvider(app, firebaseFallback = firebase),
            other = firebase,
        )
    }
}

/** Local OTP stub — does **not** send SMS. */
class StubSMSAuthProvider : SMSAuthProviding {
    private val issued = mutableMapOf<String, String>()

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!PhoneNumberNormalizer.isPlausibleE164(normalized)) throw SMSAuthError.InvalidPhone
        val verificationID = "stub-" + UUID.randomUUID()
        val expected = AuthConfig.stubAcceptCode ?: "%06d".format((0..999_999).random())
        issued[verificationID] = expected
        return SMSCodeSession(
            verificationID = verificationID,
            phoneE164 = normalized,
            stubDebugHint = expected,
            path = SmsAuthPath.STUB,
        )
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        val trimmed = PhoneNumberNormalizer.normalizeOtpDigits(code)
        if (trimmed.length != 6 || !trimmed.all { it.isDigit() }) throw SMSAuthError.InvalidCode()
        val fixed = AuthConfig.stubAcceptCode
        if (fixed != null) {
            if (trimmed != fixed) throw SMSAuthError.InvalidCode()
        } else {
            val expected = issued[session.verificationID]
            if (expected != null && trimmed != expected) throw SMSAuthError.InvalidCode()
        }
        return SMSAuthUser(
            userID = "phone-" + session.phoneE164.filter { it.isDigit() },
            phoneE164 = session.phoneE164,
        )
    }
}

/**
 * Real Firebase Phone Auth: send SMS OTP → verify code → `signInWithCredential`.
 *
 * Requires:
 * - `android/app/google-services.json` (gitignored)
 * - Phone provider enabled + SMS region allowlist
 * - SHA-1 / SHA-256 of the signing key (Play Integrity / reCAPTCHA)
 * - Foreground Activity (Custom Tabs for reCAPTCHA when Integrity cannot attest a sideloaded APK)
 */
class FirebasePhoneSMSAuthProvider(
    private val appContext: Context,
) : SMSAuthProviding {
    private val forceResendToken = AtomicReference<ForceResendingToken?>(null)
    private val lastPhoneE164 = AtomicReference<String?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun clearSendState() {
        forceResendToken.set(null)
        lastPhoneE164.set(null)
        pendingAutoCredential.set(null)
    }

    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        val normalized = PhoneNumberNormalizer.normalizeToE164(phoneE164)
        if (!PhoneNumberNormalizer.isPlausibleE164(normalized)) throw SMSAuthError.InvalidPhone

        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.hasGoogleServicesJson || !FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "google-services.json missing. Place it at " +
                    "C:\\dev\\diaryapp\\android\\app\\google-services.json " +
                    "(or android/app/google-services.json), confirm project_id is " +
                    "${FirebaseBootstrap.EXPECTED_PROJECT_ID}, rebuild Debug.",
            )
        }

        val runtimeProject = FirebaseBootstrap.projectId
        val expectedProject = AuthConfig.firebaseProjectID
            ?: FirebaseBootstrap.EXPECTED_PROJECT_ID
        // Blank projectId = JSON not applied / incomplete resources. Stop before Phone Auth
        // so Debug never shows a fake INVALID_CODE for Console test numbers.
        if (runtimeProject.isNullOrBlank()) {
            throw SMSAuthError.NotConfigured(
                "Firebase project_id is blank (google-services not applied). " +
                    "Open C:\\dev\\diaryapp\\android\\app\\google-services.json, " +
                    "confirm \"project_id\": \"${FirebaseBootstrap.EXPECTED_PROJECT_ID}\", " +
                    "Sync Gradle, uninstall app, reinstall Debug.",
            )
        }
        if (BuildConfig.DEBUG && runtimeProject != expectedProject) {
            throw SMSAuthError.NotConfigured(
                "Firebase project_id=$runtimeProject — expected $expectedProject. " +
                    "Re-download google-services.json for Android com.diaryvault.app " +
                    "from Firebase Console project $expectedProject.",
            )
        }

        if (activity.isFinishing || activity.isDestroyed) {
            throw SMSAuthError.Underlying("Activity is not available for Phone Auth verification.")
        }

        applySMSLanguageCode()
        val mode = prepareAppVerification(activity)
        // Drop any Instant Verification credential from a prior send so verify()
        // cannot sign in with a stale auto credential against a new onCodeSent session.
        pendingAutoCredential.set(null)

        // DEBUG: official Firebase test API — forces onVerificationCompleted for Console
        // Phone numbers for testing (must also be on the Console list for this project).
        val debugTestCode = DebugPhoneTestCodes.codeFor(normalized)
        applyDebugAutoRetrievedSms(normalized, debugTestCode)

        Log.i(
            TAG,
            "verifyPhoneNumber start e164=$normalized resend=$resend mode=$mode " +
                "pkg=${appContext.packageName} activity=${activity.javaClass.simpleName} " +
                "project=$runtimeProject expectedProject=$expectedProject " +
                "debugAutoSms=${debugTestCode != null} " +
                "env=${PhoneAuthEnvironment.describe(activity)}",
        )

        // Phone Auth + reCAPTCHA / Play Integrity must start on the main thread with a live Activity.
        val timeoutMs = when {
            mode == AppVerificationMode.DISABLED_FOR_TESTING -> SEND_TIMEOUT_TEST_MS
            // Emulator Custom Tabs / Chrome often ANR — fail back to in-app sooner.
            PhoneAuthEnvironment.isEmulator() -> SEND_TIMEOUT_EMULATOR_MS
            else -> SEND_TIMEOUT_DEFAULT_MS
        }
        return try {
            withTimeout(timeoutMs) {
                withContext(Dispatchers.Main.immediate) {
                    awaitVerification(activity, normalized, resend, mode)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(
                TAG,
                "verifyPhoneNumber timed out for $normalized mode=$mode " +
                    "(reCAPTCHA not completed, Chrome ANR, or Integrity hung)",
            )
            if (mode == AppVerificationMode.DISABLED_FOR_TESTING) {
                throw SMSAuthError.RecaptchaUnavailable
            }
            // Dead firebaseapp.com page / "Chrome isn't responding" → clear in-app copy.
            if (PhoneAuthEnvironment.isEmulator()) {
                throw SMSAuthError.RecaptchaBrowserFailed
            }
            throw SMSAuthError.TimedOut
        }
    }

    private enum class AppVerificationMode {
        /**
         * Prefer Play Integrity (+ SafetyNet inside Auth SDK); reCAPTCHA Custom Tabs only
         * if Integrity cannot attest. Never force the browser path.
         */
        DEFAULT,
        /**
         * DEBUG emulator / no browser: skip Integrity + reCAPTCHA so Console **test**
         * phone numbers can complete without Chrome (AVD Chrome often ANRs).
         */
        DISABLED_FOR_TESTING,
    }

    /**
     * Prefer Play Integrity over reCAPTCHA. Forcing Custom Tabs on emulator hits
     * sessionStorage / “Chrome isn't responding” and strands users on firebaseapp.com.
     */
    private fun prepareAppVerification(activity: Activity): AppVerificationMode {
        val settings = FirebaseAuth.getInstance().firebaseAuthSettings
        val hasBrowser = PhoneAuthEnvironment.hasHttpsBrowser(activity)
        val hasCustomTabs = PhoneAuthEnvironment.hasCustomTabs(activity)
        val emulator = PhoneAuthEnvironment.isEmulator()
        val playServices = PhoneAuthEnvironment.hasPlayServices(activity)

        // Never force the storage-partitioned browser reCAPTCHA path — it causes
        // “missing initial state” / Chrome ANR. Integrity first; Custom Tabs only as SDK fallback.
        settings.forceRecaptchaFlowForTesting(false)

        if (!BuildConfig.DEBUG) {
            settings.setAppVerificationDisabledForTesting(false)
            // Clear any leftover DEBUG auto-SMS pairing from a prior debug install/process.
            settings.setAutoRetrievedSmsCodeForPhoneNumber(null, null)
            if (!playServices && !hasBrowser) {
                Log.e(TAG, "No Play Services and no https browser — cannot verify app")
                throw SMSAuthError.RecaptchaUnavailable
            }
            if (hasCustomTabs) PhoneAuthEnvironment.warmCustomTabs(activity)
            Log.i(
                TAG,
                "Release: Play Integrity preferred; Custom Tabs warmed=${hasCustomTabs} " +
                    "playServices=$playServices",
            )
            return AppVerificationMode.DEFAULT
        }

        // DEBUG --------------------------------------------------------------
        // Firebase docs: disable app verification when exercising Console test numbers
        // (emulator OR physical). Leaving Play Integrity on for sideloaded debug APKs
        // on MIUI has produced onCodeSent + typed 123456 → INVALID_VERIFICATION_CODE
        // even when the number is on Phone numbers for testing.
        settings.setAppVerificationDisabledForTesting(true)
        Log.w(
            TAG,
            "DEBUG: setAppVerificationDisabledForTesting(true) " +
                "emulator=$emulator playServices=$playServices hasBrowser=$hasBrowser " +
                "env=${PhoneAuthEnvironment.describe(activity)}",
        )
        return AppVerificationMode.DISABLED_FOR_TESTING
    }

    /**
     * Pair a Console test number with its fixed code so Firebase triggers
     * [PhoneAuthProvider.OnVerificationStateChangedCallbacks.onVerificationCompleted]
     * instead of relying on typed [PhoneAuthProvider.getCredential] (fragile on MIUI).
     */
    private fun applyDebugAutoRetrievedSms(e164: String, testCode: String?) {
        val settings = FirebaseAuth.getInstance().firebaseAuthSettings
        if (!BuildConfig.DEBUG || testCode.isNullOrBlank()) {
            settings.setAutoRetrievedSmsCodeForPhoneNumber(null, null)
            return
        }
        settings.setAutoRetrievedSmsCodeForPhoneNumber(e164, testCode)
        Log.i(
            TAG,
            "DEBUG setAutoRetrievedSmsCodeForPhoneNumber e164=$e164 codeLen=${testCode.length} " +
                "(requires same pair on Console Phone numbers for testing for project " +
                "${FirebaseBootstrap.projectId})",
        )
    }

    private suspend fun awaitVerification(
        activity: Activity,
        normalized: String,
        resend: Boolean,
        mode: AppVerificationMode,
    ): SMSCodeSession =
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            fun resumeOnce(block: () -> Unit) {
                if (resumed.compareAndSet(false, true) && cont.isActive) block()
            }

            // When Chrome Custom Tabs ANRs / user closes the dead firebaseapp.com page,
            // Activity resumes without a Firebase callback — fail back to in-app error.
            val leftForBrowser = AtomicBoolean(false)
            var lifecycleObserver: LifecycleEventObserver? = null
            val abandonRunnable = Runnable {
                if (resumed.get()) return@Runnable
                Log.e(TAG, "Custom Tab / browser abandoned without Firebase callback")
                resumeOnce {
                    cont.resumeWithException(SMSAuthError.RecaptchaBrowserFailed)
                }
            }
            val owner = activity as? LifecycleOwner
            if (mode == AppVerificationMode.DEFAULT && owner != null) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_PAUSE -> {
                            // Likely opening Play Integrity sheet or reCAPTCHA Custom Tab.
                            leftForBrowser.set(true)
                            mainHandler.removeCallbacks(abandonRunnable)
                        }
                        Lifecycle.Event.ON_RESUME -> {
                            if (leftForBrowser.get() && !resumed.get()) {
                                // Grace: Integrity/reCAPTCHA may still complete after return.
                                // If Chrome ANR'd or sessionStorage died, no callback arrives.
                                mainHandler.removeCallbacks(abandonRunnable)
                                mainHandler.postDelayed(
                                    abandonRunnable,
                                    BROWSER_ABANDON_GRACE_MS,
                                )
                            }
                        }
                        else -> Unit
                    }
                }
                lifecycleObserver = observer
                owner.lifecycle.addObserver(observer)
            }

            fun cleanupLifecycle() {
                mainHandler.removeCallbacks(abandonRunnable)
                lifecycleObserver?.let { obs ->
                    owner?.lifecycle?.removeObserver(obs)
                }
                lifecycleObserver = null
            }

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Instant verification / SMS Retriever — no SMS for the user to type.
                    val smsPresent = credential.smsCode != null
                    val alreadyResumed = resumed.get()
                    Log.i(
                        TAG,
                        "onVerificationCompleted auto smsCodePresent=$smsPresent " +
                            "alreadyResumed=$alreadyResumed (onCodeSent may have won)",
                    )
                    pendingAutoCredential.set(credential)
                    lastPhoneE164.set(normalized)
                    if (alreadyResumed) {
                        // Late Instant Verification after onCodeSent: keep credential for
                        // verify(); do not change the OTP UI session (verificationId may
                        // already be burned for getCredential + typed code).
                        Log.w(
                            TAG,
                            "Late Instant Verification after onCodeSent — " +
                                "holding pendingAutoCredential for verify()",
                        )
                        return
                    }
                    cleanupLifecycle()
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = "auto",
                                phoneE164 = normalized,
                                stubDebugHint = null,
                                autoVerified = true,
                                path = SmsAuthPath.FIREBASE,
                            ),
                        )
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e(
                        TAG,
                        "onVerificationFailed code=${(e as? FirebaseAuthException)?.errorCode} msg=${e.message}",
                        e,
                    )
                    cleanupLifecycle()
                    resumeOnce { cont.resumeWithException(mapFirebaseError(e)) }
                }

                override fun onCodeSent(verificationId: String, token: ForceResendingToken) {
                    // Treat blank id as failure — never advance OTP UI on an empty session.
                    if (verificationId.isBlank()) {
                        Log.e(TAG, "onCodeSent with blank verificationId — failing closed")
                        cleanupLifecycle()
                        resumeOnce {
                            cont.resumeWithException(SMSAuthError.EmptyVerificationId)
                        }
                        return
                    }
                    // onCodeSent ≠ carrier delivery. Console "Phone numbers for testing"
                    // also fires this with no SMS — user must enter the fixed test code.
                    Log.i(
                        TAG,
                        "onCodeSent e164=$normalized verificationIdLen=${verificationId.length} " +
                            "prefix=${verificationId.take(6)}… " +
                            "carrier SMS only if: NOT on Phone numbers for testing, " +
                            "Blaze billing, SMS region allowed (HK=+852), SHA-1/SHA-256 match",
                    )
                    forceResendToken.set(token)
                    lastPhoneE164.set(normalized)
                    prefs().edit().putString(KEY_VERIFICATION_ID, verificationId).apply()
                    cleanupLifecycle()
                    resumeOnce {
                        cont.resume(
                            SMSCodeSession(
                                verificationID = verificationId,
                                phoneE164 = normalized,
                                stubDebugHint = null,
                                autoVerified = false,
                                path = SmsAuthPath.FIREBASE,
                            ),
                        )
                    }
                }

                override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                    // SMS may still arrive; session already resumed via onCodeSent when applicable.
                    // Do NOT overwrite prefs after onCodeSent with a different id — that caused
                    // stale verificationId confusion when timeout=0 fires this immediately.
                    Log.d(
                        TAG,
                        "onCodeAutoRetrievalTimeOut verificationIdLen=${verificationId.length} " +
                            "alreadyResumed=${resumed.get()}",
                    )
                    if (verificationId.isNotBlank() && !resumed.get()) {
                        prefs().edit().putString(KEY_VERIFICATION_ID, verificationId).apply()
                    }
                }
            }

            // Timeout semantics (Firebase): 0 = disable SMS auto-retrieval only
            // (Instant Verification can still fire). When DEBUG pairs a Console test
            // number via setAutoRetrievedSmsCodeForPhoneNumber, keep a normal window
            // so onVerificationCompleted can win. Otherwise DEBUG uses 0 to avoid
            // MIUI SMS-retriever races burning a typed verificationId.
            val debugTestCode = DebugPhoneTestCodes.codeFor(normalized)
            val autoRetrieveSeconds = when {
                !BuildConfig.DEBUG -> 60L
                debugTestCode != null -> 60L
                else -> 0L
            }
            Log.i(
                TAG,
                "PhoneAuthOptions timeout=${autoRetrieveSeconds}s " +
                    "debugAutoSms=${debugTestCode != null} DEBUG=${BuildConfig.DEBUG} " +
                    "e164=$normalized",
            )
            val builder = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(normalized)
                .setTimeout(autoRetrieveSeconds, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            val storedToken = forceResendToken.get()
            val samePhone = lastPhoneE164.get() == normalized
            when {
                resend && storedToken != null && samePhone -> {
                    builder.setForceResendingToken(storedToken)
                    Log.i(TAG, "applying forceResendingToken for resend")
                }
                resend && (storedToken == null || !samePhone) -> {
                    // Fall through as a fresh send rather than failing closed.
                    Log.w(TAG, "resend requested without usable token — fresh verifyPhoneNumber")
                    forceResendToken.set(null)
                }
                !resend -> {
                    // Never reuse a stale token on a fresh Send (or after “change number”).
                    if (!samePhone) forceResendToken.set(null)
                    // Explicit fresh send: do not attach forceResendingToken.
                }
            }

            val options = builder.build()
            val start: () -> Unit = {
                try {
                    PhoneAuthProvider.verifyPhoneNumber(options)
                } catch (e: Exception) {
                    Log.e(TAG, "verifyPhoneNumber threw", e)
                    cleanupLifecycle()
                    resumeOnce { cont.resumeWithException(mapFirebaseError(e)) }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                start()
            } else {
                mainHandler.post { start() }
            }

            cont.invokeOnCancellation {
                Log.w(TAG, "sendCode cancelled for $normalized")
                cleanupLifecycle()
            }
        }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        FirebaseBootstrap.configureIfNeeded(appContext)
        if (!FirebaseBootstrap.isConfigured) {
            throw SMSAuthError.NotConfigured(
                "Firebase is not configured. Place google-services.json at " +
                    "C:\\dev\\diaryapp\\android\\app\\google-services.json " +
                    "with project_id=${FirebaseBootstrap.EXPECTED_PROJECT_ID}, rebuild Debug.",
            )
        }
        if (FirebaseBootstrap.projectId.isNullOrBlank()) {
            throw SMSAuthError.NotConfigured(
                "Firebase project_id is blank. Open " +
                    "C:\\dev\\diaryapp\\android\\app\\google-services.json and confirm " +
                    "\"project_id\": \"${FirebaseBootstrap.EXPECTED_PROJECT_ID}\", then rebuild.",
            )
        }

        val trimmed = PhoneNumberNormalizer.normalizeOtpDigits(code)

        // Auto-retrieval / Instant Verification for the same send that set autoVerified.
        if (session.autoVerified) {
            pendingAutoCredential.getAndSet(null)?.let { auto ->
                Log.i(TAG, "verify via pending auto credential (autoVerified session)")
                return signIn(auto, session.phoneE164)
            }
            throw SMSAuthError.Underlying("Auto-verification expired. Request a new code.")
        }

        // Late Instant Verification / SMS Retriever after onCodeSent: prefer that
        // credential when its smsCode matches the typed code, or when Instant
        // Verification completed with no smsCode (typed getCredential often fails then).
        val pending = pendingAutoCredential.getAndSet(null)
        if (pending != null) {
            val autoCode = pending.smsCode?.let { PhoneNumberNormalizer.normalizeOtpDigits(it) }
            when {
                autoCode != null && autoCode == trimmed -> {
                    Log.i(TAG, "verify via late pending auto credential (smsCode matched typed)")
                    return signIn(pending, session.phoneE164)
                }
                autoCode == null -> {
                    Log.w(
                        TAG,
                        "verify: late Instant Verification (no smsCode) — " +
                            "signing in with pending credential; typed getCredential often burned",
                    )
                    try {
                        return signIn(pending, session.phoneE164)
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "late Instant Verification sign-in failed — not falling through " +
                                "to typed getCredential (verificationId likely consumed): " +
                                "${e.message}",
                            e,
                        )
                        // Falling through previously mapped a burned id as「驗證碼不符」.
                        throw SMSAuthError.SessionExpired(
                            firebaseErrorCode = (e as? FirebaseAuthException)?.errorCode.orEmpty(),
                        )
                    }
                }
                else -> {
                    Log.w(
                        TAG,
                        "verify: pending auto smsCode did not match typed " +
                            "(autoLen=${autoCode.length} typedLen=${trimmed.length}) — " +
                            "using typed getCredential",
                    )
                }
            }
        }

        return verifyWithTypedCode(trimmed, session)
    }

    private suspend fun verifyWithTypedCode(trimmed: String, session: SMSCodeSession): SMSAuthUser {
        if (trimmed.length < 4 || trimmed.any { it !in '0'..'9' }) throw SMSAuthError.InvalidCode()

        // Must use the verificationId from the same onCodeSent that filled otpSession.
        // Prefs are last-resort recovery only when the session id is blank (should be rare).
        val fromSession = session.verificationID.trim()
        val verificationID = when {
            fromSession.isNotEmpty() && fromSession != "auto" -> fromSession
            else -> prefs().getString(KEY_VERIFICATION_ID, "").orEmpty().trim()
        }
        if (verificationID.isEmpty() || verificationID == "auto") {
            throw SMSAuthError.Underlying("Missing verification session. Request a new code.")
        }
        if (fromSession.isNotEmpty() && fromSession != "auto") {
            // Session from this onCodeSent always wins over any older prefs value.
            if (fromSession != prefs().getString(KEY_VERIFICATION_ID, null)) {
                Log.w(
                    TAG,
                    "verify: session verificationId differs from prefs — using session " +
                        "(same onCodeSent). sessionLen=${fromSession.length} " +
                        "prefsLen=${prefs().getString(KEY_VERIFICATION_ID, null)?.length ?: 0}",
                )
            }
            prefs().edit().putString(KEY_VERIFICATION_ID, fromSession).apply()
        }
        Log.i(
            TAG,
            "verifyCredential provider=FirebasePhone verificationIdLen=${verificationID.length} " +
                "prefix=${verificationID.take(6)}… codeLen=${trimmed.length} " +
                "fromSession=${fromSession.isNotEmpty() && fromSession != "auto"} " +
                "e164=${session.phoneE164} project=${FirebaseBootstrap.projectId}",
        )

        val credential = PhoneAuthProvider.getCredential(verificationID, trimmed)
        return try {
            val user = signIn(credential, session.phoneE164)
            prefs().edit().remove(KEY_VERIFICATION_ID).apply()
            user
        } catch (e: Exception) {
            val errCode = (e as? FirebaseAuthException)?.errorCode.orEmpty()
            Log.e(
                TAG,
                "signInWithCredential failed code=$errCode msg=${e.message} " +
                    "verificationIdLen=${verificationID.length} e164=${session.phoneE164} " +
                    "project=${FirebaseBootstrap.projectId}",
                e,
            )
            throw mapFirebaseError(e)
        }
    }

    private suspend fun signIn(credential: PhoneAuthCredential, fallbackPhone: String): SMSAuthUser {
        val result = FirebaseAuth.getInstance().signInWithCredential(credential).await()
        val user = result.user ?: throw SMSAuthError.Underlying("Firebase Auth returned no user")
        return SMSAuthUser(
            userID = user.uid,
            phoneE164 = user.phoneNumber ?: fallbackPhone,
        )
    }

    private fun applySMSLanguageCode() {
        val stored = appContext.getSharedPreferences("diary_vault_prefs", Context.MODE_PRIVATE)
            .getString("appLanguage", "")
            .orEmpty()
        val lang = when (stored) {
            "zh-Hant" -> "zh-TW"
            "zh-Hans" -> "zh-CN"
            "ja" -> "ja"
            "th" -> "th"
            else -> "en"
        }
        FirebaseAuth.getInstance().setLanguageCode(lang)
    }

    private fun prefs() =
        appContext.getSharedPreferences("diary.firebase.auth", Context.MODE_PRIVATE)

    private fun mapFirebaseError(error: Exception): SMSAuthError {
        val message = error.message.orEmpty()
        val lowered = message.lowercase()
        val code = when (error) {
            is FirebaseAuthException -> error.errorCode
            else -> ""
        }
        Log.e(
            TAG,
            "mapFirebaseError code=$code class=${error.javaClass.simpleName} msg=$message " +
                "project=${FirebaseBootstrap.projectId}",
        )

        if (error is FirebaseTooManyRequestsException) return SMSAuthError.RateLimited

        // IMPORTANT: do NOT map all FirebaseAuthInvalidCredentialsException → InvalidCode first.
        // ERROR_SESSION_EXPIRED / ERROR_INVALID_VERIFICATION_ID also use that class and were
        // previously shown as「驗證碼不符」with a misleading Console test-list hint.

        when {
            code.contains("INVALID_PHONE", ignoreCase = true) ||
                lowered.contains("invalid format") ||
                lowered.contains("invalid phone") -> return SMSAuthError.InvalidPhone
            code.contains("SESSION_EXPIRED", ignoreCase = true) ||
                code.contains("INVALID_VERIFICATION_ID", ignoreCase = true) ||
                lowered.contains("session expired") ||
                lowered.contains("code has expired") ||
                (lowered.contains("verification id") && lowered.contains("invalid")) ->
                return SMSAuthError.SessionExpired(firebaseErrorCode = code)
            code.contains("INVALID_VERIFICATION_CODE", ignoreCase = true) ||
                (
                    (
                        code.contains("INVALID_VERIFICATION", ignoreCase = true) ||
                            lowered.contains("verification code") ||
                            lowered.contains("invalid code") ||
                            lowered.contains("sms code")
                        ) &&
                        !code.contains("INVALID_VERIFICATION_ID", ignoreCase = true)
                    ) ->
                return SMSAuthError.InvalidCode(firebaseErrorCode = code)
            error is FirebaseAuthInvalidCredentialsException -> {
                // Broad credential failure with no specific code — prefer InvalidCode but
                // keep the raw firebase code for DEBUG UI.
                return if (lowered.contains("phone")) {
                    SMSAuthError.InvalidPhone
                } else {
                    SMSAuthError.InvalidCode(firebaseErrorCode = code.ifBlank { "INVALID_CREDENTIAL" })
                }
            }
            code.contains("BILLING_NOT_ENABLED", ignoreCase = true) ||
                lowered.contains("billing not enabled") ||
                lowered.contains("upgrade to blaze") ||
                lowered.contains("blaze plan") ||
                (lowered.contains("billing") &&
                    (lowered.contains("required") ||
                        lowered.contains("enable") ||
                        lowered.contains("upgrade"))) ->
                return SMSAuthError.BillingRequired
            code.contains("TOO_MANY", ignoreCase = true) ||
                code.contains("QUOTA", ignoreCase = true) ||
                lowered.contains("too many") ||
                lowered.contains("quota") -> return SMSAuthError.RateLimited
            code.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
                if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
                if (lowered.contains("billing") || lowered.contains("blaze")) {
                    return SMSAuthError.BillingRequired
                }
                return SMSAuthError.NotConfigured(
                    "Phone sign-in is disabled. Enable Authentication → Sign-in method → Phone.",
                )
            }
            code.contains("MISSING_ACTIVITY", ignoreCase = true) ||
                lowered.contains("missing activity") ||
                lowered.contains("recaptchaactivity") ||
                lowered.contains("no browser") ||
                lowered.contains("custom tab") ->
                return SMSAuthError.RecaptchaUnavailable
            // Dead *.firebaseapp.com page / partitioned storage / Chrome ANR on emulator.
            lowered.contains("missing initial state") ||
                lowered.contains("sessionstorage") ||
                lowered.contains("session storage") ||
                lowered.contains("web_internal_error") ||
                lowered.contains("chrome isn't responding") ||
                lowered.contains("browser closed") ||
                code.contains("WEB_INTERNAL_ERROR", ignoreCase = true) ||
                code.contains("WEB_CONTEXT_CANCELED", ignoreCase = true) ||
                code.contains("ERROR_WEB_CONTEXT_CANCELED", ignoreCase = true) ->
                return SMSAuthError.RecaptchaBrowserFailed
            code.contains("APP_NOT_AUTHORIZED", ignoreCase = true) ||
                code.contains("INVALID_APP_CREDENTIAL", ignoreCase = true) ||
                code.contains("CAPTCHA", ignoreCase = true) ||
                code.contains("MISSING_APP_CREDENTIAL", ignoreCase = true) ||
                code.contains("ERROR_APP_NOT_AUTHORIZED", ignoreCase = true) ||
                code.contains("MISSING_CLIENT_IDENTIFIER", ignoreCase = true) ||
                lowered.contains("app credential") ||
                lowered.contains("recaptcha") ||
                lowered.contains("play integrity") ||
                lowered.contains("safetynet") ||
                lowered.contains("app verification") ||
                lowered.contains("sha-1") ||
                lowered.contains("sha1") ||
                lowered.contains("this app is not authorized") ->
                return SMSAuthError.AppVerificationFailed
        }
        if (looksLikeRegionBlock(lowered)) return SMSAuthError.RegionNotAllowed
        val detail = buildString {
            if (code.isNotBlank()) append("[$code] ")
            append(message.ifBlank { "Firebase Auth error" })
        }
        return SMSAuthError.Underlying(detail)
    }

    private fun looksLikeRegionBlock(lowered: String): Boolean =
        lowered.contains("region") ||
            lowered.contains("blocked") ||
            lowered.contains("not authorized to send") ||
            lowered.contains("cannot be used") ||
            lowered.contains("sms cannot")

    companion object {
        private const val TAG = "FirebasePhoneSMS"
        private const val KEY_VERIFICATION_ID = "diary.firebase.authVerificationID"
        /** Default Integrity → optional reCAPTCHA fallback. */
        private const val SEND_TIMEOUT_DEFAULT_MS = 120_000L
        /** Emulator: Chrome Custom Tabs often ANR — fail back to in-app sooner. */
        private const val SEND_TIMEOUT_EMULATOR_MS = 45_000L
        /** Test-number path with app verification disabled — should return quickly. */
        private const val SEND_TIMEOUT_TEST_MS = 45_000L
        /**
         * After Activity resumes from Custom Tab / Integrity sheet without a Firebase
         * callback, wait this long then treat as abandoned browser (Chrome ANR / dead page).
         */
        private const val BROWSER_ABANDON_GRACE_MS = 8_000L
        private val pendingAutoCredential = AtomicReference<PhoneAuthCredential?>(null)
    }
}

/** Placeholder for Twilio Verify — fail closed. */
class TwilioReadySMSAuthProvider : SMSAuthProviding {
    override suspend fun sendCode(
        activity: Activity,
        phoneE164: String,
        resend: Boolean,
    ): SMSCodeSession {
        throw SMSAuthError.NotConfigured(
            "Twilio Verify client is not linked. Keep DIARY_SMS_PROVIDER empty/firebase.",
        )
    }

    override suspend fun verify(code: String, session: SMSCodeSession): SMSAuthUser {
        throw SMSAuthError.NotConfigured("Twilio Verify client is not linked.")
    }
}
