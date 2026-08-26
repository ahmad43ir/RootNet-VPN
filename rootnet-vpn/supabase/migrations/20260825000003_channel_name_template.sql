-- ============================================================
-- 📡 20260825000003_channel_name_template.sql
-- ============================================================
-- Naming template: a scraped item's NAME is the Telegram channel it
-- came from. When the import RPC derives the name from the link's
-- #fragment and that fragment is junk (a bare UUID — common for
-- links whose real title was stripped), replace it with the channel
-- handle recorded on vless_links.source_channel.
--
-- Extends the sync trigger (20260825000001) and backfills existing
-- rows. Channels verified from saved-message samples:
--   @broz_time (2651956769) .npvt files · @prrofile_purple (1268460826)
--   vless · @proxymtproto (1395363861) / @mrshahabx (1740160257) /
--   @iroproxy (1171741566) MTProto proxies.
-- ============================================================

CREATE OR REPLACE FUNCTION public.sync_vless_source_channel()
RETURNS trigger
LANGUAGE plpgsql
AS $fn$
BEGIN
  IF NEW.imported_to_servers IS TRUE AND OLD.imported_to_servers IS NOT TRUE THEN
    UPDATE public.servers s
    SET source_channel = COALESCE(NULLIF(NEW.source_channel, ''), s.source_channel),
        name = CASE
          WHEN COALESCE(NEW.source_channel, '') <> ''
               AND s.name ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          THEN NEW.source_channel
          ELSE s.name
        END
    WHERE s.config = NEW.link
      AND (s.source_channel IS NULL OR s.source_channel = '');
  END IF;
  RETURN NEW;
END;
$fn$;

-- Backfill: any existing row whose name is a bare UUID but which has a
-- salvaged source channel takes the channel as its display name.
UPDATE public.servers
SET name = source_channel
WHERE source_channel <> ''
  AND name ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';
