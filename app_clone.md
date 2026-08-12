# ROOTNET VPN — Complete Clone Specification (`app_clone.md`)

> **Purpose:** This document is the single source of truth for reimplementing the RootNet VPN app
> in **any programming language / framework** (native Android, Kotlin + Jetpack Compose,
> React Native, Flutter, KMP, etc.). It is written to be **implementation-agnostic**: every
> screen, state, API contract, database schema, engine config, security rule, and edge case
> is specified precisely enough that an agent can rebuild a **feature-identical clone**
> without ever looking at the original source.
>
> **How to use this spec:** Follow the sections in order. Section 16 (Definition of Done)
> is the acceptance checklist — treat it as the contract. If a requirement here conflicts
> with a library/framework default, **this spec wins**.
>
> Original app: **RootNet VPN** — Flutter, version `1.1.2+3`, application ID `com.chobgroup.rootnet`.

---

## 1. Product Overview

**RootNet** is a production-grade, ad-supported **VPN client** that connects users to
**VLESS/V2Ray (Xray)** proxy servers to bypass internet censorship (target region: Iran).

- **Free tier:** watch a **rewarded video ad** → unlock a **30-minute VPN session**.
- **Premium tier:** granted **server-side** (signed JWT claim `app_metadata.isPremium`) →
  **no ads**, unlimited session length, and access to **premium-only servers**.
- **Mandatory product rules (non-negotiable):**
  1. The ad gate must **NEVER lock users out of the VPN** — if ads are not configured or the ad
     SDK fails to initialize, free users connect **without** watching an ad.
  2. The 30-minute session must be **enforced by hard-stopping the VPN tunnel at 00:00** —
     not just visually. This is the monetization gate.
  3. Premium status must come **ONLY from the signed JWT** (`app_metadata.isPremium`) —
     never from a client-side flag the user could forge.
  4. Premium server visibility must be enforced **both server-side and in the UI**.
  5. All VPN server configs live **only in the database** and are delivered per-request over
     HTTPS — never hardcoded in the client.
  6. Version check runs **at app startup AND before every connect attempt**.
  7. Theme: dark "cyber-organic" (deep forest + neon green). No light theme.

---

## 2. Tech Stack (original) & Language-Agnostic Equivalents

| Concern | Original (Flutter) | Equivalent in any stack |
|---|---|---|
| UI | Flutter + Material | Compose / SwiftUI / RN components |
| State mgmt | Riverpod (StateNotifier + Provider) | StateFlow / Redux / Provider / Bloc |
| Backend | **Supabase** (Auth + Postgres + Edge Functions) | Supabase (reuse) or any Postgres + API |
| Push | Firebase Cloud Messaging (FCM) | FCM / APNs |
| Crash reporting | Firebase Crashlytics | Crashlytics / Sentry |
| VPN engine | `flutter_vless` (wraps Xray/V2Ray core, TUN) | sing-box / Xray-core / v2ray-core via JNI/FFI |
| Ads | Unity Ads (rewarded video) | Unity Ads / AdMob rewarded (same semantics) |
| Notifications | `flutter_local_notifications` | Android NotificationManager / UNUserNotificationCenter |
| Secure storage | `flutter_secure_storage` (Keystore/Keychain) | EncryptedSharedPreferences / Keychain |
| Settings | `shared_preferences` | DataStore / NSUserDefaults |
| Networking | `http` (wrapped, pinned TLS) | OkHttp / URLSession with cert pinning |
| Deep links | `com.chobgroup.rootnet://callback` | Custom scheme / App Links |
| Config parsing | custom normalizers | port as-is (pure logic) |

**Guideline:** keep the architecture simple — a handful of singleton services + one connection
state machine + thin screens. Do **not** over-engineer (no needless DI frameworks, no complex
state libraries).

---

## 3. Architecture

```
┌─ MOBILE APP ─────────────────────────────────────────────┐
│  Screens (Login · MainShell · Connect · ServerList ·     │
│           Settings · UpdateRequired)                     │
│        │                                                 │
│  Connection State Machine (Idle→Connecting→Connected→…)  │
│        │                                                 │
│  Connector Factory → per-protocol Connector → Xray core  │
│        │                                                 │
│  Services: Auth · ApiClient(pinned TLS) · Ping · Ads ·   │
│            VersionCheck · Push/FCM · Notifications ·     │
│            Security(root detect) · GeoRouting ·          │
│            Crashlytics                                   │
└──────────────┬───────────────────────────────────────────┘
               │ HTTPS (JSON, JWT bearer, pinned cert)
┌──────────────▼───────────────────────────────────────────┐
│  SUPABASE PROJECT                                        │
│  • Auth (email/pw + Google OAuth)                        │
│  • Postgres: servers, app_config, device_tokens,         │
│              vless_links, rate_limits                    │
│  • Edge Function `rootnet-api` = the app's API           │
│  • Edge Function `geo-api` (GeoIP lookup)                │
│  • pg_cron: import_pending_vless_links                   │
└──────────────┬───────────────────────────────────────────┘
               │ webhook (X-Webhook-Key)
┌──────────────▼───────────────────────────────────────────┐
│  CONTENT PIPELINE                                        │
│  Telegram channels → scraper (Telethon) → ingestion      │
│  Worker (/webhook) → Supabase vless_links → import RPC   │
│  → servers table → the app                               │
└──────────────────────────────────────────────────────────┘
```

**Deployment targets:** Android primary (minSdk 23, ARM64 + ARM32 + x86_64). iOS optional.
Desktop/web builds are not a product requirement.

---

## 4. App Boot Sequence (exact order)

1. Initialize the rewarded-ad service (Unity Ads) — **non-fatal** on failure.
2. Initialize Supabase Auth with project URL + public anon key.
3. Initialize Firebase Core — **non-fatal** on failure (devices without GMS).
4. Initialize Push (FCM): permission request (Android 13+), get token, register with API.
5. Wire global error handlers (crash reporting) — release builds only.
6. Init preferences storage.
7. Determine login state; associate user ID with crash reporter if logged in; subscribe to
   auth changes (associate on login, disassociate on sign-out).
8. **Version check** → if below minimum, boot straight into the Update-Required screen.
9. Launch UI. Initial route: `/update-required` if blocked, else the main shell (with a login
   gate: sign-out returns to Login).

---

## 5. Screens, Navigation & UI Specification

### 5.0 Design System (theme tokens — reproduce exactly)

| Token | Value | Usage |
|---|---|---|
| `bgDeepForest` | `#0B1A12` | app background base |
| `bgDarkEmerald` | `#0A1912` | dialogs, dark surfaces |
| `bgCard` | ~`#10251B` (translucent) | glass cards |
| `accentNeon` | `#4CFF88` | primary accent (buttons, active states, live status) |
| `accentLime` | lighter neon green | secondary accent (download arrow) |
| `textPrimary` | near-white | headings, values |
| `textSecondary` | muted white | body copy |
| `textMuted` | faint white | labels, captions |
| `cardBorder` / `glassBorder` | white @ low alpha | card and divider borders |
| `cornerRadius` | ~20 | cards |
| `screenPadding` | ~20 horizontal | all screens |
| Warning color | orange (`0xFF...`) | root-detection + session-expired warnings |
| Error color | redAccent | errors, disconnect state |

Effects: full-screen vertical **gradient** background (`backgroundGradient`), glass panels
(translucent fill + hairline border + subtle shadow), neon glow shadows on primary buttons,
letter-spaced uppercase micro-labels (`SESSION REMAINING`, `CONNECTION DETAILS`).
Backgrounds render an animated organic "root/wire" motif; the connect screen adds a rotating
**spirograph** (6 orbiting circles, stroke-only, alpha ~0.04–0.08, 20s loop) plus a pulsing
glow on the main button (3s ease-in-out loop).

### 5.1 Login / Signup

- Email + password form; "Sign In", "Create Account", and **Google Sign-In** buttons.
- Google OAuth: open browser → Supabase OAuth → deep link `com.chobgroup.rootnet://callback`
  returns to the app (Supabase SDK handles the exchange; a deep-link intent filter on that
  scheme/host is required).
- **Rooted-device warning banner** (orange, dismissible): shown while `isDeviceCompromised()`
  is true: *"Device is rooted/jailbroken. VPN connections may be less secure."*
- Privacy-policy link → `https://chobgroup.pages.dev/privacy.html`.
- Password reset: email → Supabase sends reset email → `com.chobgroup.rootnet://reset-password`.
- On successful auth (any method): replace with main shell. Sign-in timeouts show a snackbar.

### 5.2 Main Shell (3 tabs, bottom navigation)

| Tab | Icon (outlined/filled) | Label | Content |
|---|---|---|---|
| 0 | vpn_lock | VPN | ConnectScreen for the selected server, or a placeholder |
| 1 | dns | Servers | ServerListScreen |
| 2 | person | Profile | SettingsScreen |

- Placeholder (no server selected): centered icon, "No Server Selected", copy
  *"Free mode is on by default. Open Servers to browse free options or unlock premium for more."*,
  and a neon "Browse Servers" button that switches to tab 1.
- Listen to auth state: on `signedOut`, clear the selected server and return to tab 0.
- **Server selection contract:** the Servers tab returns a selected `{name, config, type,
  config_format}`; the shell stores it and shows the Connect tab. Selecting a different server
  must produce a **fresh ConnectScreen keyed by the config** (avoids stale state; see 9.7 race).

### 5.3 Connect Screen (the heart of the app)

Layout, top → bottom (all inside a scroll view; animated spirograph behind):

1. Rooted-device warning banner (if flagged).
2. **Server card**: icon, server name, status subtitle ("Connected" green or the server
   address), and a green "Live" pill when connected.
3. **Status text** (28pt, letter-spaced, pulsing when connecting) + detail line:
   - `Idle` → "TAP TO CONNECT"
   - `Connecting` → "Connecting..." / detail "Establishing tunnel to <address>"
   - `Connected` → "Connected" (neon) / "via <address>"
   - `Disconnecting` → "Disconnecting..." / "Cleaning up connection..."
   - `Error` → "Connection error" (red) + message
   - `SessionExpired` → "Session Expired" (orange) / "Watch an ad to connect again"
4. **Timer card** (Connected only): "SESSION REMAINING" label + 48pt `HH:MM:SS` countdown
   with neon glow; turns **red below 60s**.
5. **Session-expired chip** (orange): "Session expired — tap to reconnect".
6. **Speed row** (Connected only): ↑ Upload / ↓ Download, formatted `B/s`, `KB/s`, `MB/s`.
7. **Main button** (72px circle): idle = neon power icon with pulsing glow; connecting =
   orange stop icon (tap cancels, no dialog); connected = red power icon (tap → disconnect
   confirmation dialog); disconnecting = spinner, disabled.
   Label under button: "Tap to connect" / "Tap to disconnect" / "Tap to retry".
8. **Connection details panel** (slides in on connect): Encryption `AES-256-CBC`, Protocol
   (`VLESS`/`VMess`/…), Server (name), Session remaining.
9. **Error box** (red) with message.
10. **Update banner** (top overlay, auto-dismiss 5s): shown when an update is available
    (non-blocking). Dismissible.

Behavior details:
- All transient feedback uses floating rounded **snackbars** (green for info, red for errors).
- Cancel-while-connecting: sets a `userCancelled` flag so the subsequent engine error is not
  shown to the user; always succeeds without a dialog.
- Disconnect confirmation dialog: "Disconnect VPN" / body "VPN disconnected. Timer will reset
  to 00:00" / [Cancel | Disconnect(red)].
- **Do not navigate away when connection drops** — revert to Idle in place.

### 5.4 Server List

- Fetch servers on open (see 7.2), render cards: flag emoji, name, country, **live TCP ping**
  (`123ms` or `Timeout`), and for premium servers a lock/premium badge.
- Header chip shows mode: "Free mode • premium servers are hidden until unlocked" (grey) or
  "Premium mode • premium servers unlocked" (neon).
- **Free users:** filter out premium servers client-side **and** the API already excludes them.
- Tap a server → select → jump to Connect tab. Refresh (pull or button) re-pings servers.
- Ping = TCP connect to `host:port` with 5s timeout; failed ping → "Timeout".

### 5.5 Settings / Profile

- Account card: "Logged in as" + email.
- Premium card: status text — premium: "Premium is active. You can use premium servers and no
  ads." / free: "Premium is granted on your account (server-side). Coming soon." + pill
  ("Premium active" / "Free account").
- Actions list: **Reset Password** (email dialog → Supabase), **Privacy Policy** (open URL),
  **Bypass domestic traffic** switch (see 10.3; default ON), **Logout** (confirm dialog →
  unregister FCM token → sign out → back to login).
- Rooted-device warning banner at top (same as login).

### 5.6 Update-Required Screen

Full-screen blocker: "Update Required" heading, release notes, and a prominent button opening
`updateUrl`. No way past it (routing returns to this screen while blocked).

---

## 6. Data Models (port these exactly)

### 6.1 `VpnServer` (UI entity)
```
name: string          // display name, e.g. "Oak", "Redwood"
flag: string          // emoji flag, e.g. "🌐", "🇺🇸"
country: string       // location label, e.g. "Cloud", "US"
rawConfig: string     // the raw URI/JSON/conf string
type: enum {vless, vmess, trojan, wireguard, shadowsocks}
configFormat: enum {link, json, npv, conf, raw}
pingMs: int?          // null = timeout
isPremium: bool       // true = premium-only server
```

### 6.2 `UnifiedConfig` (normalized engine input — the core abstraction)
```
protocol: enum {vless, vmess, trojan, wireguard, shadowsocks}
uuid: string?              // user UUID (VLESS) / password (Trojan/SS)
address: string            // hostname/IP
port: int                  // default 443 when missing
encryption: string         // "none", "aes-256-gcm", "chacha20-poly1305", ...
security: string           // "none" | "tls" | "reality"
sni: string?               // TLS SNI (defaults to address)
fingerprint: string?       // "chrome" | "firefox" | "randomized" ...
allowInsecure: bool
transport: string?         // "tcp" | "ws" | "xhttp" | "grpc"
transportHost: string?     // WS Host header / xhttp host
transportPath: string?     // WS/xhttp path, grpc serviceName
alpn: string?              // comma-separated, e.g. "h2,http/1.1"
rawConfig: string?
extra: map                 // protocol-specific: flow, publicKey(pbk), shortId(sid),
                           // mode, private_key, local_address, public_key,
                           // allowed_ips, dns, mtu ...
```
**Rule: no raw config is ever passed to a connector — normalize first.** A single
`normalize(raw, configFormat?, protocol?)` entry point auto-detects the format:
- starts with `{` and contains `"npv"` → NPV
- starts with `[Interface]`/`[Peer]` → WireGuard `.conf`
- base64-decodes to JSON starting with `{` → VMess JSON
- contains `://` → URI link
- else parses as raw JSON.

### 6.3 Connection states (sealed state machine)
```
Idle
Connecting { server }
Connected { server, sessionEnd: DateTime, uploadSpeed, downloadSpeed }
Disconnecting
Error { message }
SessionExpired
```
Transitions: `Idle → Connecting → Connected → Disconnecting → Idle`;
`Connecting|Connected → Error`; `Connected → SessionExpired → Idle (after 2s)`.
`Connected.remainingSeconds = sessionEnd - now` (0 if negative);
`formattedTime = HH:MM:SS`.

### 6.4 `VersionInfo`
```
hasUpdate, forceUpdate, isBelowMinimum: bool
latestVersion: string, latestBuild: int
minimumVersion: string, updateUrl: string, releaseNotes: string
```
Semver compare on 3 numeric segments (`a>b → 1, a<b → -1, else 0`).
`hasUpdate = latestVersion > current || latestBuild > currentBuild`.
`isBelowMinimum = minimumVersion > current`.
`forceUpdate = forceUpdate_flag || isBelowMinimum`.

---

## 7. Backend API — Exact Contracts

The API is a **Supabase Edge Function** named `rootnet-api`
(base: `https://<PROJECT>.supabase.co/functions/v1/rootnet-api`). The clone should deploy its
own equivalent (any serverless host works) implementing these **identical** contracts.

### 7.0 Client HTTP wrapper (required behavior)

- All requests: `Content-Type: application/json`.
- Headers on every request: `X-Request-Timestamp` (epoch ms), `X-Request-Id` (unique per
  request — anti-replay).
- `Authorization: Bearer <supabase access token>` when a session exists.
- Timeout 15s. Retry: up to **2 retries** with exponential backoff (500ms base) on
  `408, 429, 500, 502, 503, 504` and on timeouts/socket errors.
- **Certificate pinning** on the underlying TLS layer (see 11.1).
- Error mapping: `NetworkException` (timeout/unreachable), status-code-aware failures.

### 7.1 `GET /public/servers` — no auth
```
→ 200 { "servers": [ { "name": "Oak", "flag": "🌐", "country": "Cloud" } ] }
```
Only `is_active=true AND premium_only=false`; **no `config` field** (used by the landing page).

### 7.2 `POST /servers` — JWT required
```
→ 200 { "servers": [
      { "name": "Oak", "flag": "🌐", "country": "Cloud",
        "config": "vless://<uuid>@<host>:<port>?encryption=none&security=tls&type=ws&path=/&host=<host>#RootNet",
        "type": "vless", "config_format": "link" } ],
    "premium": true|false }
```
Non-premium users: server-side filter `premium_only=false`. `premium` echoes the JWT claim.
**Client fallback:** try a direct Supabase read (`select name,flag,country,config,type,
config_format,premium_only where is_active=true`) via the Supabase SDK (RLS-protected — same
access rules), then fall back to this endpoint.

### 7.3 `POST /version` — JWT required
```
→ 200 { "latestVersion": "1.1.2", "latestBuild": 3, "minimumVersion": "1.0.0",
        "updateUrl": "https://chobgroup.pages.dev",
        "releaseNotes": "• ...\n• ...",
        "forceUpdate": false }
```
Served from `app_config` row id=1 (with a hardcoded fallback constant on DB error).

### 7.4 `POST /register-device` — JWT required
```
→ body { "token": "<fcm-token>", "platform": "android" }
→ 200 { "success": true }     // 400 if token missing/<10 chars
```
Upsert into `device_tokens` on conflict `(user_id, token)`.

### 7.5 `POST /unregister-device` — JWT required
```
→ body { "token": "<fcm-token>" }
→ 200 { "success": true }
```
Delete row `(user_id, token)`.

### 7.6 `POST /send-notification` — admin (`X-Admin-Key` header)
```
→ body { "userId": "...", "title": "...", "message": "...", "data": {...} }
→ 200 { ...fcm result }  // 404 if no registered devices
```
Sends an FCM message (v1 HTTP API) to all `device_tokens` rows for `userId`.

### 7.7 `POST /import-vless` — admin (`X-Admin-Key` header)
```
→ body { "limit": 200 }   // optional, max 500
→ 200 { ...result of import_pending_vless_links RPC }
```
Manual trigger of the link-promotion job (see 12.3).

### 7.8 `GET /geoip?ip=<optional>` — no auth
```
→ 200 { "country": "Iran", "country_code": "IR", "flag": "🇮🇷" }
```
Proxies to the `geo-api` edge function (multi-provider GeoIP with circuit breaker + cache);
unknown → `{"country":"Unknown","country_code":"XX","flag":""}`.

### 7.9 `GET /health` (or `/`) — no auth
```
→ 200 { "status": "ok", "service": "RootNet VPN API", "version": "1.1.2",
        "docs": "https://chobgroup.pages.dev" }
```

### 7.10 Shared API security (all endpoints)
- CORS with an allowed-origin allowlist; `OPTIONS` preflight returns 204.
- **Rate limiting in Postgres RPC** (not in-memory): 60 req/min per IP, 30 req/min per user →
  `429 {"error":"Too many requests. Please slow down."}`.
- Auth: verify JWT via the Supabase admin client (`auth.getUser(token)`) — never hand-rolled
  JWKS. 401 `{"error":"Missing or invalid authorization header"}`.
- POST endpoints require `Content-Type: application/json` (400 otherwise).
- All errors: `{"error": "..."}` with proper status codes.

---

## 8. Database Schema (Postgres) — exact DDL

### 8.1 `public.servers`
```sql
CREATE TABLE IF NOT EXISTS public.servers (
  id          BIGSERIAL PRIMARY KEY,
  name        TEXT NOT NULL,
  flag        TEXT NOT NULL DEFAULT '🌐',
  country     TEXT NOT NULL DEFAULT 'Global',
  config      TEXT NOT NULL,                 -- full URI (sensitive)
  host        TEXT DEFAULT '',               -- hostname/IP for landing page
  port        INTEGER DEFAULT 443,
  is_active   BOOLEAN DEFAULT true,
  premium_only BOOLEAN DEFAULT false,
  created_at  TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_servers_is_active     ON public.servers (is_active);
CREATE INDEX IF NOT EXISTS idx_servers_premium_only  ON public.servers (premium_only);
CREATE INDEX IF NOT EXISTS idx_servers_name          ON public.servers (name);
ALTER TABLE public.servers ENABLE ROW LEVEL SECURITY;

-- anon: active non-premium only
CREATE POLICY "Anyone can view active non-premium servers"
  ON public.servers FOR SELECT
  USING (is_active = true AND premium_only = false);
-- authenticated: active non-premium only
CREATE POLICY "Authenticated users can view active non-premium servers"
  ON public.servers FOR SELECT TO authenticated
  USING (is_active = true AND premium_only = false);
-- premium: all active
CREATE POLICY "Premium users can view all active servers"
  ON public.servers FOR SELECT TO authenticated
  USING (is_active = true AND (
    premium_only = false OR
    (current_setting('request.jwt.claims', true)::jsonb
       -> 'app_metadata' ->> 'isPremium')::boolean = true
  ));
```

### 8.2 `public.app_config` (single row)
```sql
CREATE TABLE IF NOT EXISTS public.app_config (
  id              INTEGER PRIMARY KEY DEFAULT 1,
  latest_version  TEXT NOT NULL DEFAULT '1.0.0',
  latest_build    INTEGER NOT NULL DEFAULT 1,
  minimum_version TEXT NOT NULL DEFAULT '1.0.0',
  update_url      TEXT NOT NULL DEFAULT 'https://chobgroup.pages.dev',
  release_notes   TEXT NOT NULL DEFAULT '',
  force_update    BOOLEAN NOT NULL DEFAULT false,
  updated_at      TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT single_row CHECK (id = 1)
);
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Anyone can view app config" ON public.app_config FOR SELECT USING (true);
```

### 8.3 `public.device_tokens`
```
user_id: uuid (references auth.users) NOT NULL
token:   text NOT NULL
platform: text NOT NULL DEFAULT 'android'
UNIQUE (user_id, token)
```

### 8.4 `public.vless_links` (pipeline staging)
```
id: bigserial PK
link: text UNIQUE NOT NULL           -- full vless:// URI
source_channel: text NOT NULL DEFAULT ''
premium_only: boolean NOT NULL DEFAULT false
imported_to_servers: boolean NOT NULL DEFAULT false
created_at: timestamptz DEFAULT now()
CREATE INDEX ... ON vless_links (created_at ASC) WHERE imported_to_servers = false;
```
(plus a `rate_limits` table backing the rate-limit RPC).

### 8.5 Import RPC (`import_pending_vless_links`)
- Selects up to `p_max_links` rows where `imported_to_servers = false` (oldest first).
- For each: parse the URI → `name` (host or fragment), `host`, `port`; skip duplicates by
  `config`; insert into `servers` with `premium_only`; then mark `imported_to_servers = true`.
- Runs via **pg_cron** (e.g. every 15 min) **and** is callable from `POST /import-vless`.

---

## 9. VPN Engine Specification

### 9.1 Engine requirements

The clone must integrate a real Xray/V2Ray-capable core (e.g. `sing-box`, `xray-core`, or a
wrapper like `flutter_vless`) that supports:
- **Protocols:** VLESS, VMess, Trojan, Shadowsocks, WireGuard (at minimum VLESS + VMess
  end-to-end; the others optional for parity).
- **Full TUN mode** (system-wide VPN; all device traffic) and a `proxyOnly`/SOCKS mode (debug).
- **Status callbacks** with states `connected | connecting | disconnected | disconnecting |
  error` plus live **upload/download byte rates**.
- **VPN permission prompt** (Android `VpnService`); connection must abort cleanly if denied.
- Start call: `start(remark: string, configJson: string, proxyOnly: false,
  bypassSubnets: [192.168.0.0/16, 10.0.0.0/8, 172.16.0.0/12, 100.64.0.0/10])`.

### 9.2 Generated Xray JSON (the contract — reproduce verbatim)

```jsonc
{
  "log": { "loglevel": "error", "access": "", "error": "", "dnsLog": false },
  "inbounds": [{
    "tag": "in_proxy",
    "port": 10807,                       // tun2socks forwards here
    "protocol": "socks",
    "listen": "127.0.0.1",
    "settings": { "auth": "noauth", "udp": true, "userLevel": 8 },
    "sniffing": { "enabled": true, "destOverride": ["http","tls","quic"], "metadataOnly": false }
  }],
  "outbounds": [
    { /* proxy outbound — protocol-specific, see 9.3 */ },
    { "tag": "direct", "protocol": "freedom", "settings": { "domainStrategy": "AsIs" } },
    { "tag": "blackhole", "protocol": "blackhole", "settings": {} }
  ],
  "routing": {
    "domainStrategy": "IPIfNonMatch",     // resolve when no domain rule matches
    "rules": /* geo-bypass ON */ [
      { "type": "field", "outboundTag": "direct", "domain": ["geosite:ir"] },
      { "type": "field", "outboundTag": "direct", "ip": ["geoip:ir"] }
    ] /* geo-bypass OFF */ []
  }
}
```
`requestPermission()` must happen **before** building/starting; a denial raises
"VPN permission was denied. Please grant VPN access in system settings."

### 9.3 Per-protocol outbound builders

**VLESS**
```jsonc
{ "tag": "proxy", "protocol": "vless",
  "settings": { "vnext": [{ "address": "<address>", "port": <port>,
    "users": [{ "id": "<uuid>", "encryption": "<encryption or none>",
                "flow": "<extra.flow or omit>" }] }] },
  "streamSettings": <buildStreamSettings(config)> }
```

**VMess** — users `[{ "id": "<uuid>", "security": "<encryption>" }]`; same stream settings.

**Trojan** — `servers: [{ address, port, password: uuid, level: 0 }]`, stream settings with
`security: "tls"`.

**Shadowsocks** — `servers: [{ address, port, method: encryption, password: uuid }]`.

**WireGuard** — peer from `extra` (private_key, local_address, public_key, allowed_ips, dns,
mtu), endpoint from address:port, `AllowedIPs = 0.0.0.0/0, ::/0` (geo-split happens at the
Xray routing layer, not in WG).

### 9.4 `buildStreamSettings(config)` — exact mapping

```
transport = config.transport ?? "tcp"
security  = config.security → "tls" | "reality" | "none"
base: { "network": transport, "security": security }

tls:      tlsSettings = { serverName: sni ?? address, fingerprint: fp ?? "chrome",
                          allowInsecure, alpn: alpn.split(",") if present }
reality:  realitySettings = { serverName: sni ?? address, fingerprint: fp ?? "chrome",
                              publicKey: extra.publicKey, spiderX: "/",
                              shortId: extra.shortId if present }
ws:       wsSettings = { path: transportPath ?? "/", headers: { Host: transportHost ?? address } }
xhttp:    xhttpSettings = { path: transportPath ?? "/", host: transportHost ?? address,
                            mode: extra.mode if present }
grpc:     grpcSettings = { serviceName: transportPath ?? "" }
tcp:      no extra settings
```

### 9.5 Config parsing details (port the parsers)

**VLESS URI** `vless://uuid@host:port?encryption=none&security=tls&sni=x&fp=chrome&type=ws&path=/&host=x&pbk=...&sid=...&flow=...&mode=...`
→ map: `uuid=userInfo, address=host, port (default 443), encryption, security, sni, fp,
allowInsecure (allowInsecure=1 or insecure=1), type→transport, host→transportHost,
path→transportPath, alpn`; `pbk→extra.publicKey, sid→extra.shortId, flow→extra.flow,
mode→extra.mode`.

**VMess link** `vmess://<base64-json>` — strip ONLY the leading `/` from the path, restore
`-`→`+`, `_`→`/`, re-add padding (`%4==2`→`==`, `%4==3`→`=`), decode, map JSON fields:
`id, add/address, port (default 443), security→encryption, tls=="tls"→security:"tls", sni,
net→transport, host→transportHost, path→transportPath, alpn`.

**Trojan URL** `trojan://password@host:port?security=tls&sni=...` — `uuid=password`,
`security ?? "tls"`, `sni ?? host`, `transport: "tcp"`.

**Shadowsocks** `ss://method:password@host:port` — `encryption=method, uuid=password`.

**WireGuard .conf** — parse `[Interface]` (PrivateKey, Address, DNS, MTU) and `[Peer]`
(PublicKey, Endpoint, AllowedIPs); endpoint required; extra map holds the rest.

**NPV** `{"npv": {"protocol": "vless", "config": "vless://..."}}` — recurse on inner config.

### 9.6 Geo split-tunneling (Settings toggle)

- Persisted bool `geo_bypass_domestic` (default **true**).
- When ON: add `geosite:ir → direct` + `geoip:ir → direct` routing rules (Iranian traffic
  bypasses the tunnel; everything else proxies).
- When OFF: no rules → all traffic through the VPN.
- The setting is loaded from storage **at connect time** (in case the user never opens
  Settings this session).

### 9.7 Critical engine edge cases (port these!)

1. **Server switch race:** the previous screen's dispose() may run AFTER the new screen's
   initState already checked "engine initialized". Fix: cache init params; in `connect()`,
   if the engine handle is null but params were saved, **re-initialize automatically**; if the
   handle is still null, throw "Engine not initialized".
2. **Session expiry enforcement:** on the 30-min timer, call the engine `stop()` FIRST
   (fire-and-forget), then emit `SessionExpired`; after 2s → `Idle`.
3. **Manual disconnect ≠ expiry:** the disconnect path cancels timers and goes straight to
   Idle; it must NOT trigger the session-expired path.
4. **Cancel-while-connecting:** suppress the resulting engine error UI via a flag.
5. **Notification throttling:** repost the persistent notification at most every 10s (force
   once on connect), not every 1s tick.
6. **Engine status mapping:** engine `unknown` → state `Error`; `disconnected` after an
   established connection → return to Idle (stay on screen).

---

## 10. Ads, Sessions & Monetization (exact rules)

### 10.1 Unity Ads configuration
```
Android Game ID: 800111592      // iOS Game ID: set per build
placement: "video"              // rewarded video
testMode: false
Firebase Test Lab mode: disableAds
isConfigured = !gameId.startsWith("YOUR_") && gameId.isNotEmpty
```

### 10.2 Ad service semantics
- `isAvailable = isConfigured && sdkInitialized` — computed live.
- Init at boot (non-fatal). Preload a rewarded ad when the Connect screen opens.
- `showRewardedAd() → bool`:
  - ensure initialized + loaded (load on demand if needed);
  - **true only on the "watched to completion" callback**;
  - **false** on skipped / failed / not-loaded / not-initialized;
  - after any show attempt, mark the ad consumed (must reload next time).

### 10.3 The connect gate (implement exactly)
```
isPremium   = JWT app_metadata.isPremium == true
adsAvailable = RewardedAdService.isAvailable

if (isPremium)            → skip ad (log "premium — skipping ad")
else if (!adsAvailable)   → skip ad (log "ads unavailable — skipping gate")   // NO LOCKOUT
else:
    snackbar "Watch an ad to unlock 30 minutes"
    completed = showRewardedAd()
    if (!completed)       → snackbar "Ad was not completed. Try again." ; ABORT
// continue to engine connect
```

### 10.4 Session timer (authoritative enforcement)
- On engine `connected`: `sessionEnd = now + 30 min`.
- **Single-shot `Timer(30 min)`** → `_onSessionExpired()` — the authoritative expiry.
- **Periodic 1s ticker** — updates the countdown UI each second AND acts as a safety net:
  if `now >= sessionEnd` (device slept / timer throttled), cancel the single-shot and force
  expiry on the next tick.
- `_onSessionExpired()`: cancel timers → `engine.stop()` (fire-and-forget, the tunnel must
  actually drop) → emit `SessionExpired` → after 2s emit `Idle`.
- Expiry handler is wired by the screen that owns the engine (callback pattern) so the state
  machine itself stays UI-free.

---

## 11. Security Specification

1. **Certificate pinning:** a custom TLS layer verifies the server's cert SHA-256 against a
   pinned fingerprint for the API host before allowing any connection (implemented via the
   TLS bad-certificate callback). Hosts without pins use standard validation. Pinned host:
   `bprkazfxqmanrybiexnh.supabase.co` with the cert fingerprint stored in the app
   (rotate with the server cert; keep old pins during transitions). Applied to **every** API
   call including push registration.
2. **Root/jailbreak detection** (native platform code): Android checks `su` binary,
   test-keys build, known root packages, dangerous props; iOS checks Cydia URL scheme,
   jailbreak paths, sandbox violations. Exposed via a platform channel as
   `isDeviceCompromised() -> bool`; cache 5 min; on platform error default **false**.
   Warning banners on Login, Settings, and Connect screens.
3. **Replay protection:** `X-Request-Id` uniqueness + `X-Request-Timestamp` on every request;
   server-side validation.
4. **Rate limiting** server-side (IP + user).
5. **No secrets in the client:** only the public Supabase URL + anon key + API URL live in
   the app. Service-role keys, admin keys, and FCM service accounts exist ONLY as server
   environment variables.
6. **Crash reporting:** global error handlers (uncaught UI errors + platform/isolate errors)
   → crash service, release builds only; error-level log lines forwarded as non-fatal events;
   associate `user_id` on login, clear on logout.
7. **Secure storage:** auth tokens/keys via platform keystore-backed storage, never plaintext
   prefs.

---

## 12. Content Pipeline (server ecosystem the clone must reproduce)

### 12.1 Scraper (Python + Telethon)
- Listens to configured Telegram channels; also supports **run-once** mode for cron hosts
  (scan last N messages, forward, exit).
- Extracts VLESS URIs with regex:
  `vless://[A-Za-z0-9_-]+(?:\?[A-Za-z0-9_&=.#%~+,-]+)?(?:#[A-Za-z0-9_ .#%~+,-]+)?`
- Marks a link `premium_only` when the message text contains "premium"/"vip".
- Sends raw message (or pre-extracted links) to the worker webhook with header
  `X-Webhook-Key`; falls back to direct Supabase inserts if the webhook is unavailable.
- Periodic cleanup of links older than 36h.
- Env: `API_ID, API_HASH, TELEGRAM_SESSION, WEBHOOK_URL, WEBHOOK_API_KEY,
  SUPABASE_URL, SUPABASE_KEY, CHANNELS, CLEANUP_INTERVAL, RUN_ONCE,
  RUN_ONCE_MAX_MESSAGES`.

### 12.2 Ingestion worker (serverless)
Endpoints (all behind `X-Webhook-Key`):
- `POST /webhook` — body `{message, source, premium_only?}` → extract/dedupe/validate/store.
- `POST /webhook/batch` — body `{links: [...], source}` or a bare array.
- `POST /cleanup` — body `{max_age_hours?}` (default 36).
- `GET /health` — status + link count.
Validation: must start `vless://` and have an id segment ≥ 20 chars. Dedupe via UNIQUE
constraint + pre-check. Rate-limit inserts (~200ms between Supabase calls).
Env: `SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, WEBHOOK_API_KEY`.

### 12.3 Promotion
`vless_links → servers` via `import_pending_vless_links` RPC: scheduled by pg_cron and
triggerable manually through `POST /import-vless`.

---

## 13. Push Notifications (FCM)

- **Two channels:**
  - Session timer channel `vpn_session_timer` — `importance LOW`, no sound, no vibration,
    `ongoing: true` (cannot be swiped), notification id `42`, BigText style, content
    `"RootNet — HH:MM:SS"`, body `"Server: <name>"`, speeds line `⬆ x  ⬇ y`. Updates
    throttled to every 10s. Cancelled on disconnect.
  - Push channel `push_notifications` — `importance HIGH`, sound + vibration, unique id per
    message.
- **Lifecycle:** on boot → request permission (Android 13+) → get token → register with API
  (only when logged in). Re-register on token refresh AND on every login (token is tied to the
  user). **Unregister before logout.** Listen to: foreground messages (display via local
  notification), background messages (re-init local-notification plugin + display), notification
  taps (deep-link), and cold-start from a terminated notification.
- **Graceful degradation:** on devices without Google Play Services (e.g. Huawei), FCM setup is
  skipped silently; the local notification channels still work.

---

## 14. Build Configuration & Hard-Won Gotchas

> These are REAL failures from the original project. Reproduce the fixed state, not the bugs.

- **Crashlytics Gradle plugin: use `3.0.7`.** Version `3.1.0` does not exist
  (max published is `3.0.7`) — declaring it fails plugin resolution in every repository.
- **Google-Services Gradle plugin: use `4.5.0`.** Crashlytics plugin v3 requires
  **Google-Services ≥ 4.4.1**; `4.4.0` fails at task creation
  (`uploadCrashlyticsMappingFileRelease`: "Crashlytics Gradle plugin 3 requires Google-Services
  4.4.1 and above").
- AGP `9.0.1`, Kotlin `2.3.20`, Gradle wrapper `9.1.0`, compileSdk `36`, minSdk `23`, Java 17
  toolchain, desugar_jdk_libs forced to `2.1.4` across all subprojects (old versions removed
  from Maven).
- **Regional mirror fallbacks (target region blocks Google storage):** declare Google Maven
  first, then Aliyun mirrors (`maven.aliyun.com/repository/google|central|gradle-plugin`),
  then Maven Central. This is why the above version mismatches are fatal instead of just
  slow.
- Package native Xray binaries into the APK (`useLegacyPackaging = true` for jniLibs).
- Release signing from a local `key.properties` (keystore path + aliases + passwords); CI
  falls back to debug signing when absent.
- Unity Ads requires the Game ID as a manifest meta-data; Crashlytics requires the mapping
  upload config present in release builds.

---

## 15. Environment Variables / Secrets (server side)

| Where | Variable | Purpose |
|---|---|---|
| Edge Function | `SUPABASE_URL` | project URL |
| Edge Function | `SUPABASE_SERVICE_ROLE_KEY` | admin DB access (never in the app) |
| Edge Function | `ADMIN_KEY` | `X-Admin-Key` for admin endpoints |
| Edge Function | `FCM_SERVICE_ACCOUNT` | Firebase Admin service-account JSON for FCM v1 |
| Edge Function | `GEOIP_SERVICE_URL` | optional override for geo-api |
| Ingestion worker | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `WEBHOOK_API_KEY` | staging writes |
| Scraper | see 12.1 | Telegram + webhook + storage |
| App (public, safe) | Supabase URL + anon key, API URL, Firebase project ids, deep-link URIs | shipped in the client |

**Do not** ship: service-role keys, admin keys, Firebase service-account JSON, webhook keys.

---

## 16. Definition of Done (acceptance checklist for the clone)

- [ ] Boot: ads init → Supabase auth → Firebase → push → preferences → version check, all
      non-fatal on failure, in this order.
- [ ] Auth: email/password signup, sign-in, password reset, Google OAuth via deep link,
      session survives restart, sign-out returns to login and clears server selection.
- [ ] Server list: fetched from the backend with live TCP pings; free users never see
      premium servers (server + client filtering); premium badge/lock UI.
- [ ] Connect flow (exact order): version gate → ad gate (with the **no-lockout fallback**) →
      VPN permission (fail fast on deny) → config normalize → engine start (full TUN).
- [ ] Connected state: 30-min countdown, speeds, persistent timer notification (≤ 1 update /
      10s), connection-details panel.
- [ ] **Tunnel hard-stops at exactly 00:00** (single-shot timer + 1s safety net), then
      SessionExpired → Idle.
- [ ] Manual disconnect: confirm dialog, timers cancelled, notification removed, state Idle;
      manual disconnect never triggers the expiry path.
- [ ] Cancel-while-connecting: instant, no error snackbar afterwards.
- [ ] Premium user: no ad, unlimited session, premium servers visible, "Premium active" UI.
- [ ] Ads misconfigured/offline: free user still connects (no lockout).
- [ ] Ad skipped/failed: connection aborted with "Ad was not completed" message.
- [ ] Geo-bypass toggle ON/OFF changes the Xray routing rules accordingly.
- [ ] Rooted device: warning banner on Login/Settings/Connect.
- [ ] Update-required: app below `minimumVersion` is blocked by the full-screen page; optional
      update shows an auto-dismissing banner; version check also runs before each connect.
- [ ] Push: token registered after login, unregistered before logout, foreground + background
      messages display, no crash on GMS-less devices.
- [ ] All 5 protocols parse; VLESS and VMess connect end-to-end; WireGuard/Trojan/SS at least
      parse and build valid Xray configs.
- [ ] Crash reports reach the crash service with user-ID association.
- [ ] No secrets in the client bundle (grep the APK for service-role keys).

---

## 17. Operational Notes

- The target region (Iran) blocks several domains; the landing page is served through a
  reverse-proxy worker as a fallback. All production testing of API endpoints should use the
  Supabase Edge Function URL directly.
- Keep the pinned cert fingerprint in sync with the live certificate; regenerate with
  `openssl x509 -fingerprint -sha256` from the live TLS handshake.
- Release cadence: bump `latest_version`/`latest_build`/`minimum_version` in `app_config`
  (DB) — no app redeploy needed for version gates; the app reads it from the API.
- Provide branded auth email templates (signup confirm, reset, magic link, security
  notifications) matching the dark neon theme, sent via custom SMTP (Resend) because the
  free Supabase tier locks template editing on the built-in mailer.

*End of specification — implement to Section 16, then verify against Sections 9–15.*
