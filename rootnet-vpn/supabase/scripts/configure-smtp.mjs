#!/usr/bin/env node
// ============================================================
// 📮 configure-smtp.mjs
// ============================================================
// Configures Resend as the custom SMTP provider for the hosted
// Supabase project. This is REQUIRED to unlock custom email
// templates on the free tier (Supabase blocks template edits
// when using its default email provider).
//
//   PATCH https://api.supabase.com/v1/projects/{ref}/config/auth
//   { smtp_host, smtp_port, smtp_user, smtp_pass,
//     smtp_admin_email, smtp_sender_name }
//
// ── Usage ──────────────────────────────────────────────────
//   node supabase/scripts/configure-smtp.mjs          # uses .env
//   node supabase/scripts/configure-smtp.mjs --dry-run
//
// Reads RESEND_* + SUPABASE_ACCESS_TOKEN from .env (git-ignored).
// When you verify a real domain in Resend, update
// RESEND_SENDER_EMAIL in .env and re-run this script.
// ============================================================

import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

// Auto-load the Kotlin project's .env (android-app/.env, git-ignored).
// Existing env wins. NOTE: process.loadEnvFile requires Node >= 20.12.
try {
  process.loadEnvFile(join(__dirname, '..', '..', 'android-app', '.env'));
} catch {
  // No .env file — rely on process environment only.
}

const ACCESS_TOKEN = process.env.SUPABASE_ACCESS_TOKEN || '';
const PROJECT_REF = process.env.PROJECT_REF || 'bprkazfxqmanrybiexnh';
const DRY_RUN = process.argv.includes('--dry-run');

const RESEND_HOST = process.env.RESEND_SMTP_HOST || 'smtp.resend.com';
const RESEND_PORT = process.env.RESEND_SMTP_PORT || '465';
const RESEND_USER = process.env.RESEND_SMTP_USER || 'resend';
const RESEND_PASS = process.env.RESEND_API_KEY || process.env.RESEND_SMTP_PASS || '';
const SENDER_EMAIL = process.env.RESEND_SENDER_EMAIL || 'onboarding@resend.dev';
const SENDER_NAME = 'RootNet';

async function main() {
  if (!RESEND_PASS) {
    console.error('❌ Missing Resend API key (RESEND_API_KEY in .env).');
    process.exit(1);
  }

  const payload = {
    smtp_host: RESEND_HOST,
    smtp_port: RESEND_PORT,
    smtp_user: RESEND_USER,
    smtp_pass: RESEND_PASS,
    smtp_admin_email: SENDER_EMAIL,
    smtp_sender_name: SENDER_NAME,
  };

  console.log(`📮 Supabase custom SMTP → Resend`);
  console.log(`   Host:  ${RESEND_HOST}:${RESEND_PORT}`);
  console.log(`   User:  ${RESEND_USER}`);
  console.log(`   From:  "${SENDER_NAME}" <${SENDER_EMAIL}>`);
  console.log(`   Project: ${PROJECT_REF}`);

  if (SENDER_EMAIL === 'onboarding@resend.dev') {
    console.log(
      '\n⚠️  TEST MODE — onboarding@resend.dev only delivers to the Resend\n' +
        '    account owner. Verify a domain at resend.com/domains, then set\n' +
        '    RESEND_SENDER_EMAIL in .env and re-run this script.',
    );
  }

  if (DRY_RUN) {
    console.log('\n── DRY RUN (no request sent) ──');
    console.log(JSON.stringify(payload, null, 2));
    return;
  }

  if (!ACCESS_TOKEN) {
    console.error(
      '\n❌ Missing SUPABASE_ACCESS_TOKEN (add it to .env).',
    );
    process.exit(1);
  }

  const url = `https://api.supabase.com/v1/projects/${PROJECT_REF}/config/auth`;
  console.log(`\n➡️  PATCH ${url}`);

  const res = await fetch(url, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${ACCESS_TOKEN}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });

  const text = await res.text();

  if (!res.ok) {
    console.error(`\n❌ Request failed (${res.status}):`);
    console.error(text.slice(0, 2000));
    process.exit(1);
  }

  console.log(`\n✅ SMTP configured (${res.status}).`);
  try {
    const current = JSON.parse(text);
    console.log(`   Reported host: ${current.smtp_host}:${current.smtp_port}`);
    console.log(`   Reported sender: "${current.smtp_sender_name}" <${current.smtp_admin_email}>`);
  } catch {
    // Non-JSON body — nothing to verify here.
  }
}

main().catch((err) => {
  console.error('\n❌ Unexpected error:', err);
  process.exit(1);
});
