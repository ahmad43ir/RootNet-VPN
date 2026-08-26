-- ============================================================
-- 📁 20260727000002_create_vless_links_table.sql
-- ============================================================
-- Creates the vless_links table for storing scraped VLESS configs
-- from Telegram channels, plus helper functions for:
--   - Getting link age (hours since created)
--   - Cleaning up links older than 36 hours
-- ============================================================

-- ── 1. Create the table ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.vless_links (
    id              BIGSERIAL PRIMARY KEY,
    link            TEXT NOT NULL UNIQUE,
    source_channel  TEXT NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for fast cleanup queries (look up links by age)
CREATE INDEX IF NOT EXISTS idx_vless_links_created_at
    ON public.vless_links (created_at ASC);

-- Index for fast dedup lookup
CREATE INDEX IF NOT EXISTS idx_vless_links_link
    ON public.vless_links USING hash (link);

-- ── 2. Enable Row-Level Security ─────────────────────────────
ALTER TABLE public.vless_links ENABLE ROW LEVEL SECURITY;

-- Allow anon read (landing page / API can show links)
CREATE POLICY "Anyone can view vless_links"
    ON public.vless_links
    FOR SELECT
    USING (true);

-- Allow service_role full access (for the scraper)
-- (service_role bypasses RLS by default, so no explicit policy needed)

-- ── 3. Helper: Get link age in hours ─────────────────────────
-- Returns how many hours have passed since a link was created.
CREATE OR REPLACE FUNCTION public.get_vless_link_age(link_id BIGINT)
RETURNS NUMERIC
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_age NUMERIC;
BEGIN
    SELECT EXTRACT(EPOCH FROM (now() - created_at)) / 3600.0
    INTO v_age
    FROM public.vless_links
    WHERE id = link_id;

    RETURN COALESCE(v_age, 0);
END;
$$;

-- ── 4. Helper: Delete links older than N hours ───────────────
-- Returns the number of deleted rows.
CREATE OR REPLACE FUNCTION public.cleanup_old_vless_links(max_age_hours NUMERIC DEFAULT 36)
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_deleted INTEGER;
BEGIN
    DELETE FROM public.vless_links
    WHERE created_at < now() - (max_age_hours || ' hours')::INTERVAL;

    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RETURN v_deleted;
END;
$$;

-- ── 5. Helper: Get recent links (for display) ────────────────
-- Returns links that are still within the age limit, sorted newest first.
CREATE OR REPLACE FUNCTION public.get_active_vless_links(max_age_hours NUMERIC DEFAULT 36)
RETURNS TABLE (
    id              BIGINT,
    link            TEXT,
    source_channel  TEXT,
    age_hours       NUMERIC,
    created_at      TIMESTAMPTZ
)
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    RETURN QUERY
    SELECT
        vl.id,
        vl.link,
        vl.source_channel,
        ROUND(EXTRACT(EPOCH FROM (now() - vl.created_at)) / 3600.0, 1) AS age_hours,
        vl.created_at
    FROM public.vless_links vl
    WHERE vl.created_at > now() - (max_age_hours || ' hours')::INTERVAL
    ORDER BY vl.created_at DESC;
END;
$$;
