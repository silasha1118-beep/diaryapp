# Firebase / Firestore (DiaryVault)

**Text + multimedia diary sync** for signed-in users (Firebase Auth phone / Apple / **Tencent +86 custom token**).  
On-device JSON remains the offline source of truth. Media bytes go to **Firebase Storage**; entry docs keep an `attachments` array. Free/Plus/Pro vault **upload counts** include text + media in one bucket.

**+86 SMS:** Tencent Cloud SMS via Cloud Functions — see [`TENCENT_SMS.md`](TENCENT_SMS.md). Other dial codes use Firebase Phone Auth on the client.

## Enable Firestore (Silas — Console)

1. Open [Firebase Console](https://console.firebase.google.com/) → project that matches the app’s live **Project ID**.
   - Expected from the repo example / `DIARY_FIREBASE_PROJECT_ID`: **`diaryappsilas`**.
   - **Source of truth** is `GoogleService-Info.plist` → `PROJECT_ID` (also shown in Settings → Cloud sync → **Firebase project** after this fix). If Console shows a different project name, you are looking at the wrong place — Auth + Firestore both use the plist project.
2. **Build → Firestore Database** → database **`(default)`** (the app never writes a named database).
3. Start in **production mode** (rules below will replace the default deny-all after you paste them).
4. **Location (data residency narrative: Taiwan):** choose the closest available region — prefer **`asia-east1` (Taiwan)** if offered, otherwise **`asia-east2` (Hong Kong)** or **`asia-northeast1` (Tokyo)**. Location is permanent for the database.
5. Open **Rules** → replace with the contents of [`firestore.rules`](firestore.rules) in this folder → **Publish**. Deny-all / unpublished rules make writes fail; the app now surfaces permission-denied instead of “synced”.
6. No composite indexes are required for phase 1 (simple collection reads under `users/{uid}/entries`).
7. Rebuild the iOS app with `GoogleService-Info.plist` present (same as Phone Auth). SPM products **FirebaseFirestore** + **FirebaseStorage** are linked in the Xcode project. For Android, place gitignored `android/app/google-services.json` (package `com.diaryvault.app`) and rebuild — Auth + Firestore deps are in `android/app/build.gradle.kts`.
8. **Build → Storage** → get started if needed. Prefer the same region narrative as Firestore (`asia-east1` when available). Paste [`storage.rules`](storage.rules) → **Publish**.
9. After **Sync now**, open Data → `users` → `{Auth UID from Settings}` → `entries`. Media files appear under Storage → `users/{uid}/entries/{entryId}/`. Settings also shows **Entries on server**; if that is `0` while you have local diaries, sync did not land (check project ID + rules).

## Schema

```
users/{uid}                     // uid = Firebase Auth UID (path key — do not use phone/Apple id)
  ownerUid: string              // same as uid
  updatedAt: timestamp
  schemaVersion: number         // 1
  hasTextEntries: bool
  phoneNumber?: string          // E.164 from Firebase Auth (Console filter / search)
  appleUserId?: string          // Apple provider UID from providerData
  displayName?: string
  authProviders?: string[]      // e.g. ["phone"], ["apple"], or both
  vaultUploadLifetimeCount?: number  // Free vault uploads lifetime (text + media one bucket)
  mirrorLifetimeCount?: number       // Free Mirror chats lifetime

users/{uid}/entries/{entryId}   // entryId = DiaryEntry UUID string (stable — never phone+datetime)
  id: string                    // same UUID as document ID
  title: string
  body: string                  // full text
  bodyPreview: string           // short Console / admin excerpt
  label: string                 // e.g. "2026-10-30 18:22 · Morning notes" for scanning
  status: string                // "active" | "deleted" — Console twin of `deleted`
  hasMedia: bool                // true when attachments[] is non-empty
  moodTag: string | null
  createdAt: timestamp
  updatedAt: timestamp
  deleted: bool                 // soft-delete; Vault hides when true (default false)
  deletedAt: timestamp | null   // set when the user deletes; docs are never hard-removed
  ownerUid: string              // enables Admin SDK collection-group queries later
  schemaVersion: number         // 1
  contentType: string           // "text" | "multimedia"
  sourcePlatform?: string       // "ios" | "android" — which client first wrote the entry
  attachments?: array           // [{ id, kind, localFileName, remotePath?, mimeType, durationSeconds?, byteSize?, createdAt }]
```

**Storage path:** `users/{uid}/entries/{entryId}/{localFileName}` (voice m4a / image jpg / video mp4). Soft-delete keeps Storage objects with the archived entry. **Multimedia metadata always lives on the same entry document** (`attachments` / `hasMedia` / `contentType`) — never as orphaned docs elsewhere.

**Soft-delete / retention:** User delete sets `deleted: true` + `deletedAt` + `status: "deleted"` (local JSON + Firestore upsert). Clients never call Firestore `delete()` on entries; rules deny hard delete. Docs remain under `users/{uid}/entries` for admin/future. Vault list, Mirror context, and export omit soft-deleted rows. After changing rules, **Publish** again in Console.

**Console tip:** Data → `users` → open a doc, or filter by `phoneNumber`. Document ID remains the Auth UID. Identity fields are written on sign-in and each cloud sync (user doc only — not mirrored onto entries).

**Entry order / Console browsing:** Document IDs stay entry UUIDs (required for local sync, privacy, and phone number changes — do **not** rename to phone+datetime). Console’s default list is UUID order. Open `users/{uid}/entries`, sort by `createdAt`, and scan **`label`**, **`status`**, **`hasMedia`**, **`sourcePlatform`**. Phone lives on the parent `users/{uid}.phoneNumber`, not in the entry path. The app Vault list always sorts newest-`createdAt`-first.

**Vault-like admin viewer (preferred over raw UUID lists):** see [`admin/README.md`](admin/README.md). Sign in with the same phone as the app to list Active + soft-deleted posts together with media badges.

## Client behavior

| Auth state | Behavior |
|------------|----------|
| Signed out / DEBUG local session / stub OTP | Local vault only — no Firestore writes |
| Firebase Auth phone or Apple | Local save first; upsert (incl. soft-delete flags) when online; pull+merge (LWW by `updatedAt`) on launch / after Auth restore / after sign-in; Vault UI excludes `deleted == true` |

**Important:** Sync gates on live `Auth.auth().currentUser` (not UserDefaults alone). Settings → **Cloud sync** shows whether Firebase Auth is live and surfaces sync errors (e.g. rules not Published). If status is “Local session only”, sign out and complete real Firebase SMS (banner must say Firebase).

**Rules:** Creating the database in production mode starts deny-all — paste [`firestore.rules`](firestore.rules) and **Publish**, or every write fails.

## Admin / Vault-like viewer

- **In-repo viewer:** [`admin/`](admin/) — **operator password** (OTP-free, all Auth accounts) via Cloud Functions `mintAdminVaultSession` + claim `vaultAdmin`, or alternate customer phone OTP (one account). Media: Storage `getDownloadURL` for image / voice / video on each entry. Soft-delete filters: All / Active / Deleted. Setup: [`admin/README.md`](admin/README.md).
- **Rules:** Firestore + Storage allow `request.auth.token.vaultAdmin == true` to **read** any user’s entries and media (writes stay owner-only). If Admin Vault lists entries but media fails with `storage/unauthorized`, publish Storage rules: from `firebase/`, `firebase deploy --only storage`.
- **Console:** sort `entries` by `createdAt`; scan `label` / `status` / `hasMedia` / `sourcePlatform`.
