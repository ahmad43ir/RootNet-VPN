-- ============================================================
-- 📁 20260808000001_create_scraper_config_and_proxies.sql
-- ============================================================
-- Shared persistence between the Telegram manager bot and the
-- Python VLESS scraper (vless-scraper/).
--
--   scraper_config  — key/value runtime settings for the scraper:
--                     `vless_channels` (comma-separated channels the
--                     scraper listens to) can be managed from the bot.
--   scraper_proxies — the MTProto proxy pool the scraper connects
--                     through. The scraper collects proxies from the
--                     proxy channels, tests them, prunes dead ones,
--                     and rotates through the working pool. The bot
--                     can add/remove proxies manually.
--
-- SECURITY: RLS enabled with NO policies. Only service_role (used by
-- the edge function and the scraper) can read/write.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  scraper_config — scraper runtime settings
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.scraper_config (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────
-- 2️⃣  scraper_proxies — MTProto proxy pool
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.scraper_proxies (
  id           BIGSERIAL PRIMARY KEY,
  host         TEXT NOT NULL,
  port         INTEGER NOT NULL,
  secret       TEXT,
  source       TEXT,
  added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_checked TIMESTAMPTZ,
  last_ok      BOOLEAN,
  is_active    BOOLEAN NOT NULL DEFAULT true,
  CONSTRAINT scraper_proxies_host_port_key UNIQUE (host, port)
);

CREATE INDEX IF NOT EXISTS scraper_proxies_active_idx
  ON public.scraper_proxies (is_active, last_ok);

-- ──────────────────────────────────────────────
-- 3️⃣  ROW LEVEL SECURITY — no policies at all
-- ──────────────────────────────────────────────
ALTER TABLE public.scraper_config ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.scraper_proxies ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- 4️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.scraper_config IS 'Scraper runtime settings (vless_channels). service_role only.';
COMMENT ON TABLE public.scraper_proxies IS 'MTProto proxy pool for the VLESS scraper connection. service_role only.';
