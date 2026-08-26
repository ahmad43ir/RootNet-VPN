// ============================================================
// 📁 _providers/_supabase.ts — SUPABASE PRIMARY PROVIDER
// ============================================================
// Primary data source for GeoIP lookups.
// Reads from the geoip_cache Postgres table.
//
// Data flow:
//   1. Look up IP in geoip_cache table
//   2. If found and fresh (≤24h) → return cached result
//   3. If not found or stale → return null (triggers Cloud API fallback)
//      The Cloud API provider will then cache the result via set_cached_geoip
// ============================================================

import { log } from '../_utils.ts';
import type { GeoIpResult } from '../_types.ts';

// ─── Timeout ──────────────────────────────────────────────────────────────────
// Aggressive: 2 seconds max. We do NOT allow slow Supabase queries
// to block the system — if it's slow, we fall back to Cloud API.

const SUPABASE_TIMEOUT_MS = 2_000;

// ─── Lookup ───────────────────────────────────────────────────────────────────

export interface SupabaseProviderDeps {
  supabase: any; // Supabase client (service_role)
}

/**
 * Look up an IP address using the Supabase geoip_cache table.
 *
 * @returns GeoIpResult if found in cache, null otherwise
 */
export async function lookupViaSupabase(
  ip: string,
  deps: SupabaseProviderDeps,
): Promise<{ data: GeoIpResult | null; status?: number }> {
  const startTime = Date.now();

  try {
    // Query the geoip_cache table with a timeout signal
    const { data, error } = await deps.supabase
      .rpc('get_cached_geoip', { p_ip_address: ip })
      .abortSignal(AbortSignal.timeout(SUPABASE_TIMEOUT_MS));

    const elapsed = Date.now() - startTime;

    if (error) {
      log('error', 'supabase-provider',
        `RPC failed for ${ip} after ${elapsed}ms: ${error.message}`);
      return { data: null, status: 502 };
    }

    if (!data) {
      log('info', 'supabase-provider',
        `Cache MISS for ${ip} after ${elapsed}ms`);
      return { data: null };
    }

    // Normalize the cached response
    const result: GeoIpResult = {
      ip,
      country: data.country || 'Unknown',
      countryCode: data.code || 'XX',
    };

    log('info', 'supabase-provider',
      `Cache HIT for ${ip} → ${result.country} (${elapsed}ms)`);
    return { data: result };
  } catch (err) {
    const elapsed = Date.now() - startTime;
    const message = err instanceof Error ? err.message : String(err);

    // Detect timeout specifically
    if (message.includes('timeout') || message.includes('Timedout') || message.includes('abort')) {
      log('error', 'supabase-provider',
        `TIMEOUT for ${ip} after ${elapsed}ms — falling back to Cloud API`);
    } else {
      log('error', 'supabase-provider',
        `Error for ${ip} after ${elapsed}ms: ${message}`);
    }

    return { data: null, status: 502 };
  }
}

/**
 * Store a GeoIP result in the Supabase cache.
 * Fire-and-forget — failures are non-critical.
 */
export async function cacheInSupabase(
  ip: string,
  countryName: string,
  countryCode: string,
  flagEmoji: string,
  deps: SupabaseProviderDeps,
): Promise<void> {
  try {
    await deps.supabase
      .rpc('set_cached_geoip', {
        p_ip_address: ip,
        p_country_name: countryName,
        p_country_code: countryCode,
        p_flag_emoji: flagEmoji,
      })
      .abortSignal(AbortSignal.timeout(2_000));
  } catch (err) {
    // Non-critical — log and swallow
    log('warn', 'supabase-provider',
      `Cache write failed for ${ip}: ${err instanceof Error ? err.message : String(err)}`);
  }
}
