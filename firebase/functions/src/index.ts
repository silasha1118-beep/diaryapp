/**
 * DiaryVault Cloud Functions — mainland +86 SMS via Alibaba Cloud Dysmsapi,
 * then Firebase Auth custom token so clients keep `users/{uid}` Firestore sync.
 *
 * Secrets (never ship to the Android APK) — set before deploy:
 *   firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_ID
 *   firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_SECRET
 *   firebase functions:secrets:set ALIYUN_SMS_SIGN_NAME
 *   firebase functions:secrets:set ALIYUN_SMS_TEMPLATE_CODE
 *
 * Optional env (not Secret Manager):
 *   ALIYUN_SMS_TEMPLATE_PARAM_KEY   (default `code`)
 *   ALIYUN_SMS_ALLOW_STUB           (`true` — local OTP when keys missing; never in prod)
 *
 * See ../ALIYUN_SMS.md for Silas Console + PowerShell setup.
 *
 * DA-159: prefer Aliyun for +86 (supersedes open Tencent PR #119 path for ops).
 */

import "./globalOptions";

import * as admin from "firebase-admin";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import {
  aliyunSmsSecrets,
  aliyunStubAllowed,
  readAliyunSmsConfig,
  sendAliyunSms,
} from "./aliyunSms";
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
      "Aliyun SMS path only accepts mainland China +86 mobile numbers",
    );
  }
  const e164 = `+${digits}`;
  const match = CHINA_E164.exec(e164);
  if (!match) {
    throw new HttpsError(
      "invalid-argument",
      "Aliyun SMS path only accepts mainland China +86 mobile numbers",
    );
  }
  return { e164, national: match[1] };
}

/**
 * Request an OTP for a +86 number. No Google reCAPTCHA on the client for this path.
 *
 * Response: `{ sessionId, stub?, stubDebugHint? }`
 */
export const sendAliyunSmsOtp = onCall(
  {
    region: FUNCTIONS_REGION,
    enforceAppCheck: false,
    secrets: [...aliyunSmsSecrets],
  },
  async (request) => {
    const { e164, national } = assertChinaMobile(request.data?.phoneE164);
    const config = readAliyunSmsConfig();
    const code = generateOtpCode();

    if (!config) {
      if (!aliyunStubAllowed()) {
        throw new HttpsError(
          "failed-precondition",
          "Aliyun SMS is not configured. Set ALIYUN_SMS_* secrets, or ALIYUN_SMS_ALLOW_STUB=true for local OTP.",
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
      await sendAliyunSms(config, national, code);
    } catch (err) {
      const detail = err instanceof Error ? err.message : String(err);
      console.error("sendAliyunSms failed", detail);
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
export const verifyAliyunSmsOtp = onCall(
  {
    region: FUNCTIONS_REGION,
    enforceAppCheck: false,
    // Bound so verify can re-read config if send ever needs parity; OTP itself is in Firestore.
    secrets: [...aliyunSmsSecrets],
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
      phoneAuthVia: "aliyun",
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
