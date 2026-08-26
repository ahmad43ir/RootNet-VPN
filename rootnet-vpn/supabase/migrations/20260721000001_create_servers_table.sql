-- ============================================================
-- 📁 20260721000001_create_servers_table.sql
-- ============================================================
-- RootNet VPN — Server list migration
--
-- Moves server configs from the Cloudflare Worker into Supabase DB.
-- The Worker queries this table using the SERVICE_ROLE key (bypasses RLS).
-- RLS policies are set up so the anon key can read non-premium servers
-- (for future direct Supabase queries if needed).
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  CREATE servers TABLE
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.servers (
  id          BIGSERIAL PRIMARY KEY,
  name        TEXT NOT NULL,
  flag        TEXT NOT NULL DEFAULT '🌐',
  country     TEXT NOT NULL DEFAULT 'Global',
  config      TEXT NOT NULL,                -- Full VLESS URI
  host        TEXT DEFAULT '',              -- Hostname/IP (for landing page)
  port        INTEGER DEFAULT 443,          -- Server port (for landing page)
  is_active   BOOLEAN DEFAULT true,         -- Whether server is available
  premium_only BOOLEAN DEFAULT false,       -- Whether server requires premium
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- ──────────────────────────────────────────────
-- 2️⃣  INDEXES
-- ──────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_servers_is_active ON public.servers (is_active);
CREATE INDEX IF NOT EXISTS idx_servers_premium_only ON public.servers (premium_only);
CREATE INDEX IF NOT EXISTS idx_servers_name ON public.servers (name);

-- ──────────────────────────────────────────────
-- 3️⃣  ENABLE ROW LEVEL SECURITY
-- ──────────────────────────────────────────────
ALTER TABLE public.servers ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- 4️⃣  RLS POLICIES
-- ──────────────────────────────────────────────

-- ✅ Anyone (including anon) can read active, non-premium servers
--    Used by: Future direct Supabase queries from the landing page
DROP POLICY IF EXISTS "Anyone can view active non-premium servers" ON public.servers;
CREATE POLICY "Anyone can view active non-premium servers"
  ON public.servers
  FOR SELECT
  USING (is_active = true AND premium_only = false);

-- ✅ Authenticated users can read active non-premium servers
DROP POLICY IF EXISTS "Authenticated users can view active non-premium servers" ON public.servers;
CREATE POLICY "Authenticated users can view active non-premium servers"
  ON public.servers
  FOR SELECT
  TO authenticated
  USING (is_active = true AND premium_only = false);

-- ✅ Premium users (app_metadata.isPremium = true) can read ALL active servers
DROP POLICY IF EXISTS "Premium users can view all active servers" ON public.servers;
CREATE POLICY "Premium users can view all active servers"
  ON public.servers
  FOR SELECT
  TO authenticated
  USING (
    is_active = true
    AND (
      premium_only = false
      OR (current_setting('request.jwt.claims', true)::jsonb -> 'app_metadata' ->> 'isPremium')::boolean = true
    )
  );

-- ──────────────────────────────────────────────
-- 5️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.servers IS 'RootNet VPN server configurations — all VLESS configs live here';
COMMENT ON COLUMN public.servers.name IS 'Display name (e.g. Oak, Pine, Redwood)';
COMMENT ON COLUMN public.servers.flag IS 'Emoji flag (e.g. 🌐, 🇺🇸)';
COMMENT ON COLUMN public.servers.country IS 'Location label (e.g. Cloud, US, CDN)';
COMMENT ON COLUMN public.servers.config IS 'Full VLESS URI — sensitive! Only exposed via Worker API (encrypted) or service_role key';
COMMENT ON COLUMN public.servers.host IS 'Server hostname/IP for landing page display (no config details)';
COMMENT ON COLUMN public.servers.port IS 'Server port for landing page display';
COMMENT ON COLUMN public.servers.is_active IS 'Whether this server is currently available for connections';
COMMENT ON COLUMN public.servers.premium_only IS 'Whether this server requires a premium subscription';
