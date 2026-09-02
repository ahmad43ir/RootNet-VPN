-- ============================================================
-- 📁 20260809000001_generic_config_import_rpc.sql
-- ============================================================
-- Replaces the VLESS-only import RPC with a generic multi-scheme
-- parser. The scraper / ingestion worker now store configs for every
-- protocol (VLESS, VMess, Trojan, SS/SSR, Hysteria2, WireGuard, SOCKS,
-- ...) and NPV attachments, so the import step must understand them all:
--
--   vless/trojan/wireguard/hysteria2/hysteria/hy2/tuic/socks*/ssh
--     → generic `[userinfo@]host[:port]` authority, #fragment = name,
--       ?address= / ?port= fallback for links without an authority.
--   vmess  → base64 JSON payload  → add/port/ps.
--   ssr    → base64 `host:port:...` payload → first two fields.
--   ss     → plain or base64 userinfo `method:password@host:port`, or
--            fully base64 `base64(method:password@host:port)`.
--
-- `servers.type` is now set to the mapped protocol (was hardcoded
-- 'vless') and `servers.config_format` to 'link'.
-- Signature / grants / pg_cron job are unchanged.
-- ============================================================

CREATE OR REPLACE FUNCTION public.import_pending_vless_links(p_max_links integer DEFAULT 200)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $fn$
DECLARE
  v_row           record;
  v_scheme        text;
  v_rest          text;
  v_query         text;
  v_fragment      text;
  v_authority     text;
  v_payload       text;
  v_decoded       text;
  v_pad           integer;
  v_name          text;
  v_ps            text;
  v_host          text;
  v_port          integer;
  v_port_str      text;
  v_type          text;
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
       -- ── SIP JSON format detection ─────────────────────────────
       IF left(trim(v_row.link), 1) = '{' THEN
         -- Try to parse as SIP JSON: {"protocol":"ssh|socks|...", "host":"...", "port":...}
         BEGIN
           DECLARE
             v_sip_proto    text;
             v_sip_host     text;
             v_sip_port     integer;
           BEGIN
             v_sip_proto := lower(trim(both '"' from (coalesce(
               regexp_match(v_row.link, '"protocol"\s*:\s*"([^"]+)"'),
               regexp_match(v_row.link, '"type"\s*:\s*"([^"]+)"')
             ))[1], ''));
             v_sip_host := trim(both '"' from (coalesce(
               regexp_match(v_row.link, '"host"\s*:\s*"([^"]+)"'),
               regexp_match(v_row.link, '"address"\s*:\s*"([^"]+)"'),
               regexp_match(v_row.link, '"server"\s*:\s*"([^"]+)"')
             ))[1], '');

             -- FIX (2026-08-13): `coalesce(...)[1]` is invalid Postgres — the
             -- calls above are wrapped in parens so the subscript applies to
             -- the result. Same fix applied in migration 20260813000002.
             v_sip_port := coalesce(
               (regexp_match(v_row.link, '"port"\s*:\s*([0-9]+)'))[1], ''
             )::integer;
 
             IF v_sip_proto = '' OR v_sip_host = '' THEN
               RAISE EXCEPTION 'not a valid sip config';
             END IF;
 
             -- Map SIP protocol to canonical type
             v_type := CASE
               WHEN v_sip_proto IN ('ssh', 'socks', 'socks4', 'socks5') THEN 'socks'
               WHEN v_sip_proto IN ('http', 'https') THEN 'socks'
               ELSE 'socks'
             END;
             v_host := v_sip_host;
             v_port := coalesce(v_sip_port, CASE
               WHEN v_sip_proto = 'ssh' THEN 22
               WHEN v_sip_proto IN ('http', 'https') THEN 8080
               ELSE 1080
             END);
             v_name := 'SIP-' || upper(v_sip_proto);
 
             -- Skip if duplicate
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
 
             INSERT INTO public.servers
               (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
             VALUES
               (left(v_name, 64), '🌐', 'Community', v_row.link, left(v_host, 255), v_port, true, v_row.premium_only, v_type, 'sip');
             v_imported := v_imported + 1;
             UPDATE public.vless_links SET imported_to_servers = true WHERE id = v_row.id;
             CONTINUE;
           END;
         EXCEPTION WHEN others THEN
           -- Not a valid SIP JSON, fall through to URI parsing
         END;
       END IF;
 
       -- ── Scheme ─────────────────────────────────────────────
      v_scheme := lower(coalesce((regexp_match(v_row.link, '^([a-zA-Z0-9]+)://'))[1], ''));
      IF v_scheme IS NULL OR v_scheme = '' THEN
        RAISE EXCEPTION 'unknown scheme';
      END IF;

      v_rest := substring(v_row.link from position('://' in v_row.link) + 3);
      v_query := '';
      v_fragment := '';

      -- Strip the query string (everything from the first '?')
      IF position('?' in v_rest) > 0 THEN
        v_query := substring(v_rest from position('?' in v_rest) + 1);
        v_rest := substring(v_rest for position('?' in v_rest) - 1);
      END IF;

      -- Strip the #fragment → name
      IF position('#' in v_rest) > 0 THEN
        v_fragment := substring(v_rest from position('#' in v_rest) + 1);
        v_rest := substring(v_rest for position('#' in v_rest) - 1);
      END IF;

      v_host := '';
      v_port := 443;

      IF v_scheme IN ('vmess', 'ssr') THEN
        -- ── Base64 payload schemes ────────────────────────────
        v_payload := v_rest;
        IF left(v_payload, 1) = '/' THEN v_payload := substring(v_payload from 2); END IF;
        IF v_payload = '' THEN RAISE EXCEPTION 'empty payload'; END IF;
        v_pad := (4 - (length(v_payload) % 4)) % 4;
        v_decoded := convert_from(
          decode(
            replace(replace(v_payload, '-', '+'), '_', '/') || repeat('=', v_pad),
            'base64'
          ),
          'UTF8'
        );

        IF v_scheme = 'vmess' THEN
          v_host := coalesce((regexp_match(v_decoded, '"add"\s*:\s*"([^"]+)"'))[1], '');
          v_port_str := coalesce((regexp_match(v_decoded, '"port"\s*:\s*([0-9]+)'))[1], '');
          v_ps := coalesce((regexp_match(v_decoded, '"ps"\s*:\s*"([^"]*)"'))[1], '');
        ELSE -- ssr: <host>:<port>:<protocol>:<method>:<obfs>:<password>...
          v_host := split_part(v_decoded, ':', 1);
          v_port_str := split_part(v_decoded, ':', 2);
        END IF;

        IF v_host = '' THEN RAISE EXCEPTION 'no host in payload'; END IF;
        IF v_port_str ~ '^[0-9]+$' THEN v_port := v_port_str::integer; END IF;
      ELSE
        -- ── Generic authority schemes ─────────────────────────
        IF v_scheme IN ('ss', 'shadowsocks') AND position('@' in v_rest) = 0 THEN
          -- ss://base64(method:password@host:port) — all-in-one payload
          v_pad := (4 - (length(v_rest) % 4)) % 4;
          v_decoded := convert_from(
            decode(
              replace(replace(v_rest, '-', '+'), '_', '/') || repeat('=', v_pad),
              'base64'
            ),
            'UTF8'
          );
          IF position('@' in v_decoded) > 0 THEN
            v_authority := substring(v_decoded from position('@' in v_decoded) + 1);
          ELSE
            RAISE EXCEPTION 'no host in ss payload';
          END IF;
        ELSE
          -- Everything after the last '@' is the authority
          v_authority := v_rest;
          IF position('@' in v_rest) > 0 THEN
            v_authority := substring(v_rest from position('@' in v_rest) + 1);
          END IF;
        END IF;

        v_host := v_authority;
        IF v_host LIKE '[%' THEN
          -- IPv6: [addr]:port
          v_host := substring(v_host from '^\[([^\]]+)\]');
          v_port_str := coalesce((regexp_match(v_authority, '^\[[^\]]+\]:(\d+)'))[1], '');
          IF v_port_str <> '' THEN v_port := v_port_str::integer; END IF;
        ELSIF position(':' in v_host) > 0 THEN
          -- Split host:port at the LAST colon (hosts have no other ':')
          v_port_str := split_part(v_host, ':', array_length(string_to_array(v_host, ':'), 1));
          IF v_port_str ~ '^[0-9]+$' THEN
            v_port := v_port_str::integer;
            v_host := substring(v_host for length(v_host) - length(v_port_str) - 1);
          END IF;
        END IF;

        -- Fallback: address=/port= query params (links without authority)
        IF v_host = '' AND v_query <> '' THEN
          v_host := coalesce((regexp_match(v_query, 'address=([^&]+)'))[1], '');
          v_port_str := coalesce((regexp_match(v_query, 'port=([0-9]+)'))[1], '');
          IF v_port_str <> '' THEN v_port := v_port_str::integer; END IF;
        END IF;
      END IF;

      IF v_host = '' THEN
        RAISE EXCEPTION 'no host parseable';
      END IF;

      -- ── Name: fragment → VMess "ps" → host label → fallback ─
      v_name := trim(both from coalesce(v_fragment, ''));
      IF v_name = '' AND v_scheme = 'vmess' THEN
        v_name := trim(both from coalesce(v_ps, ''));
      END IF;
      IF v_name = '' THEN
        v_name := split_part(v_host, '.', 1);
      END IF;
      IF v_name = '' THEN
        v_name := 'Community';
      END IF;

      -- ── Type: map aliases to canonical wire names ───────────
      v_type := CASE
        WHEN v_scheme IN ('ss', 'shadowsocks') THEN 'shadowsocks'
        WHEN v_scheme = 'ssr' THEN 'ssr'
        WHEN v_scheme IN ('socks', 'socks4', 'socks5', 'socks5h') THEN 'socks'
        WHEN v_scheme IN ('hy2', 'hysteria', 'hysteria2') THEN 'hysteria2'
        ELSE v_scheme
      END;

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
        INSERT INTO public.servers
          (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
        VALUES
          (left(v_name, 64), '🌐', 'Community', v_row.link, left(v_host, 255), v_port, true, v_row.premium_only, v_type, 'link');
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
