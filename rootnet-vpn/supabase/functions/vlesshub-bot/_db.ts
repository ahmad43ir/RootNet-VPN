// ============================================================
// üìÅ _db.ts ‚Äî SERVERS CRUD (via service_role client)
// ============================================================
// Port of bot.py's Supabase REST calls. Runs with the service_role
// client so it bypasses RLS (same trust level as the Python bot).
// ============================================================

import { deriveHostPort, deriveName, deriveType } from './_parser.ts';
import { lookupGeo } from './_geo.ts';

export interface ServerRow {
  id: number;
  name: string;
  flag: string;
  country: string;
  config: string;
  type: string;
  config_format: string;
}

export async function fetchServers(supabase: any): Promise<ServerRow[]> {
  try {
    const { data, error } = await supabase
      .from('servers')
      .select('id, name, flag, country, config, type, config_format')
      .eq('is_active', true)
      .order('id', { ascending: true });
    if (error) {
      console.warn('[db] fetchServers failed:', error.message);
      return [];
    }
    return (data ?? []) as ServerRow[];
  } catch (e) {
    console.warn('[db] fetchServers threw:', (e as Error).message);
    return [];
  }
}

export async function checkDuplicate(supabase: any, config: string): Promise<boolean> {
  try {
    const { data, error } = await supabase
      .from('servers')
      .select('id')
      .eq('config', config)
      .limit(1);
    if (error) return false;
    return (data?.length ?? 0) > 0;
  } catch {
    return false;
  }
}

export interface InsertContext {
  geoApiUrl: string;
  defaultFlag: string;
  defaultCountry: string;
}

/** A config ready to be stored, produced by the parser. */
export interface InsertEntry {
  config: string;
  configFormat: 'link' | 'json' | 'npv' | 'sip';
  type: string;
  host?: string;
  port?: number;
  name?: string;
}
// ============================================================
// VlessHub data plane ó Cloudflare D1 via the `vlesshub-api` Worker.
// Same function signatures as the old Supabase implementation, so
// handlers don't change. `supabase` params are kept for compatibility
// (bot chat state still lives there).
// ============================================================

import { apiGet, apiPost, apiPut, apiDelete } from './_api.ts';
import { deriveHostPort, deriveName, deriveType } from './_parser.ts';
import { lookupGeo } from './_geo.ts';

export async function fetchServers(_supabase: any): Promise<ServerRow[]> {
  const res = await apiGet<{ rows: ServerRow[] }>('/admin/servers');
  return res?.rows ?? [];
}

export async function checkDuplicate(_supabase: any, config: string): Promise<boolean> {
  // The worker dedupes on insert (config UNIQUE) ó nothing to pre-check.
  void config;
  return false;
}

export interface InsertContext {
  geoApiUrl: string;
  defaultFlag: string;
  defaultCountry: string;
}

/** A config ready to be stored, produced by the parser. */
export interface InsertEntry {
  config: string;
  configFormat: 'link' | 'json' | 'npv' | 'sip';
  type: string;
  host?: string;
  port?: number;
  name?: string;
}

export async function insertServer(
  _supabase: any,
  entry: InsertEntry,
  ctx: InsertContext,
  nameOverride?: string,
): Promise<boolean> {
  const { host: uriHost, port: uriPort } = deriveHostPort(entry.config);
  const host = entry.host || uriHost;
  const port = entry.port || uriPort;
  let flag = ctx.defaultFlag;
  let country = ctx.defaultCountry;

  const geo = await lookupGeo(host, ctx.geoApiUrl, ctx.defaultFlag, ctx.defaultCountry);
  if (geo) {
    flag = geo.flag;
    country = geo.country;
  }

  const name =
    nameOverride ??
    entry.name ??
    (entry.configFormat === 'link' ? deriveName(entry.config) : host || entry.type);
  const type = entry.type || deriveType(entry.config);

  const res = await apiPost<{ ok: boolean; duplicate?: boolean }>('/admin/servers', {
    row: {
      name,
      flag,
      country,
      config: entry.config,
      host,
      port,
      type,
      config_format: entry.configFormat,
    },
  });
  return res?.ok === true;
}

export async function deleteServer(_supabase: any, serverId: number): Promise<boolean> {
  const res = await apiDelete<{ count?: number }>(`/admin/servers?id=${serverId}`);
  return (res?.count ?? 0) > 0;
}

export async function deleteAllServers(_supabase: any): Promise<number> {
  const res = await apiDelete<{ count?: number }>('/admin/servers?all=1');
  return res?.count ?? 0;
}

export async function countActiveServers(_supabase: any): Promise<number | null> {
  const res = await apiGet<{ count: number }>('/admin/stats');
  return res?.count ?? null;
}

export async function updateServerGeo(
  _supabase: any,
  serverId: number,
  flag: string,
  country: string,
): Promise<boolean> {
  const res = await apiPost('/admin/servers/geo', { id: serverId, flag, country });
  return res?.ok === true;
}

export async function backfillFlags(
  _supabase: any,
  ctx: InsertContext,
): Promise<{ scanned: number; updated: number; failed: number }> {
  const res = await apiGet<{ rows: { id: number; host: string; flag: string; country: string }[] }>(
    '/admin/servers',
  );
  const servers = res?.rows ?? [];
  let scanned = 0;
  let updated = 0;
  let failed = 0;
  for (const s of servers) {
    scanned++;
    const geo = await lookupGeo(s.host ?? '', ctx.geoApiUrl, ctx.defaultFlag, ctx.defaultCountry);
    if (!geo) {
      failed++;
      continue;
    }
    if (geo.flag !== s.flag || geo.country !== s.country) {
      if (await updateServerGeo(_supabase, s.id, geo.flag, geo.country)) updated++;
      else failed++;
    }
  }
  return { scanned, updated, failed };
}

// --- Scraper proxy pool (proxies) ----------------------------

export interface ScraperProxyRow {
  id: number;
  host: string;
  port: number;
  secret: string | null;
  source: string | null;
  added_at: string | null;
  last_checked: string | null;
  last_ok: boolean | null;
  is_active: boolean;
}

export async function listScraperProxies(_supabase: any): Promise<ScraperProxyRow[]> {
  const res = await apiGet<{ rows: any[] }>('/admin/proxies');
  return (res?.rows ?? []).map((r) => ({
    id: r.id,
    host: r.host,
    port: r.port,
    secret: r.secret || null,
    source: r.source || null,
    added_at: r.added_at ?? null,
    last_checked: null,
    last_ok: null,
    is_active: r.is_active === true,
  }));
}

export async function addScraperProxy(
  _supabase: any,
  host: string,
  port: number,
  secret?: string,
  source = 'manual',
): Promise<'added' | 'exists' | 'error'> {
  const res = await apiPost<{ result?: string }>('/admin/proxies', { host, port, secret, source });
  if (!res) return 'error';
  if (res.result === 'added') return 'added';
  if (res.result === 'exists') return 'exists';
  return 'error';
}

/** Delete proxies by numeric id or by host. Returns the number deleted. */
export async function deleteScraperProxy(_supabase: any, hostOrId: string): Promise<number> {
  const res = await apiDelete<{ count?: number }>(`/admin/proxies?target=${encodeURIComponent(hostOrId.trim())}`);
  return res?.count ?? 0;
}

export async function deleteAllScraperProxies(_supabase: any): Promise<number> {
  const res = await apiDelete<{ count?: number }>('/admin/proxies?all=1');
  return res?.count ?? 0;
}

// --- App config (version management) ------------------------

export interface AppConfigRow {
  id: number;
  latest_version: string;
  latest_build: number;
  minimum_version: string;
  update_url: string;
  release_notes: string;
  force_update: boolean;
}

export async function getAppConfig(_supabase: any): Promise<AppConfigRow | null> {
  return await apiGet<AppConfigRow>('/admin/config');
}

export async function updateAppConfig(
  _supabase: any,
  updates: Partial<Pick<AppConfigRow, 'latest_version' | 'latest_build' | 'minimum_version' | 'update_url' | 'release_notes' | 'force_update'>>,
): Promise<boolean> {
  const res = await apiPut('/admin/config', updates);
  return res?.ok === true;
}

// --- Scraper channel list (bot_state in D1) ------------------

export async function getScraperChannels(_supabase: any): Promise<string[]> {
  const res = await apiGet<{ channels: string[] }>('/admin/channels');
  return res?.channels ?? [];
}

export async function setScraperChannels(_supabase: any, channels: string[]): Promise<boolean> {
  const res = await apiPut('/admin/channels', { channels });
  return res?.ok === true;
}

export async function getLastScrapeTime(_supabase: any): Promise<string | null> {
  const res = await apiGet<{ value: string | null }>('/admin/lastscrape');
  return res?.value ?? null;
}

export async function setLastScrapeTime(_supabase: any): Promise<boolean> {
  const res = await apiPut('/admin/lastscrape', {});
  return res?.ok === true;
}

// --- VPN Files (vpn_files) ------------------------------------

export interface VpnFileRow {
  id: number;
  filename: string;
  mime_type: string | null;
  size_bytes: number;
  source_channel: string | null;
  uploaded_by: number | null;
  uploaded_at: string;
  is_encrypted: boolean;
  config_count: number;
}

export async function listVpnFiles(
  _supabase: any,
  options?: {
    channel?: string;
    limit?: number;
    offset?: number;
    onlyEncrypted?: boolean;
  },
): Promise<VpnFileRow[]> {
  const q = new URLSearchParams();
  if (options?.channel) q.set('channel', options.channel);
  if (options?.onlyEncrypted) q.set('encrypted', '1');
  q.set('limit', String(options?.limit ?? 50));
  if (options?.offset) q.set('offset', String(options.offset));
  const res = await apiGet<{ rows: any[] }>(`/admin/files?${q.toString()}`);
  return (res?.rows ?? []).map((r) => ({
    id: r.id,
    filename: r.filename,
    mime_type: r.mime_type ?? null,
    size_bytes: r.size_bytes ?? 0,
    source_channel: r.source_channel ?? null,
    uploaded_by: null,
    uploaded_at: r.uploaded_at,
    is_encrypted: r.is_encrypted === true,
    config_count: r.config_count ?? 0,
  }));
}

export async function getVpnFile(_supabase: any, fileId: number): Promise<{ filename: string; content: string; mime_type: string | null } | null> {
  return await apiGet<{ filename: string; content: string; mime_type: string | null }>(
    `/admin/files/${fileId}`,
  );
}

export async function deleteVpnFile(_supabase: any, fileId: number): Promise<boolean> {
  const res = await apiDelete<{ ok?: boolean }>(`/admin/files?id=${fileId}`);
  return res?.ok === true;
}

export async function countVpnFiles(_supabase: any, channel?: string): Promise<number> {
  const res = await apiGet<{ count: number }>(
    `/admin/files/count${channel ? `?channel=${encodeURIComponent(channel)}` : ''}`,
  );
  return res?.count ?? 0;
}

export interface SaveVpnFileInput {
  filename: string;
  mime_type: string | null;
  size_bytes: number;
  /** Base64-encoded raw bytes. */
  contentBase64: string;
  source_channel?: string | null;
  uploaded_by?: number | null;
}

/**
 * Store a raw config file (.npvt / .sip / .npv / ...) so it appears in the
 * app's File tab. Dedupe on filename + size (worker-side). Returns whether
 * a new row was inserted.
 */
export async function saveVpnFile(
  _supabase: any,
  input: SaveVpnFileInput,
): Promise<{ saved: boolean; duplicate: boolean }> {
  const res = await apiPost<{ saved: boolean; duplicate: boolean }>('/admin/files', {
    filename: input.filename,
    mime_type: input.mime_type,
    size_bytes: input.size_bytes,
    contentBase64: input.contentBase64,
    source_channel: input.source_channel ?? null,
  });
  if (!res) return { saved: false, duplicate: false };
  return { saved: res.saved === true, duplicate: res.duplicate === true };
}
