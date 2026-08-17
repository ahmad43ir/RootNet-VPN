-- ============================================================
-- 📁 20260810000004_add_config_format_check.sql
-- ============================================================
-- Add CHECK constraint on servers.config_format to prevent typos.
-- ============================================================

ALTER TABLE public.servers
  ADD CONSTRAINT config_format_valid
  CHECK (config_format IN ('link','json','npv','conf','raw','sip'));