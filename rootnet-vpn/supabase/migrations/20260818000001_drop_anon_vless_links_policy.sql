-- ============================================================
-- 📁 20260818000001_drop_anon_vless_links_policy.sql
-- ============================================================
-- v2: `vless_links` is an INTERNAL staging table (scraper → worker →
-- import RPC → servers). Nothing reads it with the anon key:
--   • the app reads `servers` (public RLS) and `vpn_files` (File tab)
--   • the landing pages / rootnet-proxy don't touch it
--   • the pipeline uses service_role (bypasses RLS)
-- The v1 policy "Anyone can view vless_links" (USING (true)) exposes
-- raw scraped configs over the public REST API for no reason and
-- contradicts the documented posture (service_role only).
-- Drop it — the pipeline is unaffected.
-- ============================================================

DROP POLICY IF EXISTS "Anyone can view vless_links" ON public.vless_links;
