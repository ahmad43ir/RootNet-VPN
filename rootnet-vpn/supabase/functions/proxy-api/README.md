# 🌐 proxy-api — ProxyBox Edge Function

Serves 10 random **working MTProto proxies** (as `tg://proxy` links) from the
shared `scraper_proxies` pool — the same pool the RootNet Telegram scraper
maintains and tests. ProxyBox (`proxybox-app/`) is the only consumer.

| Endpoint | Method | Auth | Purpose |
|---|---|---|---|
| `/proxies` | GET | None (IP rate-limited) | 10 random active proxies `{ host, port, secret, source, link }` + `pool_size` / `working` counts |
| `/health` | GET | None | Health check |

## Deploy

```bash
cd supabase
npx supabase functions deploy proxy-api \
  --no-verify-jwt --project-ref bprkazfxqmanrybiexnh
```

- Requires **migration `20260808000001`** (creates `scraper_proxies`) to be
  applied, and the scraper / Telegram bot to have seeded the pool.
- No extra secrets: `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY` are injected
  automatically by Supabase.
- The pool is **service_role-only** (RLS with no policies) — the function is
  the only read path for the app.

## Verify

```bash
curl https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/proxy-api/proxies
# → { "proxies": [ { "host": "...", "port": 443, "secret": "...", "link": "tg://proxy?..." } ], "pool_size": 12, "working": 9 }
```

If `pool_size` is 0, seed the pool first: add an MTProto proxy to the bot
(`/addproxy`) and run `/scrape`, or set `PROXY_CHANNELS` on the scraper
workflow so it collects + tests proxies automatically.
