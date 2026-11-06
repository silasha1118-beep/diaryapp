# +86 SMS via Alibaba Cloud (Aliyun) — Silas setup

**Routing:**
- **Release** dial **`+86`** → Aliyun SMS OTP (Cloud Functions) → Firebase Auth **custom token** when keys are set
- **DEBUG** dial **`+86`** → **Firebase Phone Auth** (so Console *Phone numbers for testing* work)
- Aliyun keys / Functions missing → Android falls back to **Firebase Phone Auth** (not a local stub OTP)
- All other dial codes keep **Firebase Phone Auth**

Secrets never go in the Android APK. The app only calls Firebase Callable Functions
`sendAliyunSmsOtp` / `verifyAliyunSmsOtp`.

## 1. Aliyun Console (短信服务)

1. Open [Alibaba Cloud SMS](https://dysms.console.aliyun.com/) (短信服务) with a mainland-capable account.
2. Complete enterprise real-name / SMS qualification if prompted (required for domestic SMS).
3. **国内消息 → 签名管理** — create a signature (签名), e.g. app / brand name. Wait until **审核通过**.
4. **国内消息 → 模板管理** — create a verification-code template, e.g.  
   `您的验证码为：${code}，10分钟内有效。`  
   Note the **模板 CODE** (e.g. `SMS_xxxxxxxx`). Param name should be `code` (or set `ALIYUN_SMS_TEMPLATE_PARAM_KEY`).
5. **AccessKey** — RAM 用户 with `AliyunDysmsFullAccess` (or narrower SendSms-only).  
   Create AccessKey ID + Secret. **Do not** put these in git or `BuildConfig`.

## 2. Firebase project (`diaryappsilas`)

1. Upgrade to **Blaze** (required for Cloud Functions + existing Phone Auth SMS).
2. Enable **Authentication** (Phone already on). Custom tokens need no extra toggle — Admin SDK mints them.
3. Confirm Firestore **`(default)`** exists (OTP session docs live in `aliyunOtpSessions/{id}`; Admin SDK only — clients cannot read them under current rules).
4. From repo `firebase/`:

```bash
cd firebase/functions
npm install
npm run build
cd ..
# First time: firebase login && use project diaryappsilas
firebase deploy --only functions
```

`firebase.json` predeploy uses `npm --prefix ./functions run build` (relative path) so Windows PowerShell works — do not use `$RESOURCE_DIR` / `%RESOURCE_DIR%` placeholders.

5. Set runtime env / secrets on the deployed functions (Cloud Console → Cloud Functions → each function → Edit → Runtime environment variables), **or** Secret Manager mapped as env:

| Name | Value |
|------|--------|
| `ALIYUN_SMS_ACCESS_KEY_ID` | RAM AccessKey ID |
| `ALIYUN_SMS_ACCESS_KEY_SECRET` | RAM AccessKey Secret |
| `ALIYUN_SMS_SIGN_NAME` | Approved 签名 |
| `ALIYUN_SMS_TEMPLATE_CODE` | Approved 模板 CODE |
| `ALIYUN_SMS_TEMPLATE_PARAM_KEY` | optional, default `code` |
| `ALIYUN_SMS_ALLOW_STUB` | `true` only for emulator / until keys exist |

Redeploy after changing secrets if your binding method requires it.

6. **Service account:** the default App Engine / Functions service account must be allowed to mint custom tokens (Firebase Admin — default project SA is enough).

## 3. Android app

1. Keep `android/app/google-services.json` (same as today).
2. Leave `DIARY_SMS_PROVIDER` empty → Firebase for non-`+86`; release `+86` uses Aliyun callables when configured.
3. Rebuild / install.
   - **DEBUG:** Login → **+86** → Send → enter Firebase Console test code (Play Integrity / emulator test path from Phone Auth harden).
   - **Release + Aliyun keys:** Login → **+86** → Send → enter Aliyun SMS (no Google reCAPTCHA on that path).

## 4. Stub / debug without Aliyun keys

- **Android DEBUG:** `+86` skips Aliyun and uses Firebase Phone Auth — add the number under Console **Phone numbers for testing**.
- **Cloud `ALLOW_STUB`:** if a release build somehow hits Aliyun CF in stub mode, the Android client **rejects** that stub session and falls back to Firebase Phone Auth (Console test codes), instead of accepting a random server OTP.
- **Android without JSON:** existing **stub** SMS provider still compiles and runs (any 6-digit / `DIARY_SMS_STUB_CODE`).
- Release builds that need real mainland SMS must have Aliyun keys; do not ship `ALLOW_STUB=true` in production.

## 5. Quick test matrix

| Dial / build | Expected path |
|--------------|----------------|
| `+86` DEBUG | Firebase Phone Auth (Console test numbers) |
| `+86` release + Aliyun keys | Aliyun CF → custom token → `users/{uid}` sync |
| `+86` release, keys missing | Firebase Phone Auth fallback |
| `+852` / `+886` / others | Firebase Phone Auth (Play Integrity preferred) |

## 6. iOS later

Same callables (`sendAliyunSmsOtp` / `verifyAliyunSmsOtp`) + `signIn(withCustomToken:)`. No new backend required when matching Android routing.

## Security notes

- OTP codes are **hashed** in Firestore (`aliyunOtpSessions`); 10‑minute TTL; max 5 verify attempts.
- Functions reject non-`+86` numbers on this path.
- AccessKeys stay server-side only.
