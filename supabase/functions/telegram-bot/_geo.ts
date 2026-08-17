// ============================================================
// 📁 _geo.ts — GEOIP LOOKUP (DNS + existing geo-api function)
// ============================================================
// Port of bot.py's geo lookup. Resolves host -> IPv4, asks the
// existing `geo-api` Supabase Edge Function for country info, and
// returns a flag emoji + country label. Falls back to defaults on
// any failure — GeoIP is a nice-to-have, never a blocker.
// ============================================================

const DNS_TIMEOUT_MS = 3_000;

export function isValidIpv4(str: string): boolean {
  const parts = str.split('.');
  if (parts.length !== 4) return false;
  return parts.every((part) => {
    if (!/^\d{1,3}$/.test(part)) return false;
    const n = Number(part);
    return n >= 0 && n <= 255 && String(n) === part;
  });
}

async function resolveViaDoH(host: string): Promise<string | null> {
  try {
    const res = await fetch(
      `https://dns.google/resolve?name=${encodeURIComponent(host)}&type=A`,
      {
        headers: { accept: 'application/dns-json' },
        signal: AbortSignal.timeout(DNS_TIMEOUT_MS),
      },
    );
    if (!res.ok) return null;
    const json = await res.json();
    const answers: { type?: number; data?: string }[] = json?.Answer ?? [];
    const aRecord = answers.find((a) => a.type === 1);
    return aRecord?.data ?? null;
  } catch {
    return null;
  }
}

/**
 * Resolve a hostname to an IPv4 address. IPs pass through; failures
 * -> null. Tries Deno's native DNS first, then DNS-over-HTTPS.
 */
export async function resolveHost(host: string): Promise<string | null> {
  if (!host) return null;
  if (isValidIpv4(host)) return host;

  try {
    const ips = await Deno.resolveDns(host, 'A');
    if (ips && ips.length > 0) return ips[0];
  } catch {
    // fall through to DoH
  }

  return resolveViaDoH(host);
}

/** ISO-3166 alpha-2 -> flag emoji (regional indicator symbols). */
export function flagFromCountryCode(code: string, defaultFlag: string): string {
  const normalized = (code || '').toUpperCase();
  if (normalized.length !== 2 || !/^[A-Z]{2}$/.test(normalized) || normalized === 'XX') {
    return defaultFlag;
  }
  const base = 0x1f1e6;
  return (
    String.fromCodePoint(base + normalized.charCodeAt(0) - 65) +
    String.fromCodePoint(base + normalized.charCodeAt(1) - 65)
  );
}

export interface GeoResult {
  flag: string;
  country: string;
}

export async function lookupGeo(
  host: string,
  geoApiUrl: string,
  defaultFlag: string,
  defaultCountry: string,
): Promise<GeoResult | null> {
  const ip = await resolveHost(host);
  if (!ip) return null;

  try {
    const res = await fetch(`${geoApiUrl}?ip=${encodeURIComponent(ip)}`, {
      signal: AbortSignal.timeout(DNS_TIMEOUT_MS),
    });
    if (!res.ok) return null;
    const json = await res.json();
    const countryCode = String(json?.countryCode ?? '');
    const country = String(json?.country ?? '') || defaultCountry;
    return { flag: flagFromCountryCode(countryCode, defaultFlag), country };
  } catch {
    return null;
  }
}
