# 📦 VlessHub — Free VLESS & MTProto Proxies

A lightweight **Android app** (Kotlin + Jetpack Compose) that provides
**free VLESS VPN configs** and **MTProto proxies** for Telegram.
Monetized with **Adivery** — a persistent banner, throttled interstitials,
and rewarded video for batch refresh.

```
User opens the app
  → GET vlesshub-api/servers → VLESS servers with geo data
  → cards with Copy / Share / Open in V2RayNG / NekoBox / Hiddify
  → MTProto proxy pool via proxy-api
```

## Stack

- **AGP 9.0.1, Kotlin 2.3.20, Gradle 9.1.0, compileSdk 36, minSdk 23, Java 17**
- **Jetpack Compose** with Material 3
- **Adivery SDK** 4.9.0 (banner + interstitial + rewarded video)
- **OkHttp** 5.4.0 for networking
- Backend: Cloudflare Workers on D1 + Supabase

## Build

```bash
cd vlesshub-app
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release (R8-minified, needs signing)
```

## Before Release

1. Create the app in the **Adivery dashboard** → get the **App ID**.
2. Create **Interstitial**, **Rewarded**, and **Banner** placements → get their UUIDs.
3. Replace the placeholder IDs in `ads/AdiveryAdsManager.kt`:
   - `APP_ID`
   - `INTERSTITIAL_PLACEMENT_ID`
   - `REWARDED_PLACEMENT_ID`
   - `BANNER_PLACEMENT_ID`

Until then the app gracefully skips all ads (no lockout).

## Release Signing

1. Create a keystore: `keytool -genkey -v -keystore vlesshub-release.jks ...`
2. Create `keystore.properties` at the `vlesshub-app/` root:
   ```
   storeFile=keystore/vlesshub-release.jks
   storePassword=your_password
   keyAlias=your_alias
   keyPassword=your_key_password
   ```
3. Both `keystore.properties` and `keystore/` are gitignored — never commit them.

## Architecture

```
vlesshub-app/          ← This Android app
vless-scraper/         ← Telegram channel scraper (Python/Telethon)
telegram-bot/          ← Telegram bot for commands
```
