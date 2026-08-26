# RootNet — Configs for Your VPN. Rooted in Privacy.

RootNet v2.0 is a **native Android config launcher** (Kotlin + Jetpack Compose): it serves fresh
**VLESS/V2Ray** server configs from Supabase and lets you **copy** them or **export** them
straight into the client app you already use (v2rayNG, NekoBox, Hiddify, …). No accounts, no
built-in tunnel — RootNet never sees your traffic.

- **Backend:** Supabase (Postgres + public REST reads)
- **Protocol:** VLESS / V2Ray — opened in your own client
- **UI:** Dark cyber-organic theme (neon green, deep forest)
- **Platform:** Android (minSdk 23)

## Features

- 📋 **Copy a config** — an Adivery interstitial may play, then the config is on your clipboard
- 📤 **Export a config** — an Adivery rewarded video plays, then it opens in your default VPN client
- 🔄 **Refresh** — gated by an Adivery rewarded video (list re-fetches after a full watch)
- 🌐 Fresh global VLESS servers, refreshed live from Supabase every pull
- 📡 Live TCP ping per server so you always pick the fastest node
- 🔔 No account, no signup, no tracking
- 🛡️ Version gating — blocks outdated app versions
- 🎨 Premium cyber-organic UI

## Publishing and monetization model

- The app is **free and open** — monetization is per-action via **Adivery** (the only ad
  network): a **picture ad** before Copy, a **rewarded video** before Export and Refresh,
  plus a persistent banner. If an ad can't load, the action still completes (users are never
  locked out).
- The admin Telegram pipeline (scraper → `vless_links` → `servers`) keeps the config list fresh.
- This plan is captured in [PUBLISH_PLAN.md](PUBLISH_PLAN.md) for future agents and maintainers.

## Auth email templates (branded)

RootNet's Supabase auth emails (confirm signup, password reset, magic link, invite, email change, and the 7 security notifications) use custom HTML templates in `rootnet-vpn/supabase/templates/`, styled to match the app's dark cyber-organic theme (deep forest `#0B1A12`, neon green `#4CFF88`).

- **Edit a template** → change the HTML in `rootnet-vpn/supabase/templates/<name>.html`, then **apply it to the hosted project** with `node rootnet-vpn/supabase/scripts/apply-email-templates.mjs`. The script reads credentials from `.env` (git-ignored) and pushes via the Supabase Management API (`mailer_templates_*` / `mailer_subjects_*` fields).
- **Sender address** → configured as a **Resend custom SMTP** (`smtp.resend.com:465`) via `node rootnet-vpn/supabase/scripts/configure-smtp.mjs`. Custom SMTP is required: Supabase's free tier blocks template editing when using its built-in email service.
- **Local dev** → the same templates are wired into `rootnet-vpn/supabase/config.toml` under `[auth.email.template.*]` and `[auth.email.notification.*]`. Restart with `supabase stop && supabase start` after editing.
- **⚠️ Test mode** → `RESEND_SENDER_EMAIL=onboarding@resend.dev` only delivers to the Resend account owner's inbox. Before real users rely on these emails, verify a sending domain at resend.com/domains, set `RESEND_SENDER_EMAIL=no-reply@<your-domain>` in `.env`, and re-run `configure-smtp.mjs` + `apply-email-templates.mjs`.
- **Secrets** → `SUPABASE_ACCESS_TOKEN` and `RESEND_API_KEY` are stored in `.env` and `agent_only.txt` (both git-ignored). Revoke the Supabase PAT after use — it has full account access.

## Quick Start

```bash
# Run the app
flutter run

# Analyze code
flutter analyze

# Run tests
flutter test
```

## Deployment

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for full deployment guide.

## Architecture

```
Android App (config launcher)  →  Supabase public REST (servers)  →  Supabase DB
   │   copy / export (ads)   →   your VPN client app (v2rayNG, NekoBox, …)
                                                ↕
                           Chob Group Hub (chobgroup.pages.dev)
                           VLESS server configs in DB
```

## Repo Layout — The RootNet Stack (3 components)

| Component | Path | Role |
|-----------|------|------|
| **RootNet app** | `rootnet-vpn/android-app/` | Native Android config launcher (Kotlin + Compose) |
| **Ingestion Worker** | `rootnet-vpn/vless-worker/` | Cloudflare Worker — receives scraped links, stores in Supabase |
| **Telegram Scraper** | `vlesshub/vless-scraper/` | Python (Telethon) — watches channels, extracts VLESS links |

### End-to-end server pipeline

```
Telegram channels
   ↓  (vlesshub/vless-scraper/main.py — Telethon listener)
Cloudflare Worker  (vless-worker — POST /webhook)
   ↓
Supabase vless_links  (dedup, 36h auto-cleanup)
   ↓  (rootnet-api POST /import-vless — admin key, idempotent)
Supabase servers  →  RootNet app server list
```

See [ROADMAP.md](ROADMAP.md) for the full product roadmap and what's left before publish.

## Key Links

| Service | URL |
|---------|-----|
| Chob Group Hub | https://chobgroup.pages.dev |
| RootNet Page | https://chobgroup.pages.dev/rootnet.html |
| GeoIP Tool | https://chobgroup.pages.dev/geoip.html |
| API (Supabase Edge Function) | https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/rootnet-api |
| GeoIP (Supabase Edge Function) | https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/geo-api |
| Supabase Dashboard | https://app.supabase.com/project/bprkazfxqmanrybiexnh |

## Project Pages

### Chob Group (`chobgroup.pages.dev`)
AI-crafted apps on free-tier infrastructure. Hub for all projects.

### GeoIP Worker (`/geoip.html`)
Live IP lookup tool — enter an IP, get country + flag emoji. Client-side validated.

### Reverse Proxy (`rootnet-proxy...`)
Serves chobgroup.pages.dev through workers.dev domain for regions where pages.dev is blocked.

---

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for full project context, deployment guide, and architecture details.