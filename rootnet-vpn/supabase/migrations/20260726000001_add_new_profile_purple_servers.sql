-- ============================================================
-- 📁 20260726000001_add_new_profile_purple_servers.sql
-- ============================================================
-- Adds new servers from @prrofile_purple Telegram channel
-- including VLESS (WS+TLS), Trojan (TCP+TLS), and VLESS Reality.
--
-- ⚠️  VLESS Reality servers (#4, #6) need code changes to
--     buildStreamSettings in config_normalizer.dart before
--     they will work. The SQL adds them anyway so they're
--     ready when the code is updated.
-- ============================================================

-- Insert servers (skip if config already exists)
DO $$
BEGIN

  -- 1. 🌻 Cloudflare CDN — betty.ns.cloudflare.com
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://073d1d50-8478-47bf-a828-7a1b381931d5@betty.ns.cloudflare.com%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 2. 🌻 Cloudflare CDN — 173.245.58.70:2053
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@173.245.58.70:2053%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 3. 🌞 Trojan — AWS (13.38.35.46)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'trojan://ON38567014@13.38.35.46%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 4. 🌻 VLESS Reality — 193.233.255.5
  -- ⚠️  NEEDS CODE CHANGE: buildStreamSettings doesn't handle reality yet
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://0108c657-a9bd-4c99-9179-74050cf78889@193.233.255.5%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 5. 🌸 VLESS WS+TLS — ctcc.cloudflare.seeck.cn
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://57d68ec3-6cc5-10e9-2bca-5ba3994aa783@ctcc.cloudflare.seeck.cn%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 6. 🌺 VLESS Reality xhttp — animall-zooke.fardaty.ir:8080
  -- ⚠️  NEEDS CODE CHANGE: buildStreamSettings doesn't handle reality + xhttp yet
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://e912f8d6-f59a-48b1-b5ad-bbe548d7ebf3@animall-zooke.fardaty.ir%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 7. 🌹 VLESS WS+TLS — cf.levikogjgfdd.ir (different UUID)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://7968c546-02dc-4f8c-b791-934591a94cb2@cf.levikogjgfdd.ir%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
    );
  END IF;

  -- 8. 🌷 VLESS WS+TLS — Cloudflare CDN (104.21.40.208:8443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://9047efd2-5aa4-4c9c-a58f-a5791b06c79e@104.21.40.208:8443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES (
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
  END IF;

END $$;
