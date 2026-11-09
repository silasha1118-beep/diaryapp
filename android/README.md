# 日記金庫 — Android (Kotlin + Jetpack Compose)

Compose app that mirrors the iOS `DiaryVault/` product: phone SMS OTP → three tabs（金庫／鏡子／設定）→ vault list, Mirror chat, Settings. **Dial routing:** `+86` → Aliyun SMS via Cloud Functions (custom token; no reCAPTCHA); other dials → **Firebase Phone Auth**. Firestore text sync matches iOS phase 1 when `google-services.json` is present; otherwise SMS falls back to a local stub.

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

`google-services.json` is **gitignored** (same rule as iOS `GoogleService-Info.plist`). It is **not** in the repo and there is no CI secret for it — Silas must download it once onto the Windows PC. **Debug `assembleDebug` / Run fails closed** if the file is missing or `project_id` ≠ `diaryappsilas` (escape hatch: `-Pdiary.allowStubSms=true` for local stub OTP only).

Without the real file the APK falls back to **stub SMS** — Console test codes like `123456` never verify, and Debug UI can show `Firebase project=?`.

### 0. Silas Windows check (do this first when Redmi shows `project=?`)

Open in Notepad / VS Code:

```text
C:\dev\diaryapp\android\app\google-services.json
```

Confirm a line exactly like:

```json
"project_id": "diaryappsilas"
```

Also confirm `"package_name": "com.diaryvault.app"`. If the file is **missing**, or `project_id` is wrong/blank → follow §1 below, then Android Studio **Sync**, **uninstall** the app on Redmi, **Run Debug** again.

### 1. Add the Android app in Firebase Console

1. Open [Firebase Console](https://console.firebase.google.com/) → project **`diaryappsilas`**.
2. **项目设置** (Project settings) → **您的应用** → **添加应用** → **Android** (Add app if Android `com.diaryvault.app` is not listed yet).
3. Android package name: **`com.diaryvault.app`** (must match `applicationId` in `app/build.gradle.kts`).
4. Download **`google-services.json`** and save it at:

```text
C:\dev\diaryapp\android\app\google-services.json
```

(Repo-relative: `android/app/google-services.json`. See `android/app/google-services.json.example` for the expected shape — **never commit** the real file; it stays gitignored.)

5. Leave `DIARY_SMS_PROVIDER` empty (default). Empty + JSON present → Firebase. Set `stub` / `-Pdiary.allowStubSms=true` only to force local OTP without Firebase.

### 2. Register SHA-1 **and SHA-256** for Phone Auth (required for live SMS)

Android Phone Auth completes Play Integrity / reCAPTCHA using the **signing certificate** of the APK you install. iOS SHA does **not** cover Android. Fingerprints must come from the **same PC that built the APK you install** (Silas: Huawei Windows at `C:\dev\diaryapp`).

**Windows（简体 UI / PowerShell）— debug keystore on THIS PC:**

1. 打开 **Windows PowerShell**（或 Android Studio 底部 **终端**）。
2. 若本机已装 JDK／Android Studio，运行：

```powershell
keytool -list -v -alias androiddebugkey `
  -keystore "$env:USERPROFILE\.android\debug.keystore" `
  -storepass android -keypass android
```

（默认路径即 `C:\Users\<你的用户名>\.android\debug.keystore`。若从未跑过 debug 构建，先在 Android Studio 对 `C:\dev\diaryapp\android` 执行一次 **运行**／`installDebug` 再取指纹。）

3. 在输出中复制 **SHA1:** 与 **SHA256:** 两行（去掉标签，只留冒号分隔的十六进制）。
4. 打开 [Firebase Console](https://console.firebase.google.com/) → 项目 **`diaryappsilas`** → ⚙️ **项目设置**（Project settings）→ **您的应用** → Android `com.diaryvault.app` → **添加指纹**（Add fingerprint）→ 分别粘贴 SHA-1 与 SHA-256 → 保存。
5. **重新安装** debug APK（卸载旧包再 Run，或 `.\gradlew :app:installDebug`），避免沿用旧签名状态。

**macOS / Linux（参考）:**

```bash
keytool -list -v -alias androiddebugkey \
  -keystore ~/.android/debug.keystore \
  -storepass android -keypass android
```

If you install via a different machine or CI keystore, fingerprint **that** key — not a Mac default when the APK was built on Windows.

Without matching SHA fingerprints, send fails with app-verification / reCAPTCHA / `invalid-app-credential` — the UI surfaces that as the app-verification error string.

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
| **SHA-1 + SHA-256** | Project settings → Android `com.diaryvault.app` | Must match the **keystore that signed the installed APK** (Windows: `%USERPROFILE%\.android\debug.keystore` on the build PC). |
| **App Check enforced** | App Check → Authentication | Enforce without an Android debug token → send fails (red error). Leave unenforced while wiring. |
| **API key HTTP referrer** | Google Cloud → Credentials → Browser key | reCAPTCHA needs `https://PROJECT_ID.firebaseapp.com` allowed (or unrestricted) for the Auth API key. |
| **Authorized domains** | Auth → Settings → Authorized domains | Keep default `diaryappsilas.firebaseapp.com` (and `diaryappsilas.web.app`). Removing it breaks the reCAPTCHA fallback handler. |
| **SHA-256** | Project settings → Android app fingerprints | Required for **Play Integrity** so Phone Auth stays in-app and skips the flaky Chrome Custom Tabs / `sessionStorage` path. |

App code: blank `verificationId` fails closed; Play instant-verify auto-signs-in. **Prefer Play Integrity** (never force reCAPTCHA). **Emulator debug** disables app verification so Console **test phone numbers** work without Chrome (AVD Chrome often ANRs / “missing initial state”). Real SMS: use a **physical device with Google Play** + matching SHA-1/SHA-256. If a browser page still opens and fails, the app fails back to an in-app error (close that page and retry) instead of stranding you on `*.firebaseapp.com`.

### 4. Firestore (same as iOS)

1. Firestore Database **`(default)`** — paste / publish [`firebase/firestore.rules`](../firebase/firestore.rules).
2. Sync path: `users/{uid}/entries/{entryId}` with soft-delete (`deleted` / `deletedAt` / `status`), `sourcePlatform` (`ios` | `android`), and media on the **same** entry (`attachments` / `hasMedia` / `contentType`). Bytes go to Storage `users/{uid}/entries/{entryId}/{fileName}` (same as iOS). Soft-delete hides the entry; Storage objects stay. Vault-like cloud viewer: [`../firebase/admin/README.md`](../firebase/admin/README.md).

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

## How to test live SMS (Silas checklist) — `+852` 真短信

固定测试码能登入 **≠** 已开通运营商短信。Countdown／OTP 页只代表 Firebase `onCodeSent`。要用真实短信登录 `+85284028001`（或你的其他港号），在 **Firebase Console** 按下面点（菜单名以英文 Console 为准；Windows 本机步骤用简体）：

### A. Console（必须本人登录；本仓库／云端代理无法代登）

1. 打开 [Firebase Console](https://console.firebase.google.com/) → 项目 **`diaryappsilas`**。
2. **升级 Blaze（按量付费）** — 左下角／项目概览的 **Upgrade**／**升级**。自 2024-09 起 **Spark 无法发送真实 Phone Auth 短信**；测试号仍可假发送。这是唯一可能产生费用、且只有账户所有者能开的步骤。
3. **Authentication** → **Sign-in method** → **Phone** → 找到 **Phone numbers for testing**（测试电话号码）→ **删除** `+85284028001`（或任意你要收真短信的号码）。留在列表 = 永远不发运营商短信，只认你设的固定码。
4. **Authentication** → **Settings** → **SMS region policy**（短信地区政策）→ 允许清单加入 **Hong Kong / HK**（以及 TW／CN／MY／SG／TH／JP）→ **Save**。
5. ⚙️ **Project settings** → **Your apps** → Android `com.diaryvault.app` → 确认已添加 **本机 Windows debug keystore** 的 SHA-1 **与** SHA-256（见上文 §2）。
6. **App Check**：若对 Authentication **强制执行**且未登记 Android debug token，发送会失败。调试期请保持 Auth **未强制**，或登记 debug token。
7. （可选）**Authentication** → **Usage**：发送后再看当日 Phone Auth 是否计次。

### B. 本机 Windows（`C:\dev\diaryapp`）

1. 确认 `android\app\google-services.json` 存在且 package 为 `com.diaryvault.app`；`DIARY_SMS_PROVIDER` 留空。
2. Android Studio 打开 **`android/`** → 连上带 Google Play 的真机（Redmi）→ **运行** debug，或在项目目录：

```powershell
cd C:\dev\diaryapp\android
.\gradlew :app:installDebug
```

3. App → 登录 → 国码 **+852** → 输入号码（已从测试列表移除）→ **发送验证码**。
4. 应收到运营商短信；输入短信内 6 位码完成登录（不要再输 Console 固定码）。
5. Logcat 过滤：`VaultAuth|FirebasePhoneSMS`。`onCodeSent` 仍会出现；若号码还在测试列表，日志会提示无运营商短信。

**模拟器：** 仍用 Console 测试号 + 固定码（debug 模拟器会跳过 Integrity／Chrome）。**真短信请用实体机 + Google Play。**

> **Debug 固定码登录（+86 与 +852 相同规则）：**  
> 号码必须在 Firebase Console → Authentication → Phone → **Phone numbers for testing**，且本机 `google-services.json` 的 **project_id 必须是 `diaryappsilas`**（与 Console 同一项目）。  
> Debug 对内置测试号（`+85284028001` / `+8615014531587` → `123456`）会调用 Firebase 官方  
> `setAutoRetrievedSmsCodeForPhoneNumber` + `setAppVerificationDisabledForTesting(true)`，发送后应 **自动登录**（不必手输）。  
> 若仍出现验证码栏：输入 `123456`；失败时红字会带 `project=` 与 Firebase `error=`（方便发现 json 指错项目／session 过期被误报成「验证码不符」）。  
> 测 `+852` 真短信时临时删除测试号即可；删完不要再输 `123456`。  
> Release `+86` 待 Tencent／DA-102 KYC；`+852` release 仍走 Firebase（60s auto-retrieve）。  
> Logcat：`VaultAuth|FirebasePhoneSMS`。

## Cloud Mirror AI (xAI Grok)

Working provider: **xAI Grok** (OpenAI-compatible). No Firebase Functions proxy in-repo yet — key stays on the device build via gitignored `local.properties`.

### Silas — Windows Studio steps（简体）

1. Get an API key at https://console.x.ai → API Keys.
2. Open (or create) `C:\dev\diaryapp\android\local.properties`（Android Studio 已有 `sdk.dir=…` 时直接追加，不要删掉 sdk.dir）:

```properties
XAI_API_KEY=xai-your-key-here
# optional overrides (defaults below are already correct for Grok):
# DIARY_AI_BASE_URL=https://api.x.ai
# DIARY_AI_MODEL=grok-4.7
# DIARY_AI_PROVIDER=openai
```

`DIARY_AI_API_KEY` is accepted as an alias of `XAI_API_KEY`. **Never commit** `local.properties` (already gitignored).

3. Android Studio → **Sync Project with Gradle Files** → Run Debug（须重新编译，BuildConfig 才会带上密钥）.
4. Open **镜子 / Mirror** → send a message. Banner should show remaining Free lifetime quota (40). Reply comes from live Grok.

Without a key the app still builds; Mirror shows a clear “needs key” banner and does not invent mock replies.

### Env alternative (CI / terminal)

```bash
export XAI_API_KEY="…"
# or: export DIARY_AI_API_KEY="…"
./gradlew :app:assembleDebug
```

Quotas (local counter): Free **40 lifetime** · Plus **8/day** · Pro **16/day**.

## Play Billing（訂閱 / Author Plus · Author Pro）

Client uses **Google Play Billing Library 7** (`billing-ktx`). Product IDs match iOS StoreKit:

| Tier | Period | Product ID (subscription) | Base plan ID | Billing period |
|------|--------|---------------------------|--------------|----------------|
| Author Plus | monthly | `com.diaryvault.author.plus.monthly` | `monthly` | 1 month |
| Author Plus | quarterly | `com.diaryvault.author.plus.quarterly` | `quarterly` | 3 months |
| Author Plus | yearly | `com.diaryvault.author.plus.yearly` | `yearly` | 1 year |
| Author Pro | monthly | `com.diaryvault.author.pro.monthly` | `monthly` | 1 month |
| Author Pro | quarterly | `com.diaryvault.author.pro.quarterly` | `quarterly` | 3 months |
| Author Pro | yearly | `com.diaryvault.author.pro.yearly` | `yearly` | 1 year |

App **compiles and runs without** Console products — paywall shows「价格尚未加载／Plans could not be loaded」until SKUs exist. Free vault copy never claims device-only storage (signed-in Free includes cloud sync). Client uses Play **subscription replacement** when upgrading Plus → Pro or changing period (no release stub that skips payment).

### What works in the app now

- Settings → **查看方案／恢复购买** → full paywall (Plus + Pro × 月／季／年 + restore)
- Vault create / Mirror exhausted → upgrade CTA → compact checkout sheet
- Entitlements drive existing Free 40 / Plus 8/day / Pro 16/day quota guards
- **DEBUG only:** paywall → **模拟 Plus / 模拟 Pro / 清除模拟** (no Play charge) for Redmi quota smoke

### Silas — Play Console 商品（华为 Windows 简体 UI）

在 [Google Play Console](https://play.google.com/console) 打开应用 **`com.diaryvault.app`**（若尚未创建应用：所有应用 → 创建应用 → 填写名称「日記金庫」／包名 `com.diaryvault.app`）。

1. 左侧 **获利** → **商品** → **订阅**（Monetize → Products → Subscriptions）。
2. 建立 **6 个订阅商品**（每个周期一个独立 product ID，与上表完全一致；不要改拼写）。
3. 每个商品内再建 **一个自动续订基础方案（base plan）**，ID 与周期按下表（可复制）：
   - 月订 → base plan ID `monthly` · 续订周期 **1 个月**
   - 季订 → base plan ID `quarterly` · **3 个月**
   - 年订 → base plan ID `yearly` · **1 年**
   - 价格按地区设定（TW／HK／CN／MY／SG／TH／JP）。
4. 把 6 个商品都设为 **有效**（Active）。
5. **设置** → **授权许可测试**（License testing）→ 加入你的 Google 测试账号（Gmail）。
6. **先**完成应用创建与上传签名 AAB（见下文 § Play release AAB），再把 APK／AAB 放到 **内部测试** 轨道；许可测试账号从该轨道安装后才能做真实测试购买。纯 sideload Debug 通常查不到商品。
7. 静态回复（可选）：许可测试账号可在 Play 商店对测试购买使用「测试卡片」／延迟结算等选项（Console → 设置 → 授权许可测试说明）。

完成前：Debug 构建用 paywall 底部 **模拟 Plus／Pro** 验证额度（仅 DEBUG；Release 无模拟、不会跳过付款）；Console SKU 未建时真实购买按钮会提示商品不可用。

### Redmi 真机测试（手机 UI 繁體）

1. 連上 Redmi → Android Studio **執行** Debug（包名 `com.diaryvault.app`）。
2. **設定** → **訂閱** → **查看方案／恢復購買**：
   - Console 尚未建商品時：看到「目前無法從 Google Play 載入方案」。
   - Debug：點 **模擬 Plus** → 回設定應顯示 Author Plus；鏡子／金庫額度變每日 8。
3. 金庫寫滿 Free 40 或鏡子用盡 → 出現升級 CTA → 開啟結帳表。
4. Console SKU 就緒後：用 **許可測試** Google 帳號登入 Play → 真機訂閱 → **恢復購買**。

## Layout

```text
android/
  app/google-services.json.example
  app/src/main/java/com/diaryvault/app/
    account/     # AccountSession (Firebase Auth state)
    auth/        # SMS OTP (DEBUG +86 → Firebase; release +86 → Aliyun CF / Firebase fallback)
    billing/     # BillingConfig + SubscriptionStore (Play Billing)
    config/      # AuthConfig, FirebaseBootstrap, DiaryMediaConfig, AI / quotas
    data/        # AppPreferences, DiaryStore (JSON + soft-delete + sync)
    media/       # DiaryMediaFileStore (filesDir/diary_media/{entryId}/)
    sync/        # DiaryCloudSync + DiaryMediaCloudSync (Firestore + Storage parity with iOS)
    ui/          # auth, diary, mirror, paywall, settings, theme
```

## Vault multimedia smoke (Windows Studio / Huawei)

1. Open `android/` in Android Studio; place `google-services.json`; Sync Gradle.
2. Run on a **physical Huawei / Play device** (mic + camera). Grant RECORD_AUDIO / CAMERA when prompted.
3. Sign in → Vault FAB `+` → attach voice (≤90s) / photo / video (≤60s) → Save (media-only OK).
4. List shows media icons; open detail → play voice / view image / play video.
5. Settings → **Sync now** → Console: entry has `attachments` + `hasMedia` + `sourcePlatform: android`; Storage under `users/{uid}/entries/{entryId}/`.
6. Swipe-delete the entry — it disappears from Vault; Firestore keeps `deleted` / `deletedAt`; Storage objects remain (same as iOS / admin Vault).

## Play release AAB (upload key — do this before Console unlock)

**IDs today:** `applicationId` / namespace `com.diaryvault.app` · `versionName` `0.1.0` · `versionCode` `1`.

Release signing is **optional at configure time**: if all four keys below are set and the keystore file exists, `:app:bundleRelease` signs with that upload key. If any are missing, release still builds but is **unsigned** (not Play-uploadable).

### 1. Create upload keystore once (Windows PowerShell — keep an offline backup)

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v `
  -keystore C:\dev\diaryapp\android\diaryvault-upload.jks `
  -alias diaryvault -keyalg RSA -keysize 2048 -validity 10000
```

Use the **same** store password and key password (PKCS12). Never commit the `.jks` (gitignored). Losing it blocks app updates if you opt out of Play App Signing backup.

### 2. Put secrets in `android/local.properties` (gitignored)

```properties
DIARY_RELEASE_STORE_FILE=C:\\dev\\diaryapp\\android\\diaryvault-upload.jks
DIARY_RELEASE_STORE_PASSWORD=...
DIARY_RELEASE_KEY_ALIAS=diaryvault
DIARY_RELEASE_KEY_PASSWORD=...
```

Same four names also work as environment variables. See `local.properties.example`.

### 3. Build the AAB

```powershell
cd C:\dev\diaryapp\android
.\gradlew :app:bundleRelease
```

Artifact: `android\app\build\outputs\bundle\release\app-release.aab`

Confirm signing: `.\gradlew :app:signingReport` → **Variant: release** must show your upload keystore (not `debug.keystore`).

### 4. When Play Console developer approval unlocks

1. Create the app (package `com.diaryvault.app`) if missing.
2. **Play App Signing** → upload / enroll the upload key (or let Play generate the app signing key and keep your upload key for AABs).
3. Register **release** SHA-1 + SHA-256 (from `signingReport` / keytool on the **upload** keystore) on Firebase Android `com.diaryvault.app` for Phone Auth / Integrity.
4. Production / testing track → **Upload** that `app-release.aab` (do not use the debug APK).
5. Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts` before each subsequent upload.
