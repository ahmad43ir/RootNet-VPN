// ============================================================
// 📁 index.ts — PROXYBOX API (SUPABASE EDGE FUNCTION)
// ============================================================
// Public, no-auth endpoint that serves 10 random working MTProto
// proxies (tg:// links) from the shared `scraper_proxies` pool —
// the same pool the RootNet Telegram scraper maintains. The
// ProxyBox Android app (com.chobgroup.proxybox) consumes this.
//
// Endpoints:
//   GET /proxies  — 10 random active MTProto proxies (tg:// links)
//   GET /health   — health check
//
// Deploy (same Supabase project as RootNet):
//   npx supabase functions deploy proxy-api \
//     --no-verify-jwt --project-ref bprkazfxqmanrybiexnh
//
// Requires migration 20260808000001 (scraper_proxies table) to be
// applied, and the scraper/bot to have seeded the pool (proxies are
// only useful once they've been tested → last_ok = true).
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';
import { checkIpRateLimit } from '../_shared/rate-limit.ts';

const BATCH_SIZE = 10;
const MAX_FETCH = 100;

Deno.serve(async (req) => {
  // ── CORS preflight ─────────────────────────────────────────────
  if (req.method === 'OPTIONS') {
    return new Response(null, { status: 204, headers: corsHeaders(req) });
  }

  const url = new URL(req.url);
  const route = normalizedRoute(url.pathname);

  // ── Health ─────────────────────────────────────────────────────
  if (req.method === 'GET' && (route === '/' || route === '/health')) {
    return json({ status: 'ok', service: 'proxy-api' });
  }

  // ── Random proxy batch ─────────────────────────────────────────
  if (req.method === 'GET' && route === '/proxies') {
    return handleProxies(req);
  }

  return json({ error: 'Not found' }, 404, req);
});

// ═══════════════════════════════════════════════════════════════
// GET /proxies — 10 random active MTProto proxies
// ═══════════════════════════════════════════════════════════════
// Picks a random subset: the working pool (last_ok = true) is
// shuffled and served first; if there are fewer than 10, untested
// but active rows top the batch up. Dead proxies (is_active=false)
// are never served.
async function handleProxies(req: Request): Promise<Response> {
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!supabaseUrl || !supabaseKey) {
    return json({ error: 'Server configuration error' }, 500, req);
  }
  const supabase = createClient(supabaseUrl, supabaseKey);

  // IP rate limit — shared Postgres RPC (same as rootnet-api).
  const { allowed } = await checkIpRateLimit(supabase, getClientIp(req));
  if (!allowed) {
    return json({ error: 'Too many requests. Please slow down.' }, 429, req);
  }

  try {
    // Exact counts (head queries — no rows returned) for an accurate pool_size.
    const [{ count: activeCount }, { count: workingCount }] = await Promise.all([
      supabase.from('scraper_proxies').select('id', { count: 'exact', head: true }).eq('is_active', true),
      supabase.from('scraper_proxies').select('id', { count: 'exact', head: true }).eq('is_active', true).eq('last_ok', true),
    ]);

    const { data, error } = await supabase
      .from('scraper_proxies')
      .select('host, port, secret, source, last_ok')
      .eq('is_active', true)
      .order('last_checked', { ascending: false, nullsFirst: false })
      .limit(MAX_FETCH);

    if (error) throw error;

    const rows: any[] = data ?? [];
    const working = rows.filter((r) => r.last_ok === true);
    const untested = rows.filter((r) => r.last_ok !== true);

    // Random subset — working proxies first, untested tops up.
    const picked = [...shuffle(working), ...shuffle(untested)].slice(0, BATCH_SIZE);

    const proxies = picked.map((r) => ({
      host: r.host,
      port: r.port,
      secret: r.secret ?? null,
      source: r.source ?? null,
      link: buildTgLink(r.host, r.port, r.secret ?? null),
    }));

    return json(
      {
        proxies,
        pool_size: activeCount ?? rows.length,
        working: workingCount ?? working.length,
      },
      200,
      req,
    );
  } catch (e) {
    console.error('[proxy-api] /proxies failed:', (e as Error).message);
    return json({ error: 'Internal server error' }, 500, req);
  }
}

// ─── Helpers ────────────────────────────────────────────────────

function buildTgLink(host: string, port: number, secret: string | null): string {
  let link = `https://t.me/proxy?server=${encodeURIComponent(host)}&port=${port}`;
  if (secret) link += `&secret=${encodeURIComponent(secret)}`;
  return link;
}

function shuffle<T>(arr: T[]): T[] {
  const out = [...arr];
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

// ─── CORS / response / routing ──────────────────────────────────

function corsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get('Origin') || '';
  return {
    'Access-Control-Allow-Origin': origin || '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Max-Age': '86400',
    'Vary': 'Origin',
  };
}

function json(
  data: Record<string, unknown>,
  status = 200,
  req?: Request,
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...(req ? corsHeaders(req) : {}),
    },
  });
}

function normalizedRoute(pathname: string): string {
  const marker = '/proxy-api';
  const idx = pathname.lastIndexOf(marker);
  const path = idx >= 0 ? pathname.slice(idx + marker.length) : pathname;
  return path === '' ? '/' : path;
}

function getClientIp(req: Request): string {
  // Only trust x-forwarded-for (set by the Supabase gateway) — for rate
  // limiting only, never for location. Rightmost = original client.
  const forwarded = req.headers.get('x-forwarded-for');
  if (forwarded) {
    const ips = forwarded.split(',').map((ip) => ip.trim()).filter(Boolean);
    if (ips.length > 0) return ips[ips.length - 1];
  }
  return 'unknown';
}
