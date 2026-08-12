# RootNet Clone — Build Progress Checklist

> Track implementation against the full spec in **`app_clone.md`** (section numbers referenced).
> Tick boxes as you complete each item. All unchecked = not started.

**Target stack:** ✅ **Kotlin/Jetpack Compose** — project at `android-app/` (app id `com.chobgroup.rootnet`)
**Status legend:** `[ ]` todo · `[x]` done · `[~]` in progress

---

## Phase 0 — Setup & Toolchain
- [x] Project scaffolded (Android minSdk 23, app id `com.chobgroup.rootnet`) — `android-app/`
- [x] Build config: AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, compileSdk 36, Java 17 (§14)
- [x] Maven repos: Google + Maven Central; **Aliyun mirrors REQUIRED in this region (dl.google.com 404s)** — mirror listed FIRST in `dependencyResolutionManagement` (§14) — `:app:assembleDebug` green
- [x] Crashlytics/Google-Services gradle plugins applied — `3.0.7` / `4.5.0` (§14)
- [x] Xray/V2Ray core integrated (TUN support, native binaries packaged) (§9.1) — **real Xray engine ported + built (APK 225 MB, 4 ABIs, geoip/geosite assets)**
- [x] Theme tokens implemented (`#0B1A12` bg, `#4CFF88` accent, glass cards, spirograph) (§5.0)
- [x] Secure storage + preferences wired — **real Keystore AES/GCM `SecurePrefs`** (alias `rootnet_auth_keystore_key`) (§2/§8)

## Phase 1 — Foundation
- [x] Data models: `VpnServer`, `UnifiedConfig`, `VersionInfo` (§6)
- [x] Connection state machine (sealed: Idle/Connecting/Connected/Disconnecting/Error/SessionExpired) (§6.3)
- [x] Config normalizer: auto-detect + parse link/json/npv/conf/raw (§9.5) — 9/9 unit tests green
- [x] `buildStreamSettings` + full Xray JSON builder: tcp/ws/xhttp/grpc + tls/reality (§9.2–9.4)
- [x] Pinned HTTPS client + retry/backoff — **real `PinnedHttpClient`** (CertificatePinner on API host, anti-replay headers, 2× retry, 500ms backoff) (§1/§8)

## Phase 2 — Auth
- [x] **Real Supabase auth** — email/pw sign-in + signup (email confirmation), session persisted + auto-refresh, **premium from signed JWT `app_metadata.isPremium` only** (§2/§8)
- [x] Google OAuth — PKCE browser flow + deep link `com.chobgroup.rootnet://callback` (needs Supabase redirect + Google client config)
- [x] Password reset email — real Supabase `/auth/v1/recover` (Forgot password link on login)
- [x] Session persistence across restarts (SharedPreferences); `isPremium` decoded from stored JWT
- [x] Sign-out: Supabase `/logout` (best-effort) + clears session → back to login (§5.5)

## Phase 3 — Screens
- [x] Login / Signup themed UI (+ privacy policy link) — auth wiring in Phase 2 (§5.1)
- [x] Main shell: 3 tabs (VPN / Servers / Profile) + placeholder + server selection (§5.2)
- [x] Server list: cards + real backend fetch + TCP ping + premium badges/hiding (§5.4)
- [x] Connect screen: animated spirograph, status text, 48pt countdown, speeds, pulsing power button, session timer (debug mock engine) (§5.3)
- [x] Settings/Profile: account card, premium card, geo-bypass toggle, logout dialog (§5.5)
- [x] Update-Required full-screen blocker (§5.6)

## Phase 4 — VPN Engine
- [x] `VpnEngine` interface + factory — **real `XrayVpnEngine` now wired** (swap point closed) (§9.3)
- [x] Xray JSON build (SOCKS 10807 inbound, freedom/blackhole outbounds, routing) — real, tested (§9.2)
- [x] Geo split-tunneling rules + persisted toggle → passed to engine at connect time (§9.6)
- [x] Status callbacks: connecting/connected/disconnected/error + speeds (real broadcasts from daemon process) (§9.1)
- [x] Cancel-while-connecting handled; server-switch race is engine-implementation concern (Phase 4)
- [x] **Xray engine port complete** — `XrayConfig`/`VpnRuntime`/`VpnAssets`/`XrayCoreManager` (stats poll, runtime-config injection, notification) + `RootNetVpnService` (tun2socks + UDS fd transfer, Android 15 16KB fix), `:RunSoLibXrayDaemon` process, `specialUse` FGS. Compiles + `assembleDebug` green.

## Phase 5 — Connect Flow & Monetization
- [x] Version check before connect → block snackbar on below-minimum / update snackbar (§4, §13)
- [x] Ad gate exact logic: premium → skip; ads unavailable → skip (NO lockout); else rewarded ad required (§10.3)
- [x] 30-min session timer: wall-clock single-shot + 1s safety-net loop (§10.4)
- [x] **Tunnel hard-stop at 00:00** → SessionExpired → Idle after 2s — real engine stops via `stopService` (background-safe) (§10.4)
- [x] Manual disconnect ≠ expiry; timers cleaned (§9.7)

## Phase 6 — Ads
- [x] `AdsRepository` + rewarded-ad gate (reward ONLY on full completion; skip = no reward) — **real `UnityAdsRepository`** (§10.2)
- [x] `isAvailable` gate + **no-lockout fallback** wired in the connect flow (§10.3)
- [x] Unity Ads SDK init + Game ID — **real**: SDK `4.18.1`, Game ID `800111592`, placement `video`, init once at app start, preload on connect screen (§10)

## Phase 7 — Notifications & Push
- [x] Timer notification channel (id 42, LOW importance, ongoing, no sound) + 10s throttle — **real** (§13)
- [x] Push channel + FCM handlers — **real FCM**: `RootNetMessagingService`, `PushNotificationService`, channel `push_notifications` (§13)
- [x] Token register/unregister — **real** via Worker `/register-device` + `/unregister-device` (login/logout/token refresh)

## Phase 8 — Security
- [x] Cert pinning on API host — **real `PinnedHttpClient`** (primary + backup SPKI pins; mismatch → blocked like Flutter) (§9)
- [x] Root/jailbreak detection — **real** (su paths, test-keys, Magisk) + banners on Login/Settings (§11.2)
- [x] Crash reporting — **real Firebase Crashlytics** (`CrashlyticsService`, default-handler hook, user ID re-assoc) (§11.4)
- [x] No secrets in client (public constants only) (§11.5)

## Phase 9 — Backend (Supabase)
- [x] Tables/migrations — **13/13 migrations applied + in sync** (`migration list --linked` clean); new `20260806085114` adds `vless_links.premium_only` + propagates into import RPC (§12)
- [x] Version check — **real** HTTP to public `app_config` REST (RLS) with offline fallback (§13)
- [x] Servers — **real backend**: direct Supabase REST (`servers` table, RLS public read) primary + Worker `POST /servers` fallback (session JWT); empty-state on failure; configs never hardcoded (§7.2)
- [x] Edge Function + rate limiting + geo-api — **deployed & live**: `rootnet-api` v12 (health/geoip/version/register-device/unregister-device verified live), `geo-api` v8, `vless-ingestion-api` worker redeployed with `premium_only` detection (webhook + batch, verified end-to-end)
- [x] SECURITY: revoked EXECUTE on SECURITY DEFINER RPCs (`import_pending_vless_links`, `cleanup_old_vless_links`) from PUBLIC/anon/authenticated — only service_role + postgres (verified via ACL + advisors)

## Phase 10 — Content Pipeline
- [x] Telegram scraper (Telethon): extract/dedupe/premium-detect, webhook + run-once modes (§12.1) — verified; premium fallback now propagates `premium_only` through webhook AND direct-Supabase paths
- [x] Ingestion worker: `/webhook`, `/webhook/batch`, `/cleanup`, `/health` (§12.2)
- [x] End-to-end: Telegram → scraper → worker → `vless_links` → import → `servers` (§12.3) — tested live via import RPC: `premium_only=true/false` landed correctly on `servers`, test rows cleaned up

## Phase 11 — Release Hardening
- [x] Crashlytics mapping-file upload in release builds — crashlytics-gradle `3.0.7` applied (§14)
- [x] Release signing (keystore) + APK artifact — **signed `app-release.apk`** (RSA 2048, 10y, v1+v2 verified via apksigner; R8-minified 204 MB; lint fatal fixed by pinning `fragment-ktx` 1.8.5). Keystore + `keystore.properties` are **gitignored** — back them up (`android-app/keystore/`)
- [x] Branded auth email templates + custom SMTP (§17) — **deployed live & verified**: 13 RootNet templates (confirm/recovery/magic-link/invite/email-change/re-auth + 7 security notifications) match `supabase/templates/` byte-for-byte; security-notification emails enabled; Resend SMTP (`smtp.resend.com:465`, sender RootNet) configured. Automation: `supabase/scripts/apply-email-templates.mjs` + `configure-smtp.mjs` (run `node supabase/scripts/apply-email-templates.mjs` with `SUPABASE_ACCESS_TOKEN`)
- [ ] Landing page + download link + privacy policy page (§17)
- [ ] Full §16 acceptance pass on a real device (see checklist below)

---

## Acceptance (from `app_clone.md` §16)
- [x] Boot order: version gate → auth → UI (non-fatal fallbacks)
- [x] Auth flows real (Supabase REST): email/pw, Google PKCE, reset, session persistence, premium via JWT claim
- [x] Server list loads from live backend + real TCP pings; free users never see premium servers (remote repo + UI filter)
- [~] Connect flow: version → ad gate → engine — real Xray TUN wired + build green; on-device verification pending
- [x] 30-min countdown + **real** timer notification; tunnel hard-stop wired (real engine)
- [x] Premium: no ad, unlimited session, premium servers, "Premium active" (real JWT claim)
- [x] Ads offline → free user still connects (no lockout)
- [x] Ad skipped/failed → aborted with message
- [x] Geo-bypass toggle → passed into Xray routing rules
- [x] Rooted device warnings shown (real detection)
- [x] Below-minimum version → full-screen block (boot + connect gate)
- [x] Push register/unregister — real FCM via Worker endpoints
- [x] VLESS/VMess/Trojan/SS/WireGuard all parse + build Xray configs (tested)
- [x] Crash reporting — real Crashlytics
- [x] No service-role/admin secrets in the APK

---

*Reference: full spec in `app_clone.md`. Update this file as you go.*
