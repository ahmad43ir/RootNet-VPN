-- ============================================================
-- 📁 20260813000003_cleanup_dormant_v1_tables.sql — ⚠️ DRAFT
-- ============================================================
-- RootNet v2.2 is a config launcher with NO accounts, NO FCM push,
-- NO premium/free-quota. These v1 objects are dormant (unused by the
-- app, the bot, and the scraper pipeline):
--
--   1. `device_tokens`            — FCM push token registry (auth-era)
--   2. `free_connection_quota`    — daily free-connection counter (v1
--                                   ad-unavailable fallback, auth users)
--   3. `claim_free_connection()`  — the RPC that wrote that counter
--
-- ⚠️ DESTRUCTIVE: any rows in these tables will be lost. They have no
--    live writers in v2, but REVIEW before applying:
--      npx supabase db push --dry-run        # see what would run
--      npx supabase migration list --linked  # confirm it's pending
--
-- ⚠️ After applying, rootnet-api's `/free-connection` route will 500
--    (the RPC won't exist). The route is unused by the v2 app; remove
--    the handler from `supabase/functions/rootnet-api/index.ts` and
--    redeploy if you want the route gone for good.
--
-- Kept (still live): `rate_limits`, `request_ids` (anti-replay),
-- `scraper_config`, `scraper_proxies`, `bot_chat_state`, `vpn_files`.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1. device_tokens (FCM push registry)
--    Drops table + its RLS policies + triggers + indexes automatically.
-- ──────────────────────────────────────────────
DROP TABLE IF EXISTS public.device_tokens;

-- ──────────────────────────────────────────────
-- 2. free_connection_quota (v1 free-connection counter)
-- ──────────────────────────────────────────────
DROP TABLE IF EXISTS public.free_connection_quota;

-- ──────────────────────────────────────────────
-- 3. claim_free_connection(user_id) RPC
-- ──────────────────────────────────────────────
DROP FUNCTION IF EXISTS public.claim_free_connection(UUID);
