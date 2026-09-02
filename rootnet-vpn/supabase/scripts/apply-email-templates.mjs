#!/usr/bin/env node
// ============================================================
// 📧 apply-email-templates.mjs
// ============================================================
// Pushes the RootNet-branded auth email templates (in
// supabase/templates/) to the HOSTED Supabase project via the
// Management API.
//
//   PATCH https://api.supabase.com/v1/projects/{ref}/config/auth
//
// It also enables the security-notification emails (password
// changed, email changed, etc.) which are off by default.
//
// ── Usage ──────────────────────────────────────────────────
//   export SUPABASE_ACCESS_TOKEN="sbp_..."   # Personal Access Token
//   export PROJECT_REF="bprkazfxqmanrybiexnh" # optional, has default
//   node supabase/scripts/apply-email-templates.mjs
//
//   # Preview the payload without sending anything:
//   node supabase/scripts/apply-email-templates.mjs --dry-run
//
// Get a token at: https://supabase.com/dashboard/account/tokens
// Revoke it afterwards — PATs carry full account privileges.
// ============================================================

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const templatesDir = join(__dirname, '..', 'templates');

// ── Auto-load the repo .env (git-ignored) so agents can run this
//    script without exporting anything. Existing env vars win.
//    Credentials live in the Kotlin project's .env
//    (android-app/.env), three levels up from this file.
//    NOTE: process.loadEnvFile requires Node >= 20.12.
try {
  process.loadEnvFile(join(__dirname, '..', '..', 'android-app', '.env'));
} catch {
  // No .env file — rely on process environment only.
}

// ── Template registry ──────────────────────────────────────
// file      -> HTML file in supabase/templates/
// subject   -> email subject line
// template  -> Management API field for the HTML body
// subjectF  -> Management API field for the subject
// enableF   -> (optional) Management API field to switch the
//              security-notification email on
const TEMPLATES = [
  {
    file: 'confirmation.html',
    subject: 'Confirm your email — RootNet',
    template: 'mailer_templates_confirmation_content',
    subjectF: 'mailer_subjects_confirmation',
  },
  {
    file: 'recovery.html',
    subject: 'Reset your password — RootNet',
    template: 'mailer_templates_recovery_content',
    subjectF: 'mailer_subjects_recovery',
  },
  {
    file: 'magic_link.html',
    subject: 'Your sign-in link — RootNet',
    template: 'mailer_templates_magic_link_content',
    subjectF: 'mailer_subjects_magic_link',
  },
  {
    file: 'invite.html',
    subject: "You're invited to RootNet",
    template: 'mailer_templates_invite_content',
    subjectF: 'mailer_subjects_invite',
  },
  {
    file: 'email_change.html',
    subject: 'Confirm your new email — RootNet',
    template: 'mailer_templates_email_change_content',
    subjectF: 'mailer_subjects_email_change',
  },
  {
    file: 'reauthentication.html',
    subject: '{{ .Token }} is your verification code',
    template: 'mailer_templates_reauthentication_content',
    subjectF: 'mailer_subjects_reauthentication',
  },
  // ── Security notifications (enabled by this script) ──────
  {
    file: 'password_changed.html',
    subject: 'Your password was changed — RootNet',
    template: 'mailer_templates_password_changed_notification_content',
    subjectF: 'mailer_subjects_password_changed_notification',
    enableF: 'mailer_notifications_password_changed_enabled',
  },
  {
    file: 'email_changed.html',
    subject: 'Your email address was changed — RootNet',
    template: 'mailer_templates_email_changed_notification_content',
    subjectF: 'mailer_subjects_email_changed_notification',
    enableF: 'mailer_notifications_email_changed_enabled',
  },
  {
    file: 'phone_changed.html',
    subject: 'Your phone number was changed — RootNet',
    template: 'mailer_templates_phone_changed_notification_content',
    subjectF: 'mailer_subjects_phone_changed_notification',
    enableF: 'mailer_notifications_phone_changed_enabled',
  },
  {
    file: 'mfa_factor_enrolled.html',
    subject: 'A verification method was added — RootNet',
    template: 'mailer_templates_mfa_factor_enrolled_notification_content',
    subjectF: 'mailer_subjects_mfa_factor_enrolled_notification',
    enableF: 'mailer_notifications_mfa_factor_enrolled_enabled',
  },
  {
    file: 'mfa_factor_unenrolled.html',
    subject: 'A verification method was removed — RootNet',
    template: 'mailer_templates_mfa_factor_unenrolled_notification_content',
    subjectF: 'mailer_subjects_mfa_factor_unenrolled_notification',
    enableF: 'mailer_notifications_mfa_factor_unenrolled_enabled',
  },
  {
    file: 'identity_linked.html',
    subject: 'A sign-in method was linked — RootNet',
    template: 'mailer_templates_identity_linked_notification_content',
    subjectF: 'mailer_subjects_identity_linked_notification',
    enableF: 'mailer_notifications_identity_linked_enabled',
  },
  {
    file: 'identity_unlinked.html',
    subject: 'A sign-in method was removed — RootNet',
    template: 'mailer_templates_identity_unlinked_notification_content',
    subjectF: 'mailer_subjects_identity_unlinked_notification',
    enableF: 'mailer_notifications_identity_unlinked_enabled',
  },
];

// ── Config ─────────────────────────────────────────────────
const ACCESS_TOKEN = process.env.SUPABASE_ACCESS_TOKEN || '';
const PROJECT_REF = process.env.PROJECT_REF || 'bprkazfxqmanrybiexnh';
const DRY_RUN = process.argv.includes('--dry-run');

function buildPayload() {
  const body = {};
  for (const t of TEMPLATES) {
    const filePath = join(templatesDir, t.file);
    const html = readFileSync(filePath, 'utf8');
    body[t.template] = html;
    body[t.subjectF] = t.subject;
    if (t.enableF) body[t.enableF] = true;
  }
  return body;
}

function summarize(payload) {
  const names = TEMPLATES.map((t) => t.file.replace('.html', ''));
  return `${names.length} templates (${names.join(', ')})`;
}

async function main() {
  const payload = buildPayload();

  console.log(`📧 RootNet email templates — ${summarize(payload)}`);
  console.log(`   Project: ${PROJECT_REF}`);
  console.log(`   Body size: ${(JSON.stringify(payload).length / 1024).toFixed(1)} KB`);

  if (DRY_RUN) {
    console.log('\n── DRY RUN (no request sent) ──');
    console.log(JSON.stringify(payload, null, 2).slice(0, 1200));
    console.log('\n...');
    return;
  }

  if (!ACCESS_TOKEN) {
    console.error(
      '\n❌ Missing SUPABASE_ACCESS_TOKEN.\n' +
        '   export SUPABASE_ACCESS_TOKEN="sbp_..." (https://supabase.com/dashboard/account/tokens)',
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

  console.log(`\n✅ Success (${res.status}) — templates applied to project ${PROJECT_REF}.`);
  console.log('   Emails now go through the custom SMTP (see configure-smtp.mjs).');

  // Show what the project now reports for these fields
  try {
    const current = JSON.parse(text);
    const keys = Object.keys(payload);
    const reported = keys.filter((k) => typeof current[k] === 'string' && current[k].length > 0);
    console.log(`\nVerified ${reported.length}/${keys.length} fields on the project.`);
  } catch {
    // Non-JSON body — nothing to verify here.
  }
}

main().catch((err) => {
  console.error('\n❌ Unexpected error:', err);
  process.exit(1);
});
