-- ============================================================
-- 📡 20260825000002_salvage_source_channel.sql
-- ============================================================
-- vless_links rows were already purged by the 36h cleanup, so the
-- backfill in 20260825000001 matched nothing. But imported NAMES
-- preserved the channel handle ("@fnsd 1", "@prrofile_purple 2").
-- This migration extracts that handle into servers.source_channel.
-- Rows whose names are bare UUIDs have no recoverable channel and
-- stay '' (shown as "Community" in the app).
-- ============================================================

UPDATE public.servers
SET source_channel = substring(name from '^(@[^\s]+)')
WHERE source_channel = ''
  AND name ~ '^@[^\s]+';
