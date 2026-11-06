# Diary Vault（日記金庫）

Personal diary vault MVP scaffold (iOS + Android). Near-term runnable target: **Android** (no Mac required). Living-author loop: **write → AI learns → gentle mirror-style advice** (reflect + guiding questions, not coach prescriptions). Memorial / voice clone / legacy executor UI is **out of Phase 1** and only mentioned as a stub in Settings.

Product plan (source of truth for decisions): kept in the project workspace docs — not duplicated here. Account / billing sketch: `docs/account-and-billing-plan.md` (workspace).


## Android (near-term runnable)

> **No Mac / Xcode for now** — the Kotlin + Jetpack Compose app under [`android/`](android/) is the **near-term build & run target** on Linux/Windows. The iOS project in `DiaryVault/` stays intact for when a Mac is available.

See **[android/README.md](android/README.md)** for Android Studio / `./gradlew :app:assembleDebug`.

Android **Play Billing** is wired in-app (Author Plus / Author Pro × 月／季／年, same product IDs as iOS). Settings paywall + Vault/Mirror hard-cap checkout work; catalog is empty until Silas creates the six subscriptions in Play Console (see [android/README.md](android/README.md) § Play Billing). Debug builds can **Simulate Plus/Pro** without Console SKUs.

## Open in Xcode

1. Clone this repo on a Mac with **Xcode 15+** (iOS 17 deployment target).
2. Open:

   ```bash
   open DiaryVault/DiaryVault.xcodeproj
   ```

3. Wait for Swift Package Manager to fetch **Firebase** (`firebase-ios-sdk` → FirebaseAuth + FirebaseCore + FirebaseFirestore). If needed: **File → Packages → Resolve Package Versions**.
4. Add your local **`GoogleService-Info.plist`** to the DiaryVault target (gitignored — see Firebase Phone Auth below). Bundle ID is **`com.silasha.diaryvault`**.
5. Select an iPhone Simulator (or **device** for real SMS), then **Run** (⌘R).
6. If signing prompts appear for a physical device, set your **Team** under the DiaryVault target → Signing & Capabilities. Free **Personal Team** can still run basic builds **only if** you temporarily remove Push / Sign in with Apple from entitlements. **APNs (no reCAPTCHA) and Sign in with Apple require a paid Apple Developer team ($99/yr).** Simulator needs no team for basic runs.

### Account (phone SMS OTP — all regions)

Flow: **Onboarding → Auth gate (phone + OTP) → main tabs**.

- **Primary**: mobile number + SMS one-time code for **CN / HK / TW** (and any phone that can receive SMS). **Sign in with Apple** is an optional backup (paid Apple Developer). WeChat / Google are **not** used.
- With Firebase: session UID comes from Firebase Auth; **text diary entries sync to Firestore** when signed in (local JSON remains offline source of truth). Signed-out / DEBUG local sessions stay on-device only.
- Settings shows signed-in identity / provider and **Sign out** (also signs out of Firebase Auth).
- **DEBUG** builds also offer “Dev: continue with local session”.
- Do **not** commit `GoogleService-Info.plist` or other secrets (already gitignored).

#### Firebase Phone Auth (iOS · Mac / Xcode steps for Silas)

**SPM (already wired in the Xcode project):**

| Item | Value |
|------|--------|
| Package URL | `https://github.com/firebase/firebase-ios-sdk.git` |
| Products | **FirebaseAuth**, **FirebaseCore**, **FirebaseFirestore** |
| Version rule | Up to next major from `11.0.0` |

If Xcode does not resolve packages after pull: **File → Packages → Resolve Package Versions**. You should see Firebase under Package Dependencies. (`-ObjC` is already set on the target.)

#### Firestore text diary sync (Silas — Console)

1. Firebase Console → project **`diaryappsilas`** → **Build → Firestore Database → Create database** (if not already).
2. Prefer location **`asia-east1` (Taiwan)** when available (product residency narrative); else nearest Asia region. Location is permanent.
3. **Rules** → paste [`firebase/firestore.rules`](firebase/firestore.rules) → **Publish**. Clients may only read/write `users/{theirUid}/…`.
4. Schema + behavior notes: [`firebase/README.md`](firebase/README.md). Entry docs include `label` / `status` / `hasMedia` / `attachments` on the **same** post. Vault-like cloud viewer: [`firebase/admin/README.md`](firebase/admin/README.md).
5. Rebuild the iOS app with `GoogleService-Info.plist` present. Sync runs only when Firebase Auth has a live user.

**You already have `GoogleService-Info.plist` locally — do not commit it.** After merge:

1. Open `DiaryVault/DiaryVault.xcodeproj` in Xcode.
2. Place your real plist at **`DiaryVault/DiaryVault/GoogleService-Info.plist`** (same folder as `Info.plist`).  
   A **Run Script** build phase copies it into the app bundle automatically — you do **not** need Target Membership / Copy Bundle Resources for beginners. (Dragging into the DiaryVault group with membership checked still works too.)
3. Bundle ID must be **`com.silasha.diaryvault`** (project setting matches Firebase Console iOS app).
4. **URL scheme (reCAPTCHA fallback — required if APNs is not set up):**
   - Source `Info.plist` commits **non-empty** `CFBundleURLTypes` placeholders (an empty array can be stripped → Firebase `mainBundleUrlTypes` stays nil → fatal in `PhoneAuthProvider.init` at line 649).
   - The **Copy GoogleService-Info.plist** Run Script runs `scripts/inject-firebase-url-schemes.py` to replace those placeholders with real **`REVERSED_CLIENT_ID`** + encoded **`GOOGLE_APP_ID`** from your local plist.
   - If `REVERSED_CLIENT_ID` is **missing**: Firebase Console → enable **Google** sign-in (generates the client id) → re-download `GoogleService-Info.plist` → replace the local file → **Product → Clean Build Folder** → delete the app from the phone → reinstall.
   - Confirm: Xcode → DiaryVault **target** → **Info** → **URL Types** shows `com.googleusercontent.apps.…` (not `DIARYVAULT_REVERSED_CLIENT_ID`).
   - App code never calls `PhoneAuthProvider.provider()` until `Bundle.main` URL Types contain a real scheme; otherwise Send stays disabled with an in-app message.
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
| App verification / reCAPTCHA / Xcode fatal at PhoneAuthProvider:649 | Confirm plist has `REVERSED_CLIENT_ID`; Clean Build Folder; delete app; reinstall; URL Types must show `com.googleusercontent.apps.…`; or set up APNs |
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

### Subscriptions (StoreKit 2 · Free / Author Plus / Author Pro)

Three Mirror tiers (entitlement rank **Pro > Plus > Free**). Each paid tier has **monthly / quarterly / yearly**. Product IDs are configurable (override via scheme env or Info.plist — **no secrets**):

| Variable | Default |
|----------|---------|
| `DIARY_IAP_PLUS_MONTHLY_PRODUCT_ID` (alias `DIARY_IAP_MONTHLY_PRODUCT_ID`) | `com.diaryvault.author.plus.monthly` |
| `DIARY_IAP_PLUS_QUARTERLY_PRODUCT_ID` | `com.diaryvault.author.plus.quarterly` |
| `DIARY_IAP_PLUS_YEARLY_PRODUCT_ID` (alias `DIARY_IAP_YEARLY_PRODUCT_ID`) | `com.diaryvault.author.plus.yearly` |
| `DIARY_IAP_PRO_MONTHLY_PRODUCT_ID` | `com.diaryvault.author.pro.monthly` |
| `DIARY_IAP_PRO_QUARTERLY_PRODUCT_ID` | `com.diaryvault.author.pro.quarterly` |
| `DIARY_IAP_PRO_YEARLY_PRODUCT_ID` | `com.diaryvault.author.pro.yearly` |

| Tier | Vault **uploads** (text + media, one bucket) | **Mirror** |
|------|-----------------------------------------------|------------|
| Free | **Lifetime 40** hard stop → paywall (media included) | **Lifetime 40** hard stop → paywall |
| Author Plus | **8 / day**; at cap → Author Pro upsell | **8 / day** + Pro upsell |
| Author Pro | **16 / day** | **16 / day** |

- Settings → **View plans / restore** opens the paywall (Free card + Plus periods + Pro periods, purchase, **Restore purchases**, reload).
- Shared scheme already points at `DiaryVault/Configuration/DiaryVault.storekit` for local testing (one subscription group, Pro service level above Plus).
- Estate / legacy custody remain out of this paywall. Vault upload quotas include multimedia counts for Free/Plus/Pro; duration/Storage UI polish is still DA-15.

#### Beginner Mac steps — buy / restore with local StoreKit (Personal Team OK)

You do **not** need a paid Apple Developer account to try purchase + restore on Simulator with the local `.storekit` file.

1. On a Mac, open `DiaryVault/DiaryVault.xcodeproj` in Xcode 15+.
2. Confirm StoreKit Configuration is attached (**required** or every SKU shows “price unavailable” / empty catalog):
   - **Product → Scheme → Edit Scheme…** (or click the scheme name → Edit Scheme…)
   - Select **Run** on the left → **Options** tab
   - **StoreKit Configuration** = `DiaryVault.storekit`  
     (path in repo: `DiaryVault/DiaryVault/Configuration/DiaryVault.storekit`; the shared scheme already sets this)
   - Do **not** leave this on **None** unless you intentionally want Sandbox / Connect (see below).
3. Select an **iPhone Simulator** → **Run** (⌘R).
4. Finish onboarding → sign in (Firebase phone OTP, or DEBUG “local session”) → open **Settings**.
5. Tap **View plans / restore**:
   - You should see three cards: **Free**, **Author Plus**, **Author Pro**.
   - Plus / Pro show period chips (monthly / quarterly / yearly) and **StoreKit prices** (from `.storekit`, not hardcoded).
   - Release builds never show raw product IDs; DEBUG builds may show a StoreKit diagnostic block.
   - Tap **Subscribe** → confirm the StoreKit purchase sheet → Settings plan should flip to **Author Plus** or **Author Pro**.
   - Tap **Restore purchases** after a purchase (or after deleting/reinstalling the app) to re-sync entitlement.
6. Optional debug: in Xcode menu **Debug → StoreKit → Manage Transactions…** to expire / refund / renew the local subscription.

#### Why products show empty / “price unavailable”

`Product.products(for:)` returns **no products** (empty array, often without throwing) when:

| Mode | StoreKit Configuration | What happens |
|------|------------------------|--------------|
| **Local** | `DiaryVault.storekit` | Products + prices load from the file; purchase works without Connect. |
| **Sandbox** | **None** | App talks to App Store Sandbox. Needs **paid** Apple Developer + six matching Connect SKUs + Sandbox Apple ID. If Connect SKUs are missing / wrong IDs, catalog is empty and purchase is impossible. |

IDs in `BillingConfig` must match both `DiaryVault.storekit` and Connect exactly (`com.diaryvault.author.plus.*` / `com.diaryvault.author.pro.*`).

#### Beginner Mac steps — diary **upload** quotas (vault create)

`DiaryUploadUsageGuard` / `UploadQuotaConfig` (independent of Mirror). Text + media share one vault bucket. Free is **lifetime** (uid-keyed UserDefaults + Firestore); Plus/Pro are local calendar day.

1. Stay on **Free**. Open Vault → **New** → save entries. Each create counts toward lifetime **40** (text + media).
2. Soft ladders are gone — at **40**, hard stop → paywall (no “continue anyway”).
3. Buy **Author Plus** → **8 vault uploads/day**. At 8, upsell **Author Pro**. Buy **Author Pro** → **16/day**; at 16 done for today.
4. Multimedia creates call the same guard (`checkAndIncrementMultimedia`) and count toward the same vault bucket for Free/Plus/Pro. Duration/Storage UI polish remains DA-15.

#### Beginner Mac steps — Free / Plus / Pro Mirror AI quotas

Cloud Mirror only (set `DIARY_AI_API_KEY` in the scheme). Defaults: **Free = 40 lifetime**, **Plus = 8/day**, **Pro = 16/day**. Soft Free ladders removed. Optional: set `DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT=2` for a faster Free exhaust test.

1. Stay on **Free**. Open **Mirror** → after lifetime **40**, hard stop → paywall.
2. Buy **Author Plus** → Mirror quota becomes **8/day**. At 8, upsell **Author Pro**.
3. Buy **Author Pro** → Mirror quota becomes **16/day**. At 16, “done for today” (no higher CTA).

### App Store Connect (paid Apple Developer) — for Silas

1. App Store Connect → your app → **Subscriptions** → create one subscription group, e.g. **Diary Vault Author** (same as local `.storekit`).
2. Add **six** auto-renewable products with these IDs (must match the app / overrides exactly):

   | Reference | Product ID | Duration | Suggested level |
   |-----------|------------|----------|-----------------|
   | Author Pro Monthly | `com.diaryvault.author.pro.monthly` | 1 month | Higher (level 1) |
   | Author Pro Quarterly | `com.diaryvault.author.pro.quarterly` | 3 months | Higher (level 1) |
   | Author Pro Yearly | `com.diaryvault.author.pro.yearly` | 1 year | Higher (level 1) |
   | Author Plus Monthly | `com.diaryvault.author.plus.monthly` | 1 month | Lower (level 2) |
   | Author Plus Quarterly | `com.diaryvault.author.plus.quarterly` | 3 months | Lower (level 2) |
   | Author Plus Yearly | `com.diaryvault.author.plus.yearly` | 1 year | Lower (level 2) |

3. Set localization (TW / HK / CN first), pricing, and review screenshot / notes.
4. Create a **Sandbox** Apple ID; on device/Simulator clear scheme **StoreKit Configuration** (set to None) so the app talks to Sandbox / production.
5. Enable **In-App Purchase** on the App ID; Paid Apps Agreement must be active.

#### Personal Team vs paid Apple Developer

| What | Free Personal Team | Paid Apple Developer ($99/yr) |
|------|--------------------|-------------------------------|
| Local `.storekit` purchase / restore in Simulator | Yes | Yes |
| Device run with Personal Team signing | Yes (app runs; local StoreKit still works if scheme points at the file) | Yes |
| Create real IAP products in **App Store Connect** | **No** | Required |
| Sandbox Apple ID + Connect Sandbox testing | **No** | Required |
| TestFlight / App Store release with IAP | **No** | Required |

#### Capabilities

- **Push Notifications** + **Sign in with Apple** are in `DiaryVault.entitlements` (paid Apple Developer). Personal Team often cannot provision them — temporarily remove those keys to keep a free-team device build, or enroll in the paid program.
- StoreKit 2 needs no extra capability checkbox for local `.storekit` testing. Before shipping with Connect products, enable **In-App Purchase** on the App ID in the Developer portal (paid account).

### Enable cloud Mirror AI

**Working provider (product pref): xAI Grok** — OpenAI-compatible `POST {base}/v1/chat/completions`, default host `https://api.x.ai`, model `grok-4.7`. There is **no** Firebase Functions Mirror proxy in-repo yet; keys stay local (never commit).

#### Android (near-term runnable)

1. Get a key at https://console.x.ai → API Keys.
2. Append to gitignored `android/local.properties` (keep existing `sdk.dir`):

```properties
XAI_API_KEY=xai-your-key-here
```

(`DIARY_AI_API_KEY` also works. Optional: `DIARY_AI_BASE_URL=https://api.x.ai`, `DIARY_AI_MODEL=grok-4.7`.)

3. Sync Gradle → rebuild Debug → Mirror → send. Without a key the app builds and Mirror shows a clear error (no mock replies). See `android/README.md` and `android/local.properties.example`.

#### iOS (Xcode scheme env)

1. **Product → Scheme → Edit Scheme… → Run → Arguments → Environment Variables**, add:

   | Variable | Required | Example / default |
   |----------|----------|-------------------|
   | `DIARY_AI_API_KEY` or `XAI_API_KEY` | **Yes** (to leave mock) | xAI key (scheme-local only) |
   | `DIARY_AI_PROVIDER` | No | `openai` / `xai` (default) or `anthropic` |
   | `DIARY_AI_BASE_URL` | No | xAI: `https://api.x.ai` · Anthropic: `https://api.anthropic.com` |
   | `DIARY_AI_MODEL` | No | xAI: `grok-4.7` · Anthropic: `claude-sonnet-4-5` |
   | `DIARY_AI_MAX_TOKENS` | No | `400` |
   | `DIARY_AI_MAX_CONTEXT_ENTRIES` | No | `8` |
   | `DIARY_AI_FREE_LIFETIME_REQUEST_LIMIT` | No | `40` (Free Mirror lifetime; alias `DIARY_AI_FREE_DAILY_REQUEST_LIMIT`) |
   | `DIARY_AI_DAILY_REQUEST_LIMIT` | No | `8` (Author Plus daily; `0` = unlimited for Plus) |
   | `DIARY_AI_PRO_DAILY_REQUEST_LIMIT` | No | `16` (Author Pro daily; `0` = unlimited for Pro) |

2. Leave Info.plist AI keys empty in the repo. Prefer scheme env (or a gitignored `Secrets.xcconfig`).
3. Restart the app.

**Anthropic (Claude)** remains available as an alternate OpenAI-incompatible path (`DIARY_AI_PROVIDER=anthropic`) but is region-blocked for this product — prefer xAI.

Same **mirror-style** system prompt + diary excerpts (**RAG-lite**) + usage guards. Data residency narrative: **Taiwan (TW)**.

Cost / billing notes: see project docs `ai-cost-and-billing.md` and `account-and-billing-plan.md` (workspace).

## What works in this scaffold

| Area | Status |
|------|--------|
| Onboarding (brand, privacy, optional name, language strip) | Works (local) |
| **Account gate (phone SMS OTP)** | Firebase Phone Auth when plist present; else stub SMS; **Sign in with Apple** optional backup (paid team); DEBUG local session; Settings has the same |
| **StoreKit 2 Author Plus purchase / restore** | Works against local `.storekit` (Personal Team OK) or Sandbox when Connect products exist |
| Vault list / create / detail / edit / delete | Works — JSON on device; Firestore sync when Firebase Auth signed in; delete is soft-delete (`deleted` + `deletedAt`), Vault hides deleted rows |
| Mirror chat | **Android**: live **xAI Grok** when `XAI_API_KEY` / `DIARY_AI_API_KEY` is in `local.properties` (clear error if missing). **iOS**: mock until scheme key set; same OpenAI-compatible / Anthropic clients |
| Diary → Mirror context | Recent entries passed as prompt excerpts (RAG-lite) |
| Usage safeguards | Max context entries, max tokens, Free lifetime / Plus–Pro daily Mirror + vault upload quotas |
| Language switch 繁中 / 簡中 / English | Works (Settings + onboarding) |
| Gentle reminders toggle | Persisted preference only |
| Sample seed entry | First launch if vault empty |
| Android Play Billing (Plus / Pro × 月／季／年) | Client wired — needs Play Console SKUs for real prices / purchase; DEBUG simulate for quota smoke |

## What is stubbed / omitted

- **App Store Connect live products / prices** — need paid Apple Developer; IDs are configurable here; local `.storekit` covers Personal Team testing
- **Auth / receipt backend** — Firebase Phone Auth + Firestore text sync on iOS; Twilio / server receipt verify later
- **Android Play Console subscription products** — client ready; create the six SKUs + license testers before real purchase smoke
- **Android +86 release SMS** — Tencent path / KYC still pending (DEBUG +86 uses Firebase test numbers)
- **Voice / video diary UI / Storage** — duration polish deferred (DA-15); Free/Plus/Pro vault **counts** already include multimedia in one bucket
- **Text Firestore sync** is phase 1 (media Storage paths later)
- **Taiwan residency** — product narrative + prefer Firestore `asia-east1` when creating the database
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
