# RootNet — Secure VPN. Rooted in Privacy.

RootNet is a **Flutter VPN mobile app** that provides secure, blazing-fast internet access via the **VLESS/V2Ray** protocol.

- **Backend:** Supabase Edge Functions + Supabase (Auth + Database)
- **Protocol:** VLESS / V2Ray over WebSocket + TLS + Reality
- **UI:** Dark cyber-organic theme (neon green, deep forest)
- **Platform:** Android (with iOS, Web, Windows, macOS support)

## Features

- 🔐 Email/password authentication with Supabase
- 🌐 8 global VLESS servers (managed via Supabase DB)
- ⚡ Real-time connection speeds (upload/download)
- ⏱️ 30-minute ad-gated VPN session (each ad/free connection adds +30 min, capped at 60 min total)
- 🔔 Persistent Android notification with countdown + speeds
- 📡 Ping/latency testing per server with auto-sort
- 🛡️ Version gating — blocks outdated app versions
- 🎨 Premium cyber-organic UI with animated logo
- 🛑 Cancel button while connecting (orange stop icon)

## Publishing and monetization model

- Free mode: the admin Telegram account can publish VLESS links, free users can view them, and ads remain visible.
- Premium mode: premium users get an ad-free experience and help fund the scraper-bot workflow and related infrastructure.
- This plan is captured in [PUBLISH_PLAN.md](PUBLISH_PLAN.md) for future agents and maintainers.

## Auth email templates (branded)

RootNet's Supabase auth emails (confirm signup, password reset, magic link, invite, email change, and the 7 security notifications) use custom HTML templates in `supabase/templates/`, styled to match the app's dark cyber-organic theme (deep forest `#0B1A12`, neon green `#4CFF88`).

- **Edit a template** → change the HTML in `supabase/templates/<name>.html`, then **apply it to the hosted project** with `node supabase/scripts/apply-email-templates.mjs`. The script reads credentials from `.env` (git-ignored) and pushes via the Supabase Management API (`mailer_templates_*` / `mailer_subjects_*` fields).
- **Sender address** → configured as a **Resend custom SMTP** (`smtp.resend.com:465`) via `node supabase/scripts/configure-smtp.mjs`. Custom SMTP is required: Supabase's free tier blocks template editing when using its built-in email service.
- **Local dev** → the same templates are wired into `supabase/config.toml` under `[auth.email.template.*]` and `[auth.email.notification.*]`. Restart with `supabase stop && supabase start` after editing.
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
Flutter App  →  Supabase Edge Functions (JWT auth)  →  Supabase DB
                                                ↕
                           Chob Group Hub (chobgroup.pages.dev)
                           VLESS server configs in DB
```

## Repo Layout — The RootNet Stack (3 components)

| Component | Path | Role |
|-----------|------|------|
| **RootNet app** | `./` | Flutter VPN client (VLESS/V2Ray) |
| **Ingestion Worker** | `vless-worker/` | Cloudflare Worker — receives scraped links, stores in Supabase |
| **Telegram Scraper** | `vless-scraper/` | Python (Telethon) — watches channels, extracts VLESS links |

### End-to-end server pipeline

```
Telegram channels
   ↓  (vless-scraper/main.py — Telethon listener)
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
| GeoPoint Studio | https://iran-gis.pages.dev |
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