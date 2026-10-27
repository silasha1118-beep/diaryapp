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

### Optional: enable cloud AI stub

By default the Mirror tab uses a **local mock**. To point at a real cloud endpoint (no secrets in git):

1. In Xcode → Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables, add:
   - `DIARY_AI_BASE_URL` — e.g. `https://your-api.example.com`
   - `DIARY_AI_API_KEY` — your secret (scheme-local / CI secret only)
   - `DIARY_AI_MODEL` — optional (default `mirror-v0`)
2. Restart the app. Settings → AI should show **Cloud API**.

The stub client `POST`s to `{baseURL}/v1/mirror` with mirror-style constraints and expects JSON `{ "text": "...", "citedEntryIds": ["…"] }`. Data residency narrative: **Taiwan (TW)**.

## What works in this scaffold

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language strip) | Works (local) |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | Works with **mock** replies; cloud path gated by env |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |

## What is stubbed / omitted

- **Cloud LLM** — protocol + `CloudMirrorAIClient` stub; no production backend or keys in repo
- **Account / sync / Taiwan server** — local vault only; residency is narrative + config label
- **Style learning / RAG / “like me?” feedback** — not built yet
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
    Services/        # DiaryStore, Mirror AI protocol/mock/cloud, LocalizationManager
    Config/          # AIConfig (env / Info.plist — no secrets)
    Views/           # Onboarding, Diary, Mirror, Settings + vault theme
    Resources/       # en, zh-Hant, zh-Hans Localizable.strings
```

## Languages

In-app switchable (not only system locale):

- **繁體中文** (`zh-Hant`)
- **简体中文** (`zh-Hans`)
- **English** (`en`)

## License / secrets

Do not commit API keys. Prefer Xcode scheme environment variables or a gitignored `Secrets.xcconfig`.
