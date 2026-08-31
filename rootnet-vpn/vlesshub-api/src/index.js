// ============================================================
// vlesshub-api — VlessHub data plane
// ============================================================
// Public endpoints read from Supabase (where the scraper writes).
// Admin endpoints also write to Supabase.
// ============================================================

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type, x-admin-key',
};

const json = (data, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS, 'Content-Type': 'application/json' },
  });

function proxyLink(host, port, secret) {
  const params = new URLSearchParams({ server: host, port: String(port) });
  if (secret) params.set('secret', secret);
  return `https://t.me/proxy?${params.toString()}`;
}

// ─── Supabase helper ──────────────────────────────────────────
async function sbGet(env, table, params = {}) {
  const url = new URL(`${env.SUPABASE_URL}/rest/v1/${table}`);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const res = await fetch(url.toString(), {
    headers: {
      apikey: env.SUPABASE_KEY,
      Authorization: `Bearer ${env.SUPABASE_KEY}`,
    },
  });
  if (!res.ok) return [];
  return res.json();
}

async function sbCount(env, table) {
  const res = await fetch(`${env.SUPABASE_URL}/rest/v1/${table}?select=id&limit=0`, {
    headers: {
      apikey: env.SUPABASE_KEY,
      Authorization: `Bearer ${env.SUPABASE_KEY}`,
      Prefer: 'count=exact',
    },
  });
  const cr = res.headers.get('content-range', '*/0');
  const n = cr.split('/')[1];
  return n === '*' ? 0 : parseInt(n, 10) || 0;
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const adminKey = env.VLESSHUB_ADMIN_KEY ?? '';

    // ── Health ───────────────────────────────────────────────
    if (path === '/health') return json({ status: 'ok', service: 'vlesshub-api' });

    // ── Public: request a 6-digit code ───────────────────────
    // Uses Cloudflare KV for persistent storage across isolates.
    if (path === '/request-code' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      const device = String(body?.device ?? 'unknown');
      const code = String(Math.floor(100000 + Math.random() * 900000));
      const expires = Date.now() + 5 * 60 * 1000; // 5 min
      await env.OTP_STORE.put(code, JSON.stringify({ expires, device }), { expirationTtl: 300 });
      // Send email
      try {
        await fetch('https://api.resend.com/emails', {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${env.RESEND_API_KEY}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            from: 'Admin VlessHub <onboarding@resend.dev>',
            to: ['mobileahmad43@gmail.com'],
            subject: `🔐 Admin VlessHub — Code: ${code}`,
            html: `<h2>Admin VlessHub Login Code</h2><p style="font-size:32px;font-weight:bold;letter-spacing:8px;color:#FF5252">${code}</p><p><strong>Device:</strong> ${device}</p><p><strong>Expires in 5 minutes.</strong></p>`,
          }),
        });
      } catch { /* email failed but code still valid */ }
      return json({ ok: true });
    }

    // ── Public: verify code → returns admin key ──────────────
    if (path === '/verify-code' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      const code = String(body?.code ?? '').trim();
      if (!code || code.length !== 6) return json({ ok: false, error: 'invalid code' }, 400);
      const raw = await env.OTP_STORE.get(code);
      if (!raw) return json({ ok: false, error: 'wrong code' }, 401);
      const entry = JSON.parse(raw);
      if (entry.expires < Date.now()) { await env.OTP_STORE.delete(code); return json({ ok: false, error: 'code expired' }, 401); }
      // Code valid — delete immediately (one-time use) and return admin key
      await env.OTP_STORE.delete(code);
      return json({ ok: true, adminKey: adminKey });
    }

    // ── Public: servers (Links tab) ──────────────────────────
    if (path === '/servers' && request.method === 'GET') {
      const rows = await sbGet(env, 'servers', {
        select: 'id,name,flag,country,config,type,config_format,source_channel,created_at',
        order: 'id.asc',
        is_active: 'eq.true',
      });
      return json(rows ?? []);
    }

    // ── Public: proxies (MTProto tab) ────────────────────────
    if (path === '/proxies' && request.method === 'GET') {
      const rows = await sbGet(env, 'scraper_proxies', {
        select: 'id,host,port,secret,source,is_active,last_ok',
        order: 'id.asc',
      });
      const active = (rows ?? []).filter((r) => r.is_active !== false);
      const working = active.filter((r) => r.last_ok === true);
      // 10 random, working first
      const shuffled = [...working].sort(() => Math.random() - 0.5);
      const rest = active.filter((r) => r.last_ok !== true);
      const picked = shuffled.slice(0, 10);
      while (picked.length < 10 && rest.length) picked.push(rest.shift());
      return json({
        proxies: picked.map((r) => ({
          id: String(r.id),
          host: r.host,
          port: r.port,
          secret: r.secret || '',
          source: r.source || '',
          link: proxyLink(r.host, r.port, r.secret),
        })),
        pool_size: active.length,
        working: working.length,
      });
    }

    // ── Public: files (Files tab) ────────────────────────────
    let m = path.match(/^\/files\/(\d+)\/content$/);
    if (m && request.method === 'GET') {
      const rows = await sbGet(env, 'vpn_files', {
        select: 'filename,content',
        id: `eq.${m[1]}`,
        limit: '1',
      });
      if (!rows.length) return json({ error: 'not found' }, 404);
      return json([{ filename: rows[0].filename, content: rows[0].content }]);
    }
    if (path === '/files' && request.method === 'GET') {
      const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
      const rows = await sbGet(env, 'vpn_files', {
        select: 'id,filename,size_bytes,uploaded_at,is_encrypted,config_count,source_channel',
        order: 'uploaded_at.desc',
        limit: String(limit),
      });
      return json((rows ?? []).map((r) => ({ ...r, is_encrypted: !!r.is_encrypted })));
    }

    // ── Public: version config ───────────────────────────────
    if (path === '/version' && request.method === 'GET') {
      const rows = await sbGet(env, 'app_config', {
        select: 'id,latest_version,latest_build,minimum_version,update_url,release_notes,force_update',
        id: 'eq.1',
        limit: '1',
      });
      if (!rows.length) return json({});
      return json({ ...rows[0], force_update: !!rows[0].force_update });
    }

    // ── Admin auth gate ──────────────────────────────────────
    const isAdmin = adminKey !== '' && request.headers.get('x-admin-key') === adminKey;

    // ── Scraper ingestion ────────────────────────────────────
    if (path === '/ingest' && request.method === 'POST') {
      if (!isAdmin) return json({ error: 'unauthorized' }, 401);
      const body = await request.json().catch(() => null);
      const links = Array.isArray(body?.links) ? body.links : [];
      const source = String(body?.source ?? '');
      if (!links.length) return json({ ok: true, inserted: 0 });
      let inserted = 0;
      let n = 0;
      for (const link of links) {
        n++;
        const config = String(link).trim().replace(/[.,;:]+$/, '');
        if (!/^(vless|trojan|vmess|ss|hysteria2|wireguard):\/\//i.test(config)) continue;
        let name = `Server ${n}`;
        try {
          const frag = decodeURIComponent(config.split('#')[1] ?? '').trim();
          if (frag) name = frag.split('|')[0].trim().slice(0, 60) || name;
        } catch { /* keep fallback */ }
        const type = config.split(':')[0].toLowerCase();
        // Insert into Supabase servers table
        const res = await fetch(`${env.SUPABASE_URL}/rest/v1/servers`, {
          method: 'POST',
          headers: {
            apikey: env.SUPABASE_KEY,
            Authorization: `Bearer ${env.SUPABASE_KEY}`,
            'Content-Type': 'application/json',
            Prefer: 'return=minimal',
          },
          body: JSON.stringify({
            name,
            flag: '\ud83c\udf10',
            country: 'Cloud',
            config,
            type,
            config_format: 'link',
            source_channel: source,
          }),
        });
        if (res.ok) inserted++;
      }
      return json({ ok: true, inserted });
    }

    if (!isAdmin) return json({ error: 'unauthorized' }, 401);

    // ── Admin: DELETE servers by ID ──────────────────────────
    m = path.match(/^\/servers\/(\d+)$/);
    if (m && request.method === 'DELETE') {
      const id = Number(m[1]);
      const res = await fetch(`${env.SUPABASE_URL}/rest/v1/servers?id=eq.${id}`, {
        method: 'DELETE',
        headers: { apikey: env.SUPABASE_KEY, Authorization: `Bearer ${env.SUPABASE_KEY}` },
      });
      return json({ ok: res.ok, deleted: res.ok ? 1 : 0 });
    }

    // ── Admin: DELETE files by ID ────────────────────────────
    m = path.match(/^\/files\/(\d+)$/);
    if (m && request.method === 'DELETE') {
      const id = Number(m[1]);
      const res = await fetch(`${env.SUPABASE_URL}/rest/v1/vpn_files?id=eq.${id}`, {
        method: 'DELETE',
        headers: { apikey: env.SUPABASE_KEY, Authorization: `Bearer ${env.SUPABASE_KEY}` },
      });
      return json({ ok: res.ok, deleted: res.ok ? 1 : 0 });
    }

    // ── Admin: DELETE proxies by ID ──────────────────────────
    m = path.match(/^\/proxies\/(\d+)$/);
    if (m && request.method === 'DELETE') {
      const id = Number(m[1]);
      const res = await fetch(`${env.SUPABASE_URL}/rest/v1/scraper_proxies?id=eq.${id}`, {
        method: 'DELETE',
        headers: { apikey: env.SUPABASE_KEY, Authorization: `Bearer ${env.SUPABASE_KEY}` },
      });
      return json({ ok: res.ok, deleted: res.ok ? 1 : 0 });
    }

    // ── Admin: servers CRUD ──────────────────────────────────
    if (path === '/admin/servers' && request.method === 'GET') {
      const rows = await sbGet(env, 'servers', {
        select: 'id,name,flag,country,config,type,config_format,host',
        order: 'id.asc',
        is_active: 'eq.true',
      });
      return json({ rows: rows ?? [] });
    }
    if (path === '/admin/servers' && request.method === 'DELETE') {
      if (url.searchParams.get('all') === '1') {
        const res = await fetch(`${env.SUPABASE_URL}/rest/v1/servers?is_active=eq.true`, {
          method: 'DELETE',
          headers: { apikey: env.SUPABASE_KEY, Authorization: `Bearer ${env.SUPABASE_KEY}` },
        });
        return json({ ok: res.ok });
      }
      const id = Number(url.searchParams.get('id'));
      if (!id) return json({ error: 'id or all=1 required' }, 400);
      const res = await fetch(`${env.SUPABASE_URL}/rest/v1/servers?id=eq.${id}`, {
        method: 'DELETE',
        headers: { apikey: env.SUPABASE_KEY, Authorization: `Bearer ${env.SUPABASE_KEY}` },
      });
      return json({ ok: res.ok });
    }

    // ── Admin: proxies CRUD ──────────────────────────────────
    if (path === '/admin/proxies' && request.method === 'GET') {
      const rows = await sbGet(env, 'scraper_proxies', {
        select: 'id,host,port,secret,source,added_at,is_active',
        order: 'id.asc',
      });
      return json({ rows: (rows ?? []).map((r) => ({ ...r, is_active: !!r.is_active })) });
    }
    if (path === '/admin/proxies' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      if (!body?.host || !body?.port) return json({ error: 'host and port required' }, 400);
      const res = await fetch(`${env.SUPABASE_URL}/rest/v1/scraper_proxies`, {
        method: 'POST',
        headers: {
          apikey: env.SUPABASE_KEY,
          Authorization: `Bearer ${env.SUPABASE_KEY}`,
          'Content-Type': 'application/json',
          Prefer: 'return=minimal',
        },
        body: JSON.stringify({
          host: body.host,
          port: body.port,
          secret: body.secret ?? '',
          source: body.source ?? 'manual',
        }),
      });
      return json({ ok: res.ok });
    }

    // ── Admin: files ─────────────────────────────────────────
    if (path === '/admin/files' && request.method === 'GET') {
      const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
      const rows = await sbGet(env, 'vpn_files', {
        select: 'id,filename,mime_type,size_bytes,source_channel,uploaded_at,is_encrypted,config_count',
        order: 'uploaded_at.desc',
        limit: String(limit),
      });
      return json({ rows: (rows ?? []).map((r) => ({ ...r, is_encrypted: !!r.is_encrypted })) });
    }

    // ── Admin: channels ──────────────────────────────────────
    if (path === '/admin/channels' && request.method === 'GET') {
      const rows = await sbGet(env, 'scraper_config', {
        select: 'value',
        key: 'eq.vless_channels',
        limit: '1',
      });
      const channels = (rows[0]?.value ?? '').split(',').map((s) => s.trim()).filter(Boolean);
      return json({ channels });
    }

    // ── Admin: version config ────────────────────────────────
    if (path === '/admin/config' && request.method === 'GET') {
      const rows = await sbGet(env, 'app_config', {
        select: '*',
        id: 'eq.1',
        limit: '1',
      });
      return json(rows[0] ?? {});
    }

    return json({ error: 'not found' }, 404);
  },
};
