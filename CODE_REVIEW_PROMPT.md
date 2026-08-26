# RootNet Project — Comprehensive Code Review Prompt (v2.2 config launcher)

## Project Overview
**RootNet** is a native Android (Kotlin + Jetpack Compose) **config launcher** — it serves
fresh VLESS/V2Ray server configs from Supabase and hands them to the user (**Copy** to the
clipboard, or **Export** into their own client app via `ACTION_VIEW`). It **never** connects,
tunnels, or routes traffic. No accounts, no premium, no JWT.

**Stack:**
- Android: Kotlin, Jetpack Compose (Material 3), OkHttp (`PinnedHttpClient`), Adivery (the ONLY ad network), Crashlytics
- Backend: Supabase (PostgreSQL, Edge Functions — public REST reads only from the app)
- Edge Functions: `rootnet-api` (version-gate + admin routes — auth routes unused by the app), `geo-api` (GeoIP), `telegram-bot` (management bot)
- Workers: `vless-worker` (ingestion webhook), `rootnet-proxy` (landing page)
- Scraper: Python (Telethon) — GitHub Actions on-demand via bot `/scrape`
- CI/CD: GitHub Actions (manual workflow_dispatch)

---

## Architecture Components

### 1. Android App (`rootnet-vpn/android-app/`)
- **Config launcher**: `ServerListScreen` — cache-first server list, live TCP ping, per-row **Copy** + **Export** (combined Adivery interstitial on every 3rd tap, 60s cooldown) + **Refresh** (Adivery rewarded video gate)
- **UI**: Compose screens — `MainShellScreen` (2 tabs: Servers/Settings), `ServerListScreen`, `SettingsScreen`, `UpdateRequiredScreen`
- **Ads**: `AdiveryAdsManager` — interstitial/rewarded/banner, `image_rootnet` / `video_rootnet` / `banner_rootnet`. v2.3 lock rule: required ad unavailable → blur lock gate until a rewarded video is watched to the end
- **Security**: Cert pinning (`PinnedHttpClient`), anti-replay headers, version gating, no client secrets
- **Config**: `ConfigNormalizer` — parsers ONLY (used for ping); Xray JSON builders deleted
- **No engine, no auth**: `domain/` + `vpn/` packages, `AuthRepository`, `SecurePrefs`, `SessionTimer`, FCM push, root detection — all deleted. Do NOT re-add

### 2. Supabase Edge Functions (`rootnet-vpn/supabase/functions/`)
| Function | Purpose |
|----------|---------|
| `rootnet-api` | Version gate (`app_config`), `/import-vless` (admin), legacy JWT routes (unused by the app) |
| `geo-api` | GeoIP lookup with 24h caching |
| `telegram-bot` | Webhook bot: `/scrape`, `/addproxy`, `/listproxy`, `/addchannel`, server CRUD, VPN files |

### 3. Cloudflare Workers (`rootnet-vpn/vless-worker/`, `pages-site/`)
| Worker | Purpose |
|--------|---------|
| `vless-worker` | Ingestion webhook: `POST /webhook`, `/webhook/batch`, `/cleanup`, `/health` — stores `scraped_at` as `created_at` |
| `pages-site` | Static landing page (`rootnet-proxy`) |

### 4. Python Scraper (`vlesshub/vless-scraper/`)
- **Files**: `main.py`, `proxy_pool.py`, `cleanup_chats.py`
- **Pipeline**: Telegram channels → Telethon → extract VLESS/NPV/SIP → POST to vless-worker (with `scraped_at` = Telegram message date) → Supabase `vless_links` → pg_cron `import_pending_vless_links()` → `servers` (created_at preserved)
- **Proxy Pool**: MTProto proxies from `PROXY_CHANNELS`, tested (cap ≤15/run, concurrency ≤4), stored in `scraper_proxies`, rotated on failure
- **Limits**: ≤30 msgs/VLESS channel, ≤1 req/sec avg, dead proxies kept 3 days

### 5. Database Schema (Supabase)
Key tables: `servers` (configs + `created_at` scrape time), `vless_links`, `vpn_files`, `scraper_proxies`, `scraper_config`, `app_config`, `rate_limits`, `bot_chat_state`. Dormant v1 tables (`device_tokens`, `free_connection_quota`, `request_ids`) may exist — verify they're unused before touching.

---

## Review Areas — Check Each Thoroughly

### A. Security & Privacy
1. **No secrets in client** — Verify `AppConstants.kt` has only the public URL + anon key + Adivery placement IDs; no keys in `rootnet-vpn/android-app/.env` / `agent_only.txt` leak into tracked files
2. **Cert pinning** — `PinnedHttpClient.kt`: pins enforced? Backup pins? Cert rotation handled?
3. **RLS policies** — `servers` anon SELECT = `is_active=true` (no premium filter); `app_config` public; `vless_links`/`vpn_files` service_role only
4. **Anti-replay** — `PinnedHttpClient` adds `X-Request-ID` + timestamp; server validates?
5. **Version gate** — `VersionCheckService` runs at startup; below-minimum → `UpdateRequiredScreen`; failure → fail-open (never locks users out)
6. **Ad lock rule** — Every required ad that can't load/show triggers the v2.3 blur lock gate (rewarded video must be watched to the end); no "continue without ad" escape
7. **Session string** — `TELEGRAM_SESSION` never logged, never in repo, only in GitHub Secrets/Supabase Vault

### B. Config & Ad Flows
1. **Config normalization** — `ConfigNormalizer.kt`: parsers (vless, vmess, trojan, ss, socks, wireguard, ssh, sip) produce valid `UnifiedConfig` for ping?
2. **Scrape-time display** — `servers.created_at` flows scraper → worker → import RPC → app (`VpnServer.createdAt` → `🕗 h:mm a` on the card)?
3. **Copy+Export gate** — combined counter → Adivery interstitial on every 3rd tap, 60s app-wide cooldown; action completes on close; opens via clipboard / `ACTION_VIEW`; no-client fallback dialog offers Copy
4. **Reward rule** — ONLY from `onRewardedAdClosed(isRewarded=true)`, never on show/request
5. **Refresh gate** — rewarded video watched to the end before re-fetch; lock gate (v2.3) if the ad can't show
6. **Ping** — `pingServer`: TCP connect to normalized host:port, 5s timeout, -1 = Timeout chip
7. **Cache** — `ServerCacheStore` persists list + `createdAt`; cache-first load, refresh re-fetches

### C. Scraper Correctness & Safety
1. **Message limits** — `RUN_ONCE_MAX_MESSAGES` default 3; cursor read uses `limit=limit`?
2. **Proxy test cap** — ≤10–15 tests/run, concurrency ≤4; priority: newly scraped → untested/failed → stale (>12h)?
3. **Dead proxy retention** — `deactivate()` sets `deactivated_at`; `cleanup_dead_proxies(3)` deletes >3 days old?
4. **NPV/NPVT/SIP extraction** — `extract_from_json_text` handles nested JSON, base64 VMess, nested protocol keys?
5. **FloodWait handling** — `flood_sleep_threshold=60`; `FloodWaitError` caught, slept, not swallowed?
6. **Concurrency** — no overlapping scraper runs (GitHub Actions `concurrency` guard)?
7. **Idempotency** — `vless_links.link` UNIQUE; import marks `imported_to_servers=true` on success/duplicate/parse-fail; `created_at` carried into `servers`?
8. **Scrape timestamps** — webhook payloads include `scraped_at`; worker + direct-Supabase paths both honor it; `vpn_files.uploaded_at` stamped from message date?

### D. Bot & Admin UX
1. **Rate-limit warnings** — `/scrape` <5min ago: shows wait time + confirm/cancel buttons?
2. **Running check** — GitHub API queries `in_progress`/`queued` runs for `scrape.yml`?
3. **Server CRUD** — delete always confirms; no premium/normal split (removed 2026-08-15 — every server is public)
4. **VPN Files** — list/download/delete works; `uploaded_at` shown where relevant
5. **SIP support** — Bot parser, Worker (`isValidConfigLink`/`extractConfigLinks`), App (`ConfigNormalizer.fromSip`) all aligned?
6. **Chat state** — `pendingScrapeConfirm` cleared on confirm/cancel/timeout?

### E. Database & Migrations
1. **Migrations order** — Check `rootnet-vpn/supabase/migrations/` sequence; no destructive changes without backup
2. **Indexes** — `scraper_proxies_active_idx (is_active, last_ok)`, `servers` queries covered
3. **Triggers/cron** — `import_pending_vless_links` pg_cron every 30min; grants = service_role only (REVOKEd from PUBLIC/anon/authenticated)
4. **Config formats** — `servers.config_format` accepts `link|json|npv|conf|sip`; `type` mapped
5. **RLS** — `scraper_config`, `scraper_proxies`, `vpn_files` have RLS + no policies (service_role only)

### F. Workers & Edge Functions
1. **Webhook auth** — `X-Webhook-Key` validated on all `/webhook*`, `/cleanup`
2. **CORS** — Proper headers on all endpoints
3. **Rate limiting** — `rootnet-api`: IP-based via `checkIpRateLimit` RPC
4. **Error handling** — No stack traces leaked; structured JSON errors
5. **Import RPC** — `import_pending_vless_links` propagates `created_at` (scrape time) into `servers`; no premium flag (removed 2026-08-15)

### G. Build & Deploy
1. **Gradle** — `android-app`: AGP/Kotlin versions current? `compileDebugKotlin` passes? APK stays ~1.8 MB (no jniLibs, no material-icons-extended)?
2. **Python** — `vless-scraper`: `main.py`/`proxy_pool.py` syntax OK? `requirements.txt` pinned?
3. **Node** — `rootnet-vpn/vless-worker/src/index.js` syntax OK? `wrangler.toml` bindings correct?
4. **GitHub Actions** — `scrape.yml`: `concurrency` group, `timeout-minutes: 10`, sane message limits
5. **Secrets** — All required secrets documented (API_ID, API_HASH, TELEGRAM_SESSION, CHANNELS, PROXY_CHANNELS, WEBHOOK_URL, WEBHOOK_API_KEY, SUPABASE_URL, SUPABASE_KEY, BOT_TOKEN, GH_PAT, ADMIN_KEY, ADMIN_IDS); none hardcoded in tracked files

### H. AGENTS.md Compliance (Critical)
1. **Warn-first rule** — Any action that could ban `@rootnet_vpn_manager` or exhaust quotas requires explicit user confirmation
2. **Telegram safety budget** — ≤30 msgs/channel, ≤5–8 channels/run, ≤1 req/sec avg, ≤10–15 proxy tests/run, ≤4 concurrency
3. **GitHub Actions budget** — Private repo: 2000 min/mo; cron OFF; manual `/scrape` only; run ≤2 min
4. **Supabase budget** — ≤20 REST calls/scraper run; webhook inserts spaced 200ms
5. **Session protection** — `TELEGRAM_SESSION` never regenerated; same StringSession reused across proxy rotations
6. **No user-account sends** — Bot uses `BOT_TOKEN` for reports; scraper never sends messages

---

## Known Patterns to Verify

### Good Patterns (Should Exist)
- ✅ Cache-first server list (SharedPreferences → Supabase refresh)
- ✅ Configs ALWAYS from the DB (never hardcoded)
- ✅ Ad gating with v2.3 lock rule (Adivery — lock gate, never silent bypass)
- ✅ Deduplication at Worker + DB UNIQUE constraint
- ✅ Proxy pool with test-on-demand, rotation on failure
- ✅ pg_cron for automatic import (no manual step)
- ✅ Scrape timestamps threaded end-to-end (scraper → worker → import → app)
- ✅ Cert pinning with backup pins
- ✅ Structured logging (no secrets in logs)

### Anti-Patterns (Should NOT Exist)
- ❌ Hardcoded configs/servers in app
- ❌ Any re-added engine/auth code (`domain/`, `vpn/`, `SecurePrefs`, `SessionTimer`, FCM)
- ❌ Secrets in repo or client code (e.g. `SUPABASE_REFERENCE.md` style leaks)
- ❌ AdMob / Unity Ads (Adivery is the ONLY network since v2.2)
- ❌ No-lockout bypass (removed in v2.3 — a required ad that can't show must lock, not pass)
- ❌ Blocking/long-running operations on UI thread
- ❌ Unbounded proxy tests or message fetches
- ❌ Scraper cron < 60 min (GitHub Actions quota)
- ❌ Multiple concurrent scraper instances
- ❌ User-account Telegram messages (ban risk)

---

## Output Format for Reviewer

Report findings as:

```
### [SEVERITY] Component: File:Line — Description
**Issue:** What's wrong
**Risk:** Security/Functional/Performance/Compliance
**Fix:** Specific code change or migration needed
**References:** AGENTS.md section, related files
```

Severity: 🔴 Critical (ban/data loss/security) | 🟠 High (functional break) | 🟡 Medium (degradation) | 🔵 Low (style/docs)

---

## Files to Prioritize (High Impact)
1. `rootnet-vpn/android-app/app/src/main/java/com/chobgroup/rootnet/data/ads/AdiveryAdsManager.kt`
2. `rootnet-vpn/android-app/app/src/main/java/com/chobgroup/rootnet/data/remote/PinnedHttpClient.kt`
3. `rootnet-vpn/android-app/app/src/main/java/com/chobgroup/rootnet/ui/screens/ServerListScreen.kt`
4. `rootnet-vpn/supabase/functions/rootnet-api/index.ts` + `_rate-limit.ts`
5. `rootnet-vpn/supabase/functions/telegram-bot/_handlers.ts` + `_db.ts` + `_parser.ts`
6. `rootnet-vpn/vless-worker/src/index.js`
7. `vlesshub/vless-scraper/main.py` + `proxy_pool.py`
8. `rootnet-vpn/supabase/migrations/20260813000002_propagate_scraped_at_to_servers.sql`
9. `.github/workflows/scrape.yml`
10. `AGENTS.md` (as compliance checklist)

---

## Test Scenarios to Mentally Simulate
1. Fresh install → version gate OK → server list loads (cache-first) → rows show name/flag/protocol/ping + 🕗 scrape time
2. Tap Copy → 3rd-tap interstitial (or in-cooldown, no ad) → config on clipboard
3. Tap Export → shares the combined 3rd-tap interstitial → config opens in v2rayNG; no client installed → "No app found" dialog with Copy fallback
4. Refresh → rewarded video watched to end → list re-fetches from Supabase
5. Ads unavailable (no fill/offline) → blur lock gate; rewarded video watched to end unlocks the action
6. Scraper runs → new links get `created_at` = Telegram message date → import preserves it → app shows correct local time
7. Scraper running → admin presses `/scrape` → "already running" message
8. Proxy fails → deactivated → not re-tested for 12h → cleaned after 3 days
9. SIP config uploaded via bot → parsed → stored as `config_format=sip` → app normalizes for ping
10. Network error → cert pinning fails → graceful fallback (never crash, never lock out)

---

**Reviewer:** Run this checklist systematically. Flag any deviation from AGENTS.md, security best practices, or the patterns above. Prioritize 🔴/🟠 findings.
