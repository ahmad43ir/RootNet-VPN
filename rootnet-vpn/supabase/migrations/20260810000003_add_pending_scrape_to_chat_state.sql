-- ============================================================
-- 📁 20260810000003_add_pending_scrape_to_chat_state.sql
-- ============================================================
-- Add pending_scrape_confirm and scrape_message_id columns to
-- bot_chat_state for /scrape confirm/cancel flow persistence.
-- ============================================================

ALTER TABLE public.bot_chat_state
  ADD COLUMN IF NOT EXISTS pending_scrape_confirm BOOLEAN NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS scrape_message_id BIGINT;

COMMENT ON COLUMN public.bot_chat_state.pending_scrape_confirm IS 'User is confirming /scrape despite recent run';
COMMENT ON COLUMN public.bot_chat_state.scrape_message_id IS 'Message ID of the confirm prompt for editing';