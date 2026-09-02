-- ============================================================
-- 📁 20260807000001_create_free_connection_quota.sql
-- ============================================================
-- RootNet VPN — daily free-connection quota (ad-unavailable fallback)
--
-- When Unity Ads is unavailable (e.g. blocked in the user's region), the
-- app grants free users a limited number of ad-free connections per 24h.
-- The counter is SERVER-SIDE and resets on SERVER time (UTC date), so the
-- client cannot fake it ("less security").
--
-- ARCHITECTURE:
--   free_connection_quota — one row per user. `quota_date` is the UTC date
--     the current usage bucket belongs to; when the date rolls over the
--     bucket resets automatically.
--   claim_free_connection(user_id) — ATOMIC claim of one free slot.
--     Returns { granted, used, limit }. Concurrency-safe via
--     SELECT ... FOR UPDATE row locking (serializes simultaneous claims,
--     so the counter is never double-spent).
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  CREATE free_connection_quota TABLE
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.free_connection_quota (
  user_id     UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  quota_date  DATE NOT NULL DEFAULT ((now() AT TIME ZONE 'UTC')::date),
  used        INTEGER NOT NULL DEFAULT 0,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Date index for future aggregates / admin reports.
CREATE INDEX IF NOT EXISTS idx_free_quota_date
  ON public.free_connection_quota (quota_date);

-- ──────────────────────────────────────────────
-- 2️⃣  ENABLE ROW LEVEL SECURITY
-- ──────────────────────────────────────────────
ALTER TABLE public.free_connection_quota ENABLE ROW LEVEL SECURITY;

-- NO client-facing policies: anon/authenticated clients can neither read
-- nor write their quota. Writes happen ONLY through the SECURITY DEFINER
-- RPC below, invoked by the edge function with the service_role key
-- (which bypasses RLS anyway). The client is never trusted.

-- ──────────────────────────────────────────────
-- 3️⃣  RPC: claim_free_connection(user_id)
-- ──────────────────────────────────────────────
-- Atomically claims one free connection for the user's current UTC day.
-- Resets automatically when the UTC date rolls over.
--
-- Returns:
--   { granted: boolean, used: int, limit: int }
--     granted=true  → a slot was consumed; the user may connect free
--     granted=false → no slots left today
--
-- Concurrency: SELECT ... FOR UPDATE takes the row lock, serializing
-- simultaneous claims so the counter can never be double-spent.
-- ============================================================
CREATE OR REPLACE FUNCTION public.claim_free_connection(p_user_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_today DATE := (now() AT TIME ZONE 'UTC')::date;
  v_row   public.free_connection_quota%ROWTYPE;
BEGIN
  SELECT * INTO v_row
  FROM public.free_connection_quota
  WHERE user_id = p_user_id
  FOR UPDATE;

  -- No row yet → first claim ever: create the bucket with one used slot.
  IF NOT FOUND THEN
    INSERT INTO public.free_connection_quota (user_id, quota_date, used, updated_at)
    VALUES (p_user_id, v_today, 1, now());
    RETURN jsonb_build_object('granted', true, 'used', 1, 'limit', 2);
  END IF;

  -- UTC date rolled over → reset the bucket and consume the first slot.
  IF v_row.quota_date <> v_today THEN
    UPDATE public.free_connection_quota
    SET quota_date = v_today, used = 1, updated_at = now()
    WHERE user_id = p_user_id;
    RETURN jsonb_build_object('granted', true, 'used', 1, 'limit', 2);
  END IF;

  -- Same day, quota exhausted → deny.
  IF v_row.used >= 2 THEN
    RETURN jsonb_build_object('granted', false, 'used', v_row.used, 'limit', 2);
  END IF;

  -- Same day, slots remain → consume one.
  UPDATE public.free_connection_quota
  SET used = used + 1, updated_at = now()
  WHERE user_id = p_user_id;
  RETURN jsonb_build_object('granted', true, 'used', v_row.used + 1, 'limit', 2);
END;
$$;

-- ──────────────────────────────────────────────
-- 4️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.free_connection_quota
  IS 'Daily free-connection quota (ad-unavailable fallback) — server-side, resets on UTC date';
COMMENT ON COLUMN public.free_connection_quota.user_id IS 'Supabase auth user ID (foreign key to auth.users)';
COMMENT ON COLUMN public.free_connection_quota.quota_date IS 'UTC date this usage bucket belongs to — resets when it rolls over';
COMMENT ON COLUMN public.free_connection_quota.used IS 'Free connections consumed on quota_date';
COMMENT ON FUNCTION public.claim_free_connection
  IS 'Atomically claim one free connection slot for a user for the current UTC day. Returns { granted, used, limit }';
