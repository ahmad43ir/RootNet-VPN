-- ============================================================
-- 📁 20260813000001_bump_app_config_2_0_0.sql
-- ============================================================
-- RootNet v2.0 — config-launcher rewrite (built-in Xray engine removed).
--
-- The app now just serves configs (copy / open in your own client). Bump the
-- advertised latest build so installed 1.x VPN apps are prompted to update to
-- the new 2.0.0 APK; the new app sees no update (quiet version gate).
-- ============================================================

INSERT INTO public.app_config (id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update)
VALUES (1, '2.0.0', 101, '1.0.0', 'https://chobgroup.pages.dev',
        E'• v2.0 — RootNet is now a config launcher\n• No built-in VPN engine — copy configs or open them in your own client\n• Picture ads before copy, short video before export\n• No account needed',
        false)
ON CONFLICT (id) DO UPDATE SET
    latest_version  = EXCLUDED.latest_version,
    latest_build    = EXCLUDED.latest_build,
    minimum_version = EXCLUDED.minimum_version,
    update_url      = EXCLUDED.update_url,
    release_notes   = EXCLUDED.release_notes,
    force_update    = EXCLUDED.force_update,
    updated_at      = now();
