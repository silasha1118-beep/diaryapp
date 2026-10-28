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

### Enable cloud Mirror AI

By default Mirror uses a **local mock**. To call a real cloud chat API (**no secrets in git**), export before Gradle assemble / run:

**Anthropic (Claude):**

```bash
export DIARY_AI_API_KEY="sk-ant-…"       # required; CI / local env only — never commit
export DIARY_AI_PROVIDER=anthropic
export DIARY_AI_MODEL="claude-sonnet-4-5"              # optional (default when provider=anthropic)
export DIARY_AI_BASE_URL="https://api.anthropic.com"   # optional
export DIARY_AI_MAX_TOKENS="400"         # optional
export DIARY_AI_MAX_CONTEXT_ENTRIES="8"  # optional
export DIARY_AI_DAILY_REQUEST_LIMIT="40" # optional; 0 = unlimited
./gradlew :app:assembleDebug
```

**OpenAI-compatible** (default — Groq / DeepSeek / OpenAI):

```bash
export DIARY_AI_API_KEY="sk-…"           # required to leave mock
export DIARY_AI_PROVIDER=openai          # optional (default)
export DIARY_AI_BASE_URL="https://api.openai.com"   # Groq: https://api.groq.com/openai
export DIARY_AI_MODEL="gpt-4o-mini"      # optional
./gradlew :app:assembleDebug
```

Values are baked into `BuildConfig` at assemble time. Rebuild after changing env vars.

- Anthropic → `POST {baseURL}/v1/messages` (`x-api-key` + `anthropic-version`)
- OpenAI-compatible → `POST {baseURL}/v1/chat/completions` (`Authorization: Bearer …`)

Same mirror system prompt + diary excerpts (**RAG-lite**) + usage guards. Data residency narrative: **Taiwan (TW)**. Provider also auto-detects when `DIARY_AI_BASE_URL` contains `anthropic`.

## What works

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language) | Works (local) |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | **Mock** by default; **cloud** (OpenAI-compatible or Anthropic) when `DIARY_AI_API_KEY` is set |
| Diary → Mirror context | Recent entries as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, local daily request counter stub |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |

## Stubbed / omitted

- **Account / Play Billing** — `account/AccountSession` + `billing/BillingConfig` + Settings stubs only; no Google Sign-In / BillingClient (iOS SIWA + StoreKit first)
- Cloud sync / Taiwan server — local vault only
- Fine-tuning — not used; learning = prompt context only
- Voice / video diary — deferred
- Voice input / memorial mode / voice clone / legacy executor UI
- Push / contextual reminder scheduling — toggle only
- Unit / UI tests

## Layout

```text
android/
  app/src/main/java/com/diaryvault/app/
    account/      # AccountSession stub (local prefs)
    billing/      # BillingConfig + BillingStub (no Play Billing yet)
    config/       # AIConfig (env / BuildConfig — no secrets)
    data/         # AppPreferences, DiaryStore
    model/        # DiaryEntry, MirrorMessage, AppLanguage
    service/      # Mirror AI protocol / mock / cloud / prompt / usage
    ui/           # Onboarding, Diary, Mirror, Settings, theme
  app/src/main/res/values{,-zh-rTW,-zh-rCN}/
```
