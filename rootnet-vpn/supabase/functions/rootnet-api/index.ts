// ============================================================
// 📁 index.ts — ROOTNET VPN API (SUPABASE EDGE FUNCTION)
// ============================================================
// Replaces the Cloudflare Worker completely.
//
// Endpoints:
//   GET  /public/servers     — Public server list (no auth)
//   POST /servers            — Full server list (JWT required)
//   POST /version            — Version check (JWT required)
//   POST /register-device    — Register FCM token (JWT required)
//   POST /unregister-device  — Remove FCM token (JWT required)
//   POST /send-notification  — Push notification (admin key)
//   POST /import-vless       — Manual trigger of the import RPC (admin key; also runs via pg_cron)
//   GET  /geoip             — GeoIP lookup (proxied to geo-api)
//   GET  /health or /        — Health check
//
// Security:
//   - JWT via supabase.auth.getUser() (no manual JWKS)
//   - Rate limiting via Postgres RPC (no in-memory Maps)
//   - CORS with allowed origins list
//   - No Cloudflare-specific headers
//
// Deploy: supabase functions deploy rootnet-api --no-verify-jwt
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';
import { getCorsHeaders, jsonResponse, corsPreflight, getClientIp, validateAntiReplay } from './_utils.ts';
import { authenticate } from './_auth.ts';
import { checkIpRateLimit } from './_rate-limit.ts';
import { sendPushNotification } from './_fcm.ts';

// ═══════════════════════════════════════════════════════════════════════════════
// 🏠  MAIN HANDLER
// ═══════════════════════════════════════════════════════════════════════════════

Deno.serve(async (req) => {
  const startTime = Date.now();

  // ── CORS preflight ──────────────────────────────────────────────────────
  if (req.method === 'OPTIONS') {
    return corsPreflight(req);
  }

  const url = new URL(req.url);
  const method = req.method;

  // ── Extract route path ────────────────────────────────────────────────
  // Supabase Edge Functions receive the full URL including prefix.
  // Example: /functions/v1/rootnet-api/health
  // We extract just the meaningful route part.
  const fullPath = url.pathname;
  const fnMarker = '/rootnet-api/';
  const fnIndex = fullPath.lastIndexOf(fnMarker);
  const normalizedRoute = fnIndex >= 0
    ? fullPath.slice(fnIndex + fnMarker.length - 1) // gives /health
    : fullPath;

  // Log incoming request for debugging
  console.log(`[rootnet-api] ${method} ${fullPath} → route="${normalizedRoute}" (${Date.now() - startTime}ms)`);

  // ── Validate environment ────────────────────────────────────────────────
  const supabaseUrl = Deno.env.get('SUPABASE_URL');
  const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (!supabaseUrl || !supabaseKey) {
    return jsonResponse(
      { error: 'Server configuration error' },
      500,
      getCorsHeaders(req),
    );
  }

  const supabase = createClient(supabaseUrl, supabaseKey);

  // ── Rate limit ALL requests by IP ───────────────────────────────────────
  const clientIp = getClientIp(req);
  const { allowed: ipAllowed } = await checkIpRateLimit(supabase, clientIp);
  if (!ipAllowed) {
    return jsonResponse(
      { error: 'Too many requests. Please slow down.' },
      429,
      getCorsHeaders(req),
    );
  }

  try {
    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS (no auth required)
    // ═══════════════════════════════════════════════════════════════════════

    // ── GET /public/servers — Public server list ─────────────────────────
    if (method === 'GET' && normalizedRoute === '/public/servers') {
      return await handlePublicServers(supabase, req);
    }

    // ── GET /geoip — GeoIP lookup (proxies to geo-api service) ──────────
    if (method === 'GET' && normalizedRoute === '/geoip') {
      return await handleGeoIp(req, clientIp);
    }

    // ── GET /bpb-sub — one random BPB subscription, parsed server-side ──
    if (method === 'GET' && normalizedRoute === '/bpb-sub') {
      return await handleBpbSub(supabase, req);
    }

    // ── GET / or /health — Health check ─────────────────────────────────
    if (method === 'GET' && (normalizedRoute === '/' || normalizedRoute === '/health')) {
      return await handleHealth(supabase, req);
    }

    // ── POST /quota/sync — anonymous device time-quota ledger (no JWT;
    //    the app has no accounts — deviceId is a random install UUID) ────
    if (method === 'POST' && normalizedRoute === '/quota/sync') {
      return await handleQuotaSync(supabase, req, clientIp);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ONLY POST methods beyond this point (all authenticated endpoints)
    // ═══════════════════════════════════════════════════════════════════════

    if (method !== 'POST') {
      return jsonResponse({ error: 'Not found' }, 404, getCorsHeaders(req));
    }

    // Validate Content-Type for POST
    const contentType = req.headers.get('Content-Type');
    if (!contentType || !contentType.includes('application/json')) {
      return jsonResponse(
        { error: 'Bad request', details: ['Content-Type must be application/json'] },
        400,
        getCorsHeaders(req),
      );
    }

    // Authenticate
    const user = await authenticate(req, supabaseUrl, supabaseKey);
    if (!user) {
      return jsonResponse(
        { error: 'Missing or invalid authorization header' },
        401,
        getCorsHeaders(req),
      );
    }

    // Anti-replay validation (timestamp + request ID dedup)
    const replayErr = await validateAntiReplay(req, supabase);
    if (replayErr) return replayErr;

    // User rate limiting (30 req/min per user)
    const { allowed: userAllowed } = await checkIpRateLimit(
      supabase,
      `user:${user.id}`,
      30,
      1,
    );
    if (!userAllowed) {
      return jsonResponse(
        { error: 'Too many requests. Please slow down.' },
        429,
        getCorsHeaders(req),
      );
    }

    // ── Route to authenticated handlers ──────────────────────────────────
    if (normalizedRoute === '/servers') {
      return await handleServers(supabase, req);
    }
    if (normalizedRoute === '/version') {
      return await handleVersion(supabase, req);
    }
    if (normalizedRoute === '/register-device') {
      return await handleRegisterDevice(supabase, req, user);
    }
    if (normalizedRoute === '/unregister-device') {
      return await handleUnregisterDevice(supabase, req, user);
    }
    if (normalizedRoute === '/send-notification') {
      // Anti-replay for admin endpoint
      const replayErr = await validateAntiReplay(req, supabase);
      if (replayErr) return replayErr;
      return await handleSendNotification(supabase, req);
    }
    if (normalizedRoute === '/import-vless') {
      // Anti-replay for admin endpoint
      const replayErr = await validateAntiReplay(req, supabase);
      if (replayErr) return replayErr;
      return await handleImportVless(supabase, req);
    }
    // ── 404 ──────────────────────────────────────────────────────────────
    return jsonResponse({ error: 'Not found' }, 404, getCorsHeaders(req));
  } catch (e) {
    console.error('[rootnet-api] Unhandled error:', (e as Error).message);
    return jsonResponse(
      { error: 'Internal server error' },
      500,
      getCorsHeaders(req),
    );
  }
});

// ═══════════════════════════════════════════════════════════════════════════════
// 📋  GET /public/servers — Public server list
// ═══════════════════════════════════════════════════════════════════════════════

async function handlePublicServers(supabase: any, req: Request): Promise<Response> {
  const cors = getCorsHeaders(req);
  try {
    const { data: servers, error } = await supabase
      .from('servers')
      .select('name, flag, country')
      .eq('is_active', true);

    if (error) throw error;
    return jsonResponse({ servers: servers || [] }, 200, cors);
  } catch (e) {
    console.error('[handlePublicServers] Error:', (e as Error).message);
    return jsonResponse({ error: 'Failed to fetch servers' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📡  POST /servers — Full server list (JWT required)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleServers(
  supabase: any,
  req: Request,
): Promise<Response> {
  const cors = getCorsHeaders(req);

  try {
    const { data: servers, error } = await supabase
      .from('servers')
      .select('name, flag, country, config, type, config_format')
      .eq('is_active', true);

    if (error) throw error;
    return jsonResponse({ servers: servers || [] }, 200, cors);
  } catch (e) {
    console.error('[handleServers] Error:', (e as Error).message);
    return jsonResponse({ error: 'Failed to fetch servers' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📱  POST /version — Version check (JWT required)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleVersion(supabase: any, req: Request): Promise<Response> {
  const cors = getCorsHeaders(req);

  const DEFAULT_CONFIG = {
    latestVersion: '2.0.0',
    latestBuild: 101,
    minimumVersion: '1.0.0',
    updateUrl: 'https://chobgroup.pages.dev',
    releaseNotes:
      '• v2.0 — RootNet is now a config launcher\n' +
      '• No built-in VPN engine — copy configs or open them in your own client\n' +
      '• Picture ads before copy, short video before export\n' +
      '• No account needed',
    forceUpdate: false,
  };

  try {
    const { data, error } = await supabase
      .from('app_config')
      .select('latest_version, latest_build, minimum_version, update_url, release_notes, force_update')
      .eq('id', 1)
      .single();

    if (error || !data) {
      return jsonResponse(DEFAULT_CONFIG, 200, cors);
    }

    return jsonResponse(
      {
        latestVersion: data.latest_version,
        latestBuild: data.latest_build,
        minimumVersion: data.minimum_version,
        updateUrl: data.update_url,
        releaseNotes: data.release_notes,
        forceUpdate: data.force_update,
      },
      200,
      cors,
    );
  } catch (e) {
    console.error('[handleVersion] Error:', (e as Error).message);
    return jsonResponse(DEFAULT_CONFIG, 200, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📲  POST /register-device — Register FCM token (JWT required)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleRegisterDevice(
  supabase: any,
  req: Request,
  user: { id: string },
): Promise<Response> {
  const cors = getCorsHeaders(req);

  try {
    const body = (await req.json()) as { token?: string; platform?: string };
    const token = body?.token;
    const platform = body?.platform || 'android';

    if (!token || typeof token !== 'string' || token.length < 10) {
      return jsonResponse({ error: 'Invalid or missing token' }, 400, cors);
    }

    // Upsert: insert or update the token for this user
    const { error } = await supabase
      .from('device_tokens')
      .upsert(
        { user_id: user.id, token, platform },
        { onConflict: 'user_id,token', ignoreDuplicates: false },
      );

    if (error) {
      console.error('[registerDevice] Upsert failed:', error.message);
      return jsonResponse({ error: 'Failed to register device' }, 500, cors);
    }

    console.log(`[registerDevice] Token registered: user=${user.id} platform=${platform}`);
    return jsonResponse({ success: true }, 200, cors);
  } catch (e) {
    console.error('[registerDevice] Error:', (e as Error).message);
    return jsonResponse({ error: 'Internal server error' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📲  POST /unregister-device — Remove FCM token (JWT required)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleUnregisterDevice(
  supabase: any,
  req: Request,
  user: { id: string },
): Promise<Response> {
  const cors = getCorsHeaders(req);

  try {
    const body = (await req.json()) as { token?: string };
    const token = body?.token;

    if (!token || typeof token !== 'string') {
      return jsonResponse({ error: 'Invalid or missing token' }, 400, cors);
    }

    const { error } = await supabase
      .from('device_tokens')
      .delete()
      .eq('user_id', user.id)
      .eq('token', token);

    if (error) {
      console.error('[unregisterDevice] Delete failed:', error.message);
    }

    return jsonResponse({ success: true }, 200, cors);
  } catch (e) {
    console.error('[unregisterDevice] Error:', (e as Error).message);
    return jsonResponse({ error: 'Internal server error' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 📲  POST /send-notification — Send push notification (admin key required)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleSendNotification(
  supabase: any,
  req: Request,
): Promise<Response> {
  const cors = getCorsHeaders(req);

  // Admin-only endpoint
  const adminKey = Deno.env.get('ADMIN_KEY');
  const requestAdminKey = req.headers.get('X-Admin-Key');
  if (!adminKey || requestAdminKey !== adminKey) {
    return jsonResponse(
      { error: 'Unauthorized — valid X-Admin-Key required' },
      401,
      cors,
    );
  }

  const fcmServiceAccount = Deno.env.get('FCM_SERVICE_ACCOUNT');
  if (!fcmServiceAccount) {
    return jsonResponse({ error: 'FCM not configured' }, 500, cors);
  }

  try {
    const body = (await req.json()) as {
      userId?: string;
      title?: string;
      message?: string;
      data?: Record<string, string>;
    };
    const { userId, title, message, data } = body;

    if (!userId || !title || !message) {
      return jsonResponse(
        { error: 'Missing required fields: userId, title, message' },
        400,
        cors,
      );
    }

    const result = await sendPushNotification(
      fcmServiceAccount,
      supabase,
      userId,
      title,
      message,
      data,
    );

    return jsonResponse(result, 200, cors);
  } catch (e) {
    const errMsg = (e as Error).message;
    if (errMsg === 'No registered devices') {
      return jsonResponse({ error: 'No registered devices' }, 404, cors);
    }
    console.error('[sendNotification] Error:', errMsg);
    return jsonResponse({ error: 'Internal server error' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 🔗  POST /import-vless — Promote scraped VLESS links into the servers table
// ═══════════════════════════════════════════════════════════════════════════════
// Pipeline: Telegram → scraper → vless-worker → vless_links → THIS endpoint → servers
//
// The import logic itself lives in the DATABASE (migration 20260803000002:
// `import_pending_vless_links` RPC), so this endpoint and the pg_cron
// scheduled job share ONE implementation. This endpoint just provides a
// manual trigger for ops/admin (protected by X-Admin-Key, same as
// /send-notification).
//
// Optional body: { "limit": 100 } (default 200, max 500)

async function handleImportVless(
  supabase: any,
  req: Request,
): Promise<Response> {
  const cors = getCorsHeaders(req);

  // Admin-only endpoint — same pattern as /send-notification
  const adminKey = Deno.env.get('ADMIN_KEY');
  const requestAdminKey = req.headers.get('X-Admin-Key');
  if (!adminKey || requestAdminKey !== adminKey) {
    return jsonResponse(
      { error: 'Unauthorized — valid X-Admin-Key required' },
      401,
      cors,
    );
  }

  try {
    let limit = 200;
    try {
      const body = (await req.json()) as { limit?: number };
      if (typeof body?.limit === 'number' && body.limit > 0) {
        limit = Math.min(Math.floor(body.limit), 500);
      }
    } catch {
      // No body — use default limit
    }

    // Delegate to the DB function (shared with the pg_cron job)
    const { data, error } = await supabase.rpc('import_pending_vless_links', {
      p_max_links: limit,
    });

    if (error) throw error;

    console.log('[importVless] RPC result:', JSON.stringify(data));
    return jsonResponse((data as Record<string, unknown>) ?? {}, 200, cors);
  } catch (e) {
    console.error('[importVless] Error:', (e as Error).message);
    return jsonResponse({ error: 'Internal server error' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 🛰  GET /bpb-sub — one random ACTIVE BPB subscription, parsed server-side
// ═══════════════════════════════════════════════════════════════════════════════
// Keeps the subscription URLs out of the APK: the client never sees them.
// Returns { servers: [{name, flag, country, rawConfig}], source: "<label>" }
// or { servers: [] } when no sub is configured/reachable (client falls back
// to its Supabase list).

const VLESS_RE = /vless:\/\/[^\s"'<>]+/g;

function decodeSubBody(raw: string): string {
  const trimmed = raw.trim();
  if (trimmed.includes('vless://')) return trimmed;
  try {
    // Standard V2Ray subs ship base64-encoded link lists.
    const binary = atob(trimmed.replace(/\s+/g, ''));
    return decodeURIComponent(
      Array.from(binary)
        .map((ch) => '%' + ('00' + ch.charCodeAt(0).toString(16)).slice(-2))
        .join(''),
    );
  } catch {
    return trimmed;
  }
}

async function handleBpbSub(supabase: any, req: Request): Promise<Response> {
  const cors = getCorsHeaders(req);
  try {
    const { data: subs, error } = await supabase
      .from('bpb_services')
      .select('label, flag, country, sub_url')
      .eq('is_active', true);

    if (error) throw error;
    if (!subs || subs.length === 0) {
      return jsonResponse({ servers: [], source: null }, 200, cors);
    }

    const picked = subs[Math.floor(Math.random() * subs.length)] as {
      label: string;
      flag: string;
      country: string;
      sub_url: string;
    };

    let body = '';
    try {
      const resp = await fetch(picked.sub_url, { signal: AbortSignal.timeout(10_000) });
      if (!resp.ok) throw new Error(`sub HTTP ${resp.status}`);
      body = await resp.text();
    } catch (e) {
      console.error('[bpbSub] fetch failed for', picked.label, (e as Error).message);
      return jsonResponse({ servers: [], source: picked.label, error: 'sub unreachable' }, 200, cors);
    }

    const decoded = decodeSubBody(body);
    const links = [...new Set(decoded.match(VLESS_RE) ?? [])]
      .map((l) => l.trim().replace(/[.,;:]+$/, ''));

    const servers = links.map((link, i) => {
      let name = '';
      try {
        name = decodeURIComponent(link.split('#')[1] ?? '').split('|')[0].split(' ')[0];
      } catch {
        name = '';
      }
      if (!/[a-zA-Z0-9]/.test(name)) name = `BPB-${i + 1}`;
      return {
        name: `${name} · ${picked.label}`.slice(0, 48),
        flag: picked.flag,
        country: picked.country,
        rawConfig: link,
      };
    });

    return jsonResponse({ servers, source: picked.label }, 200, cors);
  } catch (e) {
    console.error('[bpbSub] Error:', (e as Error).message);
    return jsonResponse({ servers: [], source: null }, 200, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ⏱  POST /quota/sync — anonymous device time-quota ledger
// ═══════════════════════════════════════════════════════════════════════════════
// Body: { deviceId, remainingSeconds?, watchAd? }
//   - watchAd=true  → +1800 s capped so remaining ≤ 3600 (server-verified grant)
//   - heartbeat     → server takes min(server, local): burn-down syncs down,
//                     a fresh reinstall (local=0 while disconnected) can't
//                     zero the clock because clients only heartbeat in-session.
// Response: { remainingSeconds, totalGrantedSeconds }

async function handleQuotaSync(supabase: any, req: Request, clientIp: string): Promise<Response> {
  const cors = getCorsHeaders(req);

  let body: { deviceId?: string; remainingSeconds?: number; watchAd?: boolean };
  try {
    body = (await req.json()) as typeof body;
  } catch {
    return jsonResponse({ error: 'Bad request' }, 400, cors);
  }

  const deviceId = body?.deviceId;
  if (!deviceId || typeof deviceId !== 'string' || deviceId.length < 8 || deviceId.length > 64) {
    return jsonResponse({ error: 'Invalid deviceId' }, 400, cors);
  }

  const GRANT = 1800;        // 30 min per rewarded video
  const CAP = 3600;          // 60 min hard cap

  try {
    const { data: row } = await supabase
      .from('device_quota')
      .select('remaining_seconds, total_granted_seconds')
      .eq('device_id', deviceId)
      .maybeSingle();

    let serverRemaining = row ? Number(row.remaining_seconds) : 0;
    let totalGranted = row ? Number(row.total_granted_seconds) : 0;

    if (body.watchAd === true) {
      // Server-verified grant — mirrors the client rule exactly.
      if (serverRemaining < GRANT) {
        const added = Math.min(GRANT, CAP - serverRemaining);
        serverRemaining += added;
        totalGranted += added;
      }
    } else if (typeof body.remainingSeconds === 'number' && body.remainingSeconds >= 0) {
      // In-session burn-down only.
      const local = Math.floor(body.remainingSeconds);
      if (local <= 3600) serverRemaining = Math.min(serverRemaining, local);
    }

    await supabase.from('device_quota').upsert({
      device_id: deviceId,
      remaining_seconds: serverRemaining,
      total_granted_seconds: totalGranted,
      updated_at: new Date().toISOString(),
    }, { onConflict: 'device_id' });

    return jsonResponse(
      { remainingSeconds: serverRemaining, totalGrantedSeconds: totalGranted },
      200,
      cors,
    );
  } catch (e) {
    console.error('[quotaSync] Error:', (e as Error).message);
    return jsonResponse({ error: 'Internal server error' }, 500, cors);
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 🌍  GET /geoip — GeoIP lookup (proxied to external GeoIP service)
// ═══════════════════════════════════════════════════════════════════════════════

async function handleGeoIp(req: Request, clientIp: string): Promise<Response> {
  const cors = getCorsHeaders(req);
  const url = new URL(req.url);
  const targetIp = url.searchParams.get('ip') || clientIp;

  // GeoIP_SERVICE_URL can be overridden via env var or defaults to Supabase geo-api
  const geoIpServiceUrl =
    Deno.env.get('GEOIP_SERVICE_URL') ||
    `${Deno.env.get('SUPABASE_URL')}/functions/v1/geo-api`;

  try {
    const resp = await fetch(
      `${geoIpServiceUrl}?ip=${encodeURIComponent(targetIp)}`,
      { signal: AbortSignal.timeout(3_000) },
    );

    if (!resp.ok) {
      return jsonResponse(
        { country: 'Unknown', country_code: 'XX', flag: '' },
        200,
        cors,
      );
    }

    const data = (await resp.json()) as Record<string, unknown>;
    // Support both old format (country_code, flag) and new format (countryCode)
    return jsonResponse(
      {
        country: data.country || 'Unknown',
        country_code: data.country_code || (data.countryCode as string) || 'XX',
        flag: data.flag || '',
      },
      200,
      cors,
    );
  } catch (_) {
    return jsonResponse(
      { country: 'Unknown', country_code: 'XX', flag: '' },
      200,
      cors,
    );
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// 🩺  GET / or /health — Health check
// ═══════════════════════════════════════════════════════════════════════════════

async function handleHealth(supabase: any, req: Request): Promise<Response> {
  const cors = getCorsHeaders(req);

  try {
    const { data } = await supabase
      .from('app_config')
      .select('latest_version')
      .eq('id', 1)
      .single();

    return jsonResponse(
      {
        status: 'ok',
        service: 'RootNet VPN API',
        version: data?.latest_version || '2.0.0',
        docs:    'https://chobgroup.pages.dev',
      },
      200,
      cors,
    );
  } catch (_) {
    return jsonResponse(
      {
        status: 'ok',
        service: 'RootNet VPN API',
        version: '2.0.0',
        docs:    'https://chobgroup.pages.dev',
      },
      200,
      cors,
    );
  }
}
