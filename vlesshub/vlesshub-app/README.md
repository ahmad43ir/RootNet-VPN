# 📦 ProxyBox — Free MTProto Proxies for Telegram

A lightweight **Android app** (Kotlin + Jetpack Compose) that hands out
**10 random working MTProto proxies** (`tg://proxy` links) per batch from the
shared RootNet Supabase pool. Monetized with **AdMob** — a persistent banner
plus a throttled interstitial before each new batch.

```
User taps "Get 10 proxies"
  → (refresh only) AdMob interstitial — throttled to 1/min
  → GET proxy-api/proxies → 10 random active proxies (working first)
  → cards with Copy / Share / Open in Telegram
```

## Stack

- Same toolchain as RootNet: **AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0,
  compileSdk 36, minSdk 23, Java 17** — with the regional Aliyun mirror
  fallbacks so builds work in the target region.
- **AdMob** `play-services-ads` 23.6.0 (banner + interstitial).
- Backend: `rootnet-vpn/supabase/functions/proxy-api/` — public GET `/proxies`,
  IP rate-limited, no login (that's why there's no user/table separation:
  the app is fully anonymous).

## Build

```bash
cd vlesshub-app
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release (R8-minified, unsigned)
```

## Before release (AdMob)

1. Create the app in the **AdMob console** → get the **App ID**.
2. Create **Banner** and **Interstitial** ad units → get their unit IDs.
3. Put the real App ID in `app/src/main/AndroidManifest.xml`
   (`com.google.android.gms.ads.APPLICATION_ID`).
4. Put the real unit IDs in `ads/AdManager.kt`
   (`BANNER_UNIT_ID`, `INTERSTITIAL_UNIT_ID`).

Until then the app uses Google's official **test** ad unit IDs (safe to ship,
shows "Test ad" banners).

## Backend

The `proxy-api` Supabase Edge Function must be deployed once:

```bash
cd supabase
npx supabase functions deploy proxy-api --no-verify-jwt --project-ref bprkazfxqmanrybiexnh
```

Requires migration `20260808000001` (`scraper_proxies`) + a seeded pool
(see `rootnet-vpn/supabase/functions/proxy-api/README.md`).
