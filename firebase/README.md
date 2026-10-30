# Firebase / Firestore (DiaryVault)

Phase 1: **text diary sync** for signed-in users (Firebase Auth phone / Apple).  
On-device JSON remains the offline source of truth. Multimedia upload is **not** in this phase (DA-15).

## Enable Firestore (Silas — Console)

1. Open [Firebase Console](https://console.firebase.google.com/) → project **`diaryappsilas`**.
2. **Build → Firestore Database → Create database**.
3. Start in **production mode** (rules below will replace the default deny-all after you paste them).
4. **Location (data residency narrative: Taiwan):** choose the closest available region — prefer **`asia-east1` (Taiwan)** if offered, otherwise **`asia-east2` (Hong Kong)** or **`asia-northeast1` (Tokyo)**. Location is permanent for the database.
5. Open **Rules** → replace with the contents of [`firestore.rules`](firestore.rules) in this folder → **Publish**.
6. No composite indexes are required for phase 1 (simple collection reads under `users/{uid}/entries`).
7. Rebuild the iOS app with `GoogleService-Info.plist` present (same as Phone Auth). SPM product **FirebaseFirestore** is already linked in the Xcode project.

## Schema

```
users/{uid}
  ownerUid: string
  updatedAt: timestamp
  schemaVersion: number   // 1
  hasTextEntries: bool

users/{uid}/entries/{entryId}   // entryId = DiaryEntry UUID string
  id: string
  title: string
  body: string
  moodTag: string | null
  createdAt: timestamp
  updatedAt: timestamp
  ownerUid: string              // enables Admin SDK collection-group queries later
  schemaVersion: number         // 1
  contentType: string           // "text" (media later)
```

## Client behavior

| Auth state | Behavior |
|------------|----------|
| Signed out / DEBUG local session / stub OTP | Local vault only — no Firestore writes |
| Firebase Auth phone or Apple | Local save first; upsert/delete sync when online; pull+merge (LWW by `updatedAt`) on launch / after sign-in |

## Admin read (later — do not build UI now)

- Security rules keep clients to own `uid` paths.
- A future admin tool should use the **Firebase Admin SDK** (or Console) which bypasses rules.
- Query `users` for profiles; `collectionGroup('entries')` + `ownerUid` for cross-user content.
