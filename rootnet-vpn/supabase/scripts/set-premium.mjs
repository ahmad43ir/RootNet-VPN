// Set a user's premium flag (app_metadata.isPremium = true) via the Management API.
//
//   node supabase/scripts/set-premium.mjs <email> [true|false]
//
// Reads SUPABASE_ACCESS_TOKEN + PROJECT_REF from the Kotlin project's .env.
// Runs a guarded UPDATE against auth.users so the new claim lands in the JWT
// (app checks app_metadata.isPremium; RLS uses the same claim).
// NOTE: existing JWTs are only refreshed on token refresh / re-login.
//
// Usage examples:
//   node supabase/scripts/set-premium.mjs mobileahmad43@gmail.com          # grant
//   node supabase/scripts/set-premium.mjs mobileahmad43@gmail.com false    # revoke

import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadEnvFile } from 'node:process';

const __dirname = dirname(fileURLToPath(import.meta.url));
loadEnvFile(join(__dirname, '..', '..', 'android-app', '.env'));

const PROJECT_REF = process.env.PROJECT_REF;
const MGMT_TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
const MGMT = 'https://api.supabase.com';

const email = process.argv[2]?.trim().toLowerCase();
const grant = (process.argv[3] ?? 'true').toLowerCase() !== 'false';

if (!email || !PROJECT_REF || !MGMT_TOKEN) {
  console.error('usage: node set-premium.mjs <email> [true|false]  (needs .env)');
  process.exit(1);
}

const query = `
UPDATE auth.users
SET raw_app_meta_data = raw_app_meta_data || jsonb_build_object('isPremium', ${grant})
WHERE email = '${email.replace(/'/g, "''")}'
RETURNING id, email, raw_app_meta_data;
`;

const res = await fetch(`${MGMT}/v1/projects/${PROJECT_REF}/database/query`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${MGMT_TOKEN}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ query }),
});

const data = await res.json().catch(() => ({}));
if (!res.ok) {
  console.error(`Management API query failed -> ${res.status}: ${JSON.stringify(data)}`);
  process.exit(1);
}

if (!Array.isArray(data) || data.length === 0) {
  console.error(`No user found with email "${email}".`);
  process.exit(1);
}

const row = data[0];
console.log(`✅ premium=${grant} for ${row.email} (${row.id})`);
console.log(`   app_metadata: ${JSON.stringify(row.raw_app_meta_data)}`);
