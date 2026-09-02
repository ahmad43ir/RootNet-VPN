# RootNet VPN

Android VPN app with an embedded Xray engine, backed by Supabase Edge Functions.

## Project

| Project | Path | Package | Description |
|---------|------|---------|-------------|
| **RootNet VPN** | `rootnet-vpn/android-app/` | `com.chobgroup.rootnet` | VPN app with embedded Xray engine |
| **Landing Pages** | `pages-site/` | -- | Chob Group hub + project pages |

## Infrastructure

- **Supabase Project:** `bprkazfxqmanrybiexnh`
- **Edge Functions:** `rootnet-api`, `geo-api`, `proxy-api`, `support-bot`
- **Database:** `servers`, `vpn_files`, `scraper_proxies`, `app_config`
- **Cloudflare Worker:** `vless-worker` (VPN config ingestion API)
- **Cloudflare Pages:** `chobgroup` (landing pages + APK downloads)

## Quick Start

### Android App
```bash
cd rootnet-vpn/android-app
./gradlew.bat :app:assembleRelease
```

### Edge Functions
```bash
cd rootnet-vpn
npx supabase functions deploy rootnet-api --project-ref bprkazfxqmanrybiexnh
```

### Landing Pages
```bash
cd pages-site
npx wrangler pages deploy . --project-name chobgroup --branch main
```

## Documentation

| Doc | Location |
|-----|----------|
| Agent Rules | `AGENTS.md` |
