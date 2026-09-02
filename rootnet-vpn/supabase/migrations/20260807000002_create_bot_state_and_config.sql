-- ============================================================
-- 📁 20260807000002_create_bot_state_and_config.sql
-- ============================================================
-- RootNet Telegram bot (edge function) — persistence layer.
--
-- The Python long-poll bot (telegram-bot/bot.py) kept per-chat UI
-- state in module memory. Edge functions are stateless (cold starts,
-- multiple isolates), so the rewritten bot persists state here:
--
--   bot_chat_state — per-chat UI state: upload mode (premium flag),
--                    the premium filter of the current server list,
--                    and the set of server ids toggled for deletion.
--   bot_config     — tiny key/value store for bot runtime settings
--                    (the Telegram webhook secret_token).
--
-- SECURITY: RLS enabled with NO policies. Only service_role (used by
-- the edge function) can read/write — anon/authenticated cannot.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  bot_chat_state — per-chat UI state
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.bot_chat_state (
  chat_id      BIGINT PRIMARY KEY,             -- Telegram chat id
  upload_mode  BOOLEAN NOT NULL DEFAULT false, -- true = next upload is premium
  list_mode    BOOLEAN NOT NULL DEFAULT false, -- premium filter of current list
  selected_ids BIGINT[] NOT NULL DEFAULT '{}', -- server ids toggled for deletion
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────
-- 2️⃣  bot_config — bot runtime settings
-- ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS public.bot_config (
  key        TEXT PRIMARY KEY,
  value      TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────────────────────────────────
-- 3️⃣  ROW LEVEL SECURITY — no policies at all
-- ──────────────────────────────────────────────
ALTER TABLE public.bot_chat_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bot_config ENABLE ROW LEVEL SECURITY;

-- ──────────────────────────────────────────────
-- 4️⃣  COMMENTS
-- ──────────────────────────────────────────────
COMMENT ON TABLE public.bot_chat_state IS 'Telegram bot per-chat UI state (single admin) — edge functions are stateless, so UI state lives here. service_role only.';
COMMENT ON TABLE public.bot_config IS 'Telegram bot runtime settings (webhook secret_token). service_role only.';
