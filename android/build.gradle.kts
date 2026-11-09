plugins {
    // AGP 8.9.1+ required for compileSdk / targetSdk 36 (Play API-36 requirement).
    id("com.android.application") version "8.9.1" apply false
    // Kotlin 2.2.x required: billing-ktx 8.1+ pulls kotlin-stdlib 2.2.10 metadata.
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
