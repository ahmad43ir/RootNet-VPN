# 🔗 VLESS Ingestion API (vless-worker)

Cloudflare Worker that receives scraped VLESS links from the Telegram scraper
and stores them in Supabase (`vless_links` table). Part of the **RootNet stack**
(app + worker + scraper) — see the root [ROADMAP.md](../ROADMAP.md).

## Pipeline

```
Telegram channel → vless-scraper (Telethon) → POST /webhook → this worker → Supabase vless_links
                                                                                    ↓
                                            rootnet-api POST /import-vless → servers table
```

## Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/webhook` | Receive raw message, extract + dedupe + validate VLESS links, insert |
| POST | `/webhook/batch` | Receive pre-extracted links array |
| POST | `/cleanup` | Delete links older than 36h (default) |
| GET | `/health` | Health check with current link count |

## Secrets (required)

```bash
npx wrangler secret put SUPABASE_URL
npx wrangler secret put SUPABASE_SERVICE_ROLE_KEY
npx wrangler secret put WEBHOOK_API_KEY   # shared with the scraper (X-Webhook-Key header)
```

## Deploy

```bash
npx wrangler deploy
```

## Related

- Scraper: [`vless-scraper/`](../vless-scraper/) — the Telegram listener that calls `/webhook`
- Import endpoint: `rootnet-api` Edge Function → `POST /import-vless` (see `../supabase/functions/rootnet-api/index.ts`)
- **Automatic import:** pg_cron job `import-vless-every-30min` runs `import_pending_vless_links()` every 30 min (migration `../supabase/migrations/20260803000002_add_vless_import_rpc_and_cron.sql`) — no manual step needed
- DB tables: `vless_links` (migrations `20260727000002` + `20260803000001`), `servers` (import target)
