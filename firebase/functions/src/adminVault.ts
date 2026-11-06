/**
 * Operator Vault admin session — password gate + short-lived custom token.
 *
 * Secret (never commit):
 *   ADMIN_VAULT_PASSWORD  — Firebase secret or functions/.env (emulator)
 *
 * Console: firebase functions:secrets:set ADMIN_VAULT_PASSWORD
 * Then deploy functions and publish Firestore/Storage rules that honor vaultAdmin.
 */

import * as admin from "firebase-admin";
import { createHash, timingSafeEqual } from "crypto";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";

/** Stable Auth UID for the operator session (not a customer phone user). */
export const VAULT_ADMIN_UID = "diaryvault-operator";

const adminVaultPassword = defineSecret("ADMIN_VAULT_PASSWORD");

function readExpectedPassword(): string {
  // Secret from Cloud Functions runtime; .env for emulator / local.
  try {
    const fromSecret = adminVaultPassword.value();
    if (typeof fromSecret === "string" && fromSecret.length > 0) {
      return fromSecret;
    }
  } catch {
    // Secret not bound (emulator without secrets) — fall through to env.
  }
  const fromEnv = process.env.ADMIN_VAULT_PASSWORD;
  if (typeof fromEnv === "string" && fromEnv.length > 0) {
    return fromEnv;
  }
  return "";
}

function passwordsMatch(provided: string, expected: string): boolean {
  if (!provided || !expected) return false;
  const a = createHash("sha256").update(provided, "utf8").digest();
  const b = createHash("sha256").update(expected, "utf8").digest();
  return timingSafeEqual(a, b);
}

async function ensureVaultAdminUser(): Promise<string> {
  const auth = admin.auth();
  try {
    await auth.getUser(VAULT_ADMIN_UID);
  } catch (err: unknown) {
    const code =
      err && typeof err === "object" && "code" in err
        ? String((err as { code: unknown }).code)
        : "";
    if (code !== "auth/user-not-found") {
      console.error("getUser vault admin failed", err);
      throw new HttpsError("internal", "Failed to resolve operator Auth user");
    }
    await auth.createUser({
      uid: VAULT_ADMIN_UID,
      displayName: "DiaryVault Operator",
      disabled: false,
    });
  }
  await auth.setCustomUserClaims(VAULT_ADMIN_UID, { vaultAdmin: true });
  return VAULT_ADMIN_UID;
}

function assertVaultAdmin(auth: { token?: Record<string, unknown> } | undefined): void {
  if (!auth?.token || auth.token.vaultAdmin !== true) {
    throw new HttpsError("permission-denied", "Operator vaultAdmin session required");
  }
}

/**
 * Password → short-lived Firebase custom token with claim `vaultAdmin: true`.
 * ID token after exchange lasts ~1 hour; call again to renew.
 *
 * Request: `{ password: string }`
 * Response: `{ customToken, uid, expiresInHintSec }`
 */
export const mintAdminVaultSession = onCall(
  {
    enforceAppCheck: false,
    secrets: [adminVaultPassword],
  },
  async (request) => {
    const password =
      typeof request.data?.password === "string" ? request.data.password : "";
    const expected = readExpectedPassword();
    if (!expected) {
      throw new HttpsError(
        "failed-precondition",
        "ADMIN_VAULT_PASSWORD is not configured. Set the Firebase secret (or functions/.env for emulator).",
      );
    }
    if (!passwordsMatch(password, expected)) {
      throw new HttpsError("permission-denied", "Invalid operator password");
    }

    const uid = await ensureVaultAdminUser();
    const customToken = await admin.auth().createCustomToken(uid, {
      vaultAdmin: true,
    });

    return {
      customToken,
      uid,
      expiresInHintSec: 3600,
    };
  },
);

/**
 * List Auth accounts for the operator picker (phones + UIDs).
 * Requires a live vaultAdmin ID token (after mintAdminVaultSession + signInWithCustomToken).
 *
 * Response: `{ accounts: [{ uid, phoneNumber?, displayName?, disabled }] }`
 */
export const listAdminVaultAccounts = onCall(
  {
    enforceAppCheck: false,
  },
  async (request) => {
    assertVaultAdmin(request.auth);
    const auth = admin.auth();
    const accounts: Array<{
      uid: string;
      phoneNumber: string | null;
      displayName: string | null;
      disabled: boolean;
    }> = [];

    let pageToken: string | undefined;
    do {
      const page = await auth.listUsers(1000, pageToken);
      for (const user of page.users) {
        if (user.uid === VAULT_ADMIN_UID) continue;
        accounts.push({
          uid: user.uid,
          phoneNumber: user.phoneNumber ?? null,
          displayName: user.displayName ?? null,
          disabled: user.disabled,
        });
      }
      pageToken = page.pageToken;
    } while (pageToken);

    accounts.sort((a, b) => {
      const ap = a.phoneNumber || a.uid;
      const bp = b.phoneNumber || b.uid;
      return ap.localeCompare(bp);
    });

    return { accounts };
  },
);
