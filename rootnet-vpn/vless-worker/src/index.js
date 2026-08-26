/**
 * VLESS Ingestion API Worker
 * ─────────────────────────────────────────────────
 * Receives VPN configs (VLESS, VMess, Trojan, SS/SSR, Hysteria2, WireGuard,
 * SOCKS, ...) from the Telegram scraper (via webhook) and stores them in
 * Supabase (vless_links table).
 *
 * Pipeline:
 *   Telegram Channel → Telethon Listener → POST /webhook → Worker → Supabase
 *
 * Endpoints:
 *   POST /webhook        — Receive a message from Telegram, extract configs, store
 *   POST /webhook/batch  — Receive pre-extracted config links directly
 *   GET  /health         — Health check with link count
 *   POST /cleanup        — Trigger manual cleanup of old links (3s+ age)
 *
 * Environment (secrets):
 *   SUPABASE_URL              — Supabase project URL
 *   SUPABASE_SERVICE_ROLE_KEY — Supabase service_role key (write access)
 *   WEBHOOK_API_KEY           — Shared secret to authenticate incoming webhooks
 *
 * Deployment:
 *   cd vless-worker
 *   npx wrangler deploy
 */
// ─── Config Pattern ─────────────────────────────────────────────────────────
// Matches any VPN config URI the scraper may send (VLESS, VMess, Trojan,
// SS/SSR, Hysteria2, WireGuard, SOCKS, ...). Kept loose on purpose — junk is
// filtered at import time by the import RPC.
const CONFIG_SCHEMES =
  'vless|vmess|trojan|ss|ssr|shadowsocks|socks|socks5|socks5h|socks4' +
  '|hysteria2|hy2|hysteria|tuic|wireguard|warp|ssh';
const CONFIG_PATTERN = new RegExp(`\\b(?:${CONFIG_SCHEMES}):\\/\\/[^\\s"'<>]+`, 'gi');

// Scheme names accepted by isValidConfigLink().
const SUPPORTED_SCHEMES = new Set(
  CONFIG_SCHEMES.split('|').map((s) => s.toLowerCase()),
);

// Trailing junk that sometimes rides along on a copied URI.
const TRAILING_JUNK = ')]}>},;."\'';

// ─── Constants ───────────────────────────────────────────────────────────────
const DEFAULT_MAX_AGE_HOURS = 36;
const TABLE = 'vless_links';

// ─── CORS Headers ────────────────────────────────────────────────────────────
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Webhook-Key',
  'Access-Control-Max-Age': '86400',
};

// ─── Helpers ─────────────────────────────────────────────────────────────────

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...corsHeaders },
  });
}

/**
 * Validate the webhook API key from the request header.
 */
function validateWebhookKey(request, env) {
  const key = request.headers.get('X-Webhook-Key');
  if (!env.WEBHOOK_API_KEY) {
    console.warn('⚠️ No WEBHOOK_API_KEY configured — rejecting all requests');
    return false;
  }
  return key === env.WEBHOOK_API_KEY;
}

/**
 * Extract all unique config links from a text string (any scheme).
 * Returns deduplicated array preserving order of first occurrence.
 * Also extracts SIP JSON configs from text.
 */
function extractConfigLinks(text) {
  if (!text || typeof text !== 'string') return [];

  const matches = text.match(CONFIG_PATTERN) || [];
  const seen = new Set();
  const unique = [];

  for (const link of matches) {
    let normalized = link.trim();
    while (normalized.length > 0 && TRAILING_JUNK.includes(normalized[normalized.length - 1])) {
      normalized = normalized.slice(0, -1);
    }
    if (normalized && !seen.has(normalized)) {
      seen.add(normalized);
      unique.push(normalized);
    }
  }

  // Also extract SIP JSON configs from text (look for {...} patterns with protocol/host)
  const jsonMatches = text.match(/\{[\s\S]*?\}/g) || [];
  for (const jsonStr of jsonMatches) {
    try {
      const parsed = JSON.parse(jsonStr);
      const protocolKeys = ['protocol', 'type', 'proto'];
      const hostKeys = ['host', 'address', 'server', 'ip', 'hostname'];
      const portKeys = ['port', 'port_number'];
      
      const hasProtocol = protocolKeys.some(k => k in parsed);
      const hasHost = hostKeys.some(k => k in parsed);
      const hasPort = portKeys.some(k => k in parsed);
      
      if (hasProtocol && hasHost) {
        const proto = String(parsed.protocol || parsed.type || parsed.proto || '').toLowerCase();
        if (['ssh', 'socks', 'socks4', 'socks5', 'http', 'https', 'socks4a', 'socks5h'].includes(proto)) {
          const compact = JSON.stringify(parsed);
          if (!seen.has(compact)) {
            seen.add(compact);
            unique.push(compact);
          }
        }
      }
    } catch {
      // Not valid JSON, skip
    }
  }

  return unique;
}

/**
 * Basic config format validation.
 * Accepts any supported scheme with a non-empty body:
 *   scheme://<payload>?[params]#[fragment]
 * Also accepts SIP JSON configs: {"protocol": "ssh|socks|http", "host": "...", "port": 22, ...}
 * Supports variations: "type"/"proto" for protocol, "ip"/"hostname" for host
 */
function isValidConfigLink(link) {
  if (!link || typeof link !== 'string') return false;

  // Check for SIP JSON config
  if (link.trim().startsWith('{')) {
    try {
      const parsed = JSON.parse(link);
      const protocolKeys = ['protocol', 'type', 'proto'];
      const hostKeys = ['host', 'address', 'server', 'ip', 'hostname'];
      
      const hasProtocol = protocolKeys.some(k => k in parsed);
      const hasHost = hostKeys.some(k => k in parsed);
      
      if (hasProtocol && hasHost) {
        const proto = String(parsed.protocol || parsed.type || parsed.proto || '').toLowerCase();
        if (['ssh', 'socks', 'socks4', 'socks5', 'http', 'https', 'socks4a', 'socks5h'].includes(proto)) {
          return true;
        }
      }
    } catch {
      // Not valid JSON
    }
  }

  const match = link.match(/^([a-z0-9]+):\/\//i);
  if (!match) return false;
  const scheme = match[1].toLowerCase();
  if (!SUPPORTED_SCHEMES.has(scheme)) return false;

  const rest = link.slice(match[0].length);
  return rest.length > 0;
}

/**
 * Check if a link already exists in Supabase.
 */
async function linkExists(supabase, link) {
  const url = `${supabase.url}/rest/v1/${TABLE}?link=eq.${encodeURIComponent(link)}&select=id&limit=1`;
  const response = await fetch(url, {
    headers: {
      'apikey': supabase.key,
      'Authorization': `Bearer ${supabase.key}`,
    },
  });

  if (!response.ok) {
    console.error(`Supabase query error: ${response.status} ${await response.text()}`);
    return false; // don't die on query failure — retry on insert
  }

  const data = await response.json();
  return data.length > 0;
}

/**
 * Insert a single VLESS link into Supabase.
 * [scrapedAt] is the ISO-8601 UTC time the link was scraped (Telegram message
 * date) — used as created_at so the app can show it next to the link. Falls
 * back to now() when absent.
 * Returns { inserted: boolean, error?: string }.
 */
async function insertLink(supabase, link, sourceChannel, scrapedAt = null) {
  try {
    const exists = await linkExists(supabase, link);
    if (exists) {
      console.log(`  → Duplicate, skipped: ${link.substring(0, 60)}...`);
      return { inserted: false };
    }

    const body = {
      link,
      source_channel: sourceChannel || '',
      created_at: scrapedAt || new Date().toISOString(),
    };

    const response = await fetch(`${supabase.url}/rest/v1/${TABLE}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': supabase.key,
        'Authorization': `Bearer ${supabase.key}`,
        'Prefer': 'return=minimal',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const text = await response.text();

      // 409 Conflict = duplicate (unique constraint)
      if (response.status === 409) {
        console.log(`  → Duplicate (conflict), skipped: ${link.substring(0, 60)}...`);
        return { inserted: false };
      }

      console.error(`Supabase insert error (${response.status}): ${text}`);
      return { inserted: false, error: `Supabase error: ${response.status}` };
    }

    console.log(`  ✅ Inserted: ${link.substring(0, 60)}... (from @${sourceChannel})`);
    return { inserted: true };
  } catch (e) {
    console.error(`  ❌ Insert error: ${e.message}`);
    return { inserted: false, error: e.message };
  }
}

/**
 * Get the total number of links in the vless_links table.
 */
async function getLinkCount(supabase) {
  try {
    const url = `${supabase.url}/rest/v1/${TABLE}?select=id&limit=0`;
    const response = await fetch(url, {
      headers: {
        'apikey': supabase.key,
        'Authorization': `Bearer ${supabase.key}`,
        'Prefer': 'count=exact',
      },
    });

    if (!response.ok) return null;

    const count = response.headers.get('content-range');
    if (count) {
      const match = count.match(/\/(\d+)$/);
      if (match) return parseInt(match[1], 10);
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * Delete links older than the specified age in hours.
 * Returns the number of deleted rows.
 */
async function cleanupOldLinks(supabase, maxAgeHours = DEFAULT_MAX_AGE_HOURS) {
  try {
    const cutoff = new Date(Date.now() - maxAgeHours * 3600 * 1000).toISOString();

    const response = await fetch(`${supabase.url}/rest/v1/${TABLE}?created_at=lt.${encodeURIComponent(cutoff)}`, {
      method: 'DELETE',
      headers: {
        'apikey': supabase.key,
        'Authorization': `Bearer ${supabase.key}`,
        'Prefer': 'return=representation',
      },
    });

    if (!response.ok) {
      console.error(`Cleanup error: ${response.status} ${await response.text()}`);
      return 0;
    }

    const deleted = await response.json();
    const count = Array.isArray(deleted) ? deleted.length : 0;

    if (count > 0) {
      console.log(`🧹 Cleaned up ${count} old link(s) (> ${maxAgeHours}h old)`);
    }

    return count;
  } catch (e) {
    console.error(`🧹 Cleanup error: ${e.message}`);
    return 0;
  }
}

// ─── Handlers ────────────────────────────────────────────────────────────────

/**
 * POST /webhook — Receive a message from the Telegram scraper.
 *
 * Expected payload:
 *   { "message": "...raw message text...", "source": "channel_name" }
 *
 * The Worker extracts config links (any scheme), deduplicates, validates,
 * and stores in Supabase.
 */
async function handleWebhook(request, env) {
  if (!validateWebhookKey(request, env)) {
    return json({ error: 'Unauthorized — valid X-Webhook-Key required' }, 401);
  }

  try {
    const body = await request.json();

    if (!body || typeof body !== 'object') {
      return json({ error: 'Invalid payload — JSON object expected' }, 400);
    }

    const message = body.message || '';
    const source = body.source || 'unknown';
    // Scraped-at time (Telegram message date, ISO-8601 UTC) — shown in the app.
    const scrapedAt = typeof body.scraped_at === 'string' && body.scraped_at ? body.scraped_at : null;

    if (!message || typeof message !== 'string') {
      return json({ error: 'Empty or invalid message' }, 400);
    }

    console.log(`📨 Webhook received from @${source} (${message.length} chars)`);

    // Extract config links (any scheme)
    const links = extractConfigLinks(message);
    if (links.length === 0) {
      console.log('  → No config links found in message');
      return json({
        success: true,
        extracted: 0,
        inserted: 0,
        message: 'No config links found',
      });
    }

    console.log(`🔗 Found ${links.length} config link(s) in message`);

    const supabase = { url: env.SUPABASE_URL, key: env.SUPABASE_SERVICE_ROLE_KEY };

    if (!supabase.url || !supabase.key) {
      return json({ error: 'Supabase not configured' }, 500);
    }

    // Process each link with dedup and validation
    let inserted = 0;
    let skipped = 0;
    const results = [];

    for (const link of links) {
      if (!isValidConfigLink(link)) {
        console.log(`  → Invalid format, skipped: ${link.substring(0, 60)}...`);
        skipped++;
        results.push({ link: link.substring(0, 60), status: 'invalid' });
        continue;
      }

      const result = await insertLink(supabase, link, source, scrapedAt);
      if (result.inserted) {
        inserted++;
        results.push({ link: link.substring(0, 60), status: 'inserted' });
      } else {
        skipped++;
        results.push({ link: link.substring(0, 60), status: result.error ? 'error' : 'duplicate' });
      }

      // Rate limit: small delay between Supabase calls
      await new Promise((r) => setTimeout(r, 200));
    }

    const total = await getLinkCount(supabase);

    return json({
      success: true,
      extracted: links.length,
      inserted,
      skipped,
      total_links: total,
      results,
    });
  } catch (e) {
    console.error('POST /webhook error:', e.message);
    return json({ error: 'Internal server error' }, 500);
  }
}

/**
 * POST /webhook/batch — Receive pre-extracted config links directly.
 *
 * Expected payload:
 *   { "links": ["vless://...", "ss://..."], "source": "channel_name" }
 *   OR
 *   ["vless://...", "ss://..."]
 */
async function handleWebhookBatch(request, env) {
  if (!validateWebhookKey(request, env)) {
    return json({ error: 'Unauthorized — valid X-Webhook-Key required' }, 401);
  }

  try {
    const body = await request.json();
    const links = Array.isArray(body) ? body : (body?.links || []);
    const source = body?.source || 'batch';
    // Scraped-at time (Telegram message date, ISO-8601 UTC) — shown in the app.
    const scrapedAt = typeof body?.scraped_at === 'string' && body?.scraped_at ? body.scraped_at : null;

    if (!Array.isArray(links) || links.length === 0) {
      return json({ error: 'No links provided' }, 400);
    }

    console.log(`📦 Batch received: ${links.length} link(s) from @${source}`);

    const supabase = { url: env.SUPABASE_URL, key: env.SUPABASE_SERVICE_ROLE_KEY };

    if (!supabase.url || !supabase.key) {
      return json({ error: 'Supabase not configured' }, 500);
    }

    let inserted = 0;
    let skipped = 0;
    const results = [];

    for (const link of links) {
      if (!isValidConfigLink(link)) {
        skipped++;
        results.push({ link: link.substring(0, 60), status: 'invalid' });
        continue;
      }

      const result = await insertLink(supabase, link, source, scrapedAt);
      if (result.inserted) {
        inserted++;
        results.push({ link: link.substring(0, 60), status: 'inserted' });
      } else {
        skipped++;
        results.push({ link: link.substring(0, 60), status: result.error ? 'error' : 'duplicate' });
      }

      await new Promise((r) => setTimeout(r, 200));
    }

    const total = await getLinkCount(supabase);

    return json({
      success: true,
      received: links.length,
      inserted,
      skipped,
      total_links: total,
      results,
    });
  } catch (e) {
    console.error('POST /webhook/batch error:', e.message);
    return json({ error: 'Internal server error' }, 500);
  }
}

/**
 * POST /cleanup — Trigger manual cleanup of old VLESS links.
 *
 * Optional body: { "max_age_hours": 36 }
 */
async function handleCleanup(request, env) {
  if (!validateWebhookKey(request, env)) {
    return json({ error: 'Unauthorized — valid X-Webhook-Key required' }, 401);
  }

  try {
    const supabase = { url: env.SUPABASE_URL, key: env.SUPABASE_SERVICE_ROLE_KEY };

    if (!supabase.url || !supabase.key) {
      return json({ error: 'Supabase not configured' }, 500);
    }

    let maxAgeHours = DEFAULT_MAX_AGE_HOURS;

    try {
      const body = await request.json();
      if (body && body.max_age_hours) {
        maxAgeHours = Math.max(1, parseInt(body.max_age_hours, 10) || DEFAULT_MAX_AGE_HOURS);
      }
    } catch {
      // No body — use default
    }

    const deleted = await cleanupOldLinks(supabase, maxAgeHours);

    return json({
      success: true,
      deleted,
      max_age_hours: maxAgeHours,
    });
  } catch (e) {
    console.error('POST /cleanup error:', e.message);
    return json({ error: 'Internal server error' }, 500);
  }
}

/**
 * GET /health — Health check with link count.
 */
async function handleHealth(env) {
  const supabase = { url: env.SUPABASE_URL, key: env.SUPABASE_SERVICE_ROLE_KEY };
  let linkCount = null;

  if (supabase.url && supabase.key) {
    linkCount = await getLinkCount(supabase);
  }

  return json({
    status: 'ok',
    service: 'VLESS Ingestion API',
    version: '1.0.0',
    supabase_configured: !!(supabase.url && supabase.key),
    link_count: linkCount,
  });
}

// ─── Router ──────────────────────────────────────────────────────────────────

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const method = request.method;

    // CORS preflight
    if (method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders });
    }

    try {
      // POST /webhook — Receive message from Telegram scraper
      if (method === 'POST' && url.pathname === '/webhook') {
        return await handleWebhook(request, env);
      }

      // POST /webhook/batch — Receive pre-extracted links
      if (method === 'POST' && url.pathname === '/webhook/batch') {
        return await handleWebhookBatch(request, env);
      }

      // POST /cleanup — Trigger manual cleanup
      if (method === 'POST' && url.pathname === '/cleanup') {
        return await handleCleanup(request, env);
      }

      // GET /health — Health check
      if (method === 'GET' && url.pathname === '/health') {
        return await handleHealth(env);
      }

      // 404
      return json({ error: 'Not found' }, 404);
    } catch (e) {
      console.error('Unhandled error:', e.message);
      return json({ error: 'Internal server error' }, 500);
    }
  },
};
