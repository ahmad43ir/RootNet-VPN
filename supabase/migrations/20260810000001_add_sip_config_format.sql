-- ============================================================
-- 📁 20260810000001_add_sip_config_format.sql
-- ============================================================
-- Add SIP (SocksIP/SSH/SOCKS5/HTTP proxy) config format support
-- ============================================================

-- Update column comment to include SIP format
COMMENT ON COLUMN public.servers.config_format IS 'Config format: link (URI), json (JSON/base64), npv (NekoBox), conf (WireGuard), sip (SocksIP/SSH/SOCKS/HTTP)';