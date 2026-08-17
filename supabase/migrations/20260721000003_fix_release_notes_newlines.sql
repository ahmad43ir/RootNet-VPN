-- ============================================================
-- 📁 20260721000003_fix_release_notes_newlines.sql
-- ============================================================
-- RootNet VPN — Fix release_notes newlines
--
-- The initial migration inserted the default row with literal
-- backslash-n characters instead of actual newlines. This fixes
-- the existing row by updating it with the correct E'...' syntax.
-- ============================================================

UPDATE public.app_config
SET release_notes = E'• New RootNet branding\n• Cloudflare Worker backend\n• Encrypted server list\n• Ping & speed test improvements\n• 30-min session timer\n• Persistent VPN notification',
    updated_at = now()
WHERE id = 1
  AND release_notes LIKE '%\\n%';
