# RootNet — v2.2 Config Launcher — Build Progress Checklist

> Track implementation against `app_clone.md` — section **0 (v2.0)** is the source of
> truth for the new model; sections 1–17 are the retired v1 engine spec.
> v2.2: **Adivery is the only ad network** (App ID `73697db8-c7dc-4af2-9f3c-dd422942cf57`,
> placements image_rootnet / video_rootnet / banner_rootnet). AdMob + Unity Ads removed.
>
> **Target stack:** ✅ Kotlin/Jetpack Compose — project at `rootnet-vpn/android-app/` (app id `com.chobgroup.rootnet`)
> **Status legend:** `[ ]` todo · `[x]` done · `[~]` in progress

---

## v2.0 — Config Launcher (current)
- [x] **Adivery ads (v2.2)** — `AdiveryAdsManager` is the ONLY network (interstitial / rewarded / banner); AdMob + Unity Ads removed (files, deps, manifest meta-data, ProGuard rules); refresh gated by Adivery rewarded video (full-watch required); SDK `com.adivery:sdk:4.9.0`
- [x] **Engine removed** — `domain/` + `vpn/` packages deleted (state machine, SessionTimer, Xray engine, VPN service, daemon process)
- [x] **Auth removed** — `AuthRepository`/`SupabaseAuthRepository`/`SecurePrefs`/guest mode deleted; no login, no premium UI
- [x] **Monetization reworked** — ads gate Copy (AdMob interstitial) + Export (Unity rewarded video); banner on the list; no-lockout fallback kept
- [x] **Server list → config launcher** — cache-first list + refresh re-fetch + live TCP ping; per-row Copy / Export actions
- [x] **Export flow** — `ACTION_VIEW` on the raw config URI → user's default client; "no app found" dialog with copy fallback
- [x] **Settings** — how-it-works steps, recommended clients (v2rayNG/NekoBox/Hiddify install links), privacy policy, version
- [x] **Boot** — version gate kept; app opens straight into the shell (no login route, no NavHost)
- [x] **Manifest** — VPN/FCM/notification permissions + service + daemon process + auth deep links removed; AdMob App ID added (TEST ID)
- [x] **Gradle** — AdMob (`play-services-ads 23.6.0`) added; `firebase-messaging` removed; Crashlytics kept; version bumped 2.0.0 (101)
- [x] **Tests** — state-machine tests deleted; `ConfigNormalizerTest` trimmed to parsers (Xray builders removed)
- [x] **Websites** — rootnet.html + index.html + privacy.html updated to config-launcher positioning
- [x] **Backend** — version gate bumped to 2.0.0/101 (migration `20260813000001` + `rootnet-api` DEFAULT_CONFIG); endpoints otherwise unchanged
- [x] **Docs/skills** — app_clone.md §0 supersession, PROJECT_CONTEXT/README/ROADMAP/TODO, skills updated
- [ ] On-device acceptance: copy gated by Adivery interstitial, export gated by Adivery rewarded video, refresh gated by Adivery rewarded video, export opens v2rayNG when installed, banner renders, refresh returns fresh servers
- [x] Adivery placement IDs wired — `image_rootnet` (interstitial) / `video_rootnet` (rewarded) / `banner_rootnet` (banner)
- [x] AdMob + Unity Ads removed (v2.2) — files, gradle deps, manifest meta-data, ProGuard rules all cleaned

---

## Retired (v1 — do not re-implement)
- [x] (v1) Xray engine integration, VPN service + daemon, geo split-tunneling, session timer, timer notification — **removed in v2.0**
- [x] (v1) Supabase auth, Google OAuth, premium JWT gating, guest mode, root detection — **removed in v2.0**
- [x] (v1) FCM push + device-token lifecycle — **removed in v2.0** (client-side; endpoints stay server-side)

---

## Acceptance (v2.0, from `app_clone.md` §0)
- [x] Boot: version gate → config list (no login)
- [x] Server list loads from live backend (public Supabase REST) + real TCP pings
- [x] Copy: interstitial (when loaded) → clipboard
- [x] Export: rewarded video (when loaded) → default client app; dialog fallback when no handler
- [x] Ads unavailable → actions still complete (no lockout)
- [x] Refresh always re-fetches fresh servers
- [x] Below-minimum version → full-screen block
- [x] Crash reporting (Crashlytics) — release only
- [x] No service-role/admin secrets in the APK

*Reference: `app_clone.md` §0 (v2.0). Update this file as you go.*
