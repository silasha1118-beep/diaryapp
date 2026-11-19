/**
 * DiaryVault Cloud Functions — mainland +86 SMS via Tencent Cloud SMS,
 * then Firebase Auth custom token so clients keep `users/{uid}` Firestore sync.
 *
 * Secrets (never ship to the Android APK) — set before deploy:
 *   firebase functions:secrets:set TENCENT_SMS_SECRET_ID
 *   firebase functions:secrets:set TENCENT_SMS_SECRET_KEY
 *   firebase functions:secrets:set TENCENT_SMS_SDK_APP_ID
 *   firebase functions:secrets:set TENCENT_SMS_SIGN_NAME
 *   firebase functions:secrets:set TENCENT_SMS_TEMPLATE_ID
 *
 * Optional env (not Secret Manager):
 *   TENCENT_SMS_REGION         (default `ap-guangzhou`)
 *   TENCENT_SMS_ALLOW_STUB     (`true` — local OTP when keys missing; never in prod)
 *
 * See ../TENCENT_SMS.md for Silas Console + PowerShell setup.
 *
 * Supersedes Aliyun Dysmsapi path (PR #87 closed, not merged).
 */

import "./globalOptions";

import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import {
  readTencentSmsConfig,
  sendTencentSms,
  tencentSmsSecrets,
  tencentStubAllowed,
} from "./tencentSms";
import { createOtpSession, generateOtpCode, verifyOtpSession } from "./otpStore";
import { FUNCTIONS_REGION } from "./globalOptions";

// Operator Vault admin (password → custom token). Re-exported for deploy.
// globalOptions is imported first above AND inside adminVault so region sticks.
export { mintAdminVaultSession, listAdminVaultAccounts } from "./adminVault";

admin.initializeApp();

const CHINA_E164 = /^\+86(1\d{10})$/;

function assertChinaMobile(phoneRaw: unknown): { e164: string; national: string } {
  if (typeof phoneRaw !== "string" || !phoneRaw.trim()) {
    throw new HttpsError("invalid-argument", "phoneE164 is required");
  }
  const digits = phoneRaw.trim().replace(/[^\d]/g, "");
  // Require country code 86 in the digit string (E.164 from the app always includes it).
  if (!digits.startsWith("86")) {
    throw new HttpsError(
      "invalid-argument",
      "Tencent SMS path only accepts mainland China +86 mobile numbers",
    );
  }
  const e164 = `+${digits}`;
  const match = CHINA_E164.exec(e164);
  if (!match) {
    throw new HttpsError(
      "invalid-argument",
      "Tencent SMS path only accepts mainland China +86 mobile numbers",
    );
  }
  return { e164, national: match[1] };
}

/**
 * Request an OTP for a +86 number. No Google reCAPTCHA on the client for this path.
 *
 * Response: `{ sessionId, stub?, stubDebugHint? }`
 */
export const sendTencentSmsOtp = onCall(
  {
    region: FUNCTIONS_REGION,
    enforceAppCheck: false,
    secrets: [...tencentSmsSecrets],
  },
  async (request) => {
    const { e164 } = assertChinaMobile(request.data?.phoneE164);
    const config = readTencentSmsConfig();
    const code = generateOtpCode();

    if (!config) {
      if (!tencentStubAllowed()) {
        throw new HttpsError(
          "failed-precondition",
          "Tencent SMS is not configured. Set TENCENT_SMS_* secrets, or TENCENT_SMS_ALLOW_STUB=true for local OTP.",
        );
      }
      const sessionId = await createOtpSession(e164, code, true);
      return {
        sessionId,
        stub: true,
        stubDebugHint: code,
      };
    }

    try {
      await sendTencentSms(config, e164, code);
    } catch (err) {
      const detail = err instanceof Error ? err.message : String(err);
      console.error("sendTencentSms failed", detail);
      throw new HttpsError("internal", `SMS send failed: ${detail}`);
    }

    const sessionId = await createOtpSession(e164, code, false);
    return { sessionId, stub: false };
  },
);

/**
 * Verify OTP and mint a Firebase Auth custom token for the phone user.
 * Creates the Auth user with `phoneNumber` when needed so Firestore identity matches Phone Auth.
 */
export const verifyTencentSmsOtp = onCall(
  {
    region: FUNCTIONS_REGION,
    enforceAppCheck: false,
    // Bound so verify can re-read config if send ever needs parity; OTP itself is in Firestore.
    secrets: [...tencentSmsSecrets],
  },
  async (request) => {
    const sessionId =
      typeof request.data?.sessionId === "string" ? request.data.sessionId.trim() : "";
    const code = typeof request.data?.code === "string" ? request.data.code.trim() : "";
    if (!sessionId || !/^\d{4,8}$/.test(code)) {
      throw new HttpsError("invalid-argument", "sessionId and numeric code are required");
    }

    const outcome = await verifyOtpSession(sessionId, code);
    if (!outcome.ok) {
      switch (outcome.reason) {
        case "invalid":
          throw new HttpsError("invalid-argument", "Invalid verification code");
        case "expired":
        case "consumed":
        case "not_found":
          throw new HttpsError(
            "failed-precondition",
            "Verification session expired. Request a new code.",
          );
        case "rate_limited":
          throw new HttpsError("resource-exhausted", "Too many attempts; try again later");
        default:
          throw new HttpsError("internal", "Verification failed");
      }
    }

    const phoneE164 = outcome.phoneE164;
    const auth = admin.auth();
    let uid: string;
    try {
      const existing = await auth.getUserByPhoneNumber(phoneE164);
      uid = existing.uid;
    } catch (err: unknown) {
      const codeName =
        err && typeof err === "object" && "code" in err
          ? String((err as { code: unknown }).code)
          : "";
      if (codeName !== "auth/user-not-found") {
        console.error("getUserByPhoneNumber failed", err);
        throw new HttpsError("internal", "Failed to resolve Auth user");
      }
      const created = await auth.createUser({
        phoneNumber: phoneE164,
      });
      uid = created.uid;
    }

    const customToken = await auth.createCustomToken(uid, {
      phoneAuthVia: "tencent",
      phoneE164,
    });

    return {
      customToken,
      uid,
      phoneE164,
      stub: outcome.stub,
    };
  },
);
