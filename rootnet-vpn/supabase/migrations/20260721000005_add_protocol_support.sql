-- ============================================================
-- 📁 20260721000005_add_protocol_support.sql
-- ============================================================
-- RootNet VPN — Multi-protocol support migration
--
-- Adds `type` and `config_format` columns to the servers table
-- so the app can support VLESS, VMess, Trojan, WireGuard, etc.
-- ============================================================

-- ──────────────────────────────────────────────
-- 1️⃣  ADD NEW COLUMNS
-- ──────────────────────────────────────────────
ALTER TABLE public.servers
  ADD COLUMN IF NOT EXISTS type TEXT NOT NULL DEFAULT 'vless',
  ADD COLUMN IF NOT EXISTS config_format TEXT NOT NULL DEFAULT 'link';

-- ──────────────────────────────────────────────
-- 2️⃣  ADD COMMENT ON NEW COLUMNS
-- ──────────────────────────────────────────────
COMMENT ON COLUMN public.servers.type IS 'VPN protocol type: vless, vmess, trojan, wireguard, shadowsocks';
COMMENT ON COLUMN public.servers.config_format IS 'Config format: link (URI), json (JSON/base64), npv (NekoBox), conf (WireGuard)';

-- ──────────────────────────────────────────────
-- 3️⃣  UPDATE EXISTING SEED DATA (set all existing to vless/link)
-- ──────────────────────────────────────────────
-- All existing servers in the DB are VLESS, so this is a safe default.
-- The DEFAULT clause already handles new inserts.
