import * as crypto from "crypto";
import * as admin from "firebase-admin";

const COLLECTION = "aliyunOtpSessions";
const OTP_TTL_MS = 10 * 60 * 1000;
const MAX_ATTEMPTS = 5;

type OtpSessionDoc = {
  phoneE164: string;
  codeHash: string;
  salt: string;
  createdAt: admin.firestore.Timestamp;
  expiresAt: admin.firestore.Timestamp;
  attempts: number;
  consumed: boolean;
  stub: boolean;
};

function db(): admin.firestore.Firestore {
  return admin.firestore();
}

export function generateOtpCode(): string {
  return String(crypto.randomInt(0, 1_000_000)).padStart(6, "0");
}

export function hashOtp(code: string, salt: string): string {
  return crypto.createHash("sha256").update(`${salt}:${code}`).digest("hex");
}

export async function createOtpSession(
  phoneE164: string,
  code: string,
  stub: boolean,
): Promise<string> {
  const sessionId = crypto.randomUUID();
  const salt = crypto.randomBytes(16).toString("hex");
  const now = Date.now();
  const doc: OtpSessionDoc = {
    phoneE164,
    codeHash: hashOtp(code, salt),
    salt,
    createdAt: admin.firestore.Timestamp.fromMillis(now),
    expiresAt: admin.firestore.Timestamp.fromMillis(now + OTP_TTL_MS),
    attempts: 0,
    consumed: false,
    stub,
  };
  await db().collection(COLLECTION).doc(sessionId).set(doc);
  return sessionId;
}

export type VerifyOutcome =
  | { ok: true; phoneE164: string; stub: boolean }
  | { ok: false; reason: "not_found" | "expired" | "consumed" | "rate_limited" | "invalid" };

export async function verifyOtpSession(
  sessionId: string,
  code: string,
): Promise<VerifyOutcome> {
  const ref = db().collection(COLLECTION).doc(sessionId);
  return db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return { ok: false, reason: "not_found" };
    const data = snap.data() as OtpSessionDoc;
    if (data.consumed) return { ok: false, reason: "consumed" };
    if (data.expiresAt.toMillis() < Date.now()) {
      return { ok: false, reason: "expired" };
    }
    if (data.attempts >= MAX_ATTEMPTS) {
      return { ok: false, reason: "rate_limited" };
    }
    const nextAttempts = data.attempts + 1;
    const match = hashOtp(code.trim(), data.salt) === data.codeHash;
    if (!match) {
      tx.update(ref, { attempts: nextAttempts });
      if (nextAttempts >= MAX_ATTEMPTS) {
        return { ok: false, reason: "rate_limited" };
      }
      return { ok: false, reason: "invalid" };
    }
    tx.update(ref, { consumed: true, attempts: nextAttempts });
    return { ok: true, phoneE164: data.phoneE164, stub: data.stub };
  });
}
