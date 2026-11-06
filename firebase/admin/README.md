# Vault-like cloud admin viewer

Browse diary entries the way Vault does: **title / preview**, **time**, **Active vs Deleted** together, plus **playable media** (image lightbox, voice `.m4a`, video `.mp4`) via Storage download URLs.

**Operator (OTP-free):** password → Cloud Function custom token with claim `vaultAdmin` → pick any Auth account.  
**Alternate:** customer phone OTP (one account only). Soft-deleted posts stay forever.

## Silas — enable once (Console)

### 1. Web app config (if not already)

1. Firebase Console → project **`diaryappsilas`** → Project settings → **Add app → Web**.
2. Nickname e.g. `Vault Admin`. Copy the `firebaseConfig` values into gitignored `firebase-config.js` (from `firebase-config.example.js`).
3. Auth → Settings → **Authorized domains** → keep `localhost` (+ Hosting domain if you deploy).

### 2. Operator password secret (required for OTP-free)

From `firebase/` (or repo root with the Firebase CLI logged into **diaryappsilas**):

```bash
firebase functions:secrets:set ADMIN_VAULT_PASSWORD
# paste a strong password when prompted — never commit it
```

Emulator / local Functions only: copy `functions/.env.example` → `functions/.env` and set `ADMIN_VAULT_PASSWORD=…` (gitignored).

### 3. Deploy Functions + rules

```bash
cd firebase
firebase deploy --only functions:mintAdminVaultSession,functions:listAdminVaultAccounts,firestore:rules,storage:rules
# or full: firebase deploy --only functions,firestore:rules,storage:rules
```

What this enables:

| Piece | Role |
| --- | --- |
| `mintAdminVaultSession` | Checks password against secret; creates Auth user `diaryvault-operator` with claim `vaultAdmin: true`; returns a custom token (~1h ID token after client exchange). |
| `listAdminVaultAccounts` | Operator-only: lists Auth users (phones/UIDs) for the account picker. |
| Firestore rules | `isVaultAdmin()` may **read** any `users/{uid}` + `entries`. Customers still write only their own tree. |
| Storage rules | `isVaultAdmin()` may **read** any `users/{uid}/entries/...` object so `getDownloadURL` works for media. |

No service-account JSON in the browser or git — the Admin SDK runs only inside Cloud Functions (default compute SA).

### 4. Open the viewer

**Local**

```bash
cp firebase-config.example.js firebase-config.js   # once; fill Web config
# from firebase/
npx --yes serve admin -p 4173
```

Open [http://127.0.0.1:4173](http://127.0.0.1:4173) → **Operator password** → **Open Vault as operator** → pick an account → All / Active / Deleted. Expand attachments for thumbnails / audio / video.

**Hosting**

```bash
firebase deploy --only hosting
```

Open `https://diaryappsilas.web.app` (or the Hosting URL Console shows).

### 5. Optional: customer phone OTP

Expand **Alternate: customer phone OTP** on the signed-out panel. Same as before — only that user’s `users/{uid}/entries`.

## Media

Attachments use paths already on the entry (`attachments[].remotePath`, typically `users/{uid}/entries/{entryId}/{fileName}`). The viewer calls `getDownloadURL` (not path text alone):

- **image** — thumbnail + click lightbox  
- **voice** — `<audio controls>` (`.m4a` / `audio/mp4`)  
- **video** — `<video controls>` (`.mp4`)

## Raw Console (no viewer)

1. Firestore → `users` → `{Auth UID}` → `entries` (sort by `createdAt`).
2. Storage → `users/{uid}/entries/{entryId}/…`.
