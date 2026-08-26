-- ============================================================
-- 📁 20260818000001_add_bot_chat_state_menu_message_id.sql
-- ============================================================
-- Track the single "live" menu/option message per chat so the bot
-- can delete it when a new menu page replaces it — stops old
-- keyboards from piling up in the chat after every button tap.
-- Nullable: unset until the first menu is shown.
-- ============================================================

ALTER TABLE public.bot_chat_state
  ADD COLUMN IF NOT EXISTS menu_message_id BIGINT;

COMMENT ON COLUMN public.bot_chat_state.menu_message_id IS 'Message ID of the current live menu/option message — deleted when the next menu page is shown';
