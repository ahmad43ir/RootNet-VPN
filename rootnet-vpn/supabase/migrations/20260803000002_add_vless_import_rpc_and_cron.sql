-- ============================================================
-- 📁 20260803000002_add_vless_import_rpc_and_cron.sql
-- ============================================================
-- Automatic import of scraped VLESS links into the servers table.
--
-- Two pieces:
--   1. `import_pending_vless_links(p_max_links)` — the single source
--      of truth for the import logic. Used by BOTH the rootnet-api
--      `/import-vless` edge-function endpoint (manual trigger) and
--      the pg_cron scheduled job (automatic trigger).
--   2. pg_cron job `import-vless-every-30min` — runs the import
--      every 30 minutes with no human/endpoint involvement.
--
-- SECURITY: the function is SECURITY DEFINER and bypasses RLS, so
-- execution is REVOKEd from PUBLIC and granted ONLY to service_role
-- (the edge function uses service_role; pg_cron runs as postgres).
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  IMPORT RPC — shared import logic
-- ──────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.import_pending_vless_links(p_max_links integer DEFAULT 200)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
  v_row           record;
  v_rest          text;
  v_query         text;
  v_fragment      text;
  v_name          text;
  v_host          text;
  v_port          integer;
  v_port_str      text;
  v_at            integer;
  v_existing      bigint;
  v_total         integer := 0;
  v_imported      integer := 0;
  v_skipped       integer := 0;
  v_unparsable    integer := 0;
  v_duplicate     integer := 0;
  v_insert_failed integer := 0;
BEGIN
  -- Guard the batch size
  IF p_max_links < 1 THEN p_max_links := 200; END IF;
  IF p_max_links > 500 THEN p_max_links := 500; END IF;

  -- Count pending links (for the response)
  SELECT count(*) INTO v_total
  FROM public.vless_links
  WHERE imported_to_servers = false;

  -- Process oldest pending links first (keeps the servers table fresh)
  FOR v_row IN
    SELECT id, link
    FROM public.vless_links
    WHERE imported_to_servers = false
    ORDER BY created_at ASC
    LIMIT p_max_links
  LOOP
    BEGIN
      -- ── Parse the VLESS URI ────────────────────────────────
      v_rest := v_row.link;
      IF v_rest IS NULL OR left(v_rest, 8) <> 'vless://' THEN
        RAISE EXCEPTION 'not a vless link';
      END IF;
      v_rest := substring(v_rest from 9);

      -- Name comes from the #fragment (e.g. vless://uuid@host:443#Tokyo-1)
      v_fragment := '';
      IF position('#' in v_rest) > 0 THEN
        v_fragment := substring(v_rest from position('#' in v_rest) + 1);
        v_rest := substring(v_rest for position('#' in v_rest) - 1);
      END IF;

      -- Keep the query string for the address=/port= fallback
      v_query := '';
      IF position('?' in v_rest) > 0 THEN
        v_query := substring(v_rest from position('?' in v_rest) + 1);
        v_rest := substring(v_rest for position('?' in v_rest) - 1);
      END IF;

      -- Authority: <uuid>@<host>:<port>
      v_host := '';
      v_port := 443;
      v_at := position('@' in v_rest);
      IF v_at > 0 THEN
        v_host := substring(v_rest from v_at + 1);
        IF position(':' in v_host) > 0 THEN
          v_port := substring(v_host from position(':' in v_host) + 1)::integer;
          v_host := substring(v_host for position(':' in v_host) - 1);
        END IF;
      END IF;

      -- Fallback: address=/port= query params (links without @host:port)
      IF v_host = '' AND v_query <> '' THEN
        v_host := coalesce((regexp_match(v_query, 'address=([^&]+)'))[1], '');
        v_port_str := coalesce((regexp_match(v_query, 'port=([0-9]+)'))[1], '');
        IF v_port_str <> '' THEN
          v_port := v_port_str::integer;
        END IF;
      END IF;

      -- Derive a friendly name from the fragment, then the host
      v_name := trim(both from v_fragment);
      IF v_name = '' THEN
        v_name := split_part(v_host, '.', 1);
      END IF;
      IF v_name = '' THEN
        v_name := 'Community';
      END IF;

      IF v_host = '' THEN
        RAISE EXCEPTION 'no host parseable';
      END IF;

      -- ── Idempotency: skip if this config already exists ────
      v_existing := NULL;
      SELECT id INTO v_existing
      FROM public.servers
      WHERE config = v_row.link
      LIMIT 1;

      IF v_existing IS NOT NULL THEN
        v_skipped := v_skipped + 1;
        v_duplicate := v_duplicate + 1;
        UPDATE public.vless_links SET imported_to_servers = true WHERE id = v_row.id;
        CONTINUE;
      END IF;

      -- ── Insert as a Community server ───────────────────────
      -- Inner block: a failed insert is counted separately and NOT marked
      -- imported, so it gets retried on the next run (same as the old
      -- TypeScript implementation in the edge function).
      BEGIN
        INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only)
        VALUES (left(v_name, 64), '🌐', 'Community', v_row.link, left(v_host, 255), v_port, true, false);
        v_imported := v_imported + 1;
        UPDATE public.vless_links SET imported_to_servers = true WHERE id = v_row.id;
      EXCEPTION WHEN others THEN
        v_skipped := v_skipped + 1;
        v_insert_failed := v_insert_failed + 1;
      END;
    EXCEPTION WHEN others THEN
      -- Parse failures / dedupe failures: junk links are marked imported
      -- so they aren't retried forever.
      v_skipped := v_skipped + 1;
      v_unparsable := v_unparsable + 1;
      UPDATE public.vless_links SET imported_to_servers = true WHERE id = v_row.id;
    END;
  END LOOP;

  RETURN jsonb_build_object(
    'success', true,
    'imported', v_imported,
    'skipped', v_skipped,
    'total', v_total,
    'skippedReasons', jsonb_build_object(
      'duplicate', v_duplicate,
      'unparsable', v_unparsable,
      'insert_failed', v_insert_failed
    )
  );
END;
$fn$;

-- 🔒 Lock it down — bypasses RLS, so only service_role may execute
REVOKE ALL ON FUNCTION public.import_pending_vless_links(integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.import_pending_vless_links(integer) TO service_role;

-- ──────────────────────────────────────────────
-- 2️⃣  PG_CRON — run the import automatically
-- ──────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pg_cron;

-- Idempotent: unschedule the job if it already exists, then (re)create it
DO $do$
BEGIN
  IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = 'import-vless-every-30min') THEN
    PERFORM cron.unschedule('import-vless-every-30min');
  END IF;

  PERFORM cron.schedule(
    'import-vless-every-30min',
    '*/30 * * * *',
    $$SELECT public.import_pending_vless_links(200);$$
  );
END
$do$;
