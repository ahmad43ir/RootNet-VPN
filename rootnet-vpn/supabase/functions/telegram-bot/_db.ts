// ============================================================
// 📁 _db.ts — SERVERS CRUD (via service_role client)
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

export async function insertServer(
  supabase: any,
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

  try {
    const { error } = await supabase.from('servers').insert({
      name,
      flag,
      country,
      config: entry.config,
      host,
      port,
      is_active: true,
      type,
      config_format: entry.configFormat,
    });
    if (error) {
      console.warn('[db] insertServer failed:', error.message);
      return false;
    }
    return true;
  } catch (e) {
    console.warn('[db] insertServer threw:', (e as Error).message);
    return false;
  }
}

export async function deleteServer(supabase: any, serverId: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('servers').delete().eq('id', serverId);
    if (error) console.warn('[db] deleteServer failed:', error.message);
    return !error;
  } catch (e) {
    console.warn('[db] deleteServer threw:', (e as Error).message);
    return false;
  }
}

export async function deleteAllServers(supabase: any): Promise<number> {
  try {
    const { count, error } = await supabase
      .from('servers')
      .delete()
      .eq('is_active', true)
      .select('id', { count: 'exact' });
    if (error) {
      console.warn('[db] deleteAllServers failed:', error.message);
      return 0;
    }
    return count ?? 0;
  } catch (e) {
    console.warn('[db] deleteAllServers threw:', (e as Error).message);
    return 0;
  }
}

export async function countActiveServers(supabase: any): Promise<number | null> {
  try {
    const { count, error } = await supabase
      .from('servers')
      .select('id', { count: 'exact', head: true })
      .eq('is_active', true);
    if (error) {
      console.warn('[db] countActiveServers failed:', error.message);
      return null;
    }
    return count ?? 0;
  } catch {
    return null;
  }
}

export async function updateServerGeo(
  supabase: any,
  serverId: number,
  flag: string,
  country: string,
): Promise<boolean> {
  try {
    const { error } = await supabase.from('servers').update({ flag, country }).eq('id', serverId);
    if (error) console.warn('[db] updateServerGeo failed:', error.message);
    return !error;
  } catch (e) {
    console.warn('[db] updateServerGeo threw:', (e as Error).message);
    return false;
  }
}

export async function backfillFlags(
  supabase: any,
  ctx: InsertContext,
): Promise<{ scanned: number; updated: number; failed: number }> {
  try {
    const { data, error } = await supabase
      .from('servers')
      .select('id, host, flag, country')
      .eq('is_active', true)
      .order('id', { ascending: true });
    if (error) return { scanned: 0, updated: 0, failed: 0 };

    const servers = (data ?? []) as {
      id: number;
      host?: string;
      flag?: string;
      country?: string;
    }[];

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
        if (await updateServerGeo(supabase, s.id, geo.flag, geo.country)) updated++;
        else failed++;
      }
    }
    return { scanned, updated, failed };
  } catch (e) {
    console.warn('[db] backfillFlags threw:', (e as Error).message);
    return { scanned: 0, updated: 0, failed: 0 };
  }
}

// ─── App config (version management) ────────────────────────

export interface AppConfigRow {
  id: number;
  latest_version: string;
  latest_build: number;
  minimum_version: string;
  update_url: string;
  release_notes: string;
  force_update: boolean;
}

export async function getAppConfig(supabase: any): Promise<AppConfigRow | null> {
  try {
    const { data, error } = await supabase
      .from('app_config')
      .select('id, latest_version, latest_build, minimum_version, update_url, release_notes, force_update')
      .eq('id', 1)
      .maybeSingle();
    if (error) {
      console.warn('[db] getAppConfig failed:', error.message);
      return null;
    }
    return data as AppConfigRow;
  } catch (e) {
    console.warn('[db] getAppConfig threw:', (e as Error).message);
    return null;
  }
}

export async function updateAppConfig(
  supabase: any,
  updates: Partial<Pick<AppConfigRow, 'latest_version' | 'latest_build' | 'minimum_version' | 'update_url' | 'release_notes' | 'force_update'>>,
): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('app_config')
      .update({ ...updates, updated_at: new Date().toISOString() })
      .eq('id', 1);
    if (error) {
      console.warn('[db] updateAppConfig failed:', error.message);
      return false;
    }
    return true;
  } catch (e) {
    console.warn('[db] updateAppConfig threw:', (e as Error).message);
    return false;
  }
}

// ─── BPB subscriptions (bpb_services) ───────────────────────

export interface BpbServiceRow {
  id: number;
  label: string;
  sub_url: string;
  is_active: boolean;
}

export async function listBpbServices(supabase: any): Promise<BpbServiceRow[] | null> {
  try {
    const { data, error } = await supabase
      .from('bpb_services')
      .select('id, label, sub_url, is_active')
      .order('id', { ascending: true });
    if (error) {
      console.warn('[db] listBpbServices failed:', error.message);
      return null;
    }
    return (data ?? []) as BpbServiceRow[];
  } catch (e) {
    console.warn('[db] listBpbServices threw:', (e as Error).message);
    return null;
  }
}

export async function addBpbService(
  supabase: any,
  label: string,
  subUrl: string,
): Promise<{ ok: boolean; error?: string }> {
  try {
    const { data: existing } = await supabase
      .from('bpb_services')
      .select('id')
      .eq('label', label)
      .maybeSingle();
    if (existing) return { ok: false, error: `A subscription labeled \`${label}\` already exists. Delete it first.` };
    const { error } = await supabase
      .from('bpb_services')
      .insert({ label, sub_url: subUrl, is_active: true });
    if (error) return { ok: false, error: error.message };
    return { ok: true };
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
}

/** Returns the number of rows deleted (0 = label not found). */
export async function deleteBpbService(supabase: any, label: string): Promise<number> {
  try {
    const { data, error } = await supabase
      .from('bpb_services')
      .delete()
      .eq('label', label)
      .select('id');
    if (error) {
      console.warn('[db] deleteBpbService failed:', error.message);
      return -1;
    }
    return data?.length ?? 0;
  } catch (e) {
    console.warn('[db] deleteBpbService threw:', (e as Error).message);
    return -1;
  }
}

/** Flips is_active for a label; returns the new state (null on failure). */
export async function toggleBpbService(
  supabase: any,
  label: string,
): Promise<boolean | null> {
  try {
    const { data: row } = await supabase
      .from('bpb_services')
      .select('id, is_active')
      .eq('label', label)
      .maybeSingle();
    if (!row) return null;
    const next = !row.is_active;
    const { error } = await supabase
      .from('bpb_services')
      .update({ is_active: next })
      .eq('id', row.id);
    if (error) {
      console.warn('[db] toggleBpbService failed:', error.message);
      return null;
    }
    return next;
  } catch (e) {
    console.warn('[db] toggleBpbService threw:', (e as Error).message);
    return null;
  }
}

// ─── VPN Files (vpn_files) ────────────────────────────────────

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
  supabase: any,
  options?: {
    channel?: string;
    limit?: number;
    offset?: number;
    onlyEncrypted?: boolean;
  },
): Promise<VpnFileRow[]> {
  try {
    let query = supabase
      .from('vpn_files')
      .select('id, filename, mime_type, size_bytes, source_channel, uploaded_by, uploaded_at, is_encrypted, config_count')
      .order('uploaded_at', { ascending: false });
    
    if (options?.channel) {
      query = query.eq('source_channel', options.channel);
    }
    if (options?.onlyEncrypted) {
      query = query.eq('is_encrypted', true);
    }
    if (options?.limit) {
      query = query.limit(options.limit);
    }
    if (options?.offset) {
      query = query.range(options.offset, (options.offset + (options.limit || 50)) - 1);
    }

    const { data, error } = await query;
    if (error) {
      console.warn('[db] listVpnFiles failed:', error.message);
      return [];
    }
    return (data ?? []) as VpnFileRow[];
  } catch (e) {
    console.warn('[db] listVpnFiles threw:', (e as Error).message);
    return [];
  }
}

export async function getVpnFile(supabase: any, fileId: number): Promise<{ filename: string; content: string; mime_type: string | null } | null> {
  try {
    const { data, error } = await supabase
      .from('vpn_files')
      .select('filename, content, mime_type')
      .eq('id', fileId)
      .maybeSingle();
    if (error || !data) {
      console.warn('[db] getVpnFile failed:', error?.message);
      return null;
    }
    return data as { filename: string; content: string; mime_type: string | null };
  } catch (e) {
    console.warn('[db] getVpnFile threw:', (e as Error).message);
    return null;
  }
}

export async function deleteVpnFile(supabase: any, fileId: number): Promise<boolean> {
  try {
    const { error } = await supabase.from('vpn_files').delete().eq('id', fileId);
    if (error) {
      console.warn('[db] deleteVpnFile failed:', error.message);
      return false;
    }
    return true;
  } catch (e) {
    console.warn('[db] deleteVpnFile threw:', (e as Error).message);
    return false;
  }
}

export async function countVpnFiles(supabase: any, channel?: string): Promise<number> {
  try {
    let query = supabase
      .from('vpn_files')
      .select('id', { count: 'exact', head: true });
    if (channel) {
      query = query.eq('source_channel', channel);
    }
    const { count, error } = await query;
    if (error) {
      console.warn('[db] countVpnFiles failed:', error.message);
      return 0;
    }
    return count ?? 0;
  } catch {
    return 0;
  }
}

export interface SaveVpnFileInput {
  filename: string;
  mime_type: string | null;
  size_bytes: number;
  /** Base64-encoded raw bytes — PostgREST decodes this into the bytea column. */
  contentBase64: string;
  source_channel?: string | null;
  uploaded_by?: number | null;
}

/**
 * Store a raw config file (.npvt / .sip / .npv / ...) in vpn_files so it
 * appears in the app's File tab. Mirrors the scraper's upload contract
 * (dedupe on filename + size). Returns whether a new row was inserted.
 */
export async function saveVpnFile(
  supabase: any,
  input: SaveVpnFileInput,
): Promise<{ saved: boolean; duplicate: boolean }> {
  try {
    const { data: existing, error: checkErr } = await supabase
      .from('vpn_files')
      .select('id')
      .eq('filename', input.filename)
      .eq('size_bytes', input.size_bytes)
      .limit(1);
    if (checkErr) {
      console.warn('[db] saveVpnFile dedupe check failed:', checkErr.message);
      return { saved: false, duplicate: false };
    }
    if (existing && existing.length > 0) {
      return { saved: false, duplicate: true };
    }

    const { error: insertErr } = await supabase.from('vpn_files').insert({
      filename: input.filename,
      mime_type: input.mime_type ?? null,
      size_bytes: input.size_bytes,
      content: input.contentBase64,
      source_channel: input.source_channel ?? null,
      uploaded_by: input.uploaded_by ?? null,
      config_count: 0, // raw copy — configs are parsed separately into servers
    });
    if (insertErr) {
      console.warn('[db] saveVpnFile insert failed:', insertErr.message);
      return { saved: false, duplicate: false };
    }
    return { saved: true, duplicate: false };
  } catch (e) {
    console.warn('[db] saveVpnFile threw:', (e as Error).message);
    return { saved: false, duplicate: false };
  }
}
