# PROJECT SEPARATION — READ THIS FIRST

> **This repository contains RootNet only.** Do not mix infrastructure with other products.

---

## 🌿 RootNet (VPN App)

| Item | Value |
|------|-------|
| **Supabase Project** | `bprkazfxqmanrybiexnh` |
| **Project URL** | `https://bprkazfxqmanrybiexnh.supabase.co` |
| **Edge Functions** | `rootnet-api`, `geo-api`, `telegram-bot`, `proxy-api` |
| **Database** | `servers`, `app_config`, `vless_links`, `scraper_config`, etc. |
| **Frontend** | `android-app/` (Kotlin + Compose) |
| **Scraper** | `vless-scraper/` (Telegram VLESS configs) |

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
| `android-app/` | RootNet |
| `proxybox-app/` | RootNet |
| `supabase/functions/rootnet-api/` | RootNet |
| `supabase/functions/geo-api/` | RootNet |
| `supabase/functions/telegram-bot/` | RootNet |
| `supabase/functions/proxy-api/` | RootNet |
| `supabase/migrations/` | RootNet |
| `vless-scraper/` | RootNet |
| `pages-site/` | Shared (Chob Group hub) |
