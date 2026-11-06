import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Resolve Mirror AI / SMS config from (in order):
 * 1. `android/local.properties` (gitignored — preferred on Windows Studio)
 * 2. Gradle `-P` / `gradle.properties`
 * 3. Process environment
 *
 * Never commit real keys. Prefer `XAI_API_KEY` or `DIARY_AI_API_KEY` in local.properties.
 */
fun diaryAiProp(key: String): String {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        val props = Properties()
        localFile.inputStream().use { props.load(it) }
        val fromLocal = props.getProperty(key)?.trim().orEmpty()
        if (fromLocal.isNotEmpty()) return fromLocal
    }
    val fromProject = project.findProperty(key)?.toString()?.trim().orEmpty()
    if (fromProject.isNotEmpty()) return fromProject
    return System.getenv(key)?.trim().orEmpty()
}

fun diaryAiApiKey(): String {
    val diary = diaryAiProp("DIARY_AI_API_KEY")
    if (diary.isNotEmpty()) return diary
    // xAI console env name — same key, OpenAI-compatible chat completions.
    return diaryAiProp("XAI_API_KEY")
}

fun escapeBuildConfigString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "")
        .replace("\r", "")

android {
    namespace = "com.diaryvault.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.diaryvault.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // Cloud AI — local.properties / env at assemble time; never commit real keys.
        // Working provider (product pref): xAI Grok — defaults applied in AIConfig when key is set.
        buildConfigField("String", "DIARY_AI_PROVIDER", "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_PROVIDER"))}\"")
        buildConfigField("String", "DIARY_AI_BASE_URL", "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_BASE_URL"))}\"")
        buildConfigField("String", "DIARY_AI_API_KEY", "\"${escapeBuildConfigString(diaryAiApiKey())}\"")
        buildConfigField("String", "DIARY_AI_MODEL", "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_MODEL"))}\"")
        buildConfigField("String", "DIARY_AI_MAX_TOKENS", "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_MAX_TOKENS"))}\"")
        buildConfigField("String", "DIARY_AI_MAX_CONTEXT_ENTRIES", "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_MAX_CONTEXT_ENTRIES"))}\"")
        buildConfigField(
            "String",
            "DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT",
            "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT"))}\"",
        )
        buildConfigField(
            "String",
            "DIARY_AI_FREE_DAILY_REQUEST_LIMIT",
            "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_FREE_DAILY_REQUEST_LIMIT"))}\"",
        )
        buildConfigField(
            "String",
            "DIARY_AI_DAILY_REQUEST_LIMIT",
            "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_DAILY_REQUEST_LIMIT"))}\"",
        )
        buildConfigField(
            "String",
            "DIARY_AI_PRO_DAILY_REQUEST_LIMIT",
            "\"${escapeBuildConfigString(diaryAiProp("DIARY_AI_PRO_DAILY_REQUEST_LIMIT"))}\"",
        )
        // SMS / Firebase — leave empty to auto-select firebase when google-services.json is present.
        buildConfigField("String", "DIARY_SMS_PROVIDER", "\"${escapeBuildConfigString(diaryAiProp("DIARY_SMS_PROVIDER"))}\"")
        buildConfigField("String", "DIARY_SMS_STUB_CODE", "\"${escapeBuildConfigString(diaryAiProp("DIARY_SMS_STUB_CODE"))}\"")
        buildConfigField(
            "String",
            "DIARY_FIREBASE_PROJECT_ID",
            "\"${escapeBuildConfigString(diaryAiProp("DIARY_FIREBASE_PROJECT_ID").ifEmpty { "diaryappsilas" })}\"",
        )
        // Overridden per buildType — release must stay empty (no hardcoded test OTPs).
        buildConfigField("String", "DIARY_DEBUG_PHONE_TEST_CODES", "\"\"")
    }

    buildTypes {
        debug {
            // Console Phone numbers for testing (diaryappsilas). Used only when BuildConfig.DEBUG
            // with FirebaseAuthSettings.setAutoRetrievedSmsCodeForPhoneNumber.
            val fromEnv = System.getenv("DIARY_DEBUG_PHONE_TEST_CODES")?.trim().orEmpty()
            val defaults = "+85284028001:123456,+8615014531587:123456"
            buildConfigField(
                "String",
                "DIARY_DEBUG_PHONE_TEST_CODES",
                "\"${(fromEnv.ifEmpty { defaults }).replace("\"", "\\\"")}\"",
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "DIARY_DEBUG_PHONE_TEST_CODES", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.code.gson:gson:2.11.0")

    // Firebase Auth (Phone SMS) + Firestore diary sync — same project as iOS (diaryappsilas).
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.5.1")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    // Diary media bytes — same Storage paths as iOS (`users/{uid}/entries/{entryId}/{file}`).
    implementation("com.google.firebase:firebase-storage-ktx")
    // Callable Functions for +86 Aliyun SMS OTP (keys stay server-side).
    implementation("com.google.firebase:firebase-functions-ktx")
    // Vault detail video playback (voice uses MediaPlayer).
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    // Phone Auth app verification: Play Integrity (+ SafetyNet fallback) and SMS Retriever.
    implementation("com.google.android.play:integrity:1.4.0")
    implementation("com.google.android.gms:play-services-base:18.5.0")
    implementation("com.google.android.gms:play-services-auth-api-phone:18.1.0")
    // reCAPTCHA fallback opens a Chrome Custom Tab when Play Integrity cannot attest (sideloaded debug).
    // Prefer Custom Tabs (Chrome) so the Auth redirect returns to the app instead of a dead
    // *.firebaseapp.com page (sessionStorage / "Chrome isn't responding" on emulator).
    implementation("androidx.browser:browser:1.8.0")

    // Author Plus / Author Pro subscriptions — same product IDs as iOS StoreKit.
    // Compiles without Play Console SKUs; empty catalog shows a clear unavailable state.
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

// Apply only when Silas has placed the real (gitignored) google-services.json —
// mirrors iOS optional GoogleService-Info.plist so stub builds still assemble.
val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

/**
 * Debug Phone Auth for diaryappsilas requires a real google-services.json.
 * Escape hatch for stub-only CI / local builds: `-Pdiary.allowStubSms=true`
 * or env `DIARY_SMS_PROVIDER=stub`.
 */
fun readGoogleServicesProjectId(jsonFile: java.io.File): String? {
    if (!jsonFile.exists()) return null
    val match = Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"").find(jsonFile.readText())
    return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

fun readGoogleServicesPackageName(jsonFile: java.io.File): String? {
    if (!jsonFile.exists()) return null
    val match = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(jsonFile.readText())
    return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

fun stubSmsExplicitlyAllowed(): Boolean {
    if (project.findProperty("diary.allowStubSms")?.toString()?.equals("true", ignoreCase = true) == true) {
        return true
    }
    return System.getenv("DIARY_SMS_PROVIDER")?.trim().equals("stub", ignoreCase = true)
}

tasks.configureEach {
    if (name != "preDebugBuild") return@configureEach
    doFirst {
        val expectedProject = "diaryappsilas"
        val expectedPackage = "com.diaryvault.app"
        val windowsPath = "C:\\dev\\diaryapp\\android\\app\\google-services.json"
        if (!googleServicesFile.exists()) {
            if (stubSmsExplicitlyAllowed()) {
                logger.warn(
                    "diary.allowStubSms / DIARY_SMS_PROVIDER=stub: building Debug without " +
                        "google-services.json (local stub OTP only).",
                )
                return@doFirst
            }
            throw GradleException(
                """
                Missing android/app/google-services.json (gitignored — never committed).

                Redmi Debug Phone Auth needs project_id=$expectedProject wired into the APK.
                Without this file the app falls back to stub OTP and shows
                Firebase project=? error=INVALID_CODE for Console test codes like 123456.

                Silas — Windows steps (简体 UI):
                1. Open https://console.firebase.google.com/ → project $expectedProject
                2. 项目设置 (Project settings) → 您的应用 → Android `$expectedPackage`
                   (Add app if missing; package must be exactly `$expectedPackage`)
                3. Download google-services.json
                4. Save / overwrite:
                   $windowsPath
                5. Open the file and confirm: "project_id": "$expectedProject"
                6. Android Studio Sync → uninstall app on Redmi → Run Debug

                Stub-only (no Firebase): .\gradlew :app:assembleDebug -Pdiary.allowStubSms=true
                """.trimIndent(),
            )
        }
        val projectId = readGoogleServicesProjectId(googleServicesFile)
        if (projectId != expectedProject) {
            throw GradleException(
                "android/app/google-services.json project_id=${projectId ?: "<missing>"} — " +
                    "expected \"$expectedProject\". " +
                    "Windows path: $windowsPath — re-download from Firebase Console for " +
                    "project $expectedProject / Android $expectedPackage.",
            )
        }
        val packageName = readGoogleServicesPackageName(googleServicesFile)
        if (packageName != null && packageName != expectedPackage) {
            throw GradleException(
                "android/app/google-services.json package_name=$packageName — " +
                    "expected \"$expectedPackage\". Download the Android app config for " +
                    "$expectedPackage from project $expectedProject.",
            )
        }
        logger.lifecycle(
            "google-services.json OK project_id=$projectId package=${packageName ?: "?"}",
        )
    }
}
