# 🗺️ RootNet — Product Roadmap

## 🚀 V2.0 — CONFIG LAUNCHER (2026-08-13, DONE)

> ⚠️ **Major pivot:** RootNet v2.0 is a **config launcher**, not a VPN client.
> The built-in Xray engine, auth/premium, session timer, and FCM push are **gone**.

- [x] **Engine removed** — `domain/` + `vpn/` packages deleted; no tunnel, no daemon process, no geo-routing
- [x] **Auth removed** — no login, no guest mode, no premium tiers; app opens straight to the config list
- [x] **Monetization reworked (v2.2)** — Adivery is the ONLY network: Copy gated by interstitial, Export + Refresh gated by rewarded video, persistent banner; no-lockout fallback kept
- [x] **Config list** — cache-first, live refresh, TCP ping; per-server **Copy** / **Export** (opens in the user's default client: v2rayNG, NekoBox, Hiddify…)
- [x] **Version gate kept** — 2.0.0/101 (migration `20260813000001` + `rootnet-api` fallback updated)
- [x] **Websites + docs + skills** updated to the new model
- [ ] Adivery placements verified filling on-device (banner/interstitial/rewarded confirmed loading 2026-08-13)

> See `app_clone.md` §0 (v2.0 supersession) for the authoritative spec.

---

## 📅 Pre-v2.0 (original VPN model — history)

> Combined project home for the **RootNet VPN stack** (3 components that work together):
>
> | # | Component | Path | What it does |
> |---|-----------|------|--------------|
> | 1 | **RootNet app** | `lib/` (+ `android/`, `ios/`, ...) | Flutter VPN client (VLESS/V2Ray) |
> | 5 | **VLESS Ingestion API** | `rootnet-vpn/vless-worker/` | Cloudflare Worker — receives scraped links, stores in Supabase `vless_links` |
> | 6 | **VLESS Telegram Scraper** | `vlesshub/vless-scraper/` | Python (Telethon) bot — watches Telegram channels, extracts VLESS links |
>
> **Pipeline:** Telegram channels → scraper (`vlesshub/vless-scraper/`) → ingestion worker (`rootnet-vpn/vless-worker/`) → Supabase `vless_links` → import endpoint (`rootnet-api` `/import-vless`) → `servers` table → RootNet app.
>
> Last updated: August 5, 2026

---

## ✅ Done (Shipped)

### App (RootNet Flutter)
- [x] Email/password auth (Supabase) + password reset
- [x] Google Sign-In (Supabase OAuth — login + signup screens, deep-link callback)
- [x] Session persistence / auto-login
- [x] Server list with 15+ VLESS servers (DB-managed, no hardcoded configs)
- [x] Ping/latency testing + "Ping All" auto-sort
- [x] VLESS/V2Ray connection engine (`flutter_vless`) with Reality + xhttp support
- [x] 30-min ad-gated session timer (DateTime-based, no drift)
- [x] Persistent Android timer notification with live speeds
- [x] Real-time upload/download speed display
- [x] Version gating (blocks outdated builds, update banner)
- [x] Device integrity warning (root/jailbreak detection — Android native + iOS native)
- [x] Certificate pinning on HTTP API client (`certificate_pinning.dart`)
- [x] FCM push notifications (`push_service.dart` — token register/unregister, foreground/background handlers)
- [x] Real rewarded ads via Unity Ads (`unity_ads_plugin`, `UnityAdsService`) with graceful fallback when the SDK isn't configured — no VPN lockout
- [x] **Session expiry enforcement** — engine is hard-stopped at 30 min (single-shot timer + 1s safety net), and manual disconnects don't trip it
- [x] **JWT-based premium gating** — premium = signed `app_metadata.isPremium` (no forgeable local flag); premium servers filtered server-side + in UI, no ads for premium users
- [x] **Throttled persistent notification** — timer notification updates ~10s instead of 1/s
- [x] **Crash reporting** — `firebase_crashlytics` wrapper (`crashlytics_service.dart`), user-ID association, global error handlers in `main.dart`
- [x] Clean Architecture + Riverpod migration
- [x] Branded animated logo + launcher icons (all platforms)
- [x] Premium cyber-organic UI theme
- [x] Privacy policy link on auth screens (Play Store requirement)

### Backend (Supabase Edge Functions)
- [x] `rootnet-api` — servers, version, device tokens, push, GeoIP proxy, health
- [x] `geo-api` — production-grade GeoIP (cache, circuit breaker, retry, fallback)
- [x] Postgres-backed rate limiting (RPC)
- [x] DB schema + RLS + seed data (servers, app_config, device_tokens, vless_links)

### Pipeline (scraper + worker)
- [x] `vlesshub/vless-scraper/` — event-driven Telethon listener, webhook + direct-Supabase fallback, FloodWait handling, auto-cleanup (36h)
- [x] `rootnet-vpn/vless-worker/` — webhook ingestion, dedup, validation, cleanup endpoint
- [x] `vless_links` table + helper functions (age, cleanup, active links)
- [x] **Pipeline integration** — `rootnet-api` `POST /import-vless` promotes scraped links into the `servers` table (admin key, idempotent)
- [x] **Auto-import scheduler** — pg_cron job `import-vless-every-30min` runs `import_pending_vless_links()` automatically every 30 min (migration `20260803000002`); the `/import-vless` endpoint now calls the same DB RPC
- [x] **Production deploy (2026-08-03)** — `rootnet-api` Edge Function deployed, all 12 migrations applied (`npx supabase db push`), `vless-ingestion-api` worker deployed with secrets, `ADMIN_KEY` set. Pipeline live: Telegram → worker → `vless_links` → pg_cron → `servers` → app
- [x] **Free scraper hosting** — `.github/workflows/scrape.yml` runs the scraper in `RUN_ONCE` mode every 30 min on GitHub Actions ($0, no credit card). Gracefully skips until the Telegram credentials are added as repo secrets; overlap between runs is deduped by the worker

### Site (Cloudflare Pages)
- [x] Chob Group hub (`/`), RootNet page (`/rootnet.html`), GeoIP tool (`/geoip.html`), privacy policy

---

## 🔄 In Progress

- [ ] **Unity Ads iOS Game ID** — Android Game ID is set (`800111592` in `lib/services/ads_config.dart`). The iOS Game ID is still a placeholder (`YOUR_IOS_GAME_ID`) — not needed for Android builds. Verify the `video` rewarded placement exists in the Unity dashboard (testMode is off).

## 💳 Monetization direction

- **Per-action Adivery ads** (the only network): interstitial on every 3rd Copy/Export tap, rewarded video before Refresh, persistent banner. Ads unavailable → the action still completes (no lockout).
- **No premium, no accounts** — the `premium_only` flag was removed end-to-end (2026-08-15); every config is public.
- The publishing plan is documented in [PUBLISH_PLAN.md](PUBLISH_PLAN.md) for future agents and release planning.

---

## 🔜 Next Up (code ready to build)

- [ ] **App store listings** — Google Play listing (description, screenshots, content rating, data-safety form). VPN apps need Google's VPN permission declaration + special approval.
- [x] **APK download link** — **v1.1.2 release APK published** (2026-08-03):
  - GitHub Release: https://github.com/ahmad43ir/rootnet/releases (asset `app-release.apk`, 84.8 MB, signed with the real upload keystore)
  - `pages-site/rootnet.html` `#downloadBtn` → `https://github.com/ahmad43ir/rootnet/releases/latest/download/app-release.apk` (auto-resolves to the newest asset named `app-release.apk`)
  - ⚠️ CI tag builds still need the `GOOGLE_SERVICES_JSON` repo secret (see `ci.yml`) — v1.1.2 was uploaded manually from a locally built, properly-signed APK. Add the secret so future tags auto-attach.
- [ ] **Error tracking** — optional Sentry alternative if Crashlytics is undesirable.

---

## 🧪 Nice-to-Have / Future

- [ ] Apple Sign-In (Supabase supports it; add button alongside Google)
- [ ] Notification tap → navigate to specific screen (data payload routing)
- [ ] Land on `/servers` — dedupe with `source_channel` badges ("Community")
- [x] CI/CD — GitHub Actions: `flutter analyze` + `flutter test` on push/PR, release APK on tags (`ci.yml`) (needs `GOOGLE_SERVICES_JSON` secret for tag builds)
- [ ] E2E widget tests for auth + server list flows

---

## 🔐 Requires External Accounts / Credentials (blocked on you)

| Item | What's needed | Where |
|------|---------------|-------|
| Real Unity Ads | ✅ Android Game ID set (`800111592`); iOS ID + `video` placement verification pending | `lib/services/ads_config.dart` |
| FCM working on device | `google-services.json` in `android/app/` | `android/app/google-services.json` (gitignored) |
| FCM server push | ✅ `FCM_SERVICE_ACCOUNT` secret set (key saved to `credentials/firebase-adminsdk.json`, gitignored) | Edge Function secret |
| Play Store release | Play Console account, keystore (`upload-keystore.jks` exists) | Play Console |
| APK hosting | ✅ Done — GitHub Releases (v1.1.2 `app-release.apk`) | `pages-site/rootnet.html` |
| Telegram session | API_ID / API_HASH / StringSession (member of target channels) — add as **GitHub Actions repo secrets** when ready | `vlesshub/vless-scraper/.env` + repo secrets |
| Worker webhook URL | ✅ `vless-worker` deployed — `WEBHOOK_URL` + `WEBHOOK_API_KEY` already in `vlesshub/vless-scraper/.env` | scraper `.env` |
| Scraper hosting | ✅ Free GitHub Actions cron — `.github/workflows/scrape.yml` ready; just push the repo + add 8 secrets (see `vlesshub/vless-scraper/README.md`) | `vlesshub/vless-scraper/` |

---

## 🧭 Deployment Quick Reference

```bash
# App
cd RootNet && flutter run                      # dev
cd RootNet && flutter build apk --release      # release APK

# Edge functions
cd RootNet/supabase
npx supabase functions deploy rootnet-api --no-verify-jwt
npx supabase functions deploy geo-api --no-verify-jwt

# Ingestion worker (Cloudflare)
cd RootNet/vless-worker
npx wrangler secret put SUPABASE_URL
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
npx wrangler secret put WEBHOOK_API_KEY
npx wrangler deploy

# Scraper (free — GitHub Actions cron, run-once every 30 min)
# Push RootNet/ to GitHub → workflow .github/workflows/scrape.yml runs it.
# Add repo secrets: API_ID, API_HASH, TELEGRAM_SESSION, CHANNELS,
# WEBHOOK_URL, WEBHOOK_API_KEY, SUPABASE_URL, SUPABASE_KEY.
cd RootNet/vless-scraper
# local test of the same run-once mode:
#   RUN_ONCE=1 python main.py  (persistent listener: python main.py)

# Site
cd RootNet/pages-site
npx wrangler pages deploy . --project-name chobgroup --branch main
```

---

## 📌 Conventions (from PROJECT_CONTEXT.md)

- Never hardcode secrets — all config in env vars / `agent_only.txt` (gitignored)
- VLESS configs live ONLY in Supabase DB — never in app or worker code
- Keep code simple; do not over-engineer
- Do NOT rewrite existing auth logic or change the UI theme
