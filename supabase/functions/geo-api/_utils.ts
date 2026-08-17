// ============================================================
// 📁 _utils.ts — SHARED UTILITIES FOR GEOIP SERVICE
// ============================================================

// ─── CORS Headers ─────────────────────────────────────────────────────────────

export const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers':
    'authorization, x-client-info, apikey, content-type, x-forwarded-for',
  'Access-Control-Max-Age': '86400',
};

// ─── Response Builders ────────────────────────────────────────────────────────

export function jsonResponse(data: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
  });
}

export function corsPreflight(): Response {
  return new Response(null, { status: 204, headers: CORS_HEADERS });
}

// ─── IP Validation ────────────────────────────────────────────────────────────

/**
 * Strict IPv4 validation.
 * - Exactly 4 octets
 * - Each octet 0–255
 * - NO leading zeros (e.g. "01" is rejected)
 * - NO empty octets
 */
export function isValidIpv4(str: string): boolean {
  const parts = str.split('.');
  if (parts.length !== 4) return false;
  return parts.every((part) => {
    if (part.length === 0 || part.length > 3) return false;
    if (part.length > 1 && part.startsWith('0')) return false;
    const n = parseInt(part, 10);
    return !isNaN(n) && n >= 0 && n <= 255 && n.toString() === part;
  });
}

// ─── Client IP Extraction ─────────────────────────────────────────────────────

/**
 * Extract the client IP from request headers.
 * 🔒 ONLY trusts x-forwarded-for (set by Supabase gateway).
 *     NEVER trusts Cloudflare-specific headers (cf-connecting-ip, cf-ipcountry).
 */
export function getClientIp(req: Request): string {
  const forwarded = req.headers.get('x-forwarded-for');
  if (forwarded) {
    const firstIp = forwarded.split(',')[0]?.trim();
    if (firstIp && isValidIpv4(firstIp)) return firstIp;
  }
  return 'unknown';
}

// ─── Logging ──────────────────────────────────────────────────────────────────

export function log(level: 'info' | 'warn' | 'error', module: string, message: string, meta?: Record<string, unknown>): void {
  const entry = {
    ts: new Date().toISOString(),
    level,
    module,
    message,
    ...(meta || {}),
  };
  const line = JSON.stringify(entry);
  switch (level) {
    case 'error': console.error(line); break;
    case 'warn':  console.warn(line); break;
    default:      console.log(line); break;
  }
}

// ─── Environment Helpers ──────────────────────────────────────────────────────

export function requireEnv(name: string): string {
  const val = Deno.env.get(name);
  if (!val) throw new Error(`Missing required env var: ${name}`);
  return val;
}
