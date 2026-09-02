-- ============================================================
-- 20260806085114_add_vless_premium_only.sql
-- ============================================================
-- Supports premium-only community servers in the scrape pipeline.
--
-- The scraper (`vless-scraper/main.py`) and ingestion worker
-- (`vless-worker/src/index.js`) already send/detect a `premium_only`
-- flag (`premium|vip` regex or explicit body flag), but the
-- `vless_links` table has no such column — inserts would fail.
--
-- This migration:
--   1. Adds `premium_only` to `vless_links`.
--   2. Reworks `import_pending_vless_links` to propagate that flag
--      into `servers.premium_only` (instead of the hardcoded false).
-- ============================================================

-- ──────────────────────────────────────────────
-- 1. Column on vless_links
-- ──────────────────────────────────────────────
ALTER TABLE public.vless_links
    ADD COLUMN IF NOT EXISTS premium_only BOOLEAN NOT NULL DEFAULT false;

-- ──────────────────────────────────────────────
-- 2. Import RPC — propagate premium_only
--    Same grants as before: SECURITY DEFINER, exec only via service_role.
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
    SELECT id, link, premium_only
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

      -- ── Insert as a Community server (premium flag honored) ─
      BEGIN
        INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only)
        VALUES (left(v_name, 64), '🌐', 'Community', v_row.link, left(v_host, 255), v_port, true, v_row.premium_only);
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

-- 🔒 Lock it down — bypasses RLS, so only service_role may execute.
--    (Explicitly revoke from PUBLIC/anon/authenticated — they inherit EXECUTE
--    from PUBLIC and would otherwise reach the function via /rest/v1/rpc.)
REVOKE ALL ON FUNCTION public.import_pending_vless_links(integer) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.import_pending_vless_links(integer) TO service_role;

-- 🔒 Same hardening for the pre-existing SECURITY DEFINER cleanup function
--    (was left PUBLIC-executable by the original migration).
REVOKE ALL ON FUNCTION public.cleanup_old_vless_links(numeric) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.cleanup_old_vless_links(numeric) TO service_role;
