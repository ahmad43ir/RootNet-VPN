# 🔐 RootNet Supabase Reference
> **Last updated:** July 20, 2026
> **Created by:** Buffy (Freebuff AI Agent)
>
> ⚠️ **WARNING:** This file contains sensitive credentials. It is listed in `.gitignore`
>    and must NEVER be committed to version control!

---

## 1. 📋 Project Overview

| Property | Value |
|----------|-------|
| **Organization ID** | `oraqqsgyscywcvvmxwwj` |
| **Project Reference ID** | `bprkazfxqmanrybiexnh` |
| **Project Name** | `RootNet` |
| **Region** | West EU (Ireland) |
| **Flutter Package** | `com.chobgroup.rootnet` |

---

## 2. 🔑 Credentials

### 2.1 Supabase Project URL
```
https://bprkazfxqmanrybiexnh.supabase.co
```

### 2.2 Supabase Anon Key (Public — safe to share)
```
sb_publishable_h2oEryaNO2GWDEYw-flm3A_EV9pP9Co
```
> **Where used:**
> - `lib/services/app_constants.dart` (hardcoded)
> - Cloudflare Worker (`wrangler secret put SUPABASE_ANON_KEY`)
>
> This key is safe because it's RLS-protected — it only grants access
> to data your Row-Level Security policies allow.

### 2.3 Supabase Service Role Key (⚠️ SECRET — never expose!)
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwcmthemZ4cW1hbnJ5YmlleG5oIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NDA2MzIwOSwiZXhwIjoyMDk5NjM5MjA5fQ.3Bhewah8dYT2EaBUg1o5BZAcHUmwad90oXjr5m2lt04
```
> **Used for:** Cloudflare Worker → Supabase DB queries (server-to-server)
> **Stored as:** `wrangler secret put SUPABASE_SERVICE_ROLE_KEY`
> **Get it from:** `npx supabase projects api-keys --project-ref bprkazfxqmanrybiexnh`
>  or https://app.supabase.com/project/bprkazfxqmanrybiexnh/settings/api

### 2.4 Supabase Access Token (⚠️ SECRET — CLI only)
```
sbp_98f9868ee646aad93c2c9d09e1b5ecf3c88fe627
```
> **Used for:** Supabase CLI authentication (`supabase login`)
> **Stored in:** `~/.supabase/access-token` (locally on your machine)
> **To regenerate:** https://app.supabase.com/dashboard/account/tokens

### 2.5 Supabase Database Password (⚠️ SECRET — CLI only)
```
102030302010ahmad
```
> **Used for:** `supabase link` command, direct DB connections
> **Stored in:** Supabase CLI secure storage (not in any project file)
> **To change:** https://app.supabase.com/project/bprkazfxqmanrybiexnh/settings/database

---

## 3. 🚀 Supabase CLI Setup

| Setting | Value |
|---------|-------|
| **CLI Version** | `2.109.1` |
| **Run via** | `npx supabase` |
| **Local config** | `supabase/config.toml` |
| **Project ID** | `rootnet` |
| **Linked** | ✅ Yes — linked to `bprkazfxqmanrybiexnh` |

### 3.1 Setup Commands (for future reference)

```bash
# Initialize (already done — creates supabase/config.toml)
npx supabase init

# Login (already done — token stored locally)
npx supabase login --token sbp_98f9868ee646aad93c2c9d09e1b5ecf3c88fe627

# Link to project (already done)
npx supabase link --project-ref bprkazfxqmanrybiexnh --password 102030302010ahmad
```

### 3.2 Useful CLI Commands

| Command | Purpose |
|---------|---------|
| `npx supabase db pull` | Pull remote schema into local migration files |
| `npx supabase db push` | Push local migrations to remote |
| `npx supabase db diff` | Show schema differences |
| `npx supabase db query "SQL"` | Run SQL queries directly |
| `npx supabase db remote commit` | Commit remote schema changes as a migration |
| `npx supabase projects list` | List all Supabase projects |
| `npx supabase functions list` | List Edge Functions |
| `npx supabase db advisors` | Run DB performance advisors |

---

## 4. 🔗 Other Supabase Projects in Your Account

| Organization ID | Project Ref | Project Name | Region |
|----------------|-------------|--------------|--------|
| `ulqaddobfhkathldepbh` | `dzbsluvmepzbyfgsuvwa` | name_phone number | North EU (Stockholm) |
| `ulqaddobfhkathldepbh` | `gtnlaigouzkncvslyche` | smart_attendance | West EU (Ireland) |
| `oraqqsgyscywcvvmxwwj` | `eisytewiotmqhydpfkuw` | ahmad43ir's Project | North EU (Stockholm) |
| `oraqqsgyscywcvvmxwwj` | **`bprkazfxqmanrybiexnh`** | **RootNet** ⬅️ | West EU (Ireland) |
| `oraqqsgyscywcvvmxwwj` | `rjkdgooyjpwvpncbzvhx` | telebot | West EU (Ireland) |

---

## 5. 📱 Flutter App Integration

### 5.1 Auth Service (`lib/services/auth_service.dart`)

- **Singleton** pattern via `AuthService.instance`
- **Init:** `AuthService.instance.initialize(url:, publishableKey:)`
  - Called in `lib/main.dart` at startup
- **Methods:** `signInWithPassword()`, `signUp()`, `signOut()`
- **Properties:** `currentUser`, `isLoggedIn`, `currentSession`, `client`
- **Events:** `onAuthChange` stream

### 5.2 Supabase Flutter SDK

| Setting | Value |
|---------|-------|
| **Package** | `supabase_flutter: ^2.16.0` |
| **Init location** | `lib/main.dart` (before `runApp`) |

### 5.3 App Constants (`lib/services/app_constants.dart`)

```dart
static const String supabaseUrl = 'https://bprkazfxqmanrybiexnh.supabase.co';
static const String supabaseAnonKey =
    'sb_publishable_h2oEryaNO2GWDEYw-flm3A_EV9pP9Co';
static const String emailRedirectTo = 'com.chobgroup.rootnet://callback';
static const String passwordResetRedirect =
    'com.chobgroup.rootnet://reset-password';
```

---

## 6. 🌐 Supabase Edge Functions Integration

### 6.1 Edge Functions Setup

| Setting | Value |
|---------|-------|
| **rootnet-api URL** | `https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api` |
| **geo-api URL** | `https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/geo-api` |
| **Supabase secrets** | Set via `npx supabase secrets set SUPABASE_URL` |
| | Set via `npx supabase secrets set SUPABASE_SERVICE_ROLE_KEY` |
| **JWT verification** | `supabase/auth/v1` — via `supabase.auth.getUser()` |

### 6.2 Security Flow

1. Rate limit by IP via Postgres `check_rate_limit()` RPC
2. Rate limit by userId+IP (30 req/min, authenticated endpoints)
3. JWT verification via `supabase.auth.getUser()` (no manual JWKS)
4. Premium check via `app_metadata.isPremium` (NEVER trusts `user_metadata`)
5. Query Supabase DB for filtered server list using service_role key

---

## 7. 🌐 Important URLs

| What | URL |
|------|-----|
| **Supabase Dashboard** | https://app.supabase.com/project/bprkazfxqmanrybiexnh |
| **API Settings** | https://app.supabase.com/project/bprkazfxqmanrybiexnh/settings/api |
| **Database Settings** | https://app.supabase.com/project/bprkazfxqmanrybiexnh/settings/database |
| **Auth Settings** | https://app.supabase.com/project/bprkazfxqmanrybiexnh/auth/settings |
| **Access Tokens Page** | https://supabase.com/dashboard/account/tokens |
| **SQL Editor** | https://app.supabase.com/project/bprkazfxqmanrybiexnh/sql |
| **Table Editor** | https://app.supabase.com/project/bprkazfxqmanrybiexnh/editor |
| **Landing Page** | https://chobgroup.pages.dev |
| **RootNet API** | https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api |
| **Cloudflare Dashboard** | https://dash.cloudflare.com |

---

## 8. 🗄️ Database Schema

### 8.1 `servers` Table

| Column | Type | Description |
|--------|------|-------------|
| `id` | `BIGSERIAL PRIMARY KEY` | Auto-increment ID |
| `name` | `TEXT NOT NULL` | Display name (e.g. "Oak", "Pine") |
| `flag` | `TEXT NOT NULL` | Emoji flag (e.g. "🌐", "🇺🇸") |
| `country` | `TEXT NOT NULL` | Location label (e.g. "Cloud", "US") |
| `config` | `TEXT NOT NULL` | VLESS URI string |
| `host` | `TEXT DEFAULT ''` | Server hostname/IP (for landing page) |
| `port` | `INTEGER DEFAULT 443` | Server port (for landing page) |
| `is_active` | `BOOLEAN DEFAULT true` | Whether server is available |
| `premium_only` | `BOOLEAN DEFAULT false` | Whether server requires premium |
| `created_at` | `TIMESTAMPTZ DEFAULT now()` | Creation timestamp |

RLS: Enabled (but Edge Functions use service_role key which bypasses RLS)

### 8.2 Premium Access
Premium status is stored in Supabase Auth → `auth.users.raw_app_meta_data`.
The Worker checks `app_metadata.isPremium` from the JWT (NEVER trusts `user_metadata`).
To make a user premium, set their `app_metadata` via Supabase Dashboard:
```
Settings → Auth → Users → [User] → Edit → App metadata → {"isPremium": true}
```

### 8.3 Adding/Removing Servers
Use the Supabase SQL Editor or Table Editor:
```sql
-- Add a new server
INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only)
VALUES ('Tokyo-1', '🇯🇵', 'Japan', 'vless://...', 'your-server.com', 443, true, false);

-- Deactivate a server
UPDATE public.servers SET is_active = false WHERE name = 'Oak';

-- Make a server premium-only
UPDATE public.servers SET premium_only = true WHERE name = 'Redwood';
```

### 8.4 Current Servers in DB
| id | name | flag | country | premium_only |
|----|------|------|---------|-------------|
| 1 | Oak | 🌐 | Cloud | false |
| 2 | Pine | 🌐 | Cloud | false |
| 3 | Redwood | 🇺🇸 | US | false |
| 4 | Cedar | 🌐 | Cloud | false |
| 5 | Birch | 🌍 | CDN | false |

---

## 9. 🔄 Data Flow (Updated Architecture)

```
Flutter App                 Supabase Edge Function          Supabase DB
─────────────               ─────────────────────         ───────────
POST /servers ─── JWT ───→  1. Rate limit (IP)
                             2. Verify JWT (auth.getUser)
                             3. Rate limit (userId)
                             4. Check app_metadata.isPremium
                             5. Query Supabase ──────────→  SELECT servers
                             ←── JSON response ──────────
←── { servers[], premium } ──

GET /public/servers ── no auth ─→  1. Rate limit (IP)
                                   2. Query Supabase (non-premium only)
                                   ←── { name, flag, country }
```

## 10. 📋 Database Info

| Info | Value |
|------|-------|
| **DB Version** | PostgreSQL 17 (from `supabase/config.toml`) |
| **Pooler** | Disabled locally (configurable in `supabase/config.toml`) |
| **Exposed schemas** | `public`, `graphql_public` |
| **Max rows per request** | 1000 |

---

## 11. 🛡️ Security Notes

- **Anon key is public** — it's safe because RLS policies protect your data
- **Service role key** is NEVER stored in code — only used server-side or from dashboard
- **`user_metadata` is NEVER trusted** for authorization decisions — only `app_metadata`
- **Access token** (`sbp_...`) is stored in `~/.supabase/access-token` on your local machine
- **Database password** is stored in Supabase CLI's secure credential storage
- **This file** is in `.gitignore` — never commit it!
- **JWT verification** is handled by Supabase Auth via `supabase.auth.getUser()`

## 12. 🔄 Quick Setup for New Machine

If you need to set this up on a new computer:

```bash
# 1. Install Supabase CLI
npm install -g supabase

# 2. Login with your access token
npx supabase login --token sbp_98f9868ee646aad93c2c9d09e1b5ecf3c88fe627

# 3. Init and link in your project folder
cd /path/to/rootnet
npx supabase init
npx supabase link --project-ref bprkazfxqmanrybiexnh --password 102030302010ahmad

# 4. Set Edge Function secrets
npx supabase secrets set SUPABASE_URL=<your-project-url>
npx supabase secrets set SUPABASE_SERVICE_ROLE_KEY=<your-key>
npx supabase secrets set ADMIN_KEY=<shared-admin-secret>
npx supabase secrets set FCM_SERVICE_ACCOUNT='{...}'

# 5. Deploy Edge Functions
npx supabase functions deploy rootnet-api --no-verify-jwt
npx supabase functions deploy geo-api --no-verify-jwt

# 6. Verify
npx supabase projects list
```
