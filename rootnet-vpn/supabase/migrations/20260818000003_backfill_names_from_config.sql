-- ============================================================
-- 📁 20260818000003_backfill_names_from_config.sql
-- ============================================================
-- The old import RPC truncated names with left(name, 64) BEFORE
-- percent-decoding, so some rows store a fragment cut mid-escape
-- (e.g. "...%F0%9D%97%A6%" — a lone trailing '%'). Migration
-- 20260818000002 decoded those names, which correctly yielded
-- "@prrofile_purple | 𝗙𝗮𝗦%" — still truncated.
-- This migration re-derives the name from the FULL config
-- fragment (servers.config was never truncated), decodes it,
-- and truncates AFTER decoding. Rows that fail are left as-is.
-- ============================================================

DO $do$
DECLARE
  v_rec  record;
  v_frag text;
  v_new  text;
BEGIN
  FOR v_rec IN
    SELECT id, name, config
    FROM public.servers
    WHERE position('%' in name) > 0
  LOOP
    BEGIN
      -- Fragment = everything after the first '#' in the config URI.
      IF position('#' in v_rec.config) > 0 THEN
        v_frag := substring(v_rec.config from position('#' in v_rec.config) + 1);
        IF v_frag IS NOT NULL AND v_frag <> '' THEN
          v_new := public.rootnet_url_decode(v_frag);
          IF v_new IS NOT NULL AND v_new <> '' THEN
            UPDATE public.servers SET name = left(v_new, 64) WHERE id = v_rec.id;
          END IF;
        END IF;
      END IF;
    EXCEPTION WHEN others THEN
      NULL; -- keep the current name if the fragment doesn't decode
    END;
  END LOOP;
END;
$do$;
