-- ============================================================
-- 📁 20260903000002_cap_table_sizes.sql
-- ============================================================
-- Auto-prune for the three user-facing content tables so they
-- never grow unbounded:
--     servers          (config "links" shown in the app / PWA)
--     vpn_files        (uploaded config attachments)
--     scraper_proxies  (MTProto proxy pool)
--
-- RULE (as requested, per table):
--     servers         ≥ 100 rows → delete the 50 oldest  (settles ~50)
--     vpn_files       ≥ 200 rows → delete the 100 oldest (settles ~100)
--     scraper_proxies ≥ 100 rows → delete the 50 oldest  (settles ~50)
--
-- Runs via pg_cron every 30 minutes (cheap: 3 COUNT(*) + rare
-- small DELETEs — only fires when a table is over the cap).
--
-- Deletion is permanent. "Oldest" = by insertion time:
--     servers.created_at, vpn_files.uploaded_at, scraper_proxies.added_at.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  Prune function
-- ──────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.prune_capped_tables()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_servers   INTEGER := 0;
  v_files     INTEGER := 0;
  v_proxies   INTEGER := 0;
BEGIN
  -- Links (servers): keep newest, drop the 50 oldest when ≥ 100 rows.
  IF (SELECT count(*) FROM public.servers) >= 100 THEN
    DELETE FROM public.servers
    WHERE id IN (
      SELECT id FROM public.servers
      ORDER BY created_at ASC NULLS LAST, id ASC
      LIMIT 50
    );
    GET DIAGNOSTICS v_servers = ROW_COUNT;
  END IF;

  -- VPN files (cap 200 → drop the 100 oldest when reached)
  IF (SELECT count(*) FROM public.vpn_files) >= 200 THEN
    DELETE FROM public.vpn_files
    WHERE id IN (
      SELECT id FROM public.vpn_files
      ORDER BY uploaded_at ASC NULLS LAST, id ASC
      LIMIT 100
    );
    GET DIAGNOSTICS v_files = ROW_COUNT;
  END IF;

  -- Proxy pool
  IF (SELECT count(*) FROM public.scraper_proxies) >= 100 THEN
    DELETE FROM public.scraper_proxies
    WHERE id IN (
      SELECT id FROM public.scraper_proxies
      ORDER BY added_at ASC NULLS LAST, id ASC
      LIMIT 50
    );
    GET DIAGNOSTICS v_proxies = ROW_COUNT;
  END IF;

  RETURN jsonb_build_object(
    'servers_deleted',         v_servers,
    'vpn_files_deleted',       v_files,
    'scraper_proxies_deleted', v_proxies
  );
END;
$$;

-- ──────────────────────────────────────────────
-- 2️⃣  pg_cron schedule (every 30 minutes)
-- ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_cron;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'prune-capped-every-30min') THEN
    PERFORM cron.unschedule('prune-capped-every-30min');
  END IF;
  PERFORM cron.schedule(
    'prune-capped-every-30min',
    '*/30 * * * *',
    $job$SELECT public.prune_capped_tables();$job$
  );
END $$;

-- ──────────────────────────────────────────────
-- 3️⃣  Manual trigger (optional):
--     SELECT public.prune_capped_tables();
-- ──────────────────────────────────────────────
