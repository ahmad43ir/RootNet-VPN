-- ============================================================
-- 📁 20260721000004_create_device_tokens_table.sql
-- ============================================================
-- RootNet VPN — Device tokens for push notifications
--
-- Stores FCM (Firebase Cloud Messaging) device tokens so the
-- Cloudflare Worker can send push notifications to users.
-- Tokens are linked to authenticated Supabase users.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  CREATE device_tokens TABLE
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.device_tokens (
  id          BIGSERIAL PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  token       TEXT NOT NULL,
  platform    TEXT NOT NULL DEFAULT 'android',
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ DEFAULT now(),
  UNIQUE(user_id, token)
);

-- ──────────────────────────────────────────────
-- 2️⃣  INDEXES
-- ──────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON public.device_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_device_tokens_token ON public.device_tokens (token);

-- ──────────────────────────────────────────────
-- 3️⃣  ENABLE ROW LEVEL SECURITY
-- ──────────────────────────────────────────────
ALTER TABLE public.device_tokens ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- 4️⃣  RLS POLICIES
-- ──────────────────────────────────────────────

-- ✅ Users can read their own device tokens
DROP POLICY IF EXISTS "Users can view their own device tokens" ON public.device_tokens;
CREATE POLICY "Users can view their own device tokens"
  ON public.device_tokens
  FOR SELECT
  TO authenticated
  USING (auth.uid() = user_id);

-- ✅ Users can insert their own device tokens
DROP POLICY IF EXISTS "Users can insert their own device tokens" ON public.device_tokens;
CREATE POLICY "Users can insert their own device tokens"
  ON public.device_tokens
  FOR INSERT
  TO authenticated
  WITH CHECK (auth.uid() = user_id);

-- ✅ Users can delete their own device tokens (e.g., on logout)
DROP POLICY IF EXISTS "Users can delete their own device tokens" ON public.device_tokens;
CREATE POLICY "Users can delete their own device tokens"
  ON public.device_tokens
  FOR DELETE
  TO authenticated
  USING (auth.uid() = user_id);

-- Note: The Worker uses the service_role key which bypasses RLS,
-- so it can read tokens for any user when sending notifications.

-- ──────────────────────────────────────────────
-- 5️⃣  AUTO-UPDATE updated_at TRIGGER
-- ──────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_device_tokens_updated_at ON public.device_tokens;
CREATE TRIGGER update_device_tokens_updated_at
  BEFORE UPDATE ON public.device_tokens
  FOR EACH ROW
  EXECUTE FUNCTION public.update_updated_at_column();

-- ──────────────────────────────────────────────
-- 6️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.device_tokens IS 'FCM device tokens for push notifications — linked to auth users';
COMMENT ON COLUMN public.device_tokens.user_id IS 'Supabase auth user ID (foreign key to auth.users)';
COMMENT ON COLUMN public.device_tokens.token IS 'Firebase Cloud Messaging device token';
COMMENT ON COLUMN public.device_tokens.platform IS 'Device platform: android, ios, web';
