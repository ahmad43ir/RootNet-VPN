// ============================================================
// 📁 index.ts — TELEGRAM BOT ENTRY POINT (WEBHOOK MODE)
// ============================================================
// Supabase Edge Function replacement for telegram-bot/bot.py.
//
// Edge functions can't run Telegram's long-poll loop, so this bot
// runs in WEBHOOK mode: Telegram POSTs updates here and we answer
// via the Bot API. Register the webhook once with POST /setwebhook
// (X-Admin-Key), after which Telegram delivers updates to:
//   https://bprkazfxqmanrybiexnh.supabase.co/functions/v1/telegram-bot
//
// Endpoints:
//   POST /               — Telegram update (validated by secret_token)
//   POST /setwebhook     — (X-Admin-Key) register the Telegram webhook
//   POST /deletewebhook  — (X-Admin-Key) remove the webhook
//   POST /getwebhookinfo — (X-Admin-Key) show webhook status
//   GET  / or /health    — health check
//
// Environment (set via `supabase secrets set`):
//   BOT_TOKEN  — Telegram bot token from @BotFather
//   ADMIN_IDS  — comma-separated Telegram user IDs (single admin)
//   ADMIN_KEY  — shared secret for the admin endpoints
//   DEFAULT_FLAG / DEFAULT_COUNTRY — optional import defaults
//   (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are injected automatically)
//
// Deploy: supabase functions deploy telegram-bot --project-ref bprkazfxqmanrybiexnh --no-verify-jwt
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsPreflight, jsonResponse, log, requireEnv } from './_utils.ts';
import * as tg from './_telegram.ts';
import { getWebhookSecret, saveWebhookSecret } from './_state.ts';
import { routeUpdate, type BotContext } from './_handlers.ts';

// ─── Environment at module load (fail fast on required vars) ──
const BOT_TOKEN = requireEnv('VLESSHUB_BOT_TOKEN');
const SUPABASE_URL = requireEnv('SUPABASE_URL');
const SUPABASE_KEY = requireEnv('SUPABASE_SERVICE_ROLE_KEY');
const ADMIN_KEY = Deno.env.get('ADMIN_KEY') ?? '';
const ADMIN_IDS = new Set(
  (Deno.env.get('ADMIN_IDS') ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter((s) => /^\d+$/.test(s))
    .map(Number),
);
const DEFAULT_FLAG = Deno.env.get('DEFAULT_FLAG') ?? '🌐';
const DEFAULT_COUNTRY = Deno.env.get('DEFAULT_COUNTRY') ?? 'Community';
const GEOAPI_URL = `${SUPABASE_URL}/functions/v1/geo-api`;

if (ADMIN_IDS.size === 0) {
  log('warn', 'entry', 'ADMIN_IDS is empty — the bot starts but denies everyone (type /myid to discover your ID).');
}
if (!ADMIN_KEY) {
  log('warn', 'entry', 'ADMIN_KEY is empty — admin endpoints (setwebhook etc.) will reject all requests.');
}

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const ctx: BotContext = {
  token: BOT_TOKEN,
  supabase,
  adminIds: ADMIN_IDS,
  insertCtx: {
    geoApiUrl: GEOAPI_URL,
    defaultFlag: DEFAULT_FLAG,
    defaultCountry: DEFAULT_COUNTRY,
  },
};

// ─── Helpers ─────────────────────────────────────────────────

function normalizedRoute(pathname: string): string {
  const marker = '/telegram-bot';
  const idx = pathname.lastIndexOf(marker);
  const path = idx >= 0 ? pathname.slice(idx + marker.length) : pathname;
  return path === '' ? '/' : path;
}

function requireAdminKey(req: Request): boolean {
  const provided = req.headers.get('X-Admin-Key') ?? '';
  return ADMIN_KEY !== '' && provided === ADMIN_KEY;
}

async function handleAdmin(req: Request, route: string): Promise<Response> {
  const webhookUrl = `${SUPABASE_URL}/functions/v1/telegram-bot`;
  try {
    if (route === '/setwebhook') {
      // Reuse the existing secret if present, else mint one and persist it.
      let secret = await getWebhookSecret(supabase);
      if (!secret) {
        secret = crypto.randomUUID().replace(/-/g, '');
        await saveWebhookSecret(supabase, secret);
      }
      const ok = await tg.setWebhook(BOT_TOKEN, webhookUrl, secret);
      if (!ok) {
        return jsonResponse(
          { error: 'setWebhook failed — check BOT_TOKEN and that the function URL is HTTPS' },
          502,
        );
      }
      return jsonResponse({ ok: true, webhookUrl, secret });
    }

    if (route === '/deletewebhook') {
      const ok = await tg.deleteWebhook(BOT_TOKEN);
      if (!ok) {
        return jsonResponse({ error: 'deleteWebhook failed' }, 502);
      }
      return jsonResponse({ ok: true });
    }

    // /getwebhookinfo
    const info = await tg.getWebhookInfo(BOT_TOKEN);
    if (!info) {
      return jsonResponse({ error: 'getWebhookInfo failed' }, 502);
    }
    return jsonResponse({ ok: true, webhook: info });
  } catch (e) {
    log('error', 'admin', `Admin endpoint ${route} failed: ${(e as Error).message}`);
    return jsonResponse({ error: 'Internal server error' }, 500);
  }
}

// ─── HTTP handler ────────────────────────────────────────────

Deno.serve(async (req) => {
  if (req.method === 'OPTIONS') return corsPreflight();

  const url = new URL(req.url);
  const route = normalizedRoute(url.pathname);

  // ── Health ───────────────────────────────────────────────
  if (req.method === 'GET' && (route === '/' || route === '/health')) {
    return jsonResponse({
      status: 'ok',
      service: 'telegram-bot',
      webhook: `${SUPABASE_URL}/functions/v1/telegram-bot`,
    });
  }

  // ── Admin endpoints ──────────────────────────────────────
  if (
    req.method === 'POST' &&
    (route === '/setwebhook' || route === '/deletewebhook' || route === '/getwebhookinfo')
  ) {
    if (!requireAdminKey(req)) {
      return jsonResponse({ error: 'Unauthorized — valid X-Admin-Key required' }, 401);
    }
    return await handleAdmin(req, route);
  }

  // ── Telegram updates (only POST to /) ────────────────────
  if (req.method !== 'POST' || route !== '/') {
    return jsonResponse({ error: 'Not found' }, 404);
  }

  // Validate the secret_token Telegram signed the webhook with.
  const secret = await getWebhookSecret(supabase);
  const provided = req.headers.get('X-Telegram-Bot-Api-Secret-Token');
  if (!secret || provided !== secret) {
    log('warn', 'entry', 'Rejected update — invalid or missing webhook secret token');
    return jsonResponse({ error: 'Unauthorized' }, 401);
  }

  let update: any;
  try {
    update = await req.json();
  } catch {
    return jsonResponse({ error: 'Bad request' }, 400);
  }

  try {
    await routeUpdate(ctx, update);
  } catch (e) {
    log('error', 'entry', `Unhandled update error: ${(e as Error).message}`);
  }

  return jsonResponse({ ok: true });
});
