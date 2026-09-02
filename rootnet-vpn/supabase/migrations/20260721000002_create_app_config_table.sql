-- ============================================================
-- 📁 20260721000002_create_app_config_table.sql
-- ============================================================
-- RootNet VPN — App config migration
--
-- Moves VERSION_INFO from worker.js into Supabase DB so that
-- version settings can be updated without redeploying the Worker.
-- The Worker caches this in-memory and refreshes periodically.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  CREATE app_config TABLE
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.app_config (
  id              INTEGER PRIMARY KEY DEFAULT 1,
  latest_version  TEXT NOT NULL DEFAULT '1.0.0',
  latest_build    INTEGER NOT NULL DEFAULT 1,
  minimum_version TEXT NOT NULL DEFAULT '1.0.0',
  update_url      TEXT NOT NULL DEFAULT 'https://chobgroup.pages.dev',
  release_notes   TEXT NOT NULL DEFAULT '',
  force_update    BOOLEAN NOT NULL DEFAULT false,
  updated_at      TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT single_row CHECK (id = 1)
);

-- ──────────────────────────────────────────────
-- 2️⃣  INSERT DEFAULT ROW (only if empty)
-- ──────────────────────────────────────────────
INSERT INTO public.app_config (id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update)
SELECT 1, '1.1.2', 3, '1.0.0', 'https://chobgroup.pages.dev',
       E'• New RootNet branding\n• Supabase Edge Function backend\n• Encrypted server list\n• Ping & speed test improvements\n• 30-min session timer\n• Persistent VPN notification',
       false
WHERE NOT EXISTS (SELECT 1 FROM public.app_config WHERE id = 1);

-- ──────────────────────────────────────────────
-- 3️⃣  ENABLE ROW LEVEL SECURITY
-- ──────────────────────────────────────────────
ALTER TABLE public.app_config ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- 4️⃣  RLS POLICIES
-- ──────────────────────────────────────────────

-- ✅ Anyone (including anon) can read the app config
--    Version info is not sensitive; rate limiting protects the endpoint.
DROP POLICY IF EXISTS "Anyone can view app config" ON public.app_config;
CREATE POLICY "Anyone can view app config"
  ON public.app_config
  FOR SELECT
  USING (true);

-- ──────────────────────────────────────────────
-- 5️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.app_config IS 'RootNet VPN application configuration — version info, update URLs, etc.';
COMMENT ON COLUMN public.app_config.latest_version IS 'Latest version string (e.g. 1.1.2)';
COMMENT ON COLUMN public.app_config.latest_build IS 'Latest build number';
COMMENT ON COLUMN public.app_config.minimum_version IS 'Minimum app version allowed to connect';
COMMENT ON COLUMN public.app_config.update_url IS 'URL where users can download the latest version';
COMMENT ON COLUMN public.app_config.release_notes IS 'Release notes for the latest version';
COMMENT ON COLUMN public.app_config.force_update IS 'Whether to force all users to update immediately';
