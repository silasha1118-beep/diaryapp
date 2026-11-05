# 日記金庫 — Android (Kotlin + Jetpack Compose)

Compose scaffold that visually/functionally mirrors the iOS `DiaryVault/` app and the HarmonyOS UI notes: fake phone OTP → three tabs（金庫／鏡子／設定）→ vault list, composer, Mirror home+chat, Settings sections. Mock data / stubs first — no Firebase SMS or Play Billing in this folder yet.

**Package:** `com.diaryvault.app`  
**Display name:** 日記金庫  
**minSdk:** 26 · **compileSdk / targetSdk:** 34

> Do not open or edit the iOS Xcode project from this folder. `DiaryVault/` stays the iOS source of truth.

## What Silas needs on his Mac

1. **Android Studio** Hedgehog or newer (or Meerkat / Narwhal) — [developer.android.com/studio](https://developer.android.com/studio)
2. During setup, install:
   - Android SDK Platform **34**
   - Android SDK Build-Tools
   - A system image for an emulator (API 26+; API 34 recommended)
3. **JDK 17** (Android Studio bundles one — prefer the IDE’s embedded JDK)

## Open in Android Studio

1. Clone / pull this repo.
2. **File → Open…** and select the **`android/`** directory (not only the repo root).
3. Trust the Gradle project and wait for sync (`Gradle sync`).
4. If sync asks for an SDK path, set it under **Settings → Languages & Frameworks → Android SDK**, or create `android/local.properties`:

```properties
sdk.dir=/Users/YOUR_MAC_USERNAME/Library/Android/sdk
```

(`local.properties` is gitignored — never commit it.)

## Run on an emulator

1. **Tools → Device Manager → Create Device** (e.g. Pixel 7, system image API 34).
2. Start the emulator.
3. Select the **app** run configuration → green **Run**.

Or from Terminal:

```bash
cd android
./gradlew :app:installDebug
```

## Run on a physical phone (USB debugging)

1. On the phone: **Settings → About phone** → tap **Build number** 7 times → enable Developer options.
2. **Developer options → USB debugging** → ON.
3. Plug in USB; accept the “Allow USB debugging?” prompt.
4. In Android Studio, pick the device in the device dropdown → **Run**.
5. Confirm with `adb devices` (Platform-Tools must be installed).

## Build with Gradle (CLI)

```bash
cd android
./gradlew :app:assembleDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Screens in this scaffold

| Screen | Notes |
|--------|--------|
| Onboarding | Brand / privacy / optional name / language |
| Phone OTP login | Fake gate like HarmonyOS `Index` — any number → send → any code |
| 金庫 | Cream `#F3EEE6`, burgundy `#6B3A2E`, title 日記金庫 + seal underline + `{name} 的金庫`, FAB `+`, date/title/body rows |
| Composer | Title / mood / body + media icon row **stubs** |
| 鏡子 | Home (今日一問) → in-tab chat with **mock** reply |
| 設定 | Account, link phone stub, sync, billing, privacy, language, reminders, vault name, legal — UI only |

## Optional: cloud Mirror AI

Default Mirror is local mock. To call a cloud chat API (**no secrets in git**):

```bash
export DIARY_AI_API_KEY="…"
export DIARY_AI_PROVIDER=openai   # or anthropic
./gradlew :app:assembleDebug
```

See also root `README.md` for env var details.

## Layout

```text
android/
  app/src/main/java/com/diaryvault/app/
    account/     # AccountSession stub (phone + local)
    billing/     # BillingConfig stub
    config/      # AI / upload quota config
    data/        # AppPreferences, DiaryStore (JSON on device)
    model/
    service/     # Mirror AI mock / cloud
    ui/          # auth, diary, mirror, settings, theme
  app/src/main/res/values{,-zh-rTW,-zh-rCN}/
```
