-- ============================================================
-- 📁 20260803000001_add_imported_to_servers_column.sql
-- ============================================================
-- Pipeline integration: tracks which scraped VLESS links have
-- already been promoted into the `servers` table via the
-- rootnet-api `POST /import-vless` endpoint.
--
-- Links with imported_to_servers = false are pending import.
-- ============================================================

-- ── 1. Add the tracking column ──────────────────────────────
ALTER TABLE public.vless_links
  ADD COLUMN IF NOT EXISTS imported_to_servers BOOLEAN NOT NULL DEFAULT FALSE;

-- ── 2. Partial index for fast "pending import" lookups ──────
CREATE INDEX IF NOT EXISTS idx_vless_links_imported
  ON public.vless_links (created_at ASC)
  WHERE imported_to_servers = false;
