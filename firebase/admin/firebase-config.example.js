/**
 * Copy to firebase-config.js (gitignored) and fill from Firebase Console.
 *
 * Console → Project settings → Your apps → Add app → Web (</>),
 * register nickname "Vault Admin", then paste the firebaseConfig object values.
 *
 * Project must be diaryappsilas (same as iOS + Android).
 * Authorized domains must include localhost (for local serve) and your Hosting domain.
 *
 * Operator OTP-free login also needs Cloud Functions secret ADMIN_VAULT_PASSWORD
 * and published Firestore/Storage rules with vaultAdmin — see README.md.
 */
window.DIARY_FIREBASE_CONFIG = {
  apiKey: "REPLACE_ME",
  authDomain: "diaryappsilas.firebaseapp.com",
  projectId: "diaryappsilas",
  storageBucket: "diaryappsilas.appspot.com",
  messagingSenderId: "REPLACE_ME",
  appId: "REPLACE_ME",
};
