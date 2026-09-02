// Apply a SQL migration file to the project via the Management API.
//
//   node supabase/scripts/apply-migration.mjs supabase/migrations/20260807000001_create_free_connection_quota.sql
//
// Reads SUPABASE_ACCESS_TOKEN + PROJECT_REF from the Kotlin project's .env.
// Runs the whole file as one query against the database/query endpoint
// (same channel the SQL editor uses).

import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readFileSync } from 'node:fs';
import { loadEnvFile } from 'node:process';

const __dirname = dirname(fileURLToPath(import.meta.url));
loadEnvFile(join(__dirname, '..', '..', 'android-app', '.env'));

const PROJECT_REF = process.env.PROJECT_REF;
const MGMT_TOKEN = process.env.SUPABASE_ACCESS_TOKEN;
const MGMT = 'https://api.supabase.com';

const file = resolve(process.argv[2] ?? '');
if (!file || !PROJECT_REF || !MGMT_TOKEN) {
  console.error('usage: node apply-migration.mjs <migration.sql>  (needs .env)');
  process.exit(1);
}

const sql = readFileSync(file, 'utf8');
if (!sql.trim()) {
  console.error(`Empty migration file: ${file}`);
  process.exit(1);
}

const res = await fetch(`${MGMT}/v1/projects/${PROJECT_REF}/database/query`, {
  method: 'POST',
  headers: {
    Authorization: `Bearer ${MGMT_TOKEN}`,
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({ query: sql }),
});

const data = await res.json().catch(() => ({}));
if (!res.ok) {
  console.error(`Management API query failed -> ${res.status}: ${JSON.stringify(data)}`);
  process.exit(1);
}

console.log(`✅ Applied ${file.split('\\').pop().split('/').pop()}`);
if (Array.isArray(data) && data.length) {
  console.log('   result:', JSON.stringify(data).slice(0, 500));
}
