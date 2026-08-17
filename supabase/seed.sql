-- ============================================================
-- 📁 seed.sql — RootNet VPN initial data
-- ============================================================
-- Populates the servers table AND the app_config table.
-- Idempotent — only inserts if tables are empty.
-- ============================================================

-- ──────────────────────────────────────────────
-- 🌐 Servers (only if empty)
-- ──────────────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.servers LIMIT 1) THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, type, config_format) VALUES
    (
      'Oak',
      '🌐',
      'Cloud',
      'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.66.45.6:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F%3FTELEGRAM-MARAMBASHI_MARAMBASHI_MARAMBASHI_MARAMBASHI_MARAMBASHI%3Fed#%40prrofile_purple',
      '172.66.45.6',
      443,
      true,
      'vless',
      'link'
    ),
    (
      'Pine',
      '🌐',
      'Cloud',
      'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@104.16.72.20:8443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple',
      '104.16.72.20',
      8443,
      true,
      'vless',
      'link'
    ),
    (
      'Redwood',
      '🇺🇸',
      'US',
      'vless://3536e1fa-0850-44d1-b123-925ce12476cf@206.71.158.124:443?encryption=none&security=tls&sni=dey.lnmarketplace.net&fp=chrome&alpn=h2&insecure=0&allowInsecure=0&type=xhttp&host=dey.lnmarketplace.net&path=%2Fkavir&mode=stream-one#%40prrofile_purple',
      '206.71.158.124',
      443,
      true,
      'vless',
      'link'
    ),
    (
      'Cedar',
      '🌐',
      'Cloud',
      'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.40.79:2083?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple',
      '172.64.40.79',
      2083,
      true,
      'vless',
      'link'
    ),
    (
      'Birch',
      '🌍',
      'CDN',
      'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@store.ubi.com:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple',
      'store.ubi.com',
      443,
      true,
      'vless',
      'link'
    );
  END IF;
END $$;

-- ──────────────────────────────────────────────
-- 🚀 VMess Servers (only if empty)
-- ──────────────────────────────────────────────
-- Three VMess servers at various locations with different
-- transports (WebSocket + TLS, TCP + TLS) for testing.
--
-- Config format: vmess://base64(JSON)
--   JSON keys: v, ps, add, port, id, aid, scy, net,
--              type, host, path, tls, sni, fp
-- ============================================================
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE type = 'vmess' LIMIT 1) THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, type, config_format) VALUES
    -- 🌐 Maple — Cloudflare CDN, WebSocket + TLS
    (
      'Maple',
      '🌐',
      'Cloud',
      'vmess://eyJ2IjoiMiIsInBzIjoiTWFwbGUiLCJhZGQiOiIxNzIuNjYuNDUuMTAiLCJwb3J0IjoiNDQzIiwiaWQiOiJhMWIyYzNkNC1lNWY2LTRhN2ItOGM5ZC0wZTFmMmEzYjRjNWQiLCJhaWQiOiIwIiwic2N5IjoiYXV0byIsIm5ldCI6IndzIiwidHlwZSI6Im5vbmUiLCJob3N0IjoibWFwbGUudm1lc3Mucml0dGJvLmtkbnMuZnIiLCJwYXRoIjoiLyIsInRscyI6InRscyIsInNuaSI6Im1hcGxlLnZtZXNzLnJpdHRiby5rZG5zLmZyIiwiZnAiOiJjaHJvbWUifQ',
      '172.66.45.10',
      443,
      true,
      false,
      'vmess',
      'link'
    ),
    -- 🇺🇸 Spruce — US-based, TCP + TLS
    (
      'Spruce',
      '🇺🇸',
      'US',
      'vmess://eyJ2IjoiMiIsInBzIjoiU3BydWNlIiwiYWRkIjoiMjA2LjcxLjE1OC4xMjUiLCJwb3J0IjoiNDQzIiwiaWQiOiJiMmMzZDRlNS1mNmE3LTRiOGMtOWQwZS0xZjJhM2I0YzVkNmUiLCJhaWQiOiIwIiwic2N5IjoiYXV0byIsIm5ldCI6InRjcCIsInR5cGUiOiJub25lIiwiaG9zdCI6InNwcnVjZS52bWVzcy5yaXR0Ym8ua2Rucy5mciIsInBhdGgiOiIvIiwidGxzIjoidGxzIiwic25pIjoic3BydWNlLnZtZXNzLnJpdHRiby5rZG5zLmZyIiwiZnAiOiJjaHJvbWUifQ',
      '206.71.158.125',
      443,
      true,
      false,
      'vmess',
      'link'
    ),
    -- 🇳🇱 Willow — Netherlands, WebSocket + TLS
    (
      'Willow',
      '🇳🇱',
      'NL',
      'vmess://eyJ2IjoiMiIsInBzIjoiV2lsbG93IiwiYWRkIjoiMTQ2LjE5MC4xMDAuNTAiLCJwb3J0IjoiNDQzIiwiaWQiOiJjM2Q0ZTVmNi1hN2I4LTRjOWQtMGUxZi0yYTNiNGM1ZDZlN2YiLCJhaWQiOiIwIiwic2N5IjoiYXV0byIsIm5ldCI6IndzIiwidHlwZSI6Im5vbmUiLCJob3N0Ijoid2lsbG93LnZtZXNzLnJpdHRiby5rZG5zLmZyIiwicGF0aCI6Ii8iLCJ0bHMiOiJ0bHMiLCJzbmkiOiJ3aWxsb3cudm1lc3Mucml0dGJvLmtkbnMuZnIiLCJmcCI6ImNocm9tZSJ9',
      '146.190.100.50',
      443,
      true,
      false,
      'vmess',
      'link'
    );
  END IF;
END $$;

-- ──────────────────────────────────────────────
-- ⚙️  App Config (only if empty)
-- ──────────────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.app_config WHERE id = 1) THEN
    INSERT INTO public.app_config (id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update)
    VALUES (
      1,
      '1.1.2',
      3,
      '1.0.0',
      'https://chobgroup.pages.dev',
      E'• New RootNet branding\n• Supabase Edge Function backend\n• Encrypted server list\n• Ping & speed test improvements\n• 30-min session timer\n• Persistent VPN notification',
      false
    );
  END IF;
END $$;
