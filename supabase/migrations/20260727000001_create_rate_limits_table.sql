-- ============================================================
-- 📁 20260727000001_create_rate_limits_table.sql
-- ============================================================
-- RootNet VPN — IP-based Rate Limiting + GeoIP Cache via Postgres
--
-- Replaces Cloudflare Workers in-memory rate limiting with a
-- persistent Postgres-backed approach. Also caches GeoIP lookups
-- to stay within free upstream API limits (45 req/min for ip-api.com).
--
-- ARCHITECTURE:
--   rate_limits  table tracks requests per IP address.
--   geoip_cache  table caches IP→country results (24h TTL).
--   check_rate_limit() RPC atomically checks & increments counts.
--   get_or_fetch_geoip() RPC checks cache, fetches from API if needed.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  CREATE rate_limits TABLE
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.rate_limits (
  id              BIGSERIAL PRIMARY KEY,
  ip_address      TEXT NOT NULL,
  request_count   INTEGER NOT NULL DEFAULT 1,
  window_start    TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_request    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Unique index on IP — one row per IP for fast upserts
CREATE UNIQUE INDEX IF NOT EXISTS idx_rate_limits_ip
  ON public.rate_limits (ip_address);

-- Index on window_start for efficient cleanup of expired rows
CREATE INDEX IF NOT EXISTS idx_rate_limits_window
  ON public.rate_limits (window_start);

-- ──────────────────────────────────────────────
-- 1b️⃣  CREATE geoip_cache TABLE
-- ──────────────────────────────────────────────
-- Caches IP→country lookups to reduce calls to the upstream GeoIP API.
-- TTL is 24 hours; stale entries are purged by cleanup_rate_limits().
CREATE TABLE IF NOT EXISTS public.geoip_cache (
  id              BIGSERIAL PRIMARY KEY,
  ip_address      TEXT NOT NULL,
  country_name    TEXT NOT NULL,
  country_code    TEXT NOT NULL,
  flag_emoji      TEXT NOT NULL,
  fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_geoip_cache_ip
  ON public.geoip_cache (ip_address);

CREATE INDEX IF NOT EXISTS idx_geoip_cache_fetched
  ON public.geoip_cache (fetched_at);

-- ──────────────────────────────────────────────
-- 2️⃣  ENABLE ROW LEVEL SECURITY
-- ──────────────────────────────────────────────
ALTER TABLE public.rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.geoip_cache ENABLE ROW LEVEL SECURITY;

-- No public RLS policies — both tables are only accessible via service_role.
-- Since the Edge Function uses the service_role key, RLS is bypassed.

-- ──────────────────────────────────────────────
-- 3️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.rate_limits
  IS 'IP-based rate limiting — tracks request counts per IP within rolling windows';
COMMENT ON COLUMN public.rate_limits.ip_address
  IS 'Client IP address (from x-forwarded-for header, validated server-side)';
COMMENT ON COLUMN public.rate_limits.request_count
  IS 'Number of requests in the current time window';
COMMENT ON COLUMN public.rate_limits.window_start
  IS 'When the current rate limit window started';
COMMENT ON COLUMN public.rate_limits.last_request
  IS 'Timestamp of the most recent request from this IP';

COMMENT ON TABLE public.geoip_cache
  IS 'Cache for IP geolocation lookups — reduces upstream API calls';
COMMENT ON COLUMN public.geoip_cache.ip_address
  IS 'The looked-up IP address';
COMMENT ON COLUMN public.geoip_cache.country_name
  IS 'Full country name (e.g. United States)';
COMMENT ON COLUMN public.geoip_cache.country_code
  IS 'ISO 3166-1 alpha-2 country code (e.g. US)';
COMMENT ON COLUMN public.geoip_cache.flag_emoji
  IS 'Country flag emoji (e.g. 🇺🇸)';
COMMENT ON COLUMN public.geoip_cache.fetched_at
  IS 'When this cache entry was created/updated';

-- ============================================================
-- 4️⃣  RPC: check_rate_limit(ip, max_requests, window_minutes)
-- ============================================================
-- Atomically checks and updates the rate limit for an IP.
--
-- Returns:
--   { allowed: boolean, remaining: int, retry_after: float }
--
--   allowed=true  → request is within limits
--   allowed=false → IP is rate limited
--   remaining     → how many requests remain in this window
--   retry_after   → seconds until the window resets (0 if allowed)
--
-- HOW IT WORKS:
--   1. If no row exists for this IP → insert with count=1, allowed=true
--   2. If window_start + window_minutes > now() →
--        increment count, check if over limit
--   3. If window has expired → reset count to 1, allowed=true
--
-- Atomic via INSERT ... ON CONFLICT in a single transaction.
-- ============================================================
CREATE OR REPLACE FUNCTION public.check_rate_limit(
  p_ip_address      TEXT,
  p_max_requests    INTEGER DEFAULT 60,
  p_window_minutes  INTEGER DEFAULT 1
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
  v_row           public.rate_limits%ROWTYPE;
  v_now           TIMESTAMPTZ := now();
  v_window_expiry TIMESTAMPTZ;
  v_allowed       BOOLEAN;
  v_remaining     INTEGER;
  v_retry_after   NUMERIC;
BEGIN
  -- Upsert: insert if new, update if exists (atomic, no race condition)
  INSERT INTO public.rate_limits (ip_address, request_count, window_start, last_request)
  VALUES (p_ip_address, 1, v_now, v_now)
  ON CONFLICT (ip_address) DO UPDATE
    SET
      request_count = CASE
        -- If window has expired, reset count to 1
        WHEN public.rate_limits.window_start + (p_window_minutes || ' minutes')::INTERVAL <= v_now
        THEN 1
        -- Otherwise increment
        ELSE public.rate_limits.request_count + 1
      END,
      window_start = CASE
        -- If window has expired, start a new window
        WHEN public.rate_limits.window_start + (p_window_minutes || ' minutes')::INTERVAL <= v_now
        THEN v_now
        ELSE public.rate_limits.window_start
      END,
      last_request = v_now
  RETURNING * INTO v_row;

  -- Calculate window expiry
  v_window_expiry := v_row.window_start + (p_window_minutes || ' minutes')::INTERVAL;

  -- Determine if allowed
  v_allowed := v_row.request_count <= p_max_requests;

  -- Calculate remaining requests in this window
  v_remaining := GREATEST(p_max_requests - v_row.request_count, 0);

  -- Calculate retry_after (seconds until window resets), 0 if allowed
  IF v_allowed THEN
    v_retry_after := 0;
  ELSE
    v_retry_after := EXTRACT(EPOCH FROM (v_window_expiry - v_now));
    IF v_retry_after < 0 THEN
      v_retry_after := 0;
    END IF;
  END IF;

  RETURN jsonb_build_object(
    'allowed',     v_allowed,
    'remaining',   v_remaining,
    'retry_after', v_retry_after
  );
END;
$$;

-- ============================================================
-- 5️⃣  RPC: get_cached_geoip(ip_address)
-- ============================================================
-- Returns a cached GeoIP result if it exists and is still fresh.
-- Cache TTL is 24 hours.
-- ============================================================
CREATE OR REPLACE FUNCTION public.get_cached_geoip(
  p_ip_address TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
AS $$
DECLARE
  v_row public.geoip_cache%ROWTYPE;
BEGIN
  SELECT * INTO v_row
  FROM public.geoip_cache
  WHERE ip_address = p_ip_address
    AND fetched_at > now() - INTERVAL '24 hours';

  IF NOT FOUND THEN
    RETURN NULL;
  END IF;

  RETURN jsonb_build_object(
    'country',   v_row.country_name,
    'code',      v_row.country_code,
    'flag',      v_row.flag_emoji
  );
END;
$$;

-- ============================================================
-- 6️⃣  RPC: set_cached_geoip(ip, country, code, flag)
-- ============================================================
-- Stores a GeoIP result in the cache (upsert).
-- ============================================================
CREATE OR REPLACE FUNCTION public.set_cached_geoip(
  p_ip_address   TEXT,
  p_country_name TEXT,
  p_country_code TEXT,
  p_flag_emoji   TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
  INSERT INTO public.geoip_cache (ip_address, country_name, country_code, flag_emoji, fetched_at)
  VALUES (p_ip_address, p_country_name, p_country_code, p_flag_emoji, now())
  ON CONFLICT (ip_address) DO UPDATE
    SET
      country_name = EXCLUDED.country_name,
      country_code = EXCLUDED.country_code,
      flag_emoji   = EXCLUDED.flag_emoji,
      fetched_at   = now();
END;
$$;

-- ============================================================
-- 7️⃣  RPC: cleanup_stale_records()
-- ============================================================
-- Deletes rate limit rows older than 24 hours AND GeoIP cache
-- entries older than 48 hours. Returns total count of deleted rows.
-- Call periodically (e.g., via pg_cron or a scheduled Edge Function).
-- ============================================================
CREATE OR REPLACE FUNCTION public.cleanup_stale_records()
RETURNS INTEGER
LANGUAGE plpgsql
AS $$
DECLARE
  v_deleted_rl  INTEGER := 0;
  v_deleted_geo INTEGER := 0;
BEGIN
  DELETE FROM public.rate_limits
  WHERE last_request < now() - INTERVAL '24 hours';
  GET DIAGNOSTICS v_deleted_rl = ROW_COUNT;

  DELETE FROM public.geoip_cache
  WHERE fetched_at < now() - INTERVAL '48 hours';
  GET DIAGNOSTICS v_deleted_geo = ROW_COUNT;

  RETURN v_deleted_rl + v_deleted_geo;
END;
$$;

COMMENT ON FUNCTION public.check_rate_limit
  IS 'Atomically check and increment rate limit for an IP. Returns { allowed, remaining, retry_after }';
COMMENT ON FUNCTION public.get_cached_geoip
  IS 'Retrieve a cached GeoIP lookup result, or NULL if not found / expired';
COMMENT ON FUNCTION public.set_cached_geoip
  IS 'Store or update a GeoIP lookup result in the cache';
COMMENT ON FUNCTION public.cleanup_stale_records
  IS 'Remove stale rate_limit and geoip_cache rows. Returns total deleted count.';
