// ============================================================
// 📁 _parser.ts — CONFIG URI PARSING & METADATA DERIVATION
// ============================================================
// Port of telegram-bot/bot.py's parsing (supported schemes, regex
// extraction, JSON walk, name/type/host/port derivation) so behavior
// stays identical to the Python bot.
// ============================================================

const KNOWN_SCHEMES = [
  'vless',
  'vmess',
  'trojan',
  'ss',
  'ssr',
  'shadowsocks',
  'socks',
  'socks4',
  'socks5',
  'socks5h',
  'hysteria2',
  'hysteria',
  'hy2',
  'tuic',
  'wireguard',
  'warp',
  'ssh',
];

// Same pattern as the Python bot: scheme:// up to whitespace/comma.
const LINK_PATTERN =
  /(?<uri>(?:vless|vmess|trojan|ss|ssr|shadowsocks|socks|socks4|socks5|socks5h|hysteria2|hysteria|hy2|tuic|wireguard|warp|ssh):\/\/[^\s,\u00a0]+)/gi;

// Trailing junk that sometimes rides along on a copied URI.
const TRAILING_JUNK = ")]}>},;.\"'";

export function isUri(value: unknown): boolean {
  if (typeof value !== 'string') return false;
  const lower = value.toLowerCase();
  return KNOWN_SCHEMES.some((scheme) => lower.startsWith(`${scheme}://`));
}

export function cleanUri(uri: string): string {
  let trimmed = uri.trim();
  while (trimmed.length > 0 && TRAILING_JUNK.includes(trimmed[trimmed.length - 1])) {
    trimmed = trimmed.slice(0, -1);
  }
  return trimmed;
}

export function extractLinks(text: string): string[] {
  if (!text) return [];
  const seen = new Set<string>();
  const result: string[] = [];
  for (const match of text.matchAll(LINK_PATTERN)) {
    const uri = cleanUri(match.groups?.uri ?? '');
    if (uri && !seen.has(uri)) {
      seen.add(uri);
      result.push(uri);
    }
  }
  return result;
}

export function extractFromJson(node: unknown): string[] {
  const result: string[] = [];
  if (Array.isArray(node)) {
    for (const item of node) {
      result.push(...extractFromJson(item));
    }
  } else if (node && typeof node === 'object') {
    const obj = node as Record<string, unknown>;
    if (isUri(obj.config)) {
      result.push(cleanUri(String(obj.config)));
    }
    for (const value of Object.values(obj)) {
      if (Array.isArray(value) || (value !== null && typeof value === 'object')) {
        result.push(...extractFromJson(value));
      }
    }
  }
  return result;
}

/** A single parsed server config, ready to be stored in `servers`. */
export interface ParsedConfig {
  config: string;
  configFormat: 'link' | 'json' | 'npv' | 'sip';
  type: string;
  host?: string;
  port?: number;
  name?: string;
}

function toParsedLink(uri: string): ParsedConfig {
  const { host, port } = deriveHostPort(uri);
  return { config: uri, configFormat: 'link', type: deriveType(uri), host, port, name: deriveName(uri) };
}

function normalizeType(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const t = value.trim().toLowerCase();
  if (t === 'ss' || t === 'shadowsocks') return 'shadowsocks';
  if (t === 'ssr') return 'ssr';
  if (t === 'hy2' || t === 'hysteria' || t === 'hysteria2') return 'hysteria2';
  if (t === 'socks' || t === 'socks4' || t === 'socks5' || t === 'socks5h') return 'socks';
  if (['vless', 'vmess', 'trojan', 'tuic', 'wireguard', 'warp', 'ssh'].includes(t)) return t;
  return null;
}

function hostPortFromJson(value: Record<string, unknown>): { host: string; port: number } {
  const host = String(value.add ?? value.address ?? value.host ?? '');
  const raw = Number(value.port ?? 443);
  const port = Number.isInteger(raw) && raw > 0 ? raw : 443;
  return { host, port };
}

const BASE64_RE = /^[A-Za-z0-9+/_-]+={0,2}$/;

function tryDecodeBase64(value: string): string | null {
  if (!BASE64_RE.test(value)) return null;
  try {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const decoded = new TextDecoder().decode(bytes).trim();
    if (!decoded) return null;
    if (decoded.startsWith('{') || decoded.startsWith('[') || isUri(decoded)) return decoded;
    return null;
  } catch {
    return null;
  }
}

/**
 * Turn a profile value (URI string, base64 VMess payload, inline JSON
 * object, or object) into a ParsedConfig. Returns `null` when the value
 * does not look like any supported config shape.
 */
function configFromValue(
  value: unknown,
  typeHint?: unknown,
  nameHint?: string,
): ParsedConfig | null {
  if (value === null || value === undefined) return null;

  if (typeof value === 'object') {
    const obj = value as Record<string, unknown>;
    const type = normalizeType(typeHint) ?? normalizeType(obj.type) ?? normalizeType(obj.protocol) ?? 'vless';
    const { host, port } = hostPortFromJson(obj);
    return { config: JSON.stringify(obj), configFormat: 'json', type, host, port, name: nameHint };
  }

  if (typeof value !== 'string') return null;
  let s = value.trim();
  if (!s) return null;

  if ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith("'") && s.endsWith("'"))) {
    s = s.slice(1, -1).trim();
  }

  if (isUri(s)) {
    const pc = toParsedLink(s);
    if (nameHint) pc.name = nameHint;
    return pc;
  }

  const decoded = tryDecodeBase64(s);
  if (decoded) {
    return configFromValue(decoded, typeHint, nameHint);
  }

  if (s.startsWith('{') || s.startsWith('[')) {
    try {
      return configFromValue(JSON.parse(s), typeHint, nameHint);
    } catch {
      return null;
    }
  }

  const links = extractLinks(s);
  if (links.length > 0) {
    const pc = toParsedLink(links[0]);
    if (nameHint) pc.name = nameHint;
    return pc;
  }

  return null;
}

const NPV_KEYS = ['npv', 'npvt', 'npt'];

/**
 * Recursively collect every NPV-family envelope node (`{"npv": ...}`,
 * `{"npvt": ...}`, `{"npt": ...}`) in the JSON tree. `.npv` and `.npvt`
 * are the same list/template structure in NekoBox; the extra keys are a
 * safety net for exporters that use them as the envelope.
 */
function collectNpvNodes(node: unknown, out: Record<string, unknown>[]): void {
  if (Array.isArray(node)) {
    for (const item of node) collectNpvNodes(item, out);
  } else if (node && typeof node === 'object') {
    const obj = node as Record<string, unknown>;
    for (const key of NPV_KEYS) {
      const inner = obj[key];
      if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
        out.push(inner as Record<string, unknown>);
      }
    }
    for (const value of Object.values(obj)) {
      if (value !== null && typeof value === 'object') collectNpvNodes(value, out);
    }
  }
}

/**
 * Extract configs from NekoBox / NekoRay NPV-family exports (`.npv` proxy
 * lists and `.npvt` templates), covering both the single-profile envelope
 * `{"npv":{"protocol":...,"config":...}}` and the full export
 * `{"npv":{"profiles":[{"type":...,"name":...,"config": ...}]}}` where
 * `config` may itself be a base64 VMess payload.
 */
export function extractNpv(json: unknown): ParsedConfig[] {
  const nodes: Record<string, unknown>[] = [];
  collectNpvNodes(json, nodes);

  const result: ParsedConfig[] = [];
  for (const npv of nodes) {
    const profileName = String(npv.name ?? npv.remark ?? '').trim() || undefined;
    const typeHint = npv.protocol ?? npv.type;

    const profiles = Array.isArray(npv.profiles) ? npv.profiles : null;
    if (profiles) {
      for (const profile of profiles) {
        if (!profile || typeof profile !== 'object') continue;
        const p = profile as Record<string, unknown>;
        const name = String(p.name ?? p.remark ?? profileName ?? '').trim() || undefined;
        const pc = configFromValue(p.config, p.type ?? typeHint, name);
        if (pc) result.push(pc);
      }
      continue;
    }

    if ('config' in npv) {
      const pc = configFromValue(npv.config, typeHint, profileName);
      if (pc) result.push(pc);
    } else if (Object.keys(npv).length > 0) {
      const pc = configFromValue(npv, typeHint, profileName);
      if (pc) result.push(pc);
    }
  }
  return result;
}

/**
 * Extract configs from SIP format (SocksIP / SSH / SOCKS5 / HTTP proxy configs).
 * Expected JSON: {"protocol": "ssh|socks|http", "host": "...", "port": 22, "username": "...", "password": "...", "key": "..."}
 * Also supports array of SIP configs.
 */
function extractSip(json: unknown): ParsedConfig[] {
  const result: ParsedConfig[] = [];
  
  function processSipObject(obj: Record<string, unknown>): ParsedConfig | null {
    const proto = String(obj.protocol ?? obj.type ?? 'socks').toLowerCase();
    const host = String(obj.host ?? obj.address ?? obj.server ?? '').trim();
    const port = Number(obj.port ?? (proto === 'ssh' ? 22 : proto === 'http' || proto === 'https' ? 8080 : 1080));
    const username = String(obj.username ?? obj.user ?? '').trim() || undefined;
    const password = String(obj.password ?? obj.pass ?? '').trim() || undefined;
    const privateKey = String(obj.key ?? obj.private_key ?? '').trim() || undefined;
    
    if (!host || port <= 0) return null;
    
    // Build SIP config JSON string
    const sipConfig = JSON.stringify({
      protocol: proto,
      host,
      port,
      username,
      password,
      key: privateKey,
    });
    
    const type = proto === 'ssh' ? 'socks' : proto === 'http' || proto === 'https' ? 'socks' : proto;
    const displayType = proto === 'ssh' ? 'SSH' : proto.toUpperCase();
    
    return {
      config: sipConfig,
      configFormat: 'sip',
      type,
      host,
      port,
      name: `SIP-${displayType}`,
    };
  }
  
  if (Array.isArray(json)) {
    for (const item of json) {
      if (item && typeof item === 'object') {
        const pc = processSipObject(item as Record<string, unknown>);
        if (pc) result.push(pc);
      }
    }
  } else if (json && typeof json === 'object') {
    const obj = json as Record<string, unknown>;
    // Check if it's a SIP config object (has protocol and host fields)
    if ('protocol' in obj && ('host' in obj || 'address' in obj || 'server' in obj)) {
      const pc = processSipObject(obj);
      if (pc) result.push(pc);
    }
    // Also check nested objects
    for (const value of Object.values(obj)) {
      if (value && typeof value === 'object' && !Array.isArray(value)) {
        const nested = extractSip(value);
        result.push(...nested);
      } else if (Array.isArray(value)) {
        const nested = extractSip(value);
        result.push(...nested);
      }
    }
  }
  
  return result;
}

/**
 * Parse the uploaded file/text into a deduplicated list of parsed
 * configs. Prefers NPV JSON parsing when the content looks like JSON,
 * then falls back to plain regex extraction (handles .txt and pasted
 * text).
 */
export function parseFile(content: string): ParsedConfig[] {
  const stripped = content.trim();
  let parsed: ParsedConfig[] = [];

  if (stripped.startsWith('{') || stripped.startsWith('[')) {
    try {
      const json = JSON.parse(stripped);
      parsed = extractNpv(json);
      if (parsed.length === 0) {
        parsed = extractSip(json);
      }
      if (parsed.length === 0) {
        for (const uri of extractFromJson(json)) {
          parsed.push(toParsedLink(uri));
        }
      }
    } catch {
      parsed = [];
    }
  }

  if (parsed.length === 0) {
    for (const uri of extractLinks(content)) {
      parsed.push(toParsedLink(uri));
    }
  }

  const seen = new Set<string>();
  const result: ParsedConfig[] = [];
  for (const pc of parsed) {
    if (pc.config && !seen.has(pc.config)) {
      seen.add(pc.config);
      result.push(pc);
    }
  }
  return result;
}

/**
 * Extract the Telegram channel handle (@name) from config text.
 * Prefers the `tel:@...` / `telegram:@...` / `t.me/...` references that
 * VPN channels append to their links, and falls back to any bare
 * `@name` in the text. Returns `null` when no channel reference exists.
 */
export function extractChannel(value: string): string | null {
  if (!value) return null;

  // 1. Explicit t.me / telegram links (most reliable)
  const explicit =
    value.match(/t(?:elegram|el)?:\s*@([A-Za-z0-9_]{3,})/i)?.[1] ??
    value.match(/t\.me\/(?:@)?([A-Za-z0-9_]{3,})/i)?.[1];
  if (explicit) return `@${explicit}`;

  // 2. Check URL fragments (after #) — channels embed @handle there.
  //    Fragments may be URL-encoded, so decode first.
  const hashIdx = value.lastIndexOf('#');
  if (hashIdx >= 0) {
    const rawFragment = value.slice(hashIdx + 1);
    try {
      const fragment = decodeURIComponent(rawFragment);
      const fragMatch = fragment.match(/@([A-Za-z0-9_]{3,})/);
      if (fragMatch) return `@${fragMatch[1]}`;
    } catch {
      // malformed percent-encoding — try raw
      const fragMatch = rawFragment.match(/@([A-Za-z0-9_]{3,})/);
      if (fragMatch) return `@${fragMatch[1]}`;
    }
  }

  // 3. Bare @handle in the body text — skip IP-like matches
  //    (e.g. @159 from vless://...@159.195.242.180...)
  const bare = value.match(/@([A-Za-z][A-Za-z0-9_]{2,})/);
  if (bare) return `@${bare[1]}`;

  return null;
}

/** Name from #fragment, else first host label, else the protocol name. */
export function deriveName(uri: string): string {
  const hashIndex = uri.indexOf('#');
  const fragment = hashIndex >= 0 ? uri.slice(hashIndex + 1) : '';
  const name = fragment.trim();
  if (name) return name.slice(0, 64);

  try {
    const host = new URL(uri).hostname || '';
    // Long, dot-less hosts are base64 payloads (e.g. vmess://<b64>) — skip.
    if (host && (host.includes('.') || host.length <= 30)) {
      return host.split('.')[0].slice(0, 64);
    }
  } catch {
    // fall through to the scheme fallback
  }

  const scheme = (uri.split(':', 1)[0] ?? '').toLowerCase();
  if (scheme === 'ss' || scheme === 'shadowsocks' || scheme === 'ssr') return 'Shadowsocks';
  if (!scheme) return '';
  return scheme.charAt(0).toUpperCase() + scheme.slice(1).toLowerCase();
}

export function deriveType(uri: string): string {
  const scheme = (uri.split(':', 1)[0] ?? '').toLowerCase();
  switch (scheme) {
    case 'ss':
    case 'shadowsocks':
      return 'shadowsocks';
    case 'ssr':
      return 'ssr';
    case 'socks':
    case 'socks4':
    case 'socks5':
    case 'socks5h':
      return 'socks';
    case 'hy2':
    case 'hysteria':
    case 'hysteria2':
      return 'hysteria2';
    default:
      return scheme;
  }
}

export function deriveHostPort(uri: string): { host: string; port: number } {
  try {
    const url = new URL(uri);
    const host = url.hostname || '';
    let port = 443;
    if (url.port) {
      const parsed = Number(url.port);
      if (Number.isInteger(parsed) && parsed > 0) port = parsed;
    }
    return { host, port };
  } catch {
    return { host: '', port: 443 };
  }
}
