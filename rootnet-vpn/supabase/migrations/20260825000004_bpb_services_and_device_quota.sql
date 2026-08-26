-- ============================================================
-- 📡 20260825000004_bpb_services_and_device_quota.sql
-- ============================================================
-- RootNet v3 backend pieces:
--
--   1. `bpb_services` — registry of the 10 BPB worker subscriptions.
--      sub_url is SECRET: RLS denies everyone except service_role, so the
--      URLs ship only inside rootnet-api (which proxies subscriptions via
--      GET /bpb-sub) and never inside the APK.
--
--   2. `device_quota` — server-side ledger for the ad-funded TIME quota
--      (30 min per rewarded video, 60 min hard cap). The device clock is
--      mirrored here so reinstalls can't refill time. Service_role only.
-- ============================================================

-- 1 ── BPB subscription registry ──────────────────────────────
CREATE TABLE IF NOT EXISTS public.bpb_services (
  id          BIGSERIAL PRIMARY KEY,
  label       TEXT NOT NULL,                 -- e.g. "BPB-1"
  flag        TEXT NOT NULL DEFAULT '🛰',
  country     TEXT NOT NULL DEFAULT 'BPB service',
  sub_url     TEXT NOT NULL,                 -- SECRET — never exposed to clients
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bpb_services_active ON public.bpb_services (is_active);

ALTER TABLE public.bpb_services ENABLE ROW LEVEL SECURITY;

-- Deny all direct access (service_role bypasses RLS).
DROP POLICY IF EXISTS "deny_anon_bpb" ON public.bpb_services;
CREATE POLICY "deny_anon_bpb" ON public.bpb_services
  FOR SELECT TO anon USING (false);

COMMENT ON TABLE public.bpb_services IS
  'BPB worker subscription registry — sub_url is secret; served only through rootnet-api GET /bpb-sub';

-- 2 ── Device time-quota ledger ───────────────────────────────
CREATE TABLE IF NOT EXISTS public.device_quota (
  device_id             TEXT PRIMARY KEY,
  remaining_seconds     BIGINT NOT NULL DEFAULT 0,
  total_granted_seconds BIGINT NOT NULL DEFAULT 0,
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE public.device_quota ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "deny_anon_device_quota" ON public.device_quota;
CREATE POLICY "deny_anon_device_quota" ON public.device_quota
  FOR SELECT TO anon USING (false);

COMMENT ON TABLE public.device_quota IS
  'Ad-funded connection-time ledger (seconds). Written only by rootnet-api /quota/sync';
