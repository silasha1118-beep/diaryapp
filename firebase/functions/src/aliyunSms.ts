/**
 * Minimal Alibaba Cloud Dysmsapi SendSms client (RPC signature v1.0).
 * AccessKey stays in Functions secrets / env — never shipped to clients.
 *
 * Production (PowerShell-friendly):
 *   firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_ID
 *   firebase functions:secrets:set ALIYUN_SMS_ACCESS_KEY_SECRET
 *   firebase functions:secrets:set ALIYUN_SMS_SIGN_NAME
 *   firebase functions:secrets:set ALIYUN_SMS_TEMPLATE_CODE
 * Then: firebase deploy --only functions
 *
 * Emulator: copy .env.example → .env (secrets not required locally).
 */

import * as crypto from "crypto";
import { defineSecret } from "firebase-functions/params";

/** Bound via `secrets: [...]` on send/verify callables — see index.ts. */
export const aliyunSmsAccessKeyId = defineSecret("ALIYUN_SMS_ACCESS_KEY_ID");
export const aliyunSmsAccessKeySecret = defineSecret("ALIYUN_SMS_ACCESS_KEY_SECRET");
export const aliyunSmsSignName = defineSecret("ALIYUN_SMS_SIGN_NAME");
export const aliyunSmsTemplateCode = defineSecret("ALIYUN_SMS_TEMPLATE_CODE");

/** Pass to `onCall({ secrets: aliyunSmsSecrets })`. */
export const aliyunSmsSecrets = [
  aliyunSmsAccessKeyId,
  aliyunSmsAccessKeySecret,
  aliyunSmsSignName,
  aliyunSmsTemplateCode,
] as const;

export type AliyunSmsConfig = {
  accessKeyId: string;
  accessKeySecret: string;
  signName: string;
  templateCode: string;
  /** Template variable name for the OTP, usually `code`. */
  templateParamKey: string;
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

export function readAliyunSmsConfig(): AliyunSmsConfig | null {
  const accessKeyId = secretOrEnv(aliyunSmsAccessKeyId, "ALIYUN_SMS_ACCESS_KEY_ID");
  const accessKeySecret = secretOrEnv(
    aliyunSmsAccessKeySecret,
    "ALIYUN_SMS_ACCESS_KEY_SECRET",
  );
  const signName = secretOrEnv(aliyunSmsSignName, "ALIYUN_SMS_SIGN_NAME");
  const templateCode = secretOrEnv(aliyunSmsTemplateCode, "ALIYUN_SMS_TEMPLATE_CODE");
  const templateParamKey =
    (process.env.ALIYUN_SMS_TEMPLATE_PARAM_KEY || "code").trim() || "code";

  if (!accessKeyId || !accessKeySecret || !signName || !templateCode) {
    return null;
  }
  return { accessKeyId, accessKeySecret, signName, templateCode, templateParamKey };
}

/** True when missing keys should use local OTP stub (no carrier SMS). */
export function aliyunStubAllowed(): boolean {
  const raw = (process.env.ALIYUN_SMS_ALLOW_STUB || "").trim().toLowerCase();
  return raw === "1" || raw === "true" || raw === "yes";
}

/**
 * Send a mainland China SMS via Dysmsapi.
 * @param phoneNational 11-digit CN mobile without +86
 */
export async function sendAliyunSms(
  config: AliyunSmsConfig,
  phoneNational: string,
  code: string,
): Promise<void> {
  const params: Record<string, string> = {
    AccessKeyId: config.accessKeyId,
    Action: "SendSms",
    Format: "JSON",
    PhoneNumbers: phoneNational,
    RegionId: "cn-hangzhou",
    SignName: config.signName,
    SignatureMethod: "HMAC-SHA1",
    SignatureNonce: crypto.randomUUID(),
    SignatureVersion: "1.0",
    TemplateCode: config.templateCode,
    TemplateParam: JSON.stringify({ [config.templateParamKey]: code }),
    Timestamp: new Date().toISOString().replace(/\.\d{3}Z$/, "Z"),
    Version: "2017-05-25",
  };

  const canonicalized = Object.keys(params)
    .sort()
    .map((k) => `${percentEncode(k)}=${percentEncode(params[k])}`)
    .join("&");

  const stringToSign = `GET&${percentEncode("/")}&${percentEncode(canonicalized)}`;
  const signature = crypto
    .createHmac("sha1", `${config.accessKeySecret}&`)
    .update(stringToSign)
    .digest("base64");

  const query = `${canonicalized}&Signature=${percentEncode(signature)}`;
  const url = `https://dysmsapi.aliyuncs.com/?${query}`;

  const res = await fetch(url, { method: "GET" });
  const bodyText = await res.text();
  let parsed: { Code?: string; Message?: string; BizId?: string } = {};
  try {
    parsed = JSON.parse(bodyText) as typeof parsed;
  } catch {
    throw new Error(`Aliyun SMS non-JSON response (HTTP ${res.status}): ${bodyText.slice(0, 200)}`);
  }

  if (!res.ok || parsed.Code !== "OK") {
    throw new Error(
      `Aliyun SMS failed: code=${parsed.Code || res.status} message=${parsed.Message || bodyText.slice(0, 200)}`,
    );
  }
}

function percentEncode(value: string): string {
  return encodeURIComponent(value)
    .replace(/\+/g, "%20")
    .replace(/\*/g, "%2A")
    .replace(/%7E/g, "~");
}
