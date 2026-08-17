-- ============================================================
-- 📁 20260818000004_add_scraper_proxies_deactivated_at.sql
-- ============================================================
-- vless-scraper/proxy_pool.py's deactivate() PATCHes
--   { "is_active": false, "deactivated_at": ... }
-- and cleanup_dead_proxies() filters on `deactivated_at`, but the
-- column was never created (migration 20260808000001 only made
-- id/host/port/secret/source/added_at/last_checked/last_ok/is_active).
-- Result: every dead-proxy deactivation returned HTTP 400 (PGRST204
-- "Could not find the 'deactivated_at' column"), dead proxies stayed
-- is_active=true forever, and the 3-day cleanup never ran.
-- This adds the column + an index for the cleanup query.
-- ============================================================

ALTER TABLE public.scraper_proxies
  ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS scraper_proxies_deactivated_idx
  ON public.scraper_proxies (is_active, deactivated_at);
