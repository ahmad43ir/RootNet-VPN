# RootNet VPN — TODO & Handoff for Next AI

> 📅 Last updated: August 5, 2026
> 🧠 Prepared by: Previous AI session — read this FIRST before making any changes

---

## 🔴 FIXED — Critical Bug History (Read for Context)

### 1. ✅ VPN Permission Dialog — NOW WORKING
**Original symptom:** Tapping connect showed nothing — VPN permission dialog never appeared.
**Root cause:** `flutter_vless`'s `startVless(proxyOnly: false)` was supposed to trigger Android's VPN permission dialog internally, but it wasn't working reliably.
**Fix applied:** Added explicit `await engine!.requestPermission()` call BEFORE every `startVless()` call:
- `lib/services/connectors/base_connector.dart` — added before config building (fallback path for non-VLESS protocols)
- `lib/services/connectors/vless_connector.dart` — added before `FlutterVless.parse()` (main VLESS path)
- Permission denied now throws a clear `StateError`: "VPN permission was denied. Please grant VPN access in system settings."

### 2. ✅ VPN Immediate Disconnect (1 sec after connecting) — NOW WORKING
**Original symptom:** VPN would connect ("Session started — 30:00 remaining") but immediately disconnect.
**Root cause:** Custom Xray JSON building was broken — `ConfigNormalizer` produced invalid `streamSettings` that caused Xray-core to crash after TUN establishment.
**Fix applied:** Restored the original working approach in `VlessConnector.connect()` — using `FlutterVless.parse(rawConfig)` + `getFullConfiguration()` instead of custom JSON:
- `lib/services/connectors/vless_connector.dart` — overrides `connect()` to use flutter_vless's built-in config generator
- Also added protected getters (`engine`, `savedProviderBundleIdentifier`, `savedGroupIdentifier`) to `BaseVpnConnector` for subclass access

### 3. ✅ VLESS Reality Protocol Support — NOW WORKING
**Original symptom:** Rose (Reality) and Orchid (Reality xhttp) servers couldn't connect.
**Root cause:** `ConfigNormalizer.buildStreamSettings()` didn't handle `security=reality` or `mode=packet-up` parameters.
**Fix applied:** Added `realitySettings` block (serverName, fingerprint, publicKey, shortId, spiderX) and optional `mode` field for xhttpSettings.
- `lib/services/config_normalizer.dart` — buildStreamSettings() now handles `tls`, `reality`, and `none` security modes
- `lib/models/unified_config.dart` — fromVlessUri() now captures `pbk`, `sid`, `flow`, `mode` params

### 2. Google Sign-In — redirect_uri_mismatch
**Status:** ✅ FIXED (per user — update TODO next time if anything regresses)
**Symptom:** Error 400: `redirect_uri_mismatch` with `flowName=GeneralAOAuthFlow`
**What was done:**
- ✅ Created new **Web application** OAuth client (NOT Desktop app):
  - Client ID: `465028330311-j3nlh3ustjf92u3ndaa37e2gookphe4g.apps.googleusercontent.com`
  - Client Secret: `REDACTED_GOOGLE_CLIENT_SECRET`
  - JSON file saved to `credentials/` directory (gitignored)
- ✅ Added `https://bprkazfxqmanrybiexnh.supabase.co/auth/v1/callback` as Authorized redirect URI
- ✅ Supabase Auth provider updated with new Client ID + Secret
- ✅ Old Desktop app client deleted: `465028330311-kphpupp30l4uehjjirilgggti7514l90.apps.googleusercontent.com`

**Next steps:**
1. Run `flutter run` and test Google Sign-In end-to-end
2. If it regresses, debug by checking the exact redirect_uri Google sees in Supabase server logs

---

## 🟡 HIGH PRIORITY

### 3. Production Readiness Checklist
**Status:** ✅ IN PROGRESS (docs exist in PUBLISH_PLAN.md)
**Covered so far:**
- ✅ App signing & release build config (`upload-keystore.jks`, `key.properties`)
- ✅ Crash reporting (Firebase Crashlytics — `crashlytics_service.dart`)
- ✅ Google Play privacy policy link on auth screens
- ⬜ App store listing (description, screenshots, content rating, data-safety form)
- ⬜ Real Unity Ads Game ID (`lib/services/ads_config.dart` — currently placeholder)
- ⬜ Play Console setup + VPN declaration + special approval
- ⬜ ProGuard/R8 rules verification for V2Ray native libraries

---

## 🟢 COMPLETED (Don't Re-do)

| Task | Status |
|------|--------|
| Clean Architecture migration — all 8 old `lib/pages/` files deleted | ✅ Done |
| 15 VLESS servers added to Supabase (Worker returns 20 servers) | ✅ Done |
| Swamp background (`RootNetBackground`) applied to login, connect, main shell | ✅ Done |
| Launcher icon — new `launcher_icon_painter.dart` with network-ring design | ✅ Done |
| All mipmap variants generated (ic_launcher.png + ic_launcher_round.png, 5 densities) | ✅ Done |
| `android:roundIcon` added to AndroidManifest.xml | ✅ Done |
| VPN `proxyOnly: false` fix applied (was stuck on Connecting) | ✅ Done |
| 9 Flutter analyze warnings fixed | ✅ Done |
| Black circle around login logo fixed (solid bg → translucent gradient) | ✅ Done |
| Dead code removed (`renderRootNetLogoToPng`, unused imports) | ✅ Done |
| SHA fingerprints fetched for both debug & release keystores | ✅ Done |
| New Web application OAuth client created in Google Cloud Console | ✅ Done |
| Old Desktop app OAuth client deleted | ✅ Done |
| `agent_only.txt` updated with new credentials | ✅ Done |
| Push token lifecycle — auto-register on login (auth listener), unregister on logout (settings) | ✅ Done |
| Pinned HTTP client applied to `push_service` (register/unregister device) | ✅ Done |
| iOS jailbreak MethodChannel fixed to standard Flutter API (`AppDelegate.swift`) | ✅ Done |
| `cydia` added to iOS `LSApplicationQueriesSchemes` | ✅ Done |
| Scraper pipeline — `POST /import-vless` endpoint + migration `20260803000001` | ✅ Done |
| Auto-import — pg_cron `import-vless-every-30min` + shared RPC `import_pending_vless_links` (migration `20260803000002`) | ✅ Done |
| Deployed to production — rootnet-api Edge Function, all 12 migrations (db push), vless-ingestion-api worker + secrets, ADMIN_KEY secret | ✅ Done |
| `FCM_SERVICE_ACCOUNT` secret set (service account key in `credentials/firebase-adminsdk.json`) — push verified end-to-end | ✅ Done |
| `vless-worker/` moved into `RootNet/` (app + worker + scraper unified) | ✅ Done |
| `ROADMAP.md` created for the combined project | ✅ Done |
| **First release APK (v1.1.2)** — signed 84.8 MB `app-release.apk` (Gradle mirror fix for 403-blocked storage.googleapis.com) | ✅ Done |
| **GitHub Release v1.1.2** — created, APK attached (`app-release.apk`) | ✅ Done |
| **Site download link** — `rootnet.html` `#downloadBtn` → GitHub Releases `/latest/download`, site deployed | ✅ Done |
| **Session expiry enforcement** — engine hard-stopped at 30 min; single-shot timer + 1s safety net; manual disconnect doesn't trip it | ✅ Done |
| **JWT-based premium gating** — premium = signed `app_metadata.isPremium`; fake local `premium_access`/`premium_unlock_pending` flags removed | ✅ Done |
| **Ad gate graceful fallback** — VPN not locked out when Unity Ads unavailable (placeholder Game ID / init failure); premium users skip ads | ✅ Done |
| **Notification throttle** — persistent timer notification updated ~10s instead of every 1s tick | ✅ Done |
| **Dead Firebase deps removed** — `firebase_auth`, `cloud_firestore`, `firebase_analytics`, `firebase_service.dart` (9 packages dropped from lockfile) | ✅ Done |

---

## 📋 PROJECT REFERENCE

### Key Constants
- **Package name:** `com.chobgroup.rootnet`
- **Supabase project:** `bprkazfxqmanrybiexnh`
- **Supabase URL:** `https://bprkazfxqmanrybiexnh.supabase.co`
- **RootNet API (Supabase Edge Function):** `https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api`
- **Firebase project:** `rootnet-43714` (project number: `465028330311`)
- **Android OAuth Client ID:** `465028330311-j3nlh3ustjf92u3ndaa37e2gookphe4g`
- **Android OAuth Client Secret:** `REDACTED_GOOGLE_CLIENT_SECRET`

### File Locations
- **Credentials:** `credentials/client_secret_465028330311-j3nlh3ustjf92u3ndaa37e2gookphe4g...json` (gitignored)
- **All secrets/config:** `agent_only.txt` (gitignored)
- **Release keystore:** `android/app/upload-keystore.jks`
- **Key properties:** `android/key.properties` (gitignored)

### SHA Fingerprints
- **Debug SHA-1:** `E2:29:AF:42:52:77:2C:61:F8:48:6F:BA:C9:E7:E2:1E:B8:80:EC:D9`
- **Release SHA-1:** `62:19:AC:6E:65:7D:E8:F2:35:49:A1:2C:0A:56:05:32:1A:8A:98:80`
