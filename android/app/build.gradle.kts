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
        buildConfigField("String", "DIARY_AI_BASE_URL", "\"${System.getenv("DIARY_AI_BASE_URL") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_API_KEY", "\"${System.getenv("DIARY_AI_API_KEY") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MODEL", "\"${System.getenv("DIARY_AI_MODEL") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MAX_TOKENS", "\"${System.getenv("DIARY_AI_MAX_TOKENS") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_MAX_CONTEXT_ENTRIES", "\"${System.getenv("DIARY_AI_MAX_CONTEXT_ENTRIES") ?: ""}\"")
        buildConfigField("String", "DIARY_AI_DAILY_REQUEST_LIMIT", "\"${System.getenv("DIARY_AI_DAILY_REQUEST_LIMIT") ?: ""}\"")
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
    implementation("com.google.code.gson:gson:2.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
