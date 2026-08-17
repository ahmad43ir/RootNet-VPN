-- ============================================================
-- 📁 20260810000002_add_request_ids_table.sql
-- ============================================================
-- Table for anti-replay request ID deduplication (TTL 60s).
-- Used by rootnet-api to prevent replay attacks.
-- ============================================================

CREATE TABLE IF NOT EXISTS public.request_ids (
  request_id   TEXT PRIMARY KEY,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Auto-cleanup old request IDs (run via pg_cron or manual)
-- DELETE FROM public.request_ids WHERE created_at < now() - interval '60 seconds';

COMMENT ON TABLE public.request_ids IS 'Anti-replay request ID deduplication (60s TTL). service_role only.';

ALTER TABLE public.request_ids ENABLE ROW LEVEL SECURITY;