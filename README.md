# Diary Vault（日記金庫）

Personal diary vault MVP scaffold (iOS + Android). Near-term runnable target: **Android** (no Mac required). Living-author loop: **write → AI learns → gentle mirror-style advice** (reflect + guiding questions, not coach prescriptions). Memorial / voice clone / legacy executor UI is **out of Phase 1** and only mentioned as a stub in Settings.

Product plan (source of truth for decisions): kept in the project workspace docs — not duplicated here. Account / billing sketch: `docs/account-and-billing-plan.md` (workspace).


## Android (near-term runnable)

> **No Mac / Xcode for now** — the Kotlin + Jetpack Compose app under [`android/`](android/) is the **near-term build & run target** on Linux/Windows. The iOS project in `DiaryVault/` stays intact for when a Mac is available.

See **[android/README.md](android/README.md)** for Android Studio / `./gradlew :app:assembleDebug`.

Android **account / Play Billing** in this PR: **stub hooks only** (Settings shows local session / phone stub + Author Plus product ID placeholders). Full SMS OTP / Play Billing is deferred — iOS phone OTP + StoreKit is the first path.

## Open in Xcode

1. Clone this repo on a Mac with **Xcode 15+** (iOS 17 deployment target).
2. Open:

   ```bash
   open DiaryVault/DiaryVault.xcodeproj
   ```

3. Select an iPhone Simulator (or device), then **Run** (⌘R).
4. If signing prompts appear for a physical device, set your **Team** under the DiaryVault target → Signing & Capabilities. Simulator needs no team for basic runs; **Sign in with Apple** needs the capability enabled for your Team (entitlements file is already in the project).

### Account (phone SMS OTP — all regions)

Flow: **Onboarding → Auth gate (phone + OTP) → main tabs**.

- **Primary**: mobile number + SMS one-time code for **CN / HK / TW** (and any phone that can receive SMS). WeChat / Google are **not** primary.
- **Optional backup**: Sign in with Apple (collapsed under “Or use Sign in with Apple”).
- Session is **local** (`AccountSession` in UserDefaults). No auth backend in this MVP.
- Settings shows signed-in identity / provider and **Sign out** (returns to the auth gate).
- **DEBUG** builds also offer “Dev: continue with local session”.
- Do **not** commit secrets. OTP / identity tokens are not sent to a real backend yet.

#### SMS provider (stub · Firebase / Twilio ready)

| Variable | Purpose |
|----------|---------|
| `DIARY_SMS_PROVIDER` | `stub` (default) · `firebase` · `twilio` |
| `DIARY_SMS_STUB_CODE` | Optional fixed 6-digit code for stub; if empty, stub accepts the DEBUG-hinted code (or any 6-digit if session was lost) |
| `DIARY_FIREBASE_PROJECT_ID` | Firebase-ready placeholder — keep empty in git; add `GoogleService-Info.plist` locally only |
| `DIARY_TWILIO_ACCOUNT_SID` / `DIARY_TWILIO_AUTH_TOKEN` / `DIARY_TWILIO_VERIFY_SERVICE_SID` | Twilio Verify placeholders — **scheme env only**, never commit |

Default provider is **stub**: no real SMS is sent. In DEBUG, the gate shows the stub code after “Send”. Selecting `firebase` / `twilio` without local config fails closed with a clear message (SDK not linked in this MVP).

### Subscriptions (StoreKit 2 stub)

Author Plus product IDs (override via scheme env or Info.plist — **no secrets**):

| Variable | Default |
|----------|---------|
| `DIARY_IAP_MONTHLY_PRODUCT_ID` | `com.diaryvault.author.plus.monthly` |
| `DIARY_IAP_YEARLY_PRODUCT_ID` | `com.diaryvault.author.plus.yearly` |

- Settings → **View plans / restore** opens the paywall placeholder (load products, purchase when available, **Restore purchases**).
- Shared scheme points at `DiaryVault/Configuration/DiaryVault.storekit` for local StoreKit testing.
- Estate / legacy custody products are **not** in this paywall (later).

### Sandbox / StoreKit testing (iOS)

1. **Local StoreKit (no App Store Connect yet)**  
   - Scheme → Run → Options → StoreKit Configuration = `DiaryVault.storekit` (already set on the shared scheme).  
   - Run on Simulator → sign in (phone OTP stub or DEBUG local) → Settings → View plans → purchase / restore against the local config.

2. **App Store Connect Sandbox (real products)**  
   - Create an auto-renewable subscription group **Author Plus** with the monthly / yearly product IDs above (or override IDs to match Connect).  
   - Availability: start with **Taiwan, Hong Kong, Mainland China** per product plan.  
   - Create a Sandbox Apple ID (Users and Access → Sandbox).  
   - On a **device**: Settings → App Store → Sandbox Account; run a development build; purchase / restore.  
   - Clear the local StoreKit config from the scheme when testing against Sandbox / TestFlight, or products may come from the `.storekit` file instead.

3. **Capabilities**  
   - Target → Signing & Capabilities → **Sign in with Apple**.  
   - In-App Purchase capability is provided by StoreKit linkage; ensure the App ID has IAP enabled in the Developer portal before shipping.

### Enable cloud Mirror AI

By default the Mirror tab uses a **local mock**. To call a real cloud chat API (**no secrets in git**):

1. In Xcode → **Product → Scheme → Edit Scheme… → Run → Arguments → Environment Variables**, add:

   | Variable | Required | Example / default |
   |----------|----------|-------------------|
   | `DIARY_AI_API_KEY` | **Yes** (to leave mock) | Anthropic `sk-ant-…` or OpenAI-compatible key (scheme-local only) |
   | `DIARY_AI_PROVIDER` | No | `openai` (default) or `anthropic`. Auto-detects `anthropic` if `DIARY_AI_BASE_URL` contains `anthropic`. |
   | `DIARY_AI_BASE_URL` | No | OpenAI: `https://api.openai.com` · Anthropic: `https://api.anthropic.com` |
   | `DIARY_AI_MODEL` | No | OpenAI: `gpt-4o-mini` · Anthropic: `claude-sonnet-4-5` |
   | `DIARY_AI_MAX_TOKENS` | No | `400` |
   | `DIARY_AI_MAX_CONTEXT_ENTRIES` | No | `8` |
   | `DIARY_AI_DAILY_REQUEST_LIMIT` | No | `40` (`0` = unlimited) |

2. Leave Info.plist AI keys empty in the repo. Prefer scheme env (or a gitignored `Secrets.xcconfig`).
3. Restart the app. Settings → AI should show **Cloud API** and the resolved provider.

**Anthropic (Claude)** — exact Xcode scheme env vars for Silas:

```text
DIARY_AI_API_KEY=<your-anthropic-key>   # never commit
DIARY_AI_PROVIDER=anthropic
DIARY_AI_MODEL=claude-sonnet-4-5        # optional (this is the default when provider=anthropic)
DIARY_AI_BASE_URL=https://api.anthropic.com   # optional
```

The Anthropic client `POST`s to `{baseURL}/v1/messages` with headers `x-api-key` + `anthropic-version: 2023-06-01`.

**OpenAI-compatible** (Groq / DeepSeek / OpenAI / xAI, default) — `POST`s to `{baseURL}/v1/chat/completions` with `Authorization: Bearer …`. Groq host quirk: set `DIARY_AI_BASE_URL=https://api.groq.com/openai` (app appends `/v1/chat/completions`).

Same **mirror-style** system prompt + diary excerpts (**RAG-lite**) + usage guards for both providers. Data residency narrative: **Taiwan (TW)**.

Cost / billing notes for later App Store pricing: see project docs `ai-cost-and-billing.md` and `account-and-billing-plan.md` (workspace).

## What works in this scaffold

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language strip) | Works (local) |
| **Account gate (phone SMS OTP + optional Apple)** | Works on iOS with stub SMS; DEBUG local session available |
| **StoreKit 2 Author Plus stub + paywall / restore** | Works against local `.storekit` or Sandbox products |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | **Mock** by default; **cloud** (OpenAI-compatible or Anthropic) when `DIARY_AI_API_KEY` is set |
| Diary → Mirror context | Recent entries passed as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, local daily request counter stub |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |
| Android account / billing | **Stub hooks** in Settings only |

## What is stubbed / omitted

- **App Store Connect live products / prices** — create in Connect; IDs are configurable here
- **Auth / receipt backend / real SMS** — local session + SMS stub + StoreKit; Firebase/Twilio wiring later
- **Android SMS OTP / Play Billing** — stubs only this PR
- **Free vs Plus Mirror quotas** — not yet wired to `MirrorUsageGuard`
- **Voice / video diary** — deferred (out of this PR)
- **Taiwan sync server** — local vault only; residency is narrative + config label
- **Fine-tuning / private model training** — not used; learning = prompt context only
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
    DiaryVault.entitlements   # Sign in with Apple (optional backup)
    Models/          # AppState, DiaryEntry, MirrorMessage
    Services/        # DiaryStore, AccountSession, SMSAuthProvider, SubscriptionStore, Mirror AI, Localization
    Config/          # AIConfig, BillingConfig, AuthConfig (env / Info.plist — no secrets)
    Configuration/   # DiaryVault.storekit (local IAP)
    Views/           # Onboarding, Auth, Diary, Mirror, Paywall, Settings + vault theme
    Resources/       # en, zh-Hant, zh-Hans Localizable.strings
android/             # Near-term runnable Compose MVP (+ account/billing stubs)
```

## Languages

In-app switchable (not only system locale):

- **繁體中文** (`zh-Hant`)
- **简体中文** (`zh-Hans`)
- **English** (`en`)

## License / secrets

Do not commit API keys or signing secrets. Prefer Xcode scheme environment variables or a gitignored `Secrets.xcconfig`. IAP product IDs are public identifiers, not secrets.
