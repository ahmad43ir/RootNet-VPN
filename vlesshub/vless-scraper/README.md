# 🕵️ VLESS Telegram Scraper

> Persistent, event-driven Telegram scraper that extracts VLESS links from channels and stores them in Supabase. Links older than 36 hours are automatically deleted.

## Architecture

### Hybrid Pipeline (Default)

```
Telegram Channel
      ↓ (events.NewMessage)
Telethon Listener
      ↓ (POST /webhook)
Cloudflare Worker (vless-ingestion-api)
      ↓
Supabase (vless_links table)
      │
      ├── Deduplication
      ├── Validation
      └── Auto-cleanup (> 36h old)
```

### Direct Fallback (Legacy)

```
Telegram Channel  ──→  Telethon (events.NewMessage)  ──→  Supabase (vless_links table)
```

- **Event-driven** — no polling, no loops checking every second
- **Run-once mode** — `RUN_ONCE=1` fetches recent messages then exits (for free scheduled runners like GitHub Actions cron)
- **Hybrid by default** — scraper forwards messages to Cloudflare Worker via webhook
- **Fallback support** — if `WEBHOOK_URL` is not set, falls back to direct Supabase writes
- **StringSession** — no file-based sessions, works on ephemeral deployments
- **Deduplication & Validation** — handled by the Worker (or Supabase UNIQUE constraint)
- **Auto-cleanup** — deletes links older than 36 hours via Worker API or direct DB
- **FloodWait handling** — sleeps and retries automatically

## Files

| File | Purpose |
|------|---------|
| `main.py` | The scraper — entry point and all logic |
| `requirements.txt` | Python dependencies |
| `.env.example` | Environment variable template |

## Quick Start (Local Testing)

### 1. Prerequisites

```bash
cd vless-scraper
python -m venv venv
source venv/bin/activate   # Linux/macOS
venv\Scripts\activate      # Windows
pip install -r requirements.txt
```

### 2. Generate a Telegram Session

You need a **StringSession** (not a file session):

```bash
python -c "
from telethon.sessions import StringSession
from telethon import TelegramClient
import asyncio

API_ID = 123456       # ← Your API ID
API_HASH = 'your_hash'  # ← Your API hash

async def main():
    client = TelegramClient(StringSession(), API_ID, API_HASH)
    await client.start()
    print('Session:', client.session.save())
    await client.disconnect()

asyncio.run(main())
```

This will prompt you for your **phone number** and **verification code**. Paste the printed session string into your `.env`.

> ⚠️ The account must be a **member** of the channels you want to scrape.

### 3. Configure & Run

```bash
cp .env.example .env
# Edit .env with your credentials

python main.py
```

## Deploy with GitHub Actions (free — recommended)

This is the **$0 / no-credit-card** option. The scraper runs in **run-once mode**
**on demand** (triggered by the Telegram bot `/scrape` command): each run connects
to Telegram, fetches the latest **3 messages** from each VLESS channel and
**1 message** from each proxy channel, extracts VLESS links and proxies, tests
up to **3 proxies** (newly scraped first, then oldest-untested from pool), and
POSTs them to the vless-ingestion-api worker. The worker deduplicates overlaps,
so delayed or missed runs are harmless. The pg_cron import (every 30 min) then
moves new links from `vless_links` → `servers` automatically.

**Scheduling is OFF by default** (see AGENTS.md — running every 30 min would
exceed Telegram free-tier flood limits and GitHub Actions quota). The bot's
`/scrape` command dispatches the workflow manually via `workflow_dispatch`.

| Secret | Value |
|--------|-------|
| `API_ID` | Telegram API ID (from my.telegram.org) |
| `API_HASH` | Telegram API hash |
| `TELEGRAM_SESSION` | StringSession string |
| `CHANNELS` | Comma-separated VLESS channel usernames |
| `PROXY_CHANNELS` | Comma-separated MTProto proxy channel usernames (optional) |
| `WEBHOOK_URL` | `https://vless-ingestion-api.mobileahmad43-a18.workers.dev` |
| `WEBHOOK_API_KEY` | From `credentials/.env` |
| `SUPABASE_URL` | `https://bprkazfxqmanrybiexnh.supabase.co` |
| `SUPABASE_KEY` | `service_role` key (fallback only) |

Until the Telegram credentials are added, the workflow **skips gracefully**
instead of failing.

**4. Watch it run** — GitHub → Actions → vless-scraper → runs on demand via
`/scrape` in Telegram. Logs show `✅ Connected as @...` and `📊 Run-once summary:
N channel(s), ...`.

## Deploy to Railway (paid — alternative)

## Deploy to Fly.io

```bash
fly launch
fly secrets set API_ID=123 API_HASH=abc TELEGRAM_SESSION=... SUPABASE_URL=... SUPABASE_KEY=... CHANNELS=...
fly deploy
```

## Database Schema

The scraper uses a `vless_links` table in your Supabase project:

```sql
CREATE TABLE public.vless_links (
    id              BIGSERIAL PRIMARY KEY,
    link            TEXT NOT NULL UNIQUE,
    source_channel  TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

The migration file is at `../supabase/migrations/20260727000002_create_vless_links_table.sql`.

Run it:

```bash
cd ..
npx supabase db push
```

## Helper Functions (in Supabase)

| Function | Description |
|----------|-------------|
| `get_vless_link_age(link_id)` | Returns hours since creation |
| `cleanup_old_vless_links(max_hours)` | Deletes links older than N hours, returns count |
| `get_active_vless_links(max_hours)` | Returns active links with age in hours |

## Monitoring

The scraper logs to stdout:

```
2026-07-27 12:00:00 [INFO] ✅ Connected as @your_bot
2026-07-27 12:00:00 [INFO]   📡 Listening to @vpn_channel (ID: 123456789)
2026-07-27 12:00:00 [INFO] 🚀 Listening on 2 channel(s). Waiting for messages...
2026-07-27 12:05:00 [INFO] 📨 New message from @vpn_channel (342 chars)
2026-07-27 12:05:00 [INFO] 🔗 Found 3 VLESS link(s) in message
2026-07-27 12:05:00 [INFO]   ✅ Inserted: vless://abc123... (from @vpn_channel)
2026-07-27 12:05:01 [INFO]   → Duplicate, skipped: vless://abc123...
2026-07-27 12:05:01 [INFO]   📊 Total links in DB: 47
2026-07-27 13:00:00 [INFO] 🧹 Cleaned up 12 old link(s) (> 36h old)
```

## Configuration Reference

| Environment Variable | Required | Default | Description |
|---------------------|----------|---------|-------------|
| `API_ID` | ✅ Yes | — | Telegram API ID from my.telegram.org |
| `API_HASH` | ✅ Yes | — | Telegram API hash |
| `TELEGRAM_SESSION` | ✅ Yes | — | Telethon StringSession (NOT file session) |
| `SUPABASE_URL` | ✅ Yes | — | Supabase project URL |
| `SUPABASE_KEY` | ✅ Yes | — | Supabase `service_role` key |
| `CHANNELS` | ✅ Yes | — | Comma-separated VLESS channel usernames |
| `PROXY_CHANNELS` | ❌ No | — | Comma-separated MTProto proxy channel usernames |
| `CLEANUP_INTERVAL` | ❌ No | `60` | Cleanup interval in minutes (persistent mode) |
| `RUN_ONCE` | ❌ No | `0` | `1` = fetch recent messages then exit (for cron runners) |
| `RUN_ONCE_MAX_MESSAGES` | ❌ No | `3` | Messages scanned per VLESS channel in run-once mode |

## Security Notes

- **`service_role` key** is required because the scraper needs to insert and delete data
- The session string is **never committed to version control**
- The account used for the session **must be a member of the target channels**
- For production, use a dedicated Telegram account (not your personal one)
