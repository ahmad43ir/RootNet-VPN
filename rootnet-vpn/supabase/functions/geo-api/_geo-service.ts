// ============================================================
// 📁 _geo-service.ts — GEOIP SERVICE ORCHESTRATOR
// ============================================================
// The core orchestration layer that:
//   1. Checks in-memory cache first (ultra-fast path)
//   2. Tries Supabase provider (primary) with circuit breaker
//   3. Falls back to Cloud API provider (secondary) if Supabase fails
//   4. Caches results in-memory AND in Supabase
//   5. Logs all failures, fallbacks, and circuit breaker events
//
// IMPORTANT: This module contains ZERO RootNet logic.
//            It ONLY does IP→country lookups.
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';

import { log, requireEnv } from './_utils.ts';
import { cacheGet, cacheSet } from './_cache.ts';
import { recordSuccess, recordFailure, isCircuitOpen, getCircuitState } from './_circuit-breaker.ts';
import { withRetry } from './_retry.ts';
import { lookupViaSupabase, cacheInSupabase } from './_providers/_supabase.ts';
import { lookupViaCloudApi, countryCodeToFlag } from './_providers/_cloud-api.ts';
import type { GeoIpResult, RetryConfig } from './_types.ts';

// ─── Retry Config for Cloud API ───────────────────────────────────────────────

const CLOUD_RETRY: RetryConfig = {
  maxRetries: 2,
  baseDelayMs: 300,
};

// ─── Service Initialization ───────────────────────────────────────────────────

export interface GeoService {
  lookup(ip: string): Promise<GeoIpResult>;
}

/**
 * Initialize the GeoIP service with its dependencies.
 * Call once at module load or on first request.
 */
export function createGeoService(): GeoService {
  const supabaseUrl = requireEnv('SUPABASE_URL');
  const supabaseKey = requireEnv('SUPABASE_SERVICE_ROLE_KEY');
  const supabase = createClient(supabaseUrl, supabaseKey);

  const deps = { supabase };

  return {
    lookup: async (ip: string): Promise<GeoIpResult> => {
      const startTime = Date.now();

      // ────────────────────────────────────────────────────────────────────
      // STEP 1: Check in-memory cache (fastest path, ~0ms)
      // ────────────────────────────────────────────────────────────────────
      const cached = cacheGet(ip);
      if (cached) {
        const elapsed = Date.now() - startTime;
        log('info', 'geo-service', `In-memory cache HIT: ${ip} → ${cached.country} (${elapsed}ms)`);
        return cached;
      }

      // ────────────────────────────────────────────────────────────────────
      // STEP 2: Try Supabase (primary provider) — unless circuit is open
      // ────────────────────────────────────────────────────────────────────
      const circuit = isCircuitOpen();
      if (circuit) {
        const circuitState = getCircuitState();
        log('warn', 'geo-service',
          `Circuit is OPEN — skipping Supabase for ${ip}. ` +
          `Remaining cooldown: ${Math.ceil(circuitState.remainingCooldownMs / 1000)}s`);
      }

      let supabaseResult: GeoIpResult | null = null;

      if (!circuit) {
        log('info', 'geo-service', `Querying Supabase for ${ip}...`);
        const result = await lookupViaSupabase(ip, deps);

        if (result.data !== null) {
          // Supabase cache HIT
          recordSuccess();
          supabaseResult = result.data;

          // Also store in in-memory cache
          cacheSet(ip, supabaseResult);

          const elapsed = Date.now() - startTime;
          log('info', 'geo-service',
            `Supabase → ${supabaseResult.country} (${elapsed}ms)`);
          return supabaseResult;
        }

        // Supabase failed — record failure (may trigger circuit breaker)
        recordFailure();

        if (result.status === 429) {
          log('warn', 'geo-service',
            `Supabase rate limited (429) — routing ${ip} to Cloud API fallback`);
        } else {
          log('warn', 'geo-service',
            `Supabase failed for ${ip} — routing to Cloud API fallback`);
        }
      }

      // ────────────────────────────────────────────────────────────────────
      // STEP 3: Cloud API (secondary/fallback provider) with retry
      // ────────────────────────────────────────────────────────────────────
      log('info', 'geo-service', `Querying Cloud API for ${ip}...`);

      const cloudResult = await withRetry(
        () => lookupViaCloudApi(ip),
        `cloud-api(${ip})`,
        CLOUD_RETRY,
      );

      if (cloudResult.data !== null) {
        const result = cloudResult.data;

        // Store in-memory cache
        cacheSet(ip, result);

        // Store in Supabase cache (fire-and-forget, non-critical)
        const flag = countryCodeToFlag(result.countryCode);
        cacheInSupabase(ip, result.country, result.countryCode, flag, deps);

        const elapsed = Date.now() - startTime;
        log('info', 'geo-service',
          `Cloud API → ${result.country} (${elapsed}ms) — ` +
          `FALLBACK USED${circuit ? ' (circuit breaker active)' : ' (Supabase failed)'}`);

        // Log fallback usage prominently for monitoring
        console.log(`[geo-api] ⚠️ FALLBACK: ${ip} resolved via Cloud API (provider=cloud-api)`);

        return result;
      }

      // ────────────────────────────────────────────────────────────────────
      // STEP 4: All providers failed — return "Unknown"
      // ────────────────────────────────────────────────────────────────────
      const elapsed = Date.now() - startTime;
      log('error', 'geo-service',
        `ALL PROVIDERS FAILED for ${ip} after ${elapsed}ms`);

      // Cache the Unknown result briefly to avoid hammering on repeated failures
      const unknown: GeoIpResult = { ip, country: 'Unknown', countryCode: 'XX' };
      cacheSet(ip, unknown);

      return unknown;
    },
  };
}
