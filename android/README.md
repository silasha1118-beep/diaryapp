# Diary Vault — Android MVP

Kotlin + Jetpack Compose scaffold for the living-author loop: **write → AI learns → gentle mirror-style advice** (reflect + guiding questions). Memorial / voice clone / executor UI is stubbed in Settings only.

> **Near-term runnable target:** Android (this folder). The iOS Xcode project under `DiaryVault/` remains in the repo for when a Mac is available; do not treat iOS as broken — it simply cannot be built on this Linux agent.

## Requirements

- Android Studio Hedgehog+ **or** JDK 17+ and Android SDK 34
- Set `ANDROID_HOME` (or create `android/local.properties` with `sdk.dir=…`)

## Open in Android Studio

1. **File → Open** the `android/` directory (not the repo root alone).
2. Let Gradle sync.
3. Run the **app** configuration on an emulator or device (API 26+).

## Build with Gradle

```bash
cd android
# optional: echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Other useful tasks: `./gradlew :app:assembleRelease` (unsigned), `./gradlew clean`.

### Optional: enable cloud AI stub

By default Mirror uses a **local mock**. To point at a real cloud endpoint (no secrets in git), export before Gradle assemble / run:

```bash
export DIARY_AI_BASE_URL="https://your-api.example.com"
export DIARY_AI_API_KEY="…"          # CI / local env only
export DIARY_AI_MODEL="mirror-v0"    # optional
./gradlew :app:assembleDebug
```

The stub `POST`s to `{baseURL}/v1/mirror` and expects JSON `{ "text": "...", "citedEntryIds": ["…"] }`. Data residency narrative: **Taiwan (TW)**.

## What works

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language) | Works (local) |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | Works with **mock** replies; cloud path gated by env |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |

## Stubbed / omitted

- Cloud LLM — `CloudMirrorAIClient` stub; no production backend or keys in repo
- Account / sync / Taiwan server — local vault only
- Style learning / RAG / “like me?” feedback
- Voice input / memorial mode / voice clone / legacy executor UI
- Push / contextual reminder scheduling — toggle only
- Unit / UI tests

## Layout

```text
android/
  app/src/main/java/com/diaryvault/app/
    config/       # AIConfig (env / BuildConfig — no secrets)
    data/         # AppPreferences, DiaryStore
    model/        # DiaryEntry, MirrorMessage, AppLanguage
    service/      # Mirror AI protocol / mock / cloud
    ui/           # Onboarding, Diary, Mirror, Settings, theme
  app/src/main/res/values{,-zh-rTW,-zh-rCN}/
```
