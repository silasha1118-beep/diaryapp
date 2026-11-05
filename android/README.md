# 日記金庫 — Android (Kotlin + Jetpack Compose)

Compose app that mirrors the iOS `DiaryVault/` product: phone SMS OTP → three tabs（金庫／鏡子／設定）→ vault list, Mirror chat, Settings. **Firebase Phone Auth + Firestore text sync** match iOS phase 1 when `google-services.json` is present; otherwise SMS falls back to a local stub.

**Package:** `com.diaryvault.app`  
**Display name:** 日記金庫  
**minSdk:** 26 · **compileSdk / targetSdk:** 34  
**Firebase project:** `diaryappsilas` (same as iOS)

> Do not open or edit the iOS Xcode project from this folder. `DiaryVault/` stays the iOS source of truth.

## What Silas needs on his Mac

1. **Android Studio** Hedgehog or newer — [developer.android.com/studio](https://developer.android.com/studio)
2. Android SDK Platform **34**, Build-Tools, an emulator image (API 26+)
3. **JDK 17** (prefer the IDE’s embedded JDK)

## Firebase setup (required for real SMS + sync)

`google-services.json` is **gitignored** (same rule as iOS `GoogleService-Info.plist`). Without it the app builds and runs in **stub SMS** mode (no Firestore writes).

### 1. Add the Android app in Firebase Console

1. Open [Firebase Console](https://console.firebase.google.com/) → project **`diaryappsilas`**.
2. **Project settings → Your apps → Add app → Android**.
3. Android package name: **`com.diaryvault.app`** (must match `applicationId` in `app/build.gradle.kts`).
4. Download **`google-services.json`** and place it at:

```text
android/app/google-services.json
```

(See `android/app/google-services.json.example` for the expected shape — never commit the real file.)

5. Leave `DIARY_SMS_PROVIDER` empty (default). Empty + JSON present → Firebase. Set `stub` only to force local OTP.

### 2. Register SHA-1 for Phone Auth

Phone Auth app verification needs the signing certificate fingerprint:

```bash
# Debug keystore (default Android Studio debug key)
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

Copy the **SHA-1** (and ideally SHA-256) → Firebase Console → Project settings → Your apps → Android `com.diaryvault.app` → **Add fingerprint**.

Without SHA-1, SMS send often fails with app-verification / reCAPTCHA errors.

### 3. Enable Phone Auth + regions

1. Authentication → Sign-in method → **Phone** → Enable.
2. Allow SMS regions for launch markets: **TW / HK / CN / MY / SG / TH / JP** (same checklist as iOS README).
3. Optional: add test phone numbers under Phone → Phone numbers for testing (skips real SMS while iterating).

### 4. Firestore (same as iOS)

1. Firestore Database **`(default)`** — paste / publish [`firebase/firestore.rules`](../firebase/firestore.rules).
2. Sync path: `users/{uid}/entries/{entryId}` with soft-delete (`deleted` / `deletedAt`). Free includes sync.

## Open in Android Studio

1. Clone / pull this repo.
2. Place `android/app/google-services.json` if you want live Firebase.
3. **File → Open…** → select the **`android/`** directory.
4. Trust the Gradle project and wait for sync.

## Run

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

## How to test (Silas checklist)

1. Confirm Settings → Sync shows **Local session only** / **Signed out** before login.
2. Sign in with phone OTP (DEBUG banner: “SMS backend: Firebase” when JSON is present).
3. Create a diary entry → Settings → **Sync now** → status becomes **Synced**.
4. In Console → Firestore → `users/{your Auth UID}/entries` — docs appear; delete in-app soft-sets `deleted: true` (no hard delete).
5. If SMS fails: re-check SHA-1, package name, Phone provider enabled, region policy.

## Optional: cloud Mirror AI

```bash
export DIARY_AI_API_KEY="…"
export DIARY_AI_PROVIDER=openai   # or anthropic / xai
./gradlew :app:assembleDebug
```

## Layout

```text
android/
  app/google-services.json.example
  app/src/main/java/com/diaryvault/app/
    account/     # AccountSession (Firebase Auth state)
    auth/        # SMS OTP (Firebase + stub)
    config/      # AuthConfig, FirebaseBootstrap, AI / quotas
    data/        # AppPreferences, DiaryStore (JSON + soft-delete + sync)
    sync/        # DiaryCloudSync (Firestore schema parity with iOS)
    ui/          # auth, diary, mirror, settings, theme
```
