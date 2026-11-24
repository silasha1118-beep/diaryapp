# +86 SMS via Tencent Cloud — Silas setup

**Supersedes Aliyun** (PR #87 closed, not merged — “太麻煩”).

**Routing (Android):**
- Dial **`+86`** → Tencent Cloud SMS OTP (Cloud Functions `asia-east1`) → Firebase Auth **custom token** when secrets are set
- **DEBUG and Release** both try Tencent first for +86 (company account testable from Studio debug)
- Tencent secrets / Functions missing → Android falls back to **Firebase Phone Auth** (Console *Phone numbers for testing* still work)
- All other dial codes keep **Firebase Phone Auth**

Secrets never go in the Android APK. The app only calls Firebase Callable Functions
`sendTencentSmsOtp` / `verifyTencentSmsOtp`.

## 1. Tencent Cloud Console (国内短信) — first steps

1. Open **[短信控制台](https://console.cloud.tencent.com/smsv2)** and sign in with the company Tencent Cloud account.
2. Complete **实名认证** if prompted (国内短信 requires it). Prefer **企业认证** for signature/template APIs.
3. **应用管理** → create / open an SMS app → note **SdkAppId** (e.g. `1400xxxxxx`).  
   [应用管理](https://console.cloud.tencent.com/smsv2/app-manage)
4. **国内短信 → 签名管理** — create a signature (签名), e.g. app / brand name. Wait until **审核通过**.  
   [签名管理](https://console.cloud.tencent.com/smsv2/csms-sign)
5. **国内短信 → 正文模板管理** — create a **验证码** template, e.g.  
   `您的验证码为：{1}，10分钟内有效。`  
   Note the **模板 ID** (numeric). `{1}` maps to the OTP we send as `TemplateParamSet[0]`.  
   [正文模板](https://console.cloud.tencent.com/smsv2/csms-template)
6. **购买国内短信套餐包** if the console prompts (required before production sends).
7. **API 密钥** — [访问管理 → API密钥管理](https://console.cloud.tencent.com/cam/capi) → 新建密钥 → copy **SecretId** + **SecretKey**.  
   Prefer a RAM 子用户 with SMS send permission. **Do not** put these in git or `BuildConfig`.

Quick entry: [国内短信快速入门](https://cloud.tencent.com/document/product/382/37745)

## 2. Secrets table

| Secret | From Tencent Console |
|--------|----------------------|
| `TENCENT_SMS_SECRET_ID` | CAM API 密钥 → SecretId |
| `TENCENT_SMS_SECRET_KEY` | CAM API 密钥 → SecretKey |
| `TENCENT_SMS_SDK_APP_ID` | 短信应用 SdkAppId (`1400…`) |
| `TENCENT_SMS_SIGN_NAME` | 已审核通过的 **签名内容**（不是签名 ID） |
| `TENCENT_SMS_TEMPLATE_ID` | 已审核通过的 **模板 ID** |

Optional env (not Secret Manager):

| Name | Default | Notes |
|------|---------|--------|
| `TENCENT_SMS_REGION` | `ap-guangzhou` | `X-TC-Region` for SendSms |
| `TENCENT_SMS_ALLOW_STUB` | unset | `true` only for emulator; **never in prod** |

## 3. PowerShell (Huawei PC) — set secrets + deploy

```powershell
cd path\to\diaryapp\firebase
firebase use diaryappsilas

firebase functions:secrets:set TENCENT_SMS_SECRET_ID
firebase functions:secrets:set TENCENT_SMS_SECRET_KEY
firebase functions:secrets:set TENCENT_SMS_SDK_APP_ID
firebase functions:secrets:set TENCENT_SMS_SIGN_NAME
firebase functions:secrets:set TENCENT_SMS_TEMPLATE_ID

cd functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

**Set all five secrets before deploy** — Gen2 refuses deploy if a bound secret does not exist yet.

`firebase.json` predeploy uses `npm --prefix ./functions run build` (relative path) so Windows PowerShell works.

## 4. Firebase project checklist

1. **Blaze** plan (Functions + existing Phone Auth SMS).
2. **Authentication** Phone enabled. Custom tokens need no extra toggle — Admin SDK mints them.
3. Firestore **`(default)`** exists (OTP sessions in `tencentOtpSessions/{id}`; Admin SDK only).
4. Default Functions / App Engine service account can mint custom tokens (default project SA is enough).

## 5. Android smoke test

1. Keep `android/app/google-services.json`. Leave `DIARY_SMS_PROVIDER` empty.
2. **Secrets unset / Functions missing:** Login → **+86** → falls back to Firebase Phone Auth → Console test code works (DEBUG or release).
3. **Secrets set + redeployed:** Login → **+86** (DEBUG or release) → Tencent SMS (no Google reCAPTCHA) → custom token login → `users/{uid}` sync.
4. **+852 / +886 / others:** still Firebase Phone Auth.

| Dial / setup | Expected path |
|--------------|----------------|
| `+86` + Tencent secrets | Tencent CF → custom token |
| `+86` secrets / Functions missing | Firebase Phone Auth fallback |
| `+852` / `+886` / others | Firebase Phone Auth |

## 6. Stub / debug

- Cloud `TENCENT_SMS_ALLOW_STUB=true` without keys: CF returns stub OTP; Android **rejects** stub and falls back to Firebase (Console test codes).
- Do **not** ship `ALLOW_STUB=true` in production.
- Android without `google-services.json`: existing local **stub** SMS provider still works.

## 7. iOS later

Same callables (`sendTencentSmsOtp` / `verifyTencentSmsOtp`) + `signIn(withCustomToken:)`. No new backend required when matching Android routing.

## Security notes

- OTP codes are **hashed** in Firestore (`tencentOtpSessions`); 10‑minute TTL; max 5 verify attempts.
- Functions reject non-`+86` numbers on this path.
- SecretId / SecretKey stay server-side only.
