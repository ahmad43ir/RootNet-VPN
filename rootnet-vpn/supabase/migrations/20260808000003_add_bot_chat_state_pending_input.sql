-- ============================================================
-- bot_chat_state.pending_input
-- Lets the bot prompt for a value after the user taps a scraper
-- menu button (e.g. "Add proxy" then the user pastes the proxy),
-- instead of requiring /addproxy <value>. Nullable text column.
-- ============================================================

alter table public.bot_chat_state
  add column if not exists pending_input text;
