# RootNet VPN — Project Context

> ## 🚀 V2.0 (2026-08-13) — CONFIG LAUNCHER
>
> RootNet v2.2 is a **native Android config launcher**, not a VPN client. The built-in Xray
> engine, auth/premium, session timer, FCM push, AdMob, and Unity Ads are **removed**
> (2026-08-15: the `premium_only` column was dropped from `servers`/`vless_links` — every
> server is public).
> The app serves fresh VLESS/V2Ray configs from Supabase and lets users **copy** them
> (Adivery interstitial) or **export** them (Adivery rewarded video) into their own client
> app (v2rayNG, NekoBox, Hiddify…). No accounts. Version gate kept. **Adivery is the only
> ad network** (App ID `73697db8-c7dc-4af2-9f3c-dd422942cf57`).
>
> ⚠️ Much of this document still describes the **retired Flutter VPN app**. For current truth
> use **`app_clone.md` §0 (v2.0 supersession)** and the code in `android-app/`.

## 1. Project Overview

- Native Android config launcher (Kotlin + Jetpack Compose, rebranded from WoodVless to **RootNet**)
- Package: **com.chobgroup.rootnet**
- Uses **Supabase** (public REST) to fetch the VLESS/V2Ray server config list
- Configs are **copied** or **exported** to the user's own client app — RootNet never tunnels traffic
- Dark cyber-organic UI theme (neon green `#4CFF88` / `#39FF14`, deep forest `#0B1A12`)
- No accounts, no login, no session persistence
- App icon: **RootNet logo**
- Landing page: **https://chobgroup.pages.dev** (Chob Group hub)
- Project pages: **https://chobgroup.pages.dev/rootnet.html** (RootNet), **https://chobgroup.pages.dev/geoip.html** (GeoIP)
- Backend API: Supabase Edge Functions (replaces old Cloudflare Workers)

---

## 2. Completed Features — 🔴 RETIRED (v1 Flutter VPN app)

> Everything from here through §17 (except the explicitly v2-marked sections like §14) is
> **historical v1 content** — login/auth, sessions, Unity Ads gating, engine, FCM, etc. are
> GONE in v2.2. Do not treat these as current requirements. For the live v2 feature list
> see `app_clone_checklist.md` (v2.0 section) and `ROADMAP.md` (v2.0 section).

- User authentication (login / signup / logout) with Supabase
- Password reset via email (Supabase)
- Session persistence (auto-login on app restart)
- Server list with 5 real VLESS configs (Oak, Pine, Redwood, Cedar, Birch)
- Ping/latency testing per server and "Ping All" with auto-sort by speed
- Premium cyber-organic UI redesign on all screens
- Shared theme constants (`lib/services/theme_constants.dart`) — consistently used across all screens
  - ✅ `speed_screen.dart` now uses `AppTheme` constants (no hardcoded colors)
  - ✅ `settings_screen.dart` routes all auth through `AuthService.instance`
- **Production-grade 30-min countdown timer** — DateTime-based (`_endTime`), no drift
- **Disconnect confirmation dialog** — "VPN disconnected. Timer will reset to 00:00"
- **Upload/download speed display** — Real-time traffic from `VlessStatus` (shown compactly near connect/disconnect button)
- **Scrollable VPN page** — Prevents bottom overflow on small screens
- **RootNet logo** — Animated custom logo widget (used in app + as launcher icon)
- **Persistent timer notification** — Android notification with countdown + speeds
- **Ad-gated timer** — Free users watch a rewarded Unity Ads video to unlock a 30-min session; premium users (JWT `app_metadata.isPremium`) skip ads; gate self-disables when ads aren't configured (no VPN lockout)
- **Session-expiry enforcement** — engine hard-stopped at 30 min (single-shot timer + 1s safety net); manual disconnect doesn't trip it
- **JWT-based premium gating** — premium = signed `app_metadata.isPremium` (no forgeable local flag); premium servers filtered server-side + in UI
- **Throttled persistent notification** — timer notification updates ~10s instead of every 1s tick
- **Crash reporting** — `firebase_crashlytics` wrapper (`crashlytics_service.dart`) with user-ID association + global error handlers
- **App icon** — RootNet logo set as launcher icon for Android, iOS, Web, Windows, macOS
- **Version gating** — App blocks if below `minimumVersion` from API (full-screen update page)
- **Supabase Edge Functions API** — Server delivery, version check, push notifications, public endpoints
- **Supabase DB storage** — All VLESS server configs stored in Supabase `servers` table
- **Marketing landing page** — Dark-themed page with server list, features, download section
- **Chob Group hub page** — `/` at chobgroup.pages.dev, showcases all projects with About + Projects sections
- **GeoIP interactive tool** — `/geoip.html` with client-side IP validation + live lookup button + flag result display
- **Cancel button while connecting** — Orange stop icon appears during Connecting state, allows user to abort the connection attempt without waiting
- **Client-side IP validation** — IPv4 + IPv6 validation pre-checks before any API call to save free-tier quota
- **GeoIP Service** — Production-grade Supabase Edge Function with caching, circuit breaker, and multiple providers
- **Google Sign-In** — Supabase OAuth wired on login + signup screens (deep-link `com.chobgroup.rootnet://callback`)
- **FCM push lifecycle** — token auto-registered on login (auth-state listener), unregistered on logout
- **Certificate pinning** — pinned HTTP client covers API + push registration calls
- **Scraper pipeline integration** — `POST /import-vless` promotes scraped Telegram links into the `servers` table (idempotent, admin key)
- **Auto-import scheduler** — pg_cron job `import-vless-every-30min` runs the shared `import_pending_vless_links()` RPC every 30 min (migration `20260803000002`)

---

## 3. Supabase Edge Functions API (`supabase/functions/`)

Replaces the deprecated Cloudflare Workers. All backend logic runs as Supabase Edge Functions (Deno).

| Function | Purpose |
|----------|---------|
| `rootnet-api` | Main API — servers, version, push notifications, device registration |
| `geo-api` | GeoIP lookup — IP geolocation with caching, circuit breaker, fallback |

### 3.1 Endpoints (`rootnet-api`)

| Endpoint | Method | Auth | Purpose |
|----------|--------|------|---------|
| `/servers` | POST | JWT + Premium | Returns server list (fetched from Supabase DB) |
| `/version` | POST | JWT | Returns version info (latest, minimum, build, release notes) |
| `/public/servers` | GET | None | Public server names & countries (for landing page) |
| `/register-device` | POST | JWT | Register FCM push token |
| `/unregister-device` | POST | JWT | Remove FCM push token |
| `/send-notification` | POST | Admin key | Send push notification to user |
| `/import-vless` | POST | JWT + Admin key | Manual trigger of the import RPC `import_pending_vless_links()` (also runs automatically via pg_cron `import-vless-every-30min`) |
| `/geoip` | GET | None | GeoIP lookup (proxied to geo-api) |
| `/health` | GET | None | Health check |
| `/` | GET | None | Health check (alias) |

### 3.2 Version Info Fields

```json
{
  "latestVersion": "1.1.2",
  "latestBuild": 3,
  "minimumVersion": "1.0.0",
  "updateUrl": "https://chobgroup.pages.dev",
  "releaseNotes": "...",
  "forceUpdate": false
}
```

### 3.3 How Version Gating Works

Same as before — app startup check, connection-time check, graceful fallback on offline.

### 3.4 Security

- **JWT Bearer token** via `supabase.auth.getUser()` (no manual JWKS)
- **Premium check** on `/servers` endpoint (`app_metadata.isPremium`)
- **CORS** enabled for Flutter app and landing page
- **Supabase service_role key** used for DB queries (never exposed to client)
- **Rate limiting** — Postgres-backed via `check_rate_limit()` RPC

---

## 4. Pages Site (`pages-site/`) — Chob Group Hub

All pages are hosted on **Cloudflare Pages** under the `chobgroup` project.
The old `rootnet` Pages project has been deleted.

### Site Structure

| Path | Content |
|------|---------|
| `/` (index.html) | **Chob Group** — Landing page with About + Projects sections |
| `/rootnet.html` | **RootNet** — VPN landing page with features, servers, download |
| `/geoip.html` | **GeoIP Worker** — API docs + interactive IP lookup tool |
| `/privacy.html` | Privacy policy |

### Chob Group (`/`)
- Purple/blue theme (#8888FF, #66BBFF)
- Nav: About | Projects
- About section: AI-Powered Development + Free-Tier Infrastructure cards
- Projects section: RootNet + GeoIP Worker cards with "Learn More" links
- All nav links stay within `chobgroup.pages.dev` — no external pages

### RootNet (`/rootnet.html`)
- Neon green theme (#39FF14, deep forest #0B1A12)
- Nav: ← Chob Group | Features | Servers | Download
- Sections: Hero, Features (6 cards), Servers (fetched from Worker API), Download
- Server list shows names + countries only (NO IPs, NO ports, NO configs) - fetched from `/public/servers`
- "← Chob Group" link in nav and footer to navigate back to hub

### GeoIP (`/geoip.html`)
- Gold/yellow accent theme (#FFC832)
- **Interactive IP lookup tool** with:
  - Text input for IP address
  - "🔍 Lookup" button (Enter key also triggers)
  - Client-side validation: IPv4 (0-255 octets) + IPv6 (full + compressed `::` including `::` alone)
  - Shows error on invalid IP — **saves free API quota**
  - Loading spinner during API call
  - Result: large flag emoji + country name + looked-up IP
  - Error handling for API failures
- API docs: Endpoint, Quick Start, Features cards

### Deployment
```bash
# Deploy to Chob Group project (primary)
npx wrangler pages deploy pages-site --project-name chobgroup --branch main
```

**URL:**
- `https://chobgroup.pages.dev` (primary)

---

## 5. App Icon

- Source: Custom `RootNetLogo` painter rendered to 1024×1024 PNG
- Tool: `flutter_launcher_icons` package
- Generated for: Android (mipmap), iOS, Web (manifest), Windows, macOS
- Config in `pubspec.yaml` under `flutter_launcher_icons:`
- To regenerate: `dart run flutter_launcher_icons`

---

## 6. DEPLOYMENT GUIDE — Step by Step

This section is your **handbook** for deploying and maintaining RootNet.

---

### 🔧 Step 1: Prerequisites

- **Flutter SDK** (2026+) — `flutter doctor` should pass
- **Node.js** (v18+) — `node --version`
- **Supabase CLI** — installed via `npm install -g supabase` or `npx supabase`
- **Git** (optional)

Login to Supabase:
```bash
npx supabase login
```

---

### 🌐 Step 2: Deploy / Update Edge Functions

```bash
# Set secrets
cd supabase
npx supabase secrets set SUPABASE_URL=<your-project-url>
npx supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your-key>
npx supabase secrets set ADMIN_KEY=<shared-admin-secret>
npx supabase secrets set FCM_SERVICE_ACCOUNT='{...}'

# Deploy
npx supabase functions deploy rootnet-api --no-verify-jwt
npx supabase functions deploy geo-api --no-verify-jwt
```

Edge Function URLs:
- `https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api`
- `https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/geo-api`

---

### 📄 Step 3: Deploy / Update the Pages Site (Chob Group Hub)

```bash
# First time only (already done)
# npx wrangler pages project create chobgroup --production-branch main

# Deploy (every time)
npx wrangler pages deploy pages-site --project-name chobgroup --branch main
```

| Page | URL |
|------|-----|
| Chob Group Hub | https://chobgroup.pages.dev |
| RootNet Project | https://chobgroup.pages.dev/rootnet.html |
| GeoIP Project | https://chobgroup.pages.dev/geoip.html |
| Privacy Policy | https://chobgroup.pages.dev/privacy.html |

> ⚠️ The old `rootnet` Pages project has been **deleted**. Always use `chobgroup` as the project name.

---

### 📱 Step 4: Run the Flutter App

```bash
flutter run
```

The app is configured to point at the Supabase Edge Functions.

---

### 🔐 Step 5: Environment Variables & Secrets

| Variable | Location | Type |
|----------|----------|------|
| `SUPABASE_URL` | `supabase secrets set` | 🔒 Secret |
| `SUPABASE_SERVICE_ROLE_KEY` | `supabase secrets set` | 🔒 Secret |
| `ADMIN_KEY` | `supabase secrets set` | 🔒 Secret |
| `FCM_SERVICE_ACCOUNT` | `supabase secrets set` | 🔒 Secret |
| Supabase URL | `lib/services/app_constants.dart` | Public |
| Supabase Anon Key | `lib/services/app_constants.dart` | Public |
| VLESS configs | `supabase/seed.sql` (in Supabase DB) | 🔒 Protected |

**To change secrets:** `npx supabase secrets set SECRET_NAME`

---

### 🚀 Step 6: Build Release APK

```bash
flutter build apk --release
# APK: build/app/outputs/flutter-apk/app-release.apk
```

---

### 🔄 Step 7: Update the Download Link

1. Upload the APK somewhere (Google Drive, GitHub, etc.)
2. Open `pages-site/index.html`
3. Change `id="downloadBtn"` `href="#"` to your APK URL
4. Redeploy the landing page

---

### 🗄️ Step 8: Push DB Schema Changes

```bash
# Pull remote schema changes locally
npx supabase db pull

# Push local migrations to remote
npx supabase db push

# Run seed data (if needed)
npx supabase db reset
```

---

## 7. HOW TO: Add More Servers (No App Changes Needed)

All server configs are stored in the **Supabase `servers` table**. The Worker queries Supabase on every request — **no app rebuild or Worker redeploy needed**.

### Option A: Via Supabase SQL Editor (Recommended)

1. Go to **Supabase Dashboard** → **SQL Editor**:
   https://app.supabase.com/project/bprkazfxqmanrybiexnh/sql

2. Run the INSERT statement:
```sql
INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only)
VALUES (
  'Tokyo-1',                                                          -- Display name
  '🇯🇵',                                                              -- Emoji flag
  'Japan',                                                            -- Location label
  'vless://...',                                                      -- Full VLESS URI
  'your-server.com',                                                  -- Hostname (for landing page)
  443,                                                                -- Port (for landing page)
  true,                                                               -- is_active
  false                                                               -- premium_only
);
```

3. **Verify:**
   ```bash
   curl https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api/public/servers
   ```

**Your new server will appear in the app immediately.** Users just need to refresh the server list (pull down to refresh).

### Option B: Via Seed File (for version control)

1. Open `supabase/seed.sql`
2. Add your server to the INSERT block
3. Push: `npx supabase db push`
4. Or reset local DB: `npx supabase db reset`

---

## 8. HOW TO: Force an App Update (Version Gate)

1. **Open a Supabase SQL Editor** (or run a migration)
2. **Update the `app_config` table**:
   ```sql
   UPDATE public.app_config
   SET minimum_version = '1.2.0'
   WHERE id = 1;
   ```
3. Old apps will now see a full-screen "Update Required" page with download button

To unblock: set `minimum_version` back to `'1.0.0'`.

---

## 9. Key URLs

| What | URL |
|------|-----|
| Chob Group Hub | https://chobgroup.pages.dev |
| RootNet Project Page | https://chobgroup.pages.dev/rootnet.html |
| GeoIP Project Page | https://chobgroup.pages.dev/geoip.html |
| RootNet API (Edge Function) | https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api |
| GeoIP API (Edge Function) | https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/geo-api |
| Pages Dashboard | https://dash.cloudflare.com/?to=pages |
| Supabase Dashboard | https://app.supabase.com/project/bprkazfxqmanrybiexnh |
| Supabase SQL Editor | https://app.supabase.com/project/bprkazfxqmanrybiexnh/sql |
| Supabase Table Editor | https://app.supabase.com/project/bprkazfxqmanrybiexnh/editor |

### Architecture Migration (complete)
- **All backend logic** migrated from Cloudflare Workers → Supabase Edge Functions
- **GeoIP** from standalone Worker → production-grade Edge Function with caching + circuit breaker
- **Proxy Worker** deprecated (pages.dev is now directly accessible from Iran)

---

## 10. GeoIP Edge Function (`supabase/functions/geo-api/`)

A production-grade Supabase Edge Function for IP geolocation with:
- **Supabase as primary** provider (fast, internal)
- **ip-api.com as fallback** (when Supabase is down)
- **In-memory cache** with 1-hour TTL and LRU eviction
- **Circuit breaker** — auto-disables Supabase after 5 consecutive failures (60s cooldown)
- **Retry with exponential backoff** — 2 retries, 300ms → 600ms
- **Response format**: `{ ip, country, countryCode }`

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/?ip=X.X.X.X` | GET | Look up an IP → returns `{ip, country, countryCode}` |
| `/health` | GET | Health check |
| `/` | GET | Usage instructions |

### Architecture

```
Client
  ↓
GeoIP Edge Function
  ├── Cache lookup (1h TTL)
  ├── Circuit breaker check
  ├── Primary: Supabase IP table (2s timeout)
  │     └── Failure → Fallback: ip-api.com (5s timeout)
  └── Return normalized { ip, country, countryCode }
```

### Deployment

```bash
cd supabase
supabase functions deploy geo-api --no-verify-jwt
```

---

## 11. What's Left Before Publish

- [x] **Google Sign-In** — ✅ Wired: Supabase OAuth on login + signup screens, deep-link callback (`com.chobgroup.rootnet://callback`)
- [x] **Push notifications** — ✅ Wired: real FCM (`PushService`), token registered on login (auth-state listener), unregistered on logout
- [x] **Root/jailbreak detection** — ✅ Native Android (MainActivity.kt) + iOS (AppDelegate.swift) via `SecurityService.isDeviceCompromised()`; warning banner on login/signup/settings
- [x] **Certificate pinning** — ✅ Pinned HTTP client used by `http_service` AND `push_service` (register/unregister device)
- [x] **Pipeline integration** — ✅ `POST /import-vless` promotes scraped Telegram links (`vless_links` → `servers`); migration `20260803000001`
- [ ] **Real Unity Ads Game ID** — ✅ integration is real (`unity_ads_plugin`, `UnityAdsService`); only the Game ID is missing (`YOUR_ANDROID_GAME_ID` in `lib/services/ads_config.dart`). Until set, the ad gate self-disables.
- [x] **APK download link** — ✅ v1.1.2 release APK (signed, 84.8 MB) on GitHub Releases; `rootnet.html` `#downloadBtn` → `https://github.com/ahmad43ir/rootnet/releases/latest/download/app-release.apk`
- [ ] **App store listings** — Google Play Store description + screenshots
- [x] **Error tracking** — ✅ Firebase Crashlytics wired (`crashlytics_service.dart` + global error handlers)

---

## 12. Publishing model

- **Free mode:** the admin Telegram account publishes VLESS links, free users can view the shared content, and ads remain enabled.
- **Premium mode:** premium users get an ad-free experience and support the scraper-bot infrastructure and related operational costs.
- **Docs:** the launch strategy is captured in [PUBLISH_PLAN.md](PUBLISH_PLAN.md) for future agents and maintainers.

## 13. Key Learnings & Fixes

### VPN Connection — Permission Dialog Fix (Critical)
**Problem:** VPN permission dialog never appeared when tapping connect.
**Root Cause:** `_flutterVless.requestPermission()` was not being called before `startVless()` in the connection flow.
**Fix:** Added explicit `requestPermission()` call before `startVless()` in `vless_connector.dart`.
**Symptom Fix:** Also fixed `ConnectScreenState` initialization to properly await engine init.

### Immediate Disconnect After Connect
**Problem:** VPN connected for ~1 second then immediately disconnected.
**Root Cause:** The `_isDisposed` check in the old code was causing premature disconnect.
**Fix:** Removed stale `_isDisposed` state guard. Added proper `onEngineDisconnected()` handler that also handles `Connecting` state.

### Cancel While Connecting
**Problem:** Users had no way to abort a connection attempt that was taking too long.
**Fix:** Added orange stop icon button during `Connecting` state. `_cancelConnect()` silently disconnects the engine without showing error snackbar (via `_userCancelled` flag).

### GeoIP Validation — `::` IPv6 Edge Case
**Problem:** The original compressed IPv6 regex `/^([0-9a-fA-F]{1,4}:){0,7}(:[0-9a-fA-F]{1,4}){0,7}$/` couldn't match `::` alone (the unspecified address) — both capturing groups at 0 occurrences match empty string, not `::`.
**Fix:** Added explicit special case `if (s === '::') return true;` and changed quantifiers to `{1,7}` to prevent empty-string false matches.
**Lesson:** When working with regex for IP validation, always test edge cases like `::`, `::1`, and addresses with >8 groups.

### Chob Group Nav Duplicate
**Problem:** Replacing "RootNet" and "GeoIP" nav links with a single "Projects" link created a duplicate because "Projects" already existed in the nav.
**Fix:** Checked for pre-existing links before adding replacements.
**Lesson:** Always verify the full list of elements before replacing — redundant links are easy to miss.

---

## 14. Core Rules (v2.0)

- Do NOT modify existing UI or design theme
- Keep code simple (no over-engineering)
- The app NEVER connects/tunnels — it only serves configs (copy / export)
- Ad flow (Adivery only): **Copy** gated by an interstitial (throttled 60s); **Export** and **Refresh** gated by a rewarded video (completion required); ads unavailable → action still completes (NO lockout)
- No accounts, no premium, no JWT — the app is fully public
- Server list MUST be fetched from the backend (public Supabase REST) — never hardcoded
- Version check MUST run at app startup
- **All VLESS configs live ONLY in Supabase DB** — never hardcode in the app or Worker

---

## 12. Project Structure

```
rootnet/
├── ROADMAP.md                      # 🗺️ Combined project roadmap (app + worker + scraper)
├── pages-site/                     # Static HTML site (Chob Group hub)
│   ├── index.html                 # Chob Group — main hub page (About + Projects)
│   ├── rootnet.html               # RootNet VPN landing page
│   ├── geoip.html                 # GeoIP API docs + interactive lookup tool
│   ├── privacy.html               # Privacy policy
│   └── chob.html                  # (deprecated — renamed to index.html)
├── vless-worker/                   # Cloudflare ingestion worker (webhook → Supabase vless_links)
│   ├── wrangler.toml
│   └── src/index.js
├── vless-scraper/                  # Telegram scraper (Python/Telethon → webhook to vless-worker)
│   ├── main.py
│   └── README.md
├── android-app/                   # v2.x native Kotlin/Compose app (NOT Flutter)
│   ├── app/
│   │   ├── build.gradle.kts       # minSdk 23, Adivery + Crashlytics, R8
│   │   └── proguard-rules.pro     # R8 rules (WorkManager + Adivery keeps)
├── lib/
│   ├── main.dart                  # App entry + startup version check
│   ├── features/                  # Feature-based architecture
│   │   ├── vpn/
│   │   │   └── presentation/
│   │   │       └── screens/
│   │   │           ├── connect_screen.dart   # VPN UI + cancel button + timer + speed display
│   │   │           ├── server_list_screen.dart# Server list + ping + trash
│   │   │           └── speed_screen.dart     # Performance stats
│   │   ├── auth/
│   │   │   └── presentation/
│   │   │       └── screens/
│   │   │           ├── login_screen.dart
│   │   │           └── signup_screen.dart
│   │   ├── settings/
│   │   │   └── presentation/
│   │   │       └── screens/
│   │   │           └── settings_screen.dart
│   │   ├── main_shell/
│   │   │   └── presentation/
│   │   │       └── screens/
│   │   │           └── main_shell_screen.dart
│   │   └── version_check/
│   │       └── presentation/
│   │           └── screens/
│   │               └── update_required_screen.dart
│   ├── widgets/
│   │   ├── rootnet_logo.dart      # Animated brand logo + PNG render function
│   │   └── update_banner.dart     # Sliding update notification banner
│   └── services/
│       ├── app_constants.dart     # Supabase URL + keys, Edge Function URLs
│       ├── auth_service.dart      # Supabase auth singleton (+ JWT isPremium)
│       ├── app_theme.dart         # Shared design tokens (AppTheme)
│       ├── ads_config.dart        # Unity Ads Game IDs + isConfigured check
│       ├── unity_ads_service.dart # Real Unity Ads lifecycle
│       ├── rewarded_ad_service.dart # Public ad API + isAvailable (gate logic)
│       ├── crashlytics_service.dart # Crashlytics wrapper
│       ├── push_service.dart      # FCM push (register/unregister, handlers)
│       ├── ping_service.dart      # TCP latency measurement
│       ├── notification_service.dart # Persistent timer notification
│       ├── http_service.dart      # HTTP client for Edge Functions
│       ├── version_check_service.dart # Version check + gating
│       └── security_service.dart  # Security utilities
├── supabase/
│   ├── config.toml                # Local Supabase config
│   ├── functions/
│   │   ├── rootnet-api/           # Main API Edge Function
│   │   │   ├── index.ts
│   │   │   ├── _utils.ts
│   │   │   ├── _auth.ts
│   │   │   ├── _rate-limit.ts
│   │   │   ├── _fcm.ts
│   │   │   └── README.md
│   │   └── geo-api/               # GeoIP Edge Function
│   │       ├── index.ts
│   │       ├── _types.ts
│   │       ├── _utils.ts
│   │       ├── _cache.ts
│   │       ├── _circuit-breaker.ts
│   │       ├── _retry.ts
│   │       ├── _rate-limit.ts
│   │       ├── _geo-service.ts
│   │       ├── _providers/
│   │       │   ├── _supabase.ts
│   │       │   └── _cloud-api.ts
│   │       └── README.md
│   ├── migrations/
│   │   ├── 20260721000001_create_servers_table.sql
│   │   ├── 20260721000002_create_app_config_table.sql
│   │   ├── 20260721000003_fix_release_notes_newlines.sql
│   │   ├── 20260721000004_create_device_tokens_table.sql
│   │   ├── 20260721000005_add_protocol_support.sql
│   │   └── 20260721000006_add_vless_servers.sql
│   └── seed.sql                   # Initial server data
├── tool/
│   └── generate_icon_test.dart    # Script to regenerate the icon PNG
├── test/
│   └── widget_test.dart
├── README.md
└── PROJECT_CONTEXT.md
```

---

## 15. Connection Flow (Updated)

```
User taps connect button
  → Version check (POST /version to Edge Function)
    → If isBelowMinimum → Navigate to UpdateRequiredScreen (BLOCKED)
    → If forceUpdate → block connection, show banner
    → If hasUpdate → show banner (auto-dismiss 5s)
  → "Watching ad..." status (free users only, and only when ads are available)
  → RewardedAdService.showRewardedAd() → Unity Ads (real SDK; skipped for
    premium users or when ads aren't configured → no lockout)
  → Timer starts (endTime = DateTime.now() + 30 min)
  → Fetch servers (POST /servers → Edge Function queries Supabase → returns list)
  → FlutterVless.parse(config) to get V2Ray config
  → _flutterVless.requestPermission() → Android VPN dialog
  → _flutterVless.startVless(remark, config)
  → onStatusChanged fires connected/connecting/disconnected
  → Persistent notification shows countdown + speeds
  → Timer counts down via endTime.difference(DateTime.now())
  → Speed display shown compactly near connect/disconnect button
  → Auto-disconnect at 00:00:00
  → Manual disconnect → confirmation dialog → timer resets
```

---

## 16. Security Architecture

```
Flutter App                     Supabase Edge Function           Supabase DB
─────────────                   ─────────────────────          ───────────
POST /version ─── JWT Token ───→ Auth Check → Return version
POST /servers ─── JWT Token ───→ Auth Check → Query Supabase → SELECT servers
                                        ↑             │
                               service_role key  ←────┘
                               (supabase secret)

Landing Page                    Supabase Edge Function
─────────────                   ─────────────────────
GET /public/servers ── no auth ──→ Query Supabase (non-premium only)
                                   → Return {name, flag, country}
GET /health ───────── no auth ──→ Return {status: "ok"}
```

---

## 17. Supabase Migration & Seed Files

### `supabase/migrations/20260721000001_create_servers_table.sql`

Creates the `servers` table with:
- Columns: `id`, `name`, `flag`, `country`, `config`, `host`, `port`, `is_active`, `premium_only`, `created_at`
- Indexes on `is_active`, `premium_only`, `name`
- Row Level Security (RLS) enabled with policies for anon, authenticated, and premium users
- Note: Edge Functions use `service_role` key which **bypasses RLS** — the policies are for future direct SDK access

### `supabase/seed.sql`

Seeds the initial 5 servers (Oak, Pine, Redwood, Cedar, Birch) with their full VLESS configs.
Runs only if the `servers` table is empty (idempotent).

### To apply:
```bash
npx supabase db push          # Push migration to remote
npx supabase db reset         # Reset local DB (applies migration + seed)
```
