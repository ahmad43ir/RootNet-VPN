# RootNet API — Supabase Edge Function

> ## 🚀 v2.2 (2026-08-13)
> The v2 app is a **config launcher** — it reads `app_config` + `servers` via **public REST**
> and does NOT call this function. The JWT-gated routes below (`/servers`, `/version`,
> `/register-device`, `/unregister-device`, `/send-notification`) remain deployed for the
> retired v1 client + admin pipeline; treat them as legacy, not the app's live contract.
> `/free-connection` was **removed (2026-08-13)** — its RPC `claim_free_connection` and the
> `free_connection_quota` table were dropped in migration `20260813000003`.
> `DEFAULT_CONFIG` = 2.0.0/101.

Replaces the Cloudflare Worker backend completely.

## Migration Summary

| Feature | Old (Cloudflare Worker) | New (Supabase Edge Function) |
|---------|------------------------|------------------------------|
| **Runtime** | Cloudflare Workers | Supabase Edge Function (Deno) |
| **JWT Auth** | Manual JWKS + Web Crypto | `supabase.auth.getUser()` |
| **Rate Limiting** | In-memory Map | Postgres `rate_limits` table + `check_rate_limit()` RPC |
| **IP Source** | `CF-Connecting-IP` | `x-forwarded-for` (generic) |
| **Database** | Supabase REST API via `fetch()` | `@supabase/supabase-js` client |
| **Push** | FCM v1 OAuth2 (same logic) | FCM v1 OAuth2 (same logic, Deno) |
| **GeoIP** | `cf-ipcountry` header | External `geo-api` Edge Function |
| **Deploy** | `wrangler deploy` | `supabase functions deploy` |

## Architecture

```
Client (Flutter App)
    │
    ▼
Supabase Edge Function: rootnet-api
    │
    ├── GET  /public/servers     → servers table (RLS: public)
    ├── POST /servers            → servers table (JWT)
    ├── POST /version            → app_config table (JWT)
    ├── POST /register-device    → device_tokens table (JWT + upsert)
    ├── POST /unregister-device  → device_tokens table (JWT + delete)
    ├── POST /send-notification  → FCM v1 API (admin key)
    ├── GET  /geoip              → geo-api Edge Function (external)
    ├── GET  /health             → static health response
    └── GET  /                   → static health response
```

## Files

| File | Purpose |
|------|---------|
| `index.ts` | Main router + all endpoint handlers |
| `_utils.ts` | CORS, JSON, IP extraction helpers |
| `_auth.ts` | JWT verification via `supabase.auth.getUser()` |
| `_rate-limit.ts` | Postgres-backed rate limiting |
| `_fcm.ts` | FCM v1 push notification logic |
| This README | Documentation |

## Deploy

```bash
# 1. Set secrets
supabase secrets set SUPABASE_URL=<your-project-url>
supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your-key>
supabase secrets set FCM_SERVICE_ACCOUNT='{...}'
supabase secrets set ADMIN_KEY=<shared-admin-secret>

# 2. Deploy (public, no JWT — auth handled inside)
supabase functions deploy rootnet-api --no-verify-jwt
```

## Update Flutter App

In `lib/services/app_constants.dart`, change:

```dart
// Old
static const String workerApiUrl =
    'https://rootnet-api.mobileahmad43-a18.workers.dev';

// New
static const String workerApiUrl =
    'https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api';
```
