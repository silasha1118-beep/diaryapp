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

`google-services.json` is **gitignored** (same rule as iOS `GoogleService-Info.plist`). Without it the app builds and runs in **stub SMS** mode (no Firestore writes).

### 1. Add the Android app in Firebase Console

1. Open [Firebase Console](https://console.firebase.google.com/) → project **`diaryappsilas`**.
2. **Project settings → Your apps → Add app → Android**.
3. Android package name: **`com.diaryvault.app`** (must match `applicationId` in `app/build.gradle.kts`).
4. Download **`google-services.json`** and place it at:

```text
android/app/google-services.json
```

(See `android/app/google-services.json.example` for the expected shape — never commit the real file.)

5. Leave `DIARY_SMS_PROVIDER` empty (default). Empty + JSON present → Firebase. Set `stub` only to force local OTP.

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
> 号码必须在 Firebase Console → Authentication → Phone → **Phone numbers for testing**。  
> 测 `+852` 真短信时若已删除 `+85284028001`，输入 `123456` **永远**会「验证码不符」（`onCodeSent`／倒计时仍会出现）。加回列表即可；或改用短信真码。  
> **请同时保留** `+86 15014531587` 与（需要时）`+85284028001` 在测试列表，方便 Debug 装包登录。  
> DEBUG 另关闭 Instant Verification（`setTimeout(0)`），避免 MIUI 自动验证烧坏 `verificationId`。  
> Release `+86` 待 Tencent／DA-102 KYC；`+852` release 仍走 Firebase（60s auto-retrieve）。  
> Logcat：`VaultAuth`。

## Optional: cloud Mirror AI

```bash
export DIARY_AI_API_KEY="…"
export DIARY_AI_PROVIDER=openai   # or anthropic / xai
./gradlew :app:assembleDebug
```

## Layout

```text
android/
  app/google-services.json.example
  app/src/main/java/com/diaryvault/app/
    account/     # AccountSession (Firebase Auth state)
    auth/        # SMS OTP (DEBUG +86 → Firebase; release +86 → Aliyun CF / Firebase fallback)
    config/      # AuthConfig, FirebaseBootstrap, DiaryMediaConfig, AI / quotas
    data/        # AppPreferences, DiaryStore (JSON + soft-delete + sync)
    media/       # DiaryMediaFileStore (filesDir/diary_media/{entryId}/)
    sync/        # DiaryCloudSync + DiaryMediaCloudSync (Firestore + Storage parity with iOS)
    ui/          # auth, diary (+ media dock/playback), mirror, settings, theme
```

## Vault multimedia smoke (Windows Studio / Huawei)

1. Open `android/` in Android Studio; place `google-services.json`; Sync Gradle.
2. Run on a **physical Huawei / Play device** (mic + camera). Grant RECORD_AUDIO / CAMERA when prompted.
3. Sign in → Vault FAB `+` → attach voice (≤90s) / photo / video (≤60s) → Save (media-only OK).
4. List shows media icons; open detail → play voice / view image / play video.
5. Settings → **Sync now** → Console: entry has `attachments` + `hasMedia` + `sourcePlatform: android`; Storage under `users/{uid}/entries/{entryId}/`.
6. Swipe-delete the entry — it disappears from Vault; Firestore keeps `deleted` / `deletedAt`; Storage objects remain (same as iOS / admin Vault).
