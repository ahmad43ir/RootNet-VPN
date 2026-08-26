// ============================================================
// 📁 _providers/_cloud-api.ts — CLOUD API FALLBACK PROVIDER
// ============================================================
// Secondary (fallback) data source for GeoIP lookups.
// Used when Supabase cache misses OR circuit breaker is open.
//
// Provider: ip-api.com (free tier, no API key, 45 req/min)
//
// Features:
//   - 5-second timeout
//   - External HTTP fetch
//   - Flag emoji generation from country code
// ============================================================

import { log } from '../_utils.ts';
import type { GeoIpResult, IpApiResponse } from '../_types.ts';

// ─── Constants ────────────────────────────────────────────────────────────────

const API_BASE = 'http://ip-api.com/json';
const API_FIELDS = 'status,country,countryCode,query';
const CLOUD_TIMEOUT_MS = 5_000;

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Convert a 2-letter ISO country code to a flag emoji.
 * Example: 'US' → '🇺🇸'
 */
function countryCodeToFlag(code: string): string {
  const base = 0x1F1E6;
  return String.fromCodePoint(
    ...code
      .toUpperCase()
      .split('')
      .map((ch) => base + ch.charCodeAt(0) - 65),
  );
}

// ─── Lookup ───────────────────────────────────────────────────────────────────

/**
 * Look up an IP address using ip-api.com.
 *
 * @returns GeoIpResult on success, null on any failure
 */
export async function lookupViaCloudApi(
  ip: string,
): Promise<{ data: GeoIpResult | null; status?: number }> {
  const startTime = Date.now();

  try {
    const url = `${API_BASE}/${ip}?fields=${API_FIELDS}`;
    const response = await fetch(url, {
      signal: AbortSignal.timeout(CLOUD_TIMEOUT_MS),
    });

    const elapsed = Date.now() - startTime;

    // Treat ANY non-200 as failure (including 429, 5xx, blocked responses)
    if (!response.ok) {
      log('error', 'cloud-api-provider',
        `HTTP ${response.status} for ${ip} after ${elapsed}ms`);

      // Try to read body for more context
      const body = await response.text().catch(() => '');
      if (body) log('error', 'cloud-api-provider', `Response body: ${body.slice(0, 200)}`);

      return { data: null, status: response.status };
    }

    const geoData = (await response.json()) as IpApiResponse;

    if (geoData.status !== 'success') {
      log('error', 'cloud-api-provider',
        `Lookup failed for ${ip} after ${elapsed}ms: status=${geoData.status}`);
      return { data: null, status: 404 };
    }

    const flag = countryCodeToFlag(geoData.countryCode);

    log('info', 'cloud-api-provider',
      `Resolved ${ip} → ${geoData.country} (${elapsed}ms)`);

    // Also log the flag as a fun stat
    console.log(`[geo-api] Flag for ${ip}: ${flag} ${geoData.country}`);

    return {
      data: {
        ip: geoData.query,
        country: geoData.country,
        countryCode: geoData.countryCode,
      },
    };
  } catch (err) {
    const elapsed = Date.now() - startTime;
    const message = err instanceof Error ? err.message : String(err);

    if (message.includes('timeout') || message.includes('Timedout') || message.includes('abort')) {
      log('error', 'cloud-api-provider',
        `TIMEOUT for ${ip} after ${elapsed}ms`);
    } else {
      log('error', 'cloud-api-provider',
        `Error for ${ip} after ${elapsed}ms: ${message}`);
    }

    return { data: null, status: 502 };
  }
}

/**
 * Generate a flag emoji for a country code.
 * Exported for use by the service layer when caching in Supabase.
 */
export { countryCodeToFlag };
