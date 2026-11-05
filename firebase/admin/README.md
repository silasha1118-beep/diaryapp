# Vault-like cloud admin viewer

Browse one account’s diary entries the way Vault does: **title / preview**, **time**, **Active vs Deleted** together, plus **media** and **sourcePlatform** — without scanning raw Firestore UUID document IDs.

Soft-deleted posts stay forever (`deleted` / `deletedAt` / `status: "deleted"`). Media metadata lives on the **same entry document** (`attachments`, `hasMedia`, `contentType`).

## Open it (Silas)

### Option A — local (fastest)

1. Firebase Console → project **`diaryappsilas`** → Project settings → **Add app → Web**.
2. Nickname e.g. `Vault Admin`. Copy the `firebaseConfig` values.
3. Auth → Settings → **Authorized domains** → keep `localhost` (and Hosting domain if you deploy).
4. From this folder:

```bash
cp firebase-config.example.js firebase-config.js
# edit firebase-config.js with the Web app config
cd ..   # firebase/
npx --yes serve admin -p 4173
```

5. Open [http://127.0.0.1:4173](http://127.0.0.1:4173) → sign in with the **same phone** as the app → OTP → Vault list.
6. Use **All / Active / Deleted** filters. Expand **Attachments on this entry** when `hasMedia` is true.

### Option B — Firebase Hosting

```bash
# from firebase/ after firebase-config.js exists
firebase deploy --only hosting
```

Open `https://diaryappsilas.web.app` (or the Hosting URL Console shows). Sign in with phone OTP as above.

### Option C — raw Console (no viewer)

1. Console → Firestore → `users` → `{Auth UID}` → `entries`.
2. Sort by **`createdAt`** (not document ID).
3. Scan fields: **`label`**, **`status`** (`active`|`deleted`), **`hasMedia`**, **`sourcePlatform`**, **`bodyPreview`**.
4. Open a doc for full `body` + `attachments[]`. Storage bytes: `users/{uid}/entries/{entryId}/…`.

UID is in the app under Settings → Cloud sync (or the viewer header after sign-in).
