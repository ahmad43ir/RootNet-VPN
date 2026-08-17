# 🤖 Telegram Bot (Supabase Edge Function)

Replacement for `telegram-bot/bot.py`. The Python bot ran a long-poll
loop on a server you had to keep alive; this version runs on Supabase
Edge Functions in **webhook mode** — no server, no downtime, no cost
within the function quota.

## What changed vs the Python bot

| | Python bot | Edge function |
|---|---|---|
| Mode | Long polling | Webhook (Telegram → Supabase) |
| State | In-memory dicts (reset on restart) | `bot_chat_state` table |
| Runtime | Your machine / VPS | Supabase Edge (Deno) |
| GeoIP | `socket` + geo-api | `Deno.resolveDns`/DoH + geo-api |

Feature set is identical: upload configs (text or
`.txt` / `.npv` / `.npvt` / `.json`), list & multi-delete servers, `web pages`
quick links, `/stats`, `/backfillflags`, `/myid`, and the allowlisted
admin check.

Plus:
- **Channel naming** — servers keep the channel that posted them:
  the `tel:@...` / `telegram:@...` / `t.me/...` text at the end of a
  link is parsed into the channel handle, and servers are named
  `<channel> <number>` in upload order (e.g. `@mychannel 1`,
  `@mychannel 2`). Links without a channel fall back to the derived
  hostname name.
- **❓ Help** — a help button on the main keyboard (and `/help`)
  explains what each menu option does.

## Structure

```
supabase/functions/telegram-bot/
├── index.ts        — entry: routing, webhook secret check, admin endpoints
├── _handlers.ts    — messages / commands / inline callbacks + menus
├── _telegram.ts    — minimal Telegram Bot API client (fetch-based)
├── _parser.ts      — config URI parsing + name/type/host derivation
├── _db.ts          — servers CRUD via service_role
├── _geo.ts         — host→IP resolution + geo-api lookup
├── _state.ts       — bot_chat_state / bot_config persistence
└── _utils.ts       — jsonResponse, cors, env, markdown escaping
```

## Deploy

```bash
# 1. Apply the state migration (tables bot_chat_state + bot_config)
node supabase/scripts/apply-migration.mjs \
  supabase/migrations/20260807000002_create_bot_state_and_config.sql

# 2. Set secrets (once)
npx supabase secrets set --project-ref bprkazfxqmanrybiexnh \
  BOT_TOKEN="<from @BotFather>" \
  ADMIN_IDS="<your telegram user id>" \
  ADMIN_KEY="<shared secret — reuse the rootnet-api one>"

# 3. Deploy the function
npx supabase functions deploy telegram-bot \
  --project-ref bprkazfxqmanrybiexnh --no-verify-jwt

# 4. Register the webhook (function URL is fixed; the old Python bot
#    has been retired & deleted, so nothing conflicts anymore)
curl -X POST https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/telegram-bot/setwebhook \
  -H "X-Admin-Key: <ADMIN_KEY>"

# 5. Verify
curl -X POST https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/telegram-bot/getwebhookinfo \
  -H "X-Admin-Key: <ADMIN_KEY>"
```

The webhook `secret_token` is generated on first `setwebhook` and
persisted in `bot_config` (never logged, never returned to Telegram
logs). Every incoming update must carry the matching
`X-Telegram-Bot-Api-Secret-Token` header or it is rejected with 401.

## Rollback

The Python bot (`telegram-bot/bot.py`) has been **retired and deleted** —
it is no longer an option. To roll back to a previous edge-function
version instead, redeploy that version via `supabase functions deploy`.
Use `deletewebhook` only if you want to pause bot updates entirely:

```bash
curl -X POST https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/telegram-bot/deletewebhook \
  -H "X-Admin-Key: <ADMIN_KEY>"
```

## Notes

- Only allowlisted Telegram user IDs (`ADMIN_IDS`) may use the bot.
- Inserting a server performs a best-effort GeoIP lookup against the
  existing `geo-api` function; failures fall back to the defaults.
- The 150 ms pacing between inserts keeps `geo-api`'s rate limiter happy.
- The webhook reply is sent after processing, so very large uploads can
  take a while. Dedupe (on the `config` column) makes retries safe.
