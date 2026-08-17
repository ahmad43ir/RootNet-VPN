// ============================================================
// 📁 _types.ts — SHARED TYPES FOR GEOIP SERVICE
// ============================================================
// All modules import types from here to ensure consistency.
// ============================================================

/** Normalized GeoIP response — consistent REGARDLESS of provider */
export interface GeoIpResult {
  ip: string;
  country: string;
  countryCode: string;
}

/** Raw response from the Supabase geoip_cache query */
export interface SupabaseGeoIpRow {
  country_name: string;
  country_code: string;
  flag_emoji: string;
}

/** Raw response from ip-api.com */
export interface IpApiResponse {
  status: 'success' | 'fail';
  country: string;
  countryCode: string;
  query: string;
}

/** Result from the circuit breaker */
export interface CircuitBreakerState {
  isOpen: boolean;
  remainingCooldownMs: number;
}

/** Status reported by a provider after a lookup attempt */
export interface ProviderResult {
  data: GeoIpResult | null;
  /** Human-readable label for logging (e.g. 'supabase', 'cloud-api') */
  source: string;
  /** Whether this is a cache hit (no upstream call was made) */
  fromCache: boolean;
}

/** Configuration for retry behavior */
export interface RetryConfig {
  maxRetries: number;
  baseDelayMs: number;
}

/** Configuration for circuit breaker */
export interface CircuitBreakerConfig {
  failureThreshold: number;
  cooldownMs: number;
}
