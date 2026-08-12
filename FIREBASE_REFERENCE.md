# 🔥 RootNet Firebase Reference

> **Last updated:** July 22, 2026
> **Purpose:** Guide for future assistants to understand and work with Firebase alongside Supabase

---

## 1. 📋 Project Overview

Firebase runs **alongside** Supabase — not replacing it. Each backend handles different concerns:

| Service | Supabase (Primary) | Firebase (Secondary) |
|---------|-------------------|---------------------|
| **Auth** | Email/password, Google Sign-In, session persistence | Email/password, Google Sign-In (future: additional providers) |
| **Database** | PostgreSQL (`servers`, `app_config`, `device_tokens`) | Firestore (future: user preferences, analytics) |
| **Push Notifications** | Device token storage | **FCM** — actual push delivery |
| **Server Logic** | Cloudflare Worker API | N/A (Worker handles all backend) |

### Firebase Project Details

| Property | Value |
|----------|-------|
| **Project ID** | `rootnet-43714` |
| **Project Number** | `465028330311` |
| **Storage Bucket** | `rootnet-43714.firebasestorage.app` |
| **Android Package** | `com.chobgroup.rootnet` |
| **Android App ID** | `1:465028330311:android:a9c263d332431060031262` |

---

## 2. 📁 File Structure

```
woodvless/
├── android/
│   └── app/
│       └── google-services.json          ← Firebase Android config (ALREADY PLACED)
├── lib/
│   └── services/
│       ├── firebase_service.dart         ← Firebase Auth + Firestore service
│       └── push_service.dart             ← FCM push notification service
├── pubspec.yaml                          ← Firebase packages added
└── FIREBASE_REFERENCE.md                 ← This file
```

### `google-services.json`
- **Location:** `android/app/google-services.json` (already in place)
- **Contains:** Firebase project info, API key, Android app ID
- **⚠️ Do NOT commit this to public repos** — it's in `.gitignore`

---

## 3. 🔥 Firebase Packages in Use

| Package | Version | Purpose |
|---------|---------|---------|
| `firebase_core` | `^2.24.2` | Required by all Firebase services |
| `firebase_auth` | `^4.15.3` | Email/password + Google Sign-In |
| `cloud_firestore` | `^4.13.6` | Document database |
| `firebase_messaging` | `^14.7.10` | FCM push notifications |

---

## 4. 🚀 Initialization Order (in `main.dart`)

```dart
// 1. WidgetsFlutterBinding (required for async main)
WidgetsFlutterBinding.ensureInitialized();

// 2. Google Mobile Ads
await MobileAds.instance.initialize();

// 3. Supabase (PRIMARY backend)
await AuthService.instance.initialize(url: ..., publishableKey: ...);

// 4. Firebase Core + Push Notifications (FCM)
await PushService.instance.init();

// 5. Firebase Auth + Firestore
await FirebaseService.instance.init();
```

**Note:** `Firebase.initializeApp()` is called inside `PushService.init()`. The `FirebaseService` also calls it, but it's idempotent (safe to call multiple times).

---

## 5. 🔐 Firebase Auth Usage

The `FirebaseService` singleton provides:

### Email/Password

```dart
final fb = FirebaseService.instance;

// Sign up
await fb.signUp(email: 'user@example.com', password: 'password123');

// Sign in
await fb.signInWithPassword(email: 'user@example.com', password: 'password123');

// Password reset
await fb.sendPasswordResetEmail(email: 'user@example.com');

// Sign out
await fb.signOut();
```

### Google Sign-In

```dart
final fb = FirebaseService.instance;

// Sign in with Google (mobile uses native flow, web uses popup)
final userCredential = await fb.signInWithGoogle();
if (userCredential != null) {
  // User signed in
}
```

### Auth State

```dart
// Current user
FirebaseService.instance.currentUser; // User? (null if not logged in)

// Auth state stream
FirebaseService.instance.onAuthChange; // Stream<User?>
```

---

## 6. 🔥 Firestore Usage

```dart
final fb = FirebaseService.instance;

// Get a collection reference
final usersRef = fb.collection('users');
final serversRef = fb.collection('servers');

// Get a document reference
final userDoc = fb.document('users/user123');

// Read data
final snapshot = await usersRef.doc('user123').get();
if (snapshot.exists) {
  final data = snapshot.data() as Map<String, dynamic>;
}

// Write data
await usersRef.doc('user123').set({
  'email': 'user@example.com',
  'createdAt': FieldValue.serverTimestamp(),
});
```

---

## 7. 📲 FCM Push Notifications

Push notifications flow:
```
Flutter App ──→ Worker API (/register-device) ──→ Supabase DB
    ↑                                                    │
    └────────── FCM Push (from Worker) ──────────────────┘
```

- **Client:** `push_service.dart` handles FCM token registration + incoming messages
- **Server:** Worker endpoints `/register-device`, `/unregister-device`, `/send-notification`
- **Display:** Uses existing `flutter_local_notifications` to show push notifications

---

## 8. 🔧 Required Worker Secrets

For push notifications to work, set these Worker secrets:

```bash
npx wrangler secret put FCM_SERVER_KEY    # From Firebase Console → Cloud Messaging
npx wrangler secret put ADMIN_KEY          # Any secure random string for admin endpoints
```

---

## 9. 🛠️ Firebase CLI Commands

```bash
# Login to Firebase
npx -y firebase-tools@latest login

# Set active project
npx -y firebase-tools@latest use rootnet-43714

# Deploy auth config
npx -y firebase-tools@latest deploy --only auth

# Enable auth providers (alternative to console)
npx -y firebase-tools@latest firebase init auth
```

---

## 10. 🧪 Testing

- **Firebase Emulator Suite:** `npx -y firebase-tools@latest emulators:start`
- **Auth testing:** Use the Firebase Console Authentication tab
- **Firestore testing:** Use the Firebase Console Firestore tab

---

## 11. 📚 Project Skill

The project has its own skill file that AI assistants should load FIRST:

- **Skill**: `rootnet-vpn` (in `.agents/skills/rootnet-vpn/SKILL.md`)
- **Load with**: `skill(name: "rootnet-vpn")`
- **Covers**: Complete project architecture, core rules, security principles, and what to avoid

Generic Firebase skills have been removed — they suggested wrong auth/database patterns.
