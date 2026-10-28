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

3. Wait for Swift Package Manager to fetch **Firebase** (`firebase-ios-sdk` → FirebaseAuth + FirebaseCore). If needed: **File → Packages → Resolve Package Versions**.
4. Add your local **`GoogleService-Info.plist`** to the DiaryVault target (gitignored — see Firebase Phone Auth below). Bundle ID is **`com.silasha.diaryvault`**.
5. Select an iPhone Simulator (or **device** for real SMS), then **Run** (⌘R).
6. If signing prompts appear for a physical device, set your **Team** under the DiaryVault target → Signing & Capabilities. A free **Personal Team** works for device runs (Sign in with Apple is not required). Simulator needs no team for basic runs.

### Account (phone SMS OTP — all regions)

Flow: **Onboarding → Auth gate (phone + OTP) → main tabs**.

- **Primary**: mobile number + SMS one-time code for **CN / HK / TW** (and any phone that can receive SMS). WeChat / Google / Sign in with Apple are **not** used.
- With Firebase: session UID comes from Firebase Auth; diary data stays on device for this MVP.
- Settings shows signed-in identity / provider and **Sign out** (also signs out of Firebase Auth).
- **DEBUG** builds also offer “Dev: continue with local session”.
- Do **not** commit `GoogleService-Info.plist` or other secrets (already gitignored).

#### Firebase Phone Auth (iOS · Mac / Xcode steps for Silas)

**SPM (already wired in the Xcode project):**

| Item | Value |
|------|--------|
| Package URL | `https://github.com/firebase/firebase-ios-sdk.git` |
| Products | **FirebaseAuth**, **FirebaseCore** |
| Version rule | Up to next major from `11.0.0` |

If Xcode does not resolve packages after pull: **File → Packages → Resolve Package Versions**. You should see Firebase under Package Dependencies. (`-ObjC` is already set on the target.)

**You already have `GoogleService-Info.plist` locally — do not commit it.** After merge:

1. Open `DiaryVault/DiaryVault.xcodeproj` in Xcode.
2. Drag your real `GoogleService-Info.plist` into the **DiaryVault** group (or confirm it is still there).
3. Select the file → **Target Membership** → check **DiaryVault**. Confirm **Copy Bundle Resources** lists it.
4. Bundle ID must be **`com.silasha.diaryvault`** (project setting matches Firebase Console app).
5. **URL scheme (reCAPTCHA fallback):** Target → **Info** → **URL Types** → **+** → paste `REVERSED_CLIENT_ID` from your plist (looks like `com.googleusercontent.apps.…`). Leave other fields blank.
6. Optional but recommended for silent push (fewer reCAPTCHA prompts on device):
   - Signing & Capabilities → **Push Notifications**
   - **Background Modes** → Remote notifications
   - Apple Developer → create APNs key → Firebase Console → Project settings → Cloud Messaging → upload APNs key  
   **APNs is optional for testing** — Simulator / no APNs still works via **reCAPTCHA**.
7. Firebase Console project **`diaryappsilas`**: Phone provider **Enabled**. Under Authentication → Settings → **SMS region policy**, allow **TW / HK / CN** (new projects often allow no regions by default).
8. Scheme env (optional): `DIARY_SMS_PROVIDER=firebase` — or leave empty; with the plist in the bundle the app **auto-selects Firebase**. Use `stub` to force local OTP without SMS.
9. Run on a **physical iPhone** for real SMS (Simulator uses reCAPTCHA; test numbers can be added in Firebase Auth → Phone → Phone numbers for testing).

See also `DiaryVault/DiaryVault/GoogleService-Info.plist.example` (placeholders only).

#### SMS provider env vars

| Variable | Purpose |
|----------|---------|
| `DIARY_SMS_PROVIDER` | `stub` · `firebase` · `twilio`. Empty → **firebase** if plist present, else **stub** |
| `DIARY_SMS_STUB_CODE` | Optional fixed 6-digit code for stub; if empty, stub accepts the DEBUG-hinted code (or any 6-digit if session was lost) |
| `DIARY_FIREBASE_PROJECT_ID` | Label only (default `diaryappsilas` in Info.plist); real config is the local plist |
| `DIARY_TWILIO_ACCOUNT_SID` / `DIARY_TWILIO_AUTH_TOKEN` / `DIARY_TWILIO_VERIFY_SERVICE_SID` | Twilio Verify placeholders — **scheme env only**, never commit |

Without a plist, provider stays **stub** (DEBUG shows the stub code). Twilio remains a fail-closed shell.

### Subscriptions (StoreKit 2 · Author Plus)

**Free** vs **Author Plus** (monthly / yearly). Product IDs are configurable (override via scheme env or Info.plist — **no secrets**):

| Variable | Default |
|----------|---------|
| `DIARY_IAP_MONTHLY_PRODUCT_ID` | `com.diaryvault.author.plus.monthly` |
| `DIARY_IAP_YEARLY_PRODUCT_ID` | `com.diaryvault.author.plus.yearly` |

- Settings → **View plans / restore** opens the paywall (load products, purchase, **Restore purchases**, reload).
- Shared scheme already points at `DiaryVault/Configuration/DiaryVault.storekit` for local testing.
- Estate / legacy custody and **voice / video diary** are **not** in this paywall (deferred).

#### Beginner Mac steps — buy / restore with local StoreKit (Personal Team OK)

You do **not** need a paid Apple Developer account to try purchase + restore on Simulator with the local `.storekit` file.

1. On a Mac, open `DiaryVault/DiaryVault.xcodeproj` in Xcode 15+.
2. Confirm StoreKit Configuration is attached:
   - **Product → Scheme → Edit Scheme…** (or click the scheme name → Edit Scheme…)
   - Select **Run** on the left → **Options** tab
   - **StoreKit Configuration** = `DiaryVault.storekit`  
     (path in repo: `DiaryVault/DiaryVault/Configuration/DiaryVault.storekit`; the shared scheme already sets this)
3. Select an **iPhone Simulator** → **Run** (⌘R).
4. Finish onboarding → sign in (Firebase phone OTP, or DEBUG “local session”) → open **Settings**.
5. Tap **View plans / restore**:
   - You should see **Free** plus **Author Plus Monthly** / **Yearly** with prices from the `.storekit` file (e.g. $4.99 / $39.99).
   - Tap a plan → confirm the StoreKit purchase sheet → Settings plan should flip to **Author Plus**.
   - Tap **Restore purchases** after a purchase (or after deleting/reinstalling the app) to re-sync entitlement.
6. Optional debug: in Xcode menu **Debug → StoreKit → Manage Transactions…** to expire / refund / renew the local subscription.

#### Personal Team vs paid Apple Developer

| What | Free Personal Team | Paid Apple Developer ($99/yr) |
|------|--------------------|-------------------------------|
| Local `.storekit` purchase / restore in Simulator | Yes | Yes |
| Device run with Personal Team signing | Yes (app runs; local StoreKit still works if scheme points at the file) | Yes |
| Create real IAP products in **App Store Connect** | **No** | Required |
| Sandbox Apple ID + Connect Sandbox testing | **No** | Required |
| TestFlight / App Store release with IAP | **No** | Required |

When you upgrade to a paid account later: create subscription group **Author Plus** with the product IDs above (TW / HK / CN first), create a Sandbox Apple ID, then **clear** StoreKit Configuration on the scheme (set to None) so the app talks to Sandbox / production instead of the local file.

#### Capabilities

- Sign in with Apple is **not** required (removed so free Personal Team signing works).
- StoreKit 2 needs no extra capability checkbox for local `.storekit` testing. Before shipping with Connect products, enable **In-App Purchase** on the App ID in the Developer portal (paid account).

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
| **Account gate (phone SMS OTP + optional Apple)** | Firebase Phone Auth when plist present; else stub SMS; DEBUG local session available |
| **StoreKit 2 Author Plus purchase / restore** | Works against local `.storekit` (Personal Team OK) or Sandbox when Connect products exist |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | **Mock** by default; **cloud** (OpenAI-compatible or Anthropic) when `DIARY_AI_API_KEY` is set |
| Diary → Mirror context | Recent entries passed as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, local daily request counter stub |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |
| Android account / billing | **Stub hooks** in Settings only |

## What is stubbed / omitted

- **App Store Connect live products / prices** — need paid Apple Developer; IDs are configurable here; local `.storekit` covers Personal Team testing
- **Auth / receipt backend** — Firebase Phone Auth on iOS (local diary); Twilio / server receipt verify later
- **Android SMS OTP / Play Billing** — stubs only
- **Free vs Plus Mirror quotas** — not yet wired to `MirrorUsageGuard`
- **Voice / video diary** — deferred (no media product tier in this build)
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
    DiaryVault.entitlements   # Empty (no paid-capability entitlements)
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
