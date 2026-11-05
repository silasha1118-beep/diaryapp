/**
 * Minimal Alibaba Cloud Dysmsapi SendSms client (RPC signature v1.0).
 * AccessKey stays in Functions secrets / env — never shipped to clients.
 */

import * as crypto from "crypto";

export type AliyunSmsConfig = {
  accessKeyId: string;
  accessKeySecret: string;
  signName: string;
  templateCode: string;
  /** Template variable name for the OTP, usually `code`. */
  templateParamKey: string;
};

export function readAliyunSmsConfig(): AliyunSmsConfig | null {
  const accessKeyId = (process.env.ALIYUN_SMS_ACCESS_KEY_ID || "").trim();
  const accessKeySecret = (process.env.ALIYUN_SMS_ACCESS_KEY_SECRET || "").trim();
  const signName = (process.env.ALIYUN_SMS_SIGN_NAME || "").trim();
  const templateCode = (process.env.ALIYUN_SMS_TEMPLATE_CODE || "").trim();
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
