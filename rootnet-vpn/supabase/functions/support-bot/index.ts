// ============================================================
// 📁 index.ts — SUPPORT BOT ENTRY POINT (WEBHOOK MODE)
// ============================================================
// Customer-facing support bot for VlessHub & RootNet VPN.
// Handles downloads, contact, FAQ, and general support.
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
//   ADMIN_KEY  — shared secret for admin endpoints
//   CONTACT_EMAIL — support email (optional)
//   GITHUB_REPO_VLESSHUB — GitHub repo for VlessHub releases
//   GITHUB_REPO_ROOTNET_VPN — GitHub repo for RootNet VPN releases
//   (SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY are injected automatically)
//
// Deploy: supabase functions deploy support-bot --project-ref bprkazfxqmanrybiexnh --no-verify-jwt
// ============================================================

import { createClient } from 'jsr:@supabase/supabase-js@2';
import { corsPreflight, jsonResponse, log, requireEnv } from './_utils.ts';
import * as tg from './_telegram.ts';
import { getWebhookSecret, saveWebhookSecret } from './_state.ts';
import { routeUpdate, type BotContext } from './_handlers.ts';

// ─── Environment ─────────────────────────────────────────────
const BOT_TOKEN = requireEnv('BOT_TOKEN');
const SUPABASE_URL = requireEnv('SUPABASE_URL');
const SUPABASE_KEY = requireEnv('SUPABASE_SERVICE_ROLE_KEY');
const ADMIN_KEY = Deno.env.get('ADMIN_KEY') ?? '';
const CONTACT_EMAIL = Deno.env.get('CONTACT_EMAIL') ?? 'support@rootnet.app';
const GITHUB_REPO_VLESSHUB = Deno.env.get('GITHUB_REPO_VLESSHUB') ?? 'ahmad43ir/vlesshub';
const GITHUB_REPO_ROOTNET_VPN = Deno.env.get('GITHUB_REPO_ROOTNET_VPN') ?? 'ahmad43ir/rootnet';

log('info', 'entry', `Starting support-bot — contact: ${CONTACT_EMAIL}`);

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const ctx: BotContext = {
  token: BOT_TOKEN,
  supabase,
  contactEmail: CONTACT_EMAIL,
  githubRepoVlessHub: GITHUB_REPO_VLESSHUB,
  githubRepoRootNetVPN: GITHUB_REPO_ROOTNET_VPN,
};

// ─── Helpers ─────────────────────────────────────────────────

function normalizedRoute(pathname: string): string {
  const marker = '/support-bot';
  const idx = pathname.lastIndexOf(marker);
  const path = idx >= 0 ? pathname.slice(idx + marker.length) : pathname;
  return path === '' ? '/' : path;
}

function requireAdminKey(req: Request): boolean {
  const provided = req.headers.get('X-Admin-Key') ?? '';
  return ADMIN_KEY !== '' && provided === ADMIN_KEY;
}

async function handleAdmin(req: Request, route: string): Promise<Response> {
  const webhookUrl = `${SUPABASE_URL}/functions/v1/support-bot`;
  try {
    if (route === '/setwebhook') {
      let secret = await getWebhookSecret(supabase);
      if (!secret) {
        secret = crypto.randomUUID().replace(/-/g, '');
        await saveWebhookSecret(supabase, secret);
      }
      const ok = await tg.setWebhook(BOT_TOKEN, webhookUrl, secret);
      if (!ok) {
        return jsonResponse({ error: 'setWebhook failed' }, 502);
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
      service: 'support-bot',
      webhook: `${SUPABASE_URL}/functions/v1/support-bot`,
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

  // ── Telegram updates ─────────────────────────────────────
  if (req.method !== 'POST' || route !== '/') {
    return jsonResponse({ error: 'Not found' }, 404);
  }

  const secret = await getWebhookSecret(supabase);
  const provided = req.headers.get('X-Telegram-Bot-Api-Secret-Token');
  if (!secret || provided !== secret) {
    log('warn', 'entry', 'Rejected update — invalid webhook secret');
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
    log('error', 'entry', `Unhandled error: ${(e as Error).message}`);
  }

  return jsonResponse({ ok: true });
});
