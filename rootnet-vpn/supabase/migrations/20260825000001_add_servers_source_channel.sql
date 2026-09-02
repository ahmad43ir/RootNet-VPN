-- ============================================================
-- 📡 20260825000001_add_servers_source_channel.sql
-- ============================================================
-- VlessHub / RootNet — show which Telegram channel each server was
-- scraped from.
--
-- The import RPCs never carried `vless_links.source_channel` into
-- `servers`, so the app had no source to display. This migration:
--   1. Adds `servers.source_channel` (TEXT, default '').
--   2. Backfills existing rows by matching `servers.config` to
--      `vless_links.link`.
--   3. Adds an AFTER UPDATE trigger on `vless_links.imported_to_servers`
--      so FUTURE imports get their channel synced automatically — no
--      changes needed inside the import RPCs themselves.
-- ============================================================

-- 1 ── Column ──────────────────────────────────────────────────
ALTER TABLE public.servers
  ADD COLUMN IF NOT EXISTS source_channel TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_servers_source_channel
  ON public.servers (source_channel);

COMMENT ON COLUMN public.servers.source_channel IS
  'Telegram channel this config was scraped from (e.g. @channelname)';

-- 2 ── Backfill existing rows ─────────────────────────────────
UPDATE public.servers s
SET source_channel = vl.source_channel
FROM public.vless_links vl
WHERE vl.link = s.config
  AND vl.source_channel IS NOT NULL
  AND vl.source_channel <> ''
  AND (s.source_channel IS NULL OR s.source_channel = '');

-- 3 ── Keep future imports in sync automatically ──────────────
CREATE OR REPLACE FUNCTION public.sync_vless_source_channel()
RETURNS trigger
LANGUAGE plpgsql
AS $fn$
BEGIN
  IF NEW.imported_to_servers IS TRUE AND OLD.imported_to_servers IS NOT TRUE THEN
    UPDATE public.servers
    SET source_channel = COALESCE(NEW.source_channel, '')
    WHERE config = NEW.link
      AND (source_channel IS NULL OR source_channel = '');
  END IF;
  RETURN NEW;
END;
$fn$;

DROP TRIGGER IF EXISTS trg_sync_vless_source_channel ON public.vless_links;
CREATE TRIGGER trg_sync_vless_source_channel
AFTER UPDATE OF imported_to_servers ON public.vless_links
FOR EACH ROW
EXECUTE FUNCTION public.sync_vless_source_channel();
