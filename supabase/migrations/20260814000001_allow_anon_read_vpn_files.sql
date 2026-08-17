-- ============================================================
-- 🔓 20260814000001_allow_anon_read_vpn_files.sql
-- ============================================================
-- The Android app's new "File" tab shows .npvt / .sip (and other) VPN
-- config files from `vpn_files`. The app reads Supabase REST as **anon**
-- (same as the `servers` list), so `vpn_files` needs a SELECT policy for
-- anon — both the list fields and `content` (needed for in-app Copy of a
-- file). Writes stay service_role only: there are no INSERT/UPDATE/DELETE
-- policies, so those are still denied for anon/authenticated.
-- ============================================================

DROP POLICY IF EXISTS "Anyone can view vpn files" ON public.vpn_files;
CREATE POLICY "Anyone can view vpn files"
  ON public.vpn_files
  FOR SELECT
  USING (true);
