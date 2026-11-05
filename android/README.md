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

### 2. Register SHA-1 **and SHA-256** for Phone Auth (required for live SMS)

Android Phone Auth completes Play Integrity / reCAPTCHA using the **signing certificate** of the APK you install. iOS SHA does **not** cover Android.

```bash
# Debug keystore (default Android Studio / Gradle debug key)
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

If you install via a different machine or CI keystore, fingerprint **that** key — not only the Mac default.

Copy **SHA-1** and **SHA-256** → Firebase Console → Project settings → Your apps → Android `com.diaryvault.app` → **Add fingerprint**. Then **reinstall** the debug APK (Firebase config is baked at build time; fingerprints are checked at runtime after Console save, but a clean reinstall avoids stale builds).

Without matching SHA fingerprints, send fails with app-verification / reCAPTCHA / `invalid-app-credential` — the UI now surfaces that as the app-verification error string.

### 3. Enable Phone Auth + SMS region allowlist

1. Authentication → Sign-in method → **Phone** → Enable (already on for `diaryappsilas`).
2. Authentication → Settings → **SMS region policy** — allow **TW / HK / CN / MY / SG / TH / JP**. HK (`+852`) and TW (`+886`) must be allowed for live OTP to those numbers.
3. **App Check**: if App Check is **enforced** on Auth and Android is not registered / attested, Phone Auth fails. For debug while wiring SMS, leave App Check unenforced on Authentication, or register the Android debug token.

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

## How to test live SMS (Silas checklist)

Use a **physical device with Google Play** (or an emulator **with** Play Store). Prefer real `+852` / `+886` numbers — the project already has successful Phone Auth users historically; Android must complete its own Play Integrity / reCAPTCHA path.

1. Confirm `android/app/google-services.json` is present and package is `com.diaryvault.app`.
2. Confirm **SHA-1 + SHA-256** of **this** debug keystore are on the Android app in Console (step 2 above).
3. Rebuild + install: `./gradlew :app:installDebug`.
4. Open the login gate → pick **+852** → enter national number (e.g. `84028001`) → **Send**.
5. Watch for:
   - A reCAPTCHA / browser sheet (complete it) **or** silent Play Integrity
   - OTP field appears → Logcat `FirebasePhoneSMS` shows `onCodeSent` (success) or `onVerificationFailed` (error text also on screen)
6. Enter the SMS code → continue. Settings → Sync should show a live Firebase session.
7. If OTP UI never appears and an error shows: fix that Console item (SHA, region, App Check). If OTP UI appears but SMS never arrives after `onCodeSent`: carrier / Firebase SMS delivery for that region — not a missing Activity callback.

> DA-42 note: mainland `+86` delivery has been flaky historically; **HK `+852` / TW `+886` should work** when regions are allowlisted and Android app verification succeeds.

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
