# GeoIP Service — Production-Grade Architecture

Standalone GeoIP lookup service. **Zero RootNet coupling.**

## Architecture

```
Client (RootNet / any caller)
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  index.ts              HTTP handler (thin entry point)    │
├──────────────────────────────────────────────────────────┤
│  _geo-service.ts       Orchestrator                       │
│    ├─ _cache.ts         In-memory cache (1h TTL, LRU)     │
│    ├─ _circuit-breaker.ts 5 failures → 60s cooldown       │
│    ├─ _retry.ts         2 retries, 300→600ms backoff      │
│    └─ _providers/                                         │
│         ├─ _supabase.ts    PRIMARY — geoip_cache table     │
│         └─ _cloud-api.ts   FALLBACK — ip-api.com           │
└──────────────────────────────────────────────────────────┘
```

## Provider Strategy

**Supabase (PRIMARY):**
- Queries `geoip_cache` Postgres table
- 2-second aggressive timeout
- Circuit breaker: 5 consecutive failures → disabled 60s
- On failure → automatic fallback

**Cloud API (SECONDARY):**
- ip-api.com free tier
- 5-second timeout
- Up to 2 retries with exponential backoff (300ms → 600ms)
- Results cached in-memory AND written back to Supabase

## Data Flow

```
1. Client sends GET ?ip=X.X.X.X
2. Check in-memory cache → HIT? return immediately (~0ms)
3. MISS → Check Supabase (primary):
   a. Circuit open? → skip to step 4
   b. Query geoip_cache table (2s timeout)
   c. HIT? → return, cache in-memory
   d. FAIL? → record failure, may open circuit
4. Check Cloud API (fallback):
   a. Fetch from ip-api.com (5s timeout, up to 2 retries)
   b. Success? → return, cache in-memory + Supabase
   c. FAIL? → return "Unknown"
5. Normalized response: { ip, country, countryCode }
```

## Response Format (STRICT)

```json
// Success
{ "ip": "8.8.8.8", "country": "United States", "countryCode": "US" }

// Rate limited
{ "error": "rate_limited", "ip": "8.8.8.8", "country": "Unknown", "countryCode": "XX" }

// All providers failed
{ "ip": "8.8.8.8", "country": "Unknown", "countryCode": "XX" }
```

## Resilience Features

| Feature | Config | Behavior |
|---------|--------|----------|
| In-memory cache | 1h TTL, 10k entries LRU | ~0ms lookup for cached IPs |
| Supabase timeout | 2 seconds | Falls back to Cloud API on timeout |
| Cloud API retry | 2 retries, 300→600ms backoff | Survives transient network failures |
| Circuit breaker | 5 failures → 60s cooldown | Prevents cascading Supabase failures |
| Supabase cache write | Fire-and-forget, 2s timeout | Non-critical — failures logged only |
| Unknown fallback | Cached briefly | Prevents hammering on repeated failures |

## Files

| File | Purpose |
|------|---------|
| `index.ts` | HTTP entry point (thin) |
| `_types.ts` | Shared interfaces |
| `_utils.ts` | CORS, JSON, IP validation, logging |
| `_cache.ts` | In-memory cache (1h TTL, LRU eviction) |
| `_retry.ts` | Retry with exponential backoff |
| `_circuit-breaker.ts` | Circuit breaker (5→60s) |
| `_rate-limit.ts` | Self-contained rate limiting |
| `_providers/_supabase.ts` | Supabase primary provider (2s timeout) |
| `_providers/_cloud-api.ts` | Cloud API fallback (ip-api.com) |
| `_geo-service.ts` | Orchestrator |
