# GeoIP Decoupling — Example Request Flow

This document shows how GeoIP is cleanly separated from RootNet core logic.

## Architecture

```
                           ┌──────────────────────┐
                           │   Client / User       │
                           │   (e.g. Flutter app)  │
                           └──────────┬───────────┘
                                      │
                         ┌────────────┴────────────┐
                         │                         │
                         ▼                         ▼
              ┌──────────────────┐     ┌──────────────────────┐
              │  RootNet API     │     │  GeoIP Service       │
              │  (Worker / EF)   │     │  (geo-api Edge Func) │
              │                  │     │                      │
              │  Core logic:     │     │  ONLY does:          │
              │  • Servers       │     │  • IP→country lookup │
              │  • Version check │     │  • Country→flag      │
              │  • Push notifs   │     │  • Cache management  │
              │  • Rate limiting │     │                      │
              │  • Auth          │     │  NO RootNet logic    │
              │                  │     └──────────────────────┘
              │  NO GeoIP logic  │              │
              └──────────────────┘              │
                         │                      │
                         │     HTTP fetch       │
                         │  ?ip=CLIENT_IP       │
                         └──────→───────────────┘
                                  ←──────────────┘
                              { country, country_code, flag }
```

## Flow Diagram (Request → Response)

```
1️⃣  Client sends request to RootNet API
    (e.g. POST /servers with JWT token)

2️⃣  RootNet extracts client IP from x-forwarded-for header
    ⚠️  ONLY for rate limiting — NOT for GeoIP

3️⃣  RootNet processes the request (auth, DB queries, etc.)

4️⃣  If the response needs country/location data:
    ↓
    RootNet calls the GeoIP service:
    GET https://geo-api.supabase.co/functions/v1/geo-api?ip=CLIENT_IP
    
    ↓
    GeoIP service:
    a. Checks Postgres cache (geoip_cache table)
    b. If cache HIT → returns cached result
    c. If cache MISS → fetches from ip-api.com → caches → returns
    
    ↓
    Returns: { success: true, country: "Iran", country_code: "IR", flag: "🇮🇷" }

5️⃣  If GeoIP service is DOWN:
    ↓
    RootNet does NOT break
    ↓
    Falls back to: { country: "Unknown", country_code: "XX", flag: "" }
    ↓
    Request continues normally — just without location data

6️⃣  RootNet returns final response to client
```

## Example: Endpoint That Uses GeoIP

Here's a hypothetical `/nearby-servers` endpoint — the GeoIP call is completely external:

```
GET /nearby-servers
Authorization: Bearer <jwt>
```

### Internal Flow (RootNet code):

```javascript
// Step 1: Authenticate (core RootNet logic)
const auth = await authenticateRequest(request, env);

// Step 2: Get client IP (for rate limiting + GeoIP lookup)
const clientIp = getClientIp(request);

// Step 3: Rate limit check (core RootNet logic)
if (isRateLimitedByIp(clientIp)) {
  return jsonResponse({ error: 'Too many requests' }, 429);
}

// Step 4: Call GeoIP service (external — can be swapped)
//         If it fails, we get "Unknown" — RootNet continues!
const geo = await lookupGeoIp(clientIp, env);

// Step 5: Fetch servers (core RootNet logic)
const servers = await querySupabase(env, 'servers', { ... });

// Step 6: Sort servers by proximity to user's country (core logic)
const sorted = sortServersByProximity(servers, geo.country);

// Step 7: Return response with GeoIP data
return jsonResponse({
  servers: sorted,
  user_location: {
    country: geo.country,
    country_code: geo.country_code,
    flag: geo.flag,
  },
});
```

## Swapping the GeoIP Provider

No RootNet code changes needed — just change the `GEOIP_SERVICE_URL`:

| Provider | URL | Pros | Cons |
|----------|-----|------|------|
| **Supabase EF** (default) | `https://<project>.supabase.co/functions/v1/geo-api` | Free, cached, own infra | Requires Supabase project |
| **ip-api.com** | `http://ip-api.com/json/{ip}?fields=status,country,countryCode` | Free, no API key | 45 req/min, HTTP only |
| **ipinfo.io** | `https://ipinfo.io/{ip}/json?token=YOUR_KEY` | 50k req/mo free, HTTPS | Needs API key |
| **AbstractAPI** | `https://ipgeolocation.abstractapi.com/v1/?api_key=KEY&ip_address=` | 20k req/mo free | Needs API key |

Set via env var: `npx wrangler secret put GEOIP_SERVICE_URL`

## Error Scenarios

| Scenario | RootNet Behavior | GeoIP Service Behavior |
|----------|-----------------|----------------------|
| GeoIP is healthy | Returns `country: "Iran"` | Returns cached/fresh data |
| GeoIP times out | Returns `country: "Unknown"` | N/A (RootNet continues) |
| GeoIP returns 500 | Returns `country: "Unknown"` | Logs error internally |
| GeoIP returns wrong data | Returns whatever GeoIP said | Bug in GeoIP service only |
| GeoIP URL is misconfigured | Returns `country: "Unknown"` | N/A |
| GeoIP service is completely down | RootNet works 100% without it | All requests fail silently |

## Key Principle

> **RootNet core logic NEVER depends on GeoIP being available.**
> GeoIP is a value-add — if it's down, RootNet continues working.
> The GeoIP service knows nothing about RootNet — it only does IP→country.
> To replace GeoIP, change ONE URL — no code changes.
