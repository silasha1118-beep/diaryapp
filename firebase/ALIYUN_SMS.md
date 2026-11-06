# +86 SMS via Alibaba Cloud (Aliyun) — Silas setup

**Routing (Android):**
- Dial **`+86`** → Aliyun SMS OTP (Cloud Functions `asia-east1`) → Firebase Auth **custom token** when secrets are set
- Aliyun keys / Functions missing (or CF stub mode) → **Firebase Phone Auth** fallback (Console *Phone numbers for testing* still work)
- All other dial codes keep **Firebase Phone Auth**

Secrets never go in the Android APK. The app only calls Firebase Callable Functions
`sendAliyunSmsOtp` / `verifyAliyunSmsOtp`.

---

## 1. Aliyun Console (公司账号 · 短信服务)

Fill these from the company Aliyun console — you will paste them into Firebase secrets in §2.

| What you need | Where in Aliyun | Env / secret name |
|---------------|-----------------|-------------------|
| **AccessKey ID** | RAM → 用户 → 创建 AccessKey（权限含 `AliyunDysmsFullAccess` 或仅 SendSms） | `ALIYUN_SMS_ACCESS_KEY_ID` |
| **AccessKey Secret** | same（只显示一次） | `ALIYUN_SMS_ACCESS_KEY_SECRET` |
| **签名 SignName** | 短信服务 → 国内消息 → **签名管理**（审核通过后的签名内容，不是 ID） | `ALIYUN_SMS_SIGN_NAME` |
| **模板 CODE** | 短信服务 → 国内消息 → **模板管理**（如 `SMS_xxxxxxxx`） | `ALIYUN_SMS_TEMPLATE_CODE` |
| Template param key | 模板变量名，默认 `code` | `ALIYUN_SMS_TEMPLATE_PARAM_KEY`（可选，不设即可） |

Steps:

1. Open [短信服务](https://dysms.console.aliyun.com/) with the **company** mainland-capable account.
2. Complete enterprise real-name / SMS qualification if prompted.
3. **签名管理** — create/approve a signature (品牌／App 名). Wait until **审核通过**. Copy the **签名名称** string.
4. **模板管理** — verification-code template, e.g.  
   `您的验证码为：${code}，10分钟内有效。`  
   Note **模板 CODE** (`SMS_…`). Param name must be `code` (or set `ALIYUN_SMS_TEMPLATE_PARAM_KEY`).
5. **RAM AccessKey** — create a RAM user with Dysmsapi SendSms; create AccessKey ID + Secret.  
   **Do not** put these in git, `BuildConfig`, or the APK.

---

## 2. Firebase secrets (Windows PowerShell · Huawei PC)

Project: **`diaryappsilas`**. Region: **`asia-east1`**. Blaze required.

### Prerequisites (once)

```powershell
# Node 22+ and Firebase CLI
node -v
npm install -g firebase-tools
firebase login
cd path\to\diaryapp\firebase
firebase use diaryappsilas
```

### Set the four required secrets

`secrets:set` prompts for the value (hidden). Paste from Aliyun, Enter, confirm. **Run all four before deploy** — Gen2 deploy fails if a bound secret does not exist yet.

```powershell
cd path\to\diaryapp\firebase

firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_ID
firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_SECRET
firebase functions:secrets:set ALIYUN_SMS_SIGN_NAME
firebase functions:secrets:set ALIYUN_SMS_TEMPLATE_CODE
```

Optional template param key (only if your template uses a name other than `code`) — set as a **runtime env var** in Cloud Console, or for emulator put in `functions/.env`:

```text
ALIYUN_SMS_TEMPLATE_PARAM_KEY=code
```

**Never** set `ALIYUN_SMS_ALLOW_STUB=true` in production. Emulator only: copy `functions/.env.example` → `functions/.env`.

### Build + deploy Functions

```powershell
cd path\to\diaryapp\firebase\functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

`firebase.json` predeploy uses a relative `npm --prefix ./functions run build` path so PowerShell works (no `$RESOURCE_DIR`).

Confirm in Console → Functions that `sendAliyunSmsOtp` and `verifyAliyunSmsOtp` are in **`asia-east1`**.

### Rotate a secret later

```powershell
firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_SECRET
firebase deploy --only functions
```

---

## 3. Android app

1. Keep `android/app/google-services.json` (same project).
2. Leave `DIARY_SMS_PROVIDER` empty → dial routing applies.
3. Rebuild / install (debug is fine for company Aliyun once secrets are deployed).

| Dial / secrets | Expected path |
|----------------|---------------|
| `+86` + secrets set | Aliyun CF → SMS → custom token → `users/{uid}` (no Google reCAPTCHA) |
| `+86` + secrets missing | Firebase Phone Auth fallback (Console test numbers OK) |
| `+852` / `+886` / others | Firebase Phone Auth |

---

## 4. Android Studio smoke test

### A. Console test number (no real SMS · Aliyun not required)

1. Firebase Console → Authentication → Phone → **Phone numbers for testing** — add `+8613xxxxxxxx` + fixed code (e.g. `123456`).
2. Leave Aliyun secrets **unset** (or know Functions will fall back), **or** temporarily use a number that will hit Firebase fallback.
3. Run **debug** app → dial **+86** → that number → Send → enter the **Console** fixed code.
4. Expect Firebase path (possible Play Integrity / reCAPTCHA). Login succeeds → Vault sync.

> If company Aliyun secrets **are** already set, `+86` hits Aliyun first and will **not** accept the Console test code. Use a non-`+86` dial for Firebase tests, or a real `+86` for Aliyun (§B).

### B. Real company Aliyun +86 (carrier SMS)

1. Secrets set + Functions redeployed (§2).
2. Use a real mainland mobile **not** on Firebase “Phone numbers for testing”.
3. Run app → **+86** → national number → Send.
4. Expect **no** Google reCAPTCHA browser; SMS from Aliyun 签名 arrives; enter code → signed in.
5. Confirm Firestore `users/{uid}/entries` writable after login.

### C. Non-+86 still Firebase

1. Dial **+886** or **+852** → Send → Firebase Phone Auth (Play Integrity preferred).

---

## 5. iOS later

Same callables + `signIn(withCustomToken:)`. No new backend when matching Android routing.

## Security notes

- OTP codes are **hashed** in Firestore (`aliyunOtpSessions`); 10‑minute TTL; max 5 verify attempts.
- Functions reject non-`+86` numbers on this path.
- AccessKeys stay server-side only (Secret Manager).
