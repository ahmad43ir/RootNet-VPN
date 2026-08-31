# Admin VlessHub

A stripped-down admin companion app for managing VlessHub servers, MTProto proxies, and VPN config files.

## Features

- **3 tabs**: Links (VLESS/V2Ray configs), MTProto (Telegram proxies), Files (VPN config files)
- **Copy / Export** configs to clipboard or VPN client apps
- **TCP Ping** to test server reachability (Links tab)
- **Refresh** to pull fresh data from the backend
- **Admin Remove** — delete servers, proxies, or files from the backend (requires admin key)

## Admin Key

The Remove button only appears when an admin API key is configured in Settings. The key is sent as an `X-Admin-Key` header on DELETE requests.

Backend endpoints expected:
- `DELETE /servers/{id}` — remove a config
- `DELETE /files/{id}` — remove a VPN file
- `DELETE /proxies/{id}` — remove a proxy

## Tech Stack

- Native Android (Kotlin + Jetpack Compose)
- Same endpoints as VlessHub (Cloudflare Worker + Supabase)
- No ads, no animations, no ad SDKs
- Red accent theme (vs VlessHub's green)

## Build

```bash
cd vlesshub/admin_vlesshub
./gradlew.bat :app:assembleDebug
```
