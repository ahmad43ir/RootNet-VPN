// ============================================================
// 📁 _cache.ts — IN-MEMORY CACHE WITH 1-HOUR TTL
// ============================================================
// Purpose: Cache GeoIP responses per IP to prevent redundant
//          external requests. TTL is 1 hour minimum.
//
// This is an IN-MEMORY cache (module-scoped), layered on TOP
// of the Postgres geoip_cache table. The two caches serve
// different purposes:
//   - In-memory: ultra-low latency (~0ms), no network call
//   - Postgres: persistent across function restarts, shared
// ============================================================

import { log } from './_utils.ts';
import type { GeoIpResult } from './_types.ts';

// ─── Entry Shape ──────────────────────────────────────────────────────────────

interface CacheEntry {
  data: GeoIpResult;
  expiry: number; // timestamp in ms
}

// ─── Configuration ────────────────────────────────────────────────────────────

const DEFAULT_TTL_MS = 3_600_000; // 1 hour
const MAX_ENTRIES = 10_000;       // LRU eviction threshold

// ─── Module-level Cache Store ─────────────────────────────────────────────────

const store = new Map<string, CacheEntry>();
const accessLog: string[] = []; // ordered by access time, most recent last

// ─── API ──────────────────────────────────────────────────────────────────────

/**
 * Get a cached GeoIP result.
 * Returns null if not found or expired.
 */
export function cacheGet(ip: string): GeoIpResult | null {
  const entry = store.get(ip);
  if (!entry) return null;

  // Check expiry
  if (Date.now() > entry.expiry) {
    store.delete(ip);
    log('info', 'cache', `Expired entry purged: ${ip} → ${entry.data.country}`);
    return null;
  }

  // Update access order — move to end (most recently used)
  const idx = accessLog.indexOf(ip);
  if (idx >= 0) accessLog.splice(idx, 1);
  accessLog.push(ip);

  return entry.data;
}

/**
 * Store a GeoIP result in the cache.
 */
export function cacheSet(ip: string, data: GeoIpResult): void {
  // LRU eviction: if at capacity, evict oldest 25%
  if (store.size >= MAX_ENTRIES) {
    const evictCount = Math.ceil(MAX_ENTRIES * 0.25); // 2500 entries
    const toEvict = accessLog.splice(0, evictCount);
    for (const key of toEvict) {
      store.delete(key);
    }
    log('info', 'cache', `LRU eviction: removed ${toEvict.length} oldest entries`);
  }

  // Ensure we're not duplicating the key in accessLog
  const existingIdx = accessLog.indexOf(ip);
  if (existingIdx >= 0) accessLog.splice(existingIdx, 1);

  store.set(ip, {
    data,
    expiry: Date.now() + DEFAULT_TTL_MS,
  });
  accessLog.push(ip);
}

/**
 * Get current cache size.
 */
export function cacheSize(): number {
  return store.size;
}

/**
 * Clear the entire cache (useful for testing).
 */
export function cacheClear(): void {
  store.clear();
  accessLog.length = 0;
}
