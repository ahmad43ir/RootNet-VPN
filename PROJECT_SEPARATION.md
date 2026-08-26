# PROJECT SEPARATION — READ THIS FIRST

> **This repository contains RootNet only.** Do not mix infrastructure with other products.

---

## 🌿 RootNet (VPN App)

| Item | Value |
|------|-------|
| **What it is now** | v3 real VPN app — embedded Xray engine (LibXray) + 30 min/ad time quota synced to a server ledger + BPB subscription servers |
| **Supabase Project** | `bprkazfxqmanrybiexnh` |
| **Project URL** | `https://bprkazfxqmanrybiexnh.supabase.co` |
| **Edge Functions** | `rootnet-api` (incl. `GET /bpb-sub`, `POST /quota/sync`), `geo-api`, `telegram-bot`, `proxy-api` |
| **Database** | `servers`, `app_config`, `vless_links`, `scraper_config`, `bpb_services` (secret subs), `device_quota`, etc. |
| **Frontend** | `rootnet-vpn/android-app/` (Kotlin + Compose, pkg `com.chobgroup.rootnet`) |

## 📦 VlessHub (config hub, ex-ProxyBox)

| Item | Value |
|------|-------|
| **Path / pkg** | `vlesshub/vlesshub-app/` · `com.chobgroup.vlesshub` (NEW applicationId — separate store listing) |
| **Tabs** | Links (VLESS copy/export) · MTProto proxy batches · VPN files · Settings |
| **Ads** | Adivery only: picture = 5-click cycles + every More page; video = refreshes + every 3 completed downloads |
| **Backend** | shares `rootnet-api`, `proxy-api`, same Supabase project |
| **Scraper** | `vlesshub/vless-scraper/` (Telethon listener — VLESS configs + MTProto proxies, feeds the shared Supabase pipeline) |
| **Local bot** | `vlesshub/telegram-bot/` (admin bot env for `/scrape`, `/addproxy`, …; code lives in the `telegram-bot` Edge Function) |

> ▶️ **Continue-later pointer:** 2 of 10 BPB workers are LIVE and registered
> (`bpb_services` rows `BPB-1`, `BPB-2`); RootNet Servers tab is BPB-only (Connect-only
> cards). Full status + file map: [BPB_VLESS_PLAN.md](BPB_VLESS_PLAN.md).

---

## 🚀 Deployment Commands

### RootNet
```bash
npx supabase functions deploy rootnet-api --no-verify-jwt
npx supabase functions deploy geo-api --no-verify-jwt
```

---

## 📁 Directory Ownership

| Directory | Owner |
|-----------|-------|
| `rootnet-vpn/android-app/` | RootNet |
| `rootnet-vpn/supabase/functions/rootnet-api/` | RootNet |
| `rootnet-vpn/supabase/functions/geo-api/` | RootNet |
| `rootnet-vpn/supabase/functions/telegram-bot/` | RootNet |
| `rootnet-vpn/supabase/functions/proxy-api/` | RootNet |
| `rootnet-vpn/supabase/migrations/` | RootNet |
| `rootnet-vpn/vless-worker/` | RootNet (shared ingestion webhook) |
| `vlesshub/vlesshub-app/` | VlessHub (ex-ProxyBox — Links / MTProto / Files) |
| `vlesshub/vless-scraper/` | VlessHub (Telegram scraper — fills the shared pipeline) |
| `vlesshub/telegram-bot/` | VlessHub (admin bot env/config) |
| `pages-site/` | Shared (Chob Group hub) |

> **Group folders:** everything under `rootnet-vpn/` belongs to the RootNet VPN app;
> everything under `vlesshub/` belongs to VlessHub. Both share one Supabase project
> (`bprkazfxqmanrybiexnh`) and its edge functions.
