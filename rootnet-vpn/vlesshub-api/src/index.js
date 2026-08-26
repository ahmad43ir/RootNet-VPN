// ============================================================
// vlesshub-api — VlessHub data plane on Cloudflare D1
// ============================================================
// Fully separated from RootNet's Supabase project. Public read
// endpoints mirror the exact PostgREST / proxy-api response shapes
// the VlessHub app already parses; admin endpoints (X-Admin-Key)
// serve the @Vless_hub_bot and the scraper forwarding.
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

const PING_DELAY_ERROR = 10000;

function proxyLink(host, port, secret) {
  const params = new URLSearchParams({ server: host, port: String(port) });
  if (secret) params.set('secret', secret);
  return `tg://proxy?${params.toString()}`;
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: CORS });

    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const adminKey = env.VLESSHUB_ADMIN_KEY ?? '';

    // ── Health ───────────────────────────────────────────────
    if (path === '/health') return json({ status: 'ok', service: 'vlesshub-api' });

    // ── Public: servers (Links tab) ──────────────────────────
    if (path === '/servers' && request.method === 'GET') {
      const { results } = await env.DB.prepare(
        `SELECT name, flag, country, config, type, config_format, source_channel, created_at
         FROM servers WHERE is_active = 1 ORDER BY id ASC`,
      ).all();
      return json(results ?? []);
    }

    // ── Public: proxies (MTProto tab) ────────────────────────
    if (path === '/proxies' && request.method === 'GET') {
      const { results } = await env.DB.prepare(
        `SELECT id, host, port, secret, source, working FROM proxies WHERE is_active = 1`,
      ).all();
      const rows = results ?? [];
      const working = rows.filter((r) => r.working);
      // 10 random, working first — mirrors proxy-api behaviour.
      const shuffled = [...working].sort(() => Math.random() - 0.5);
      const rest = rows.filter((r) => !r.working);
      const picked = shuffled.slice(0, 10);
      while (picked.length < 10 && rest.length) picked.push(rest.shift());
      return json({
        proxies: picked.map((r) => ({
          host: r.host,
          port: r.port,
          secret: r.secret || '',
          source: r.source || '',
          link: r.link || proxyLink(r.host, r.port, r.secret),
        })),
        pool_size: rows.length,
        working: working.length,
      });
    }

    // ── Public: files (Files tab) ────────────────────────────
    let m = path.match(/^\/files\/(\d+)\/content$/);
    if (m && request.method === 'GET') {
      const row = await env.DB.prepare(
        'SELECT filename, content FROM vpn_files WHERE id = ?',
      ).bind(Number(m[1])).first();
      if (!row) return json({ error: 'not found' }, 404);
      // App expects a PostgREST-style array with base64 `content`.
      return json([{ filename: row.filename, content: row.content }]);
    }
    if (path === '/files' && request.method === 'GET') {
      const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
      const { results } = await env.DB.prepare(
        `SELECT id, filename, size_bytes, uploaded_at, is_encrypted, config_count, source_channel
         FROM vpn_files ORDER BY uploaded_at DESC LIMIT ?`,
      ).bind(limit).all();
      return json((results ?? []).map((r) => ({ ...r, is_encrypted: !!r.is_encrypted })));
    }

    // ── Public: version config ───────────────────────────────
    if (path === '/version' && request.method === 'GET') {
      const row = await env.DB.prepare(
        `SELECT id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update
         FROM app_config WHERE id = 1`,
      ).first();
      return json({ ...row, force_update: !!row?.force_update });
    }

    // ── Admin auth gate ──────────────────────────────────────
    const isAdmin = adminKey !== '' && request.headers.get('x-admin-key') === adminKey;

    // ── Scraper ingestion (forwarded by vless-worker) ────────
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
        // Derive a human name from the fragment ("💦 4. VLESS - Clean IP").
        let name = `Server ${n}`;
        try {
          const frag = decodeURIComponent(config.split('#')[1] ?? '').trim();
          if (frag) name = frag.split('|')[0].trim().slice(0, 60) || name;
        } catch { /* keep fallback */ }
        const type = config.split(':')[0].toLowerCase();
        const res = await env.DB.prepare(
          `INSERT OR IGNORE INTO servers (name, flag, country, config, type, config_format, source_channel)
           VALUES (?, '🌐', 'Cloud', ?, ?, 'link', ?)`,
        ).bind(name, config, type, source).run();
        if (res.meta?.changes > 0) inserted++;
      }
      return json({ ok: true, inserted });
    }

    if (!isAdmin) return json({ error: 'unauthorized' }, 401);

    // ── Admin: servers ───────────────────────────────────────
    if (path === '/admin/servers' && request.method === 'GET') {
      const { results } = await env.DB.prepare(
        `SELECT id, name, flag, country, config, type, config_format, host FROM servers
         WHERE is_active = 1 ORDER BY id ASC`,
      ).all();
      return json({ rows: results ?? [] });
    }
    if (path === '/admin/servers' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      const row = body?.row;
      if (!row?.config) return json({ error: 'config required' }, 400);
      const dup = await env.DB.prepare('SELECT id FROM servers WHERE config = ?').bind(row.config).first();
      if (dup) return json({ ok: false, duplicate: true });
      await env.DB.prepare(
        `INSERT INTO servers (name, flag, country, config, host, port, type, config_format, source_channel)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        row.name || 'Server',
        row.flag || '🌐',
        row.country || 'Cloud',
        row.config,
        row.host || '',
        row.port || 0,
        row.type || 'vless',
        row.config_format || 'link',
        row.source_channel || '',
      ).run();
      return json({ ok: true });
    }
    if (path === '/admin/servers' && request.method === 'DELETE') {
      if (url.searchParams.get('all') === '1') {
        const res = await env.DB.prepare('DELETE FROM servers WHERE is_active = 1').run();
        return json({ count: res.meta?.changes ?? 0 });
      }
      const id = Number(url.searchParams.get('id'));
      if (!id) return json({ error: 'id or all=1 required' }, 400);
      const res = await env.DB.prepare('DELETE FROM servers WHERE id = ?').bind(id).run();
      return json({ count: res.meta?.changes ?? 0 });
    }
    if (path === '/admin/servers/geo' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      if (!body?.id) return json({ error: 'id required' }, 400);
      await env.DB.prepare('UPDATE servers SET flag = ?, country = ? WHERE id = ?')
        .bind(body.flag ?? '🌐', body.country ?? 'Cloud', body.id).run();
      return json({ ok: true });
    }
    if (path === '/admin/stats' && request.method === 'GET') {
      const row = await env.DB.prepare('SELECT COUNT(*) AS n FROM servers WHERE is_active = 1').first();
      return json({ count: row?.n ?? 0 });
    }

    // ── Admin: proxies ───────────────────────────────────────
    if (path === '/admin/proxies' && request.method === 'GET') {
      const { results } = await env.DB.prepare(
        `SELECT id, host, port, secret, source, added_at, is_active FROM proxies ORDER BY id ASC`,
      ).all();
      return json({ rows: (results ?? []).map((r) => ({ ...r, is_active: !!r.is_active })) });
    }
    if (path === '/admin/proxies' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      if (!body?.host || !body?.port) return json({ error: 'host and port required' }, 400);
      const dup = await env.DB.prepare('SELECT id FROM proxies WHERE host = ? AND port = ?')
        .bind(body.host, body.port).first();
      if (dup) return json({ result: 'exists' });
      await env.DB.prepare(
        `INSERT INTO proxies (host, port, secret, source, link) VALUES (?, ?, ?, ?, ?)`,
      ).bind(body.host, body.port, body.secret ?? '', body.source ?? 'manual',
        proxyLink(body.host, body.port, body.secret)).run();
      return json({ result: 'added' });
    }
    if (path === '/admin/proxies' && request.method === 'DELETE') {
      if (url.searchParams.get('all') === '1') {
        const res = await env.DB.prepare('DELETE FROM proxies').run();
        return json({ count: res.meta?.changes ?? 0 });
      }
      const target = url.searchParams.get('target') ?? '';
      if (!target) return json({ error: 'target or all=1 required' }, 400);
      const res = /^\d+$/.test(target)
        ? await env.DB.prepare('DELETE FROM proxies WHERE id = ?').bind(Number(target)).run()
        : await env.DB.prepare('DELETE FROM proxies WHERE host = ?').bind(target).run();
      return json({ count: res.meta?.changes ?? 0 });
    }

    // ── Admin: channels + last scrape (bot_state) ────────────
    if (path === '/admin/channels' && request.method === 'GET') {
      const row = await env.DB.prepare(`SELECT value FROM bot_state WHERE key = 'vless_channels'`).first();
      const channels = (row?.value ?? '').split(',').map((s) => s.trim()).filter(Boolean);
      return json({ channels });
    }
    if (path === '/admin/channels' && request.method === 'PUT') {
      const body = await request.json().catch(() => null);
      const channels = Array.isArray(body?.channels) ? body.channels : [];
      await env.DB.prepare(
        `INSERT INTO bot_state (key, value) VALUES ('vless_channels', ?)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      ).bind(channels.join(',')).run();
      return json({ ok: true });
    }
    if (path === '/admin/lastscrape' && request.method === 'GET') {
      const row = await env.DB.prepare(`SELECT value FROM bot_state WHERE key = 'last_scrape_time'`).first();
      return json({ value: row?.value ?? null });
    }
    if (path === '/admin/lastscrape' && request.method === 'PUT') {
      await env.DB.prepare(
        `INSERT INTO bot_state (key, value) VALUES ('last_scrape_time', ?)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      ).bind(new Date().toISOString()).run();
      return json({ ok: true });
    }

    // ── Admin: version config ────────────────────────────────
    if (path === '/admin/config' && request.method === 'GET') {
      const row = await env.DB.prepare(
        `SELECT id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update
         FROM app_config WHERE id = 1`,
      ).first();
      return json({ ...row, force_update: !!row?.force_update });
    }
    if (path === '/admin/config' && request.method === 'PUT') {
      const body = await request.json().catch(() => null);
      const allowed = ['latest_version', 'latest_build', 'minimum_version', 'update_url', 'release_notes', 'force_update'];
      const sets = [];
      const vals = [];
      for (const k of allowed) {
        if (k in (body ?? {})) {
          sets.push(`${k} = ?`);
          vals.push(k === 'force_update' ? (body[k] ? 1 : 0) : body[k]);
        }
      }
      if (!sets.length) return json({ error: 'nothing to update' }, 400);
      sets.push(`updated_at = ?`);
      vals.push(new Date().toISOString(), 1);
      await env.DB.prepare(`UPDATE app_config SET ${sets.join(', ')} WHERE id = ?`).bind(...vals).run();
      return json({ ok: true });
    }

    // ── Admin: files ─────────────────────────────────────────
    m = path.match(/^\/admin\/files\/(\d+)$/);
    if (m && request.method === 'GET') {
      const row = await env.DB.prepare(
        'SELECT filename, content, mime_type FROM vpn_files WHERE id = ?',
      ).bind(Number(m[1])).first();
      if (!row) return json({ error: 'not found' }, 404);
      return json(row);
    }
    if (path === '/admin/files' && request.method === 'GET') {
      const channel = url.searchParams.get('channel');
      const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
      const offset = Number(url.searchParams.get('offset') ?? 0);
      const encrypted = url.searchParams.get('encrypted') === '1';
      let sql = `SELECT id, filename, mime_type, size_bytes, source_channel, uploaded_at, is_encrypted, config_count
                 FROM vpn_files`;
      const where = [];
      const vals = [];
      if (channel) { where.push('source_channel = ?'); vals.push(channel); }
      if (encrypted) where.push('is_encrypted = 1');
      if (where.length) sql += ' WHERE ' + where.join(' AND ');
      sql += ' ORDER BY uploaded_at DESC LIMIT ? OFFSET ?';
      vals.push(limit, offset);
      const { results } = await env.DB.prepare(sql).bind(...vals).all();
      return json({ rows: (results ?? []).map((r) => ({ ...r, is_encrypted: !!r.is_encrypted })) });
    }
    if (path === '/admin/files/count' && request.method === 'GET') {
      const channel = url.searchParams.get('channel');
      const row = channel
        ? await env.DB.prepare('SELECT COUNT(*) AS n FROM vpn_files WHERE source_channel = ?').bind(channel).first()
        : await env.DB.prepare('SELECT COUNT(*) AS n FROM vpn_files').first();
      return json({ count: row?.n ?? 0 });
    }
    if (path === '/admin/files' && request.method === 'POST') {
      const body = await request.json().catch(() => null);
      if (!body?.filename || !body?.contentBase64) return json({ error: 'filename and contentBase64 required' }, 400);
      const dup = await env.DB.prepare('SELECT id FROM vpn_files WHERE filename = ? AND size_bytes = ?')
        .bind(body.filename, body.size_bytes ?? 0).first();
      if (dup) return json({ saved: false, duplicate: true });
      await env.DB.prepare(
        `INSERT INTO vpn_files (filename, mime_type, size_bytes, content, source_channel, is_encrypted, config_count)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
      ).bind(
        body.filename,
        body.mime_type ?? null,
        body.size_bytes ?? 0,
        body.contentBase64,
        body.source_channel ?? '',
        body.is_encrypted ? 1 : 0,
        body.config_count ?? 0,
      ).run();
      return json({ saved: true, duplicate: false });
    }
    if (path === '/admin/files' && request.method === 'DELETE') {
      const id = Number(url.searchParams.get('id'));
      if (!id) return json({ error: 'id required' }, 400);
      await env.DB.prepare('DELETE FROM vpn_files WHERE id = ?').bind(id).run();
      return json({ ok: true });
    }

    return json({ error: 'not found' }, 404);
  },
};
