# Firebase / Firestore (DiaryVault)

Phase 1: **text diary sync** for signed-in users (Firebase Auth phone / Apple).  
On-device JSON remains the offline source of truth. Multimedia upload is **not** in this phase (DA-15).

## Enable Firestore (Silas — Console)

1. Open [Firebase Console](https://console.firebase.google.com/) → project that matches the app’s live **Project ID**.
   - Expected from the repo example / `DIARY_FIREBASE_PROJECT_ID`: **`diaryappsilas`**.
   - **Source of truth** is `GoogleService-Info.plist` → `PROJECT_ID` (also shown in Settings → Cloud sync → **Firebase project** after this fix). If Console shows a different project name, you are looking at the wrong place — Auth + Firestore both use the plist project.
2. **Build → Firestore Database** → database **`(default)`** (the app never writes a named database).
3. Start in **production mode** (rules below will replace the default deny-all after you paste them).
4. **Location (data residency narrative: Taiwan):** choose the closest available region — prefer **`asia-east1` (Taiwan)** if offered, otherwise **`asia-east2` (Hong Kong)** or **`asia-northeast1` (Tokyo)**. Location is permanent for the database.
5. Open **Rules** → replace with the contents of [`firestore.rules`](firestore.rules) in this folder → **Publish**. Deny-all / unpublished rules make writes fail; the app now surfaces permission-denied instead of “synced”.
6. No composite indexes are required for phase 1 (simple collection reads under `users/{uid}/entries`).
7. Rebuild the iOS app with `GoogleService-Info.plist` present (same as Phone Auth). SPM product **FirebaseFirestore** is already linked in the Xcode project.
8. After **Sync now**, open Data → `users` → `{Auth UID from Settings}` → `entries`. Settings also shows **Entries on server**; if that is `0` while you have local diaries, sync did not land (check project ID + rules).

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

users/{uid}/entries/{entryId}   // entryId = DiaryEntry UUID string (stable — never phone+datetime)
  id: string                    // same UUID as document ID
  title: string
  body: string                  // full text
  bodyPreview: string           // short Console excerpt
  label: string                 // e.g. "2026-10-30 18:22 · Morning notes" for Console browsing
  moodTag: string | null
  createdAt: timestamp
  updatedAt: timestamp
  ownerUid: string              // enables Admin SDK collection-group queries later
  schemaVersion: number         // 1
  contentType: string           // "text" (media later)
```

**Console tip:** Data → `users` → open a doc, or filter by `phoneNumber`. Document ID remains the Auth UID. Identity fields are written on sign-in and each cloud sync (user doc only — not mirrored onto entries).

**Entry order / Console browsing:** Document IDs stay entry UUIDs (required for local sync, privacy, and phone number changes — do **not** rename to phone+datetime). Console’s default list is UUID order. Open `users/{uid}/entries`, sort by `createdAt`, and scan the `label` field (`yyyy-MM-dd HH:mm · title`). Phone lives on the parent `users/{uid}.phoneNumber`, not in the entry path. The app Vault list always sorts newest-`createdAt`-first.

## Client behavior

| Auth state | Behavior |
|------------|----------|
| Signed out / DEBUG local session / stub OTP | Local vault only — no Firestore writes |
| Firebase Auth phone or Apple | Local save first; upsert/delete sync when online; pull+merge (LWW by `updatedAt`) on launch / after Auth restore / after sign-in |

**Important:** Sync gates on live `Auth.auth().currentUser` (not UserDefaults alone). Settings → **Cloud sync** shows whether Firebase Auth is live and surfaces sync errors (e.g. rules not Published). If status is “Local session only”, sign out and complete real Firebase SMS (banner must say Firebase).

**Rules:** Creating the database in production mode starts deny-all — paste [`firestore.rules`](firestore.rules) and **Publish**, or every write fails.

## Admin read (later — do not build UI now)

- Security rules keep clients to own `uid` paths.
- A future admin tool should use the **Firebase Admin SDK** (or Console) which bypasses rules.
- Query `users` for profiles; `collectionGroup('entries')` + `ownerUid` for cross-user content.
