# Diary Vault（日記金庫）

Personal diary vault MVP scaffold (iOS + Android). Near-term runnable target: **Android** (no Mac required). Living-author loop: **write → AI learns → gentle mirror-style advice** (reflect + guiding questions, not coach prescriptions). Memorial / voice clone / legacy executor UI is **out of Phase 1** and only mentioned as a stub in Settings.

Product plan (source of truth for decisions): kept in the project workspace docs — not duplicated here.


## Android (near-term runnable)

> **No Mac / Xcode for now** — the Kotlin + Jetpack Compose app under [`android/`](android/) is the **near-term build & run target** on Linux/Windows. The iOS project in `DiaryVault/` stays intact for when a Mac is available.

See **[android/README.md](android/README.md)** for Android Studio / `./gradlew :app:assembleDebug`.

## Open in Xcode

1. Clone this repo on a Mac with **Xcode 15+** (iOS 17 deployment target).
2. Open:

   ```bash
   open DiaryVault/DiaryVault.xcodeproj
   ```

3. Select an iPhone Simulator (or device), then **Run** (⌘R).
4. If signing prompts appear for a physical device, set your **Team** under the DiaryVault target → Signing & Capabilities. Simulator needs no team.

### Enable cloud Mirror AI (OpenAI-compatible)

By default the Mirror tab uses a **local mock**. To call a real cloud chat API (**no secrets in git**):

1. In Xcode → **Product → Scheme → Edit Scheme… → Run → Arguments → Environment Variables**, add:

   | Variable | Required | Example / default |
   |----------|----------|-------------------|
   | `DIARY_AI_API_KEY` | **Yes** (to leave mock) | `sk-…` (scheme-local only) |
   | `DIARY_AI_BASE_URL` | No | `https://api.openai.com` (default) |
   | `DIARY_AI_MODEL` | No | `gpt-4o-mini` (default) |
   | `DIARY_AI_MAX_TOKENS` | No | `400` |
   | `DIARY_AI_MAX_CONTEXT_ENTRIES` | No | `8` |
   | `DIARY_AI_DAILY_REQUEST_LIMIT` | No | `40` (`0` = unlimited) |

2. Leave Info.plist AI keys empty in the repo. Prefer scheme env (or a gitignored `Secrets.xcconfig`).
3. Restart the app. Settings → AI should show **Cloud API**.

The client `POST`s to `{baseURL}/v1/chat/completions` (OpenAI-compatible). The system prompt enforces **mirror style** (reflect + guiding questions, no coach prescriptions) and injects recent diary excerpts (**RAG-lite**, not fine-tuning). Data residency narrative: **Taiwan (TW)**.

Compatible hosts: OpenAI, Azure OpenAI (chat path), or any provider that speaks the same `/v1/chat/completions` shape — set `DIARY_AI_BASE_URL` accordingly.

Cost / billing notes for later App Store pricing: see project doc `docs/ai-cost-and-billing.md` (workspace).

## What works in this scaffold

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language strip) | Works (local) |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | **Mock** by default; **OpenAI-compatible cloud** when `DIARY_AI_API_KEY` is set |
| Diary → Mirror context | Recent entries passed as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, local daily request counter stub |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |

## What is stubbed / omitted

- **App Store IAP / user billing** — not in this PR; provider keys are developer-local only
- **Account / sync / Taiwan server** — local vault only; residency is narrative + config label
- **Fine-tuning / private model training** — not used; learning = prompt context only
- **Voice input / transcription** — omitted
- **Memorial mode, voice clone, legacy executor** — Settings stub copy only; no Phase 1 UI
- **Push / contextual reminder scheduling** — toggle only
- **App Store assets / App Icon art** — placeholder asset catalog slot
- **Unit / UI tests** — not included in this scaffold

## Project layout

```
DiaryVault/
  DiaryVault.xcodeproj
  DiaryVault/
    DiaryVaultApp.swift
    Models/          # AppState, DiaryEntry, MirrorMessage
    Services/        # DiaryStore, Mirror AI (mock/cloud/prompt/usage), LocalizationManager
    Config/          # AIConfig (env / Info.plist — no secrets)
    Views/           # Onboarding, Diary, Mirror, Settings + vault theme
    Resources/       # en, zh-Hant, zh-Hans Localizable.strings
android/             # Near-term runnable Compose MVP (same Mirror AI contract)
```

## Languages

In-app switchable (not only system locale):

- **繁體中文** (`zh-Hant`)
- **简体中文** (`zh-Hans`)
- **English** (`en`)

## License / secrets

Do not commit API keys. Prefer Xcode scheme environment variables or a gitignored `Secrets.xcconfig`.
