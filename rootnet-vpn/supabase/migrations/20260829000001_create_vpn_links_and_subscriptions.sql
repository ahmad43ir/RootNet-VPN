-- ============================================================
-- 📁 20260829000001_create_vpn_links_and_subscriptions.sql
-- ============================================================
-- Tables for the RootNet VPN Bot:
--   vpn_links         — Manually added VPN config links (separate from
--                        vless_links which is the scraper's pipeline)
--   vpn_subscriptions — Subscription URLs that return lists of configs
-- ============================================================

-- ──────────────────────────────────────────────
-- vpn_links — Manually managed VPN config links
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.vpn_links (
  id              BIGSERIAL PRIMARY KEY,
  link            TEXT NOT NULL UNIQUE,
  protocol        TEXT NOT NULL DEFAULT 'UNKNOWN',  -- VLESS, VMESS, TROJAN, SS, etc.
  source_channel  TEXT NOT NULL DEFAULT 'manual',   -- @channel or 'manual' or 'sub:Name'
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast dedup lookup (UNIQUE constraint already creates an index, but hash
-- is faster for exact-match lookups the bot does before insert)
CREATE INDEX IF NOT EXISTS idx_vpn_links_link ON public.vpn_links USING hash (link);

-- Fast listing by source
CREATE INDEX IF NOT EXISTS idx_vpn_links_source ON public.vpn_links (source_channel);

-- Fast listing by protocol
CREATE INDEX IF NOT EXISTS idx_vpn_links_protocol ON public.vpn_links (protocol);

-- ──────────────────────────────────────────────
-- vpn_subscriptions — Subscription URL management
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.vpn_subscriptions (
  id              BIGSERIAL PRIMARY KEY,
  name            TEXT NOT NULL UNIQUE,
  url             TEXT NOT NULL,
  active          BOOLEAN NOT NULL DEFAULT true,
  link_count      INTEGER NOT NULL DEFAULT 0,
  last_fetched    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────
-- ROW LEVEL SECURITY — service_role only
-- ──────────────────────────────────────────────
ALTER TABLE public.vpn_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.vpn_subscriptions ENABLE ROW LEVEL SECURITY;

-- No public policies — both tables are service_role only (bot writes,
-- app reads via the API worker which also uses service_role).

-- ──────────────────────────────────────────────
-- COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.vpn_links IS 'Manually managed VPN config links for the RootNet VPN Bot. Separate from vless_links (scraper pipeline).';
COMMENT ON COLUMN public.vpn_links.protocol IS 'Detected protocol: VLESS, VMESS, TROJAN, SS, SOCKS, WIREGUARD, etc.';
COMMENT ON COLUMN public.vpn_links.source_channel IS 'Source: @channel name, ''manual'', or ''sub:SubscriptionName''';

COMMENT ON TABLE public.vpn_subscriptions IS 'Subscription URLs managed by the RootNet VPN Bot. Each sub URL is fetched periodically to import links.';
COMMENT ON COLUMN public.vpn_subscriptions.link_count IS 'Number of links found on last fetch';
COMMENT ON COLUMN public.vpn_subscriptions.last_fetched IS 'When the subscription was last re-fetched';
