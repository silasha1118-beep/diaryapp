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

**Storage rules alone** (needed when entries load but media shows `storage/unauthorized`):

```bash
cd firebase
firebase deploy --only storage
```

**Windows PowerShell** (same, from the repo’s `firebase` folder):

```powershell
cd firebase
firebase deploy --only storage
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

## Troubleshooting — Open Vault shows only `internal`

That bare message is usually the **client SDK sanitizing** a failed callable (CORS / wrong region / missing function), not the HttpsError text from your password check.

### Immediate checks (Windows → `http://127.0.0.1:4173` or Hosting)

1. **Browser Network tab** → click **Open Vault as operator** → find `mintAdminVaultSession` (or the Gen2 Cloud Run URL containing that name).
   - Note **request URL region** (`asia-east1` vs `us-central1`), **HTTP status**, and response body / CORS errors.
   - Admin client is wired to **`asia-east1`** (`getFunctions(app, "asia-east1")`). If the function only exists in another region, you get `functions/internal` with message `internal`.
2. **Logs**
   ```bash
   firebase functions:log --only mintAdminVaultSession
   # or Console → Functions → mintAdminVaultSession → Logs
   ```
   No log line on click ⇒ the request never hit that function (region/CORS/deploy). A log with `ADMIN_VAULT_PASSWORD is not configured` / `Invalid operator password` ⇒ secret/password, not CORS.
3. **Secret bound to the function** (setting the secret alone is not enough until deploy binds it):
   ```bash
   firebase functions:secrets:access ADMIN_VAULT_PASSWORD
   firebase deploy --only functions:mintAdminVaultSession,functions:listAdminVaultAccounts
   ```
   In Console → Functions → `mintAdminVaultSession` → configuration, confirm **`ADMIN_VAULT_PASSWORD`** is listed under secrets. Then redeploy if you just set/rotated it.

### After pulling the region fix

Redeploy both vault callables so they live in **`asia-east1`** (explicit region + `globalOptions` loaded before `onCall`). Delete any stale **`us-central1`** copies of these two functions in Console if deploy left duplicates.

### Media fails with `storage/unauthorized`

Operator can list Firestore entries, but image / `.m4a` / `.mp4` attachments fail with:

`Firebase Storage: User does not have permission to access 'users/.../entries/...'. (storage/unauthorized)`

That means **Storage rules in the live project still only allow `request.auth.uid == userId`**. The operator signs in as `diaryvault-operator` with claim `vaultAdmin` (not the customer uid), so `getDownloadURL` is denied until Storage rules allow `isVaultAdmin()` reads.

**Fix — publish Storage rules** (Firestore rules alone are not enough):

```powershell
cd firebase
firebase deploy --only storage
```

Or Console → Storage → Rules → paste [`storage.rules`](../storage.rules) → **Publish**.

Confirm the published rules include `request.auth.token.vaultAdmin == true` on read for `users/{userId}/entries/...`. Writes stay owner-only. Reload the Vault admin page and expand attachments again.

### `iam.serviceAccounts.signBlob` denied (Operator session mint failed)

`createCustomToken` (used by `mintAdminVaultSession` and Tencent OTP verify) needs the Functions **runtime** service account to sign JWTs. On Cloud Functions **Gen2** that SA is usually the default Compute Engine account:

`{PROJECT_NUMBER}-compute@developer.gserviceaccount.com`

If IAM never granted **Service Account Token Creator** on that SA (to itself), mint fails with:

`Operator session mint failed: Permission 'iam.serviceAccounts.signBlob' denied on resource (or it may not exist).`

**Find project number** (not the project id `diaryappsilas`):

- Google Cloud Console → select project **diaryappsilas** → Dashboard / Home → **Project number**, or
- Firebase Console → Project settings → **General** → **Project number**.

**Confirm which SA the function uses** (optional but safest):

- Cloud Console → **Cloud Functions** → `mintAdminVaultSession` (`asia-east1`) → configuration / details → **Service account** (or Cloud Run service of the same name → Security → Service account).

**Fix in Console (no gcloud — Windows-friendly):**

1. Open [Google Cloud Console → IAM & Admin → IAM](https://console.cloud.google.com/iam-admin/iam?project=diaryappsilas) for project **diaryappsilas**.
2. Find principal  
   `{PROJECT_NUMBER}-compute@developer.gserviceaccount.com`  
   (or the SA shown on the function if different). If it is missing from the list, click **Grant access** and use that email as the new principal.
3. Edit that principal → **Add another role** → **Service Account Token Creator** (`roles/iam.serviceAccountTokenCreator`).
4. Save. Wait ~1–2 minutes, then retry **Open Vault as operator** (no redeploy required).

**Same fix with gcloud (optional):**

```bash
# PROJECT_NUMBER from: gcloud projects describe diaryappsilas --format="value(projectNumber)"
# RUNTIME_SA is usually: ${PROJECT_NUMBER}-compute@developer.gserviceaccount.com

gcloud iam service-accounts add-iam-policy-binding RUNTIME_SA \
  --member="serviceAccount:RUNTIME_SA" \
  --role="roles/iam.serviceAccountTokenCreator" \
  --project=diaryappsilas
```

Grant is on the service account **resource** (the SA signing for itself). Project-level IAM with the same role also works if Console only offers project IAM.

## Raw Console (no viewer)

1. Firestore → `users` → `{Auth UID}` → `entries` (sort by `createdAt`).
2. Storage → `users/{uid}/entries/{entryId}/…`.
