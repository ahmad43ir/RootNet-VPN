# RootNet + ProxyBox Monorepo — Comprehensive Code Review Prompt

## Project Overview
**RootNet** is a native Android VPN app (Kotlin + Jetpack Compose) using Xray/VLESS protocol with Supabase backend, Cloudflare Workers, and a Python Telegram scraper.

**ProxyBox** is a companion Android app that serves 10 random working MTProto proxies from the shared `scraper_proxies` pool, monetized with AdMob.

Both apps share:
- Supabase project (`bprkazfxqmanrybiexnh`)
- `scraper_proxies` pool (maintained by RootNet scraper)
- Same toolchain (AGP 9.0.1, Kotlin 2.3.20, Compose BOM 2026.06.01)
- Regional mirrors (Aliyun for target region)

---

## Architecture Components

### 1. Android Apps (`android-app/`, `proxybox-app/`)
| App | Purpose | Key Files |
|-----|---------|-----------|
| **RootNet** | VPN client (VLESS/VMess/Trojan/SS/WG/SOCKS/SIP) | `android-app/app/src/main/java/com/chobgroup/rootnet/` |
| **ProxyBox** | Free MTProto proxy list (10 random per batch) | `proxybox-app/app/src/main/java/com/chobgroup/proxybox/` |

### 2. Supabase Edge Functions (`supabase/functions/`)
| Function | Purpose |
|----------|---------|
| `rootnet-api` | Main API: `/servers`, `/version`, `/free-connection`, `/import-vless` |
| `geo-api` | GeoIP lookup with 24h caching |
| `telegram-bot` | Webhook bot: `/scrape`, `/addproxy`, server CRUD |
| `proxy-api` | Public GET `/proxies` — 10 random MTProto proxies for ProxyBox |

### 3. Cloudflare Workers (`vless-worker/`, `pages-site/`)
| Worker | Purpose |
|--------|---------|
| `vless-worker` | Ingestion webhook: `POST /webhook`, `/webhook/batch`, `/cleanup` |
| `pages-site` | Static landing page (`rootnet-proxy`) |

### 4. Python Scraper (`vless-scraper/`)
- `main.py` — Telethon listener, extracts VLESS/NPV/SIP configs, POSTs to vless-worker
- `proxy_pool.py` — MTProto proxy pool: collect from channels, test (cap 3/run), rotate
- `cleanup_chats.py` — Utility to delete placeholder chats

### 5. Database (`supabase/migrations/`)
Key tables: `servers`, `vless_links`, `scraper_proxies`, `scraper_config`, `app_config`, `device_tokens`, `free_connection_quota`, `rate_limits`, `bot_chat_state`, `request_ids`

---

## Review Areas — Check Each Thoroughly

### A. Security & Privacy
1. **No secrets in clients** — Verify `AppConstants.kt` (both apps) has no hardcoded keys; all via env/Worker bindings
2. **Cert pinning** — `PinnedHttpClient.kt`: pins enforced? Backup pins? Cert rotation handled?
3. **JWT handling** — `SupabaseAuthRepository`: token refresh, expiry, secure storage (Keystore)?
4. **RLS policies** — All tables have RLS enabled; only `service_role` bypasses; anon keys only read `servers` (premium filtered)
5. **Anti-replay** — `PinnedHttpClient` adds `X-Request-Id` + timestamp; `rootnet-api` + `proxy-api` validate via `request_ids` table (60s TTL)?
6. **Root detection** — `RootDetectionService`: multiple checks (su, magisk, test-keys); blocks VPN on rooted?
7. **Premium gating** — Server-side (`isPremiumUser` in `_auth.ts`) + client UI filter (`filterServersForAccess`) — both enforced?
8. **Session string** — `TELEGRAM_SESSION` never logged, never in repo, only in GitHub Secrets/Supabase Vault

### B. VPN Engine & Networking (RootNet)
1. **Config normalization** — `ConfigNormalizer.kt`: all schemes (vless, vmess, trojan, ss, socks, wireguard, ssh, sip) produce valid `UnifiedConfig`?
2. **Xray JSON contract** — `buildXrayConfig` / `buildProxyOutbound`: matches Xray-core expectations? Stream settings (ws/grpc/xhttp/httpupgrade/splithttp/tcp + tls/reality)?
3. **tun2socks pipeline** — `VpnRuntime` / `XrayCoreManager`: SOCKS inbound 127.0.0.1:10807 → Xray → TUN? DNS handling?
4. **Session timer** — `SessionTimer`: 30/60 min free, premium bypass, ad-gate post-connect, free-connection fallback (2/24h)?
5. **Ping** — `ServerListScreen.pingServer`: TCP connect to normalized host:port, 5s timeout, cached?
6. **Protocol display** — ServerCard shows `protocol: ${configFormat.displayName}` (VLESS/NPV/SIP/etc.)

### C. ProxyBox Specific
1. **Proxy fetch** — `ProxyApi.fetchProxies()`: 15s timeout, 1 retry, parses `tg://proxy` links correctly?
2. **AdMob** — `AdManager`: banner persistent, interstitial throttled (1/min), test IDs until real ones configured?
3. **Proxy cards** — Copy/Share/Open in Telegram work? `tg://` fallback to `https://t.me/proxy?`?
4. **Pool status** — Shows `working/total` from API response?

### D. Scraper Correctness & Safety
1. **Message limits** — `RUN_ONCE_MAX_MESSAGES=3` (VLESS), `messages_per_channel=1` (proxy); cursor read uses `limit=limit`?
2. **Proxy test cap** — `MAX_TEST_PER_RUN=3`; priority: newly scraped → untested/failed → stale (>12h)?
3. **Dead proxy retention** — `deactivate()` sets `deactivated_at`; `cleanup_dead_proxies(3)` deletes >3 days old?
4. **Skip dead in test list** — `refresh_pool` filters `is_active=False`?
5. **NPV/NPVT/SIP extraction** — `extract_from_json_text` / `extractSip` handle nested JSON, base64 VMess, nested protocol keys?
6. **FloodWait handling** — `flood_sleep_threshold=60`; `FloodWaitError` caught, slept, not swallowed?
7. **Concurrency** — `test_proxies` semaphore=4; no overlapping scraper runs (GitHub Actions `concurrency: vless-scraper`)?
8. **Idempotency** — `vless_links.link` UNIQUE; `import_pending_vless_links` marks `imported_to_servers=true` on success/duplicate/parse-fail?

### E. Bot & Admin UX
1. **Rate-limit warnings** — `/scrape` <5min ago: shows wait time + confirm/cancel buttons?
2. **Running check** — GitHub API queries `in_progress`/`queued` runs for `scrape.yml`?
3. **Premium toggle** — ServerList: premium users switch Free/Premium via Material3 Switch?
4. **Protocol badge** — ServerCard shows `protocol: ${configFormat.displayName}`?
5. **SIP support** — Bot parser (`parseFile` → `extractSip`), Worker (`isValidConfigLink`/`extractConfigLinks`), App (`ConfigNormalizer.fromSip`) all aligned?
6. **Chat state** — `pendingScrapeConfirm` cleared on confirm/cancel/timeout?

### F. Database & Migrations
1. **Migrations order** — Check `supabase/migrations/` sequence; no destructive changes without backup?
2. **Indexes** — `scraper_proxies_active_idx (is_active, last_ok)`, `servers` queries covered?
3. **Triggers/cron** — `import_pending_vless_links` pg_cron every 30min; `claim_free_connection` RPC?
4. **Config formats** — `servers.config_format` accepts `link|json|npv|conf|raw|sip` (migration 20260810000001)?
5. **RLS** — `scraper_config`, `scraper_proxies` have RLS + no policies (service_role only)?
6. **CHECK constraints** — `config_format_valid` on `servers` (migration 20260810000004)?

### G. Workers & Edge Functions
1. **Webhook auth** — `X-Webhook-Key` validated on all `/webhook*`, `/cleanup`?
2. **CORS** — Proper headers on all endpoints?
3. **Rate limiting** — `rootnet-api`: IP (60/min) + user (30/min) via `checkIpRateLimit` RPC? `proxy-api`: IP (60/min)?
4. **Error handling** — No stack traces leaked; structured JSON errors?
5. **Import RPC** — `import_pending_vless_links` inserts as `premium_only=true` (scraped → premium)?
6. **Anti-replay** — `validateAntiReplay()`: timestamp ±30s + `request_ids` table dedup (60s TTL)?

### H. Build & Deploy
1. **Gradle** — Both apps: AGP/Kotlin versions current? `compileDebugKotlin` passes?
2. **Python** — `vless-scraper`: `main.py`/`proxy_pool.py` syntax OK? `requirements.txt` pinned?
3. **Node** — `vless-worker/src/index.js` syntax OK? `wrangler.toml` bindings correct?
4. **GitHub Actions** — `scrape.yml`: `concurrency` group, `timeout-minutes: 10`, `RUN_ONCE_MAX_MESSAGES=3`?
5. **Secrets** — All required secrets documented (API_ID, API_HASH, TELEGRAM_SESSION, CHANNELS, PROXY_CHANNELS, WEBHOOK_URL, WEBHOOK_API_KEY, SUPABASE_URL, SUPABASE_KEY, BOT_TOKEN, GH_PAT, ADMIN_KEY, ADMIN_IDS)?

### I. AGENTS.md Compliance (Critical)
1. **Warn-first rule** — Any action that could ban `@rootnet_vpn_manager` or exhaust quotas requires explicit user confirmation?
2. **Telegram safety budget** — ≤30 msgs/channel, ≤5-8 channels/run, ≤1 req/sec avg, ≤10-15 proxy tests/run, ≤4 concurrency?
3. **GitHub Actions budget** — Private repo: 2000 min/mo; cron OFF; manual `/scrape` only; run ≤2 min?
4. **Supabase budget** — ≤20 REST calls/scraper run; webhook inserts spaced 200ms?
5. **Session protection** — `TELEGRAM_SESSION` never regenerated; same StringSession reused across proxy rotations?
6. **No user-account sends** — Bot uses `BOT_TOKEN` for reports; scraper never sends messages?

---

## Known Patterns to Verify

### Good Patterns (Should Exist)
- ✅ Cache-first server list (SharedPreferences → Supabase fallback)
- ✅ Config normalization before Xray (never pass raw URI to engine)
- ✅ Deduplication at Worker + DB UNIQUE constraint
- ✅ Proxy pool with test-on-demand, rotation on failure
- ✅ pg_cron for automatic import (no manual step)
- ✅ Free-connection quota server-side (RPC, not client-trusted)
- ✅ Premium gating both server + client
- ✅ Cert pinning with backup pins
- ✅ Structured logging (no secrets in logs)

### Anti-Patterns (Should NOT Exist)
- ❌ Hardcoded configs/servers in app
- ❌ Raw URI passed to Xray without normalization
- ❌ Secrets in repo or client code
- ❌ Client-side premium enforcement only
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
1. `android-app/app/src/main/java/com/chobgroup/rootnet/config/ConfigNormalizer.kt`
2. `android-app/app/src/main/java/com/chobgroup/rootnet/data/remote/PinnedHttpClient.kt`
3. `android-app/app/src/main/java/com/chobgroup/rootnet/vpn/XrayCoreManager.kt`
4. `supabase/functions/rootnet-api/index.ts` + `_auth.ts` + `_rate-limit.ts`
5. `supabase/functions/proxy-api/index.ts`
6. `supabase/functions/telegram-bot/_handlers.ts` + `_db.ts` + `_parser.ts` + `_state.ts`
7. `vless-worker/src/index.js`
8. `vless-scraper/main.py` + `proxy_pool.py`
9. `supabase/migrations/20260803000002_add_vless_import_rpc_and_cron.sql`
10. `.github/workflows/scrape.yml`
11. `proxybox-app/app/src/main/java/com/chobgroup/proxybox/data/ProxyApi.kt`
12. `proxybox-app/app/src/main/java/com/chobgroup/proxybox/ui/HomeScreen.kt`
13. `proxybox-app/app/src/main/java/com/chobgroup/proxybox/ads/AdManager.kt`
14. `AGENTS.md` (as compliance checklist)

---

## Test Scenarios to Mentally Simulate
1. Fresh install → no cache → fetches servers → shows protocol badges → premium toggle works
2. Free user connects → ad loads → 30 min timer starts → ad-gate on reconnect
3. Premium user → no ads, unlimited, sees all servers
4. Scraper runs → 3 msgs/VLESS channel → 1 msg/proxy channel → 3 proxy tests (new first) → dead marked → 3-day retention
5. Admin presses `/scrape` twice in 2 min → warned, can confirm
6. Scraper running → admin presses `/scrape` → "already running" message
7. Proxy fails → deactivated → not re-tested for 12h → cleaned after 3 days
8. SIP config uploaded via bot → parsed → stored as `config_format=sip` → app normalizes to SOCKS
9. Network error → cert pinning fails → connection blocked, not degraded
10. Root detected → VPN blocked, user informed
11. ProxyBox user taps "Get 10 proxies" → interstitial (if >1 min) → fetches 10 MTProto → cards work
12. ProxyBox refresh → throttled interstitial → new batch

---

**Reviewer:** Run this checklist systematically. Flag any deviation from AGENTS.md, security best practices, or the patterns above. Prioritize 🔴/🟠 findings.