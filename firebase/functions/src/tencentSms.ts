/**
 * Minimal Tencent Cloud SMS SendSms client (API 3.0 / TC3-HMAC-SHA256).
 * SecretId / SecretKey stay in Functions secrets / env — never shipped to clients.
 *
 * Production (PowerShell-friendly):
 *   firebase functions:secrets:set TENCENT_SMS_SECRET_ID
 *   firebase functions:secrets:set TENCENT_SMS_SECRET_KEY
 *   firebase functions:secrets:set TENCENT_SMS_SDK_APP_ID
 *   firebase functions:secrets:set TENCENT_SMS_SIGN_NAME
 *   firebase functions:secrets:set TENCENT_SMS_TEMPLATE_ID
 * Then: firebase deploy --only functions
 *
 * Emulator: copy .env.example → .env (secrets not required locally).
 */

import * as crypto from "crypto";
import { defineSecret } from "firebase-functions/params";

/** Bound via `secrets: [...]` on send/verify callables — see index.ts. */
export const tencentSmsSecretId = defineSecret("TENCENT_SMS_SECRET_ID");
export const tencentSmsSecretKey = defineSecret("TENCENT_SMS_SECRET_KEY");
export const tencentSmsSdkAppId = defineSecret("TENCENT_SMS_SDK_APP_ID");
export const tencentSmsSignName = defineSecret("TENCENT_SMS_SIGN_NAME");
export const tencentSmsTemplateId = defineSecret("TENCENT_SMS_TEMPLATE_ID");

/** Pass to `onCall({ secrets: tencentSmsSecrets })`. */
export const tencentSmsSecrets = [
  tencentSmsSecretId,
  tencentSmsSecretKey,
  tencentSmsSdkAppId,
  tencentSmsSignName,
  tencentSmsTemplateId,
] as const;

export type TencentSmsConfig = {
  secretId: string;
  secretKey: string;
  sdkAppId: string;
  signName: string;
  templateId: string;
  /** API region header; default ap-guangzhou (SMS console default). */
  region: string;
};

function secretOrEnv(
  secret: { value: () => string },
  envName: string,
): string {
  try {
    const fromSecret = secret.value();
    if (typeof fromSecret === "string" && fromSecret.trim().length > 0) {
      return fromSecret.trim();
    }
  } catch {
    // Secret not bound (emulator without secrets) — fall through to env.
  }
  return (process.env[envName] || "").trim();
}

export function readTencentSmsConfig(): TencentSmsConfig | null {
  const secretId = secretOrEnv(tencentSmsSecretId, "TENCENT_SMS_SECRET_ID");
  const secretKey = secretOrEnv(tencentSmsSecretKey, "TENCENT_SMS_SECRET_KEY");
  const sdkAppId = secretOrEnv(tencentSmsSdkAppId, "TENCENT_SMS_SDK_APP_ID");
  const signName = secretOrEnv(tencentSmsSignName, "TENCENT_SMS_SIGN_NAME");
  const templateId = secretOrEnv(tencentSmsTemplateId, "TENCENT_SMS_TEMPLATE_ID");
  const region =
    (process.env.TENCENT_SMS_REGION || "ap-guangzhou").trim() || "ap-guangzhou";

  if (!secretId || !secretKey || !sdkAppId || !signName || !templateId) {
    return null;
  }
  return { secretId, secretKey, sdkAppId, signName, templateId, region };
}

/** True when missing keys should use local OTP stub (no carrier SMS). */
export function tencentStubAllowed(): boolean {
  const raw = (process.env.TENCENT_SMS_ALLOW_STUB || "").trim().toLowerCase();
  return raw === "1" || raw === "true" || raw === "yes";
}

/**
 * Send a mainland China SMS via Tencent Cloud SMS SendSms (2021-01-11).
 * @param phoneE164 E.164 e.g. +8613800138000
 */
export async function sendTencentSms(
  config: TencentSmsConfig,
  phoneE164: string,
  code: string,
): Promise<void> {
  const endpoint = "sms.tencentcloudapi.com";
  const service = "sms";
  const action = "SendSms";
  const version = "2021-01-11";
  const timestamp = Math.floor(Date.now() / 1000);
  const date = utcDate(timestamp);

  const payloadObj = {
    PhoneNumberSet: [phoneE164],
    SmsSdkAppId: config.sdkAppId,
    SignName: config.signName,
    TemplateId: config.templateId,
    TemplateParamSet: [code],
  };
  const payload = JSON.stringify(payloadObj);

  const authorization = signTc3({
    secretId: config.secretId,
    secretKey: config.secretKey,
    service,
    host: endpoint,
    action,
    timestamp,
    date,
    payload,
  });

  const res = await fetch(`https://${endpoint}`, {
    method: "POST",
    headers: {
      Authorization: authorization,
      "Content-Type": "application/json; charset=utf-8",
      Host: endpoint,
      "X-TC-Action": action,
      "X-TC-Timestamp": String(timestamp),
      "X-TC-Version": version,
      "X-TC-Region": config.region,
    },
    body: payload,
  });

  const bodyText = await res.text();
  let parsed: {
    Response?: {
      Error?: { Code?: string; Message?: string };
      SendStatusSet?: Array<{
        Code?: string;
        Message?: string;
        PhoneNumber?: string;
      }>;
      RequestId?: string;
    };
  } = {};
  try {
    parsed = JSON.parse(bodyText) as typeof parsed;
  } catch {
    throw new Error(
      `Tencent SMS non-JSON response (HTTP ${res.status}): ${bodyText.slice(0, 200)}`,
    );
  }

  const err = parsed.Response?.Error;
  if (err?.Code) {
    throw new Error(
      `Tencent SMS API error: code=${err.Code} message=${err.Message || ""}`,
    );
  }

  const status = parsed.Response?.SendStatusSet?.[0];
  if (!status || status.Code !== "Ok") {
    throw new Error(
      `Tencent SMS send failed: code=${status?.Code || res.status} message=${status?.Message || bodyText.slice(0, 200)}`,
    );
  }
}

type SignInput = {
  secretId: string;
  secretKey: string;
  service: string;
  host: string;
  action: string;
  timestamp: number;
  date: string;
  payload: string;
};

function signTc3(input: SignInput): string {
  const hashedPayload = sha256Hex(input.payload);
  const canonicalHeaders =
    `content-type:application/json; charset=utf-8\n` +
    `host:${input.host}\n` +
    `x-tc-action:${input.action.toLowerCase()}\n`;
  const signedHeaders = "content-type;host;x-tc-action";
  const canonicalRequest = [
    "POST",
    "/",
    "",
    canonicalHeaders,
    signedHeaders,
    hashedPayload,
  ].join("\n");

  const credentialScope = `${input.date}/${input.service}/tc3_request`;
  const stringToSign = [
    "TC3-HMAC-SHA256",
    String(input.timestamp),
    credentialScope,
    sha256Hex(canonicalRequest),
  ].join("\n");

  const secretDate = hmacSha256(`TC3${input.secretKey}`, input.date);
  const secretService = hmacSha256(secretDate, input.service);
  const secretSigning = hmacSha256(secretService, "tc3_request");
  const signature = hmacSha256(secretSigning, stringToSign).toString("hex");

  return (
    `TC3-HMAC-SHA256 Credential=${input.secretId}/${credentialScope}, ` +
    `SignedHeaders=${signedHeaders}, Signature=${signature}`
  );
}

function sha256Hex(message: string): string {
  return crypto.createHash("sha256").update(message, "utf8").digest("hex");
}

function hmacSha256(key: string | Buffer, message: string): Buffer {
  return crypto.createHmac("sha256", key).update(message, "utf8").digest();
}

function utcDate(timestampSec: number): string {
  const d = new Date(timestampSec * 1000);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
