# Server Management Guide

## Architecture Overview

```
┌─────────────────────┐
│   Supabase DB       │  ← servers live here (public.servers table)
│   (PostgreSQL)      │
└──────┬──────────────┘
       │
       ├── Cloudflare Worker API  ──── Flutter App (fallback path)
       │   (blocked in some countries)
       │
       └── Direct Supabase query ──── Flutter App (primary path)
           (uses RLS + auth session)
```

### How servers are fetched

The Flutter app uses a **dual-path** strategy:

1. **Direct Supabase** (primary): `Supabase.instance.client.from('servers').select(...)`
   - Uses the user's existing auth session (JWT)
   - RLS policies automatically filter based on premium status
   - Works even when `*.workers.dev` is blocked
   - Implemented in: `lib/services/http_service.dart` → `fetchServersDirect()`

2. **Worker API** (fallback): `POST /servers` on Cloudflare Worker
   - Used only if direct Supabase query fails
   - Same JWT auth, but routed through the Worker proxy layer
   - Implemented in: `lib/services/http_service.dart` → `fetchServersViaWorker()`

## Adding New Servers

### Via Supabase Dashboard (easiest)

1. Go to: [Supabase Table Editor](https://app.supabase.com/project/bprkazfxqmanrybiexnh)
2. Open the `servers` table
3. Click **Insert row**
4. Fill in the columns:

| Column | Type | Required | Description |
|--------|------|----------|-------------|
| `name` | TEXT | ✅ | Display name in the app (e.g. "Oak", "Pine") |
| `flag` | TEXT | ✅ | Emoji flag (e.g. `🌐`, `🇺🇸`, `🇫🇮`) |
| `country` | TEXT | ✅ | Location label (e.g. "Cloud", "US", "Finland") |
| `config` | TEXT | ✅ | Full VLESS URI (e.g. `vless://uuid@host:port?...`) |
| `host` | TEXT | - | Server hostname/IP (for reference) |
| `port` | INT | - | Server port (usually 443, 8443, 2083) |
| `is_active` | BOOL | ✅ | `true` = available, `false` = hidden |
| `premium_only` | BOOL | ✅ | `true` = premium users only, `false` = everyone |
| `type` | TEXT | ✅ | Protocol: `vless`, `vmess`, `trojan`, `wireguard` |
| `config_format` | TEXT | ✅ | Format: `link`, `json`, `npv`, `conf` |

### Via SQL Editor

Run in [Supabase SQL Editor](https://app.supabase.com/project/bprkazfxqmanrybiexnh/sql):

```sql
INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
VALUES (
  'ServerName',                              -- display name
  '🌐',                                      -- flag emoji
  'Cloud',                                   -- country/location
  'vless://uuid@host:port?encryption=none&security=tls&sni=sni.host&fp=chrome&type=ws&host=sni.host&path=%2F',  -- full config
  'host.example.com',                        -- hostname
  443,                                       -- port
  true,                                      -- is_active
  false,                                     -- premium_only
  'vless',                                   -- type
  'link'                                     -- config_format
);
```

### Via Migration File (for version control)

Create a new migration in `supabase/migrations/`:

```sql
-- supabase/migrations/YYYYMMDDHHMMSS_description.sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE name = 'ServerName') THEN
    INSERT INTO public.servers (...) VALUES (...);
  END IF;
END $$;
```

## Removing Servers

### Soft delete (recommended)
Set `is_active = false` → server stays in DB but won't appear in the app.

### Hard delete
```sql
DELETE FROM public.servers WHERE name = 'ServerName';
```

## Current Server List

### Original servers (from `supabase/seed.sql`)

| Name | Flag | Country | Type | Host:Port |
|------|------|---------|------|-----------|
| Oak | 🌐 | Cloud | VLESS | 172.66.45.6:443 |
| Pine | 🌐 | Cloud | VLESS | 104.16.72.20:8443 |
| Redwood | 🇺🇸 | US | VLESS | 206.71.158.124:443 |
| Cedar | 🌐 | Cloud | VLESS | 172.64.40.79:2083 |
| Birch | 🌍 | CDN | VLESS | store.ubi.com:443 |
| Maple | 🌐 | Cloud | VMess | 172.66.45.10:443 |
| Spruce | 🇺🇸 | US | VMess | 206.71.158.125:443 |
| Willow | 🇳🇱 | NL | VMess | 146.190.100.50:443 |

### New servers added (from @prrofile_purple)

| Name | Flag | Country | Type | Host:Port |
|------|------|---------|------|-----------|
| Ash | 🌐 | Cloud | VLESS | 104.16.72.20:8443 |
| Elm | 🇫🇮 | Finland | VLESS | 104.16.72.41:443 |
| Fir | 🌐 | Cloud | VLESS | 104.18.42.54:443 |
| Hazel | 🌐 | Cloud | VLESS | 172.64.144.82:443 |
| Holly | 🌐 | Cloud | VLESS | 172.64.145.158:443 |
| Ivy | 🌐 | Cloud | VLESS | 172.64.40.49:443 |
| Juniper | 🌐 | Cloud | VLESS | 172.64.40.79:2083 |
| Laurel | 🌐 | Cloud | VLESS | 172.64.53.65:443 |
| Magnolia | 🌐 | Cloud | VLESS | 172.66.45.6:443 |
| Olive | 🌐 | Cloud | VLESS | 172.67.75.194:8443 |
| Palm | 🇩🇪 | Germany | VLESS | 188.114.97.6:443 |
| Cypress | 🌐 | Cloud | VLESS | 45.130.125.207:443 |
| Aspen | 🌐 | Cloud | VLESS | celestara.biz:443 |
| Yew | 🌐 | Cloud | VLESS | cf.levikogjgfdd.ir:443 |
| Acacia | 🌐 | Cloud | VLESS | store.ubi.com:443 |

## Future Ideas

### Priority: High
- [ ] **Offline cache**: Cache server list locally so it works immediately on app open, then refresh in background
- [ ] **Fallback chain**: If Supabase direct fails, try Worker API; if both fail, show cached servers

### Priority: Medium
- [ ] **Server tags/labels**: Allow categorizing servers by use case (streaming, browsing, gaming)
- [ ] **Auto-ping on load**: Automatically ping all servers when the server list loads (with a toggle in settings)
- [ ] **Multi-protocol display**: Show protocol icon/badge (VLESS, VMess, Trojan, WireGuard) in the server list
- [ ] **Server search/filter**: Add a search bar to filter servers by name, country, or protocol

### Priority: Low
- [ ] **Load balancing**: Auto-select the lowest-ping server on connect
- [ ] **Server health monitoring**: Periodic health checks from a Worker → mark servers as inactive if down
- [ ] **Server group/subscriptions**: Import servers in bulk from subscription URLs (like V2Ray sharing links)
- [ ] **Admin panel UI**: A web-based admin panel to add/remove servers without SQL

## Troubleshooting

### "No servers available" in the app

1. **Check Supabase → Table Editor**: Verify there are rows in `servers` with `is_active = true`
2. **Check auth**: Ensure you're logged in (the Supabase query requires a valid session)
3. **Check RLS policies**: The authenticated user needs SELECT access
4. **Look at debug logs**: Connect via USB and run `flutter logs` — look for `ServerList:` and `HttpService:` prefixed messages
5. **Check Supabase connectivity**: Test if `https://bprkazfxqmanrybiexnh.supabase.co` is reachable from your device
