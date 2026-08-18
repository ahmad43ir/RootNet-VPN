-- ============================================================
-- 📁 20260818000006_add_bot_chat_state_result_message_id.sql
-- ============================================================
-- Track the single "live" result/status message per chat (scrape
-- reports, upload summaries, command answers) so the bot can delete
-- the previous one when a new result replaces it — stops transient
-- status messages from piling up. Nullable: unset until the first
-- result is shown.
-- ============================================================

ALTER TABLE public.bot_chat_state
  ADD COLUMN IF NOT EXISTS result_message_id BIGINT;

COMMENT ON COLUMN public.bot_chat_state.result_message_id IS 'Message ID of the current live result/status message — deleted when the next result is shown';
