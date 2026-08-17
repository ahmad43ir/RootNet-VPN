-- ============================================================
-- 📁 20260812000001_create_vpn_files_table.sql
-- ============================================================
-- Store VPN config file attachments (.npvt, .sip, .npv, .json, etc.)
-- so users can download them via the bot even when configs can't be
-- auto-extracted (e.g. encrypted NPVT files).
-- ============================================================

-- ──────────────────────────────────────────────
-- vpn_files — VPN config file attachments
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.vpn_files (
  id           BIGSERIAL PRIMARY KEY,
  filename     TEXT NOT NULL,
  mime_type    TEXT,
  size_bytes   BIGINT NOT NULL,
  content      BYTEA NOT NULL,  -- raw file content
  source_channel TEXT,
  uploaded_by  BIGINT,  -- Telegram user ID who uploaded
  uploaded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  is_encrypted BOOLEAN GENERATED ALWAYS AS (
    lower(filename) LIKE '%.npvt' OR lower(filename) LIKE '%.npv'
  ) STORED,
  config_count INTEGER DEFAULT 0,  -- number of configs extracted (0 if failed)
  -- FIX (2026-08-13): case-insensitive flag must be the `~*` operator,
  -- not a stray `i` outside the string literal (`'...$'i` was a syntax error).
  CONSTRAINT vpn_files_filename_check CHECK (filename ~* '\.(npv|npvt|npt|json|sip|conf|config|ovpn|txt)$')
);

CREATE INDEX IF NOT EXISTS vpn_files_uploaded_at_idx ON public.vpn_files (uploaded_at DESC);
CREATE INDEX IF NOT EXISTS vpn_files_source_channel_idx ON public.vpn_files (source_channel);
CREATE INDEX IF NOT EXISTS vpn_files_uploaded_by_idx ON public.vpn_files (uploaded_by);

-- ──────────────────────────────────────────────
-- ROW LEVEL SECURITY — service_role only
-- ──────────────────────────────────────────────
ALTER TABLE public.vpn_files ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.vpn_files IS 'VPN config file attachments for user download. service_role only.';
COMMENT ON COLUMN public.vpn_files.is_encrypted IS 'True for NPVT/NPV files (likely encrypted, configs not auto-extracted)';
COMMENT ON COLUMN public.vpn_files.config_count IS 'Number of configs auto-extracted from this file (0 = failed/encrypted)';