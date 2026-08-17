-- ============================================================
-- 📁 20260721000006_add_vless_servers.sql
-- ============================================================
-- Adds 15 new VLESS servers (deduplicated from user's list)
-- to the public.servers table.
--
-- These are VLESS over WebSocket + TLS configs routed through
-- Cloudflare CDN, sourced from @prrofile_purple Telegram channel.
-- ============================================================

-- Add new servers (skip if already exists by config)
DO $$
BEGIN
  -- Ash — Cloudflare CDN (104.16.72.20:8443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@104.16.72.20:8443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Ash', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@104.16.72.20:8443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '104.16.72.20', 8443, true, false, 'vless', 'link');
  END IF;

  -- Elm — Finland (104.16.72.41:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://c19735a7-ff97-a112-3f07-60e500f7719b@104.16.72.41:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Elm', '🇫🇮', 'Finland', 'vless://c19735a7-ff97-a112-3f07-60e500f7719b@104.16.72.41:443?encryption=none&security=tls&sni=l-j-uu-MiTiVPN---MiTiVPN-o.MAQROR.ir&fp=chrome&alpn=http%2F1.1&insecure=0&allowInsecure=0&type=ws&host=l-j-iii-MiTiVPN---MiTiVPN-o.MAQROR.ir&path=%2F---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN%3Dfinland#%40prrofile_purple', '104.16.72.41', 443, true, false, 'vless', 'link');
  END IF;

  -- Fir — Cloudflare CDN (104.18.42.54:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@104.18.42.54:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Fir', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@104.18.42.54:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '104.18.42.54', 443, true, false, 'vless', 'link');
  END IF;

  -- Hazel — Cloudflare CDN (172.64.144.82:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.144.82:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Hazel', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.144.82:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '172.64.144.82', 443, true, false, 'vless', 'link');
  END IF;

  -- Holly — Cloudflare CDN (172.64.145.158:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.145.158:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Holly', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.145.158:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '172.64.145.158', 443, true, false, 'vless', 'link');
  END IF;

  -- Ivy — Cloudflare CDN (172.64.40.49:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.40.49:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Ivy', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.40.49:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '172.64.40.49', 443, true, false, 'vless', 'link');
  END IF;

  -- Juniper — Cloudflare CDN (172.64.40.79:2083)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.40.79:2083%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Juniper', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.40.79:2083?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '172.64.40.79', 2083, true, false, 'vless', 'link');
  END IF;

  -- Laurel — Cloudflare CDN (172.64.53.65:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.53.65:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Laurel', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.64.53.65:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', '172.64.53.65', 443, true, false, 'vless', 'link');
  END IF;

  -- Magnolia — Cloudflare CDN (172.66.45.6:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.66.45.6:443%' AND name != 'Oak') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Magnolia', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@172.66.45.6:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F%3FTELEGRAM-MARAMBASHI_MARAMBASHI_MARAMBASHI_MARAMBASHI_MARAMBASHI%3Fed#%40prrofile_purple', '172.66.45.6', 443, true, false, 'vless', 'link');
  END IF;

  -- Olive — Pages.dev Proxy (172.67.75.194:8443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://767b6340-96dc-4aa0-8013-a8af7513d920@172.67.75.194:8443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Olive', '🌐', 'Cloud', 'vless://767b6340-96dc-4aa0-8013-a8af7513d920@172.67.75.194:8443?encryption=none&security=tls&sni=c7dabe95.proxy-978.pages.dev&insecure=0&allowInsecure=0&type=ws&host=c7dabe95.proxy-978.pages.dev&path=%2F#%40prrofile_purple', '172.67.75.194', 8443, true, false, 'vless', 'link');
  END IF;

  -- Palm — Germany (188.114.97.6:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://c19735a7-ff97-a112-3f07-60e500f7719b@188.114.97.6:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Palm', '🇩🇪', 'Germany', 'vless://c19735a7-ff97-a112-3f07-60e500f7719b@188.114.97.6:443?encryption=none&security=tls&sni=ldej--MiTiVPN---MiTiVPN-o.MAQROR.ir&fp=chrome&alpn=http%2F1.1&insecure=0&allowInsecure=0&type=ws&host=l-j-p-MiTiVPN---MiTiVPN-o.MAQROR.ir&path=%2F---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN---%40MiTiVPN%3Dde#%40prrofile_purple', '188.114.97.6', 443, true, false, 'vless', 'link');
  END IF;

  -- Cypress — Dedicated IP (45.130.125.207:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@45.130.125.207:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Cypress', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@45.130.125.207:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2Ffp#%40prrofile_purple', '45.130.125.207', 443, true, false, 'vless', 'link');
  END IF;

  -- Aspen — CDN Domain (celestara.biz:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@celestara.biz:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Aspen', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@celestara.biz:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', 'celestara.biz', 443, true, false, 'vless', 'link');
  END IF;

  -- Yew — Iran CDN (cf.levikogjgfdd.ir:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://bc6e7cf0-9526-4fea-b2e3-5bcf992c565e@cf.levikogjgfdd.ir:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Yew', '🌐', 'Cloud', 'vless://bc6e7cf0-9526-4fea-b2e3-5bcf992c565e@cf.levikogjgfdd.ir:443?encryption=none&security=tls&sni=blog.webex.com.yxls.eu.cc&fp=chrome&insecure=0&allowInsecure=0&type=ws&host=blog.webex.com.yxls.eu.cc&path=%2Fmy-kc#%40prrofile_purple', 'cf.levikogjgfdd.ir', 443, true, false, 'vless', 'link');
  END IF;

  -- Acacia — Ubisoft CDN (store.ubi.com:443)
  IF NOT EXISTS (SELECT 1 FROM public.servers WHERE config LIKE 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@store.ubi.com:443%') THEN
    INSERT INTO public.servers (name, flag, country, config, host, port, is_active, premium_only, type, config_format)
    VALUES ('Acacia', '🌐', 'Cloud', 'vless://eeb6823c-b926-4ea2-866a-5542edd26e59@store.ubi.com:443?encryption=none&security=tls&sni=t1s1.rittbo.kdns.fr&insecure=0&allowInsecure=0&type=ws&host=t1s1.rittbo.kdns.fr&path=%2F#%40prrofile_purple', 'store.ubi.com', 443, true, false, 'vless', 'link');
  END IF;
END $$;
