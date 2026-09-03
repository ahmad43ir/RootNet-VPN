// ============================================================
// 📁 index.ts — GEOIP SERVICE ENTRY POINT
// ============================================================
// Thin HTTP handler. ALL GeoIP logic lives in the service layer.
//
// This file ONLY:
//   - Parses HTTP requests
//   - Calls GeoService.lookup()
//   - Returns normalized responses
//
// Zero GeoIP logic here. Zero RootNet logic here.
// ============================================================
// Deploy: supabase functions deploy geo-api --no-verify-jwt
//
// Environment:
//   SUPABASE_URL              — Required (for Supabase provider)
//   SUPABASE_SERVICE_ROLE_KEY — Required (for Supabase provider)
// ============================================================

import { corsPreflight, jsonResponse, isValidIpv4, getClientIp, log, requireEnv } from './_utils.ts';
import { createGeoService } from './_geo-service.ts';
import { checkIpRateLimit } from '../_shared/rate-limit.ts';
import { createClient } from 'jsr:@supabase/supabase-js@2';
import type { GeoIpResult } from './_types.ts';

// ─── Validate Environment at Module Load ─────────────────────────────────────
// Fail fast at startup, not on first request.
const SUPABASE_URL = requireEnv('SUPABASE_URL');
const SUPABASE_KEY = requireEnv('SUPABASE_SERVICE_ROLE_KEY');
const supabaseClient = createClient(SUPABASE_URL, SUPABASE_KEY);
log('info', 'entry', 'Environment validated — Supabase client ready');

// ─── Initialize Service (once at module load) ────────────────────────────────
let service: ReturnType<typeof createGeoService> | null = null;

function getService(): ReturnType<typeof createGeoService> {
  if (!service) {
    service = createGeoService();
    log('info', 'entry', 'GeoIP service initialized');
  }
  return service;
}

// ─── Normalize Response ──────────────────────────────────────────────────────
// STRICT response format: { ip, country, countryCode }
// Consistent REGARDLESS of which provider served the request.

function normalizeResponse(result: GeoIpResult): Record<string, unknown> {
  return {
    ip: result.ip,
    country: result.country,
    countryCode: result.countryCode,
  };
}

// ─── HTTP Handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  // ── CORS preflight ──────────────────────────────────────────────────────
  if (req.method === 'OPTIONS') {
    return corsPreflight();
  }

  if (req.method !== 'GET') {
    return jsonResponse({ error: 'Method not allowed' }, 405);
  }

  const url = new URL(req.url);
  const startTime = Date.now();

  // ── Health check ────────────────────────────────────────────────────────
  if (url.pathname.endsWith('/health')) {
    return jsonResponse({
      status: 'ok',
      service: 'GeoIP Service',
      version: '2.0.0',
    });
  }

  // ── Return 404 for unknown paths (not / and not /health) ───────────────
  if (url.pathname !== '/' && !url.pathname.endsWith('/geo-api')) {
    return jsonResponse({ error: 'Not found' }, 404);
  }

  // ── Validate query parameter ────────────────────────────────────────────
  // Omitting `ip` (or passing ip=self) looks up the CALLER's own IP —
  // used by the apps to detect the device region (e.g. Iran redirect).
  const requestedIp = url.searchParams.get('ip');
  const selfLookup = !requestedIp || requestedIp === 'self' || requestedIp === 'me';
  const targetIp = selfLookup ? getClientIp(req) : requestedIp;

  if (!targetIp || targetIp === 'unknown') {
    return jsonResponse({ error: 'Missing "ip" query parameter' }, 400);
  }

  if (!isValidIpv4(targetIp)) {
    return jsonResponse({ error: 'Invalid IP address format' }, 400);
  }

  // ── Rate limit (protect the service itself) ─────────────────────────────
  const clientIp = getClientIp(req);
  const { allowed: rateAllowed } = await checkIpRateLimit(supabaseClient, clientIp);
  if (!rateAllowed) {
    return jsonResponse({
      error: 'rate_limited',
      ip: targetIp,
      country: 'Unknown',
      countryCode: 'XX',
    }, 429);
  }

  // ── Lookup ──────────────────────────────────────────────────────────────
  try {
    const geoService = getService();
    const result = await geoService.lookup(targetIp);

    const elapsed = Date.now() - startTime;
    log('info', 'entry', `Response: ${result.ip} → ${result.country} (${elapsed}ms)`);

    return jsonResponse(normalizeResponse(result));
  } catch (err) {
    const elapsed = Date.now() - startTime;
    const message = err instanceof Error ? err.message : String(err);
    log('error', 'entry', `Unhandled error for ${targetIp} after ${elapsed}ms: ${message}`);

    // NEVER return a raw provider response — always normalize
    return jsonResponse({
      ip: targetIp,
      country: 'Unknown',
      countryCode: 'XX',
    });
  }
});
