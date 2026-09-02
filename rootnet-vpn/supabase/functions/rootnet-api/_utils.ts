// ============================================================
// 📁 _utils.ts — SHARED UTILITIES FOR ROOTNET API
// ============================================================
// Shared helpers: CORS headers, JSON response builder,
// IP extraction from request headers.
//
// No Cloudflare-specific headers are used.
// ============================================================

// ─── Allowed Origins ──────────────────────────────────────────────────────────

const ALLOWED_ORIGINS = [
  'https://chobgroup.pages.dev',
  'capacitor://localhost',
  'http://localhost',
  'http://localhost:3000',
  'http://localhost:8080',
];

// ─── CORS Headers Builder ─────────────────────────────────────────────────────

export function getCorsHeaders(req: Request): Record<string, string> {
  const origin = req.headers.get('Origin') || '';
  const allowOrigin =
    !origin || ALLOWED_ORIGINS.some((o) => origin.startsWith(o))
      ? origin || '*'
      : 'null';

  return {
    'Access-Control-Allow-Origin': allowOrigin,
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers':
      'Content-Type, Authorization, X-Request-Timestamp, X-Request-Id, X-Admin-Key',
    'Access-Control-Max-Age': '86400',
    'Vary': 'Origin',
  };
}

// ─── JSON Response Builder ────────────────────────────────────────────────────

export function jsonResponse(
  data: Record<string, unknown>,
  status = 200,
  cors: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json',
      ...cors,
    },
  });
}

// ─── CORS Preflight Response ──────────────────────────────────────────────────

export function corsPreflight(req: Request): Response {
  const cors = getCorsHeaders(req);
  return new Response(null, {
    status: 204,
    headers: {
      ...cors,
      'Access-Control-Max-Age': '86400',
    },
  });
}

// ─── Client IP Extraction ─────────────────────────────────────────────────────
// 🔒 We ONLY trust x-forwarded-for (set by Supabase gateway).
//     We NEVER use Cloudflare-specific headers (CF-Connecting-IP).
//     Rightmost IP is the original client (gateway appends left-to-right).
//     This is strictly for rate limiting — NOT for GeoIP/location.

export function getClientIp(req: Request): string {
  const forwarded = req.headers.get('x-forwarded-for');
  if (forwarded) {
    // Rightmost = original client (gateway appends left-to-right)
    const ips = forwarded.split(',').map((ip) => ip.trim()).filter(Boolean);
    if (ips.length > 0) return ips[ips.length - 1];
  }
  return 'unknown';
}

// ─── IPv4 Validation ──────────────────────────────────────────────────────────

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

// ─── Anti-Replay Validation ──────────────────────────────────────────────────────
// Validates X-Request-Timestamp (must be within ±30s of server time) and
// deduplicates X-Request-Id via a short-TTL Postgres table.
// Returns null if valid, or a Response (428) if invalid.

export async function validateAntiReplay(
  req: Request,
  supabase: any,
): Promise<Response | null> {
  const timestampHeader = req.headers.get('X-Request-Timestamp');
  const requestIdHeader = req.headers.get('X-Request-Id');

  if (!timestampHeader || !requestIdHeader) {
    return jsonResponse(
      { error: 'Missing X-Request-Timestamp or X-Request-Id header' },
      428,
      getCorsHeaders(req),
    );
  }

  const timestamp = parseInt(timestampHeader, 10);
  if (isNaN(timestamp)) {
    return jsonResponse(
      { error: 'Invalid X-Request-Timestamp' },
      428,
      getCorsHeaders(req),
    );
  }

  const nowSec = Math.floor(Date.now() / 1000);
  const maxSkewSec = 30;
  if (Math.abs(nowSec - timestamp) > maxSkewSec) {
    return jsonResponse(
      { error: 'Request timestamp out of acceptable window' },
      428,
      getCorsHeaders(req),
    );
  }

  try {
    const { error } = await supabase
      .from('request_ids')
      .insert({ request_id: requestIdHeader, created_at: new Date().toISOString() })
      .select()
      .single();
    if (error && error.code !== '23505') {
      console.warn('[anti-replay] request_ids insert failed:', error.message);
    } else if (error && error.code === '23505') {
      return jsonResponse(
        { error: 'Duplicate request ID' },
        428,
        getCorsHeaders(req),
      );
    }
  } catch (e) {
    console.warn('[anti-replay] request_ids check failed:', (e as Error).message);
  }

  return null;
}
