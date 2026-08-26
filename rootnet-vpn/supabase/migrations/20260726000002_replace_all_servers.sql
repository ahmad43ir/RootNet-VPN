-- ============================================================
-- 📁 20260726000002_replace_all_servers.sql
-- ============================================================
-- Replaces ALL existing servers with 8 new configs from
-- @prrofile_purple Telegram channel.
--
-- Steps:
--   1. Delete ALL existing servers (hard delete)
--   2. Insert 8 new servers (Iris, Lily, Daisy, Rose,
--      Tulip, Orchid, Lavender, Violet)
-- ============================================================

-- 1️⃣ Delete all existing servers
DELETE FROM public.servers;

-- 2️⃣ Insert new servers
INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format) VALUES

-- 1. Iris — VLESS WebSocket + TLS (betty.ns.cloudflare.com)
(
  'Iris',
  '🌻',
  'Cloud',
  'vless://073d1d50-8478-47bf-a828-7a1b381931d5@betty.ns.cloudflare.com:443?path=%2Fodiyfws&security=tls&encryption=none&insecure=0&host=octopusss.net&fp=chrome&type=ws&allowInsecure=0&sni=octopusss.net',
  'betty.ns.cloudflare.com',
  443,
  true,
  false,
  'vless',
  'link'
),

-- 2. Lily — VLESS WebSocket + TLS (173.245.58.70:2053)
(
  'Lily',
  '🌻',
  'Cloud',
  'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@173.245.58.70:2053?path=%2F&security=tls&encryption=none&insecure=0&host=t1s1.rittbo.kdns.fr&fp=chrome&type=ws&allowInsecure=0&sni=t1s1.rittbo.kdns.fr',
  '173.245.58.70',
  2053,
  true,
  false,
  'vless',
  'link'
),

-- 3. Daisy — Trojan TCP + TLS (13.38.35.46)
(
  'Daisy',
  '🌞',
  'AWS',
  'trojan://ON38567014@13.38.35.46:443?security=tls&insecure=0&headerType=none&type=tcp&allowInsecure=0&sni=lucky-marmot.rooster465.autos',
  '13.38.35.46',
  443,
  true,
  false,
  'trojan',
  'link'
),

-- 4. Rose — VLESS Reality TCP (193.233.255.5)
-- ⚠️  Needs Reality support in buildStreamSettings before it can connect
(
  'Rose',
  '🌻',
  'Cloud',
  'vless://0108c657-a9bd-4c99-9179-74050cf78889@193.233.255.5:443?security=reality&encryption=none&pbk=SbVKOEMjK0sIlbwg4akyBg5mL5KZwwB-ed4eEE7YnRc&headerType=none&fp=firefox&type=tcp&flow=xtls-rprx-vision&sni=sellflow.org',
  '193.233.255.5',
  443,
  true,
  false,
  'vless',
  'link'
),

-- 5. Tulip — VLESS WebSocket + TLS (ctcc.cloudflare.seeck.cn)
(
  'Tulip',
  '🌸',
  'China',
  'vless://57d68ec3-6cc5-10e9-2bca-5ba3994aa783@ctcc.cloudflare.seeck.cn:443?path=%2Fglasspanel%2F0038ba35_0_1bdabe931208%2F&security=tls&encryption=none&insecure=0&host=2.r.y.a.j.7.c.z.d.z.8.f.f.r.us.art-us.kdns.fr&fp=chrome&type=ws&allowInsecure=0&sni=2.r.y.a.j.7.c.z.d.z.8.f.f.r.us.art-us.kdns.fr',
  'ctcc.cloudflare.seeck.cn',
  443,
  true,
  false,
  'vless',
  'link'
),

-- 6. Orchid — VLESS Reality xhttp (animall-zooke.fardaty.ir:8080)
-- ⚠️  Needs Reality + xhttp support in buildStreamSettings
(
  'Orchid',
  '🌺',
  'Iran',
  'vless://e912f8d6-f59a-48b1-b5ad-bbe548d7ebf3@animall-zooke.fardaty.ir:8080?mode=packet-up&path=%2F&security=reality&encryption=none&pbk=bkj8m8COBtgcxZZ0ankB6vbxWRwnnreXjDTZA5CUVB0&fp=chrome&type=xhttp&sni=www.yahoo.com&sid=4da5c8a61cc588e8',
  'animall-zooke.fardaty.ir',
  8080,
  true,
  false,
  'vless',
  'link'
),

-- 7. Lavender — VLESS WebSocket + TLS (cf.levikogjgfdd.ir)
(
  'Lavender',
  '🌹',
  'Cloud',
  'vless://7968c546-02dc-4f8c-b791-934591a94cb2@cf.levikogjgfdd.ir:443?path=%2Fcbasur&security=tls&encryption=none&insecure=0&host=hhvl.hhapp.kdns.fr&type=ws&allowInsecure=0&sni=hhvl.hhapp.kdns.fr',
  'cf.levikogjgfdd.ir',
  443,
  true,
  false,
  'vless',
  'link'
),

-- 8. Violet — VLESS WebSocket + TLS (104.21.40.208:8443)
(
  'Violet',
  '🌷',
  'Cloud',
  'vless://9047efd2-5aa4-4c9c-a58f-a5791b06c79e@104.21.40.208:8443?path=%2F&security=tls&encryption=none&insecure=0&host=ad-010.vector2llqn.info&fp=chrome&type=ws&allowInsecure=0&sni=ad-010.vector2llqn.info',
  '104.21.40.208',
  8443,
  true,
  false,
  'vless',
  'link'
);
