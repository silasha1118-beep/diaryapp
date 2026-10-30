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
6. If signing prompts appear for a physical device, set your **Team** under the DiaryVault target → Signing & Capabilities. Free **Personal Team** can still run basic builds **only if** you temporarily remove Push / Sign in with Apple from entitlements. **APNs (no reCAPTCHA) and Sign in with Apple require a paid Apple Developer team ($99/yr).** Simulator needs no team for basic runs.

### Account (phone SMS OTP — all regions)

Flow: **Onboarding → Auth gate (phone + OTP) → main tabs**.

- **Primary**: mobile number + SMS one-time code for **CN / HK / TW** (and any phone that can receive SMS). **Sign in with Apple** is an optional backup (paid Apple Developer). WeChat / Google are **not** used.
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
2. Place your real plist at **`DiaryVault/DiaryVault/GoogleService-Info.plist`** (same folder as `Info.plist`).  
   A **Run Script** build phase copies it into the app bundle automatically — you do **not** need Target Membership / Copy Bundle Resources for beginners. (Dragging into the DiaryVault group with membership checked still works too.)
3. Bundle ID must be **`com.silasha.diaryvault`** (project setting matches Firebase Console iOS app).
4. **URL scheme (reCAPTCHA fallback — required if APNs is not set up):**
   - Open the real plist → copy the string for **`REVERSED_CLIENT_ID`** (looks like `com.googleusercontent.apps.…`).
   - If that key is **missing**, re-download the plist from Firebase Console → Project settings → Your apps → iOS app → **Download GoogleService-Info.plist**, replace the local file, then copy the new value.
   - Xcode → DiaryVault **target** → **Info** tab → **URL Types** → **+** → **URL Schemes** = paste `REVERSED_CLIENT_ID`. Leave Identifier / Role blank.
   - Rebuild after changing URL Types.
5. **APNs (recommended before launch — especially CN):** silent push avoids Google reCAPTCHA.
   - Repo already sets Push + `registerForRemoteNotifications` + `Auth.setAPNSToken`.
   - **Paid Apple Developer required** to create an APNs Auth Key (.p8) and to sign with the Push capability.
   - Apple Developer → Keys → APNs → download `.p8` → Firebase → Project settings → **Cloud Messaging** → upload for `com.silasha.diaryvault`.
   - Without APNs, Phone Auth falls back to **reCAPTCHA** (needs the URL scheme above). Personal Team cannot finish APNs.
6. **Sign in with Apple (optional backup):** SMS stays primary; gate / Settings can expand Apple sign-in.
   - Repo entitlements include `com.apple.developer.applesignin`; UI is wired.
   - **Paid team required.** Developer portal → App ID `com.silasha.diaryvault` → enable Sign in with Apple; Firebase → Authentication → Sign-in method → enable **Apple**.
7. No extra Info.plist privacy usage strings are required for SMS OTP (no camera / mic / contacts).
8. **Scheme env — leave `DIARY_SMS_PROVIDER` empty.** Empty + plist on disk → **Firebase** auto-select. Set `stub` only to force local OTP without SMS. If you previously added `DIARY_SMS_PROVIDER=stub` under Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables, **delete that row** and rebuild.
9. Run on a **physical iPhone** for real SMS without reCAPTCHA. Simulator can still drive phone via reCAPTCHA + Firebase **test phone numbers**, and can try Sign in with Apple after the paid capability is enabled.
10. On the login / Settings phone form, confirm the banner says **簡訊後端：Firebase** (not Stub).

Beginner Mac / Developer / Firebase click-path (APNs + Apple): project Context doc  
`/cursor/stores/self/docs/apns-firebase-phone-auth.md`  
(also linked from the PR for this change).

See also `DiaryVault/DiaryVault/GoogleService-Info.plist.example` (placeholders only).

#### Tonight: receive a real SMS on your iPhone (beginner click-path)

Do these in order. Phone Auth is already **Enabled** for project **`diaryappsilas`** — focus on regions, URL scheme, and device run.

**A. Firebase Console — allow first-market SMS regions (manual — not changed by app code)**

1. Open [Firebase Console](https://console.firebase.google.com/) → project **`diaryappsilas`**.
2. Left menu → **Build** → **Authentication**.
3. Tab **Sign-in method** → confirm **Phone** is **Enabled** (you already did this).
4. Tab **Settings** (still under Authentication) → section **SMS region policy** (sometimes labeled “SMS regions”).
5. Choose **Allow** / allowlist and add at least:
   - **Taiwan (TW)** · **Hong Kong (HK)** · **China (CN)** / Mainland China  
   - **Malaysia (MY)** · **Singapore (SG)** · **Thailand (TH)** · **Japan (JP)**  
   New projects often allow **no** regions by default — if your country is missing, Send code fails with a region error in the app.  
   **You must do this in Firebase Console yourself**; the app only shows these dial codes in the UI.
6. Save.

**B. Optional — Firebase test phone numbers (saves Spark quota)**

Spark (free) Phone Auth is limited to about **10 real SMS / day** per project. To verify the app without burning quota:

1. Authentication → **Sign-in method** → **Phone** → open provider settings (or **Phone numbers for testing**).
2. Add a row: your number in E.164 (e.g. `+886912345678`) + a fixed 6-digit code (e.g. `123456`).
3. In the app, send code to that number — Firebase **does not** send a real SMS; enter the fixed code.
4. When ready for a **real** SMS, use a number **not** on the test list (or remove it), still within the daily quota.

**C. Xcode → physical iPhone**

1. Plug in the iPhone → trust the computer → select the **device** (not Simulator) as the run destination.
2. Target → **Signing & Capabilities** → Team = your free **Personal Team**; Bundle ID `com.silasha.diaryvault`.
3. Confirm `GoogleService-Info.plist` is at `DiaryVault/DiaryVault/` + **URL Types** has `REVERSED_CLIENT_ID` (step 4 above).
4. Leave `DIARY_SMS_PROVIDER` empty. Do **not** use `stub` if you want a real text. Clear any leftover scheme env row named `DIARY_SMS_PROVIDER`.
5. **Product → Run** (⌘R). If asked, allow the developer app on the phone (Settings → General → VPN & Device Management). Login UI must show **簡訊後端：Firebase** before you send a code.

**D. Sign out, then log in with phone**

1. If you previously used DEBUG **“Dev: continue with local session”**, open **Settings** → **帳號 / 手機登入** → **登出** (Sign out). That returns you to the auth gate.
2. Or stay in Settings and use the same phone + OTP fields under **帳號 / 手機登入**.
3. Pick region **+886 / +852 / +86 / +60 / +65 / +66 / +81** → enter the mobile number **without** the country code (leading `0` is OK; the app strips it) → **傳送驗證碼**.
4. Complete reCAPTCHA if the phone shows a browser sheet → wait for SMS → enter the 6-digit code → **驗證並繼續**.
5. Settings should show provider **Phone (SMS OTP)** and a masked number.

**If Send code fails**

| App message / symptom | What to do |
|----------------------|------------|
| Region blocked | Console → Authentication → Settings → SMS region policy → allow TW / HK / CN / MY / SG / TH / JP |
| App verification / reCAPTCHA | Add `REVERSED_CLIENT_ID` URL scheme, rebuild; or set up APNs |
| SMS not ready / plist | File at `DiaryVault/DiaryVault/GoogleService-Info.plist`; rebuild so the Copy script bundles it |
| Too many attempts / quota | Wait, or use a **test phone number**; Spark ≈ **10 real SMS/day** |
| Banner says Stub / stub hint with a code | Stub is active: plist not on disk at that path, or scheme still has `DIARY_SMS_PROVIDER=stub` — fix & rebuild |

#### SMS provider env vars

| Variable | Purpose |
|----------|---------|
| `DIARY_SMS_PROVIDER` | `stub` · `firebase` · `twilio`. **Leave empty** → **firebase** if `GoogleService-Info.plist` is bundled, else **stub**. Explicit `stub` forces local OTP. |
| `DIARY_SMS_STUB_CODE` | Optional fixed 6-digit code for stub; if empty, stub accepts the DEBUG-hinted code (or any 6-digit if session was lost) |
| `DIARY_FIREBASE_PROJECT_ID` | Label only (default `diaryappsilas` in Info.plist); real config is the local plist |
| `DIARY_TWILIO_ACCOUNT_SID` / `DIARY_TWILIO_AUTH_TOKEN` / `DIARY_TWILIO_VERIFY_SERVICE_SID` | Twilio Verify placeholders — **scheme env only**, never commit |

**Shared scheme has no `DIARY_SMS_PROVIDER` env** — beginners should not add one. Without a plist on disk, provider stays **stub** (UI banner + DEBUG code hint). Twilio remains a fail-closed shell.

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

#### Beginner Mac steps — Free vs Author Plus Mirror AI quotas

Cloud Mirror only (set `DIARY_AI_API_KEY` in the scheme). Defaults: **Free = 8/day**, **Author Plus = 40/day** (`DIARY_AI_DAILY_REQUEST_LIMIT`; `0` = unlimited for Plus). Optional: set `DIARY_AI_FREE_DAILY_REQUEST_LIMIT=2` for a faster Free exhaust test.

1. Same as above: StoreKit Configuration = `DiaryVault.storekit`, Simulator Run, cloud AI key in scheme.
2. Stay on **Free** (do not buy yet). Open **Mirror** → send cloud messages until the quota banner / error shows the free limit is used up (Settings → AI → **Mirror today** also shows `used / limit` and an upgrade CTA).
3. Open **Settings → View plans / restore** → buy **Author Plus** via the `.storekit` sheet.
4. Return to **Mirror**: remaining quota should jump to the Plus daily limit (or “unlimited” if Plus limit is `0`). You can send again without waiting for tomorrow.
5. (Optional) Exhaust the Plus limit the same way, or set `DIARY_AI_DAILY_REQUEST_LIMIT=2` to verify the Plus exhausted copy (no upgrade CTA).

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

- **Push Notifications** + **Sign in with Apple** are in `DiaryVault.entitlements` (paid Apple Developer). Personal Team often cannot provision them — temporarily remove those keys to keep a free-team device build, or enroll in the paid program.
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
   | `DIARY_AI_FREE_DAILY_REQUEST_LIMIT` | No | `8` (Free tier daily Mirror cap) |
   | `DIARY_AI_DAILY_REQUEST_LIMIT` | No | `40` (Author Plus daily Mirror cap; `0` = unlimited for Plus) |

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
| **Account gate (phone SMS OTP)** | Firebase Phone Auth when plist present; else stub SMS; **Sign in with Apple** optional backup (paid team); DEBUG local session; Settings has the same |
| **StoreKit 2 Author Plus purchase / restore** | Works against local `.storekit` (Personal Team OK) or Sandbox when Connect products exist |
| Vault list / create / detail / edit | Works — JSON on device |
| Mirror chat | **Mock** by default; **cloud** (OpenAI-compatible or Anthropic) when `DIARY_AI_API_KEY` is set |
| Diary → Mirror context | Recent entries passed as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, **Free vs Author Plus** local daily Mirror quotas |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |
| Android account / billing | **Stub hooks** in Settings only |

## What is stubbed / omitted

- **App Store Connect live products / prices** — need paid Apple Developer; IDs are configurable here; local `.storekit` covers Personal Team testing
- **Auth / receipt backend** — Firebase Phone Auth on iOS (local diary); Twilio / server receipt verify later
- **Android SMS OTP / Play Billing** — stubs only
- **Voice / video diary** — deferred (no media product tier in this build)
- **Taiwan sync server** — local vault only; residency is narrative + config label
- **Fine-tuning / private model training** — not used; learning = prompt context only
- **Memorial mode, voice clone, legacy executor** — Settings stub copy only; no Phase 1 UI
- **Push / contextual reminder scheduling** — toggle only
- **App Store assets / App Icon art** — placeholder asset catalog slot
- **Unit / UI tests** — not included in this scaffold

## Privacy & Support (GitHub Pages)

Static legal pages live in [`docs/`](docs/) for App Store Connect and `DIARY_PRIVACY_POLICY_URL`:

| Page | Public URL (after Pages is on) |
|------|--------------------------------|
| Privacy Policy | `https://silasha1118-beep.github.io/diaryapp/privacy.html` |
| Support | `https://silasha1118-beep.github.io/diaryapp/support.html` |
| Hub | `https://silasha1118-beep.github.io/diaryapp/` |

**Enable Pages once (after this lands on `main`):**

1. Open [github.com/silasha1118-beep/diaryapp](https://github.com/silasha1118-beep/diaryapp) → **Settings**.
2. Left sidebar → **Pages**.
3. Under **Build and deployment** → **Source** → choose **Deploy from a branch**.
4. **Branch:** `main` · **Folder:** `/docs` → **Save**.
5. Wait 1–2 minutes; open the Privacy URL above to confirm HTTPS works.
6. App Store Connect → App Information → **Privacy Policy URL** = Privacy URL; Support URL = Support URL.
7. `Info.plist` already sets `DIARY_PRIVACY_POLICY_URL` to the Privacy URL (rebuild after merge).

Contact on the Support page: **silasha11@icloud.com**.

## Project layout

```
docs/                # GitHub Pages: privacy.html, support.html, index.html
DiaryVault/
  DiaryVault.xcodeproj
  DiaryVault/
    DiaryVaultApp.swift
    DiaryVault.entitlements   # Push (aps-environment) + Sign in with Apple (paid team)
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
