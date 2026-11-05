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

### 3b. Console knobs that still block live SMS **after** the app code is solid

These are the only project settings that commonly produce **OTP UI with no real text**:

| Knob | Where | What goes wrong |
|------|--------|-----------------|
| **Blaze billing** | Firebase pricing / upgrade | Since 2024-09, **Spark cannot send Phone Auth SMS**. Upgrade `diaryappsilas` to Blaze (pay-as-you-go). |
| **SMS region policy** | Auth → Settings → SMS region policy | Default allowlist can be empty — `+852` / `+886` must be allowed. |
| **Phone numbers for testing** | Auth → Sign-in method → Phone → Phone numbers for testing | Numbers on this list get **`onCodeSent` with no carrier SMS**. Use the fixed test code from Console, or **remove** the number to receive a real text. |
| **SHA-1 + SHA-256** | Project settings → Android `com.diaryvault.app` | Must match the **keystore that signed the installed APK** (this Mac’s `~/.android/debug.keystore` for `installDebug`). |
| **App Check enforced** | App Check → Authentication | Enforce without an Android debug token → send fails (red error). Leave unenforced while wiring. |
| **API key HTTP referrer** | Google Cloud → Credentials → Browser key | reCAPTCHA needs `https://PROJECT_ID.firebaseapp.com` allowed (or unrestricted) for the Auth API key. |

App code: blank `verificationId` fails closed; Play instant-verify auto-signs-in. **Emulator / no-browser** debug builds **disable app verification** (use Console **test phone numbers** — real SMS needs Chrome). Debug builds **with** a browser force visible reCAPTCHA and warm Custom Tabs. Missing browser fails fast with a clear UI error instead of hanging until timeout.

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

Use a **physical device with Google Play**. Prefer a real `+852` / `+886` number that is **not** on the Console “Phone numbers for testing” list.

1. Confirm Blaze + region allowlist + SHA fingerprints (tables above).
2. Confirm `android/app/google-services.json` is present (`com.diaryvault.app`) and `DIARY_SMS_PROVIDER` is empty.
3. Rebuild + install: `./gradlew :app:installDebug`.
4. Login → **+852** → national number → **Send**.
5. **Emulator without Chrome:** add the number under Console **Phone numbers for testing**, Send, enter the fixed test code (app disables Integrity/reCAPTCHA on no-browser debug so it does not hang). **Real SMS:** use a Play Store AVD with Chrome (or a physical device) and complete the browser reCAPTCHA sheet.
6. OTP field = Firebase returned a verification id. Enter the SMS / test code (or tap **Resend** after 60s).
7. If Send shows a red “needs a browser” error: install Chrome on the AVD or switch to a real device — do not wait for a sheet that will never open.

> DA-42 note: mainland `+86` delivery has been flaky historically; **HK `+852` / TW `+886` should work** when regions are allowlisted, billing is Blaze, and the number is not a Console test number.

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
