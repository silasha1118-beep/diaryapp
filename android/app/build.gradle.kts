plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.diaryvault.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.diaryvault.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // Cloud AI — set via env at assemble time; never commit real keys.
        buildConfigField("String", "DIARY_AI_PROVIDER", "\"${System.getenv("DIARY_AI_PROVIDER") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_BASE_URL", "\"${System.getenv("DIARY_AI_BASE_URL") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_API_KEY", "\"${System.getenv("DIARY_AI_API_KEY") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MODEL", "\"${System.getenv("DIARY_AI_MODEL") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MAX_TOKENS", "\"${System.getenv("DIARY_AI_MAX_TOKENS") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MAX_CONTEXT_ENTRIES", "\"${System.getenv("DIARY_AI_MAX_CONTEXT_ENTRIES") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_FREE_DAILY_REQUEST_LIMIT", "\"${System.getenv("DIARY_AI_FREE_DAILY_REQUEST_LIMIT") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_DAILY_REQUEST_LIMIT", "\"${System.getenv("DIARY_AI_DAILY_REQUEST_LIMIT") ?: ""}\"")
        // SMS / Firebase — leave empty to auto-select firebase when google-services.json is present.
        buildConfigField("String", "DIARY_SMS_PROVIDER", "\"${System.getenv("DIARY_SMS_PROVIDER") ?: ""}\"")
        buildConfigField("String", "DIARY_SMS_STUB_CODE", "\"${System.getenv("DIARY_SMS_STUB_CODE") ?: ""}\"")
        buildConfigField("String", "DIARY_FIREBASE_PROJECT_ID", "\"${System.getenv("DIARY_FIREBASE_PROJECT_ID") ?: "diaryappsilas"}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

// Apply only when Silas has placed the real (gitignored) google-services.json —
// mirrors iOS optional GoogleService-Info.plist so stub builds still assemble.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}
