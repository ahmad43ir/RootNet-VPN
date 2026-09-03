-- ============================================================
-- 📁 20260903000001_optimize_query_indexes.sql
-- ============================================================
-- Adds indexes for the hot query paths found by auditing the
-- PWA / API / scraper code against the live schema (Sept 2026).
--
-- Review summary (what was checked and found COVERED already):
--   vpn_files   → uploaded_at DESC index exists (PWA Files tab
--                 orders by uploaded_at); bot orders by id (PK).
--   geoip_cache → idx_geoip_cache_fetched covers cleanup.
--   scraper_config / bot tables → keyed lookups only.
--
-- What this migration adds and why:
--   1. servers(created_at DESC)   — PWA Links tab orders by
--        created_at desc + limit (direct Supabase REST read).
--   2. rate_limits(last_request)  — cleanup_stale_records()
--        DELETEs WHERE last_request < now()-24h; the existing
--        index is on window_start, so the cleanup was a full
--        table scan every run.
--   3. scraper_proxies(is_active, last_checked DESC) — proxy-api
--        GET /proxies filters is_active=true and orders by
--        last_checked desc (limit 100) on every app request.
--        (The existing (is_active, last_ok) index can't serve
--        the last_checked ordering.)
--
-- All CREATE INDEX ... IF NOT EXISTS → safe to apply repeatedly.
-- ============================================================

-- 1️⃣  servers — newest-first list (PWA Links tab)
--     Query: SELECT * FROM servers ORDER BY created_at DESC LIMIT 500
CREATE INDEX IF NOT EXISTS idx_servers_created_at
  ON public.servers (created_at DESC);

-- 2️⃣  rate_limits — cleanup_stale_records() delete predicate
--     Query: DELETE FROM rate_limits WHERE last_request < now() - '24 hours'
CREATE INDEX IF NOT EXISTS idx_rate_limits_last_request
  ON public.rate_limits (last_request);

-- 3️⃣  scraper_proxies — proxy-api /proxies hot read
--     Query: SELECT ... WHERE is_active = true
--            ORDER BY last_checked DESC NULLS LAST LIMIT 100
CREATE INDEX IF NOT EXISTS idx_scraper_proxies_active_last_checked
  ON public.scraper_proxies (is_active, last_checked DESC);
